package com.oemer.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eigene Cosmetics: aus einem Pixelbild (PNG mit Transparenz) wird ein 3D-Modell.
 * Jeder sichtbare Pixel = ein kleiner Würfel. Bilder in .minecraft/config/oemerclient/cosmetics/
 * oder im Cosmetic-Editor selbst malen.
 */
public final class CustomCosmetics {
	private CustomCosmetics() {
	}

	/** Größte Kantenlänge in Pixeln (größere Bilder werden verkleinert). */
	private static final int MAX = 32;

	private record Built(ModelPart part, Identifier texture, int width, int height, String stamp) {
	}

	private static final Map<String, Built> CACHE = new HashMap<>();

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("cosmetics");
	}

	public static List<String> files() {
		return CapeManager.imageFiles(folder());
	}

	public static void invalidate() {
		CACHE.clear();
	}

	public static int place(String file) {
		return OemerConfig.get().customCosmetics.getOrDefault(file, 0);
	}

	public static boolean anyActive() {
		for (int v : OemerConfig.get().customCosmetics.values()) {
			if (v > 0) return true;
		}
		return false;
	}

	// ------------------------------------------------------------ Bauen

	private static Built get(String file) {
		Path p = folder().resolve(file);
		String stamp;
		try {
			stamp = file + "@" + Files.getLastModifiedTime(p).toMillis();
		} catch (Exception e) {
			return null;
		}
		Built b = CACHE.get(file);
		if (b != null && b.stamp().equals(stamp)) return b;
		try {
			b = build(file, stamp, ImageTools.read(p));
			CACHE.put(file, b);
			return b;
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Cosmetic {} konnte nicht gebaut werden", file, e);
			CACHE.put(file, new Built(null, null, 0, 0, stamp));
			return null;
		}
	}

	private static Built build(String file, String stamp, BufferedImage src) {
		BufferedImage img = src;
		if (img.getWidth() > MAX || img.getHeight() > MAX) {
			img = ImageTools.limit(img, MAX);
		}
		int w = img.getWidth(), h = img.getHeight();

		// sichtbare Pixel sammeln
		int count = 0;
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((img.getRGB(x, y) >>> 24) >= 128) count++;
		if (count == 0) return new Built(null, null, 0, 0, stamp);

		// Textur-Atlas: jeder Würfel bekommt ein 4x2-Feld in seiner Farbe
		int perRow = 64;
		int atlasW = perRow * 4;
		int atlasH = Math.max(2, ((count + perRow - 1) / perRow) * 2);
		BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);

		MeshDefinition mesh = new MeshDefinition();
		CubeListBuilder cubes = CubeListBuilder.create();
		int i = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = img.getRGB(x, y);
				if ((c >>> 24) < 128) continue;
				int u = (i % perRow) * 4, v = (i / perRow) * 2;
				for (int ay = 0; ay < 2; ay++) {
					for (int ax = 0; ax < 4; ax++) {
						// Seiten etwas dunkler, oben heller → wirkt 3D
						float shade = ay == 0 ? 1.08f : (ax == 1 || ax == 3 ? 1.0f : 0.84f);
						atlas.setRGB(u + ax, v + ay, shadeColor(c, shade));
					}
				}
				// Bild-x nach links gespiegelt, damit es von vorne richtig herum aussieht
				cubes.texOffs(u, v).addBox((w / 2f) - x - 1, y - h, -0.5f, 1f, 1f, 1f);
				i++;
			}
		}
		mesh.getRoot().addOrReplaceChild("c", cubes, PartPose.ZERO);
		ModelPart part = LayerDefinition.create(mesh, atlasW, atlasH).bakeRoot();
		Textures.Tex tex = Textures.image("cc_" + Integer.toHexString(file.hashCode()), stamp, atlas);
		return new Built(part, tex == null ? null : tex.id(), w, h, stamp);
	}

	private static int shadeColor(int c, float f) {
		int r = Math.min(255, Math.round(((c >> 16) & 255) * f));
		int g = Math.min(255, Math.round(((c >> 8) & 255) * f));
		int b = Math.min(255, Math.round((c & 255) * f));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	// ------------------------------------------------------------ Zeichnen

	/** Wird von der Cosmetics-Ebene aufgerufen. */
	static void submit(PoseStack pose, SubmitNodeCollector collector, int light, PlayerModel model, float t) {
		OemerConfig c = OemerConfig.get();
		float size = OemerConfig.CUSTOM_COS_SIZES[Math.floorMod(c.customCosSize, OemerConfig.CUSTOM_COS_SIZES.length)];
		for (var e : c.customCosmetics.entrySet()) {
			int place = e.getValue();
			if (place <= 0) continue;
			Built b = get(e.getKey());
			if (b == null || b.part() == null || b.texture() == null) continue;
			float k = (size * 16f) / Math.max(b.width(), b.height()); // größte Kante = size Blöcke

			pose.pushPose();
			switch (place) {
				case 1 -> { // über dem Kopf
					model.head.translateAndRotate(pose);
					pose.translate(0f, -8.8f / 16f, 0f);
					pose.scale(k, k, k);
				}
				case 2 -> { // Rücken (schaut nach hinten)
					model.body.translateAndRotate(pose);
					pose.translate(0f, (6f + b.height() * k / 2f) / 16f, 2.7f / 16f + 0.5f * k / 16f);
					pose.mulPose(new Quaternionf().rotationY((float) Math.PI));
					pose.scale(k, k, k);
				}
				case 3 -> { // auf der rechten Schulter
					model.body.translateAndRotate(pose);
					pose.translate(-5.5f / 16f, -0.3f / 16f, 0f);
					float s = k * 0.6f;
					pose.scale(s, s, s);
				}
				default -> { // Begleiter: schwebt neben dir und dreht sich
					float bob = c.cosAnimate ? (float) Math.sin(t * 2.2) * 1.2f : 0f;
					pose.translate(15f / 16f, (-4f + bob) / 16f, 0f);
					if (c.cosAnimate) pose.mulPose(new Quaternionf().rotationY(t * 1.4f));
					float s = k * 0.8f;
					pose.scale(s, s, s);
				}
			}
			collector.submitModelPart(b.part(), pose, RenderTypes.entityCutoutNoCull(b.texture()), light, OverlayTexture.NO_OVERLAY, null);
			pose.popPose();
		}
	}
}
