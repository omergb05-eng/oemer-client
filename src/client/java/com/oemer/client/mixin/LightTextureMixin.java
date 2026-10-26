package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: dreht die Helligkeit fürs Licht ganz hoch, ohne deine echte Einstellung zu ändern. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightTextureMixin {
	@Shadow
	private boolean needsUpdate;

	@Unique
	private boolean oemer$last;

	@Inject(method = "extract", at = @At("HEAD"), require = 0)
	private void oemer$check(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		boolean fb = OemerConfig.get().fullbright;
		if (fb != oemer$last) {
			oemer$last = fb;
			this.needsUpdate = true;
		}
	}

	@Inject(method = "extract", at = @At("TAIL"), require = 0)
	private void oemer$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (OemerConfig.get().fullbright) state.brightness = 16.0f;
	}
}
