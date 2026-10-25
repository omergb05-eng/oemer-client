package com.oemer.client.mixin;

import com.oemer.client.feature.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow private double accumulatedDX;
	@Shadow private double accumulatedDY;

	// Beim Zoomen dreht sich die Kamera langsamer (sonst kaum zielbar)
	@Inject(method = "turnPlayer", at = @At("HEAD"), require = 0)
	private void oemer$zoomSensitivity(double movementTime, CallbackInfo ci) {
		double f = Zoom.factor();
		if (f > 1.01) {
			this.accumulatedDX /= f;
			this.accumulatedDY /= f;
		}
	}

	// Mausrad beim Zoomen = näher/weiter
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$zoomScroll(long window, double x, double y, CallbackInfo ci) {
		if (Zoom.scroll(y)) ci.cancel();
	}
}
