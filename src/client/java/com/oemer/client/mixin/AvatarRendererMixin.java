package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.NameShare;
import com.oemer.client.feature.NameStyle;
import com.oemer.client.feature.OmerBadge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	// Dein eigenes Namensschild auch in der 3. Person (F5) zeigen
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$showSelf(Avatar entity, double distSq, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (NameStyle.active() && OemerConfig.get().nameShowSelf && entity == mc.player && !mc.options.getCameraType().isFirstPerson()
				&& mc.getCameraEntity() == mc.player) {
			cir.setReturnValue(true);
		}
	}

	// Ömer-Symbol eine Zeile über dem Namen (bei dir und bei Freunden mit dem Client)
	@Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At("RETURN"), require = 0)
	private void oemer$badge(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (state.nameTag == null || state.nameTagAttachment == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity e = mc.level.getEntity(state.id);
		if (e == null) return;
		// Eigenes Logo neben dem Namen (du und Freunde mit dem Ömer Client)
		net.minecraft.resources.Identifier logo = NameShare.logoOf(e.getUUID());
		if (logo != null) {
			NameShare.drawLogo(pose, collector, state.nameTagAttachment, camera, mc.font.width(state.nameTag), !state.isDiscrete, logo);
		}
		if (!OmerBadge.enabled() || !OmerBadge.isOmer(e.getUUID())) return;
		OmerBadge.drawn++;
		int line = state.scoreText != null ? -20 : -10;
		collector.submitNameTag(pose, state.nameTagAttachment, line, OmerBadge.icon(), !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
	}
}
