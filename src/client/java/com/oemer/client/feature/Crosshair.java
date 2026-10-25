package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Eigenes Crosshair: eingebaute Designs, PNGs aus crosshairs/ oder selbst gemalt. */
public final class Crosshair {
	private Crosshair() {
	}

	private static final String DESIGN = "design:";
	public static final String[] DESIGNS = {"Minecraft", "Punkt", "Kreuz", "Kreuz mit Lücke", "Kreis", "Kreis + Punkt", "X", "Klammern"};

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("crosshairs");
	}

	public static List<String> choices() {
		List<String> list = new ArrayList<>();
		for (int i = 0; i < DESIGNS.length; i++) list.add(DESIGN + i);
		list.addAll(CapeManager.imageFiles(folder()));
		return list;
	}

	public static String displayName(String choice) {
		if (choice.startsWith(DESIGN)) {
			try {
				return DESIGNS[Integer.parseInt(choice.substring(DESIGN.length()))];
			} catch (Exception e) {
				return DESIGNS[0];
			}
		}
		return CapeManager.shortName(choice);
	}

	private static int designIndex(String choice) {
		try {
			return Math.floorMod(Integer.parseInt(choice.substring(DESIGN.length())), DESIGNS.length);
		} catch (Exception e) {
			return 1;
		}
	}

	public static int color() {
		OemerConfig c = OemerConfig.get();
		int col = OemerConfig.CROSSHAIR_COLORS[Math.floorMod(c.crosshairColor, OemerConfig.CROSSHAIR_COLORS.length)];
		return col == 0 ? (Theme.accent() | 0xFF000000) : col;
	}

	public static float scale() {
		return switch (OemerConfig.get().crosshairSize) {
			case 0 -> 0.75f;
			case 2 -> 1.5f;
			default -> 1f;
		};
	}

	/** Pixel-Rechtecke (x, y, w, h) auf einem 15x15-Raster, Mitte = (7,7). */
	static List<int[]> shape(int design) {
		List<int[]> r = new ArrayList<>();
		switch (design) {
			case 1 -> r.add(new int[]{6, 6, 3, 3});
			case 2 -> {
				r.add(new int[]{2, 7, 11, 1});
				r.add(new int[]{7, 2, 1, 11});
			}
			case 3 -> {
				r.add(new int[]{1, 7, 4, 1});
				r.add(new int[]{10, 7, 4, 1});
				r.add(new int[]{7, 1, 1, 4});
				r.add(new int[]{7, 10, 1, 4});
				r.add(new int[]{7, 7, 1, 1});
			}
			case 4, 5 -> {
				// Kreis mit Radius 5
				int[][] ring = {{5, 2, 5, 1}, {5, 12, 5, 1}, {2, 5, 1, 5}, {12, 5, 1, 5},
						{3, 3, 2, 1}, {10, 3, 2, 1}, {3, 11, 2, 1}, {10, 11, 2, 1},
						{3, 4, 1, 1}, {11, 4, 1, 1}, {3, 10, 1, 1}, {11, 10, 1, 1}};
				for (int[] p : ring) r.add(p);
				if (design == 5) r.add(new int[]{7, 7, 1, 1});
			}
			case 6 -> {
				for (int i = 0; i < 5; i++) {
					r.add(new int[]{3 + i, 3 + i, 1, 1});
					r.add(new int[]{11 - i, 3 + i, 1, 1});
					r.add(new int[]{3 + i, 11 - i, 1, 1});
					r.add(new int[]{11 - i, 11 - i, 1, 1});
				}
				r.add(new int[]{7, 7, 1, 1});
			}
			case 7 -> {
				r.add(new int[]{2, 3, 1, 9});
				r.add(new int[]{2, 3, 3, 1});
				r.add(new int[]{2, 11, 3, 1});
				r.add(new int[]{12, 3, 1, 9});
				r.add(new int[]{10, 3, 3, 1});
				r.add(new int[]{10, 11, 3, 1});
				r.add(new int[]{7, 7, 1, 1});
			}
			default -> {
			}
		}
		return r;
	}

	/** Bild der aktuellen Auswahl (für den Editor als Vorlage und die Vorschau). */
	public static BufferedImage previewImage() {
		OemerConfig c = OemerConfig.get();
		if (!c.crosshairSelection.startsWith(DESIGN)) {
			try {
				return ImageTools.read(folder().resolve(c.crosshairSelection));
			} catch (Exception e) {
				return null;
			}
		}
		BufferedImage img = new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
		int d = designIndex(c.crosshairSelection);
		if (d == 0) d = 2;
		for (int[] p : shape(d)) {
			for (int y = p[1]; y < p[1] + p[3]; y++) {
				for (int x = p[0]; x < p[0] + p[2]; x++) {
					img.setRGB(x, y, color());
				}
			}
		}
		return img;
	}

	/** Ist ein eigenes Crosshair aktiv (nicht "Minecraft")? */
	public static boolean active() {
		OemerConfig c = OemerConfig.get();
		if (!c.customCrosshair) return false;
		return !(c.crosshairSelection.startsWith(DESIGN) && designIndex(c.crosshairSelection) == 0);
	}

	/** Zeichnet das Crosshair zentriert. Gibt false zurück, wenn das Original gezeichnet werden soll. */
	public static boolean render(GuiGraphics g, DeltaTracker tracker) {
		if (!active()) return false;
		Minecraft mc = Minecraft.getInstance();
		if (!mc.options.getCameraType().isFirstPerson()) return true; // wie Minecraft: in F5 kein Crosshair
		if (mc.player != null && mc.player.isSpectator()) return true;
		draw(g, g.guiWidth() / 2f, g.guiHeight() / 2f, scale());
		return true;
	}

	/** Auch für die Vorschau im Menü. */
	public static void draw(GuiGraphics g, float cx, float cy, float scale) {
		OemerConfig c = OemerConfig.get();
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale);
		if (c.crosshairSelection.startsWith(DESIGN)) {
			int d = designIndex(c.crosshairSelection);
			if (d == 0) d = 2;
			int col = color();
			List<int[]> rects = shape(d);
			// dunkler Rand, damit man es auf hellem Grund sieht
			for (int[] p : rects) {
				g.fill(p[0] - 8, p[1] - 8, p[0] - 7 + p[2] + 1, p[1] - 7 + p[3] + 1, 0x70000000);
			}
			for (int[] p : rects) {
				g.fill(p[0] - 7, p[1] - 7, p[0] - 7 + p[2], p[1] - 7 + p[3], col);
			}
		} else {
			Textures.Tex t = Textures.file("crosshair", folder().resolve(c.crosshairSelection), 128);
			if (t != null) {
				Textures.drawContain(g, t, -8, -8, 16, 16);
			}
		}
		g.pose().popMatrix();
	}
}
