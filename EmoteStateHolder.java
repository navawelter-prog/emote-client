package dev.nava.emoteclient;

import dev.nava.emoteclient.emote.Pose;

/**
 * Wird per Mixin an den AvatarRenderState gehaengt, damit die Pose vom Extrahieren
 * der Renderdaten bis zum Animieren des Modells mitgenommen wird.
 */
public interface EmoteStateHolder {
	Pose emoteclient$getPose();

	void emoteclient$setPose(Pose pose);
}
