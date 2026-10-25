package com.oemer.client.feature;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

/** Ordner für deine eigenen Cosmetic-Bilder (PNG mit Transparenz): .minecraft/config/oemerclient/cosmetics/ */
public final class CustomCosmetics {
	private CustomCosmetics() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("cosmetics");
	}

	public static List<String> files() {
		return CapeManager.imageFiles(folder());
	}

	public static void invalidate() {
		SpriteCosmetics.invalidate();
	}
}
