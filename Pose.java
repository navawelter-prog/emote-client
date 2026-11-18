package dev.nava.emoteclient.emote;

/**
 * Drehwerte (Radiant) fuer die Koerperteile des Spielermodells.
 * Nur Teile, die per set() gesetzt wurden, ueberschreiben die normale Animation.
 */
public final class Pose {
	public static final int HEAD = 0;
	public static final int BODY = 1;
	public static final int RIGHT_ARM = 2;
	public static final int LEFT_ARM = 3;
	public static final int RIGHT_LEG = 4;
	public static final int LEFT_LEG = 5;
	public static final int PART_COUNT = 6;

	public final float[][] rot = new float[PART_COUNT][3];
	public final boolean[] used = new boolean[PART_COUNT];

	public void set(int part, float x, float y, float z) {
		rot[part][0] = x;
		rot[part][1] = y;
		rot[part][2] = z;
		used[part] = true;
	}
}
