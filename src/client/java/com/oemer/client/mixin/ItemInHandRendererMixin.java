package com.oemer.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerConfig;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	@Unique
	private boolean oemer$pushed;

	// Low Shield (Schild tiefer) und Smaller Totem (Totem in der Hand kleiner) – nur in der Ich-Sicht
	@Inject(method = "renderItem", at = @At("HEAD"), require = 0)
	private void oemer$handStart(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose,
								 SubmitNodeCollector collector, int light, CallbackInfo ci) {
		oemer$pushed = false;
		if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
		OemerConfig c = OemerConfig.get();
		if (c.lowShield && stack.is(Items.SHIELD)) {
			pose.pushPose();
			pose.translate(0f, -OemerConfig.LOW_AMOUNTS[c.lowShieldLevel], 0f);
			oemer$pushed = true;
		} else if (c.smallerTotem && stack.is(Items.TOTEM_OF_UNDYING)) {
			float s = OemerConfig.SMALL_SCALES[c.smallerTotemLevel];
			pose.pushPose();
			pose.scale(s, s, s);
			oemer$pushed = true;
		}
	}

	@Inject(method = "renderItem", at = @At("RETURN"), require = 0)
	private void oemer$handEnd(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose,
							   SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (oemer$pushed) {
			pose.popPose();
			oemer$pushed = false;
		}
	}
}
