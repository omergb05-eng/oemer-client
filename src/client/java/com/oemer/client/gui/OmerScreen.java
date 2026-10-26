package com.oemer.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.OemerKeys;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.ChestMarker;
import com.oemer.client.feature.Crosshair;
import com.oemer.client.feature.CustomCosmetics;
import com.oemer.client.feature.DistantHorizonsBridge;
import com.oemer.client.feature.FilePicker;
import com.oemer.client.feature.GraphicsPresets;
import com.oemer.client.feature.ImageTools;
import com.oemer.client.feature.KitProfiles;
import com.oemer.client.feature.Looks;
import com.oemer.client.feature.LooksPack;
import com.oemer.client.feature.Clipper;
import com.oemer.client.feature.MaterialList;
import com.oemer.client.feature.SchematicGrid;
import com.oemer.client.feature.SchematicPlacer;
import com.oemer.client.feature.NameStyle;
import com.oemer.client.feature.OwnFiles;
import com.oemer.client.feature.Music;
import com.oemer.client.feature.PresetArt;
import com.oemer.client.feature.SkinTotem;
import com.oemer.client.feature.Sounds;
import com.oemer.client.feature.Spotify;
import com.oemer.client.feature.SpriteCosmetics;
import com.oemer.client.feature.Textures;
import com.oemer.client.feature.YouTubeMusic;
import com.oemer.client.online.Firebase;
import com.oemer.client.online.FriendSkins;
import com.oemer.client.online.Social;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Das Ömer-Client-Menü: links die Bereiche (Mods, Settings, Design, Musik, Skin, Kits), oben die Suche,
 * rechts die Inhalte. Jede Mod ist eine Kachel mit Schalter – Klick öffnet ihre Einstellungen.
 */
public class OmerScreen extends Screen {

	enum Section {
		MODS("Mods", "◆", "Alle Mods – an/aus und Einstellungen"),
		SETTINGS("Settings", "⚙", "Allgemeine Einstellungen und Tasten"),
		DESIGN("Design", "✎", "Hintergrund, Vorlagen, Grafik"),
		MUSIK("Musik", "♪", "YouTube-Musik und Spotify"),
		SKIN("Skin", "☺", "Cape, Totem, Cosmetics, Sounds"),
		FREUNDE("Freunde", "☻", "Freunde, Chat und gemeinsam Musik hören"),
		KITS("Kits", "⚔", "Eigene Einstellungen und Tasten für jedes Kit");

		final String title, icon, subtitle;

		Section(String title, String icon, String subtitle) {
			this.title = title;
			this.icon = icon;
			this.subtitle = subtitle;
		}
	}

	/** Eine Mod-Kachel. on/set = null → nur Einstellungen, kein Schalter. */
	record ModInfo(String id, String icon, String name, String desc, BooleanSupplier on, Consumer<Boolean> set, Runnable detail) {
	}

	// ---- bleibt gemerkt, solange das Spiel läuft
	private static Section section = Section.MODS;
	private static String openMod = null;
	private static String query = "";
	private static int skinTab = 0;
	private static int cosmeticSlot = 1;
	private static boolean looksChanged;
	private static boolean skipFade;

	private static final String[] SKIN_TABS = {"Cape", "Totem", "Cosmetics", "Sounds"};

	private static final int PAD = 12;
	private static final int ROW_H = 26;
	private static final int GAP = 8;

	private final Screen parent;
	private final List<Supplier<String>> infoLines = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();
	private final boolean fadeIn;
	private long lastFrame = System.nanoTime();

	// Layout
	private int sideX, sideY, sideW, sideH;
	private int panelX, panelY, panelW, panelH;
	private int cx0, cw, cy;            // Inhalt: links, Breite, Cursor (ohne Scrollen)
	private int contentTop;
	private int flowN, flowIdx, flowW, flowH;
	private int viewTop, viewBottom;
	private double scroll;
	private String pageKey = "";
	private String dynKey = "";

	// Vorschau rechts (optional)
	private Consumer<GuiGraphics> previewArea;
	private int previewX, previewY, previewW, previewH;
	private int mouseXNow, mouseYNow;

	private final List<AbstractWidget> content = new ArrayList<>();
	private final List<Integer> contentBaseY = new ArrayList<>();

	private EditBox search;
	private EditBox ytBox;
	private EditBox keyBox;
	private EditBox plBox;
	private EditBox nameBox;
	/** Playlist, die gerade offen ist (null = Übersicht), und Song, der in eine Playlist soll. */
	private static YouTubeMusic.Playlist openPlaylist;
	private static YouTubeMusic.Track pickTrack;
	private String focusName = "";
	private KeyMapping listening;

	public OmerScreen(Screen parent) {
		super(Component.literal("Ömer Client"));
		this.parent = parent;
		this.fadeIn = !skipFade;
		skipFade = false;
	}

	/** Öffnet direkt den Musik-Bereich (Taste "Musik-Player öffnen"). */
	public static void openMusic() {
		section = Section.MUSIK;
		openMod = null;
		net.minecraft.client.Minecraft.getInstance().setScreen(new OmerScreen(null));
	}

	/** Totem/Sounds/Texturen haben sich geändert → beim Schließen Ressourcen neu laden. */
	public static void markLooksChanged() {
		looksChanged = true;
	}

	/** Für Buttons, die nicht mitscrollen. */
	void addExtra(AbstractWidget w) {
		this.addRenderableWidget(w);
	}

	// =================================================================== Aufbau

	/** Menü-Maßstab: kleiner als Minecrafts GUI-Größe, damit mehr Platz ist (immer ganze Pixel = scharf). */
	private float uiScale = 1f;
	private boolean scaledNow;

	private float computeScale() {
		var win = this.minecraft.getWindow();
		double gs = win.getGuiScale();
		int fbW = win.getWidth(), fbH = win.getHeight();
		int target = (int) Math.max(1, Math.min(gs, Math.min(fbW / 640, fbH / 360)));
		if (target >= gs) return 1f;
		return (float) (target / gs);
	}

	@Override
	protected void init() {
		uiScale = computeScale();
		this.width = Math.round(this.minecraft.getWindow().getGuiScaledWidth() / uiScale);
		this.height = Math.round(this.minecraft.getWindow().getGuiScaledHeight() / uiScale);
		infoLines.clear();
		previewArea = null;
		content.clear();
		contentBaseY.clear();
		ytBox = null;
		keyBox = null;
		plBox = null;
		nameBox = null;
		boxes.clear();
		enters.clear();

		boolean narrow = this.width < 420;
		sideW = narrow ? 34 : 104;
		sideX = PAD;
		sideY = 44;
		sideH = this.height - sideY - 32;
		panelX = sideX + sideW + 10;
		panelY = sideY;
		panelW = this.width - panelX - PAD;
		panelH = sideH;

		// ---- Seitenleiste
		Section[] secs = Section.values();
		int itemH = Math.min(30, (sideH - 12) / secs.length);
		for (int i = 0; i < secs.length; i++) {
			Section s = secs[i];
			int y = sideY + 6 + i * itemH;
			this.addRenderableWidget(new NavItem(sideX + 5, y, sideW - 10, itemH - 4, s, narrow));
		}

		// ---- Suche oben im Inhalt
		int searchW = Math.min(240, panelW - 60 - rightHeaderWidth());
		search = new EditBox(this.font, panelX + 30, panelY + 14, Math.max(60, searchW), 12, Component.literal("Suche"));
		search.setBordered(false);
		search.setMaxLength(40);
		search.setTextColor(0xFFFFFFFF);
		search.setHint(Component.literal("Suchen …"));
		search.setValue(query);
		search.setResponder(v -> {
			if (v.equals(query)) return;
			query = v;
			focusName = "search";
			scroll = 0;
			refresh();
		});
		this.addRenderableWidget(search);

		cx0 = panelX + 14;
		cw = panelW - 28;
		contentTop = panelY + 44;
		cy = contentTop;
		flowN = 0;

		String key = section + "|" + openMod + "|" + skinTab + "|" + friendTab + "|" + chatWith + "|" + OemerConfig.get().musicService;
		if (!key.equals(pageKey)) {
			pageKey = key;
			scroll = 0;
			listening = null;
		}

		if (section == Section.MODS && openMod != null) {
			// "‹ Alle Mods" oben rechts auf Detailseiten
			this.addRenderableWidget(new OmerWidgets.Flat(panelX + panelW - 92, panelY + 10, 80, 20, "‹ Alle Mods", () -> {
				openMod = null;
				refresh();
			}).style(OmerWidgets.Style.GLASS));
		}

		switch (section) {
			case MODS -> {
				if (openMod == null) buildMods();
				else buildModDetail(openMod);
			}
			case SETTINGS -> buildSettings();
			case DESIGN -> buildDesign();
			case MUSIK -> buildMusic();
			case SKIN -> buildSkin();
			case FREUNDE -> buildFriends();
			case KITS -> buildKits();
		}
		endFlow();

		viewTop = contentTop - 4;
		viewBottom = panelY + panelH - 10 - infoLines.size() * 11 - (infoLines.isEmpty() ? 0 : 4);
		if (previewArea != null) previewH = Math.max(40, viewBottom - previewY);
		scroll = Math.max(0, Math.min(maxScroll(), scroll));

		this.addRenderableWidget(new OmerWidgets.Flat(this.width - PAD - 76, this.height - 26, 76, 18,
				"Fertig", this::onClose).style(OmerWidgets.Style.PRIMARY));

		// Fokus wiederherstellen (nach Suche/Neuaufbau)
		EditBox focus = switch (focusName) {
			case "search" -> search;
			case "yt" -> ytBox;
			case "key" -> keyBox;
			case "pl" -> plBox;
			case "nm" -> nameBox;
			default -> boxes.get(focusName);
		};
		if (focus != null) {
			this.setFocused(focus);
			focus.setFocused(true);
		}
		dynKey = dynamicKey();
	}

	/** „Wirklich löschen?“ – erst bei Ja wird die Aktion ausgeführt. */
	private void confirmDelete(String what, Runnable action) {
		this.minecraft.setScreen(new ConfirmScreen(yes -> {
			this.minecraft.setScreen(this);
			if (yes) {
				action.run();
				refresh();
			}
		}, Component.literal("Wirklich löschen?"), Component.literal(what)));
	}

	private void refresh() {
		skipFade = true;
		String f = focusName;
		this.rebuildWidgets();
		focusName = f;
	}

	/** Sachen, die sich von selbst ändern (Suchergebnisse, neue Dateien …) → Seite neu aufbauen. */
	private String dynamicKey() {
		return switch (section) {
			case MUSIK -> YouTubeMusic.results().size() + ":" + (YouTubeMusic.results().isEmpty() ? "" : YouTubeMusic.results().get(0).id)
					+ ":" + OemerConfig.get().ytLibrary.size() + ":" + OemerConfig.get().ytPlaylists.size() + ":" + YouTubeMusic.queue().size()
					+ ":" + OemerConfig.get().ytPlaylists.stream().mapToInt(x -> x.tracks.size()).sum()
					+ ":" + Spotify.status();
			case SKIN -> CapeManager.choices().size() + ":" + LooksPack.totemChoices().size() + ":" + CustomCosmetics.files().size();
			case DESIGN -> Looks.files().size() + "";
			case FREUNDE -> {
				StringBuilder sb = new StringBuilder().append(Social.version()).append(Social.busy()).append(Social.loggedIn()).append(Social.status());
				for (Social.Friend f : Social.friends()) {
					sb.append(f.online() ? 1 : 0).append(Social.unread(f.uid));
					sb.append(f.listening == null ? "-" : f.listening.title() + f.listening.playing());
				}
				yield sb.toString();
			}
			case MODS -> openMod != null && openMod.equals("schematic") ? MaterialList.files().size() + ":" + SchematicPlacer.layers() + ":" + OemerConfig.get().placeSet : openMod != null && openMod.equals("material") ? ChestMarker.here().size() + ":" + MaterialList.files().size() : "";
			default -> "";
		};
	}

	@Override
	public void tick() {
		super.tick();
		String k = dynamicKey();
		if (!k.equals(dynKey)) {
			if (ytBox != null && ytBox.isFocused()) focusName = "yt";
			if (plBox != null && plBox.isFocused()) focusName = "pl";
			if (nameBox != null && nameBox.isFocused()) focusName = "nm";
			for (var en : boxes.entrySet()) if (en.getValue().isFocused()) focusName = en.getKey();
			refresh();
		}
		if (section == Section.FREUNDE) {
			Social.watch();
			if (friendTab == 1 && !chatWith.isEmpty()) Social.markRead(chatWith);
		}
	}

	// ------------------------------------------------------------- Helfer

	private boolean match(String... texts) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return true;
		for (String t : texts) if (t != null && t.toLowerCase(Locale.ROOT).contains(q)) return true;
		return false;
	}

	private <T extends AbstractWidget> T addContent(T w) {
		content.add(w);
		contentBaseY.add(w.getY());
		this.addWidget(w);
		return w;
	}

	/** Spalten-Raster beginnen: n Spalten, Höhe h. */
	private void flow(int n, int h) {
		endFlow();
		flowN = Math.max(1, n);
		flowIdx = 0;
		flowH = h;
		flowW = (cw - (flowN - 1) * GAP) / flowN;
	}

	private int[] flowSlot() {
		if (flowN == 0) flow(1, ROW_H);
		int x = cx0 + flowIdx * (flowW + GAP);
		int[] r = {x, cy, flowW, flowH};
		flowIdx++;
		if (flowIdx >= flowN) {
			flowIdx = 0;
			cy += flowH + GAP;
		}
		return r;
	}

	private void endFlow() {
		if (flowN > 0 && flowIdx > 0) cy += flowH + GAP;
		flowN = 0;
		flowIdx = 0;
	}

	/** Einstellungs-Zeilen: 2 Spalten, wenn genug Platz. */
	private void rows() {
		flow(cw >= 440 ? 2 : 1, ROW_H);
	}

	private void heading(String text) {
		endFlow();
		cy += 4;
		addContent(new OmerWidgets.Label(cx0, cy, cw, 18, text, true, Theme.TEXT));
		cy += 18 + GAP;
	}

	private void note(String text) {
		endFlow();
		addContent(new OmerWidgets.Label(cx0, cy, cw, 12, text, false, Theme.TEXT_DIM));
		cy += 12 + 4;
	}

	private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String tip) {
		if (!match(label, tip)) return;
		if (flowN == 0) rows();
		int[] p = flowSlot();
		var t = new OmerWidgets.Toggle(p[0], p[1], p[2], p[3], label, get, v -> {
			set.accept(v);
			OemerConfig.save();
		}, false);
		if (!tip.isEmpty()) t.tip(tip);
		addContent(t);
	}

	private void cycle(String label, String[] options, IntSupplier get, IntConsumer set) {
		cycle(label, () -> options, get, set);
	}

	private void cycle(String label, Supplier<String[]> options, IntSupplier get, IntConsumer set) {
		if (!match(label)) return;
		if (flowN == 0) rows();
		int[] p = flowSlot();
		addContent(new OmerWidgets.Cycle(p[0], p[1], p[2], p[3], label, options, get, v -> {
			set.accept(v);
			OemerConfig.save();
		}));
	}

	private void button(String label, Runnable action, OmerWidgets.Style style) {
		button(label, action, style, "");
	}

	private void button(String label, Runnable action, OmerWidgets.Style style, String tip) {
		if (!match(label, tip)) return;
		if (flowN == 0) rows();
		int[] p = flowSlot();
		var b = new OmerWidgets.Flat(p[0], p[1], p[2], p[3], label, action).style(style);
		if (!tip.isEmpty()) b.tip(tip);
		addContent(b);
	}

	/** Reihe aus mehreren gleich breiten Buttons. */
	private void buttonRow(String[] labels, Runnable[] actions, IntSupplier selected) {
		endFlow();
		int n = labels.length;
		int w = (cw - (n - 1) * 6) / n;
		for (int i = 0; i < n; i++) {
			final int idx = i;
			addContent(new OmerWidgets.Flat(cx0 + i * (w + 6), cy, w, ROW_H, labels[i], actions[i],
					() -> selected != null && selected.getAsInt() == idx));
		}
		cy += ROW_H + GAP;
	}

	private void keyRow(KeyMapping key, String label, String hint) {
		if (!match(label, hint, key.getTranslatedKeyMessage().getString())) return;
		if (flowN == 0) flow(cw >= 440 ? 2 : 1, ROW_H + 4);
		int[] p = flowSlot();
		addContent(new OmerWidgets.KeyRow(p[0], p[1], p[2], p[3], label, hint,
				() -> key.isUnbound() ? "—" : key.getTranslatedKeyMessage().getString(),
				() -> listening == key, () -> conflict(key), () -> {
			listening = listening == key ? null : key;
		}).tip("Klicken, dann neue Taste drücken · ESC = Taste löschen"));
	}

	private boolean conflict(KeyMapping key) {
		if (key.isUnbound()) return false;
		for (KeyMapping other : this.minecraft.options.keyMappings) {
			if (other != key && !other.isUnbound() && other.same(key)) return true;
		}
		return false;
	}

	private void info(Supplier<String> line) {
		infoLines.add(line);
	}

	private void usePreview(Consumer<GuiGraphics> draw) {
		if (cw < 400) return;
		previewArea = draw;
		int pw = Math.min(260, cw * 2 / 5);
		previewW = pw;
		previewX = cx0 + cw - pw;
		previewY = cy;
		cw = cw - pw - 14;
	}

	private static void open(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (Exception ignored) {
		}
		Util.getPlatform().openPath(dir);
	}

	private void go(Section s) {
		if (section == s && openMod == null) return;
		section = s;
		openMod = null;
		query = "";
		focusName = "";
		refresh();
	}

	private void openModPage(String id) {
		openMod = id;
		query = "";
		focusName = "";
		refresh();
	}

	// =================================================================== Mods

	private List<ModInfo> mods() {
		OemerConfig c = OemerConfig.get();
		List<ModInfo> l = new ArrayList<>();
		l.add(new ModInfo("fullbright", "☀", "Fullbright", "Alles hell, auch nachts", () -> c.fullbright, v -> c.fullbright = v, null));
		l.add(new ModInfo("freecam", "✈", "Free Cam", "Frei fliegen, nicht durch Blöcke", () -> c.freecam,
				v -> c.freecam = v && this.minecraft.level != null, null));
		l.add(new ModInfo("zoom", "⌕", "Just Zoom", "Taste halten = Zoom", () -> c.zoomOn, v -> c.zoomOn = v, null));
		l.add(new ModInfo("crosshair", "✛", "Custom Crosshair", "Eigenes Fadenkreuz", () -> c.customCrosshair, v -> c.customCrosshair = v, null));
		l.add(new ModInfo("material", "▤", "Material List", "Blöcke für deine Schematic + Kisten", () -> c.materialList, v -> c.materialList = v, null));
		l.add(new ModInfo("schematic", "▦", "Schematic-Platzierung", "Hologramm setzen, drehen, Ebene für Ebene", () -> c.placeOn, v -> c.placeOn = v, null));
		l.add(new ModInfo("clipper", "●", "Clipper", "Taste drücken = letzte 30 s / 1 / 2 min speichern", () -> c.clipperOn, v -> {
			c.clipperOn = v;
			if (!v) com.oemer.client.feature.Clipper.clear();
		}, null));
		l.add(new ModInfo("loading", "◔", "Ömer-Ladescreen", "Eigener Ladebildschirm beim Start und in Welten", () -> c.customLoading, v -> c.customLoading = v, null));
		l.add(new ModInfo("nameicon", "☾", "Ömer-Symbol", "Kleines Symbol über deinem Namen und bei Freunden", () -> c.nameIcon, v -> c.nameIcon = v, null));
		l.add(new ModInfo("armorhud", "⛨", "Armor HUD", "Zeigt deine Rüstung mit Haltbarkeit", () -> c.armorHud, v -> c.armorHud = v, null));
		l.add(new ModInfo("kithud", "⚔", "Kit-HUD", "Zeigt deine Kit-Items im Spiel", () -> c.kitHud, v -> c.kitHud = v, null));
		l.add(new ModInfo("appleskin", "🍏", "AppleSkin", "Sättigung + Essens-Vorschau", () -> c.appleSkin, v -> c.appleSkin = v, null));
		l.add(new ModInfo("clearglass", "◻", "Clear Glass", "Klares Glas ohne Streifen", () -> c.clearGlass, v -> {
			c.clearGlass = v;
			looksChanged = true;
		}, null));
		l.add(new ModInfo("darkgui", "◼", "Dark GUI", "Dunkle Kisten und Inventare", () -> c.darkGui, v -> {
			c.darkGui = v;
			looksChanged = true;
		}, null));
		l.add(new ModInfo("distanthorizons", "⛰", "Distant Horizons", "Riesige Sichtweite (LODs)", () -> c.distantHorizons, v -> {
			c.distantHorizons = v;
			DistantHorizonsBridge.changed();
		}, null));
		l.add(new ModInfo("lowfire", "🔥", "Low Fire", "Feuer am Bildschirm tiefer", () -> c.lowFire, v -> c.lowFire = v, null));
		l.add(new ModInfo("lowshield", "⛨", "Low Shield", "Schild in der Hand tiefer", () -> c.lowShield, v -> c.lowShield = v, null));
		l.add(new ModInfo("nodarkness", "☾", "No Darkness", "Kein Dunkelheits-Effekt", () -> c.noDarkness, v -> c.noDarkness = v, null));
		l.add(new ModInfo("nofog", "☁", "No Fog", "Kein Nebel an Land", () -> c.noFog, v -> c.noFog = v, null));
		l.add(new ModInfo("nopumpkin", "◕", "No Pumpkin Blur", "Kein Kürbis-Rand", () -> c.noPumpkin, v -> c.noPumpkin = v, null));
		l.add(new ModInfo("weather", "☂", "Wetter aus", "Kein Regen, kein Gewitter", () -> c.noWeather, v -> c.noWeather = v, null));
		l.add(new ModInfo("notetuner", "♫", "Note Block Tuner", "Zeigt Ton + Instrument", () -> c.noteTuner, v -> c.noteTuner = v, null));
		l.add(new ModInfo("nobedrock", "▧", "NoBedrock", "Bedrock sieht aus wie Deepslate", () -> c.noBedrock, v -> {
			c.noBedrock = v;
			looksChanged = true;
		}, null));
		l.add(new ModInfo("shulker", "▣", "Shulker Box Tooltip", "Inhalt als Raster im Tooltip", () -> c.shulkerTooltip, v -> c.shulkerTooltip = v, null));
		l.add(new ModInfo("smalltotem", "✦", "Smaller Totem", "Kleineres Totem + Animation", () -> c.smallerTotem, v -> c.smallerTotem = v, null));
		l.add(new ModInfo("tnttimer", "⏱", "TNT Timer", "Sekunden bis zur Explosion", () -> c.tntTimer, v -> c.tntTimer = v, null));
		l.add(new ModInfo("noexplosion", "✹", "No Explosion Particles", "Keine Explosions-Wolken", () -> c.noExplosionParticles, v -> c.noExplosionParticles = v, null));
		l.add(new ModInfo("clearportal", "◈", "Transparente Netherportale", "Portale durchsichtig", () -> c.clearPortal, v -> {
			c.clearPortal = v;
			looksChanged = true;
		}, null));
		l.add(new ModInfo("customname", "✎", "Own Custom Name", "Name mit Farbe, Verlauf, Rainbow", () -> c.customName, v -> c.customName = v, null));
		l.add(new ModInfo("musichud", "♪", "Musik-HUD", "Zeigt den Song im Spiel", () -> c.spotifyHud, v -> c.spotifyHud = v, null));
		l.add(new ModInfo("cosmetics", "☺", "Cosmetics", "Deine eigenen Bild-Cosmetics", () -> c.cosmeticsOn, v -> c.cosmeticsOn = v, null));
		l.add(new ModInfo("totem", "♥", "Custom Totem", "Eigenes Totem-Bild", () -> c.customTotem, v -> {
			c.customTotem = v;
			looksChanged = true;
		}, null));
		l.add(new ModInfo("cape", "⚑", "Custom Cape", "Eigenes Cape", () -> c.customCape, v -> c.customCape = v, null));
		l.add(new ModInfo("sounds", "♬", "Custom Sounds", "Eigene Sounds", () -> c.customSounds, v -> {
			c.customSounds = v;
			looksChanged = true;
		}, null));
		return l;
	}

	private void buildMods() {
		List<ModInfo> list = new ArrayList<>();
		for (ModInfo m : mods()) if (match(m.name(), m.desc(), m.id())) list.add(m);
		int n = cw >= 600 ? 3 : cw >= 360 ? 2 : 1;
		flow(n, 44);
		for (ModInfo m : list) {
			int[] p = flowSlot();
			addContent(new OmerWidgets.ModCard(p[0], p[1], p[2], p[3], m.icon(), m.name(), m.desc(), m.on(), v -> {
				m.set().accept(v);
				OemerConfig.save();
			}, () -> openModPage(m.id())).tip("Klick = Einstellungen · Schalter = an/aus"));
		}
		endFlow();
		if (list.isEmpty()) note("Keine Mod gefunden für „" + query + "“");
		info(() -> list.size() + " Mods · Klick auf eine Kachel öffnet ihre Einstellungen");
	}

	private void buildModDetail(String id) {
		OemerConfig c = OemerConfig.get();
		ModInfo m = null;
		for (ModInfo x : mods()) if (x.id().equals(id)) m = x;
		if (m == null) {
			openMod = null;
			buildMods();
			return;
		}
		final ModInfo mod = m;
		// Kopf: großes Symbol, Name, Text, Schalter
		addContent(new OmerWidgets.Painter(cx0, cy, cw, 40, (g, x, y, w, h, mx, my) -> {
			boolean onNow = mod.on() != null && mod.on().getAsBoolean();
			Draw.round(g, x, y, 40, 40, 8, onNow ? Theme.accent(0xE0) : Theme.white(0x18));
			int ic = onNow && (Theme.accent() & 0xFFFFFF) == 0xFFFFFF ? 0xFF0B0B0F : 0xFFFFFFFF;
			g.pose().pushMatrix();
			g.pose().translate(x + 20, y + 13);
			g.pose().scale(1.8f);
			Draw.centered(g, mod.icon(), 0, 0, ic);
			g.pose().popMatrix();
			g.pose().pushMatrix();
			g.pose().translate(x + 52, y + 6);
			g.pose().scale(1.4f);
			Draw.text(g, mod.name(), 0, 0, Theme.TEXT);
			g.pose().popMatrix();
			Draw.text(g, Draw.fit(mod.desc(), w - 60), x + 52, y + 24, Theme.TEXT_DIM);
		}, null));
		cy += 40 + GAP + 4;
		if (mod.on() != null) {
			flow(cw >= 440 ? 2 : 1, ROW_H);
			int[] p = flowSlot();
			addContent(new OmerWidgets.Toggle(p[0], p[1], p[2], p[3], "Aktiv", mod.on(), v -> {
				mod.set().accept(v);
				OemerConfig.save();
			}, false));
		}
		switch (id) {
			case "fullbright" -> {
				endFlow();
				keyRow(OemerKeys.fullbright, "Taste: Fullbright", "");
			}
			case "freecam" -> {
				cycle("Tempo", OemerConfig.FREECAM_SPEED_NAMES, () -> c.freecamSpeed, v -> c.freecamSpeed = v);
				endFlow();
				keyRow(OemerKeys.freecam, "Taste: Free Cam", "");
				info(() -> this.minecraft.level != null ? "WASD fliegen, Leertaste hoch, Shift runter, Sprinten = schneller."
						: "Free Cam geht nur in einer Welt oder auf Servern.");
			}
			case "zoom" -> {
				cycle("Stärke", OemerConfig.ZOOM_NAMES, () -> c.zoomLevel, v -> c.zoomLevel = v);
				toggle("Mausrad = mehr/weniger Zoom", () -> c.zoomScroll, v -> c.zoomScroll = v, "");
				toggle("Weich zoomen", () -> c.zoomSmooth, v -> c.zoomSmooth = v, "");
				endFlow();
				keyRow(OemerKeys.zoom, "Taste: Zoom (halten)", "");
			}
			case "crosshair" -> buildCrosshair();
			case "material" -> buildMaterial();
			case "schematic" -> buildSchematicPlace();
			case "armorhud" -> {
				cycle("Ort", OemerConfig.ARMOR_POS, () -> c.armorHudPos, v -> c.armorHudPos = v);
				cycle("Anzeige", OemerConfig.ARMOR_VALUE, () -> c.armorHudValue, v -> c.armorHudValue = v);
				toggle("Hände mitzeigen", () -> c.armorHudHands, v -> c.armorHudHands = v, "Zeigt auch Item in der Haupt- und Zweithand");
				endFlow();
				info(() -> "Helm, Brustplatte, Hose und Schuhe mit Haltbarkeit (grün = heil, rot = fast kaputt). Größe: Settings → HUD-Größe.");
			}
			case "loading" -> info(() -> "Gilt beim Start von Minecraft, beim Betreten einer Welt und bei Portalen. Aus = normaler Ladebildschirm.");
			case "nameicon" -> info(() -> "Das Symbol steht eine Zeile über dem Namen – bei dir (in F5 / Free Cam), bei Freunden mit Ömer Client und in der Tab-Liste.");
			case "clipper" -> buildClipper();
			case "kithud" -> {
				cycle("Ort", OemerConfig.CORNERS, () -> c.kitHudCorner, v -> c.kitHudCorner = v);
				cycle("HUD-Größe", OemerConfig.HUD_SCALE_NAMES, () -> c.hudScale, v -> c.hudScale = v);
				button("Kits & Tasten öffnen", () -> go(Section.KITS), OmerWidgets.Style.PRIMARY);
				info(() -> "Aktives Kit: " + KitProfiles.name(KitProfiles.active()) + " · " + com.oemer.client.feature.Hud.kitDescription(c.kit));
			}
			case "appleskin" -> info(() -> "Goldener Rahmen = Sättigung · Blinkende Keulen = so viel bringt dein Essen.");
			case "clearglass", "darkgui", "nobedrock" -> info(() -> "Wird beim Schließen vom Menü geladen (dauert kurz).");
			case "distanthorizons" -> {
				button("Distant-Horizons-Einstellungen", () -> {
					Screen s = DistantHorizonsBridge.configScreen(this);
					if (s != null) this.minecraft.setScreen(s);
				}, OmerWidgets.Style.GLASS);
				info(() -> DistantHorizonsBridge.installed()
						? "Version " + DistantHorizonsBridge.version() + " · " + DistantHorizonsBridge.status()
						: "Distant Horizons ist nicht geladen.");
				info(() -> "Braucht viel Leistung – für PvP lieber aus.");
			}
			case "noexplosion" -> info(() -> "Explosionen machen weiter Geräusche und Schaden – nur die Rauch-/Explosions-Wolken fehlen.");
			case "clearportal" -> {
				cycle("Wie durchsichtig", OemerConfig.PORTAL_NAMES, () -> c.portalLevel, v -> {
					c.portalLevel = v;
					looksChanged = true;
				});
				toggle("Portal-Wackeln beim Durchgehen aus", () -> c.portalOverlayOff, v -> c.portalOverlayOff = v, "Kein lila Bildschirm, wenn du im Portal stehst");
				info(() -> "Die Textur wird beim Schließen vom Menü geladen (dauert kurz).");
			}
			case "customname" -> buildCustomName();
			case "lowfire" -> cycle("Wie tief", OemerConfig.LOW_NAMES, () -> c.lowFireLevel, v -> c.lowFireLevel = v);
			case "lowshield" -> cycle("Wie tief", OemerConfig.LOW_NAMES, () -> c.lowShieldLevel, v -> c.lowShieldLevel = v);
			case "smalltotem" -> cycle("Größe", OemerConfig.SMALL_NAMES, () -> c.smallerTotemLevel, v -> c.smallerTotemLevel = v);
			case "weather" -> {
				endFlow();
				keyRow(OemerKeys.weatherToggle, "Taste: Wetter an/aus", "");
			}
			case "notetuner" -> info(() -> "Schau auf einen Notenblock: Ton, Instrument und Klaviatur erscheinen unter dem Fadenkreuz.");
			case "tnttimer" -> info(() -> "Über jedem gezündeten TNT stehen die Sekunden (grün → gelb → rot).");
			case "musichud" -> {
				cycle("Ort", OemerConfig.CORNERS, () -> c.spotifyHudCorner, v -> c.spotifyHudCorner = v);
				button("Musik öffnen", () -> go(Section.MUSIK), OmerWidgets.Style.PRIMARY);
			}
			case "cosmetics" -> {
				button("Cosmetics ausrüsten", () -> {
					skinTab = 2;
					go(Section.SKIN);
				}, OmerWidgets.Style.PRIMARY);
				endFlow();
				keyRow(OemerKeys.cosmeticsToggle, "Taste: Cosmetics an/aus", "");
			}
			case "totem" -> button("Totem auswählen", () -> {
				skinTab = 1;
				go(Section.SKIN);
			}, OmerWidgets.Style.PRIMARY);
			case "cape" -> button("Cape auswählen", () -> {
				skinTab = 0;
				go(Section.SKIN);
			}, OmerWidgets.Style.PRIMARY);
			case "sounds" -> button("Sounds auswählen", () -> {
				skinTab = 3;
				go(Section.SKIN);
			}, OmerWidgets.Style.PRIMARY);
			default -> {
			}
		}
		endFlow();
	}

	private void buildCrosshair() {
		OemerConfig c = OemerConfig.get();
		cycle("Farbe", OemerConfig.CROSSHAIR_COLOR_NAMES, () -> c.crosshairColor, v -> c.crosshairColor = v);
		cycle("Größe", OemerConfig.CROSSHAIR_SIZE_NAMES, () -> c.crosshairSize, v -> c.crosshairSize = v);
		button("Selbst malen", () -> this.minecraft.setScreen(new CapeEditorScreen(this, CapeEditorScreen.Mode.CROSSHAIR)), OmerWidgets.Style.GLASS);
		button("Datei wählen", () -> FilePicker.pickImage("Crosshair wählen", Crosshair.folder(), name -> {
			c.crosshairSelection = name;
			c.customCrosshair = true;
			OemerConfig.save();
			refresh();
		}), OmerWidgets.Style.GLASS);
		if (!c.crosshairSelection.startsWith("design:")) {
			button("Gewähltes Crosshair löschen", () -> {
				String sel = c.crosshairSelection;
				confirmDelete("Crosshair „" + Crosshair.displayName(sel) + "“ löschen?", () -> OwnFiles.crosshair(sel));
			}, OmerWidgets.Style.GLASS, "Löscht dein eigenes Bild");
		}
		heading("Vorlagen");
		List<String> list = Crosshair.choices();
		flow(Math.max(3, cw / 70), 64);
		for (String ch : list) {
			int[] p = flowSlot();
			addContent(new OmerWidgets.Painter(p[0], p[1], p[2], p[3], (g, x, y, w, h, mx, my) -> {
				boolean sel = c.crosshairSelection.equals(ch);
				boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
				Draw.round(g, x, y, w, h, 6, sel ? Theme.accent(0x30) : Theme.white(hover ? 0x1C : 0x0E));
				Draw.roundBorder(g, x, y, w, h, 6, sel ? Theme.accent(0xF0) : Theme.border(hover ? 1f : 0f));
				Draw.round(g, x + 4, y + 4, w - 8, h - 22, 4, 0xFF4F7FC2);
				String old = c.crosshairSelection;
				c.crosshairSelection = ch;
				Crosshair.draw(g, x + w / 2f, y + 4 + (h - 22) / 2f, Crosshair.scale() * 1.2f);
				c.crosshairSelection = old;
				Draw.centered(g, Draw.fit(Crosshair.displayName(ch), w - 4), x + w / 2, y + h - 13, Theme.TEXT_SOFT);
			}, () -> {
				c.crosshairSelection = ch;
				c.customCrosshair = true;
				OemerConfig.save();
			}));
		}
		endFlow();
		keyRow(OemerKeys.crosshairToggle, "Taste: Crosshair an/aus", "");
		info(() -> "PNGs (z. B. 15x15) gehen auch – „Datei wählen“ oder in den Ordner crosshairs/ legen.");
	}

	private void buildCustomName() {
		OemerConfig c = OemerConfig.get();
		endFlow();
		addContent(new OmerWidgets.Painter(cx0, cy, cw, 34, (g, x, y, w, h, mx, my) -> {
			Draw.round(g, x, y, w, h, 8, 0x50000000);
			Draw.roundBorder(g, x, y, w, h, 8, Theme.white(0x22));
			g.pose().pushMatrix();
			g.pose().translate(x + 14, y + 10);
			g.pose().scale(1.5f);
			g.drawString(this.font, NameStyle.display(), 0, 0, 0xFFFFFFFF);
			g.pose().popMatrix();
		}, null));
		cy += 34 + GAP;
		nameBox = new EditBox(this.font, cx0 + 10, cy + 8, cw - 20, 12, Component.literal("Name"));
		nameBox.setBordered(false);
		nameBox.setMaxLength(32);
		nameBox.setTextColor(0xFFFFFFFF);
		nameBox.setHint(Component.literal("Dein Name (leer = dein echter Name)"));
		nameBox.setValue(c.customNameText);
		nameBox.setResponder(v -> {
			c.customNameText = v;
			OemerConfig.save();
		});
		addContent(nameBox);
		cy += ROW_H + GAP;
		cycle("Stil", NameStyle.MODES, () -> c.nameMode, v -> {
			c.nameMode = v;
			refresh();
		});
		cycle("Farbe", NameStyle.COLOR_NAMES, () -> c.nameColor1, v -> c.nameColor1 = v);
		if (c.nameMode == 1) cycle("Zweite Farbe (Verlauf)", NameStyle.COLOR_NAMES, () -> c.nameColor2, v -> c.nameColor2 = v);
		toggle("Fett", () -> c.nameBold, v -> c.nameBold = v, "");
		toggle("Im Chat ersetzen", () -> c.nameInChat, v -> c.nameInChat = v, "Dein Name erscheint im Chat in deiner Farbe");
		toggle("Namensschild bei mir", () -> c.nameShowSelf, v -> c.nameShowSelf = v, "In F5 und mit Free Cam siehst du dein Schild");
		endFlow();
		info(() -> "Nur du siehst den Namen (Tab-Liste, Chat, Schild). Andere sehen weiter deinen echten Namen.");
	}

	private void buildMaterial() {
		OemerConfig c = OemerConfig.get();
		cycle("Schematic", () -> {
			List<String> f = MaterialList.files();
			return f.isEmpty() ? new String[]{"(keine Datei)"} : f.stream().map(n -> n.replaceAll("\\.(litematic|schem)$", "")).toArray(String[]::new);
		}, () -> Math.max(0, MaterialList.files().indexOf(c.schematic)), v -> {
			List<String> f = MaterialList.files();
			if (!f.isEmpty()) {
				c.schematic = f.get(v);
				c.materialDone.clear();
				MaterialList.reload();
			}
		});
		cycle("Ort im HUD", OemerConfig.CORNERS, () -> c.materialListCorner, v -> c.materialListCorner = v);
		button("Liste öffnen & abhaken", () -> this.minecraft.setScreen(new MaterialListScreen(this)), OmerWidgets.Style.PRIMARY);
		button("Schematic-Ordner öffnen", () -> open(MaterialList.folder()), OmerWidgets.Style.GLASS);
	button("Schematic löschen", () -> {
		String sn = c.schematic;
		if (!sn.isEmpty()) confirmDelete("Schematic „" + sn + "“ löschen?", () -> OwnFiles.schematic(sn));
	}, OmerWidgets.Style.GLASS, "Löscht die gewählte Schematic-Datei");
		if (c.schematic.isEmpty() && !MaterialList.files().isEmpty()) c.schematic = MaterialList.files().get(0);

		heading("Kisten");
		note("Sneaken + Rechtsklick mit einer Spitzhacke auf eine Kiste = markieren. Einmal öffnen, dann zählt ihr Inhalt mit.");
		toggle("Kisten mitzählen", () -> c.chestCount, v -> c.chestCount = v, "Inhalt der markierten Kisten zählt in der Material List");
		toggle("Kisten leuchten", () -> c.chestHighlight, v -> c.chestHighlight = v, "Markierte Kisten leuchten in der Welt");
		endFlow();
		List<OemerConfig.MarkedChest> chests = ChestMarker.here();
		for (OemerConfig.MarkedChest m : chests) {
			int items = m.items.values().stream().mapToInt(Integer::intValue).sum();
			String text = "Kiste " + m.x + " " + m.y + " " + m.z + " · " + (m.updated == 0 ? "noch nicht geöffnet" : items + " Items");
			addContent(new OmerWidgets.Painter(cx0, cy, cw - 90, ROW_H, (g, x, y, w, h, mx, my) -> {
				Draw.round(g, x, y, w, h, 4, Theme.white(0x0E));
				Draw.text(g, "▣", x + 8, y + 9, Theme.accent());
				Draw.text(g, Draw.fit(text, w - 30), x + 22, y + 9, Theme.TEXT_SOFT);
			}, null));
			addContent(new OmerWidgets.Flat(cx0 + cw - 84, cy, 84, ROW_H, "Entfernen", () -> {
				ChestMarker.remove(m);
				refresh();
			}));
			cy += ROW_H + 4;
		}
		if (chests.isEmpty()) note(this.minecraft.level == null ? "Kisten markierst du im Spiel." : "Noch keine Kiste markiert.");
		endFlow();
		keyRow(OemerKeys.materialToggle, "Taste: Material List an/aus", "");
		keyRow(OemerKeys.materialOpen, "Taste: Liste öffnen", "");
		info(() -> {
			if (c.schematic.isEmpty()) return ".litematic oder .schem in den Ordner schematics/ legen.";
			if (!MaterialList.error().isEmpty()) return "Fehler: " + MaterialList.error();
			int sum = 0;
			for (var e : MaterialList.entries()) sum += e.needed();
			return MaterialList.entries().size() + " verschiedene Blöcke, " + sum + " insgesamt.";
		});
	}

	// ================================================================ Schematic-Platzierung

	private void buildSchematicPlace() {
		OemerConfig c = OemerConfig.get();
		List<String> files = MaterialList.files();
		if (c.placeFile.isEmpty() && !files.isEmpty()) c.placeFile = files.get(0);
		cycle("Schematic", () -> {
			List<String> f = MaterialList.files();
			return f.isEmpty() ? new String[]{"(keine Datei)"} : f.stream().map(n -> n.replaceAll("\\.(litematic|schem)$", "")).toArray(String[]::new);
		}, () -> Math.max(0, MaterialList.files().indexOf(c.placeFile)), v -> {
			List<String> f = MaterialList.files();
			if (!f.isEmpty()) {
				c.placeFile = f.get(v);
				c.placeLayer = 0;
				SchematicPlacer.reload();
			}
		});
		button("Schematic hinzufügen", () -> FilePicker.pickSchematic(SchematicPlacer.folder(), name -> {
			c.placeFile = name;
			c.placeLayer = 0;
			OemerConfig.save();
			SchematicPlacer.reload();
			refresh();
		}), OmerWidgets.Style.PRIMARY, "Wähle eine .litematic oder .schem – sie wird in den Ordner kopiert");
		button("Ordner öffnen", () -> open(SchematicPlacer.folder()), OmerWidgets.Style.GLASS);
		endFlow();

		heading("Platzieren");
		note("Auf einen Block schauen, „Dort setzen“ drücken – die Schematic erscheint daneben.");
		button("Dort setzen (Block, auf den ich schaue)", () -> {
			if (this.minecraft.level == null) return;
			Minecraft mc = this.minecraft;
			mc.setScreen(null);
			mc.execute(() -> {
				if (!SchematicPlacer.setLooking(mc)) SchematicPlacer.setHere(mc);
			});
		}, OmerWidgets.Style.PRIMARY, "Menü schließt sich, die Schematic erscheint dort");
		button("Hier setzen (wo ich stehe)", () -> {
			if (this.minecraft.level != null) SchematicPlacer.setHere(this.minecraft);
		}, OmerWidgets.Style.GLASS);
		endFlow();
		cycle("Schrittweite", OemerConfig.PLACE_STEP_NAMES, () -> c.placeStep, v -> c.placeStep = v);
		endFlow();
		buttonRow(new String[]{"◄ X−", "X+ ►", "▼ Y−", "Y+ ▲", "Z− ▲", "Z+ ▼"}, new Runnable[]{
				() -> SchematicPlacer.move(-1, 0, 0), () -> SchematicPlacer.move(1, 0, 0),
				() -> SchematicPlacer.move(0, -1, 0), () -> SchematicPlacer.move(0, 1, 0),
				() -> SchematicPlacer.move(0, 0, -1), () -> SchematicPlacer.move(0, 0, 1)}, null);
		buttonRow(new String[]{"Drehen 90°", "Spiegeln", "Zurücksetzen"}, new Runnable[]{
				SchematicPlacer::rotate,
				() -> {
					c.placeMirror = (c.placeMirror + 1) % 3;
					SchematicPlacer.changed();
				},
				() -> {
					c.placeRot = 0;
					c.placeMirror = 0;
					c.placeSet = false;
					SchematicPlacer.changed();
				}}, null);
		info(() -> {
			if (!c.placeSet) return "Noch nicht gesetzt.";
			return "Position " + c.placeX + " " + c.placeY + " " + c.placeZ + " · gedreht " + c.placeRot * 90 + "° · Spiegel: " + OemerConfig.PLACE_MIRRORS[c.placeMirror];
		});

		heading("Ebene für Ebene");
		cycle("Anzeige", OemerConfig.PLACE_MODES, () -> c.placeLayerMode, v -> {
			c.placeLayerMode = v;
			SchematicPlacer.changed();
		});
		endFlow();
		buttonRow(new String[]{"Ebene −", "Ebene +", "Auf meine Höhe"}, new Runnable[]{
				() -> SchematicPlacer.layer(-1), () -> SchematicPlacer.layer(1),
				() -> SchematicPlacer.layerToPlayer(this.minecraft)}, null);
		info(() -> SchematicPlacer.layers() == 0 ? "" : "Ebene " + (c.placeLayer + 1) + " von " + SchematicPlacer.layers()
				+ " · Y = " + (c.placeY + c.placeLayer));

		heading("Anzeige");
		cycle("Reichweite", OemerConfig.PLACE_RANGE_NAMES, () -> c.placeRange, v -> {
			c.placeRange = v;
			SchematicPlacer.changed();
		});
		toggle("Falsche Blöcke rot", () -> c.placeShowWrong, v -> {
			c.placeShowWrong = v;
			SchematicPlacer.changed();
		}, "Rot = dort steht ein anderer Block");
		toggle("Richtige Blöcke grün", () -> c.placeShowCorrect, v -> {
			c.placeShowCorrect = v;
			SchematicPlacer.changed();
		}, "Grün = Block stimmt schon");
		toggle("Rahmen um die Schematic", () -> c.placeBox, v -> c.placeBox = v, "Zeigt die Größe der Schematic");
		endFlow();
		keyRow(OemerKeys.placeToggle, "Taste: Schematic an/aus", "");
		keyRow(OemerKeys.placeHere, "Taste: dort setzen", "");
		keyRow(OemerKeys.placeLayerUp, "Taste: Ebene höher", "");
		keyRow(OemerKeys.placeLayerDown, "Taste: Ebene tiefer", "");
		info(() -> SchematicPlacer.stats());
		info(() -> {
			SchematicGrid g = SchematicPlacer.grid();
			if (g == null) return files.isEmpty() ? ".litematic oder .schem in den Ordner legen." : "";
			return g.name + ": " + g.sx + "×" + g.sy + "×" + g.sz + " Blöcke · " + g.solid + " gesetzte Blöcke";
		});
	}

	// ================================================================ Clipper

	private void buildClipper() {
		OemerConfig c = OemerConfig.get();
		note("Der Clipper nimmt im Hintergrund ständig auf. Auf Tastendruck landen die letzten Sekunden als Video im Ordner. Ohne Ton.");
		endFlow();
		cycle("Länge", OemerConfig.CLIP_LENGTH_NAMES, () -> c.clipLen, v -> c.clipLen = v);
		cycle("Bilder pro Sekunde", OemerConfig.CLIP_FPS_NAMES, () -> c.clipFps, v -> c.clipFps = v);
		cycle("Qualität", OemerConfig.CLIP_QUALITY_NAMES, () -> c.clipQuality, v -> {
			c.clipQuality = v;
			Clipper.clear();
		});
		cycle("Format", OemerConfig.CLIP_FORMATS, () -> c.clipFormat, v -> c.clipFormat = v);
		endFlow();
		heading("Ordner");
		addContent(new OmerWidgets.Painter(cx0, cy, cw, ROW_H, (g, x, y, w, h, mx, my) -> {
			Draw.round(g, x, y, w, h, 4, Theme.white(0x0E));
			Draw.text(g, "▣", x + 8, y + 9, Theme.accent());
			Draw.text(g, Draw.fit(Clipper.folder().toString(), w - 30), x + 22, y + 9, Theme.TEXT_SOFT);
		}, null));
		cy += ROW_H + GAP;
		button("Ordner wählen", () -> FilePicker.pickFolder("Ordner für Clips wählen", Clipper.folder().toString(), path -> {
			c.clipFolder = path;
			OemerConfig.save();
			refresh();
		}), OmerWidgets.Style.PRIMARY, "Hier werden deine Clips gespeichert");
		button("Ordner öffnen", () -> open(Clipper.folder()), OmerWidgets.Style.GLASS);
		button("Standard-Ordner", () -> {
			c.clipFolder = "";
			OemerConfig.save();
			refresh();
		}, OmerWidgets.Style.GLASS, ".minecraft/oemer-clips");
		endFlow();
		heading("Speichern");
		button("Jetzt Clip speichern", () -> {
			if (this.minecraft.level == null) return;
			Clipper.saveRequested(this.minecraft);
		}, OmerWidgets.Style.PRIMARY, "Dasselbe wie die Taste");
		endFlow();
		keyRow(OemerKeys.clipSave, "Taste: Clip speichern", "");
		keyRow(OemerKeys.clipToggle, "Taste: Clipper an/aus", "");
		info(() -> c.clipperOn ? "Puffer: " + Clipper.bufferedSeconds() + " s · " + Clipper.bufferedFrames() + " Bilder · "
				+ (Clipper.bufferedBytes() / 1048576) + " MB" : "Clipper ist aus (oben bei „Aktiv“ einschalten).");
		info(() -> Clipper.status().isEmpty() ? "" : Clipper.status());
		info(() -> "MP4 läuft überall (Discord, YouTube), braucht beim Speichern ein paar Sekunden. AVI ist sofort fertig.");
	}

	// ================================================================ Settings

	private void buildSettings() {
		OemerConfig c = OemerConfig.get();
		heading("Allgemein");
		cycle("Aktives Kit", OemerConfig.KITS, () -> c.kit, v -> KitProfiles.switchTo(v));
		toggle("Wetter aus", () -> c.noWeather, v -> c.noWeather = v, "Kein Regen und kein Gewitter (nur bei dir)");
		toggle("Fullbright", () -> c.fullbright, v -> c.fullbright = v, "Alles hell");
		cycle("Free Cam Tempo", OemerConfig.FREECAM_SPEED_NAMES, () -> c.freecamSpeed, v -> c.freecamSpeed = v);
		heading("HUD");
		cycle("HUD-Größe", OemerConfig.HUD_SCALE_NAMES, () -> c.hudScale, v -> c.hudScale = v);
		cycle("Kit-HUD Ort", OemerConfig.CORNERS, () -> c.kitHudCorner, v -> c.kitHudCorner = v);
		cycle("Material List Ort", OemerConfig.CORNERS, () -> c.materialListCorner, v -> c.materialListCorner = v);
		cycle("Musik-HUD Ort", OemerConfig.CORNERS, () -> c.spotifyHudCorner, v -> c.spotifyHudCorner = v);
		heading("Menü");
		toggle("Menü-Blur im Spiel", () -> c.menuBlur, v -> c.menuBlur = v, "Welt hinter dem Menü verschwommen");
		toggle("Version anzeigen", () -> c.showVersion, v -> c.showVersion = v, "");
		toggle("Menü-Partikel", () -> c.menuParticles, v -> c.menuParticles = v, "Funkelnde Punkte im Hintergrund");
		button("Minecraft-Optionen", () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)), OmerWidgets.Style.GLASS);
		heading("Minecraft-Tasten");
		note("Hier änderst du auch F5, Springen, Inventar … Klick auf eine Taste, dann die neue drücken.");
		keySharingRows();
		gameKeyRows();
		heading("Tasten vom Ömer Client");
		note("Klick auf eine Taste, dann die neue Taste drücken. ESC löscht sie. Rot = doppelt belegt.");
		for (OemerKeys.Entry e : OemerKeys.ALL) {
			if (e.group().equals("Kits")) continue;
			keyRow(e.key(), e.label(), e.group());
		}
		endFlow();
		info(() -> "Die Tasten zum Kit-Wechseln findest du unter Kits.");
	}

	// ================================================================== Design

	private void buildDesign() {
		OemerConfig c = OemerConfig.get();
		// "Reset Settings" oben rechts
		int rw = Draw.width("Reset Settings") + 20;
		this.addRenderableWidget(new OmerWidgets.Flat(panelX + panelW - rw - 12, panelY + 10, rw, 20, "Reset Settings", () -> {
			c.designStyle = 0;
			c.accent = 0;
			c.menuOpacity = 1;
			c.menuParticles = true;
			c.lookMode = false;
			c.lookImage = Looks.WEB + "0";
			c.lookDim = 1;
			c.lookTitle = true;
			OemerConfig.save();
			CapeManager.invalidate();
			refresh();
		}).tip("Design zurück auf Standard"));

		// ---- großes Vorschaubild + Datei wählen
		int prevW = cw >= 420 ? cw / 2 : cw;
		int prevH = prevW * 9 / 16;
		int top = cy;
		addContent(new OmerWidgets.Painter(cx0, top, prevW, prevH, (g, x, y, w, h, mx, my) -> {
			Draw.shadow(g, x, y, w, h, 6, 90);
			Draw.round(g, x, y, w, h, 6, 0x60000000);
			Textures.Tex t = Looks.selected();
			if (t != null) {
				Textures.drawCover(g, t, x, y, w, h);
				int dim = OemerConfig.LOOK_DIM[Math.floorMod(c.lookDim, OemerConfig.LOOK_DIM.length)];
				g.fill(x, y, x + w, y + h, dim << 24);
				Draw.centered(g, "§lÖMER CLIENT", x + w / 2, y + h / 2 - 4, Theme.TEXT);
			} else {
				Draw.centered(g, "lädt …", x + w / 2, y + h / 2 - 4, Theme.TEXT_DIM);
			}
			Draw.roundBorder(g, x, y, w, h, 6, Theme.white(0x40));
			String name = Looks.displayName(c.lookImage) + (c.lookMode ? "" : "  (aus)");
			int nw = Draw.width(name) + 12;
			Draw.round(g, x + w - nw - 6, y + h - 18, nw, 13, 6, 0xB0000000);
			Draw.text(g, name, x + w - nw, y + h - 15, Theme.TEXT_SOFT);
		}, null));
		int belowPrev = top + prevH + 6;
		addContent(new OmerWidgets.Flat(cx0, belowPrev, prevW, ROW_H, "Datei wählen (PNG/JPG)", () ->
				FilePicker.pickImage("Hintergrund wählen", Looks.folder(), name -> {
					c.lookImage = name;
					c.lookMode = true;
					OemerConfig.save();
					refresh();
				})).style(OmerWidgets.Style.PRIMARY).tip("Dein eigenes Bild, z. B. von Madison Beer"));
		addContent(new OmerWidgets.Toggle(cx0, belowPrev + ROW_H + 6, prevW, ROW_H, "Hintergrund an", () -> c.lookMode, v -> {
			c.lookMode = v;
			OemerConfig.save();
		}, false));
		int leftBottom = belowPrev + (ROW_H + 6) * 2;

		// ---- Vorlagen (Fotos + deine Bilder)
		List<String> choices = new ArrayList<>();
		for (int i = 0; i < Looks.PHOTOS.length; i++) choices.add(Looks.WEB + i);
		choices.addAll(Looks.files());
		int gx = cw >= 420 ? cx0 + prevW + 14 : cx0;
		int gw = cw >= 420 ? cw - prevW - 14 : cw;
		int gy = cw >= 420 ? top : leftBottom + 4;
		addContent(new OmerWidgets.Label(gx, gy, gw, 14, "Vorlagen", false, Theme.TEXT));
		gy += 18;
		int cols = Math.max(2, gw / 80);
		int tw = (gw - (cols - 1) * 6) / cols;
		int th = tw * 9 / 16 + 18;
		int i = 0;
		for (String ch : choices) {
			if (!match(Looks.displayName(ch), "vorlage", "hintergrund")) continue;
			int x = gx + (i % cols) * (tw + 6);
			int y = gy + (i / cols) * (th + 6);
			addContent(new OmerWidgets.Tile(x, y, tw, th, Looks.displayName(ch), () -> Looks.texture(ch, "lt_" + Integer.toHexString(ch.hashCode()), 480),
					() -> c.lookImage.equals(ch), () -> {
				c.lookImage = ch;
				c.lookMode = true;
				OemerConfig.save();
			}).cover().badge(ch.startsWith(Looks.WEB) ? "" : "Eigenes")
					.deletable(ch.startsWith(Looks.WEB) ? null : () -> confirmDelete("„" + Looks.displayName(ch) + "“ aus deinen Hintergründen löschen?",
							() -> OwnFiles.look(ch))));
			i++;
		}
		int gridBottom = gy + ((i + cols - 1) / cols) * (th + 6);
		cy = Math.max(leftBottom, gridBottom) + GAP;

		// ---- Ältere Designs
		heading("Ältere Designs");
		int on = Math.max(3, cw / 110);
		flow(on, (cw - (on - 1) * GAP) / on * 9 / 16 + 18);
		for (int k = 0; k < PresetArt.BACKGROUNDS.length; k++) {
			String ch = Looks.PRESET + k;
			int[] p = flowSlot();
			addContent(new OmerWidgets.Tile(p[0], p[1], p[2], p[3], PresetArt.BACKGROUNDS[k], () -> Looks.texture(ch, "lp_" + ch.hashCode(), 480),
					() -> c.lookImage.equals(ch), () -> {
				c.lookImage = ch;
				c.lookMode = true;
				OemerConfig.save();
			}).cover());
		}
		endFlow();

		// ---- Grafik
		heading("Grafik");
		String[] names = GraphicsPresets.NAMES;
		Runnable[] acts = new Runnable[names.length];
		for (int k = 0; k < names.length; k++) {
			final int level = k;
			acts[k] = () -> GraphicsPresets.apply(level);
		}
		buttonRow(names, acts, () -> c.graphicsLevel);
		note("Low = max. FPS · Mid · High · Super High = alles schön (setzt Sichtweite, Partikel, Wolken, Schatten …)");

		// ---- Menü-Design
		heading("Menü");
		cycle("Design-Stil", OemerConfig.DESIGN_STYLES, () -> c.designStyle, v -> {
			c.designStyle = v;
			CapeManager.invalidate();
			refresh();
		});
		cycle("Akzentfarbe", OemerConfig.ACCENT_NAMES, () -> c.accent, v -> {
			c.accent = v;
			CapeManager.invalidate();
		});
		cycle("Transparenz", OemerConfig.OPACITY_NAMES, () -> c.menuOpacity, v -> c.menuOpacity = v);
		cycle("Bild abdunkeln", OemerConfig.LOOK_DIM_NAMES, () -> c.lookDim, v -> c.lookDim = v);
		toggle("Auch im Startscreen", () -> c.lookTitle, v -> c.lookTitle = v, "Hintergrund statt Panorama im Hauptmenü");
		toggle("Menü-Partikel", () -> c.menuParticles, v -> c.menuParticles = v, "");
		button("Bilder-Ordner öffnen", () -> open(Looks.folder()), OmerWidgets.Style.GLASS);
		endFlow();
		info(() -> "Fotos: Unsplash (frei nutzbar) · werden beim ersten Anzeigen geladen.");
	}

	// =================================================================== Musik

	private void buildMusic() {
		OemerConfig c = OemerConfig.get();
		// Umschalter oben: YouTube | Spotify
		int x = panelX + panelW - 12;
		for (int s = Music.SERVICES.length - 1; s >= 0; s--) {
			final int idx = s;
			int w = Draw.width(Music.SERVICES[s]) + 22;
			x -= w;
			this.addRenderableWidget(new OmerWidgets.Flat(x, panelY + 11, w, 18, Music.SERVICES[s], () -> {
				c.musicService = idx;
				OemerConfig.save();
				refresh();
			}, () -> c.musicService == idx).style(OmerWidgets.Style.PILL));
			x -= 4;
		}
		if (c.musicService == 0) buildYouTube();
		else buildSpotify();
	}

	private void nowPlayingCard(int h) {
		addContent(new OmerWidgets.Painter(cx0, cy, cw, h, (g, x, y, w, hh, mx, my) -> {
			Draw.round(g, x, y, w, hh, 8, 0x50000000);
			Draw.roundBorder(g, x, y, w, hh, 8, Theme.white(0x22));
			Music.Now now = Music.now();
			int iw = (hh - 16) * 16 / 9;
			if (!Music.youtube()) iw = hh - 16;
			Draw.round(g, x + 8, y + 8, iw, hh - 16, 5, Theme.white(0x12));
			if (now != null) {
				BufferedImage cover = Spotify.cover(now.imageUrl());
				if (cover != null) {
					Textures.Tex tex = Textures.image("np_cover", now.imageUrl(), cover);
					if (tex != null) Textures.drawCover(g, tex, x + 8, y + 8, iw, hh - 16);
				}
			} else {
				Draw.centered(g, "♪", x + 8 + iw / 2, y + hh / 2 - 4, Theme.TEXT_DIM);
			}
			int tx = x + iw + 18, tw = w - iw - 28;
			if (now == null) {
				Draw.text(g, "Gerade läuft nichts", tx, y + 12, Theme.TEXT);
				Draw.text(g, Draw.fit(Music.youtube() ? "Such dir unten einen Song aus" : SpotifyScreen.statusText(), tw), tx, y + 25, Theme.TEXT_DIM);
				return;
			}
			g.pose().pushMatrix();
			g.pose().translate(tx, y + 10);
			g.pose().scale(1.2f);
			Draw.text(g, Draw.fit(now.title(), Math.round(tw / 1.2f)), 0, 0, Theme.TEXT);
			g.pose().popMatrix();
			Draw.text(g, Draw.fit(now.artist() + " · " + now.service(), tw), tx, y + 24, Theme.TEXT_SOFT);
			int by = y + hh - 22;
			float f = now.durationMs() <= 0 ? 0 : (float) now.progressMs() / now.durationMs();
			Draw.round(g, tx, by, tw, 3, 1, Theme.white(0x22));
			Draw.round(g, tx, by, Math.max(3, Math.round(tw * f)), 3, 1, Theme.accent());
			Draw.text(g, Music.time(now.progressMs()) + " / " + Music.time(now.durationMs()) + (now.playing() ? "   ▶ läuft" : "   ❚❚ Pause"),
					tx, by + 7, Theme.TEXT_DIM);
		}, null));
		cy += h + 6;
		String[] labels = {"◀◀", "▶ ❚❚", "▶▶", "⇄", "−", "+"};
		Runnable[] acts = {Music::previous, Music::playPause, Music::next, Music::toggleShuffle, () -> Music.volume(-10), () -> Music.volume(10)};
		buttonRow(labels, acts, null);
	}

	private void buildYouTube() {
		OemerConfig c = OemerConfig.get();
		nowPlayingCard(78);
		// Suchfeld
		ytBox = new EditBox(this.font, cx0 + 10, cy + 8, cw - 120, 12, Component.literal("YouTube"));
		ytBox.setBordered(false);
		ytBox.setMaxLength(200);
		ytBox.setTextColor(0xFFFFFFFF);
		ytBox.setHint(Component.literal(YouTubeMusic.hasKey() ? "Song suchen oder YouTube-Link einfügen …" : "YouTube-Link oder Playlist-Link einfügen …"));
		addContent(ytBox);
		addContent(new OmerWidgets.Flat(cx0 + cw - 100, cy, 100, ROW_H, YouTubeMusic.searching() ? "…" : "Los", () -> {
			YouTubeMusic.submit(ytBox.getValue());
			focusName = "yt";
		}).style(OmerWidgets.Style.PRIMARY));
		cy += ROW_H + GAP;
		if (!YouTubeMusic.message().isEmpty()) note(YouTubeMusic.message());

		List<YouTubeMusic.Track> results = YouTubeMusic.results();
		if (!results.isEmpty()) {
			heading("Ergebnisse");
			for (YouTubeMusic.Track t : results) trackRow(t, () -> YouTubeMusic.play(t), TrackKind.RESULT, null, -1);
		}
		// Ein Song soll in eine Playlist
		if (pickTrack != null) {
			final YouTubeMusic.Track pt = pickTrack;
			openPlaylist = null;
			heading("Zu Playlist hinzufügen");
			note("„" + Draw.fit(pt.shownTitle(), cw - 70) + "“ – such dir eine Playlist aus oder erstell unten eine neue.");
			flow(cw >= 440 ? 2 : 1, ROW_H);
			for (YouTubeMusic.Playlist p : new ArrayList<>(c.ytPlaylists)) {
				int[] sl = flowSlot();
				addContent(new OmerWidgets.Flat(sl[0], sl[1], sl[2], sl[3], "＋ " + p.name + " (" + p.tracks.size() + ")", () -> {
					YouTubeMusic.addToPlaylist(p, pt);
					pickTrack = null;
					refresh();
				}).style(OmerWidgets.Style.PRIMARY));
			}
			int[] sl = flowSlot();
			addContent(new OmerWidgets.Flat(sl[0], sl[1], sl[2], sl[3], "Abbrechen", () -> {
				pickTrack = null;
				refresh();
			}));
			endFlow();
		}
		if (openPlaylist != null && !c.ytPlaylists.contains(openPlaylist)) openPlaylist = null;
		if (openPlaylist != null) {
			buildPlaylistDetail(openPlaylist);
		} else {
			// Warteschlange
			List<YouTubeMusic.Track> queue = YouTubeMusic.queue();
			if (!queue.isEmpty()) {
				heading("Warteschlange (" + queue.size() + ")");
				button("Warteschlange leeren", () -> {
					YouTubeMusic.clearQueue();
					refresh();
				}, OmerWidgets.Style.GLASS, "Wirft alle Songs aus der Warteschlange und stoppt die Musik");
				endFlow();
				for (int i = 0; i < queue.size(); i++) {
					final int idx = i;
					trackRow(queue.get(i), () -> YouTubeMusic.jump(idx), TrackKind.QUEUE, null, idx);
				}
			}
			heading("♥ Meine Songs (" + c.ytLibrary.size() + ")");
			if (c.ytLibrary.isEmpty()) {
				note("Klick bei einem Song auf ♡ – dann landet er hier.");
			} else {
				button("Alle Songs hier löschen", () -> confirmDelete("Alle " + c.ytLibrary.size() + " Songs aus „Meine Songs“ löschen?",
						YouTubeMusic::clearLibrary), OmerWidgets.Style.GLASS, "Leert Meine Songs (Playlists bleiben)");
				endFlow();
			}
			for (int i = 0; i < c.ytLibrary.size(); i++) {
				final int idx = i;
				trackRow(c.ytLibrary.get(i), () -> YouTubeMusic.playList(c.ytLibrary, idx), TrackKind.LIBRARY, null, idx);
			}
			heading("Playlists");
			// Neue Playlist direkt im Spiel
			plBox = new EditBox(this.font, cx0 + 10, cy + 8, cw - 120, 12, Component.literal("Playlist"));
			plBox.setBordered(false);
			plBox.setMaxLength(40);
			plBox.setTextColor(0xFFFFFFFF);
			plBox.setHint(Component.literal("Name für eine neue Playlist …"));
			addContent(plBox);
			addContent(new OmerWidgets.Flat(cx0 + cw - 100, cy, 100, ROW_H, "＋ Erstellen", this::createPlaylistFromBox)
					.style(OmerWidgets.Style.PRIMARY).tip("Neue leere Playlist – Songs fügst du mit ☰ hinzu"));
			cy += ROW_H + GAP;
			if (c.ytPlaylists.isEmpty()) note("Noch keine Playlist. Name eingeben + Erstellen, oder oben einen YouTube-Playlist-Link einfügen.");
			flow(cw >= 440 ? 2 : 1, ROW_H + 6);
			for (YouTubeMusic.Playlist p : new ArrayList<>(c.ytPlaylists)) {
				if (!match(p.name)) continue;
				int[] sl = flowSlot();
				addContent(new OmerWidgets.Flat(sl[0], sl[1], sl[2] - 62, sl[3], "☰ " + p.name + " (" + p.tracks.size() + ")", () -> {
					openPlaylist = p;
					scroll = 0;
					refresh();
				}).tip("Playlist öffnen – Songs ansehen, aussuchen, löschen"));
				addContent(new OmerWidgets.Flat(sl[0] + sl[2] - 58, sl[1], 26, sl[3], "▶", () -> YouTubeMusic.playList(p.tracks, 0)).tip("Playlist abspielen"));
				addContent(new OmerWidgets.Flat(sl[0] + sl[2] - 28, sl[1], 26, sl[3], "✕", () ->
						confirmDelete("Playlist „" + p.name + "“ mit " + p.tracks.size() + " Songs löschen?", () -> YouTubeMusic.deletePlaylist(p))).tip("Playlist löschen"));
			}
			endFlow();
		}
		heading("Einstellungen");
		button("Player im Browser öffnen", YouTubeMusic::openPlayer, OmerWidgets.Style.GLASS,
				"Die Musik läuft im offiziellen YouTube-Player in deinem Browser – Tab einfach offen lassen");
		toggle("Zufall", () -> c.ytShuffle, v -> c.ytShuffle = v, "");
		toggle("Wiederholen", () -> c.ytRepeat, v -> c.ytRepeat = v, "Am Ende von vorne");
		toggle("Musik-HUD", () -> c.spotifyHud, v -> c.spotifyHud = v, "Zeigt den Song im Spiel");
		endFlow();
		// API-Schlüssel (optional, für die Suche)
		if (match("schlüssel", "api", "suche")) {
			keyBox = new EditBox(this.font, cx0 + 10, cy + 8, cw - 130, 12, Component.literal("Schlüssel"));
			keyBox.setBordered(false);
			keyBox.setMaxLength(80);
			keyBox.setTextColor(0xFFFFFFFF);
			keyBox.setHint(Component.literal("YouTube-API-Schlüssel (kostenlos, nur für die Suche)"));
			keyBox.setValue(c.ytApiKey);
			keyBox.setResponder(v -> {
				c.ytApiKey = v.trim();
				OemerConfig.save();
			});
			addContent(keyBox);
			addContent(new OmerWidgets.Flat(cx0 + cw - 110, cy, 110, ROW_H, "Schlüssel holen", () ->
					Util.getPlatform().openUri(YouTubeMusic.GUIDE)).tip("Google Cloud → YouTube Data API v3 aktivieren → Anmeldedaten → API-Schlüssel"));
			cy += ROW_H + GAP;
		}
		endFlow();
		keyRow(OemerKeys.musicPlay, "Taste: Play/Pause", "");
		keyRow(OemerKeys.musicNext, "Taste: Nächster Song", "");
		keyRow(OemerKeys.musicPrev, "Taste: Vorheriger Song", "");
		keyRow(OemerKeys.musicOpen, "Taste: Musik öffnen", "");
		endFlow();
		info(() -> YouTubeMusic.pageConnected()
				? (YouTubeMusic.needsClick() ? "Im Browser einmal auf „Musik starten“ klicken." : "Player verbunden ✔ – Tab offen lassen.")
				: "Beim ersten Song öffnet sich der Ömer-Player im Browser (kostenlos, ohne Premium).");
	}

	private enum TrackKind { RESULT, LIBRARY, PLAYLIST, QUEUE }

	private void createPlaylistFromBox() {
		String name = plBox == null ? "" : plBox.getValue();
		YouTubeMusic.Playlist p = YouTubeMusic.createPlaylist(name);
		if (pickTrack != null) {
			YouTubeMusic.addToPlaylist(p, pickTrack);
			pickTrack = null;
		}
		focusName = "";
		refresh();
	}

	private void buildPlaylistDetail(YouTubeMusic.Playlist p) {
		heading("☰ " + p.name + " (" + p.tracks.size() + " Songs)");
		button("◀ Zurück", () -> {
			openPlaylist = null;
			scroll = 0;
			refresh();
		}, OmerWidgets.Style.GLASS);
		button("▶ Abspielen", () -> YouTubeMusic.playList(p.tracks, 0), OmerWidgets.Style.PRIMARY);
		button("✕ Playlist löschen", () -> confirmDelete("Playlist „" + p.name + "“ mit " + p.tracks.size() + " Songs löschen?", () -> {
			YouTubeMusic.deletePlaylist(p);
			openPlaylist = null;
		}), OmerWidgets.Style.GLASS);
		endFlow();
		if (p.tracks.isEmpty()) note("Leer – klick bei einem Song auf ☰ und wähl diese Playlist.");
		else note("Klick auf einen Song = genau den hören · ✕ = aus der Playlist löschen");
		for (int i = 0; i < p.tracks.size(); i++) {
			final int idx = i;
			trackRow(p.tracks.get(i), () -> YouTubeMusic.playList(p.tracks, idx), TrackKind.PLAYLIST, p, idx);
		}
	}

	private void trackRow(YouTubeMusic.Track t, Runnable play, TrackKind kind, YouTubeMusic.Playlist pl, int idx) {
		if (!match(t.title, t.artist)) return;
		endFlow();
		List<String> labels = new ArrayList<>();
		List<String> tips = new ArrayList<>();
		List<Runnable> acts = new ArrayList<>();
		Runnable toQueue = () -> YouTubeMusic.enqueue(t);
		Runnable heart = () -> {
			YouTubeMusic.toggleLibrary(t);
			refresh();
		};
		Runnable toList = () -> {
			pickTrack = t;
			openPlaylist = null;
			scroll = 0;
			refresh();
		};
		String heartLabel = YouTubeMusic.inLibrary(t.id) ? "♥" : "♡";
		switch (kind) {
			case RESULT -> {
				labels.addAll(List.of("＋", heartLabel, "☰"));
				tips.addAll(List.of("In die Warteschlange", "Zu Meine Songs", "Zu einer Playlist hinzufügen"));
				acts.addAll(List.of(toQueue, heart, toList));
			}
			case LIBRARY -> {
				labels.addAll(List.of("＋", "☰", "✕"));
				tips.addAll(List.of("In die Warteschlange", "Zu einer Playlist hinzufügen", "Aus Meine Songs löschen"));
				acts.addAll(List.of(toQueue, toList, () -> {
					YouTubeMusic.removeFromLibrary(t);
					refresh();
				}));
			}
			case PLAYLIST -> {
				labels.addAll(List.of("＋", heartLabel, "✕"));
				tips.addAll(List.of("In die Warteschlange", "Zu Meine Songs", "Aus der Playlist löschen"));
				acts.addAll(List.of(toQueue, heart, () -> {
					YouTubeMusic.removeFromPlaylist(pl, t);
					refresh();
				}));
			}
			default -> {
				labels.add("✕");
				tips.add("Aus der Warteschlange löschen");
				acts.add(() -> {
					YouTubeMusic.removeFromQueue(idx);
					refresh();
				});
			}
		}
		int n = labels.size();
		int w = cw - n * 32;
		addContent(new OmerWidgets.Painter(cx0, cy, w, 30, (g, x, y, ww, h, mx, my) -> {
			boolean hover = mx >= x && mx < x + ww && my >= y && my < y + h;
			YouTubeMusic.Track cur = YouTubeMusic.current();
			boolean now = cur != null && cur.id.equals(t.id) && (kind != TrackKind.QUEUE || idx == YouTubeMusic.index());
			Draw.round(g, x, y, ww, h, 5, now ? Theme.accent(0x30) : Theme.white(hover ? 0x18 : 0x0A));
			int iw = 44;
			Draw.round(g, x + 3, y + 3, iw, h - 6, 3, Theme.white(0x12));
			BufferedImage img = Spotify.cover(t.thumb());
			if (img != null) {
				Textures.Tex tex = Textures.image("yt_" + t.id, t.thumb(), img);
				if (tex != null) Textures.drawCover(g, tex, x + 3, y + 3, iw, h - 6);
			}
			Draw.text(g, Draw.fit(t.shownTitle(), ww - iw - 40), x + iw + 10, y + 6, Theme.TEXT);
			Draw.text(g, Draw.fit(t.artist, ww - iw - 40), x + iw + 10, y + 17, Theme.TEXT_DIM);
			if (hover || now) Draw.text(g, now ? "♪" : "▶", x + ww - 14, y + 11, Theme.accent());
		}, play));
		for (int k = 0; k < n; k++) {
			addContent(new OmerWidgets.Flat(cx0 + w + 4 + k * 32, cy, 28, 30, labels.get(k), acts.get(k)).tip(tips.get(k)));
		}
		cy += 30 + 4;
	}

	private void buildSpotify() {
		OemerConfig c = OemerConfig.get();
		boolean connected = Spotify.status() == Spotify.Status.CONNECTED;
		nowPlayingCard(78);
		if (!connected) {
			keyBox = new EditBox(this.font, cx0 + 10, cy + 8, cw - 20, 12, Component.literal("Client-ID"));
			keyBox.setBordered(false);
			keyBox.setMaxLength(64);
			keyBox.setTextColor(0xFFFFFFFF);
			keyBox.setHint(Component.literal("Spotify Client-ID hier einfügen"));
			keyBox.setValue(c.spotifyClientId);
			keyBox.setResponder(v -> {
				c.spotifyClientId = v.trim();
				OemerConfig.save();
			});
			addContent(keyBox);
			cy += ROW_H + GAP;
			button("Mit Spotify verbinden", Spotify::connect, OmerWidgets.Style.PRIMARY);
			button("Anleitung: App erstellen", () -> Util.getPlatform().openUri(Spotify.DASHBOARD), OmerWidgets.Style.GLASS);
			endFlow();
			info(() -> "Redirect-URI: " + Spotify.REDIRECT + " · Spotify-Steuerung braucht Premium.");
		} else {
			button("Playlists öffnen", () -> this.minecraft.setScreen(new SpotifyScreen(this)), OmerWidgets.Style.PRIMARY);
			toggle("Musik-HUD", () -> c.spotifyHud, v -> c.spotifyHud = v, "Zeigt den Song im Spiel");
			button("Abmelden", () -> {
				Spotify.disconnect();
				refresh();
			}, OmerWidgets.Style.GLASS);
			endFlow();
		}
		keyRow(OemerKeys.musicPlay, "Taste: Play/Pause", "");
		keyRow(OemerKeys.musicNext, "Taste: Nächster Song", "");
		endFlow();
		info(() -> "Oben zwischen YouTube und Spotify wechseln – die Tasten steuern das, was ausgewählt ist.");
	}

	// ==================================================================== Skin

	// ================================================================ Freunde (online)

	private static final String[] FRIEND_TABS = {"Freunde", "Chat", "Konto"};
	private static int friendTab = 0;
	private static String chatWith = "";
	private static boolean registerMode;
	private static boolean showServer;
	private static final java.util.Map<String, String> DRAFTS = new java.util.HashMap<>();
	private final java.util.Map<String, EditBox> boxes = new java.util.LinkedHashMap<>();
	private final java.util.Map<String, Runnable> enters = new java.util.HashMap<>();

	/** Texteingabe im Inhalt. Der Text bleibt erhalten, auch wenn die Seite neu aufgebaut wird. */
	private EditBox fbox(String key, String hint, int max, int width, boolean secret, Runnable enter) {
		endFlow();
		EditBox b = new EditBox(this.font, cx0 + 10, cy + 8, width - 20, 12, Component.literal(hint));
		b.setBordered(false);
		b.setMaxLength(max);
		b.setTextColor(0xFFFFFFFF);
		b.setHint(Component.literal(hint));
		if (secret) {
			b.addFormatter((text, pos) -> net.minecraft.util.FormattedCharSequence.forward("•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
		}
		b.setValue(DRAFTS.getOrDefault(key, ""));
		b.setResponder(v -> DRAFTS.put(key, v));
		addContent(b);
		boxes.put(key, b);
		if (enter != null) enters.put(key, enter);
		return b;
	}

	private void buildFriends() {
		int x = panelX + panelW - 12;
		for (int s = FRIEND_TABS.length - 1; s >= 0; s--) {
			final int idx = s;
			int w = Draw.width(FRIEND_TABS[s]) + 18;
			x -= w;
			String label = FRIEND_TABS[s] + (s == 1 && Social.unreadTotal() > 0 ? " (" + Social.unreadTotal() + ")" : "");
			if (s == 1 && Social.unreadTotal() > 0) {
				int extra = Draw.width(" (" + Social.unreadTotal() + ")");
				w += extra;
				x -= extra;
			}
			this.addRenderableWidget(new OmerWidgets.Flat(x, panelY + 11, w, 18, label, () -> {
				friendTab = idx;
				refresh();
			}, () -> friendTab == idx).style(OmerWidgets.Style.PILL));
			x -= 4;
		}
		Social.watch();
		if (!Social.loggedIn()) {
			friendTab = 2;
			buildAccount();
			return;
		}
		switch (friendTab) {
			case 0 -> buildFriendList();
			case 1 -> buildChat();
			default -> buildAccount();
		}
	}

	private void sendRequest() {
		focusName = "add";
		Social.addFriend(DRAFTS.getOrDefault("add", ""), ok -> {
			if (ok) DRAFTS.remove("add");
		});
	}

	private void buildFriendList() {
		heading("Freund hinzufügen");
		fbox("add", "Username deines Freundes …", 16, cw - 128, false, this::sendRequest);
		addContent(new OmerWidgets.Flat(cx0 + cw - 120, cy, 120, ROW_H, "＋ Anfrage senden", this::sendRequest)
				.style(OmerWidgets.Style.PRIMARY).tip("Dein Freund bekommt die Anfrage, sobald er online ist"));
		cy += ROW_H + GAP;
		if (!Social.status().isEmpty()) note(Social.status());

		List<Social.Request> reqs = Social.requests();
		if (!reqs.isEmpty()) {
			heading("Anfragen (" + reqs.size() + ")");
			for (Social.Request r : reqs) {
				endFlow();
				final String shown = r.name() + " möchte dein Freund sein";
				addContent(new OmerWidgets.Painter(cx0, cy, Math.max(60, cw - 212), ROW_H, (g, px, py, pw, ph, mx, my) -> {
					Draw.round(g, px, py, pw, ph, 5, Theme.white(0x10));
					Draw.text(g, Draw.fit(shown, pw - 16), px + 8, py + (ph - 8) / 2 + 1, Theme.TEXT);
				}, null));
				addContent(new OmerWidgets.Flat(cx0 + cw - 206, cy, 100, ROW_H, "✔ Annehmen", () -> Social.accept(r.uid(), null))
						.style(OmerWidgets.Style.PRIMARY));
				addContent(new OmerWidgets.Flat(cx0 + cw - 102, cy, 100, ROW_H, "✕ Ablehnen", () -> Social.decline(r.uid(), null)));
				cy += ROW_H + GAP;
			}
		}

		List<Social.Friend> list = Social.friends();
		heading("Freunde (" + list.size() + ")");
		if (list.isEmpty()) {
			note("Noch keine Freunde – gib oben den Username deines Freundes ein.");
		}
		for (Social.Friend f : list) {
			endFlow();
			final int rowH = 48;
			addContent(new OmerWidgets.Painter(cx0, cy, Math.max(60, cw - 226), rowH, (g, px, py, pw, ph, mx, my) -> paintFriend(g, f, px, py, pw, ph), null));
			int bx = cx0 + cw - 220;
			int unread = Social.unread(f.uid);
			addContent(new OmerWidgets.Flat(bx, cy, 106, 22, unread > 0 ? "Chat (" + unread + " neu)" : "Chat", () -> {
				chatWith = f.uid;
				friendTab = 1;
				scroll = 0;
				Social.loadChat(f.uid);
				refresh();
			}, () -> unread > 0).tip("Mit " + f.name + " schreiben"));
			Social.Listening l = f.listening;
			boolean canJoin = l != null && "YouTube".equals(l.service()) && !l.id().isEmpty();
			var join = new OmerWidgets.Flat(bx + 110, cy, 106, 22, "♪ Mitlauschen", () -> Social.listenAlong(f.uid, null)).style(OmerWidgets.Style.PRIMARY);
			join.active = canJoin;
			join.tip(canJoin ? "Spielt " + f.name + "s Song bei dir ab, an derselben Stelle" : "Geht, wenn " + f.name + " gerade YouTube-Musik teilt");
			addContent(join);
			boolean vis = Social.musicVisibleFor(f.uid);
			addContent(new OmerWidgets.Flat(bx, cy + 25, 106, 22, vis ? "Meine Musik: an" : "Meine Musik: aus",
					() -> Social.setMusicFor(f.uid, !vis, null)).tip("Ob " + f.name + " sehen darf, was du gerade hörst"));
			addContent(new OmerWidgets.Flat(bx + 110, cy + 25, 106, 22, "Entfernen",
					() -> confirmDelete(f.name + " als Freund entfernen?", () -> Social.removeFriend(f.uid, null))));
			cy += rowH + GAP;
		}
		info(() -> "Freunde sehen dein Cape in der Welt und dein Totem hier in der Liste (Konto → Teilen).");
	}

	private void paintFriend(GuiGraphics g, Social.Friend f, int x, int y, int w, int h) {
		Draw.round(g, x, y, w, h, 6, Theme.white(0x10));
		Draw.roundBorder(g, x, y, w, h, 6, Theme.white(0x1C));
		int tx = x + 22;
		Draw.round(g, x + 8, y + 9, 8, 8, 4, f.online() ? 0xFF4ADE80 : 0xFF6B7280);
		if (f.hasTotem()) {
			final int ver = FriendSkins.totemVersion(f.uid);
			Textures.Tex tex = Thumbs.get("friendtotem_" + f.uid + "_" + ver, () -> FriendSkins.totemImage(f.uid));
			Draw.round(g, x + 22, y + 8, 32, 32, 4, 0x50000000);
			if (tex != null) Textures.drawContain(g, tex, x + 23, y + 9, 30, 30);
			tx = x + 60;
		}
		String head = f.name + (f.online() ? "" : "  ·  offline");
		Draw.text(g, Draw.fit(head, w - (tx - x) - 8), tx, y + 8, f.online() ? Theme.TEXT : Theme.TEXT_DIM);
		Social.Listening l = f.listening;
		String line;
		int col;
		if (l != null && !l.title().isEmpty()) {
			line = (l.playing() ? "♪ " : "⏸ ") + l.title() + (l.artist().isEmpty() ? "" : " – " + l.artist());
			col = Theme.accent();
		} else {
			line = f.online() ? "hört gerade nichts" : "";
			col = Theme.TEXT_FAINT;
		}
		Draw.text(g, Draw.fit(line, w - (tx - x) - 8), tx, y + 22, col);
		String extra = (f.hasCape() ? "Cape" : "") + (f.hasCape() && f.hasTotem() ? " · " : "") + (f.hasTotem() ? "Totem" : "");
		if (!extra.isEmpty()) Draw.text(g, Draw.fit("teilt: " + extra, w - (tx - x) - 8), tx, y + 34, Theme.TEXT_FAINT);
	}

	private void sendChat() {
		EditBox b = boxes.get("chat");
		String text = DRAFTS.getOrDefault("chat", "");
		if (text.isBlank() || chatWith.isEmpty()) return;
		focusName = "chat";
		DRAFTS.remove("chat");
		if (b != null) b.setValue("");
		Social.send(chatWith, text, null);
	}

	private void buildChat() {
		Social.Friend f = Social.friend(chatWith);
		if (f == null) {
			chatWith = "";
			Social.setOpenChat("");
			heading("Chats");
			List<Social.Friend> list = Social.friends();
			if (list.isEmpty()) note("Du hast noch keine Freunde – füge links unter „Freunde“ jemanden hinzu.");
			flow(cw >= 440 ? 2 : 1, ROW_H + 4);
			for (Social.Friend fr : list) {
				int[] sl = flowSlot();
				int unread = Social.unread(fr.uid);
				addContent(new OmerWidgets.Flat(sl[0], sl[1], sl[2], sl[3], (fr.online() ? "● " : "○ ") + fr.name + (unread > 0 ? "  (" + unread + " neu)" : ""), () -> {
					chatWith = fr.uid;
					Social.loadChat(fr.uid);
					refresh();
				}, () -> unread > 0));
			}
			endFlow();
			return;
		}
		Social.setOpenChat(chatWith);
		endFlow();
		addContent(new OmerWidgets.Flat(cx0, cy, 110, 22, "◀ Alle Chats", () -> {
			chatWith = "";
			Social.setOpenChat("");
			refresh();
		}).style(OmerWidgets.Style.GLASS));
		final String head = "Chat mit " + f.name + (f.online() ? "  ·  online" : "  ·  offline");
		addContent(new OmerWidgets.Painter(cx0 + 120, cy, Math.max(40, cw - 120), 22, (g, px, py, pw, ph, mx, my) ->
				Draw.text(g, Draw.fit(head, pw), px, py + 7, Theme.TEXT), null));
		cy += 22 + GAP;
		int avail = panelY + panelH - 18 - cy - ROW_H - GAP;
		final int chatH = Math.max(80, avail);
		final String uid = chatWith;
		addContent(new OmerWidgets.Painter(cx0, cy, cw, chatH, (g, px, py, pw, ph, mx, my) -> paintChat(g, uid, px, py, pw, ph), null));
		cy += chatH + GAP;
		fbox("chat", "Nachricht schreiben …", 300, cw - 108, false, this::sendChat);
		addContent(new OmerWidgets.Flat(cx0 + cw - 100, cy, 100, ROW_H, "Senden", this::sendChat).style(OmerWidgets.Style.PRIMARY));
		cy += ROW_H + GAP;
	}

	private void paintChat(GuiGraphics g, String uid, int x, int y, int w, int h) {
		Draw.round(g, x, y, w, h, 8, 0x50000000);
		Draw.roundBorder(g, x, y, w, h, 8, Theme.white(0x1C));
		List<Social.Msg> msgs = Social.chat(uid);
		if (msgs.isEmpty()) {
			Draw.centered(g, "Noch keine Nachrichten – schreib die erste!", x + w / 2, y + h / 2 - 4, Theme.TEXT_DIM);
			return;
		}
		String me = Social.myUid();
		g.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
		int bottom = y + h - 8;
		int maxText = Math.max(60, w * 3 / 4 - 16);
		for (int i = msgs.size() - 1; i >= 0 && bottom > y; i--) {
			Social.Msg m = msgs.get(i);
			List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(m.text()), maxText);
			int tw = 0;
			for (var l : lines) tw = Math.max(tw, this.font.width(l));
			int bw = tw + 14, bh = lines.size() * 10 + 8;
			boolean mine = m.from().equals(me);
			int bx = mine ? x + w - 10 - bw : x + 10;
			int by = bottom - bh;
			Draw.round(g, bx, by, bw, bh, 6, mine ? Theme.accent(0x55) : Theme.white(0x1E));
			int ty = by + 5;
			for (var l : lines) {
				g.drawString(this.font, l, bx + 7, ty, 0xFFFFFFFF);
				ty += 10;
			}
			bottom = by - 4;
		}
		g.disableScissor();
	}

	private void buildAccount() {
		OemerConfig c = OemerConfig.get();
		if (!Firebase.configured() || showServer) {
			heading("Server (Firebase)");
			note("Einmal einrichten: Web-API-Schlüssel + Datenbank-Adresse aus deinem Firebase-Projekt.");
			if (!DRAFTS.containsKey("fbkey")) DRAFTS.put("fbkey", Firebase.apiKey());
			if (!DRAFTS.containsKey("fburl")) DRAFTS.put("fburl", Firebase.databaseUrl());
			fbox("fbkey", "Web-API-Schlüssel (apiKey)", 90, cw, false, null);
			cy += ROW_H + GAP;
			fbox("fburl", "Datenbank-Adresse (https://….firebasedatabase.app)", 140, cw, false, null);
			cy += ROW_H + GAP;
			button("Server speichern", () -> {
				Firebase.setServer(DRAFTS.getOrDefault("fbkey", ""), DRAFTS.getOrDefault("fburl", ""));
				Social.setStatus("Server gespeichert");
				showServer = false;
				refresh();
			}, OmerWidgets.Style.PRIMARY);
			endFlow();
			if (!Firebase.configured()) return;
		}
		if (Social.loggedIn()) {
			heading("Dein Konto");
			note("Angemeldet als " + Social.username() + "   ·   " + Social.email());
			heading("Teilen");
			toggle("Meine Musik teilen", () -> c.shareMusic, v -> c.shareMusic = v, "Freunde sehen, was du hörst (pro Freund abschaltbar)");
			toggle("Cape & Totem teilen", () -> c.shareSkins, v -> {
				c.shareSkins = v;
				Social.syncSkinsLater();
			}, "Freunde sehen dein Cape in der Welt und dein Totem in der Liste");
			toggle("Nachrichten im Spiel zeigen", () -> c.chatToasts, v -> c.chatToasts = v, "Neue Freunde-Nachrichten und Anfragen erscheinen kurz im Chat");
			endFlow();
			button("Cape & Totem jetzt hochladen", Social::syncSkinsLater, OmerWidgets.Style.GLASS);
			button("Abmelden", Social::logout, OmerWidgets.Style.GLASS);
			button(showServer ? "Server-Einstellungen zu" : "Server-Einstellungen", () -> {
				showServer = !showServer;
				refresh();
			}, OmerWidgets.Style.GHOST);
			endFlow();
			if (!Social.status().isEmpty()) note(Social.status());
			info(() -> "Dein Passwort wird nie auf dem PC gespeichert – nur eine Anmelde-Marke.");
			return;
		}
		if (Social.needsName()) {
			heading("Username festlegen");
			note("Dein Konto ist da – such dir noch einen Namen aus (3–16 Zeichen: Buchstaben, Zahlen, _).");
			Runnable claim = () -> Social.claimName(DRAFTS.getOrDefault("claim", ""), null);
			fbox("claim", "Username", 16, cw, false, claim);
			cy += ROW_H + GAP;
			button("Username festlegen", claim, OmerWidgets.Style.PRIMARY);
			button("Abmelden", Social::logout, OmerWidgets.Style.GLASS);
			endFlow();
			if (!Social.status().isEmpty()) note(Social.status());
			return;
		}
		heading(registerMode ? "Konto erstellen" : "Anmelden");
		buttonRow(new String[]{"Ich habe ein Konto", "Neu registrieren"}, new Runnable[]{
				() -> {
					registerMode = false;
					refresh();
				}, () -> {
			registerMode = true;
			refresh();
		}}, () -> registerMode ? 1 : 0);
		Runnable submit = () -> {
			String mail = DRAFTS.getOrDefault("mail", ""), pw = DRAFTS.getOrDefault("pw", "");
			if (registerMode) {
				Social.register(mail, pw, DRAFTS.getOrDefault("user", ""), ok -> {
					if (ok) {
						DRAFTS.remove("pw");
						friendTab = 0;
					}
				});
			} else {
				Social.login(mail, pw, ok -> {
					if (ok) {
						DRAFTS.remove("pw");
						friendTab = 0;
					}
				});
			}
		};
		fbox("mail", "E-Mail", 64, cw, false, submit);
		cy += ROW_H + GAP;
		if (registerMode) {
			fbox("user", "Username (3–16 Zeichen: a–z, 0–9, _)", 16, cw, false, submit);
			cy += ROW_H + GAP;
		}
		fbox("pw", "Passwort (mindestens 6 Zeichen)", 64, cw, true, submit);
		cy += ROW_H + GAP;
		button(registerMode ? "Konto erstellen" : "Anmelden", submit, OmerWidgets.Style.PRIMARY);
		if (!registerMode) {
			button("Passwort vergessen?", () -> Social.resetPassword(DRAFTS.getOrDefault("mail", ""), null), OmerWidgets.Style.GLASS,
					"Schickt dir eine E-Mail zum Zurücksetzen");
		}
		endFlow();
		if (Social.busy()) note("Bitte warten …");
		else if (!Social.status().isEmpty()) note(Social.status());
		button(showServer ? "Server-Einstellungen zu" : "Server-Einstellungen", () -> {
			showServer = !showServer;
			refresh();
		}, OmerWidgets.Style.GHOST);
		endFlow();
		info(() -> "Pro Username gibt es genau 1 Konto. Dein Passwort wird nie auf dem PC gespeichert.");
	}

	private void buildSkin() {
		// Unter-Reiter oben rechts
		int x = panelX + panelW - 12;
		for (int s = SKIN_TABS.length - 1; s >= 0; s--) {
			final int idx = s;
			int w = Draw.width(SKIN_TABS[s]) + 18;
			x -= w;
			this.addRenderableWidget(new OmerWidgets.Flat(x, panelY + 11, w, 18, SKIN_TABS[s], () -> {
				skinTab = idx;
				refresh();
			}, () -> skinTab == idx).style(OmerWidgets.Style.PILL));
			x -= 4;
		}
		switch (skinTab) {
			case 0 -> buildCape();
			case 1 -> buildTotem();
			case 2 -> buildCosmetics();
			default -> buildSounds();
		}
	}

	private int tileCols(int min) {
		return Math.max(min, cw / 86);
	}

	private void buildCape() {
		OemerConfig c = OemerConfig.get();
		toggle("Eigenes Cape", () -> c.customCape, v -> c.customCape = v, "Du siehst es immer – Freunde sehen es, wenn du es unter Freunde → Konto teilst");
		button("Datei wählen", () -> FilePicker.pickImage("Cape-Bild wählen", CapeManager.folder(), name -> {
			c.capeSelection = name;
			c.customCape = true;
			CapeManager.invalidate();
			OemerConfig.save();
			refresh();
		}), OmerWidgets.Style.PRIMARY);
		button("Cape-Editor", () -> this.minecraft.setScreen(new CapeEditorScreen(this)), OmerWidgets.Style.GLASS);
		button("Ordner öffnen", () -> open(CapeManager.folder()), OmerWidgets.Style.GLASS);
		heading("Vorlagen & deine Capes");
		int n = tileCols(4);
		int tw = (cw - (n - 1) * GAP) / n;
		flow(n, tw * 16 / 10 / 2 + 40);
		for (String ch : CapeManager.choices()) {
			if (!match(CapeManager.displayName(ch))) continue;
			int[] p = flowSlot();
			String key = "cape:" + ch + "@" + c.accent + stamp(ch.startsWith("design:") ? null : CapeManager.folder().resolve(ch));
			addContent(new OmerWidgets.Tile(p[0], p[1], p[2], p[3], CapeManager.displayName(ch), () -> Thumbs.get(key, () -> capeFront(ch)),
					() -> c.capeSelection.equals(ch), () -> {
				c.capeSelection = ch;
				c.customCape = true;
				CapeManager.invalidate();
				OemerConfig.save();
			}).badge(ch.startsWith("design:") ? "" : "Eigenes")
					.deletable(ch.startsWith("design:") ? null : () -> confirmDelete("Cape „" + CapeManager.displayName(ch) + "“ löschen?",
							() -> OwnFiles.cape(ch))));
		}
		endFlow();
		info(() -> "Bilder werden automatisch aufs Cape zugeschnitten.");
	}

	private static String stamp(Path p) {
		if (p == null) return "";
		try {
			return "@" + Files.getLastModifiedTime(p).toMillis();
		} catch (Exception e) {
			return "";
		}
	}

	private static BufferedImage capeFront(String choice) {
		try {
			BufferedImage img;
			if (choice.startsWith("design:")) img = ImageTools.design(Integer.parseInt(choice.substring(7)), Theme.accent());
			else img = ImageTools.toCape(ImageTools.read(CapeManager.folder().resolve(choice)));
			int s = Math.max(1, img.getWidth() / 64);
			return img.getSubimage(s, s, 10 * s, 16 * s);
		} catch (Exception e) {
			return null;
		}
	}

	private void buildTotem() {
		OemerConfig c = OemerConfig.get();
		toggle("Custom Totem", () -> c.customTotem, v -> {
			c.customTotem = v;
			looksChanged = true;
		}, "Dein Totem (lädt beim Schließen neu)");
		cycle("Zuschnitt (eigene Bilder)", OemerConfig.TOTEM_FITS, () -> c.totemFit, v -> {
			c.totemFit = v;
			looksChanged = true;
		});
		button("Datei wählen", () -> FilePicker.pickImage("Totem-Bild wählen", LooksPack.totemFolder(), name -> {
			c.totemSelection = name;
			c.totemMode = 0;
			c.customTotem = true;
			looksChanged = true;
			OemerConfig.save();
			refresh();
		}), OmerWidgets.Style.PRIMARY);
		button("Selbst malen", () -> this.minecraft.setScreen(new CapeEditorScreen(this, CapeEditorScreen.Mode.TOTEM)), OmerWidgets.Style.GLASS);
		heading("Totems");
		int n = tileCols(4);
		flow(n, (cw - (n - 1) * GAP) / n + 18);
		// Mein Skin
		if (match("mein skin")) {
			int[] p = flowSlot();
			addContent(new OmerWidgets.Tile(p[0], p[1], p[2], p[3], "Mein Skin", () -> {
				BufferedImage img = SkinTotem.image();
				return img == null ? null : Textures.image("totem_skin", "skin@" + img.hashCode(), img);
			}, () -> c.totemMode == 1, () -> {
				c.totemMode = 1;
				c.customTotem = true;
				looksChanged = true;
				OemerConfig.save();
			}));
		}
		for (int k = 0; k < PresetArt.TOTEMS.length; k++) {
			final int idx = k;
			if (!match(PresetArt.TOTEMS[k])) continue;
			int[] p = flowSlot();
			addContent(new OmerWidgets.Tile(p[0], p[1], p[2], p[3], PresetArt.TOTEMS[k], () -> Thumbs.get("totem_p" + idx, () -> PresetArt.totem(idx, 160)),
					() -> c.totemMode == 2 && c.totemPreset == idx, () -> {
				c.totemMode = 2;
				c.totemPreset = idx;
				c.customTotem = true;
				looksChanged = true;
				OemerConfig.save();
			}));
		}
		for (String f : LooksPack.totemChoices()) {
			if (!match(f)) continue;
			int[] p = flowSlot();
			Path path = LooksPack.totemFolder().resolve(f);
			String key = "totem_f:" + f + stamp(path);
			addContent(new OmerWidgets.Tile(p[0], p[1], p[2], p[3], LooksPack.displayName(f), () -> Thumbs.get(key, () -> {
				try {
					return ImageTools.squareContain(ImageTools.read(path), 160);
				} catch (Exception e) {
					return null;
				}
			}), () -> c.totemMode == 0 && c.totemSelection.equals(f), () -> {
				c.totemMode = 0;
				c.totemSelection = f;
				c.customTotem = true;
				looksChanged = true;
				OemerConfig.save();
			}).badge("Eigenes").deletable(() -> confirmDelete("Totem „" + LooksPack.displayName(f) + "“ löschen?", () -> {
				if (OwnFiles.totem(f)) looksChanged = true;
			})));
		}
		endFlow();
		info(() -> "Totems sind jetzt 256x256 – Fotos bleiben scharf. Lädt beim Schließen vom Menü.");
	}

	private void buildCosmetics() {
		OemerConfig c = OemerConfig.get();
		boolean inWorld = this.minecraft.player != null;
		if (inWorld) usePreview(this::drawPlayerPreview);
		toggle("Cosmetics an", () -> c.cosmeticsOn, v -> c.cosmeticsOn = v, "Alle Cosmetics an/aus");
		toggle("Animation", () -> c.cosAnimate, v -> c.cosAnimate = v, "Flügel schlagen, Begleiter schweben");
		cycle("Größe (über dem Kopf)", OemerConfig.CUSTOM_COS_SIZE_NAMES, () -> c.customCosSize, v -> c.customCosSize = v);
		button("Datei wählen", () -> FilePicker.pickImage("Cosmetic-Bild wählen (PNG mit Transparenz)", CustomCosmetics.folder(), name -> {
			SpriteCosmetics.equip(SpriteCosmetics.Slot.values()[Math.floorMod(cosmeticSlot, 3)], "custom:" + name);
			CustomCosmetics.invalidate();
			refresh();
		}), OmerWidgets.Style.PRIMARY);
		button("Selbst malen", () -> this.minecraft.setScreen(new CapeEditorScreen(this, CapeEditorScreen.Mode.COSMETIC)), OmerWidgets.Style.GLASS);
		button("Ordner öffnen", () -> open(CustomCosmetics.folder()), OmerWidgets.Style.GLASS);
		endFlow();
		// Platz wählen
		SpriteCosmetics.Slot[] slots = SpriteCosmetics.Slot.values();
		String[] names = new String[slots.length];
		Runnable[] acts = new Runnable[slots.length];
		for (int i = 0; i < slots.length; i++) {
			final int idx = i;
			names[i] = slots[i].title + (SpriteCosmetics.equipped(slots[i]).isEmpty() ? "" : " ✔");
			acts[i] = () -> {
				cosmeticSlot = idx;
				refresh();
			};
		}
		buttonRow(names, acts, () -> Math.floorMod(cosmeticSlot, 3));
		SpriteCosmetics.Slot slot = slots[Math.floorMod(cosmeticSlot, 3)];
		List<SpriteCosmetics.Item> items = SpriteCosmetics.customItems(slot);
		if (items.isEmpty()) {
			note("Noch keine eigenen Cosmetics. Leg PNGs (mit Transparenz) in den Ordner cosmetics/ oder nimm „Datei wählen“.");
			note("Flügel: ein breites Bild mit beiden Flügeln – es wird in links und rechts geteilt.");
		}
		int n = tileCols(3);
		flow(n, (cw - (n - 1) * GAP) / n + 18);
		for (SpriteCosmetics.Item it : items) {
			if (!match(it.name())) continue;
			int[] p = flowSlot();
			String file = it.id().substring(7);
			String key = "cos:" + file + stamp(CustomCosmetics.folder().resolve(file));
			addContent(new OmerWidgets.Tile(p[0], p[1], p[2], p[3], it.name(), () -> Thumbs.get(key, () -> {
				try {
					return ImageTools.squareContain(ImageTools.read(CustomCosmetics.folder().resolve(file)), 160);
				} catch (Exception e) {
					return null;
				}
			}), () -> SpriteCosmetics.equipped(slot).equals(it.id()), () -> {
				SpriteCosmetics.equip(slot, it.id());
				refresh();
			}).deletable(() -> confirmDelete("Cosmetic „" + it.name() + "“ löschen?", () -> OwnFiles.cosmetic(file))));
		}
		endFlow();
		info(() -> inWorld ? "Klick = ausrüsten/ablegen · pro Platz eins · F5 = dich von außen sehen"
				: "Klick = ausrüsten/ablegen · im Spiel siehst du hier eine Vorschau von dir");
	}

	private void buildSounds() {
		OemerConfig c = OemerConfig.get();
		toggle("Custom Sounds", () -> c.customSounds, v -> {
			c.customSounds = v;
			looksChanged = true;
		}, "Ersetzt Minecraft-Sounds durch deine eigenen .ogg-Dateien");
		for (Sounds.Slot slot : Sounds.SLOTS) {
			cycle(slot.name(), () -> Sounds.choices(slot).stream().map(Sounds::displayName).toArray(String[]::new),
					() -> Math.max(0, Sounds.choices(slot).indexOf(c.soundSelection.getOrDefault(slot.id(), ""))),
					v -> {
						List<String> list = Sounds.choices(slot);
						String f = list.get(Math.min(v, list.size() - 1));
						if (f.isEmpty()) c.soundSelection.remove(slot.id());
						else c.soundSelection.put(slot.id(), f);
						looksChanged = true;
					});
		}
		button("Sound-Ordner öffnen", () -> open(Sounds.folder()), OmerWidgets.Style.GLASS);
		endFlow();
		info(() -> "Jeder Sound hat einen Unterordner in sounds/ (z. B. sounds/treffer/). Nur .ogg!");
		info(() -> {
			List<String> bad = Sounds.invalid();
			if (!bad.isEmpty()) return "§c" + bad.get(0) + " ist keine echte .ogg-Datei.";
			return Sounds.activeCount() + " Sounds ersetzt · wird beim Schließen geladen.";
		});
	}

	// ==================================================================== Kits

	private void buildKits() {
		OemerConfig c = OemerConfig.get();
		int active = KitProfiles.active();
		// Kit-Reiter
		String[] names = new String[OemerConfig.KITS.length];
		Runnable[] acts = new Runnable[names.length];
		for (int i = 0; i < names.length; i++) {
			final int idx = i;
			names[i] = OemerConfig.KITS[i];
			acts[i] = () -> {
				KitProfiles.switchTo(idx);
				refresh();
			};
		}
		if (cw >= 520) {
			buttonRow(names, acts, KitProfiles::active);
		} else {
			flow(cw >= 300 ? 4 : 2, ROW_H);
			for (int i = 0; i < names.length; i++) {
				final int idx = i;
				int[] p = flowSlot();
				addContent(new OmerWidgets.Flat(p[0], p[1], p[2], p[3], names[i], acts[i], () -> KitProfiles.active() == idx));
			}
			endFlow();
		}
		// Aktionen
		addContent(new OmerWidgets.Painter(cx0, cy, cw, 30, (g, x, y, w, h, mx, my) -> {
			Draw.round(g, x, y, w, h, 6, Theme.accent(0x22));
			Draw.roundBorder(g, x, y, w, h, 6, Theme.accent(0x80));
			Draw.text(g, "Aktiv: §l" + KitProfiles.name(KitProfiles.active()), x + 10, y + 6, Theme.TEXT);
			Draw.text(g, KitProfiles.hasSaved(KitProfiles.active()) ? "gespeichert ✔" : "noch nicht gespeichert", x + 10, y + 17, Theme.TEXT_DIM);
		}, null));
		cy += 30 + GAP;
		flow(cw >= 440 ? 3 : 1, ROW_H);
		int[] p = flowSlot();
		addContent(new OmerWidgets.Flat(p[0], p[1], p[2], p[3], "💾 Speichern", () -> {
			KitProfiles.save(KitProfiles.active());
			refresh();
		}).style(OmerWidgets.Style.PRIMARY).tip("Alle deine Minecraft-Einstellungen (Tasten, FOV, Maus …) in diesem Kit speichern"));
		p = flowSlot();
		addContent(new OmerWidgets.Flat(p[0], p[1], p[2], p[3], "Von Survival übernehmen", () -> {
			KitProfiles.copyFrom(0, KitProfiles.active());
			refresh();
		}).tip("Setzt dieses Kit auf die Einstellungen von Survival"));
		p = flowSlot();
		addContent(new OmerWidgets.Flat(p[0], p[1], p[2], p[3], "Minecraft-Optionen", () ->
				this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))).tip("FOV, Maus, Grafik … – danach hier speichern"));
		p = flowSlot();
		addContent(new OmerWidgets.Flat(p[0], p[1], p[2], p[3], "✕ Gespeichertes löschen", () ->
				confirmDelete("Die gespeicherten Einstellungen von Kit „" + KitProfiles.name(KitProfiles.active()) + "“ löschen?",
						() -> KitProfiles.delete(KitProfiles.active()))).tip("Löscht nur die Kit-Datei – deine aktuellen Einstellungen bleiben"));
		endFlow();
		toggle("Beim Wechseln automatisch speichern", () -> c.kitAutoSave, v -> c.kitAutoSave = v,
				"Speichert das alte Kit, bevor das neue geladen wird");
		cycle("Kit-HUD", new String[]{"Aus", "An"}, () -> c.kitHud ? 1 : 0, v -> c.kitHud = v == 1);
		endFlow();

		heading(c.sharedKeys ? "Minecraft-Tasten (für alle Kits)" : "Tasten für " + KitProfiles.name(active));
		note("Klick auf eine Taste, dann die neue drücken (auch Maustasten). ESC löscht sie.");
		keySharingRows();
		gameKeyRows();
		endFlow();
		heading("Kit wechseln");
		keyRow(OemerKeys.kitNext, "Nächstes Kit", "");
		for (int i = 0; i < OemerKeys.KIT.length; i++) keyRow(OemerKeys.KIT[i], "Direkt zu: " + OemerConfig.KITS[i], "");
		endFlow();
		info(() -> "Jedes Kit merkt sich deine kompletten Minecraft-Einstellungen. Wechseln lädt sie sofort.");
	}

	
	/** Umschalter + Knopf: gelten die Minecraft-Tasten für alle Kits? */
	private void keySharingRows() {
		OemerConfig c = OemerConfig.get();
		endFlow();
		toggle("Tasten für alle Kits gleich", () -> c.sharedKeys, v -> c.sharedKeys = v,
				"An: eine Belegung für alle Kits (z. B. F5 → R überall). Aus: jedes Kit hat seine eigene");
		button("Meine Tasten in alle Kits kopieren", KitProfiles::copyKeysToAll, OmerWidgets.Style.GLASS,
				"Schreibt die jetzige Belegung in jedes gespeicherte Kit");
		endFlow();
	}

	/** Die wichtigsten Minecraft-Tasten (Settings und Kits benutzen dieselbe Liste). */
	private void gameKeyRows() {
		var o = this.minecraft.options;
		keyRow(o.keyAttack, "Angreifen / Abbauen", "");
		keyRow(o.keyUse, "Benutzen / Platzieren", "");
		for (int i = 0; i < o.keyHotbarSlots.length; i++) keyRow(o.keyHotbarSlots[i], "Hotbar " + (i + 1), "");
		keyRow(o.keyDrop, "Item fallen lassen", "");
		keyRow(o.keySwapOffhand, "Zweithand tauschen", "");
		keyRow(o.keyInventory, "Inventar", "");
		keyRow(o.keyPickItem, "Block auswählen", "");
		keyRow(o.keyJump, "Springen", "");
		keyRow(o.keyShift, "Schleichen", "");
		keyRow(o.keySprint, "Sprinten", "");
		keyRow(o.keyUp, "Vorwärts", "");
		keyRow(o.keyDown, "Rückwärts", "");
		keyRow(o.keyLeft, "Links", "");
		keyRow(o.keyRight, "Rechts", "");
		keyRow(o.keyTogglePerspective, "Perspektive (F5)", "");
		keyRow(o.keyChat, "Chat", "");
		keyRow(o.keyCommand, "Befehl", "");
		keyRow(o.keyPlayerList, "Spielerliste", "");
		endFlow();
	}

	// ============================================================== Eingaben

	private int maxScroll() {
		int bottom = viewTop;
		for (int i = 0; i < content.size(); i++) {
			bottom = Math.max(bottom, contentBaseY.get(i) + content.get(i).getHeight());
		}
		return Math.max(0, bottom + 6 - viewBottom);
	}

	private boolean inView(double mx, double my) {
		return mx >= panelX && mx <= panelX + panelW && my >= viewTop && my <= viewBottom;
	}

	private MouseButtonEvent scaled(MouseButtonEvent e) {
		return uiScale == 1f ? e : new MouseButtonEvent(e.x() / uiScale, e.y() / uiScale, e.buttonInfo());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(scaled(event));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return super.mouseDragged(scaled(event), dx / uiScale, dy / uiScale);
	}

	@Override
	public void mouseMoved(double mx, double my) {
		super.mouseMoved(mx / uiScale, my / uiScale);
	}

	@Override
	public boolean mouseScrolled(double rmx, double rmy, double h, double v) {
		double mx = rmx / uiScale, my = rmy / uiScale;
		if (maxScroll() > 0 && inView(mx, my)) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - v * 22));
			return true;
		}
		return super.mouseScrolled(mx, my, h, v);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent rawEvent, boolean doubleClick) {
		MouseButtonEvent event = scaled(rawEvent);
		if (listening != null) {
			listening.setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			KeyMapping.resetMapping();
			this.minecraft.options.save();
			listening = null;
			return true;
		}
		if (!inView(event.x(), event.y())) {
			boolean[] was = new boolean[content.size()];
			for (int i = 0; i < content.size(); i++) {
				was[i] = content.get(i).visible;
				content.get(i).visible = false;
			}
			boolean r = super.mouseClicked(event, doubleClick);
			for (int i = 0; i < content.size(); i++) content.get(i).visible = was[i];
			return r;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening != null) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) listening.setKey(InputConstants.UNKNOWN);
			else listening.setKey(InputConstants.getKey(event));
			KeyMapping.resetMapping();
			this.minecraft.options.save();
			listening = null;
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			for (var en : boxes.entrySet()) {
				Runnable r = enters.get(en.getKey());
				if (r != null && en.getValue().isFocused()) {
					r.run();
					return true;
				}
			}
		}
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && ytBox != null && ytBox.isFocused()) {
			YouTubeMusic.submit(ytBox.getValue());
			focusName = "yt";
			return true;
		}
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && plBox != null && plBox.isFocused()) {
		createPlaylistFromBox();
		return true;
	}
	if (event.key() == GLFW.GLFW_KEY_ESCAPE && pickTrack != null) {
		pickTrack = null;
		refresh();
		return true;
	}
	if (event.key() == GLFW.GLFW_KEY_ESCAPE && openMod != null) {
			openMod = null;
			refresh();
			return true;
		}
		return super.keyPressed(event);
	}

	// ============================================================== Zeichnen

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		boolean own = !scaledNow && uiScale != 1f;
		if (own) {
			g.pose().pushMatrix();
			g.pose().scale(uiScale);
		}
		drawBackground(g);
		if (own) g.pose().popMatrix();
	}

	private void drawBackground(GuiGraphics g) {
		boolean inWorld = this.minecraft.level != null;
		if (inWorld && OemerConfig.get().menuBlur) this.renderBlurredBackground(g);
		Backdrop.render(g, this.width, this.height, inWorld);

		// ---- Logo oben links
		int lx = PAD, ly = 11;
		Draw.round(g, lx, ly, 22, 22, 6, 0xFFFFFFFF);
		g.pose().pushMatrix();
		g.pose().translate(lx + 6.5f, ly + 5.5f);
		g.pose().scale(1.5f);
		Draw.text(g, "§lÖ", 0, 0, 0xFF0B0B0F);
		g.pose().popMatrix();
		g.pose().pushMatrix();
		g.pose().translate(lx + 30, ly + 3);
		g.pose().scale(1.25f);
		Draw.text(g, "§lÖMER", 0, 0, Theme.TEXT);
		Draw.text(g, "CLIENT", Draw.width("§lÖMER") + 4, 0, Theme.TEXT_DIM);
		g.pose().popMatrix();
		Draw.text(g, "Aktives Kit: " + KitProfiles.name(KitProfiles.active()), lx + 30, ly + 15, Theme.TEXT_FAINT);

		// ---- Seitenleiste + Inhalt (Glas)
		Draw.glass(g, sideX, sideY, sideW, sideH, 8, Theme.panel(), Theme.white(0x2A));
		Draw.glass(g, panelX, panelY, panelW, panelH, 8, Theme.panel(), Theme.white(0x2A));

		// ---- Suchleiste
		int sx = panelX + 10, sy = panelY + 9, sw = search.getWidth() + 32;
		boolean focus = search.isFocused();
		Draw.round(g, sx, sy, sw, 22, 11, Theme.white(focus ? 0x22 : 0x12));
		Draw.roundBorder(g, sx, sy, sw, 22, 11, focus ? Theme.accent(0xC0) : Theme.white(0x2C));
		// Lupe
		Draw.roundBorder(g, sx + 8, sy + 6, 8, 8, 4, Theme.TEXT_SOFT);
		g.fill(sx + 15, sy + 13, sx + 17, sy + 15, Theme.TEXT_SOFT);
		g.fill(sx + 16, sy + 14, sx + 18, sy + 16, Theme.TEXT_SOFT);
		// Bereich / Mod-Titel
		String title = section == Section.MODS && openMod != null ? "" : section.subtitle;
		int titleX = sx + sw + 12;
		int maxTitle = panelX + panelW - 14 - titleX - rightHeaderWidth();
		if (maxTitle > 110) Draw.text(g, Draw.fit(title, maxTitle), titleX, sy + 7, Theme.TEXT_DIM);
		g.fill(panelX + 14, panelY + 38, panelX + panelW - 14, panelY + 39, Theme.white(0x12));

		if (previewArea != null) {
			Draw.round(g, previewX, previewY, previewW, previewH, 6, 0x40000000);
			Draw.roundBorder(g, previewX, previewY, previewW, previewH, 6, Theme.white(0x22));
		}
	}

	/** Platz, den Buttons oben rechts im Inhalt brauchen (Reiter, Reset …). */
	private int rightHeaderWidth() {
		return switch (section) {
			case MUSIK -> 130;
			case SKIN -> 220;
			case FREUNDE -> 230;
			case DESIGN -> 110;
			case MODS -> openMod != null ? 90 : 0;
			default -> 0;
		};
	}

	@Override
	public void render(GuiGraphics g, int realX, int realY, float delta) {
		lastFrame = System.nanoTime();
		int mouseX = Math.round(realX / uiScale), mouseY = Math.round(realY / uiScale);
		if (uiScale != 1f) {
			g.pose().pushMatrix();
			g.pose().scale(uiScale);
		}
		scaledNow = true;
		OmerWidgets.tooltipScale = uiScale;
		try {
			super.render(g, mouseX, mouseY, delta);
			renderOverlay(g, mouseX, mouseY, delta);
		} finally {
			scaledNow = false;
			OmerWidgets.tooltipScale = 1f;
			if (uiScale != 1f) g.pose().popMatrix();
		}
	}

	private void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float delta) {

		// ---- Inhalte (scrollen, abgeschnitten)
		int max = maxScroll();
		scroll = Math.max(0, Math.min(max, scroll));
		for (int i = 0; i < content.size(); i++) {
			var w = content.get(i);
			int y = contentBaseY.get(i) - (int) Math.round(scroll);
			w.setY(y);
			w.visible = y + w.getHeight() > viewTop && y < viewBottom;
		}
		boolean mouseIn = inView(mouseX, mouseY);
		g.enableScissor(panelX + 4, viewTop, panelX + panelW - 4, viewBottom);
		for (var w : content) {
			if (!w.visible) continue;
			if (w instanceof EditBox eb) {
				boolean focus = eb.isFocused();
				Draw.round(g, eb.getX() - 10, eb.getY() - 8, eb.getWidth() + 20, ROW_H, 5, Theme.white(focus ? 0x26 : 0x14));
				Draw.roundBorder(g, eb.getX() - 10, eb.getY() - 8, eb.getWidth() + 20, ROW_H, 5, focus ? Theme.accent(0xC0) : Theme.white(0x30));
			}
			w.render(g, mouseIn ? mouseX : -1000, mouseIn ? mouseY : -1000, delta);
		}
		g.disableScissor();
		if (max > 0) {
			int barX = panelX + panelW - 8;
			int trackH = viewBottom - viewTop;
			int barH = Math.max(16, trackH * trackH / (trackH + max));
			int barY = viewTop + (int) ((trackH - barH) * (scroll / max));
			Draw.round(g, barX, viewTop, 3, trackH, 1, Theme.white(0x10));
			Draw.round(g, barX, barY, 3, barH, 1, Theme.white(0x70));
			if (scroll < max) Draw.vGradient(g, panelX + 4, viewBottom - 12, panelW - 12, 12, 0x00000000, 0x80060608);
		}

		if (previewArea != null) {
			mouseXNow = mouseX;
			mouseYNow = mouseY;
			previewArea.accept(g);
		}

		// Infos unten im Inhalt
		int y = panelY + panelH - 8 - infoLines.size() * 11;
		for (Supplier<String> line : infoLines) {
			Draw.text(g, "•", panelX + 14, y, Theme.accent());
			Draw.text(g, Draw.fit(line.get(), panelW - 30), panelX + 22, y, Theme.TEXT_DIM);
			y += 11;
		}

		// Fußzeile
		String hint = listening != null ? "Drück jetzt die neue Taste … (ESC = löschen)" : "ESC zurück  ·  R-Shift öffnet das Menü im Spiel";
		Draw.text(g, hint, PAD, this.height - 21, listening != null ? Theme.accent() : Theme.TEXT_FAINT);
		if (OemerConfig.get().showVersion) {
			String v = "v" + OemerClient.VERSION;
			int vw = Draw.width(v) + 12;
			int vx = this.width - PAD - 76 - 8 - vw;
			Draw.round(g, vx, this.height - 25, vw, 16, 8, Theme.white(0x12));
			Draw.text(g, v, vx + 6, this.height - 21, Theme.TEXT_DIM);
		}

		if (fadeIn) {
			float t = (System.currentTimeMillis() - openedAt) / 220f;
			if (t < 1f) {
				int a = Math.round(255 * (1f - t) * (1f - t));
				g.fill(0, 0, this.width, this.height, (a << 24) | 0x060608);
			}
		}
	}

	private void drawPlayerPreview(GuiGraphics g) {
		if (this.minecraft.player == null) return;
		int size = Math.min(previewW, previewH) * 2 / 5;
		// Die Spieler-Vorschau kennt unseren Menü-Maßstab nicht → in echten Koordinaten zeichnen
		float s = uiScale;
		g.pose().pushMatrix();
		g.pose().identity();
		InventoryScreen.renderEntityInInventoryFollowsMouse(g, Math.round((previewX + 4) * s), Math.round((previewY + 4) * s),
				Math.round((previewX + previewW - 4) * s), Math.round((previewY + previewH - 20) * s), Math.round(size * s), 0.0625f,
				mouseXNow * s, mouseYNow * s, this.minecraft.player);
		g.pose().popMatrix();
		Draw.centered(g, "Vorschau · Maus bewegen", previewX + previewW / 2, previewY + previewH - 14, Theme.TEXT_DIM);
	}

	// ------------------------------------------------------- Seitenleiste

	/** Eintrag in der Seitenleiste: Symbol + Name, ausgewählt = weiß. */
	private class NavItem extends OmerWidgets.Base {
		private final Section target;
		private final boolean iconOnly;

		NavItem(int x, int y, int w, int h, Section target, boolean iconOnly) {
			super(x, y, w, h, target.title);
			this.target = target;
			this.iconOnly = iconOnly;
			if (!iconOnly) tip(target.subtitle);
		}

		@Override
		protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = width, h = height;
			boolean sel = section == target;
			if (sel) {
				Draw.shadow(g, x, y, w, h, 6, 50);
				Draw.round(g, x, y, w, h, 6, 0xF5FFFFFF);
			} else if (hover > 0.01f) {
				Draw.round(g, x, y, w, h, 6, Theme.white(Math.round(0x18 * hover)));
			}
			int col = sel ? 0xFF0B0B0F : Draw.mix(Theme.TEXT_SOFT, Theme.TEXT, hover);
			int ty = y + (h - 8) / 2 + 1;
			if (iconOnly) {
				Draw.centered(g, target.icon, x + w / 2, ty, col);
				return;
			}
			Draw.round(g, x + 6, y + (h - 16) / 2, 16, 16, 4, sel ? Theme.accent(0xFF) : Theme.white(0x14));
			int iconCol = sel && (Theme.accent() & 0xFFFFFF) == 0xFFFFFF ? 0xFF0B0B0F : 0xFFFFFFFF;
			if (sel && (Theme.accent() & 0xFFFFFF) == 0xFFFFFF) {
				Draw.round(g, x + 6, y + (h - 16) / 2, 16, 16, 4, 0xFF0B0B0F);
				iconCol = 0xFFFFFFFF;
			}
			Draw.centered(g, target.icon, x + 14, ty, iconCol);
			Draw.text(g, Draw.fit(target.title, w - 34), x + 28, ty, col);
		}

		@Override
		public void onPress(net.minecraft.client.input.InputWithModifiers input) {
			go(target);
		}
	}

	/** Für den automatischen Test: Suchbegriff setzen. */
	public void searchForTest(String q) {
		search.setValue(q);
	}

	/** Für den automatischen Test: Playlist öffnen / Song zum Hinzufügen wählen. */
	public static void musicForTest(YouTubeMusic.Playlist open, YouTubeMusic.Track pick) {
		openPlaylist = open;
		pickTrack = pick;
	}

	/** Für den automatischen Test: Freunde-Reiter (0 Freunde, 1 Chat, 2 Konto) und offenen Chat setzen. */
	public static void friendsForTest(int tab, String chat, boolean register) {
		friendTab = tab;
		chatWith = chat == null ? "" : chat;
		registerMode = register;
	}

	/** Für den automatischen Test: Bereich öffnen. */
	public static void sectionForTest(String name, String mod) {
		section = Section.valueOf(name);
		openMod = mod;
		query = "";
	}

	// -------------------------------------------------------------- Schließen

	@Override
	public void onClose() {
		OemerConfig.save();
		Social.setOpenChat("");
		if (Social.loggedIn()) Social.syncSkinsLater();
		if (looksChanged) {
			looksChanged = false;
			LooksPack.apply(this.minecraft);
		}
		this.minecraft.setScreen(parent);
	}
}
