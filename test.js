'use strict';
// Selbsttest: node test.js  (Node 22+, nutzt den eingebauten WebSocket-Client)
const assert = require('assert');
const { server } = require('./server.js');

const A = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const B = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const C = 'cccccccc-cccc-cccc-cccc-cccccccccccc';

function connect(port, uuid, name, room) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${port}`);
    const inbox = [];
    ws.onmessage = (e) => inbox.push(JSON.parse(e.data));
    ws.onerror = reject;
    ws.onopen = () => {
      ws.send(JSON.stringify({ t: 'hello', uuid, name, room, token: '' }));
      resolve({ ws, inbox });
    };
  });
}
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

server.listen(0, async () => {
  const port = server.address().port;
  try {
    const a = await connect(port, A, 'Alice', 'Play.Example.Net');
    const b = await connect(port, B, 'Bob', 'play.example.net');   // gleicher Raum (Gross/Klein egal)
    const c = await connect(port, C, 'Carl', 'anderer.server');    // anderer Raum
    await wait(100);

    a.ws.send(JSON.stringify({ t: 'emote', id: 'wave' }));
    await wait(100);
    assert.deepStrictEqual(b.inbox, [{ t: 'emote', uuid: A, id: 'wave' }], 'Bob bekommt das Emote');
    assert.strictEqual(a.inbox.length, 0, 'Sender bekommt kein Echo');
    assert.strictEqual(c.inbox.length, 0, 'Anderer Raum bekommt nichts');

    a.ws.send(JSON.stringify({ t: 'emote', id: 'BAD ID!' }));
    a.ws.send('kein json');
    await wait(100);
    assert.strictEqual(b.inbox.length, 1, 'Ungueltige Nachrichten werden verworfen');

    a.ws.send(JSON.stringify({ t: 'stop' }));
    await wait(100);
    assert.deepStrictEqual(b.inbox[1], { t: 'stop', uuid: A });

    a.ws.close();
    await wait(150);
    assert.deepStrictEqual(b.inbox[2], { t: 'stop', uuid: A }, 'Beim Trennen kommt ein stop');

    // Rate-Limit: 50 Nachrichten auf einmal, nicht alle kommen durch
    for (let i = 0; i < 50; i++) b.ws.send(JSON.stringify({ t: 'emote', id: 'dance' }));
    await wait(200);
    assert.ok(c.inbox.length === 0);

    console.log('Alle Relay-Tests bestanden');
    process.exit(0);
  } catch (err) {
    console.error('TEST FEHLGESCHLAGEN:', err.message);
    process.exit(1);
  }
});
