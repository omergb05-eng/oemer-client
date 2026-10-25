package com.oemer.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
	@Unique
	private static float oemer$fireShift;
	@Unique
	private float oemer$totemScale = 1f;

	// Low Fire: Feuer am Bildschirm tiefer
	@Inject(method = "renderFire", at = @At("HEAD"), require = 0)
	private static void oemer$lowFireStart(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		OemerConfig c = OemerConfig.get();
		oemer$fireShift = c.lowFire ? OemerConfig.LOW_AMOUNTS[c.lowFireLevel] * 1.6f : 0f;
		if (oemer$fireShift != 0f) pose.translate(0f, -oemer$fireShift, 0f);
	}

	@Inject(method = "renderFire", at = @At("RETURN"), require = 0)
	private static void oemer$lowFireEnd(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (oemer$fireShift != 0f) pose.translate(0f, oemer$fireShift, 0f);
		oemer$fireShift = 0f;
	}

	// Smaller Totem: kleinere Totem-Animation
	@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), require = 0)
	private void oemer$smallTotemStart(PoseStack pose, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
		OemerConfig c = OemerConfig.get();
		oemer$totemScale = c.smallerTotem ? OemerConfig.SMALL_SCALES[c.smallerTotemLevel] : 1f;
		if (oemer$totemScale != 1f) pose.scale(oemer$totemScale, oemer$totemScale, oemer$totemScale);
	}

	@Inject(method = "renderItemActivationAnimation", at = @At("RETURN"), require = 0)
	private void oemer$smallTotemEnd(PoseStack pose, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
		if (oemer$totemScale != 1f) {
			float s = 1f / oemer$totemScale;
			pose.scale(s, s, s);
		}
		oemer$totemScale = 1f;
	}
}
