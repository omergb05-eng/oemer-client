package com.oemer.client.mixin;

import com.oemer.client.gui.OmerLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Terrain wird geladen" beim Betreten einer Welt / Dimension → Ömer-Ladescreen. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	@Shadow
	private LevelLoadTracker loadTracker;

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$render(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
		if (!OmerLoading.enabled()) return;
		float p = -1f;
		boolean ready = false;
		try {
			if (loadTracker != null) {
				if (loadTracker.hasProgress()) p = loadTracker.serverProgress();
				ready = loadTracker.isLevelReady();
			}
		} catch (Throwable ignored) {
		}
		Minecraft mc = Minecraft.getInstance();
		String text = ready ? "Gleich geht's los!" : (mc.level != null ? "Dimension wird geladen" : "Welt wird geladen");
		OmerLoading.levelFrames++;
		OmerLoading.draw(g, 1f, ready ? 1f : p, text, "level");
		ci.cancel();
	}
}
