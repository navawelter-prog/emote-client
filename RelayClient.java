package dev.nava.emoteclient.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nava.emoteclient.EmoteClientMod;
import dev.nava.emoteclient.emote.EmoteManager;
import net.minecraft.client.Minecraft;

/**
 * Haelt eine WebSocket-Verbindung zum Relay-Server, verschickt eigene Emotes
 * und spielt Emotes anderer Spieler ab. Verbindet sich bei Abbruch automatisch neu.
 */
public final class RelayClient implements WebSocket.Listener {
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	private final StringBuilder partial = new StringBuilder();

	private volatile WebSocket socket;
	private volatile boolean wanted;
	private volatile int generation;
	private int attempt;

	private String url;
	private String token;
	private UUID self;
	private String name;
	private String room;

	public synchronized void start(String url, String token, UUID self, String name, String room) {
		stop();
		this.url = url;
		this.token = token;
		this.self = self;
		this.name = name;
		this.room = room;
		this.wanted = true;
		this.attempt = 0;
		open();
	}

	public synchronized void stop() {
		wanted = false;
		generation++;
		WebSocket ws = socket;
		socket = null;
		if (ws != null) {
			ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
		}
	}

	public void sendEmote(String id) {
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "emote");
		msg.addProperty("id", id);
		send(msg);
	}

	public void sendStop() {
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "stop");
		send(msg);
	}

	private void send(JsonObject msg) {
		WebSocket ws = socket;
		if (ws != null) {
			ws.sendText(GSON.toJson(msg), true);
		}
	}

	private synchronized void open() {
		if (!wanted) {
			return;
		}
		final int gen = generation;
		HTTP.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.buildAsync(URI.create(url), this)
				.whenComplete((ws, error) -> {
					if (error != null && gen == generation) {
						scheduleReconnect(gen);
					}
				});
	}

	private void scheduleReconnect(int gen) {
		if (!wanted || gen != generation) {
			return;
		}
		int seconds = Math.min(30, 1 << Math.min(attempt++, 5));
		CompletableFuture.delayedExecutor(seconds, TimeUnit.SECONDS).execute(() -> {
			if (wanted && gen == generation) {
				open();
			}
		});
	}

	@Override
	public void onOpen(WebSocket ws) {
		socket = ws;
		attempt = 0;
		JsonObject hello = new JsonObject();
		hello.addProperty("t", "hello");
		hello.addProperty("uuid", self.toString());
		hello.addProperty("name", name);
		hello.addProperty("room", room);
		hello.addProperty("token", token);
		ws.sendText(GSON.toJson(hello), true);
		ws.request(1);
		EmoteClientMod.LOGGER.info("Mit Relay verbunden ({})", url);
	}

	@Override
	public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
		partial.append(data);
		if (last) {
			String text = partial.toString();
			partial.setLength(0);
			handle(text);
		}
		ws.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
		socket = null;
		scheduleReconnect(generation);
		return null;
	}

	@Override
	public void onError(WebSocket ws, Throwable error) {
		socket = null;
		scheduleReconnect(generation);
	}

	private void handle(String text) {
		try {
			JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
			String type = msg.get("t").getAsString();
			UUID player = UUID.fromString(msg.get("uuid").getAsString());
			if (player.equals(self)) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			if ("emote".equals(type)) {
				String id = msg.get("id").getAsString();
				mc.execute(() -> EmoteManager.start(player, id));
			} else if ("stop".equals(type)) {
				mc.execute(() -> EmoteManager.stop(player));
			}
		} catch (RuntimeException e) {
			EmoteClientMod.LOGGER.debug("Ungueltige Relay-Nachricht ignoriert: {}", text);
		}
	}
}
