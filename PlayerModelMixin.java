package dev.nava.emoteclient.mixin;

import dev.nava.emoteclient.EmoteStateHolder;
import dev.nava.emoteclient.emote.Pose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelMixin {
	/**
	 * Nach der normalen Animation ueberschreiben wir die Drehungen der Teile, die das Emote steuert.
	 * Aermel, Hosen und Hut hängen als Kindteile an den Hauptteilen und drehen automatisch mit.
	 */
	@Inject(
			method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
			at = @At("TAIL"))
	private void emoteclient$applyEmote(AvatarRenderState state, CallbackInfo ci) {
		Pose pose = ((EmoteStateHolder) (Object) state).emoteclient$getPose();
		if (pose == null) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		emoteclient$rotate(model.head, pose, Pose.HEAD);
		emoteclient$rotate(model.body, pose, Pose.BODY);
		emoteclient$rotate(model.rightArm, pose, Pose.RIGHT_ARM);
		emoteclient$rotate(model.leftArm, pose, Pose.LEFT_ARM);
		emoteclient$rotate(model.rightLeg, pose, Pose.RIGHT_LEG);
		emoteclient$rotate(model.leftLeg, pose, Pose.LEFT_LEG);
	}

	private static void emoteclient$rotate(ModelPart part, Pose pose, int index) {
		if (pose.used[index]) {
			part.xRot = pose.rot[index][0];
			part.yRot = pose.rot[index][1];
			part.zRot = pose.rot[index][2];
		}
	}
}
