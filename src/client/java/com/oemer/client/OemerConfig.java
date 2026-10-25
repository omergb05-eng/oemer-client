package com.oemer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Alle Einstellungen vom Ömer Client.
 * Wird in .minecraft/config/oemerclient.json gespeichert.
 */
public class OemerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("oemerclient.json");

	private static OemerConfig instance = new OemerConfig();

	// ---- Settings ----
	public int kit = 0;                 // Index in KITS
	public boolean fullbright = false;
	public boolean freecam = false;     // wird beim Start immer auf aus gesetzt
	public int freecamSpeed = 1;        // Index in FREECAM_SPEEDS
	public boolean materialList = false;
	public int materialListCorner = 1;  // Index in CORNERS

	// ---- Skins ----
	public boolean customCape = false;
	public boolean customTotem = false;
	public boolean customSounds = false;

	// ---- Layout ----
	public int menuOpacity = 1;         // Index in OPACITY_NAMES
	public int accent = 0;              // Index in ACCENT_NAMES
	public boolean showVersion = true;

	// ---- Graphics ----
	public boolean lookMode = false;    // Eigene Bilder als Look/Theme
	public boolean menuBlur = false;

	public static final String[] KITS = {"Normal World", "CPVP", "Sword", "Axe", "UHC", "SMP", "Mace"};
	public static final String[] FREECAM_SPEED_NAMES = {"Langsam", "Normal", "Schnell", "Turbo"};
	public static final double[] FREECAM_SPEEDS = {0.3, 0.6, 1.0, 2.0};
	public static final String[] CORNERS = {"Oben links", "Oben rechts", "Unten links", "Unten rechts"};
	public static final String[] OPACITY_NAMES = {"Leicht", "Mittel", "Stark"};
	public static final String[] ACCENT_NAMES = {"Weiß", "Eisblau", "Rosa", "Lila", "Mint"};

	public static OemerConfig get() {
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
					OemerConfig loaded = GSON.fromJson(reader, OemerConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Konnte oemerclient.json nicht laden, nutze Standardwerte", e);
			instance = new OemerConfig();
		}
		instance.clamp();
		instance.freecam = false;
	}

	public double freecamSpeedValue() {
		return FREECAM_SPEEDS[Math.floorMod(freecamSpeed, FREECAM_SPEEDS.length)];
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Konnte oemerclient.json nicht speichern", e);
		}
	}

	/** Falls jemand die Datei per Hand kaputt macht. */
	private void clamp() {
		kit = Math.floorMod(kit, KITS.length);
		freecamSpeed = Math.floorMod(freecamSpeed, FREECAM_SPEEDS.length);
		materialListCorner = Math.floorMod(materialListCorner, CORNERS.length);
		menuOpacity = Math.floorMod(menuOpacity, OPACITY_NAMES.length);
		accent = Math.floorMod(accent, ACCENT_NAMES.length);
	}
}
