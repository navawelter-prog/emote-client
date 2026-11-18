package dev.nava.emoteclient.emote;

/**
 * Ein Emote: id, Dauer in Sekunden (0 = laeuft in Schleife bis zum Abbruch) und die Posenfunktion.
 */
public record Emote(String id, float durationSeconds, PoseFunction function) {

	@FunctionalInterface
	public interface PoseFunction {
		/** @param t Sekunden seit Start des Emotes */
		void apply(Pose pose, float t);
	}
}
