package com.oemer.client.mixin;

import com.oemer.client.feature.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
	// Maus dreht in der Free Cam die Kamera statt den Spieler
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void oemer$turn(double yRot, double xRot, CallbackInfo ci) {
		if (Freecam.isEnabled() && (Object) this == Minecraft.getInstance().player) {
			Freecam.camera().turn(yRot, xRot);
			ci.cancel();
		}
	}

	// Kamera schiebt keine Mobs und wird nicht geschoben
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void oemer$push(Entity other, CallbackInfo ci) {
		if (Freecam.isEnabled() && (other == Freecam.camera() || (Object) this == Freecam.camera())) {
			ci.cancel();
		}
	}
}
