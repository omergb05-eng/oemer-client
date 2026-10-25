package com.oemer.client.gui;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeCanvas;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.Crosshair;
import com.oemer.client.feature.CustomCosmetics;
import com.oemer.client.feature.LooksPack;
import com.oemer.client.feature.SkinTotem;
import com.oemer.client.feature.ImageTools;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pixel-Editor: Cape, Totem, Crosshair oder eigene Cosmetics selbst malen. */
public class CapeEditorScreen extends Screen {

	/** Was gemalt wird. */
	public enum Mode {
		CAPE("Cape-Editor", "Mein Cape", new String[]{"10x16", "HD 20x32"}),
		TOTEM("Totem-Editor", "Mein Totem", new String[]{"16x16", "HD 32x32"}),
		CROSSHAIR("Crosshair-Editor", "Mein Crosshair", new String[]{"15x15", "HD 31x31"}),
		COSMETIC("Cosmetic-Editor", "Mein Cosmetic", new String[]{"16x16", "24x24"});

		final String title;
		final String prefix;
		final String[] sizes;

		Mode(String title, String prefix, String[] sizes) {
			this.title = title;
			this.prefix = prefix;
			this.sizes = sizes;
		}

		Path folder() {
			return switch (this) {
				case CAPE -> CapeManager.folder();
				case TOTEM -> LooksPack.totemFolder();
				case CROSSHAIR -> Crosshair.folder();
				case COSMETIC -> CustomCosmetics.folder();
			};
		}

		CapeCanvas create(int sizeIdx) {
			return switch (this) {
				case CAPE -> new CapeCanvas(OemerConfig.EDITOR_SCALE[Math.floorMod(sizeIdx, 2)]);
				case TOTEM -> sizeIdx == 0 ? new CapeCanvas(16, 16, false) : new CapeCanvas(32, 32, false);
				case CROSSHAIR -> sizeIdx == 0 ? new CapeCanvas(15, 15, false) : new CapeCanvas(31, 31, false);
				case COSMETIC -> sizeIdx == 0 ? new CapeCanvas(16, 16, false) : new CapeCanvas(24, 24, false);
			};
		}

		/** Beim Speichern so viel vergrößern (scharf). */
		int upscale(CapeCanvas c) {
			return switch (this) {
				case CAPE, COSMETIC -> 1;
				case TOTEM -> 64 / c.width;
				case CROSSHAIR -> 2;
			};
		}
	}

	private final Mode mode;
	private static final int[] SIZE_INDEX = new int[Mode.values().length];

	public enum Tool {
		PINSEL("Pinsel"), EIMER("Eimer"), RADIERER("Gummi"), PIPETTE("Pipette");

		final String label;

		Tool(String label) {
			this.label = label;
		}
	}

	public static final int[] PALETTE = {
			0xFFF4F4F4, 0xFFBFBFC6, 0xFF7A7A85, 0xFF2E2E35, 0xFF0B0B0F, 0xFFE5484D, 0xFFFF8A3D, 0xFFFFD23F, 0xFF9BE15D,
			0xFF2FBF71, 0xFF2EC4B6, 0xFF8ED6FF, 0xFF3B82F6, 0xFF6366F1, 0xFFA855F7, 0xFFFF8FC7, 0xFF8B5A2B, 0xFFE8D3B0,
	};
	private static final String[] BRUSH_NAMES = {"1px", "2px", "3px"};

	private final Screen parent;
	private CapeCanvas canvas;
	private static Tool tool = Tool.PINSEL;
	private static int color = 0xFF0B0B0F;
	private static int brush = 0;
	/** Datei, die gerade bearbeitet wird (null = neues Cape). */
	private String editingFile;

	private int canvasX, canvasY, pixel;
	private boolean painting;
	private int lastPx = -1, lastPy = -1;
	private String status = "";
	private long statusUntil;

	public CapeEditorScreen(Screen parent) {
		this(parent, Mode.CAPE);
	}

	public CapeEditorScreen(Screen parent, Mode mode) {
		super(Component.literal(mode.title));
		this.parent = parent;
		this.mode = mode;
		if (mode == Mode.CAPE) SIZE_INDEX[0] = OemerConfig.get().editorSize;
		newCanvas(SIZE_INDEX[mode.ordinal()]);
		// Die aktuelle eigene Datei direkt zum Bearbeiten laden
		String sel = currentFile();
		if (sel != null) {
			// Eigene Bilder (Fotos usw.) nie überschreiben – nur Dateien, die der Editor selbst gemacht hat
			loadFile(sel, !sel.startsWith(mode.prefix));
		}
	}

	/** Die gerade benutzte eigene Datei in diesem Modus (null = keine). */
	private String currentFile() {
		OemerConfig c = OemerConfig.get();
		String f = switch (mode) {
			case CAPE -> c.capeSelection.startsWith("design:") ? null : c.capeSelection;
			case TOTEM -> c.totemMode == 0 && !c.totemSelection.isEmpty() ? c.totemSelection : null;
			case CROSSHAIR -> c.crosshairSelection.startsWith("design:") ? null : c.crosshairSelection;
			case COSMETIC -> null;
		};
		return f != null && Files.isRegularFile(mode.folder().resolve(f)) ? f : null;
	}

	private void newCanvas(int sizeIdx) {
		canvas = mode.create(sizeIdx);
		editingFile = null;
	}

	private void loadFile(String file, boolean asTemplate) {
		try {
			BufferedImage img = ImageTools.read(mode.folder().resolve(file));
			canvas.loadFrom(img);
			editingFile = asTemplate ? null : file;
		} catch (Exception e) {
			OemerClient.LOGGER.warn("{} konnte nicht in den Editor geladen werden", file, e);
		}
	}

	private void say(String text) {
		status = text;
		statusUntil = System.currentTimeMillis() + 3500;
	}

	// ============================================================== Aufbau

	@Override
	protected void init() {
		int top = 46;
		int availH = this.height - top - 40;
		int leftW = (int) (this.width * 0.46f);
		pixel = Math.max(2, Math.min(availH / canvas.height, (leftW - 30) / canvas.width));
		int cw = canvas.width * pixel, ch = canvas.height * pixel;
		canvasX = 14 + (leftW - cw) / 2;
		canvasY = top + (availH - ch) / 2 + 4;

		int rx = leftW + 22;
		int rw = this.width - rx - 18;

		// Höhe der Zeilen und Abstände an den Platz anpassen (kleine Fenster = kompakter)
		int space = this.height - top - 8 - 34;
		int rowH = space < 190 ? 15 : 18;
		int sw = space < 190 ? 12 : 14;
		int swGap = 3;
		int perRow = Math.max(6, Math.min(PALETTE.length + 1, (rw + swGap) / (sw + swGap)));
		int paletteRows = (PALETTE.length + 1 + perRow - 1) / perRow;
		int fixed = rowH * 6 + (rowH + 4) + paletteRows * (sw + swGap);
		int gap = Math.max(2, Math.min(8, (space - fixed) / 7));
		int y = top + 6;

		// Werkzeuge
		int tw = (rw - 3 * 4) / 4;
		for (int i = 0; i < Tool.values().length; i++) {
			Tool t = Tool.values()[i];
			this.addRenderableWidget(new OmerWidgets.Flat(rx + i * (tw + 4), y, tw, rowH, t.label, () -> tool = t, () -> tool == t));
		}
		y += rowH + gap;
		int half = (rw - 6) / 2;
		this.addRenderableWidget(new OmerWidgets.Cycle(rx, y, half, rowH, "Pinsel", BRUSH_NAMES, () -> brush, v -> brush = v));
		this.addRenderableWidget(new OmerWidgets.Cycle(rx + half + 6, y, rw - half - 6, rowH, "Fläche",
				mode.sizes, () -> SIZE_INDEX[mode.ordinal()], v -> {
			SIZE_INDEX[mode.ordinal()] = v;
			if (mode == Mode.CAPE) {
				OemerConfig.get().editorSize = v;
				OemerConfig.save();
			}
			newCanvas(v);
			say("Neue leere Fläche: " + mode.sizes[v]);
			this.rebuildWidgets();
		}));
		y += rowH + gap;

		// Farben (+ Akzentfarbe als letztes Feld)
		int ac = Theme.accent() | 0xFF000000;
		for (int i = 0; i <= PALETTE.length; i++) {
			int c = i < PALETTE.length ? PALETTE[i] : ac;
			int sx = rx + (i % perRow) * (sw + swGap);
			int sy = y + (i / perRow) * (sw + swGap);
			this.addRenderableWidget(new OmerWidgets.Swatch(sx, sy, sw, c, () -> color == c, () -> {
				color = c;
				if (tool == Tool.RADIERER || tool == Tool.PIPETTE) tool = Tool.PINSEL;
			}));
		}
		y += paletteRows * (sw + swGap) + gap - swGap;

		// Farbe anpassen + Spiegeln
		int third = (rw - 8) / 3;
		this.addRenderableWidget(new OmerWidgets.Flat(rx, y, third, rowH, "Heller", () -> color = shade(color, 1.18f)));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + third + 4, y, third, rowH, "Dunkler", () -> color = shade(color, 0.82f)));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + 2 * (third + 4), y, rw - 2 * (third + 4), rowH, "Spiegeln", () -> {
			canvas.snapshot();
			canvas.mirror();
		}));
		y += rowH + gap;

		// Rückgängig / Wiederholen / Füllen
		this.addRenderableWidget(new OmerWidgets.Flat(rx, y, third, rowH, "↶ Undo", () -> {
			if (!canvas.undo()) say("Nichts zum Rückgängigmachen");
		}).tip("Rückgängig (Strg+Z)"));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + third + 4, y, third, rowH, "↷ Redo", () -> canvas.redo()).tip("Wiederholen (Strg+Y)"));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + 2 * (third + 4), y, rw - 2 * (third + 4), rowH, "Füllen", () -> {
			canvas.snapshot();
			canvas.clear(color);
		}).tip("Ganze Fläche mit der Farbe füllen"));
		y += rowH + gap;
		this.addRenderableWidget(new OmerWidgets.Flat(rx, y, rw, rowH, "Aktuelles Cape laden", this::loadCurrent));
		y += rowH + gap + 2;

		// Speichern
		this.addRenderableWidget(new OmerWidgets.Flat(rx, y, half, rowH + 4, "Speichern", this::save).style(OmerWidgets.Style.PRIMARY));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + half + 6, y, rw - half - 6, rowH + 4, "Als neue Datei", () -> {
			editingFile = null;
			save();
		}));

		// Zurück
		this.addRenderableWidget(new OmerWidgets.Flat(14, 12, 64, 20, "‹ Zurück", this::onClose).style(OmerWidgets.Style.GHOST));
	}

	private void loadCurrent() {
		try {
			canvas.snapshot();
			BufferedImage img = null;
			String name = "";
			OemerConfig c = OemerConfig.get();
			switch (mode) {
				case CAPE -> {
					String sel = c.capeSelection;
					img = sel.startsWith("design:") ? CapeManager.previewImage() : ImageTools.read(CapeManager.folder().resolve(sel));
					editingFile = sel.startsWith(mode.prefix) ? sel : null;
					name = CapeManager.displayName(sel);
				}
				case TOTEM -> {
					if (c.totemMode == 1) {
						img = SkinTotem.image();
						name = "Mein Skin";
						editingFile = null;
					} else if (!c.totemSelection.isEmpty()) {
						img = ImageTools.read(LooksPack.totemFolder().resolve(c.totemSelection));
						editingFile = c.totemSelection.startsWith(mode.prefix) ? c.totemSelection : null;
						name = c.totemSelection;
					}
				}
				case CROSSHAIR -> {
					img = Crosshair.previewImage();
					editingFile = c.crosshairSelection.startsWith(mode.prefix) ? c.crosshairSelection : null;
					name = Crosshair.displayName(c.crosshairSelection);
				}
				case COSMETIC -> {
					var list = CustomCosmetics.files();
					if (!list.isEmpty()) {
						img = ImageTools.read(CustomCosmetics.folder().resolve(list.get(0)));
						editingFile = list.get(0).startsWith(mode.prefix) ? list.get(0) : null;
						name = list.get(0);
					}
				}
			}
			if (img != null) {
				canvas.loadFrom(img);
				say("Geladen: " + name.replace(".png", ""));
			} else {
				say("Nichts zum Laden da");
			}
		} catch (Exception e) {
			say("Konnte nicht laden");
		}
	}

	private void save() {
		try {
			String name;
			Path folder = mode.folder();
			if (mode == Mode.CAPE) {
				if (editingFile != null) {
					canvas.saveAs(folder.resolve(editingFile));
					name = editingFile;
				} else {
					name = canvas.saveNew(folder);
				}
			} else {
				int up = mode.upscale(canvas);
				if (editingFile != null) {
					canvas.saveImageAs(folder.resolve(editingFile), up);
					name = editingFile;
				} else {
					name = canvas.saveNewImage(folder, mode.prefix, up);
				}
			}
			editingFile = name;

			// direkt benutzen
			OemerConfig c = OemerConfig.get();
			switch (mode) {
				case CAPE -> {
					c.capeSelection = name;
					c.customCape = true;
					CapeManager.invalidate();
				}
				case TOTEM -> {
					c.totemSelection = name;
					c.totemMode = 0;
					c.customTotem = true;
					OmerScreen.markLooksChanged();
				}
				case CROSSHAIR -> {
					c.crosshairSelection = name;
					c.customCrosshair = true;
				}
				case COSMETIC -> {
					if (c.customCosmetics.getOrDefault(name, 0) == 0) c.customCosmetics.put(name, 1);
					CustomCosmetics.invalidate();
				}
			}
			OemerConfig.save();
			say("Gespeichert: " + name.replace(".png", "") + " · ist jetzt aktiv");
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Speichern fehlgeschlagen", e);
			say("§cSpeichern fehlgeschlagen");
		}
	}

	private static int shade(int c, float f) {
		int r = Math.min(255, Math.round(((c >> 16) & 255) * f + (f > 1 ? 8 : 0)));
		int g = Math.min(255, Math.round(((c >> 8) & 255) * f + (f > 1 ? 8 : 0)));
		int b = Math.min(255, Math.round((c & 255) * f + (f > 1 ? 8 : 0)));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	// ================================================================ Malen

	private int[] pixelAt(double mx, double my) {
		int px = (int) Math.floor((mx - canvasX) / pixel);
		int py = (int) Math.floor((my - canvasY) / pixel);
		if (px < 0 || py < 0 || px >= canvas.width || py >= canvas.height) return null;
		return new int[]{px, py};
	}

	/** Für den automatischen Test: einen Pixel wie mit der Maus malen. */
	public void paintForTest(int px, int py, int argb, Tool t) {
		Tool old = tool;
		int oldColor = color;
		tool = t;
		color = argb;
		canvas.snapshot();
		apply(px, py, false);
		tool = old;
		color = oldColor;
	}

	public CapeCanvas canvas() {
		return canvas;
	}

	public void saveForTest() {
		save();
	}

	private void apply(int px, int py, boolean right) {
		Tool t = right ? Tool.PIPETTE : tool;
		int size = brush + 1;
		switch (t) {
			case PINSEL -> {
				if (lastPx >= 0 && painting) canvas.line(lastPx, lastPy, px, py, size, color);
				else canvas.brush(px, py, size, color);
			}
			case RADIERER -> {
				if (lastPx >= 0 && painting) canvas.line(lastPx, lastPy, px, py, size, canvas.eraseColor());
				else canvas.brush(px, py, size, canvas.eraseColor());
			}
			case EIMER -> canvas.fill(px, py, color);
			case PIPETTE -> {
				int picked = canvas.get(px, py);
				if ((picked >>> 24) == 0) {
					tool = Tool.RADIERER; // durchsichtig aufgenommen = Radierer
				} else {
					color = picked | 0xFF000000;
					if (!right) tool = Tool.PINSEL;
				}
			}
		}
		lastPx = px;
		lastPy = py;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		int[] p = pixelAt(event.x(), event.y());
		if (p == null) return false;
		boolean right = event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
		if (!right && tool != Tool.PIPETTE) canvas.snapshot();
		painting = false;
		lastPx = -1;
		apply(p[0], p[1], right);
		painting = !right && (tool == Tool.PINSEL || tool == Tool.RADIERER);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (painting) {
			int[] p = pixelAt(event.x(), event.y());
			if (p != null) apply(p[0], p[1], false);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		painting = false;
		lastPx = -1;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
		if (ctrl && event.key() == GLFW.GLFW_KEY_Z) {
			canvas.undo();
			return true;
		}
		if (ctrl && event.key() == GLFW.GLFW_KEY_Y) {
			canvas.redo();
			return true;
		}
		if (ctrl && event.key() == GLFW.GLFW_KEY_S) {
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	// ============================================================= Zeichnen

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Backdrop.render(g, this.width, this.height, this.minecraft.level != null);

		// Kopfzeile
		g.pose().pushMatrix();
		g.pose().translate(90, 14);
		g.pose().scale(1.35f);
		Draw.text(g, mode.title, 0, 0, Theme.TEXT);
		g.pose().popMatrix();
		String sub = editingFile != null ? "bearbeitet: " + editingFile.replace(".png", "") : "neue Datei";
		Draw.text(g, Draw.fit(sub, this.width - 110 - Math.round(Draw.width(mode.title) * 1.35f)),
				90 + Math.round(Draw.width(mode.title) * 1.35f) + 10, 17, Theme.TEXT_DIM);

		int leftW = (int) (this.width * 0.46f);
		Draw.glass(g, 10, 42, leftW, this.height - 42 - 34, 8, Theme.panel(), Theme.white(0x2A));
		Draw.glass(g, leftW + 14, 42, this.width - leftW - 24, this.height - 42 - 34, 8, Theme.panel(), Theme.white(0x2A));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);

		// Malfläche
		int cw = canvas.width * pixel, ch = canvas.height * pixel;
		Draw.shadow(g, canvasX, canvasY, cw, ch, 2, 140);
		for (int y = 0; y < canvas.height; y++) {
			for (int x = 0; x < canvas.width; x++) {
				int px = canvasX + x * pixel, py = canvasY + y * pixel;
				int col = canvas.get(x, y);
				if (!canvas.opaque && (col >>> 24) == 0) {
					// durchsichtig = Schachbrett
					int half = Math.max(1, pixel / 2);
					boolean dark = ((x + y) & 1) == 0;
					g.fill(px, py, px + pixel, py + pixel, dark ? 0xFF2A2A32 : 0xFF3A3A44);
					g.fill(px, py, px + half, py + half, dark ? 0xFF3A3A44 : 0xFF2A2A32);
					g.fill(px + half, py + half, px + pixel, py + pixel, dark ? 0xFF3A3A44 : 0xFF2A2A32);
				} else {
					g.fill(px, py, px + pixel, py + pixel, col | 0xFF000000);
				}
			}
		}
		// feines Gitter
		if (pixel >= 6) {
			int line = 0x18000000;
			for (int x = 1; x < canvas.width; x++) {
				g.fill(canvasX + x * pixel, canvasY, canvasX + x * pixel + 1, canvasY + ch, line);
			}
			for (int y = 1; y < canvas.height; y++) {
				g.fill(canvasX, canvasY + y * pixel, canvasX + cw, canvasY + y * pixel + 1, line);
			}
		}
		Draw.roundBorder(g, canvasX - 1, canvasY - 1, cw + 2, ch + 2, 2, Theme.white(0x50));
		if (mode == Mode.CROSSHAIR) {
			// Mitte markieren, damit das Crosshair zentriert wird
			int mx = canvasX + (canvas.width / 2) * pixel, my = canvasY + (canvas.height / 2) * pixel;
			Draw.roundBorder(g, mx, my, pixel, pixel, 0, Theme.accent(0x90));
		}

		// Pixel unter der Maus markieren
		int[] p = pixelAt(mouseX, mouseY);
		if (p != null) {
			int size = (tool == Tool.PINSEL || tool == Tool.RADIERER) ? brush + 1 : 1;
			int off = size == 3 ? -1 : 0;
			int hx = canvasX + (p[0] + off) * pixel, hy = canvasY + (p[1] + off) * pixel;
			int hs = pixel * size;
			Draw.roundBorder(g, hx, hy, hs, hs, 0, 0xFFFFFFFF);
			Draw.roundBorder(g, hx + 1, hy + 1, hs - 2, hs - 2, 0, 0x80000000);
		}

		// aktuelle Farbe + Hinweise unten links
		int by = this.height - 26;
		Draw.round(g, 14, by, 18, 18, 4, 0xFFFFFFFF);
		Draw.round(g, 16, by + 2, 14, 14, 3, color | 0xFF000000);
		Draw.text(g, "Links malen · Rechts Farbe aufnehmen · Strg+Z/Y/S", 38, by + 5, Theme.TEXT_FAINT);

		if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
			int w = Draw.width(status) + 20;
			int x = this.width - 14 - w;
			Draw.round(g, x, by - 2, w, 20, 10, 0xE0FFFFFF);
			Draw.text(g, status, x + 10, by + 4, 0xFF0B0B0F);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	/** Ordner anlegen, falls nötig (für "Speichern"). */
	static void ensure(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (Exception ignored) {
		}
	}
}
