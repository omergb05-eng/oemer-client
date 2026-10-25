package com.oemer.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oemer.client.OemerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

/** No Fog: kein Nebel an Land (unter Wasser, in Lava und bei Blindheit bleibt er). */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
	@WrapOperation(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
			require = 0)
	private void oemer$noFog(FogRenderer self, ByteBuffer buffer, int offset, Vector4f color, float a, float b, float c, float d,
							 float sky, float cloud, Operation<Void> original) {
		if (OemerConfig.get().noFog && oemer$clearAir()) {
			original.call(self, buffer, offset, color, 1.0e7f, 2.0e7f, 1.0e7f, 2.0e7f, sky, cloud);
			return;
		}
		original.call(self, buffer, offset, color, a, b, c, d, sky, cloud);
	}

	@Unique
	private static boolean oemer$clearAir() {
		var p = Minecraft.getInstance().player;
		if (p == null) return true;
		return !p.isEyeInFluid(FluidTags.WATER) && !p.isEyeInFluid(FluidTags.LAVA)
				&& !p.hasEffect(MobEffects.BLINDNESS) && !p.hasEffect(MobEffects.DARKNESS);
	}
}
