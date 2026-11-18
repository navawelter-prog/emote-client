package dev.nava.emoteclient.emote;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.nava.emoteclient.emote.Pose.*;

/**
 * Eingebaute Emotes. Die Werte sind Startwerte zum Ausprobieren und Feintunen.
 * Neues Emote: einfach unten mit register(...) hinzufuegen und die Uebersetzung in die lang-Dateien eintragen.
 */
public final class Emotes {
	private static final Map<String, Emote> MAP = new LinkedHashMap<>();

	static {
		// Der Tanz aus dem Video (Keyframes in assets/emoteclient/emotes/dance.emote)
		try {
			MAP.put("dance", KeyframeEmote.load("dance", "/assets/emoteclient/emotes/dance.emote"));
		} catch (java.io.IOException | RuntimeException e) {
			System.err.println("[emoteclient] Tanz konnte nicht geladen werden: " + e.getMessage());
		}

		register("wave", 3f, (p, t) -> {
			float s = (float) Math.sin(t * 9f);
			p.set(RIGHT_ARM, -2.7f, 0f, 0.35f + 0.3f * s);
			p.set(HEAD, 0f, 0f, 0.08f);
		});

		register("groove", 0f, (p, t) -> {
			float a = (float) Math.sin(t * 5f);
			float b = (float) Math.sin(t * 10f);
			p.set(BODY, 0f, 0.35f * a, 0f);
			p.set(HEAD, 0.1f * b, -0.3f * a, 0.15f * a);
			p.set(RIGHT_ARM, -2.6f + 0.5f * b, 0f, 0.5f + 0.3f * a);
			p.set(LEFT_ARM, -2.6f - 0.5f * b, 0f, -0.5f + 0.3f * a);
			p.set(RIGHT_LEG, 0.5f * a, 0f, 0.05f);
			p.set(LEFT_LEG, -0.5f * a, 0f, -0.05f);
		});

		register("clap", 3f, (p, t) -> {
			float k = Math.abs((float) Math.sin(t * 10f)) * 0.45f;
			p.set(RIGHT_ARM, -1.3f, -0.6f + k, 0f);
			p.set(LEFT_ARM, -1.3f, 0.6f - k, 0f);
		});

		register("cheer", 3f, (p, t) -> {
			float s = (float) Math.sin(t * 8f);
			p.set(RIGHT_ARM, -2.9f, 0f, 0.35f + 0.15f * s);
			p.set(LEFT_ARM, -2.9f, 0f, -0.35f - 0.15f * s);
			p.set(HEAD, -0.25f, 0f, 0f);
			p.set(RIGHT_LEG, 0.1f * s, 0f, 0.1f);
			p.set(LEFT_LEG, -0.1f * s, 0f, -0.1f);
		});

		register("bow", 2.5f, (p, t) -> {
			float theta = 0.9f * (float) Math.sin(Math.PI * Math.min(t, 2.5f) / 2.5f);
			p.set(BODY, theta, 0f, 0f);
			p.set(HEAD, theta, 0f, 0f);
			p.set(RIGHT_ARM, theta, 0f, 0f);
			p.set(LEFT_ARM, theta, 0f, 0f);
		});

		register("shrug", 2.5f, (p, t) -> {
			float s = (float) Math.sin(t * 6f) * 0.08f;
			p.set(RIGHT_ARM, -0.4f, 0f, 0.9f + s);
			p.set(LEFT_ARM, -0.4f, 0f, -0.9f - s);
			p.set(HEAD, 0f, 0f, 0.12f);
		});
	}

	private Emotes() {
	}

	private static void register(String id, float duration, Emote.PoseFunction fn) {
		MAP.put(id, new Emote(id, duration, fn));
	}

	public static Emote get(String id) {
		return id == null ? null : MAP.get(id);
	}

	public static Collection<Emote> all() {
		return MAP.values();
	}
}
