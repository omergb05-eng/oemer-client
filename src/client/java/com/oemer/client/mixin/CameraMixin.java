package com.oemer.client.mixin;

import com.oemer.client.feature.FreeCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {
	@Shadow private Entity entity;
	@Shadow private float eyeHeightOld;
	@Shadow private float eyeHeight;

	// Beim An/Aus kein Ruckeln der Augenhöhe
	@Inject(method = "setup", at = @At("HEAD"))
	private void oemer$setup(Level level, Entity newEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
		if (newEntity == null || this.entity == null || newEntity.equals(this.entity)) {
			return;
		}
		if (newEntity instanceof FreeCamera || this.entity instanceof FreeCamera) {
			this.eyeHeightOld = this.eyeHeight = newEntity.getEyeHeight();
		}
	}
}
