package com.oemer.client.gui;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.LooksPack;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Ömer-Client-Menü: schwarzer Screen, oben rechts die Tabs
 * Settings · Skins · Layout · Graphics, darunter eine weiße Glas-Fläche.
 */
public class OmerScreen extends Screen {

	enum Tab {
		SETTINGS("Settings", "Kits und Spiel-Funktionen"),
		SKINS("Skins", "Capes, Totems und Sounds"),
		LAYOUT("Layout", "So sieht dein Client aus"),
		GRAPHICS("Graphics", "Looks und Grafik");

		final String title;
		final String subtitle;

		Tab(String title, String subtitle) {
			this.title = title;
			this.subtitle = subtitle;
		}
	}

	/** Bleibt gemerkt, solange das Spiel läuft. */
	private static Tab current = Tab.SETTINGS;

	/** Totem geändert → beim Schließen Ressourcen neu laden. Gilt über Tab-Wechsel hinweg. */
	private static boolean looksChanged;

	private static final int PAD = 16;
	private static final int TOP = 38;
	private static final int ROW_H = 22;
	private static final int GAP = 5;

	private final Screen parent;
	private final List<String> infoLines = new ArrayList<>();

	private int panelX, panelY, panelW, panelH;
	private int colW, colCount;
	private int nextIndex;

	public OmerScreen(Screen parent) {
		super(Component.literal("Ömer Client"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		infoLines.clear();
		nextIndex = 0;

		// Glas-Fläche
		panelX = PAD;
		panelY = TOP;
		panelW = this.width - PAD * 2;
		panelH = this.height - TOP - 30;

		colCount = panelW >= 340 ? 2 : 1;
		colW = (panelW - 24 - (colCount - 1) * 8) / colCount;

		// Tabs oben rechts: Settings · Skins · Layout · Graphics
		int tabH = 18;
		int x = this.width - PAD;
		Tab[] tabs = Tab.values();
		for (int i = tabs.length - 1; i >= 0; i--) {
			Tab tab = tabs[i];
			int w = this.font.width(tab.title) + 16;
			x -= w;
			this.addRenderableWidget(new OmerWidgets.Flat(x, 10, w, tabH, tab.title,
					() -> switchTab(tab), () -> current == tab));
			x -= 4;
		}

		switch (current) {
			case SETTINGS -> buildSettings();
			case SKINS -> buildSkins();
			case LAYOUT -> buildLayout();
			case GRAPHICS -> buildGraphics();
		}

		// Fertig-Button unten rechts
		this.addRenderableWidget(new OmerWidgets.Flat(this.width - PAD - 70, this.height - 24, 70, 18,
				"Fertig", this::onClose));
	}

	// ---------------------------------------------------------------- Tabs

	private void buildSettings() {
		OemerConfig c = OemerConfig.get();
		boolean inWorld = this.minecraft != null && this.minecraft.level != null;

		cycle("Kit Select", OemerConfig.KITS, () -> c.kit, v -> c.kit = v);
		toggle("Fullbright", () -> c.fullbright, v -> c.fullbright = v, false);
		toggle("Free Cam", () -> c.freecam, v -> {
			if (this.minecraft != null && this.minecraft.level != null) {
				c.freecam = v;
			}
		}, false);
		cycle("Free Cam Tempo", OemerConfig.FREECAM_SPEED_NAMES, () -> c.freecamSpeed, v -> c.freecamSpeed = v);
		toggle("Material List", () -> c.materialList, v -> c.materialList = v, true);
		cycle("Material List Ort", OemerConfig.CORNERS, () -> c.materialListCorner, v -> c.materialListCorner = v);

		infoLines.add("Tasten: R-Shift Menü · F4 Free Cam · G Fullbright");
		infoLines.add(inWorld
				? "Free Cam: WASD fliegen, Leertaste hoch, Shift runter."
				: "Free Cam geht nur in einer Welt oder auf Servern.");
	}

	private void buildSkins() {
		OemerConfig c = OemerConfig.get();

		// Cape: Designs + eigene Bilder
		List<String> capes = CapeManager.choices();
		String[] capeNames = capes.stream().map(CapeManager::displayName).toArray(String[]::new);
		toggle("Eigenes Cape", () -> c.customCape, v -> c.customCape = v, false);
		cycle("Cape", capeNames, () -> Math.max(0, capes.indexOf(c.capeSelection)), v -> {
			c.capeSelection = capes.get(v);
			CapeManager.invalidate();
		});

		// Totem: eigene Bilder
		List<String> totems = LooksPack.totemChoices();
		if (!totems.contains(c.totemSelection)) {
			c.totemSelection = totems.isEmpty() ? "" : totems.get(0);
		}
		String[] totemNames = totems.isEmpty()
				? new String[]{"(kein Bild)"}
				: totems.stream().map(LooksPack::displayName).toArray(String[]::new);
		toggle("Custom Totem", () -> c.customTotem, v -> {
			c.customTotem = v;
			looksChanged = true;
		}, false);
		cycle("Totem-Bild", totemNames, () -> Math.max(0, totems.indexOf(c.totemSelection)), v -> {
			if (!totems.isEmpty()) {
				c.totemSelection = totems.get(v);
				looksChanged = true;
			}
		});

		toggle("Custom Sounds", () -> c.customSounds, v -> c.customSounds = v, true);
		button("Bilder-Ordner öffnen", () -> Util.getPlatform().openPath(CapeManager.folder().getParent()));

		infoLines.add("Bilder (PNG/JPG) in die Ordner capes/ und totems/ legen.");
		infoLines.add("Das Cape siehst nur du. Totem lädt beim Schließen neu.");
	}

	private void buildLayout() {
		OemerConfig c = OemerConfig.get();
		cycle("Transparenz", OemerConfig.OPACITY_NAMES, () -> c.menuOpacity, v -> c.menuOpacity = v);
		cycle("Akzentfarbe", OemerConfig.ACCENT_NAMES, () -> c.accent, v -> c.accent = v);
		toggle("Version anzeigen", () -> c.showVersion, v -> c.showVersion = v, false);

		infoLines.add("Änderungen siehst du sofort.");
	}

	private void buildGraphics() {
		OemerConfig c = OemerConfig.get();
		toggle("Look-Modus", () -> c.lookMode, v -> c.lookMode = v, true);
		toggle("Menü-Blur", () -> c.menuBlur, v -> c.menuBlur = v, true);

		infoLines.add("Look-Modus: eigene Bilder als Hintergrund und Theme.");
	}

	// ------------------------------------------------------------- Helfer

	private int[] nextSlot() {
		int col = nextIndex % colCount;
		int row = nextIndex / colCount;
		nextIndex++;
		int x = panelX + 12 + col * (colW + 8);
		int y = panelY + 34 + row * (ROW_H + GAP);
		return new int[]{x, y};
	}

	private void toggle(String label, java.util.function.BooleanSupplier get,
						java.util.function.Consumer<Boolean> set, boolean soon) {
		int[] p = nextSlot();
		this.addRenderableWidget(new OmerWidgets.Toggle(p[0], p[1], colW, ROW_H, label, get,
				v -> {
					set.accept(v);
					OemerConfig.save();
				}, soon));
	}

	private void cycle(String label, String[] options, java.util.function.IntSupplier get,
					   java.util.function.IntConsumer set) {
		int[] p = nextSlot();
		this.addRenderableWidget(new OmerWidgets.Cycle(p[0], p[1], colW, ROW_H, label, options, get,
				v -> {
					set.accept(v);
					OemerConfig.save();
				}));
	}

	private void button(String label, Runnable action) {
		int[] p = nextSlot();
		this.addRenderableWidget(new OmerWidgets.Flat(p[0], p[1], colW, ROW_H, label, action));
	}

	private void switchTab(Tab tab) {
		if (current == tab) {
			return;
		}
		current = tab;
		// Screen neu öffnen = Tab neu aufbauen
		this.minecraft.setScreen(new OmerScreen(parent));
	}

	// ------------------------------------------------------------ Zeichnen

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// komplett schwarzer Screen (im Spiel leicht durchsichtig)
		boolean inWorld = this.minecraft != null && this.minecraft.level != null;
		g.fill(0, 0, this.width, this.height, inWorld ? 0xD8000000 : Theme.BACKGROUND);
		// weiße, durchsichtige Glas-Fläche
		OmerWidgets.frame(g, panelX, panelY, panelW, panelH, Theme.panel(), Theme.white(0x30));
		// dünne Linie unter der Kopfzeile
		g.fill(0, TOP - 8, this.width, TOP - 7, Theme.white(0x18));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);

		// Logo oben links
		g.drawString(this.font, "§lÖMER §rCLIENT", PAD, 15, Theme.TEXT, false);
		int logoW = this.font.width("§lÖMER §rCLIENT");
		g.fill(PAD + logoW + 5, 14, PAD + logoW + 7, 24, Theme.accent());

		// Tab-Überschrift in der Glas-Fläche
		g.drawString(this.font, current.title, panelX + 12, panelY + 10, Theme.TEXT, false);
		g.drawString(this.font, current.subtitle, panelX + 12 + this.font.width(current.title) + 8,
				panelY + 10, Theme.TEXT_DIM, false);

		// Infos unten in der Glas-Fläche
		int y = panelY + panelH - 10 - infoLines.size() * 11;
		for (String line : infoLines) {
			g.drawString(this.font, line, panelX + 12, y, Theme.TEXT_DIM, false);
			y += 11;
		}

		// Fußzeile
		g.drawString(this.font, "ESC = zurück", PAD, this.height - 19, Theme.TEXT_FAINT, false);
		if (OemerConfig.get().showVersion) {
			String v = "v" + OemerClient.VERSION;
			g.drawString(this.font, v, this.width - PAD - 78 - this.font.width(v), this.height - 19,
					Theme.TEXT_FAINT, false);
		}
	}

	@Override
	public void onClose() {
		OemerConfig.save();
		if (looksChanged && this.minecraft != null) {
			looksChanged = false;
			LooksPack.apply(this.minecraft);
		}
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		}
	}
}
