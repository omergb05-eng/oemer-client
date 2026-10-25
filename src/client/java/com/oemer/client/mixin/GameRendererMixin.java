package com.oemer.client.mixin;

import com.oemer.client.feature.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	// In der Free Cam keine Hand im Bild
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void oemer$hideHand(CallbackInfo ci) {
		if (Freecam.isEnabled()) {
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
}
