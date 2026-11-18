package dev.nava.emoteclient;

import java.util.Locale;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nava.emoteclient.emote.EmoteManager;
import dev.nava.emoteclient.emote.Emotes;
import dev.nava.emoteclient.gui.EmoteScreen;
import dev.nava.emoteclient.net.RelayClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmoteClientMod implements ClientModInitializer {
	public static final String MOD_ID = "emoteclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final RelayClient RELAY = new RelayClient();

	private static KeyMapping openMenuKey;
	/** Position beim Start des eigenen Emotes. Bewegt man sich weg, wird das Emote beendet. */
	private static Vec3 localStartPos;

	@Override
	public void onInitializeClient() {
		EmoteConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key." + MOD_ID + ".open",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(EmoteClientMod::onTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> connectRelay(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			RELAY.stop();
			EmoteManager.clear();
			localStartPos = null;
		});
	}

	private static void connectRelay(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		// Alle Spieler auf demselben Server landen im selben "Raum" des Relays
		String room = server != null ? server.ip.toLowerCase(Locale.ROOT) : "singleplayer";
		RELAY.start(EmoteConfig.relayUrl, EmoteConfig.token,
				mc.getUser().getProfileId(), mc.getUser().getName(), room);
	}

	private static void onTick(Minecraft mc) {
		while (openMenuKey.consumeClick()) {
			if (mc.player != null && mc.screen == null) {
				mc.setScreen(new EmoteScreen());
			}
		}

		if (mc.player != null && localStartPos != null) {
			UUID id = mc.player.getUUID();
			if (!EmoteManager.isActive(id)) {
				localStartPos = null;
			} else if (mc.player.position().distanceToSqr(localStartPos) > 0.04) {
				stopLocal();
			}
		}
	}

	public static void playLocal(String emoteId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || Emotes.get(emoteId) == null) {
			return;
		}
		if (EmoteManager.start(mc.player.getUUID(), emoteId)) {
			localStartPos = mc.player.position();
			RELAY.sendEmote(emoteId);
		}
	}

	public static void stopLocal() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		EmoteManager.stop(mc.player.getUUID());
		localStartPos = null;
		RELAY.sendStop();
	}
}
