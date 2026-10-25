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
	public boolean kitHud = false;      // Kit-HUD mit Item-Zählern
	public int kitHudCorner = 0;        // Index in CORNERS
	public boolean materialList = false;
	public int materialListCorner = 1;  // Index in CORNERS
	public String schematic = "";       // Dateiname aus schematics/
	public java.util.List<String> materialDone = new java.util.ArrayList<>(); // abgehakte Items

	// ---- Skins ----
	public boolean customCape = false;
	public String capeSelection = "design:0"; // "design:<nr>" oder Dateiname aus capes/
	public boolean customTotem = false;
	public String totemSelection = "";        // Dateiname aus totems/
	public boolean customSounds = false;
	public java.util.Map<String, String> soundSelection = new java.util.HashMap<>(); // Slot → Datei

	// ---- Cosmetics ----
	public boolean cosHalo = false;
	public boolean cosCrown = false;
	public boolean cosTopHat = false;
	public boolean cosWings = false;
	public boolean cosEars = false;
	public boolean cosHorns = false;
	public int cosColor = 0;            // Index in COSMETIC_COLOR_NAMES
	public boolean cosAnimate = true;

	// ---- Cape-Editor ----
	public int editorSize = 0;          // Index in EDITOR_SIZES

	// ---- Layout ----
	public int menuOpacity = 1;         // Index in OPACITY_NAMES
	public int accent = 0;              // Index in ACCENT_NAMES
	public boolean showVersion = true;
	public boolean menuParticles = true;
	public int hudScale = 1;            // Index in HUD_SCALE_NAMES

	// ---- Graphics ----
	public boolean lookMode = false;    // Eigene Bilder als Look/Theme
	public String lookImage = "";       // Dateiname aus looks/
	public int lookDim = 1;             // Index in LOOK_DIM_NAMES
	public boolean lookTitle = true;    // auch im Startscreen
	public boolean menuBlur = false;

	public static final String[] KITS = {"Normal World", "CPVP", "Sword", "Axe", "UHC", "SMP", "Mace"};
	public static final String[] FREECAM_SPEED_NAMES = {"Langsam", "Normal", "Schnell", "Turbo"};
	public static final double[] FREECAM_SPEEDS = {0.3, 0.6, 1.0, 2.0};
	public static final String[] CORNERS = {"Oben links", "Oben rechts", "Unten links", "Unten rechts"};
	public static final String[] OPACITY_NAMES = {"Leicht", "Mittel", "Stark"};
	public static final String[] HUD_SCALE_NAMES = {"Klein", "Normal", "Groß"};
	public static final float[] HUD_SCALES = {0.75f, 1.0f, 1.25f};
	public static final String[] LOOK_DIM_NAMES = {"Hell", "Mittel", "Dunkel"};
	public static final int[] LOOK_DIM = {0x30, 0x70, 0xB0};
	public static final String[] COSMETIC_COLOR_NAMES = {"Weiß", "Gold", "Schwarz", "Rosa", "Eisblau", "Lila", "Rot", "Akzent"};
	public static final int[] COSMETIC_COLORS = {0xFFF4F4F4, 0xFFF2C14E, 0xFF1A1A1A, 0xFFFF8FC7, 0xFF8ED6FF, 0xFFB98CFF, 0xFFE5484D, 0};
	public static final String[] EDITOR_SIZE_NAMES = {"Klassisch 10x16", "HD 20x32"};
	public static final int[] EDITOR_SCALE = {1, 2};
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
		kitHudCorner = Math.floorMod(kitHudCorner, CORNERS.length);
		hudScale = Math.floorMod(hudScale, HUD_SCALES.length);
		lookDim = Math.floorMod(lookDim, LOOK_DIM.length);
		cosColor = Math.floorMod(cosColor, COSMETIC_COLORS.length);
		editorSize = Math.floorMod(editorSize, EDITOR_SCALE.length);
		if (schematic == null) schematic = "";
		if (lookImage == null) lookImage = "";
		if (capeSelection == null) capeSelection = "design:0";
		if (totemSelection == null) totemSelection = "";
		if (materialDone == null) materialDone = new java.util.ArrayList<>();
		if (soundSelection == null) soundSelection = new java.util.HashMap<>();
	}
}
