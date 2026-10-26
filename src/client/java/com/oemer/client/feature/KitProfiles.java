package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kits = gespeicherte Minecraft-Einstellungen.
 * Jedes Kit hat seine eigene Kopie von options.txt (Tasten, FOV, Empfindlichkeit, Grafik …).
 * "Speichern" merkt sich deine aktuellen Einstellungen im Kit, "Wechseln" lädt die vom anderen Kit.
 */
public final class KitProfiles {
	private KitProfiles() {
	}

	/** Diese Zeilen bleiben immer wie sie sind (Fenster, Sprache, Ressourcenpakete, Ömer-Tasten …). */
	private static final Set<String> KEEP = Set.of("version", "resourcePacks", "incompatibleResourcePacks", "lang",
			"fullscreen", "fullscreenResolution", "overrideWidth", "overrideHeight", "lastServer", "tutorialStep",
			"joinedFirstServer", "skipMultiplayerWarning", "onboardAccessibility", "soundDevice", "syncChunkWrites",
			"useNativeTransport", "hideServerAddress", "glDebugVerbosity", "telemetryOptInExtra");

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("kits");
	}

	private static Path file(int kit) {
		String name = OemerConfig.KITS[Math.floorMod(kit, OemerConfig.KITS.length)].toLowerCase().replaceAll("[^a-z0-9]+", "_");
		return folder().resolve(Math.floorMod(kit, OemerConfig.KITS.length) + "_" + name + ".txt");
	}

	/** Gespeicherte Einstellungen dieses Kits löschen (die aktuellen Minecraft-Einstellungen bleiben, wie sie sind). */
	public static boolean delete(int kit) {
		try {
			return Files.deleteIfExists(file(kit));
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean hasSaved(int kit) {
		return Files.isRegularFile(file(kit));
	}

	public static int active() {
		return Math.floorMod(OemerConfig.get().kit, OemerConfig.KITS.length);
	}

	public static String name(int kit) {
		return OemerConfig.KITS[Math.floorMod(kit, OemerConfig.KITS.length)];
	}

	private static boolean keep(String key) {
		return KEEP.contains(key) || key.startsWith("key_key.oemerclient")
				|| (OemerConfig.get().sharedKeys && key.startsWith("key_"));
	}

	private static Map<String, String> read(Path p) throws Exception {
		Map<String, String> map = new LinkedHashMap<>();
		if (!Files.isRegularFile(p)) return map;
		for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
			int i = line.indexOf(':');
			if (i <= 0) continue;
			map.put(line.substring(0, i), line.substring(i + 1));
		}
		return map;
	}

	private static void write(Path p, Map<String, String> map) throws Exception {
		List<String> lines = new ArrayList<>();
		for (var e : map.entrySet()) lines.add(e.getKey() + ":" + e.getValue());
		Files.createDirectories(p.getParent());
		Files.write(p, lines, StandardCharsets.UTF_8);
	}

	/** Deine jetzigen Einstellungen in diesem Kit speichern. */
	public static boolean save(int kit) {
		Minecraft mc = Minecraft.getInstance();
		try {
			mc.options.save();
			Map<String, String> all = read(mc.options.getFile().toPath());
			Map<String, String> out = new LinkedHashMap<>();
			for (var e : all.entrySet()) if (!keep(e.getKey())) out.put(e.getKey(), e.getValue());
			write(file(kit), out);
			return true;
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Kit {} konnte nicht gespeichert werden", name(kit), e);
			return false;
		}
	}

	/** Gespeicherte Einstellungen von diesem Kit laden (wenn es welche gibt). */
	public static boolean load(int kit) {
		Minecraft mc = Minecraft.getInstance();
		Path saved = file(kit);
		if (!Files.isRegularFile(saved)) return false;
		try {
			int oldGui = mc.options.guiScale().get();
			Path options = mc.options.getFile().toPath();
			Map<String, String> current = read(options);
			for (var e : read(saved).entrySet()) {
				if (!keep(e.getKey())) current.put(e.getKey(), e.getValue());
			}
			write(options, current);
			KeyMapping.releaseAll();
			mc.options.load();
			KeyMapping.resetMapping();
			if (mc.options.guiScale().get() != oldGui) mc.resizeDisplay();
			if (mc.level != null) mc.levelRenderer.allChanged();
			mc.options.broadcastOptions();
			return true;
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Kit {} konnte nicht geladen werden", name(kit), e);
			return false;
		}
	}

	/** Zu einem anderen Kit wechseln: altes Kit (optional) speichern, neues laden. */
	public static void switchTo(int kit) {
		OemerConfig c = OemerConfig.get();
		int from = active();
		kit = Math.floorMod(kit, OemerConfig.KITS.length);
		if (kit == from) return;
		if (c.kitAutoSave || !hasSaved(from)) save(from);
		c.kit = kit;
		OemerConfig.save();
		boolean loaded = load(kit);
		if (!loaded) save(kit); // neues Kit startet mit deinen jetzigen Einstellungen
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("§fKit §l" + name(kit) + (loaded ? " §ageladen" : " §7(neu, mit deinen Einstellungen)")));
		}
	}

	public static void next() {
		switchTo(active() + 1);
	}

	/** Kit zurück auf die Einstellungen von "Survival" setzen. */
	public static void copyFrom(int fromKit, int toKit) {
		try {
			if (!hasSaved(fromKit)) save(fromKit);
			Files.createDirectories(folder());
			Files.copy(file(fromKit), file(toKit), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			if (toKit == active()) load(toKit);
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Kit konnte nicht kopiert werden", e);
		}
	}

	/** Deine jetzigen Minecraft-Tasten in alle gespeicherten Kits schreiben. */
	public static void copyKeysToAll() {
		Minecraft mc = Minecraft.getInstance();
		try {
			mc.options.save();
			Map<String, String> keys = new LinkedHashMap<>();
			for (var e : read(mc.options.getFile().toPath()).entrySet()) {
				if (e.getKey().startsWith("key_") && !e.getKey().startsWith("key_key.oemerclient")) keys.put(e.getKey(), e.getValue());
			}
			for (int i = 0; i < OemerConfig.KITS.length; i++) {
				if (!hasSaved(i)) continue;
				Map<String, String> kit = read(file(i));
				kit.putAll(keys);
				write(file(i), kit);
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Tasten konnten nicht in alle Kits kopiert werden", e);
		}
	}

	/** Für die Tasten-Liste: alle Minecraft-Tasten (ohne Ömer-Tasten), sortiert nach Kategorie. */
	public static List<KeyMapping> gameKeys() {
		List<KeyMapping> list = new ArrayList<>();
		for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
			if (!k.getName().startsWith("key.oemerclient")) list.add(k);
		}
		return list;
	}
}
