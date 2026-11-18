package dev.nava.emoteclient.mixin;

import dev.nava.emoteclient.EmoteStateHolder;
import dev.nava.emoteclient.emote.EmoteManager;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	/**
	 * Beim Sammeln der Renderdaten merken wir uns, ob dieser Spieler gerade ein Emote spielt.
	 * Die Beschreibung steht als voller Descriptor da, weil der Parametertyp (Generic) zu Avatar geloescht wird.
	 */
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void emoteclient$extract(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		((EmoteStateHolder) (Object) state).emoteclient$setPose(EmoteManager.poseFor(avatar.getUUID()));
	}
}
