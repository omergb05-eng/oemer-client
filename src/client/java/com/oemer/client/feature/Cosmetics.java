package com.oemer.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Hängt deine ausgerüsteten Bild-Cosmetics an deinen Spieler (nur du siehst sie). */
public final class Cosmetics {
	private Cosmetics() {
	}

	public static class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		public Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || state.id != mc.player.getId() || state.isSpectator || !OemerConfig.get().cosmeticsOn) return;
			if (!SpriteCosmetics.anyEquipped()) return;
			float t = (System.currentTimeMillis() % 100_000L) / 1000f;
			SpriteCosmetics.submit(pose, collector, light, getParentModel(), t);
		}
	}
}
