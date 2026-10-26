package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import com.oemer.client.gui.OmerLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dark GUI: die dunkelgrauen Überschriften in Kisten/Inventar werden hell, damit man sie lesen kann. */
@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsMixin {
	@ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
	private int oemer$lightLabels(int color, Font font, Component text, int x, int y) {
		if (OemerConfig.get().darkGui && (color & 0xFFFFFF) == 0x404040
				&& Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>) {
			return 0xFFE2E2E8;
		}
		return color;
	}

	// Ömer-Ladescreen: solange das Mojang-Logo gezeichnet würde, nichts malen
	@Inject(method = "innerFill(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;IIIIILjava/lang/Integer;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$noRect(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline, net.minecraft.client.gui.render.TextureSetup setup,
			int a, int b, int c, int d, int e, Integer f, CallbackInfo ci) {
		if (OmerLoading.suppress) ci.cancel();
	}

	@Inject(method = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;IIIIFFFFI)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$noBlit(com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline, com.mojang.renderpearl.api.textures.GpuTextureView view,
			com.mojang.renderpearl.api.textures.GpuSampler sampler, int a, int b, int c, int d, float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
		if (OmerLoading.suppress) ci.cancel();
	}
}
