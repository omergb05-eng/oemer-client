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
							return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg");
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
		return loadedTexture;
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
}
