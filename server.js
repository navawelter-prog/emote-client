'use strict';
/**
 * Emote-Relay: leitet Emote-Nachrichten zwischen Clients im selben "Raum" (= Minecraft-Server) weiter.
 * Keine Abhaengigkeiten, nur Node.js 18+.
 *
 * Start:   node server.js
 * Optional: PORT=8765  RELAY_TOKEN=geheimes-passwort  node server.js
 *
 * Protokoll (JSON, Text-Frames):
 *   Client -> Relay: {"t":"hello","uuid":"...","name":"...","room":"...","token":"..."}
 *                    {"t":"emote","id":"wave"}      {"t":"stop"}
 *   Relay -> Client: {"t":"emote","uuid":"...","id":"wave"}   {"t":"stop","uuid":"..."}
 */
const http = require('http');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 8765);
const TOKEN = process.env.RELAY_TOKEN || '';
const MAX_PAYLOAD = 2048;          // Bytes pro Nachricht
const MAX_MSGS_PER_SEC = 10;       // pro Verbindung
const MAX_CLIENTS = 1000;
const HELLO_TIMEOUT_MS = 10000;
const PING_INTERVAL_MS = 30000;
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const NAME_RE = /^\w{1,16}$/;
const ID_RE = /^[a-z0-9_]{1,32}$/;

/** room -> Set<Client> */
const rooms = new Map();
let clientCount = 0;

function safeEqual(a, b) {
  const ha = crypto.createHash('sha256').update(String(a)).digest();
  const hb = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(ha, hb);
}

function frame(opcode, payload) {
  const len = payload.length;
  let header;
  if (len < 126) {
    header = Buffer.from([0x80 | opcode, len]);
  } else if (len < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x80 | opcode;
    header[1] = 126;
    header.writeUInt16BE(len, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x80 | opcode;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(len), 2);
  }
  return Buffer.concat([header, payload]);
}

class Client {
  constructor(socket, ip) {
    this.socket = socket;
    this.ip = ip;
    this.buf = Buffer.alloc(0);
    this.uuid = null;
    this.room = null;
    this.closed = false;
    this.alive = true;
    this.tokens = MAX_MSGS_PER_SEC;
    this.lastRefill = Date.now();
  }

  sendJson(obj) {
    if (this.closed) return;
    this.socket.write(frame(0x1, Buffer.from(JSON.stringify(obj))));
  }

  close(code = 1000) {
    if (this.closed) return;
    const payload = Buffer.alloc(2);
    payload.writeUInt16BE(code, 0);
    try {
      this.socket.write(frame(0x8, payload));
    } catch (_) { /* Socket schon weg */ }
    this.socket.end();
    this.cleanup();
  }

  cleanup() {
    if (this.closed) return;
    this.closed = true;
    clientCount--;
    if (this.room) {
      const set = rooms.get(this.room);
      if (set) {
        set.delete(this);
        // Alle anderen informieren, dass dieser Spieler nicht mehr emotet
        broadcast(this, { t: 'stop', uuid: this.uuid });
        if (set.size === 0) rooms.delete(this.room);
      }
    }
  }

  allow() {
    const now = Date.now();
    this.tokens = Math.min(MAX_MSGS_PER_SEC, this.tokens + ((now - this.lastRefill) / 1000) * MAX_MSGS_PER_SEC);
    this.lastRefill = now;
    if (this.tokens < 1) return false;
    this.tokens -= 1;
    return true;
  }
}

function broadcast(from, obj) {
  const set = rooms.get(from.room);
  if (!set) return;
  for (const other of set) {
    if (other !== from) other.sendJson(obj);
  }
}

function handleMessage(c, text) {
  if (!c.allow()) return; // zu schnell: Nachricht verwerfen
  let msg;
  try {
    msg = JSON.parse(text);
  } catch (_) {
    return;
  }
  if (!msg || typeof msg !== 'object') return;

  if (msg.t === 'hello') {
    if (c.room) return; // schon angemeldet
    if (typeof msg.uuid !== 'string' || !UUID_RE.test(msg.uuid)) return c.close(1008);
    if (typeof msg.name !== 'string' || !NAME_RE.test(msg.name)) return c.close(1008);
    if (typeof msg.room !== 'string' || msg.room.length < 1 || msg.room.length > 100) return c.close(1008);
    if (TOKEN && (typeof msg.token !== 'string' || !safeEqual(msg.token, TOKEN))) return c.close(1008);
    c.uuid = msg.uuid.toLowerCase();
    c.room = msg.room.toLowerCase();
    if (!rooms.has(c.room)) rooms.set(c.room, new Set());
    rooms.get(c.room).add(c);
    return;
  }

  if (!c.room) return; // erst hello

  if (msg.t === 'emote') {
    if (typeof msg.id !== 'string' || !ID_RE.test(msg.id)) return;
    broadcast(c, { t: 'emote', uuid: c.uuid, id: msg.id });
  } else if (msg.t === 'stop') {
    broadcast(c, { t: 'stop', uuid: c.uuid });
  }
}

function parseFrames(c) {
  for (;;) {
    const buf = c.buf;
    if (buf.length < 2) return;
    const fin = (buf[0] & 0x80) !== 0;
    const opcode = buf[0] & 0x0f;
    const masked = (buf[1] & 0x80) !== 0;
    let len = buf[1] & 0x7f;
    let offset = 2;
    if (len === 126) {
      if (buf.length < 4) return;
      len = buf.readUInt16BE(2);
      offset = 4;
    } else if (len === 127) {
      if (buf.length < 10) return;
      const big = buf.readBigUInt64BE(2);
      if (big > BigInt(MAX_PAYLOAD)) return c.close(1009);
      len = Number(big);
      offset = 10;
    }
    if (len > MAX_PAYLOAD) return c.close(1009);
    if (!masked) return c.close(1002);            // Client-Frames muessen maskiert sein
    if (buf.length < offset + 4 + len) return;    // Frame noch nicht komplett

    const mask = buf.subarray(offset, offset + 4);
    const payload = Buffer.from(buf.subarray(offset + 4, offset + 4 + len));
    for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
    c.buf = buf.subarray(offset + 4 + len);

    if (opcode === 0x8) return c.close(1000);
    if (opcode === 0x9) {                          // Ping -> Pong
      c.socket.write(frame(0xA, payload));
      continue;
    }
    if (opcode === 0xA) {                          // Pong
      c.alive = true;
      continue;
    }
    if (opcode !== 0x1 || !fin) return c.close(1003); // nur unfragmentierte Text-Frames
    handleMessage(c, payload.toString('utf8'));
    if (c.closed) return;
  }
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }
  res.writeHead(426, { 'Content-Type': 'text/plain' });
  res.end('WebSocket erforderlich');
});

server.on('upgrade', (req, socket) => {
  const key = req.headers['sec-websocket-key'];
  const upgrade = String(req.headers.upgrade || '').toLowerCase();
  if (upgrade !== 'websocket' || !key || req.headers['sec-websocket-version'] !== '13' || clientCount >= MAX_CLIENTS) {
    socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
    return;
  }
  const accept = crypto.createHash('sha1').update(key + GUID).digest('base64');
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\n' +
    'Connection: Upgrade\r\n' +
    `Sec-WebSocket-Accept: ${accept}\r\n\r\n`
  );
  socket.setNoDelay(true);

  const c = new Client(socket, req.socket.remoteAddress);
  clientCount++;

  const helloTimer = setTimeout(() => { if (!c.room) c.close(1008); }, HELLO_TIMEOUT_MS);
  const pingTimer = setInterval(() => {
    if (!c.alive) return c.close(1001);
    c.alive = false;
    if (!c.closed) socket.write(frame(0x9, Buffer.alloc(0)));
  }, PING_INTERVAL_MS);

  const done = () => {
    clearTimeout(helloTimer);
    clearInterval(pingTimer);
    c.cleanup();
  };
  socket.on('data', (chunk) => {
    c.buf = Buffer.concat([c.buf, chunk]);
    if (c.buf.length > MAX_PAYLOAD * 4) return c.close(1009); // Puffer-Schutz
    parseFrames(c);
  });
  socket.on('close', done);
  socket.on('error', done);
});

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Emote-Relay laeuft auf Port ${PORT}${TOKEN ? ' (mit Token)' : ' (OHNE Token)'}`);
  });
}

module.exports = { server };
