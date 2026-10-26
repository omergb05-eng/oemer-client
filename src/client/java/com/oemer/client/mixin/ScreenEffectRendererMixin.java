package com.oemer.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
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
	private static float oemer$fireScale = 1f;
	@Unique
	private float oemer$totemScale = 1f;

	// Low Fire: Feuer am Bildschirm tiefer und kleiner
	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true, require = 0)
	private static void oemer$lowFireStart(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		OemerConfig c = OemerConfig.get();
		oemer$fireShift = 0f;
		oemer$fireScale = 1f;
		if (c.lowFire && OemerConfig.LOW_FIRE_SCALES[c.lowFireLevel] <= 0f) {
			ci.cancel(); // "Fast unsichtbar": gar kein Feuer am Bildschirm
			return;
		}
		oemer$fireShift = c.lowFire ? OemerConfig.LOW_FIRE_SHIFT[c.lowFireLevel] : 0f;
		oemer$fireScale = c.lowFire ? OemerConfig.LOW_FIRE_SCALES[c.lowFireLevel] : 1f;
		if (oemer$fireShift != 0f) pose.translate(0f, -oemer$fireShift, 0f);
		if (oemer$fireScale != 1f) pose.scale(oemer$fireScale, oemer$fireScale, 1f);
	}

	@Inject(method = "submitFire", at = @At("RETURN"), require = 0)
	private static void oemer$lowFireEnd(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (oemer$fireScale != 1f) pose.scale(1f / oemer$fireScale, 1f / oemer$fireScale, 1f);
		if (oemer$fireShift != 0f) pose.translate(0f, oemer$fireShift, 0f);
		oemer$fireShift = 0f;
		oemer$fireScale = 1f;
	}

	// Smaller Totem: kleinere Totem-Animation
	@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), require = 0)
	private void oemer$smallTotemStart(PlayerRenderState player, PoseStack pose, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
		OemerConfig c = OemerConfig.get();
		oemer$totemScale = c.smallerTotem ? OemerConfig.SMALL_SCALES[c.smallerTotemLevel] : 1f;
		if (oemer$totemScale != 1f) pose.scale(oemer$totemScale, oemer$totemScale, oemer$totemScale);
	}

	@Inject(method = "renderItemActivationAnimation", at = @At("RETURN"), require = 0)
	private void oemer$smallTotemEnd(PlayerRenderState player, PoseStack pose, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
		if (oemer$totemScale != 1f) {
			float s = 1f / oemer$totemScale;
			pose.scale(s, s, s);
		}
		oemer$totemScale = 1f;
	}
}
