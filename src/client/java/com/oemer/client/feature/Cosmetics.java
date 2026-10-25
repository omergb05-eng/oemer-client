package com.oemer.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;

/**
 * Cosmetics: kleine 3D-Modelle an deinem eigenen Spieler.
 * Heiligenschein, Krone, Zylinder, Flügel, Hasenohren, Hörner.
 * Nur du siehst sie (Client-Mod).
 */
public final class Cosmetics {
	private Cosmetics() {
	}

	public static final String[] NAMES = {"Heiligenschein", "Krone", "Zylinder", "Flügel", "Hasenohren", "Teufelshörner"};

	private static ModelPart halo, crown, crownGems, hat, hatBand, wingLeft, wingRight, ears, earsInner, horns;

	private static final float PI = (float) Math.PI;

	// ------------------------------------------------------------ Modelle

	private static ModelPart bake(MeshDefinition mesh) {
		return LayerDefinition.create(mesh, 64, 64).bakeRoot();
	}

	private static void build() {
		if (halo != null) return;

		// Heiligenschein: Ring aus 20 kleinen Stücken über dem Kopf
		MeshDefinition m = new MeshDefinition();
		PartDefinition root = m.getRoot();
		int segments = 20;
		float radius = 5.2f;
		for (int i = 0; i < segments; i++) {
			float a = 2 * PI * i / segments;
			root.addOrReplaceChild("s" + i,
					CubeListBuilder.create().addBox(-0.9f, -0.4f, -0.4f, 1.8f, 0.8f, 0.8f),
					PartPose.offsetAndRotation((float) Math.cos(a) * radius, 0, (float) Math.sin(a) * radius, 0, -a + PI / 2, 0));
		}
		halo = bake(m);

		// Krone
		m = new MeshDefinition();
		root = m.getRoot();
		CubeListBuilder band = CubeListBuilder.create()
				.addBox(-4.5f, -10f, -4.5f, 9f, 2f, 1f)
				.addBox(-4.5f, -10f, 3.5f, 9f, 2f, 1f)
				.addBox(-4.5f, -10f, -3.5f, 1f, 2f, 7f)
				.addBox(3.5f, -10f, -3.5f, 1f, 2f, 7f);
		float[][] spikes = {{-4.5f, -4.5f}, {-0.5f, -4.5f}, {3.5f, -4.5f}, {-4.5f, 3.5f}, {-0.5f, 3.5f}, {3.5f, 3.5f},
				{-4.5f, -0.5f}, {3.5f, -0.5f}};
		for (float[] s : spikes) {
			band.addBox(s[0], -12f, s[1], 1f, 2f, 1f);
		}
		root.addOrReplaceChild("band", band, PartPose.ZERO);
		crown = bake(m);
		m = new MeshDefinition();
		m.getRoot().addOrReplaceChild("gems", CubeListBuilder.create()
				.addBox(-0.75f, -9.5f, -4.8f, 1.5f, 1f, 0.5f)
				.addBox(-0.75f, -9.5f, 4.3f, 1.5f, 1f, 0.5f)
				.addBox(-4.8f, -9.5f, -0.75f, 0.5f, 1f, 1.5f)
				.addBox(4.3f, -9.5f, -0.75f, 0.5f, 1f, 1.5f), PartPose.ZERO);
		crownGems = bake(m);

		// Zylinder
		m = new MeshDefinition();
		m.getRoot().addOrReplaceChild("hat", CubeListBuilder.create()
				.addBox(-6f, -9f, -6f, 12f, 1f, 12f)
				.addBox(-4f, -17f, -4f, 8f, 8f, 8f), PartPose.ZERO);
		hat = bake(m);
		m = new MeshDefinition();
		m.getRoot().addOrReplaceChild("band", CubeListBuilder.create()
				.addBox(-4.25f, -11f, -4.25f, 8.5f, 1.5f, 8.5f), PartPose.ZERO);
		hatBand = bake(m);

		// Flügel: drei Federschichten pro Seite
		wingLeft = bake(wingMesh(false));
		wingRight = bake(wingMesh(true));

		// Hasenohren
		m = new MeshDefinition();
		root = m.getRoot();
		root.addOrReplaceChild("l", CubeListBuilder.create().addBox(-1f, -7f, -0.5f, 2f, 7f, 1f),
				PartPose.offsetAndRotation(-2f, -8f, 0f, -0.1f, 0f, -0.18f));
		root.addOrReplaceChild("r", CubeListBuilder.create().addBox(-1f, -7f, -0.5f, 2f, 7f, 1f),
				PartPose.offsetAndRotation(2f, -8f, 0f, -0.1f, 0f, 0.18f));
		ears = bake(m);
		m = new MeshDefinition();
		root = m.getRoot();
		root.addOrReplaceChild("l", CubeListBuilder.create().addBox(-0.5f, -6f, -0.6f, 1f, 5f, 0.2f),
				PartPose.offsetAndRotation(-2f, -8f, 0f, -0.1f, 0f, -0.18f));
		root.addOrReplaceChild("r", CubeListBuilder.create().addBox(-0.5f, -6f, -0.6f, 1f, 5f, 0.2f),
				PartPose.offsetAndRotation(2f, -8f, 0f, -0.1f, 0f, 0.18f));
		earsInner = bake(m);

		// Teufelshörner
		m = new MeshDefinition();
		root = m.getRoot();
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition base = root.addOrReplaceChild(side < 0 ? "l" : "r",
					CubeListBuilder.create().addBox(-1f, -2f, -1f, 2f, 2f, 2f),
					PartPose.offsetAndRotation(side * 2.5f, -8f, -1.5f, 0f, 0f, side * 0.35f));
			PartDefinition mid = base.addOrReplaceChild("m",
					CubeListBuilder.create().addBox(-0.75f, -2f, -0.75f, 1.5f, 2f, 1.5f),
					PartPose.offsetAndRotation(0f, -2f, 0f, 0f, 0f, side * 0.35f));
			mid.addOrReplaceChild("t",
					CubeListBuilder.create().addBox(-0.4f, -1.5f, -0.4f, 0.8f, 1.5f, 0.8f),
					PartPose.offsetAndRotation(0f, -2f, 0f, 0f, 0f, side * 0.4f));
		}
		horns = bake(m);
	}

	private static MeshDefinition wingMesh(boolean right) {
		MeshDefinition m = new MeshDefinition();
		float s = right ? -1 : 1;
		CubeListBuilder b = CubeListBuilder.create();
		if (right) b.mirror();
		// Box-Koordinaten so, dass der Flügel vom Rücken nach außen geht
		float x0 = right ? -11f : 0f;
		b.addBox(x0, -2f, 0f, 11f, 7f, 0.6f);
		b.addBox(right ? -9f : 0f, 5f, 0f, 9f, 4f, 0.6f);
		b.addBox(right ? -6f : 0f, 9f, 0f, 6f, 3f, 0.6f);
		b.addBox(right ? -13f : 2f, -3f, 0.1f, 11f, 2f, 0.6f);
		m.getRoot().addOrReplaceChild("wing", b, PartPose.offset(s * 1f, 1f, 2.2f));
		return m;
	}

	// ----------------------------------------------------------- Texturen

	private static Identifier texture(String slot, int argb) {
		String stamp = Integer.toHexString(argb);
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				// leichter Verlauf + Rauschen, damit es nicht flach aussieht
				float shade = 1.0f - y * 0.012f - ((x * 7 + y * 13) % 5) * 0.012f;
				int r = Math.min(255, Math.round(((argb >> 16) & 255) * shade));
				int g = Math.min(255, Math.round(((argb >> 8) & 255) * shade));
				int b = Math.min(255, Math.round((argb & 255) * shade));
				img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
			}
		}
		// 64x64, damit die UVs der Modelle (Texturgröße 64) überall Farbe treffen
		BufferedImage big = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		var gr = big.createGraphics();
		gr.drawImage(img, 0, 0, 64, 64, null);
		gr.dispose();
		Textures.Tex t = Textures.image(slot, stamp, big);
		return t == null ? null : t.id();
	}

	public static int color() {
		OemerConfig c = OemerConfig.get();
		int col = OemerConfig.COSMETIC_COLORS[Math.floorMod(c.cosColor, OemerConfig.COSMETIC_COLORS.length)];
		return col == 0 ? (Theme.accent() | 0xFF000000) : col;
	}

	public static boolean anyActive() {
		OemerConfig c = OemerConfig.get();
		return c.cosHalo || c.cosCrown || c.cosTopHat || c.cosWings || c.cosEars || c.cosHorns;
	}

	// -------------------------------------------------------------- Layer

	/** Hängt sich an den Spieler-Renderer (wie die Cape-Ebene). */
	public static class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		public Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || state.id != mc.player.getId() || state.isSpectator) {
				return;
			}
			OemerConfig c = OemerConfig.get();
			float t = c.cosAnimate ? (System.currentTimeMillis() % 100_000L) / 1000f : 0f;
			PlayerModel model = getParentModel();
			if (CustomCosmetics.anyActive()) {
				CustomCosmetics.submit(pose, collector, light, model, t);
			}
			if (!anyActive()) {
				return;
			}
			build();
			int col = color();
			Identifier main = texture("cos_main", col);
			if (main == null) return;
			int overlay = OverlayTexture.NO_OVERLAY;

			// ---- alles am Kopf
			if (c.cosHalo || c.cosCrown || c.cosTopHat || c.cosEars || c.cosHorns) {
				pose.pushPose();
				model.head.translateAndRotate(pose);

				if (c.cosHalo) {
					pose.pushPose();
					float bob = c.cosAnimate ? (float) Math.sin(t * 2.0) * 0.4f : 0f;
					pose.translate(0f, (-12.5f + bob) / 16f, 0f);
					halo.yRot = t * 1.2f;
					Identifier glow = texture("cos_halo", haloColor(col));
					collector.submitModelPart(halo, pose, RenderTypes.entityTranslucentEmissive(glow), 0xF000F0, overlay, null);
					pose.popPose();
				}
				// etwas über die Hut-Ebene vom Skin, damit nichts flackert
				pose.translate(0f, -0.5f / 16f, 0f);
				if (c.cosCrown) {
					collector.submitModelPart(crown, pose, RenderTypes.entityCutoutNoCull(main), light, overlay, null);
					Identifier gem = texture("cos_gem", 0xFFE5484D);
					collector.submitModelPart(crownGems, pose, RenderTypes.entityCutoutNoCull(gem), light, overlay, null);
				}
				if (c.cosTopHat) {
					Identifier dark = texture("cos_hat", (col & 0xFFFFFF) == 0x1A1A1A ? 0xFF2A2A30 : 0xFF141418);
					collector.submitModelPart(hat, pose, RenderTypes.entityCutoutNoCull(dark), light, overlay, null);
					collector.submitModelPart(hatBand, pose, RenderTypes.entityCutoutNoCull(main), light, overlay, null);
				}
				if (c.cosEars) {
					collector.submitModelPart(ears, pose, RenderTypes.entityCutoutNoCull(main), light, overlay, null);
					Identifier pink = texture("cos_pink", 0xFFFFB3D1);
					collector.submitModelPart(earsInner, pose, RenderTypes.entityCutoutNoCull(pink), light, overlay, null);
				}
				if (c.cosHorns) {
					Identifier red = texture("cos_horn", (col & 0xFFFFFF) == 0xF4F4F4 ? 0xFFB3262B : col);
					collector.submitModelPart(horns, pose, RenderTypes.entityCutoutNoCull(red), light, overlay, null);
				}
				pose.popPose();
			}

			// ---- Flügel am Rücken
			if (c.cosWings) {
				pose.pushPose();
				model.body.translateAndRotate(pose);
				float flap = c.cosAnimate ? (float) Math.sin(t * 3.0) * 0.18f : 0f;
				float spread = 0.55f + flap;
				wingLeft.getChild("wing").yRot = -spread;
				wingLeft.getChild("wing").zRot = -0.12f;
				wingRight.getChild("wing").yRot = spread;
				wingRight.getChild("wing").zRot = 0.12f;
				collector.submitModelPart(wingLeft, pose, RenderTypes.entityCutoutNoCull(main), light, overlay, null);
				collector.submitModelPart(wingRight, pose, RenderTypes.entityCutoutNoCull(main), light, overlay, null);
				pose.popPose();
			}
		}

		private static int haloColor(int col) {
			// Heiligenschein leuchtet: helles Gold, außer du hast eine eigene Farbe gewählt
			return OemerConfig.get().cosColor == 0 ? 0xFFFFE9A8 : col;
		}
	}
}
