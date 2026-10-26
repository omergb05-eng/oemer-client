package com.oemer.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oemer.client.gui.OmerLoading;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Der Start-Ladebildschirm (Mojang-Logo) wird durch den Ömer-Ladescreen ersetzt. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	private float currentProgress;
	@Shadow
	private long fadeOutStart;
	@Shadow
	private long fadeInStart;
	@Shadow
	@Final
	private boolean fadeIn;

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), require = 0)
	private void oemer$begin(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
		OmerLoading.suppress = OmerLoading.enabled() && !fadeIn;
	}

	// Der Bildschirm darunter (Titelbild beim Ausblenden) soll normal gezeichnet werden
	@WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"), require = 0)
	private void oemer$screenBelow(Screen screen, GuiGraphicsExtractor g, int mx, int my, float pt, Operation<Void> original) {
		boolean was = OmerLoading.suppress;
		OmerLoading.suppress = false;
		try {
			original.call(screen, g, mx, my, pt);
		} finally {
			OmerLoading.suppress = was;
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("RETURN"), require = 0)
	private void oemer$end(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
		OmerLoading.suppress = false;
		if (!OmerLoading.enabled() || fadeIn) return;
		long now = Util.getMillis();
		float f = fadeOutStart > -1L ? (now - fadeOutStart) / 1000f : -1f;
		float a = f >= 1f ? 1f - Math.min(1f, f - 1f) : 1f;
		OmerLoading.overlayFrames++;
		OmerLoading.draw(g, a, currentProgress, f >= 0 ? "Fertig!" : "Ressourcen werden geladen", "overlay");
	}
}
