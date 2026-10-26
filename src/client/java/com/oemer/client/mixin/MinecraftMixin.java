package com.oemer.client.mixin;

import com.oemer.client.feature.Freecam;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	// In der Free Cam nicht angreifen
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void oemer$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (Freecam.isEnabled()) {
			cir.setReturnValue(false);
		}
	}

	// In der Free Cam nicht abbauen
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void oemer$noMining(CallbackInfo ci) {
		if (Freecam.isEnabled()) {
			ci.cancel();
		}
	}

	// In der Free Cam keinen Block mit Mausrad-Klick nehmen
	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void oemer$noPick(CallbackInfo ci) {
		if (Freecam.isEnabled()) {
			ci.cancel();
		}
	}

	// Server verlassen = Free Cam aus
	@Inject(method = "disconnect*", at = @At("HEAD"))
	private void oemer$disconnect(CallbackInfo ci) {
		Freecam.forceOff();
	}
}
