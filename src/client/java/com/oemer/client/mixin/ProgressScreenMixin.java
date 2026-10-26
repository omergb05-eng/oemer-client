package com.oemer.client.mixin;

import com.oemer.client.gui.OmerLoading;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Welt wird gespeichert / geladen …" im Ömer-Look. */
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin {
	@Shadow
	private Component header;
	@Shadow
	private Component stage;
	@Shadow
	private int progress;

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$render(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
		if (!OmerLoading.enabled()) return;
		String text = stage != null ? stage.getString() : header != null ? header.getString() : "Bitte warten …";
		if (text.isBlank()) text = header != null ? header.getString() : "Bitte warten …";
		OmerLoading.progressFrames++;
		OmerLoading.draw(g, 1f, progress > 0 ? progress / 100f : -1f, text, "progress");
		ci.cancel();
	}
}
