package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.NameShare;
import com.oemer.client.feature.NameStyle;
import com.oemer.client.feature.OmerBadge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
		// Nicht in Menüs: die Spieler-Vorschau (Kits, Inventar) hat keine Pipeline für Namensschilder (text_see_through → Absturz)
		if (NameStyle.active() && OemerConfig.get().nameShowSelf && entity == mc.player && !mc.options.getCameraType().isFirstPerson()
				&& mc.getCameraEntity() == mc.player && mc.gui.screen() == null) {
			cir.setReturnValue(true);
		}
	}

	// Ömer-Symbol eine Zeile über dem Namen (bei dir und bei Freunden mit dem Client)
	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("RETURN"), require = 0)
	private void oemer$badge(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (state.nameTag == null || state.nameTagAttachment == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		if (mc.gui.screen() != null && mc.player != null && state.id == mc.player.getId()) return; // Menü-Vorschau
		Entity e = mc.level.getEntity(state.id);
		if (e == null) return;
		// Eigenes Logo neben dem Namen (du und Freunde mit dem Ömer Client)
		net.minecraft.resources.Identifier logo = NameShare.logoOf(e.getUUID());
		if (logo != null) {
			NameShare.drawLogo(pose, collector, state.nameTagAttachment, camera, mc.font.width(state.nameTag), !state.isDiscrete, logo);
		}
	}

	// Ömer-Symbol (Mond + Haus) direkt vor dem Namen, bei dir und bei Freunden mit dem Client
	@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$badgeInline(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		// In Menüs (Spieler-Vorschau) gar kein Namensschild: dort gibt es kein OIT für text_see_through (Absturz bei R im Kit-Menü)
		if (mc.gui.screen() != null && mc.player != null && state.id == mc.player.getId()) {
			ci.cancel();
			return;
		}
		if (state.nameTag == null || !OmerBadge.enabled()) return;
		if (mc.level == null) return;
		Entity e = mc.level.getEntity(state.id);
		if (e == null || !OmerBadge.isOmer(e.getUUID())) return;
		if (!state.nameTag.getString().startsWith(OmerBadge.GLYPH)) state.nameTag = OmerBadge.prefix(state.nameTag);
		OmerBadge.drawn++;
	}
}
