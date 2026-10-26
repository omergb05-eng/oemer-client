package com.oemer.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Bild-Cosmetics: Flügel, Heiligenscheine, Kronen, Ohren, Begleiter – als hochauflösende Bilder
 * auf dünnen Flächen am Spieler. Dazu eigene PNGs aus cosmetics/.
 * Pro Platz (Kopf, Rücken, Begleiter) kann man ein Teil ausrüsten.
 */
public final class SpriteCosmetics {
	private SpriteCosmetics() {
	}

	public enum Slot {
		HEAD("Kopf"), BACK("Rücken"), COMPANION("Begleiter");

		public final String title;

		Slot(String title) {
			this.title = title;
		}
	}

	/** Wie das Bild am Spieler sitzt. */
	enum Kind { WINGS, HALO, RING, PAIR, FLOAT, PLANE }

	public record Item(String id, String name, Slot slot, Kind kind, boolean glow, Supplier<BufferedImage> painter) {
	}

	private static final Map<String, Item> BUILTIN = new LinkedHashMap<>();

	// ----------------------------------------------------------- Katalog

	public static List<Item> items(Slot slot) {
		List<Item> list = new ArrayList<>();
		for (Item i : BUILTIN.values()) if (i.slot() == slot) list.add(i);
		return list;
	}

	/** Eigene PNGs als Teile – für jeden Platz verwendbar. */
	public static List<Item> customItems(Slot slot) {
		List<Item> list = new ArrayList<>();
		for (String f : CustomCosmetics.files()) {
			Path p = CustomCosmetics.folder().resolve(f);
			Kind kind = switch (slot) {
				case BACK -> Kind.WINGS;
				case HEAD -> Kind.PLANE;
				case COMPANION -> Kind.FLOAT;
			};
			list.add(new Item("custom:" + f, CapeManager.shortName(f), slot, kind, false, () -> {
				try {
					return ImageTools.limit(ImageTools.read(p), 512);
				} catch (Exception e) {
					return null;
				}
			}));
		}
		return list;
	}

	public static Item find(String id, Slot slot) {
		if (id == null || id.isEmpty()) return null;
		if (id.startsWith("custom:")) {
			for (Item i : customItems(slot)) if (i.id().equals(id)) return i;
			return null;
		}
		Item i = BUILTIN.get(id);
		return i != null && i.slot() == slot ? i : null;
	}

	public static String equipped(Slot slot) {
		return OemerConfig.get().equipped.getOrDefault(slot.name(), "");
	}

	public static void equip(Slot slot, String id) {
		if (id == null || id.isEmpty() || id.equals(equipped(slot))) {
			OemerConfig.get().equipped.remove(slot.name());
		} else {
			OemerConfig.get().equipped.put(slot.name(), id);
		}
		OemerConfig.save();
	}

	public static boolean anyEquipped() {
		return !OemerConfig.get().equipped.isEmpty();
	}

	// ------------------------------------------------- Bilder (im Hintergrund)

	private static final Map<String, CompletableFuture<BufferedImage>> IMAGES = new ConcurrentHashMap<>();

	/** Das Bild eines Teils, null solange es noch gemalt wird. */
	public static BufferedImage image(Item item) {
		if (item.painter() == null) return null;
		String key = item.id() + stampOf(item);
		CompletableFuture<BufferedImage> f = IMAGES.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
			try {
				return item.painter().get();
			} catch (Throwable t) {
				OemerClient.LOGGER.warn("Cosmetic {} konnte nicht gemalt werden", item.id(), t);
				return null;
			}
		}));
		return f.isDone() ? f.getNow(null) : null;
	}

	private static String stampOf(Item item) {
		if (!item.id().startsWith("custom:")) return "";
		try {
			return "@" + Files.getLastModifiedTime(CustomCosmetics.folder().resolve(item.id().substring(7))).toMillis();
		} catch (Exception e) {
			return "";
		}
	}

	/** Vorschaubild (Flügel als Paar). */
	public static BufferedImage thumbnail(Item item) {
		BufferedImage img = image(item);
		if (img == null) return null;
		if (item.kind() == Kind.WINGS && !item.id().startsWith("custom:")) {
			BufferedImage pair = new BufferedImage(img.getWidth() * 2, img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = pair.createGraphics();
			g.drawImage(mirror(img), 0, 0, null);
			g.drawImage(img, img.getWidth(), 0, null);
			g.dispose();
			return pair;
		}
		return img;
	}

	// ------------------------------------------------------------ Flächen

	/** Fläche 16x16, Ursprung unten Mitte, Vorderseite = linke Bildhälfte, Rückseite = rechte. */
	private static ModelPart planeBottom, planeCenter, planeLeftEdge, planeRightEdge;

	private static ModelPart plane(float x, float y) {
		MeshDefinition m = new MeshDefinition();
		m.getRoot().addOrReplaceChild("p", CubeListBuilder.create().texOffs(0, 0).addBox(x, y, -0.02f, 16f, 16f, 0.04f), PartPose.ZERO);
		return LayerDefinition.create(m, 32, 16).bakeRoot();
	}

	private static void buildPlanes() {
		if (planeBottom != null) return;
		planeBottom = plane(-8f, -16f);
		planeCenter = plane(-8f, -8f);
		planeLeftEdge = plane(0f, -8f);     // geht nach +x
		planeRightEdge = plane(-16f, -8f);  // geht nach -x
	}

	/** Textur aus Vorder- und Rückseite (nebeneinander, gleich groß). */
	private static Identifier atlas(String key, String stamp, BufferedImage front, BufferedImage back) {
		Textures.Tex cached = Textures.cached("cs_" + key, stamp);
		if (cached != null) return cached.id();
		int w = front.getWidth(), h = front.getHeight();
		BufferedImage a = new BufferedImage(w * 2, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = a.createGraphics();
		g.drawImage(front, 0, 0, null);
		g.drawImage(back, w, 0, w, h, null);
		g.dispose();
		Textures.Tex t = Textures.image("cs_" + key, stamp, a);
		if (t == null) throw new IllegalStateException("Textur fehlt: " + key);
		return t.id();
	}

	private static final Map<BufferedImage, BufferedImage> MIRRORED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	/** Gespiegeltes Bild, nur einmal berechnet (nicht jedes Bild neu). */
	private static BufferedImage mirror(BufferedImage img) {
		return MIRRORED.computeIfAbsent(img, PresetArt::mirror);
	}

	private static final Map<String, BufferedImage[]> HALVES = new ConcurrentHashMap<>();

	/** Für eigene Flügel-PNGs: linke und rechte Bildhälfte (wie von hinten gesehen). */
	private static BufferedImage[] halves(String key, BufferedImage img) {
		return HALVES.computeIfAbsent(key, k -> {
			int hw = img.getWidth() / 2;
			return new BufferedImage[]{img.getSubimage(0, 0, hw, img.getHeight()), img.getSubimage(hw, 0, img.getWidth() - hw, img.getHeight())};
		});
	}

	// ------------------------------------------------------------- Zeichnen

	static void submit(PoseStack pose, SubmitNodeCollector collector, int light, PlayerModel model, float t) {
		for (Slot slot : Slot.values()) {
			Item item = find(equipped(slot), slot);
			if (item == null) continue;
			BufferedImage img = image(item);
			if (img == null) continue;
			buildPlanes();
			try {
				draw(item, img, pose, collector, light, model, t);
			} catch (Exception e) {
				OemerClient.LOGGER.debug("Cosmetic {} konnte nicht gezeichnet werden", item.id(), e);
			}
		}
	}

	private static void draw(Item item, BufferedImage img, PoseStack pose, SubmitNodeCollector collector, int light,
							 PlayerModel model, float t) {
		String stamp = stampOf(item);
		boolean anim = OemerConfig.get().cosAnimate;
		int lit = item.glow() ? 0xF000F0 : light;
		int ov = OverlayTexture.NO_OVERLAY;
		float px = 1f / 16f;

		switch (item.kind()) {
			case WINGS -> {
				boolean custom = item.id().startsWith("custom:");
				BufferedImage left, right; // left = Spieler-links (+x), wie von hinten gesehen
				float aspect;
				if (custom && img.getWidth() > img.getHeight() * 1.2f) {
					BufferedImage[] h = halves(item.id() + stamp, img);
					left = h[0];
					right = h[1];
					aspect = (float) left.getWidth() / left.getHeight();
				} else if (custom) {
					// einzelnes Bild → auf den Rücken (wie ein Abzeichen)
					pose.pushPose();
					model.body.translateAndRotate(pose);
					pose.translate(0, 6f * px + 8f * px, 2.6f * px);
					Identifier id = atlas(item.id(), stamp, mirror(img), img);
					pose.scale(0.75f, 0.75f, 1f);
					collector.submitModelPart(planeBottom, pose, RenderTypes.entityTranslucent(id), lit, ov, null);
					pose.popPose();
					return;
				} else {
					left = mirror(img);
					right = img;
					aspect = 1f;
				}
				float flap = anim ? (float) Math.sin(t * 2.6) * 0.16f : 0f;
				float spread = 0.42f + flap;
				float k = 1.35f; // Größe
				for (int side = 1; side >= -1; side -= 2) {
					pose.pushPose();
					model.body.translateAndRotate(pose);
					pose.translate(side * 0.5f * px, 3.5f * px, 2.4f * px);
					pose.rotate(new Quaternionf().rotationZYX(side * -0.18f, side * -spread, 0f));
					pose.scale(k * aspect, k, 1f);
					Identifier id = side > 0
							? atlas(item.id() + "_l", stamp, mirror(left), left)
							: atlas(item.id() + "_r", stamp, mirror(right), right);
					// links: Bild mit Ansatz rechts (gespiegelt) → Fläche nach +x
					collector.submitModelPart(side > 0 ? planeLeftEdge : planeRightEdge, pose,
							RenderTypes.entityTranslucent(id), lit, ov, null);
					pose.popPose();
				}
			}
			case HALO -> {
				pose.pushPose();
				model.head.translateAndRotate(pose);
				float bob = anim ? (float) Math.sin(t * 2.0) * 0.5f : 0f;
				pose.translate(0f, (-12.5f + bob) * px, 0f);
				if (anim) pose.rotate(new Quaternionf().rotationY(t * 0.8f));
				pose.rotate(new Quaternionf().rotationX((float) Math.PI / 2));
				pose.scale(0.75f, 0.75f, 1f);
				Identifier id = atlas(item.id(), stamp, img, img);
				collector.submitModelPart(planeCenter, pose, RenderTypes.entityTranslucentEmissive(id), 0xF000F0, ov, null);
				pose.popPose();
			}
			case RING -> {
				Identifier id = atlas(item.id(), stamp, img, mirror(img));
				for (int i = 0; i < 4; i++) {
					pose.pushPose();
					model.head.translateAndRotate(pose);
					pose.rotate(new Quaternionf().rotationY((float) (i * Math.PI / 2)));
					pose.translate(0f, -7.6f * px, -4.7f * px);
					pose.scale(9.4f / 16f, 4.7f / 16f, 1f);
					collector.submitModelPart(planeBottom, pose, RenderTypes.entityTranslucent(id), lit, ov, null);
					pose.popPose();
				}
			}
			case PAIR -> {
				for (int side = -1; side <= 1; side += 2) {
					pose.pushPose();
					model.head.translateAndRotate(pose);
					pose.translate(side * 2.6f * px, -8.2f * px, -0.5f * px);
					pose.rotate(new Quaternionf().rotationZ(side * 0.22f));
					pose.scale(4.5f / 16f, 4.5f / 16f, 1f);
					BufferedImage f = side > 0 ? img : mirror(img);
					Identifier id = atlas(item.id() + (side > 0 ? "_a" : "_b"), stamp, f, mirror(f));
					collector.submitModelPart(planeBottom, pose, RenderTypes.entityTranslucent(id), lit, ov, null);
					pose.popPose();
				}
			}
			case PLANE -> { // eigenes Bild über dem Kopf
				pose.pushPose();
				model.head.translateAndRotate(pose);
				pose.translate(0f, -8.8f * px, 0f);
				float aspect = (float) img.getWidth() / img.getHeight();
				float size = OemerConfig.CUSTOM_COS_SIZES[Math.floorMod(OemerConfig.get().customCosSize, 3)] * 1.2f;
				pose.scale(size * Math.min(1.5f, aspect), size, 1f);
				Identifier id = atlas(item.id(), stamp, img, mirror(img));
				collector.submitModelPart(planeBottom, pose, RenderTypes.entityTranslucent(id), lit, ov, null);
				pose.popPose();
			}
			case FLOAT -> { // schwebt neben dir
				pose.pushPose();
				float bob = anim ? (float) Math.sin(t * 2.2) * 1.3f : 0f;
				pose.translate(14f * px, (-5f + bob) * px, 1f * px);
				if (anim) pose.rotate(new Quaternionf().rotationY((float) Math.sin(t * 0.9) * 0.7f));
				float aspect = (float) img.getWidth() / img.getHeight();
				pose.scale(0.55f * Math.min(1.5f, aspect), 0.55f, 1f);
				Identifier id = atlas(item.id(), stamp, img, mirror(img));
				collector.submitModelPart(planeCenter, pose, RenderTypes.entityTranslucent(id), lit, ov, null);
				pose.popPose();
			}
			default -> {
			}
		}
	}

	/** Datei geändert/gelöscht → Bilder neu laden. */
	public static void invalidate() {
		IMAGES.clear();
		HALVES.clear();
	}
}
