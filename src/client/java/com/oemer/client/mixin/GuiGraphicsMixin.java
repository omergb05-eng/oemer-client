package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dark GUI: die dunkelgrauen Überschriften in Kisten/Inventar werden hell, damit man sie lesen kann. */
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {
	@ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
	private int oemer$lightLabels(int color, Font font, Component text, int x, int y) {
		if (OemerConfig.get().darkGui && (color & 0xFFFFFF) == 0x404040
				&& Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>) {
			return 0xFFE2E2E8;
		}
		return color;
	}
}
