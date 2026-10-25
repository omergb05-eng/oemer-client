package com.oemer.client.mixin;

import com.oemer.client.feature.FreeCamera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	// Die Kamera selbst ist unsichtbar
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void oemer$hideCamera(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof FreeCamera) {
			cir.setReturnValue(false);
		}
	}
}
