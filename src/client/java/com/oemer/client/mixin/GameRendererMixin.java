package com.oemer.client.mixin;

import com.oemer.client.feature.Freecam;
import com.oemer.client.feature.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	// In der Free Cam und beim Zoomen keine Hand im Bild
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void oemer$hideHand(CallbackInfo ci) {
		if (Freecam.isEnabled() || Zoom.factor() > 1.05) {
			ci.cancel();
		}
	}

	// Kein Block-Rahmen, weil man in der Free Cam nichts abbaut
	@Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
	private void oemer$noOutline(CallbackInfoReturnable<Boolean> cir) {
		if (Freecam.isEnabled()) {
			cir.setReturnValue(false);
		}
	}

	// Just Zoom: Sichtfeld kleiner machen
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void oemer$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (!useFovSetting) return;
		double f = Zoom.update();
		if (f > 1.001) {
			cir.setReturnValue((float) (cir.getReturnValue() / f));
		}
	}
}
