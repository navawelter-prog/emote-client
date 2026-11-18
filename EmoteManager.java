package dev.nava.emoteclient.emote;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merkt sich, welcher Spieler gerade welches Emote spielt, und berechnet die aktuelle Pose.
 */
public final class EmoteManager {
	private record Active(Emote emote, long startMillis) {
	}

	private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

	private EmoteManager() {
	}

	/** @return true, wenn es das Emote gibt und es gestartet wurde */
	public static boolean start(UUID player, String emoteId) {
		Emote emote = Emotes.get(emoteId);
		if (emote == null) {
			return false;
		}
		ACTIVE.put(player, new Active(emote, System.currentTimeMillis()));
		return true;
	}

	public static void stop(UUID player) {
		ACTIVE.remove(player);
	}

	public static void clear() {
		ACTIVE.clear();
	}

	public static boolean isActive(UUID player) {
		return ACTIVE.containsKey(player);
	}

	/** @return die Pose zum aktuellen Zeitpunkt oder null, wenn kein Emote laeuft */
	public static Pose poseFor(UUID player) {
		Active active = ACTIVE.get(player);
		if (active == null) {
			return null;
		}
		float t = (System.currentTimeMillis() - active.startMillis()) / 1000f;
		float duration = active.emote().durationSeconds();
		if (duration > 0f && t > duration) {
			ACTIVE.remove(player, active);
			return null;
		}
		Pose pose = new Pose();
		active.emote().function().apply(pose, t);
		return pose;
	}
}
