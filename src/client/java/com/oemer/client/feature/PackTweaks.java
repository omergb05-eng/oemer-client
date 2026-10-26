package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Textur-Mods, die wir ins Ömer-Ressourcenpaket schreiben (aus den Original-Texturen berechnet):
 * Clear Glass, Dark GUI und No Bedrock.
 */
public final class PackTweaks {
	private PackTweaks() {
	}

	private static final String[] GLASS_COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
	/** Ordner mit GUI-Texturen, die Dark GUI abdunkelt. */
	private static final String[] DARK_FOLDERS = {"textures/gui/container", "textures/gui/sprites/container",
			"textures/gui/sprites/widget", "textures/gui/sprites/recipe_book", "textures/gui/sprites/popup",
			"textures/gui/sprites/advancements", "textures/gui/advancements"};

	/** Schreibt alle aktiven Texturen ins Paket. Gibt zurück, wie viele es sind. */
	public static int write(Minecraft mc, Path packDir) {
		OemerConfig c = OemerConfig.get();
		Path tex = packDir.resolve("assets/minecraft/textures");
		Map<String, BufferedImage> out = new LinkedHashMap<>();
		try {
			// alte Tweak-Texturen löschen (Totem bleibt)
			Path marker = packDir.resolve("oemer_tweaks.txt");
			if (Files.isRegularFile(marker)) {
				for (String rel : Files.readAllLines(marker)) {
					if (!rel.isBlank()) Files.deleteIfExists(packDir.resolve(rel));
				}
				Files.delete(marker);
			}
			VanillaPackResources vanilla = mc.getVanillaPackResources();
			if (c.noBedrock) {
				BufferedImage deep = read(vanilla, "textures/block/deepslate.png");
				if (deep != null) out.put("textures/block/bedrock.png", deep);
			}
			if (c.clearPortal) {
				BufferedImage portal = read(vanilla, "textures/block/nether_portal.png");
				if (portal != null) out.put("textures/block/nether_portal.png", fade(portal, OemerConfig.PORTAL_ALPHA[Math.floorMod(c.portalLevel, OemerConfig.PORTAL_ALPHA.length)]));
			}
			if (c.clearGlass) {
				BufferedImage g = read(vanilla, "textures/block/glass.png");
				if (g != null) out.put("textures/block/glass.png", clearGlass(g, false));
				for (String col : GLASS_COLORS) {
					String p = "textures/block/" + col + "_stained_glass.png";
					BufferedImage s = read(vanilla, p);
					if (s != null) out.put(p, clearGlass(s, true));
				}
			}
			if (c.darkGui) {
				for (String folder : DARK_FOLDERS) {
					vanilla.fullResources().listResources(PackType.CLIENT_RESOURCES, "minecraft", folder, (id, supplier) -> {
						if (!id.getPath().endsWith(".png")) return;
						try (InputStream in = supplier.get()) {
							BufferedImage img = ImageIO.read(in);
							if (img != null) out.put(id.getPath(), darken(img));
						} catch (Exception e) {
							OemerClient.LOGGER.debug("Dark GUI: {} übersprungen", id);
						}
					});
				}
				for (String single : new String[]{"textures/gui/recipe_book.png", "textures/gui/demo_background.png"}) {
					BufferedImage img = read(vanilla, single);
					if (img != null) out.put(single, darken(img));
				}
			}
			StringBuilder list = new StringBuilder();
			for (var e : out.entrySet()) {
				Path target = packDir.resolve("assets/minecraft").resolve(e.getKey());
				Files.createDirectories(target.getParent());
				Files.write(target, ImageTools.toPng(e.getValue()));
				list.append("assets/minecraft/").append(e.getKey()).append('\n');
			}
			if (!out.isEmpty()) Files.writeString(marker, list.toString());
			// .mcmeta-Dateien (Animation, 9-Slice) bleiben aus dem Original gültig, weil Größen gleich bleiben
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Textur-Mods konnten nicht geschrieben werden", e);
		}
		return out.size();
	}

	private static BufferedImage read(VanillaPackResources vanilla, String path) {
		IoSupplier<InputStream> s = vanilla.fullResources().getResource(PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace(path));
		if (s == null) return null;
		try (InputStream in = s.get()) {
			return ImageIO.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	/** Macht eine Textur durchsichtiger: alle Alpha-Werte werden mit factor malgenommen (Mindestens 1, damit sie nicht ganz verschwindet). */
	static BufferedImage fade(BufferedImage src, float factor) {
		BufferedImage out = argb(src);
		for (int y = 0; y < out.getHeight(); y++) {
			for (int x = 0; x < out.getWidth(); x++) {
				int p = out.getRGB(x, y);
				int a = p >>> 24;
				if (a == 0) continue;
				int na = Math.max(1, Math.round(a * factor));
				out.setRGB(x, y, (na << 24) | (p & 0xFFFFFF));
			}
		}
		return out;
	}

	private static BufferedImage argb(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		out.getGraphics().drawImage(src, 0, 0, null);
		return out;
	}

	/** Klares Glas: nur ein feiner Rand bleibt, die Mitte wird durchsichtig (gefärbtes Glas leicht getönt). */
	static BufferedImage clearGlass(BufferedImage src, boolean stained) {
		BufferedImage img = argb(src);
		int w = img.getWidth(), h = img.getHeight();
		long r = 0, g = 0, b = 0, n = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int p = img.getRGB(x, y);
				if ((p >>> 24) == 0) continue;
				r += (p >> 16) & 0xFF;
				g += (p >> 8) & 0xFF;
				b += p & 0xFF;
				n++;
			}
		}
		int avg = n == 0 ? 0xFFFFFF : (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
				int p;
				if (edge) {
					p = stained ? (0xC0 << 24) | avg : 0xFFDDEEF2;
				} else {
					p = stained ? (0x46 << 24) | avg : 0;
				}
				out.setRGB(x, y, p);
			}
		}
		return out;
	}

	/** Grau → dunkelgrau (helle Flächen werden dunkel, Farben bleiben). */
	static BufferedImage darken(BufferedImage src) {
		BufferedImage img = argb(src);
		int[] from = {0, 55, 85, 139, 198, 255};
		int[] to = {0, 16, 22, 31, 46, 82};
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int p = img.getRGB(x, y);
				int a = p >>> 24;
				if (a == 0) continue;
				int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
				int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
				if (max - min > 14) {
					// farbig: nur etwas abdunkeln
					r = r * 3 / 4;
					g = g * 3 / 4;
					b = b * 3 / 4;
				} else {
					int l = (r + g + b) / 3;
					int v = map(l, from, to);
					r = g = b = v;
				}
				img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
		return img;
	}

	private static int map(int v, int[] from, int[] to) {
		for (int i = 1; i < from.length; i++) {
			if (v <= from[i]) {
				float t = (float) (v - from[i - 1]) / (from[i] - from[i - 1]);
				return Math.round(to[i - 1] + t * (to[i] - to[i - 1]));
			}
		}
		return to[to.length - 1];
	}

	/** Für Tests: wie viele Texturen Dark GUI anfassen würde. */
	public static long countDark(Minecraft mc) {
		long[] n = {0};
		for (String folder : DARK_FOLDERS) {
			mc.getVanillaPackResources().fullResources().listResources(PackType.CLIENT_RESOURCES, "minecraft", folder, (id, s) -> {
				if (id.getPath().endsWith(".png")) n[0]++;
			});
		}
		return n[0];
	}
}
