package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Iris Shaders und Sodium (eingebaut, Originale): Wir lesen nur, ob sie geladen sind, öffnen ihre Einstellungen
 * (per Reflection, damit der Client auch ohne sie läuft) und legen den Shader-Ordner an.
 */
public final class ShaderBridge {
	private ShaderBridge() {
	}

	public static boolean sodium() {
		return FabricLoader.getInstance().isModLoaded("sodium");
	}

	public static boolean iris() {
		return FabricLoader.getInstance().isModLoaded("iris");
	}

	private static String version(String id) {
		return FabricLoader.getInstance().getModContainer(id).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	public static String sodiumVersion() {
		return version("sodium");
	}

	public static String irisVersion() {
		return version("iris");
	}

	/** Hier gehören die Shader Packs (.zip oder Ordner) hinein. */
	public static Path shaderFolder() {
		Path p = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
		try {
			Files.createDirectories(p);
		} catch (Exception e) {
			OemerClient.LOGGER.warn("shaderpacks-Ordner konnte nicht angelegt werden: {}", e.toString());
		}
		return p;
	}

	/** Die Shader-Auswahl von Iris oder null (Iris fehlt / Klasse anders). */
	public static Screen shaderScreen(Screen parent) {
		return open("net.irisshaders.iris.gui.screen.ShaderPackScreen", parent);
	}

	/** Die Video-Einstellungen von Sodium oder null. */
	public static Screen sodiumScreen(Screen parent) {
		Screen s = open("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI", parent);
		if (s != null) return s;
		try {
			Class<?> c = Class.forName("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI");
			return (Screen) c.getMethod("createScreen", Screen.class).invoke(null, parent);
		} catch (Throwable t) {
			return null;
		}
	}

	private static Screen open(String cls, Screen parent) {
		try {
			Class<?> c = Class.forName(cls);
			return (Screen) c.getConstructor(Screen.class).newInstance(parent);
		} catch (Throwable t) {
			OemerClient.LOGGER.debug("{} nicht erreichbar: {}", cls, t.toString());
			return null;
		}
	}
}
