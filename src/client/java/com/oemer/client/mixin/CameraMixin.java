package com.oemer.client.mixin;

import com.oemer.client.feature.FreeCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.oemer.client.feature.Zoom;

@Mixin(Camera.class)
public class CameraMixin {
	@Shadow private Entity entity;
	@Shadow private float eyeHeightOld;
	@Shadow private float eyeHeight;

	// Beim An/Aus kein Ruckeln der Augenhöhe
	@Inject(method = "update", at = @At("HEAD"))
	private void oemer$setup(DeltaTracker tracker, CallbackInfo ci) {
		Entity newEntity = Minecraft.getInstance().getCameraEntity();
		if (newEntity == null || this.entity == null || newEntity.equals(this.entity)) {
			return;
		}
		if (newEntity instanceof FreeCamera || this.entity instanceof FreeCamera) {
			this.eyeHeightOld = this.eyeHeight = newEntity.getEyeHeight();
		}
	}

	// Just Zoom: Sichtfeld kleiner machen
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void oemer$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		double f = Zoom.update();
		if (f > 1.001) {
			cir.setReturnValue((float) (cir.getReturnValue() / f));
		}
	}
}
