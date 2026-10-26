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
	public int totemMode = 0;                 // 0 = Bild, 1 = Mein Skin
	public int totemFit = 0;                  // 0 = ganzes Bild, 1 = füllen
	public int totemPreset = 0;               // Index in PresetArt.TOTEMS
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
	public java.util.Map<String, Integer> customCosmetics = new java.util.HashMap<>(); // Datei → Platz (0 = aus), 3D-Würfel
	public java.util.Map<String, String> equipped = new java.util.HashMap<>(); // Platz (HEAD/BACK/COMPANION) → Teil
	public boolean cosmeticsOn = true;
	public int customCosSize = 1;       // Index in CUSTOM_COS_SIZE_NAMES

	// ---- Crosshair ----
	public boolean customCrosshair = false;
	public String crosshairSelection = "design:1"; // "design:<nr>" oder Datei aus crosshairs/
	public int crosshairColor = 0;      // Index in CROSSHAIR_COLOR_NAMES
	public int crosshairSize = 1;       // Index in CROSSHAIR_SIZE_NAMES

	// ---- Kits (Tasten-Profile) ----

	// ---- Material List: markierte Kisten ----
	public java.util.List<MarkedChest> markedChests = new java.util.ArrayList<>();

	public static class MarkedChest {
		public String world = "";
		public int x, y, z;
		public java.util.Map<String, Integer> items = new java.util.HashMap<>();
		public long updated;
	}

	// ---- Musik ----
	public int musicService = 0;         // 0 = YouTube, 1 = Spotify
	public int ytVolume = 70;
	public boolean ytShuffle = false;
	public boolean ytRepeat = true;
	public int ytMode = 0;               // 0 = eingebetteter Player, 1 = YouTube-Seite (Erweiterung Ömer Bridge)
	public String ytApiKey = "";         // kostenloser YouTube-Data-API-Schlüssel (nur für die Suche)
	public java.util.List<com.oemer.client.feature.YouTubeMusic.Track> ytLibrary = new java.util.ArrayList<>();
	public java.util.List<com.oemer.client.feature.YouTubeMusic.Playlist> ytPlaylists = new java.util.ArrayList<>();
	public boolean chestCount = true;    // markierte Kisten in der Material List mitzählen
	public boolean chestHighlight = true;

	// ---- Mods ----
	public boolean noWeather = false;
	public boolean appleSkin = false;
	public boolean zoomOn = true;
	public int zoomLevel = 1;            // Index in ZOOM_NAMES
	public boolean zoomScroll = true;
	public boolean zoomSmooth = true;
	public boolean lowFire = false;
	public int lowFireLevel = 1;         // Index in LOW_NAMES
	public boolean lowShield = false;
	public int lowShieldLevel = 1;
	public boolean noDarkness = false;
	public boolean noFog = false;
	public boolean noPumpkin = false;
	public boolean noteTuner = false;
	public boolean shulkerTooltip = false;
	public boolean smallerTotem = false;
	public int smallerTotemLevel = 1;    // Index in SMALL_NAMES
	public boolean tntTimer = false;
	public boolean clearGlass = false;
	public boolean darkGui = false;
	public boolean noBedrock = false;
	public boolean noExplosionParticles = false;
	public boolean clearPortal = false;
	public int portalLevel = 1;           // Index in PORTAL_NAMES
	public boolean portalOverlayOff = true; // Portal-Wackeln/Overlay beim Durchgehen aus
	public boolean customName = false;
	public String customNameText = "";   // leer = dein echter Name
	public int nameMode = 1;              // 0 Farbe, 1 Verlauf, 2 Rainbow
	public boolean shareName = true;      // Namensstil + Logo für Freunde sichtbar
	public String nameLogo = "";          // Datei in namelogo/ (ein kleines Logo neben dem Namen)
	public boolean nameLogoOn = false;
	public int nameColor1 = 8;            // Index in NAME_COLORS
	public int nameColor2 = 9;
	public boolean nameBold = false;
	public boolean nameInChat = true;     // dein Name im Chat ersetzen
	public boolean nameShowSelf = true;   // Namensschild bei dir selbst (F5, Freecam)
	public boolean distantHorizons = false;
	public boolean kitAutoSave = true;   // beim Kit-Wechsel das alte Kit automatisch speichern
	public int graphicsLevel = -1;       // zuletzt gewählte Grafik-Stufe (-1 = eigene)

	// ---- Freunde (online) ----
	public boolean shareMusic = true;     // meine Musik für Freunde sichtbar
	public boolean shareSkins = true;     // Cape + Totem mit Freunden teilen
	public boolean chatToasts = true;     // Freunde-Nachrichten kurz im Spiel anzeigen
	public java.util.Map<String, Long> chatRead = new java.util.HashMap<>(); // Freund -> zuletzt gelesene Nachricht

	// ---- Schematic-Platzierung ----
	public boolean placeOn = false;
	public String placeFile = "";
	public boolean placeSet = false;      // Position gesetzt?
	public int placeX, placeY, placeZ;
	public int placeRot = 0;              // 0..3 mal 90 Grad
	public int placeMirror = 0;           // 0 aus, 1 links/rechts, 2 vorne/hinten
	public int placeLayerMode = 0;        // 0 alle, 1 eine, 2 bis, 3 ab
	public int placeLayer = 0;
	public boolean placeShowCorrect = false;
	public boolean placeShowWrong = true;
	public boolean placeBox = true;
	public int placeRange = 3;            // Index in PLACE_RANGES (3 = unbegrenzt)
	public boolean placeRangeV81 = false;  // einmalig auf "unbegrenzt" gestellt
	public boolean placeTextures = true;  // echte Block-Texturen
	public boolean placeGhost = true;     // Geister-Schimmern
	public int placeStep = 0;             // Index in PLACE_STEPS

	// ---- Clipper ----
	public boolean clipperOn = false;
	public int clipLen = 1;               // Index in CLIP_LENGTHS (Sekunden)
	public int clipFps = 1;               // Index in CLIP_FPS
	public int clipQuality = 1;           // Index in CLIP_WIDTHS
	public int clipFormat = 0;            // 0 = MP4, 1 = AVI (sofort fertig)
	public String clipFolder = "";        // leer = .minecraft/oemer-clips

	// ---- Ladescreen + Symbol ----
	public boolean armorHud = false;        // Rüstungs-Anzeige
	public int armorHudPos = 0;             // Index in ARMOR_POS
	public int armorHudValue = 0;           // Index in ARMOR_VALUE
	public boolean armorHudHands = false;   // Hände mitzeigen
	public boolean armorHudGear = false;    // Schwert, Axt, Schild aus der Hotbar mitzeigen
	public boolean sharedKeys = true;      // Minecraft-Tasten gelten für alle Kits
	public boolean customLoading = true;
	public boolean nameIcon = true;

	// ---- Spotify ----
	public String spotifyClientId = "";
	public boolean spotifyHud = true;
	public int spotifyHudCorner = 2;    // Index in CORNERS

	// ---- Cape-Editor ----
	public int editorSize = 0;          // Index in EDITOR_SIZES

	// ---- Layout ----
	public int designStyle = 0;         // Index in DESIGN_STYLES
	public int menuOpacity = 1;         // Index in OPACITY_NAMES
	public int accent = 0;              // Index in ACCENT_NAMES
	public boolean showVersion = true;
	public boolean menuParticles = true;
	public int hudScale = 1;            // Index in HUD_SCALE_NAMES

	// ---- Graphics ----
	public boolean lookMode = false;    // Eigene Bilder als Look/Theme
	public String lookImage = "web:0"; // "preset:<nr>" oder Dateiname aus looks/
	public int lookDim = 1;             // Index in LOOK_DIM_NAMES
	public boolean lookTitle = true;    // auch im Startscreen
	public boolean menuBlur = false;

	public static final String[] KITS = {"Survival", "CPVP", "Sword", "Axe", "UHC", "SMP", "Mace"};
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
	public static final String[] DESIGN_STYLES = {"Clean", "Galaxy"};
	public static final String[] TOTEM_MODES = {"Eigenes Bild", "Mein Skin", "Vorlage"};
	public static final String[] TOTEM_FITS = {"Ganzes Bild", "Füllen"};
	public static final String[] COSMETIC_PLACES = {"Aus", "Über dem Kopf", "Rücken", "Schulter", "Begleiter"};
	public static final String[] CUSTOM_COS_SIZE_NAMES = {"Klein", "Normal", "Groß"};
	public static final float[] CUSTOM_COS_SIZES = {0.35f, 0.55f, 0.8f};
	public static final String[] CROSSHAIR_COLOR_NAMES = {"Weiß", "Akzent", "Rosa", "Lila", "Cyan", "Grün", "Rot", "Gelb"};
	public static final int[] CROSSHAIR_COLORS = {0xFFFFFFFF, 0, 0xFFFF8FC7, 0xFFC3A2FF, 0xFF6FE7FF, 0xFF7BE3A3, 0xFFFF5A5F, 0xFFFFE066};
	public static final String[] CROSSHAIR_SIZE_NAMES = {"Klein", "Normal", "Groß"};
	public static final String[] ZOOM_NAMES = {"2x", "4x", "8x", "16x"};
	public static final double[] ZOOM_FACTORS = {2, 4, 8, 16};
	public static final String[] LOW_NAMES = {"Etwas", "Mittel", "Viel"};
	public static final String[] PORTAL_NAMES = {"Leicht", "Stark", "Fast unsichtbar"};
	public static final float[] PORTAL_ALPHA = {0.6f, 0.35f, 0.14f};
	public static final float[] LOW_AMOUNTS = {0.12f, 0.25f, 0.4f};
	public static final String[] SMALL_NAMES = {"75 %", "50 %", "30 %"};
	public static final float[] SMALL_SCALES = {0.75f, 0.5f, 0.3f};
	public static final String[] ARMOR_POS = {"Links in der Mitte", "Rechts in der Mitte", "Links neben der Hotbar", "Rechts neben der Hotbar"};
	public static final String[] ARMOR_VALUE = {"Prozent", "Haltbarkeit (Zahl)", "Nur Balken", "Beides (Prozent + Zahl)"};
	public static final String[] YT_MODES = {"Eingebetteter Player (Standard)", "YouTube-Seite (Erweiterung, jedes Lied)"};
	public static final String[] PLACE_MODES = {"Alle Ebenen", "Nur diese Ebene", "Bis zu dieser Ebene", "Ab dieser Ebene"};
	public static final String[] PLACE_MIRRORS = {"Aus", "Links ↔ rechts", "Vorne ↔ hinten"};
	public static final int[] PLACE_RANGES = {32, 64, 128, Integer.MAX_VALUE / 4};
	public static final String[] PLACE_RANGE_NAMES = {"32 Blöcke", "64 Blöcke", "128 Blöcke", "Unbegrenzt"};
	public static final int[] PLACE_STEPS = {1, 4, 16};
	public static final String[] PLACE_STEP_NAMES = {"1 Block", "4 Blöcke", "16 Blöcke"};
	public static final int[] CLIP_LENGTHS = {30, 60, 120};
	public static final String[] CLIP_LENGTH_NAMES = {"30 Sekunden", "1 Minute", "2 Minuten"};
	public static final int[] CLIP_FPS = {10, 15, 20};
	public static final String[] CLIP_FPS_NAMES = {"10 Bilder/s (sparsam)", "15 Bilder/s", "20 Bilder/s (flüssig)"};
	public static final int[] CLIP_WIDTHS = {640, 854, 1280};
	public static final String[] CLIP_QUALITY_NAMES = {"Niedrig (360p)", "Mittel (480p)", "Hoch (720p)"};
	public static final String[] CLIP_FORMATS = {"MP4 (überall abspielbar)", "AVI (sofort fertig)"};

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
		if (customCosmetics == null) customCosmetics = new java.util.HashMap<>();
		if (crosshairSelection == null) crosshairSelection = "design:1";
		if (equipped == null) equipped = new java.util.HashMap<>();
		if (ytLibrary == null) ytLibrary = new java.util.ArrayList<>();
		if (ytPlaylists == null) ytPlaylists = new java.util.ArrayList<>();
		if (ytApiKey == null) ytApiKey = "";
		ytMode = Math.floorMod(ytMode, 2);
		ytVolume = Math.max(0, Math.min(100, ytVolume));
		musicService = Math.floorMod(musicService, 2);
		if (chatRead == null) chatRead = new java.util.HashMap<>();
		if (placeFile == null) placeFile = "";
		if (clipFolder == null) clipFolder = "";
		armorHudPos = Math.floorMod(armorHudPos, ARMOR_POS.length);
		armorHudValue = Math.floorMod(armorHudValue, ARMOR_VALUE.length);
		placeRot = Math.floorMod(placeRot, 4);
		placeMirror = Math.floorMod(placeMirror, 3);
		placeLayerMode = Math.floorMod(placeLayerMode, 4);
		placeLayer = Math.max(0, placeLayer);
		if (!placeRangeV81) {
			placeRangeV81 = true;
			placeRange = PLACE_RANGES.length - 1;
		}
		placeRange = Math.floorMod(placeRange, PLACE_RANGES.length);
		placeStep = Math.floorMod(placeStep, PLACE_STEPS.length);
		clipLen = Math.floorMod(clipLen, CLIP_LENGTHS.length);
		clipFps = Math.floorMod(clipFps, CLIP_FPS.length);
		clipQuality = Math.floorMod(clipQuality, CLIP_WIDTHS.length);
		clipFormat = Math.floorMod(clipFormat, 2);
		zoomLevel = Math.floorMod(zoomLevel, ZOOM_NAMES.length);
		lowFireLevel = Math.floorMod(lowFireLevel, LOW_NAMES.length);
	portalLevel = Math.floorMod(portalLevel, PORTAL_NAMES.length);
	nameMode = Math.floorMod(nameMode, 3);
	nameColor1 = Math.floorMod(nameColor1, com.oemer.client.feature.NameStyle.COLORS.length);
	nameColor2 = Math.floorMod(nameColor2, com.oemer.client.feature.NameStyle.COLORS.length);
	if (customNameText == null) customNameText = "";
	if (nameLogo == null) nameLogo = "";
	if (customNameText.length() > 32) customNameText = customNameText.substring(0, 32);
		lowShieldLevel = Math.floorMod(lowShieldLevel, LOW_NAMES.length);
		smallerTotemLevel = Math.floorMod(smallerTotemLevel, SMALL_NAMES.length);
		if (markedChests == null) markedChests = new java.util.ArrayList<>();
		kit = Math.floorMod(kit, KITS.length);
		totemPreset = Math.max(0, totemPreset);
		// alte eingebaute Cosmetics gibt es nicht mehr
		equipped.values().removeIf(v -> v == null || !v.startsWith("custom:"));
		cosHalo = cosCrown = cosTopHat = cosWings = cosEars = cosHorns = false;
		if (spotifyClientId == null) spotifyClientId = "";
		designStyle = Math.floorMod(designStyle, DESIGN_STYLES.length);
		totemMode = Math.floorMod(totemMode, TOTEM_MODES.length);
		totemFit = Math.floorMod(totemFit, TOTEM_FITS.length);
		customCosSize = Math.floorMod(customCosSize, CUSTOM_COS_SIZES.length);
		crosshairColor = Math.floorMod(crosshairColor, CROSSHAIR_COLORS.length);
		crosshairSize = Math.floorMod(crosshairSize, CROSSHAIR_SIZE_NAMES.length);
		spotifyHudCorner = Math.floorMod(spotifyHudCorner, CORNERS.length);
	}
}
