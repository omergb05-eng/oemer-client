package com.oemer.client.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Theme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Eigene Capes: eingebaute Ömer-Designs oder Bilder aus .minecraft/config/oemerclient/capes/.
 * Das Cape sieht nur du (es ist ein Client-Mod).
 */
public final class CapeManager {
	private CapeManager() {
	}

	private static final String DESIGN = "design:";

	private static String loadedKey;
	private static Identifier loadedId;
	private static ClientAsset.Texture loadedTexture;
	private static BufferedImage loadedImage;
	private static int counter;

	/** Auflösung der Einzelbilder einer animierten Cape (4 = 256x128), damit viele Bilder wenig Speicher brauchen. */
	private static final int ANIM_SCALE = 4;
	private static final int ANIM_MAX_FRAMES = 24;
	private static List<Identifier> animIds = List.of();
	private static List<ClientAsset.Texture> animTextures = List.of();
	private static ImageTools.Gif animGif;

	/** Eine Animation für Freunde: alle Bilder untereinander in einem PNG. */
	public record Shared(byte[] png, int frames, int ms) {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("capes");
	}

	/** Alle Möglichkeiten: erst die Designs, dann deine Bilder. */
	public static List<String> choices() {
		List<String> list = new ArrayList<>();
		for (int i = 0; i < ImageTools.DESIGNS.length; i++) {
			list.add(DESIGN + i);
		}
		list.addAll(imageFiles(folder()));
		return list;
	}

	public static String displayName(String choice) {
		if (choice.startsWith(DESIGN)) {
			try {
				return ImageTools.DESIGNS[Integer.parseInt(choice.substring(DESIGN.length()))];
			} catch (Exception e) {
				return ImageTools.DESIGNS[0];
			}
		}
		return shortName(choice);
	}

	public static String shortName(String file) {
		String name = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
		return name.length() > 14 ? name.substring(0, 13) + "…" : name;
	}

	public static List<String> imageFiles(Path dir) {
		List<String> files = new ArrayList<>();
		try {
			Files.createDirectories(dir);
			try (Stream<Path> s = Files.list(dir)) {
				s.filter(Files::isRegularFile)
						.map(p -> p.getFileName().toString())
						.filter(n -> {
							String l = n.toLowerCase(Locale.ROOT);
							return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".gif");
						})
						.sorted(String.CASE_INSENSITIVE_ORDER)
						.forEach(files::add);
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Konnte Ordner {} nicht lesen", dir, e);
		}
		return files;
	}

	/** Wird vom Spieler-Mixin aufgerufen. null = kein eigenes Cape. */
	public static ClientAsset.Texture currentTexture() {
		OemerConfig c = OemerConfig.get();
		if (!c.customCape) {
			return null;
		}
		String key = c.capeSelection + (c.capeSelection.startsWith(DESIGN) ? "@" + c.accent : "");
		if (!key.equals(loadedKey)) {
			load(c.capeSelection, key);
		}
		if (!animTextures.isEmpty()) {
			return animTextures.get(frameAt(animGif.delaysMs(), animGif.totalMs(), System.currentTimeMillis()));
		}
		return loadedTexture;
	}

	/** Welches Bild einer Animation zu diesem Zeitpunkt zu sehen ist. */
	public static int frameAt(int[] delaysMs, int totalMs, long nowMs) {
		long t = Math.floorMod(nowMs, (long) totalMs);
		for (int i = 0; i < delaysMs.length; i++) {
			t -= delaysMs[i];
			if (t < 0) return i;
		}
		return delaysMs.length - 1;
	}

	/** Ist die gewählte Cape eine Animation (GIF)? */
	public static boolean animated() {
		previewImage();
		return !animTextures.isEmpty();
	}

	/** Die Animation für Freunde (kleine Bilder, höchstens ca. 40 KB) oder null, wenn die Cape keine Animation ist. */
	public static Shared sharedAnimation() {
		if (!animated() || animGif == null) return null;
		try {
			int[][] tries = {{2, 8}, {1, 16}, {1, 8}};
			for (int[] t : tries) {
				int scale = t[0];
				int n = Math.min(t[1], animGif.frames().size());
				int step = Math.max(1, (animGif.frames().size() + n - 1) / n);
				List<BufferedImage> picked = new ArrayList<>();
				int ms = 0;
				for (int i = 0; i < animGif.frames().size(); i += step) {
					picked.add(ImageTools.toCape(animGif.frames().get(i), scale));
					for (int j = i; j < Math.min(animGif.frames().size(), i + step); j++) ms += animGif.delaysMs()[j];
				}
				int fw = 64 * scale, fh = 32 * scale;
				BufferedImage strip = new BufferedImage(fw, fh * picked.size(), BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D g = strip.createGraphics();
				for (int i = 0; i < picked.size(); i++) g.drawImage(picked.get(i), 0, i * fh, fw, fh, null);
				g.dispose();
				byte[] png = ImageTools.toPng(strip);
				if (png.length * 4 / 3 + 4 <= 50_000) return new Shared(png, picked.size(), Math.max(20, ms / picked.size()));
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Animierte Cape konnte nicht für Freunde vorbereitet werden", e);
		}
		return null;
	}

	/** Das Cape-Bild der aktuellen Auswahl (für die Vorschau im Menü), auch wenn das Cape aus ist. */
	public static BufferedImage previewImage() {
		OemerConfig c = OemerConfig.get();
		String key = c.capeSelection + (c.capeSelection.startsWith(DESIGN) ? "@" + c.accent : "");
		if (!key.equals(loadedKey)) {
			load(c.capeSelection, key);
		}
		return loadedImage;
	}

	public static String previewKey() {
		return loadedKey == null ? "" : loadedKey;
	}

	/** Neu laden, z. B. wenn du ein Bild im Ordner ausgetauscht hast. */
	public static void invalidate() {
		loadedKey = null;
	}

	private static void load(String choice, String key) {
		loadedKey = key;
		Minecraft mc = Minecraft.getInstance();
		for (Identifier old : animIds) {
			mc.getTextureManager().release(old);
		}
		animIds = List.of();
		animTextures = List.of();
		animGif = null;
		if (choice.toLowerCase(Locale.ROOT).endsWith(".gif") && loadAnimation(mc, choice)) {
			return;
		}
		try {
			BufferedImage img;
			if (choice.startsWith(DESIGN)) {
				int nr = Integer.parseInt(choice.substring(DESIGN.length()));
				img = ImageTools.design(nr, Theme.accent());
			} else {
				img = ImageTools.toCape(ImageTools.read(folder().resolve(choice)));
			}
			loadedImage = img;
			NativeImage nativeImage = NativeImage.read(ImageTools.toPng(img));

			Identifier id = Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "cape/" + (counter++));
			mc.getTextureManager().register(id, new DynamicTexture(() -> "Ömer Cape", nativeImage));
			if (loadedId != null) {
				mc.getTextureManager().release(loadedId);
			}
			loadedId = id;
			loadedTexture = new ClientAsset.ResourceTexture(id, id);
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Cape {} konnte nicht geladen werden", choice, e);
			loadedTexture = null;
			loadedImage = null;
		}
	}

	/** GIF als animierte Cape laden. false = kein brauchbares GIF (dann wird es wie ein normales Bild geladen). */
	private static boolean loadAnimation(Minecraft mc, String choice) {
		try {
			ImageTools.Gif gif = ImageTools.readGif(folder().resolve(choice), ANIM_MAX_FRAMES, 512);
			if (gif.frames().size() < 2) return false;
			List<Identifier> ids = new ArrayList<>();
			List<ClientAsset.Texture> texs = new ArrayList<>();
			for (BufferedImage frame : gif.frames()) {
				NativeImage ni = NativeImage.read(ImageTools.toPng(ImageTools.toCape(frame, ANIM_SCALE)));
				Identifier id = Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "cape/" + (counter++));
				mc.getTextureManager().register(id, new DynamicTexture(() -> "Ömer Cape (Animation)", ni));
				ids.add(id);
				texs.add(new ClientAsset.ResourceTexture(id, id));
			}
			if (loadedId != null) {
				mc.getTextureManager().release(loadedId);
				loadedId = null;
			}
			animIds = ids;
			animTextures = texs;
			animGif = gif;
			loadedImage = ImageTools.toCape(gif.frames().get(0), ANIM_SCALE);
			loadedTexture = texs.get(0);
			return true;
		} catch (Exception | OutOfMemoryError e) {
			OemerClient.LOGGER.warn("Animierte Cape {} konnte nicht geladen werden", choice, e);
			return false;
		}
	}
}
