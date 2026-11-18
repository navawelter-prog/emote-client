package dev.nava.emoteclient.gui;

import java.util.ArrayList;
import java.util.List;

import dev.nava.emoteclient.EmoteClientMod;
import dev.nava.emoteclient.emote.Emote;
import dev.nava.emoteclient.emote.Emotes;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Einfaches Emote-Menue: ein Knopf pro Emote und unten "Emote beenden".
 */
public class EmoteScreen extends Screen {
	private static final int COLUMNS = 3;
	private static final int BUTTON_W = 96;
	private static final int BUTTON_H = 20;
	private static final int GAP = 6;

	public EmoteScreen() {
		super(Component.translatable("screen.emoteclient.title"));
	}

	@Override
	protected void init() {
		List<Emote> emotes = new ArrayList<>(Emotes.all());
		int rows = (emotes.size() + COLUMNS - 1) / COLUMNS;
		int totalW = COLUMNS * BUTTON_W + (COLUMNS - 1) * GAP;
		int startX = (this.width - totalW) / 2;
		int totalH = (rows + 1) * (BUTTON_H + GAP);
		int startY = (this.height - totalH) / 2;

		for (int i = 0; i < emotes.size(); i++) {
			Emote emote = emotes.get(i);
			int x = startX + (i % COLUMNS) * (BUTTON_W + GAP);
			int y = startY + (i / COLUMNS) * (BUTTON_H + GAP);
			this.addRenderableWidget(Button.builder(
					Component.translatable("emote." + EmoteClientMod.MOD_ID + "." + emote.id()),
					button -> {
						EmoteClientMod.playLocal(emote.id());
						this.onClose();
					})
					.bounds(x, y, BUTTON_W, BUTTON_H)
					.build());
		}

		this.addRenderableWidget(Button.builder(
				Component.translatable("screen.emoteclient.stop"),
				button -> {
					EmoteClientMod.stopLocal();
					this.onClose();
				})
				.bounds(startX, startY + rows * (BUTTON_H + GAP), totalW, BUTTON_H)
				.build());
	}
}
