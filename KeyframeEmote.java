package dev.nava.emoteclient.emote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Laedt ein Emote aus einer .emote-Textdatei (Keyframes) und spielt es mit linearer Interpolation ab.
 *
 * Format: Kommentarzeilen mit '#', "fps N", "loop 0|1", danach pro Zeile ein Frame mit 18 Zahlen:
 * head, body, right_arm, left_arm, right_leg, left_leg (je x y z in Radiant).
 */
public final class KeyframeEmote {
	private KeyframeEmote() {
	}

	public static Emote load(String id, String resourcePath) throws IOException {
		try (InputStream in = KeyframeEmote.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new IOException("Emote-Datei nicht gefunden: " + resourcePath);
			}
			return parse(id, new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
		}
	}

	static Emote parse(String id, BufferedReader reader) throws IOException {
		float fps = 15f;
		boolean loop = true;
		List<float[]> frames = new ArrayList<>();

		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] parts = line.split("\\s+");
			if (parts[0].equals("fps")) {
				fps = Float.parseFloat(parts[1]);
			} else if (parts[0].equals("loop")) {
				loop = parts[1].equals("1");
			} else {
				if (parts.length != Pose.PART_COUNT * 3) {
					throw new IOException("Falsche Zahl an Werten in einer Frame-Zeile: " + parts.length);
				}
				float[] values = new float[parts.length];
				for (int i = 0; i < parts.length; i++) {
					values[i] = Float.parseFloat(parts[i]);
				}
				frames.add(values);
			}
		}
		if (frames.isEmpty() || fps <= 0f) {
			throw new IOException("Emote-Datei ohne Frames oder mit ungueltiger fps");
		}

		final float framesPerSecond = fps;
		final boolean looping = loop;
		final int count = frames.size();
		final float[][] data = frames.toArray(new float[0][]);
		float duration = looping ? 0f : count / framesPerSecond;

		return new Emote(id, duration, (pose, t) -> {
			float position = t * framesPerSecond;
			int i0;
			float frac;
			if (looping) {
				position = position % count;
				i0 = (int) position;
				frac = position - i0;
			} else {
				position = Math.min(position, count - 1);
				i0 = (int) position;
				frac = position - i0;
			}
			int i1 = looping ? (i0 + 1) % count : Math.min(i0 + 1, count - 1);
			float[] a = data[i0];
			float[] b = data[i1];
			for (int part = 0; part < Pose.PART_COUNT; part++) {
				int o = part * 3;
				pose.set(part,
						a[o] + (b[o] - a[o]) * frac,
						a[o + 1] + (b[o + 1] - a[o + 1]) * frac,
						a[o + 2] + (b[o + 2] - a[o + 2]) * frac);
			}
		});
	}
}
