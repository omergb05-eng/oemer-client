package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Dark GUI: Überschriften in Kisten/Inventar hell, damit man sie auf dunkel lesen kann. */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@ModifyArg(method = "renderLabels", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
			index = 4, require = 0)
	private int oemer$lightLabels(int color) {
		if (OemerConfig.get().darkGui && (color & 0xFFFFFF) == 0x404040) return 0xFFE2E2E8;
		return color;
	}
}
