package dev.nava.emoteclient.mixin;

import dev.nava.emoteclient.EmoteStateHolder;
import dev.nava.emoteclient.emote.Pose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements EmoteStateHolder {
	@Unique
	private Pose emoteclient$pose;

	@Override
	public Pose emoteclient$getPose() {
		return this.emoteclient$pose;
	}

	@Override
	public void emoteclient$setPose(Pose pose) {
		this.emoteclient$pose = pose;
	}
}
