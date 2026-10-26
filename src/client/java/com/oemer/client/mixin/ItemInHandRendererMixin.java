package com.oemer.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerConfig;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Low Shield (Schild tiefer) und Smaller Totem (Totem in der Hand kleiner) – nur in der Ich-Sicht. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {
	@WrapOperation(method = "submitArmWithItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
			require = 0)
	private void oemer$handItem(ItemStackRenderState state, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline,
								Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
		OemerConfig c = OemerConfig.get();
		if (c.lowShield && stack.is(Items.SHIELD)) {
			pose.pushPose();
			pose.translate(0f, -OemerConfig.LOW_AMOUNTS[c.lowShieldLevel], 0f);
			original.call(state, pose, collector, light, overlay, outline);
			pose.popPose();
		} else if (c.smallerTotem && stack.is(Items.TOTEM_OF_UNDYING)) {
			float s = OemerConfig.SMALL_SCALES[c.smallerTotemLevel];
			pose.pushPose();
			pose.scale(s, s, s);
			original.call(state, pose, collector, light, overlay, outline);
			pose.popPose();
		} else {
			original.call(state, pose, collector, light, overlay, outline);
		}
	}
}
