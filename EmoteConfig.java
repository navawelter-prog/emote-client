package dev.nava.emoteclient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Einstellungen in config/emoteclient.json (wird beim ersten Start angelegt).
 */
public final class EmoteConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Adresse deines Relay-Servers, z. B. ws://dein-server.de:8765 */
	public static String relayUrl = "ws://localhost:8765";
	/** Muss zum RELAY_TOKEN des Relay-Servers passen (leer lassen, wenn keiner gesetzt ist). */
	public static String token = "";

	private EmoteConfig() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("emoteclient.json");
	}

	public static void load() {
		Path file = path();
		try {
			if (Files.exists(file)) {
				JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
				if (json != null) {
					if (json.has("relayUrl")) {
						relayUrl = json.get("relayUrl").getAsString();
					}
					if (json.has("token")) {
						token = json.get("token").getAsString();
					}
				}
			} else {
				save();
			}
		} catch (IOException | RuntimeException e) {
			EmoteClientMod.LOGGER.warn("Konfiguration konnte nicht gelesen werden, nutze Standardwerte", e);
		}
	}

	private static void save() throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("relayUrl", relayUrl);
		json.addProperty("token", token);
		Files.writeString(path(), GSON.toJson(json));
	}
}
