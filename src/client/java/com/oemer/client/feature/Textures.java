package com.oemer.client.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.oemer.client.OemerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Lädt deine Bilder als Minecraft-Texturen (mit Cache) und zeichnet sie im Menü. */
public final class Textures {
	private Textures() {
	}

	public record Tex(Identifier id, int width, int height) {
	}

	private static final Map<String, Tex> CACHE = new HashMap<>();
	private static final Map<String, String> STAMP = new HashMap<>();
	private static int counter;

	/** Bild aus einer Datei. Wird neu geladen, wenn sich die Datei ändert. null bei Fehler. */
	public static Tex file(String slot, Path file, int maxSize) {
		try {
			if (file == null || !Files.isRegularFile(file)) {
				release(slot);
				return null;
			}
			String stamp = file + "@" + Files.getLastModifiedTime(file).toMillis();
			if (stamp.equals(STAMP.get(slot)) && CACHE.containsKey(slot)) {
				return CACHE.get(slot);
			}
			BufferedImage img = ImageTools.read(file);
			img = ImageTools.limit(img, maxSize);
			return image(slot, stamp, img);
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Bild {} konnte nicht geladen werden", file, e);
			STAMP.put(slot, "error:" + file);
			release(slot);
			return null;
		}
	}

	/** Bild aus dem Speicher (z. B. Cape-Vorschau). stamp: ändert sich → neu hochladen. */
	public static Tex image(String slot, String stamp, BufferedImage img) {
		if (stamp.equals(STAMP.get(slot)) && CACHE.containsKey(slot)) {
			return CACHE.get(slot);
		}
		try {
			NativeImage nativeImage = NativeImage.read(ImageTools.toPng(img));
			String safe = slot.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
			Identifier id = Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "img/" + safe + "_" + Integer.toHexString(slot.hashCode()) + "_" + (counter++));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Ömer " + slot, nativeImage));
			release(slot);
			Tex tex = new Tex(id, img.getWidth(), img.getHeight());
			CACHE.put(slot, tex);
			STAMP.put(slot, stamp);
			return tex;
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Textur {} konnte nicht erstellt werden", slot, e);
			return null;
		}
	}

	/** Schon hochgeladene Textur mit genau diesem Stempel, sonst null. */
	public static Tex cached(String slot, String stamp) {
		return stamp.equals(STAMP.get(slot)) ? CACHE.get(slot) : null;
	}

	public static void release(String slot) {
		Tex old = CACHE.remove(slot);
		if (old != null) {
			Minecraft.getInstance().getTextureManager().release(old.id());
		}
	}

	/** Ganze Textur in ein Rechteck strecken. */
	public static void draw(GuiGraphicsExtractor g, Tex t, int x, int y, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, 0f, 0f, w, h, t.width(), t.height(), t.width(), t.height());
	}

	/** Ausschnitt (u,v,uw,vh in Textur-Pixeln) in ein Rechteck zeichnen. */
	public static void drawRegion(GuiGraphicsExtractor g, Tex t, int x, int y, int w, int h, int u, int v, int uw, int vh) {
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, (float) u, (float) v, w, h, uw, vh, t.width(), t.height());
	}

	/** Füllt das Rechteck komplett aus (wie ein Hintergrundbild), schneidet überstehende Ränder ab. */
	public static void drawCover(GuiGraphicsExtractor g, Tex t, int x, int y, int w, int h) {
		double target = (double) w / h;
		double have = (double) t.width() / t.height();
		int u = 0, v = 0, uw = t.width(), vh = t.height();
		if (have > target) {
			uw = (int) Math.round(vh * target);
			u = (t.width() - uw) / 2;
		} else {
			vh = (int) Math.round(uw / target);
			v = (t.height() - vh) / 2;
		}
		drawRegion(g, t, x, y, w, h, u, v, uw, vh);
	}

	/** Passt komplett rein (mit Rand), Seitenverhältnis bleibt. */
	public static void drawContain(GuiGraphicsExtractor g, Tex t, int x, int y, int w, int h) {
		double scale = Math.min((double) w / t.width(), (double) h / t.height());
		int dw = Math.max(1, (int) Math.round(t.width() * scale));
		int dh = Math.max(1, (int) Math.round(t.height() * scale));
		draw(g, t, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);
	}
}
