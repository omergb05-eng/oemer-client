package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {
	// No Pumpkin Blur: kein Kürbis-Rand, wenn du einen Kürbis trägst
	@Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$noPumpkin(GuiGraphicsExtractor g, Identifier texture, float alpha, CallbackInfo ci) {
		if (OemerConfig.get().noPumpkin && texture.getPath().contains("pumpkinblur")) ci.cancel();
	}

	// Transparente Netherportale: kein lila Wackel-Bildschirm im Portal
	@Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$noPortalOverlay(GuiGraphicsExtractor g, float partial, CallbackInfo ci) {
		OemerConfig c = OemerConfig.get();
		if (c.clearPortal && c.portalOverlayOff) ci.cancel();
	}
}
