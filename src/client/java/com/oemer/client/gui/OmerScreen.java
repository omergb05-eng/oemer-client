package com.oemer.client.gui;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.Cosmetics;
import com.oemer.client.feature.Hud;
import com.oemer.client.feature.Looks;
import com.oemer.client.feature.LooksPack;
import com.oemer.client.feature.MaterialList;
import com.oemer.client.feature.Sounds;
import com.oemer.client.feature.Textures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Das Ömer-Client-Menü: schwarzer Screen, oben rechts die Tabs
 * Settings · Skins · Layout · Graphics, jeder Tab mit eigenen Unter-Reitern.
 */
public class OmerScreen extends Screen {

	enum Tab {
		SETTINGS("Settings"), SKINS("Skins"), LAYOUT("Layout"), GRAPHICS("Graphics");

		final String title;

		Tab(String title) {
			this.title = title;
		}
	}

	enum Sub {
		ALLGEMEIN(Tab.SETTINGS, "Allgemein", "Fullbright und Free Cam"),
		KITS(Tab.SETTINGS, "Kits", "Kit Select und Kit-HUD"),
		MATERIAL(Tab.SETTINGS, "Material List", "Blöcke für deine Schematic"),
		CAPE(Tab.SKINS, "Cape", "Dein eigenes Cape"),
		COSMETICS(Tab.SKINS, "Cosmetics", "Hüte, Flügel und mehr"),
		TOTEM(Tab.SKINS, "Totem", "Dein eigenes Totem"),
		SOUNDS(Tab.SKINS, "Sounds", "Eigene Sounds, jeder mit eigenem Ordner"),
		DESIGN(Tab.LAYOUT, "Design", "So sieht dein Client aus"),
		HUD(Tab.LAYOUT, "HUD", "Anzeigen im Spiel"),
		LOOKS(Tab.GRAPHICS, "Looks", "Eigene Bilder als Hintergrund");

		final Tab tab;
		final String title;
		final String subtitle;

		Sub(Tab tab, String title, String subtitle) {
			this.tab = tab;
			this.title = title;
			this.subtitle = subtitle;
		}

		static List<Sub> of(Tab tab) {
			List<Sub> list = new ArrayList<>();
			for (Sub s : values()) if (s.tab == tab) list.add(s);
			return list;
		}
	}

	/** Bleibt gemerkt, solange das Spiel läuft. */
	private static Tab current = Tab.SETTINGS;
	private static final Map<Tab, Sub> CURRENT_SUB = new EnumMap<>(Tab.class);

	/** Totem/Sounds geändert → beim Schließen Ressourcen neu laden. */
	private static boolean looksChanged;
	/** Beim Tab-Wechsel nicht nochmal einblenden. */
	private static boolean skipFade;
	/** Gleitende Markierung unter den Tabs. */
	private static float tabHighlightX = -1, tabHighlightW;

	private static final int PAD = 14;
	private static final int TOP = 50;
	private static final int ROW_H = 24;
	private static final int GAP = 6;

	private final Screen parent;
	private final List<Supplier<String>> infoLines = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();
	private final boolean fadeIn;
	private long lastFrame = System.nanoTime();

	private int panelX, panelY, panelW, panelH;
	private int contentX, contentY, contentW;
	private int colW, colCount;
	private int nextIndex;
	private int tabsX, tabsW, tabsY = 13, tabsH = 20;
	private final int[] tabX = new int[Tab.values().length];
	private final int[] tabW = new int[Tab.values().length];
	/** Rechte Hälfte der Glas-Fläche für Vorschauen (null = keine). */
	private Consumer<GuiGraphics> previewArea;
	/** Inhalte, die scrollen (eigene Liste, damit wir sie abschneiden können). */
	private final List<net.minecraft.client.gui.components.AbstractWidget> content = new ArrayList<>();
	private final List<Integer> contentBaseY = new ArrayList<>();
	private int viewTop, viewBottom;
	private double scroll;
	private int pillsLeft;
	private int previewX, previewY, previewW, previewH;

	public OmerScreen(Screen parent) {
		super(Component.literal("Ömer Client"));
		this.parent = parent;
		this.fadeIn = !skipFade;
		skipFade = false;
	}

	private Sub sub() {
		Sub s = CURRENT_SUB.get(current);
		if (s == null || s.tab != current) {
			s = Sub.of(current).get(0);
			CURRENT_SUB.put(current, s);
		}
		return s;
	}

	@Override
	protected void init() {
		infoLines.clear();
		previewArea = null;
		nextIndex = 0;
		content.clear();
		contentBaseY.clear();

		panelX = PAD;
		panelY = TOP;
		panelW = this.width - PAD * 2;
		panelH = this.height - TOP - 34;
		contentX = panelX + 14;
		contentY = panelY + 44;
		contentW = panelW - 28;
		colCount = contentW >= 330 ? 2 : 1;
		colW = (contentW - (colCount - 1) * 8) / colCount;

		// ---- Tabs oben rechts (in einer Glas-Pille)
		Tab[] tabs = Tab.values();
		int total = 0;
		for (int i = 0; i < tabs.length; i++) {
			tabW[i] = Draw.width(tabs[i].title) + 22;
			total += tabW[i];
		}
		tabsW = total + 4;
		tabsX = this.width - PAD - tabsW;
		int x = tabsX + 2;
		for (int i = 0; i < tabs.length; i++) {
			Tab tab = tabs[i];
			tabX[i] = x;
			this.addRenderableWidget(new OmerWidgets.Flat(x, tabsY + 2, tabW[i], tabsH - 4, tab.title,
					() -> switchTab(tab), () -> current == tab).style(OmerWidgets.Style.TAB));
			x += tabW[i];
		}

		// ---- Unter-Reiter (Pillen) oben rechts in der Glas-Fläche
		List<Sub> subs = Sub.of(current);
		pillsLeft = panelX + panelW - 12;
		if (subs.size() > 1) {
			int px = panelX + panelW - 12;
			for (int i = subs.size() - 1; i >= 0; i--) {
				Sub s = subs.get(i);
				int w = Draw.width(s.title) + 16;
				px -= w;
				this.addRenderableWidget(new OmerWidgets.Flat(px, panelY + 12, w, 16, s.title,
						() -> switchSub(s), () -> sub() == s).style(OmerWidgets.Style.PILL));
				px -= 4;
			}
			pillsLeft = px;
		}

		switch (sub()) {
			case ALLGEMEIN -> buildGeneral();
			case KITS -> buildKits();
			case MATERIAL -> buildMaterial();
			case CAPE -> buildCape();
			case COSMETICS -> buildCosmetics();
			case TOTEM -> buildTotem();
			case SOUNDS -> buildSounds();
			case DESIGN -> buildDesign();
			case HUD -> buildHud();
			case LOOKS -> buildLooks();
		}

		// ---- Sichtbarer Bereich für die Inhalte (darunter stehen die Infos)
		viewTop = contentY - 2;
		viewBottom = panelY + panelH - 12 - infoLines.size() * 11 - 4;
		if (previewArea != null) {
			previewH = Math.max(40, viewBottom - previewY);
		}
		scroll = Math.max(0, Math.min(maxScroll(), scroll));

		// ---- Fußzeile
		this.addRenderableWidget(new OmerWidgets.Flat(this.width - PAD - 76, this.height - 26, 76, 18,
				"Fertig", this::onClose).style(OmerWidgets.Style.PRIMARY));
	}

	private <T extends net.minecraft.client.gui.components.AbstractWidget> T addContent(T w) {
		content.add(w);
		contentBaseY.add(w.getY());
		this.addWidget(w);
		return w;
	}

	private int maxScroll() {
		int bottom = viewTop;
		for (int i = 0; i < content.size(); i++) {
			bottom = Math.max(bottom, contentBaseY.get(i) + content.get(i).getHeight());
		}
		return Math.max(0, bottom + 4 - viewBottom);
	}

	private boolean inView(double mx, double my) {
		return mx >= contentX - 4 && mx <= contentX + contentW + 4 && my >= viewTop && my <= viewBottom;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (maxScroll() > 0 && inView(mx, my)) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - v * 18));
			return true;
		}
		return super.mouseScrolled(mx, my, h, v);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (!inView(event.x(), event.y())) {
			// Klick außerhalb vom sichtbaren Bereich darf keine halb versteckten Inhalte treffen
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

	// ============================================================== Inhalte

	private void buildGeneral() {
		OemerConfig c = OemerConfig.get();
		toggle("Fullbright", () -> c.fullbright, v -> c.fullbright = v, false, "Alles hell, auch nachts und in Höhlen (Taste G)");
		toggle("Free Cam", () -> c.freecam, v -> {
			if (this.minecraft.level != null) c.freecam = v;
		}, false, "Frei fliegen, aber nicht durch Blöcke (Taste F4)");
		cycle("Free Cam Tempo", OemerConfig.FREECAM_SPEED_NAMES, () -> c.freecamSpeed, v -> c.freecamSpeed = v);

		info(() -> "Tasten: R-Shift Menü · F4 Free Cam · G Fullbright");
		info(() -> this.minecraft.level != null
				? "Free Cam: WASD fliegen, Leertaste hoch, Shift runter, Sprinten = schneller."
				: "Free Cam geht nur in einer Welt oder auf Servern.");
	}

	private void buildKits() {
		OemerConfig c = OemerConfig.get();
		cycle("Kit Select", OemerConfig.KITS, () -> c.kit, v -> c.kit = v);
		toggle("Kit-HUD anzeigen", () -> c.kitHud, v -> c.kitHud = v, false, "Zeigt im Spiel, wie viel du von den Kit-Items hast");
		cycle("Kit-HUD Ort", OemerConfig.CORNERS, () -> c.kitHudCorner, v -> c.kitHudCorner = v);
		cycle("HUD-Größe", OemerConfig.HUD_SCALE_NAMES, () -> c.hudScale, v -> c.hudScale = v);

		info(() -> OemerConfig.KITS[Math.floorMod(c.kit, OemerConfig.KITS.length)] + ": " + Hud.kitDescription(c.kit));
		info(() -> "Rot = du hast nichts mehr davon.");
	}

	private void buildMaterial() {
		OemerConfig c = OemerConfig.get();
		toggle("Material List anzeigen", () -> c.materialList, v -> c.materialList = v, false, "Zeigt die Blöcke deiner Schematic im Spiel");
		cycleDynamic("Schematic", () -> {
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
		cycle("Material List Ort", OemerConfig.CORNERS, () -> c.materialListCorner, v -> c.materialListCorner = v);
		button("Liste öffnen & bearbeiten", () -> this.minecraft.setScreen(new MaterialListScreen(this)), OmerWidgets.Style.PRIMARY);
		button("Schematic-Ordner öffnen", () -> open(MaterialList.folder()), OmerWidgets.Style.GLASS);

		if (c.schematic.isEmpty() && !MaterialList.files().isEmpty()) {
			c.schematic = MaterialList.files().get(0);
		}
		info(() -> ".litematic oder .schem in den Ordner schematics/ legen.");
		info(() -> {
			if (c.schematic.isEmpty()) return "Noch keine Schematic gefunden.";
			if (!MaterialList.error().isEmpty()) return "Fehler: " + MaterialList.error();
			var e = MaterialList.entries();
			int sum = 0;
			for (var x : e) sum += x.needed();
			return e.size() + " verschiedene Blöcke, " + sum + " insgesamt.";
		});
	}

	private void buildCape() {
		OemerConfig c = OemerConfig.get();
		usePreview(this::drawCapePreview);
		List<String> capes = CapeManager.choices();
		toggle("Eigenes Cape", () -> c.customCape, v -> c.customCape = v, false, "Das Cape siehst nur du");
		cycleDynamic("Cape-Auswahl", () -> CapeManager.choices().stream().map(CapeManager::displayName).toArray(String[]::new),
				() -> Math.max(0, CapeManager.choices().indexOf(c.capeSelection)), v -> {
					List<String> list = CapeManager.choices();
					c.capeSelection = list.get(Math.min(v, list.size() - 1));
					CapeManager.invalidate();
				});
		button("Cape-Editor öffnen", () -> this.minecraft.setScreen(new CapeEditorScreen(this)), OmerWidgets.Style.PRIMARY);
		button("Bilder-Ordner öffnen", () -> open(CapeManager.folder()), OmerWidgets.Style.GLASS);
		if (capes.isEmpty()) c.capeSelection = "design:0";

		info(() -> "Bilder (PNG/JPG) in capes/ legen oder selbst malen.");
	}

	private void buildCosmetics() {
		OemerConfig c = OemerConfig.get();
		boolean inWorld = this.minecraft.player != null;
		if (inWorld) usePreview(this::drawPlayerPreview);
		toggle(Cosmetics.NAMES[0], () -> c.cosHalo, v -> c.cosHalo = v, false, "Leuchtender Ring über dem Kopf");
		toggle(Cosmetics.NAMES[1], () -> c.cosCrown, v -> c.cosCrown = v, false, "Krone mit roten Steinen");
		toggle(Cosmetics.NAMES[2], () -> c.cosTopHat, v -> c.cosTopHat = v, false, "Eleganter Zylinder-Hut");
		toggle(Cosmetics.NAMES[3], () -> c.cosWings, v -> c.cosWings = v, false, "Flügel auf dem Rücken");
		toggle(Cosmetics.NAMES[4], () -> c.cosEars, v -> c.cosEars = v, false, "Süße Hasenohren");
		toggle(Cosmetics.NAMES[5], () -> c.cosHorns, v -> c.cosHorns = v, false, "Gebogene Hörner");
		cycle("Farbe", OemerConfig.COSMETIC_COLOR_NAMES, () -> c.cosColor, v -> c.cosColor = v);
		toggle("Animation", () -> c.cosAnimate, v -> c.cosAnimate = v, false, "Heiligenschein dreht sich, Flügel schlagen");

		info(() -> inWorld ? "Tipp: Mit F5 oder Free Cam (F4) siehst du dich von außen."
				: "Im Spiel siehst du hier eine Vorschau von dir.");
	}

	private void buildTotem() {
		OemerConfig c = OemerConfig.get();
		usePreview(this::drawTotemPreview);
		List<String> totems = LooksPack.totemChoices();
		if (!totems.contains(c.totemSelection)) {
			c.totemSelection = totems.isEmpty() ? "" : totems.get(0);
		}
		toggle("Custom Totem", () -> c.customTotem, v -> {
			c.customTotem = v;
			looksChanged = true;
		}, false, "Dein Bild wird zum Totem (lädt beim Schließen neu)");
		cycleDynamic("Totem-Bild", () -> {
			List<String> t = LooksPack.totemChoices();
			return t.isEmpty() ? new String[]{"(kein Bild)"} : t.stream().map(LooksPack::displayName).toArray(String[]::new);
		}, () -> Math.max(0, LooksPack.totemChoices().indexOf(c.totemSelection)), v -> {
			List<String> t = LooksPack.totemChoices();
			if (!t.isEmpty()) {
				c.totemSelection = t.get(Math.min(v, t.size() - 1));
				looksChanged = true;
			}
		});
		button("Totem-Ordner öffnen", () -> open(LooksPack.totemFolder()), OmerWidgets.Style.GLASS);

		info(() -> "Bilder (PNG/JPG) in den Ordner totems/ legen.");
	}

	private void buildSounds() {
		OemerConfig c = OemerConfig.get();
		toggle("Custom Sounds", () -> c.customSounds, v -> {
			c.customSounds = v;
			looksChanged = true;
		}, false, "Ersetzt Minecraft-Sounds durch deine eigenen .ogg-Dateien");
		for (Sounds.Slot slot : Sounds.SLOTS) {
			cycleDynamic(slot.name(), () -> Sounds.choices(slot).stream().map(Sounds::displayName).toArray(String[]::new),
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

		info(() -> "Jeder Sound hat einen Unterordner in sounds/ (z. B. sounds/treffer/). Nur .ogg!");
		info(() -> {
			List<String> bad = Sounds.invalid();
			if (!bad.isEmpty()) return "§c" + bad.get(0) + " ist keine echte .ogg-Datei.";
			return Sounds.activeCount() + " Sounds ersetzt · wird beim Schließen geladen.";
		});
	}

	private void buildDesign() {
		OemerConfig c = OemerConfig.get();
		cycle("Akzentfarbe", OemerConfig.ACCENT_NAMES, () -> c.accent, v -> {
			c.accent = v;
			CapeManager.invalidate();
		});
		cycle("Transparenz", OemerConfig.OPACITY_NAMES, () -> c.menuOpacity, v -> c.menuOpacity = v);
		toggle("Menü-Partikel", () -> c.menuParticles, v -> c.menuParticles = v, false, "Funkelnde Punkte im Hintergrund");
		toggle("Menü-Blur im Spiel", () -> c.menuBlur, v -> c.menuBlur = v, false, "Welt hinter dem Menü verschwommen");
		toggle("Version anzeigen", () -> c.showVersion, v -> c.showVersion = v, false, "");

		info(() -> "Änderungen siehst du sofort.");
	}

	private void buildHud() {
		OemerConfig c = OemerConfig.get();
		cycle("HUD-Größe", OemerConfig.HUD_SCALE_NAMES, () -> c.hudScale, v -> c.hudScale = v);
		cycle("Kit-HUD Ort", OemerConfig.CORNERS, () -> c.kitHudCorner, v -> c.kitHudCorner = v);
		cycle("Material List Ort", OemerConfig.CORNERS, () -> c.materialListCorner, v -> c.materialListCorner = v);
		toggle("Kit-HUD anzeigen", () -> c.kitHud, v -> c.kitHud = v, false, "");
		toggle("Material List anzeigen", () -> c.materialList, v -> c.materialList = v, false, "");

		info(() -> "Beide in derselben Ecke? Dann stapeln sie sich.");
	}

	private void buildLooks() {
		OemerConfig c = OemerConfig.get();
		usePreview(this::drawLookPreview);
		toggle("Look-Modus", () -> c.lookMode, v -> c.lookMode = v, false, "Dein Bild als Hintergrund vom Menü");
		cycleDynamic("Bild", () -> {
			List<String> l = Looks.choices();
			return l.isEmpty() ? new String[]{"(kein Bild)"} : l.stream().map(LooksPack::displayName).toArray(String[]::new);
		}, () -> Math.max(0, Looks.choices().indexOf(c.lookImage)), v -> {
			List<String> l = Looks.choices();
			if (!l.isEmpty()) c.lookImage = l.get(Math.min(v, l.size() - 1));
		});
		cycle("Abdunkeln", OemerConfig.LOOK_DIM_NAMES, () -> c.lookDim, v -> c.lookDim = v);
		toggle("Auch im Startscreen", () -> c.lookTitle, v -> c.lookTitle = v, false, "Bild statt dem drehenden Panorama");
		button("Looks-Ordner öffnen", () -> open(Looks.folder()), OmerWidgets.Style.GLASS);

		info(() -> "Lieblingsbilder (PNG/JPG) in den Ordner looks/ legen.");
	}

	// ============================================================== Helfer

	private void info(Supplier<String> line) {
		infoLines.add(line);
	}

	/** Linke Hälfte für Einstellungen, rechte Hälfte für eine Vorschau. */
	private void usePreview(Consumer<GuiGraphics> draw) {
		if (contentW < 330) return; // zu schmal → keine Vorschau
		previewArea = draw;
		colCount = 1;
		colW = (contentW - 12) / 2;
		previewX = contentX + colW + 12;
		previewY = contentY;
		previewW = contentW - colW - 12;
		previewH = panelY + panelH - 14 - previewY - 26;
	}

	private int[] nextSlot() {
		int col = nextIndex % colCount;
		int row = nextIndex / colCount;
		nextIndex++;
		return new int[]{contentX + col * (colW + 8), contentY + row * (ROW_H + GAP)};
	}

	private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, boolean soon, String tip) {
		int[] p = nextSlot();
		var t = new OmerWidgets.Toggle(p[0], p[1], colW, ROW_H, label, get, v -> {
			set.accept(v);
			OemerConfig.save();
		}, soon);
		if (!tip.isEmpty()) t.tip(tip);
		addContent(t);
	}

	private void cycle(String label, String[] options, IntSupplier get, IntConsumer set) {
		int[] p = nextSlot();
		addContent(new OmerWidgets.Cycle(p[0], p[1], colW, ROW_H, label, options, get, v -> {
			set.accept(v);
			OemerConfig.save();
		}));
	}

	private void cycleDynamic(String label, Supplier<String[]> options, IntSupplier get, IntConsumer set) {
		int[] p = nextSlot();
		addContent(new OmerWidgets.Cycle(p[0], p[1], colW, ROW_H, label, options, get, v -> {
			set.accept(v);
			OemerConfig.save();
		}));
	}

	private void button(String label, Runnable action, OmerWidgets.Style style) {
		int[] p = nextSlot();
		addContent(new OmerWidgets.Flat(p[0], p[1], colW, ROW_H, label, action).style(style));
	}

	private static void open(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (Exception ignored) {
		}
		Util.getPlatform().openPath(dir);
	}

	private void switchTab(Tab tab) {
		if (current == tab) return;
		current = tab;
		skipFade = true;
		this.minecraft.setScreen(new OmerScreen(parent));
	}

	private void switchSub(Sub s) {
		if (sub() == s) return;
		CURRENT_SUB.put(current, s);
		skipFade = true;
		this.minecraft.setScreen(new OmerScreen(parent));
	}

	// ============================================================ Zeichnen

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		boolean inWorld = this.minecraft.level != null;
		if (inWorld && OemerConfig.get().menuBlur) {
			this.renderBlurredBackground(g);
		}
		Backdrop.render(g, this.width, this.height, inWorld);

		long now = System.nanoTime();
		float d = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;

		// ---- Kopfzeile: Logo
		int lx = PAD, ly = 12;
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
		Draw.text(g, "dein eigener Client", lx + 30, ly + 15, Theme.TEXT_FAINT);

		// ---- Tabs: Glas-Pille + gleitende weiße Markierung
		Draw.glass(g, tabsX, tabsY, tabsW, tabsH, tabsH / 2, Theme.white(0x12), Theme.white(0x2A));
		int idx = current.ordinal();
		float targetX = tabX[idx], targetW = tabW[idx];
		if (tabHighlightX < 0) {
			tabHighlightX = targetX;
			tabHighlightW = targetW;
		}
		tabHighlightX = Draw.approach(tabHighlightX, targetX, 18f, d);
		tabHighlightW = Draw.approach(tabHighlightW, targetW, 18f, d);
		Draw.round(g, Math.round(tabHighlightX), tabsY + 2, Math.round(tabHighlightW), tabsH - 4, (tabsH - 4) / 2, 0xF5FFFFFF);

		// ---- Glas-Fläche
		Draw.glass(g, panelX, panelY, panelW, panelH, 8, Theme.panel(), Theme.white(0x2A));

		// Überschrift
		Sub s = sub();
		g.pose().pushMatrix();
		g.pose().translate(panelX + 14, panelY + 12);
		g.pose().scale(1.35f);
		Draw.text(g, s.title, 0, 0, Theme.TEXT);
		g.pose().popMatrix();
		Draw.text(g, Draw.fit(s.subtitle, Math.max(40, pillsLeft - panelX - 26)), panelX + 14, panelY + 27, Theme.TEXT_DIM);
		g.fill(panelX + 14, panelY + 38, panelX + panelW - 14, panelY + 39, Theme.white(0x14));

		// Vorschau-Karte
		if (previewArea != null) {
			Draw.round(g, previewX, previewY, previewW, previewH, 6, 0x40000000);
			Draw.roundBorder(g, previewX, previewY, previewW, previewH, 6, Theme.white(0x22));
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);

		// ---- Inhalte (scrollbar, abgeschnitten)
		int max = maxScroll();
		scroll = Math.max(0, Math.min(max, scroll));
		for (int i = 0; i < content.size(); i++) {
			var w = content.get(i);
			int y = contentBaseY.get(i) - (int) Math.round(scroll);
			w.setY(y);
			w.visible = y + w.getHeight() > viewTop && y < viewBottom;
		}
		boolean mouseIn = inView(mouseX, mouseY);
		g.enableScissor(contentX - 6, viewTop, contentX + contentW + 6, viewBottom);
		for (var w : content) {
			if (w.visible) w.render(g, mouseIn ? mouseX : -1000, mouseIn ? mouseY : -1000, delta);
		}
		g.disableScissor();
		if (max > 0) {
			// Hinweis: es gibt mehr → weiche Kante + Scrollbalken
			int barX = contentX + contentW + 3;
			int trackH = viewBottom - viewTop;
			int barH = Math.max(16, trackH * trackH / (trackH + max));
			int barY = viewTop + (int) ((trackH - barH) * (scroll / max));
			Draw.round(g, barX, viewTop, 3, trackH, 1, Theme.white(0x10));
			Draw.round(g, barX, barY, 3, barH, 1, Theme.white(0x70));
			if (scroll < max) {
				Draw.vGradient(g, contentX - 6, viewBottom - 12, contentW + 12, 12, 0x00000000, 0x90060608);
			}
			if (scroll > 0) {
				Draw.vGradient(g, contentX - 6, viewTop, contentW + 12, 10, 0x90060608, 0x00000000);
			}
		}

		if (previewArea != null) {
			this.mouseXNow = mouseX;
			this.mouseYNow = mouseY;
			previewArea.accept(g);
		}

		// Infos unten in der Glas-Fläche
		int y = panelY + panelH - 10 - infoLines.size() * 11;
		for (Supplier<String> line : infoLines) {
			String text = Draw.fit(line.get(), panelW - 28);
			Draw.text(g, "•", panelX + 14, y, Theme.accent());
			Draw.text(g, text, panelX + 22, y, Theme.TEXT_DIM);
			y += 11;
		}

		// Fußzeile
		Draw.text(g, "ESC zurück  ·  R-Shift öffnet das Menü im Spiel", PAD, this.height - 21, Theme.TEXT_FAINT);
		if (OemerConfig.get().showVersion) {
			String v = "v" + OemerClient.VERSION;
			int vw = Draw.width(v) + 12;
			int vx = this.width - PAD - 76 - 8 - vw;
			Draw.round(g, vx, this.height - 25, vw, 16, 8, Theme.white(0x12));
			Draw.text(g, v, vx + 6, this.height - 21, Theme.TEXT_DIM);
		}

		// Einblenden beim Öffnen
		if (fadeIn) {
			float t = (System.currentTimeMillis() - openedAt) / 220f;
			if (t < 1f) {
				int a = Math.round(255 * (1f - t) * (1f - t));
				g.fill(0, 0, this.width, this.height, (a << 24) | 0x060608);
			}
		}

	}

	private int mouseXNow, mouseYNow;

	// ------------------------------------------------------------ Vorschauen

	private void previewLabel(GuiGraphics g, String text) {
		Draw.centered(g, text, previewX + previewW / 2, previewY + previewH - 14, Theme.TEXT_DIM);
	}

	private void drawCapePreview(GuiGraphics g) {
		BufferedImage img = CapeManager.previewImage();
		if (img == null) {
			Draw.centered(g, "Kein Bild", previewX + previewW / 2, previewY + previewH / 2 - 4, Theme.TEXT_DIM);
			return;
		}
		Textures.Tex t = Textures.image("cape_preview", CapeManager.previewKey() + "@" + img.hashCode(), img);
		if (t == null) return;
		int u = Math.max(1, img.getWidth() / 64);
		int availH = previewH - 34;
		int ch = Math.min(availH, (previewW - 20) * 16 / 10);
		int cw = ch * 10 / 16;
		int cx = previewX + (previewW - cw) / 2;
		int cy = previewY + 10;
		Draw.shadow(g, cx, cy, cw, ch, 3, 120);
		Textures.drawRegion(g, t, cx, cy, cw, ch, u, u, 10 * u, 16 * u);
		Draw.roundBorder(g, cx - 1, cy - 1, cw + 2, ch + 2, 2, Theme.white(0x40));
		previewLabel(g, CapeManager.displayName(OemerConfig.get().capeSelection) + (OemerConfig.get().customCape ? "" : "  (aus)"));
	}

	private void drawPlayerPreview(GuiGraphics g) {
		if (this.minecraft.player == null) return;
		int size = Math.min(previewW, previewH) * 2 / 5;
		InventoryScreen.renderEntityInInventoryFollowsMouse(g, previewX + 4, previewY + 4, previewX + previewW - 4,
				previewY + previewH - 20, size, 0.0625f, mouseXNow, mouseYNow, this.minecraft.player);
		previewLabel(g, "Vorschau · Maus bewegen");
	}

	private void drawTotemPreview(GuiGraphics g) {
		OemerConfig c = OemerConfig.get();
		Textures.Tex t = c.totemSelection.isEmpty() ? null
				: Textures.file("totem_preview", LooksPack.totemFolder().resolve(c.totemSelection), 256);
		if (t == null) {
			Draw.centered(g, "Kein Bild im Ordner totems/", previewX + previewW / 2, previewY + previewH / 2 - 4, Theme.TEXT_DIM);
			return;
		}
		int s = Math.min(previewW - 30, previewH - 40);
		int x = previewX + (previewW - s) / 2, y = previewY + 10;
		Draw.shadow(g, x, y, s, s, 6, 110);
		Textures.drawCover(g, t, x, y, s, s);
		previewLabel(g, LooksPack.displayName(c.totemSelection) + (c.customTotem ? "" : "  (aus)"));
	}

	private void drawLookPreview(GuiGraphics g) {
		Textures.Tex t = Looks.selected();
		if (t == null) {
			Draw.centered(g, "Kein Bild im Ordner looks/", previewX + previewW / 2, previewY + previewH / 2 - 4, Theme.TEXT_DIM);
			return;
		}
		int w = previewW - 20;
		int h = Math.min(previewH - 34, w * 9 / 16);
		w = h * 16 / 9;
		int x = previewX + (previewW - w) / 2, y = previewY + 10;
		Draw.shadow(g, x, y, w, h, 4, 110);
		Textures.drawCover(g, t, x, y, w, h);
		int dim = OemerConfig.LOOK_DIM[Math.floorMod(OemerConfig.get().lookDim, OemerConfig.LOOK_DIM.length)];
		g.fill(x, y, x + w, y + h, dim << 24);
		Draw.centered(g, "§lÖMER", x + w / 2, y + h / 2 - 4, Theme.TEXT);
		previewLabel(g, LooksPack.displayName(OemerConfig.get().lookImage) + (OemerConfig.get().lookMode ? "" : "  (aus)"));
	}

	// ------------------------------------------------------------ Schließen

	@Override
	public void onClose() {
		OemerConfig.save();
		if (looksChanged) {
			looksChanged = false;
			LooksPack.apply(this.minecraft);
		}
		this.minecraft.setScreen(parent);
	}
}
