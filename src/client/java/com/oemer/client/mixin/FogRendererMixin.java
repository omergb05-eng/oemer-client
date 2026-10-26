package com.oemer.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.oemer.client.OemerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;


/** No Fog: kein Nebel an Land (unter Wasser, in Lava und bei Blindheit bleibt er). */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"), require = 0)
	private void oemer$noFog(Camera camera, int renderDistance, DeltaTracker tracker, float partial, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		FogData fog = cir.getReturnValue();
		if (fog != null && OemerConfig.get().noFog && oemer$clearAir()) {
			fog.environmentalStart = 1.0e7f;
			fog.environmentalEnd = 2.0e7f;
			fog.renderDistanceStart = 1.0e7f;
			fog.renderDistanceEnd = 2.0e7f;
		}
	}

	@Unique
	private static boolean oemer$clearAir() {
		var p = Minecraft.getInstance().player;
		if (p == null) return true;
		return !p.isEyeInFluid(FluidTags.WATER) && !p.isEyeInFluid(FluidTags.LAVA)
				&& !p.hasEffect(MobEffects.BLINDNESS) && !p.hasEffect(MobEffects.DARKNESS);
	}
}
