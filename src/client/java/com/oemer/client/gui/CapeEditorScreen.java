package com.oemer.client.gui;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeCanvas;
import com.oemer.client.feature.CapeManager;
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

/** Cape-Editor: dein Cape Pixel für Pixel selbst malen. */
public class CapeEditorScreen extends Screen {

	public enum Tool {
		PINSEL("Pinsel"), EIMER("Eimer"), RADIERER("Radierer"), PIPETTE("Pipette");

		final String label;

		Tool(String label) {
			this.label = label;
		}
	}

	public static final int[] PALETTE = {
			0xFFF4F4F4, 0xFFBFBFC6, 0xFF7A7A85, 0xFF2E2E35, 0xFF0B0B0F, 0xFFE5484D, 0xFFFF8A3D, 0xFFFFD23F, 0xFF9BE15D,
			0xFF2FBF71, 0xFF2EC4B6, 0xFF8ED6FF, 0xFF3B82F6, 0xFF6366F1, 0xFFA855F7, 0xFFFF8FC7, 0xFF8B5A2B, 0xFFE8D3B0,
	};
	private static final int ERASE = 0xFFF4F4F4;
	private static final String[] BRUSH_NAMES = {"1 Pixel", "2x2", "3x3"};

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
		super(Component.literal("Cape-Editor"));
		this.parent = parent;
		newCanvas(OemerConfig.EDITOR_SCALE[Math.floorMod(OemerConfig.get().editorSize, OemerConfig.EDITOR_SCALE.length)]);
		// Das aktuelle eigene Cape direkt zum Bearbeiten laden
		String sel = OemerConfig.get().capeSelection;
		if (!sel.startsWith("design:")) {
			loadFile(sel, false);
		}
	}

	private void newCanvas(int scale) {
		canvas = new CapeCanvas(scale);
		editingFile = null;
	}

	private void loadFile(String file, boolean asTemplate) {
		try {
			BufferedImage img = ImageTools.read(CapeManager.folder().resolve(file));
			canvas.loadFrom(img);
			editingFile = asTemplate ? null : file;
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Cape {} konnte nicht in den Editor geladen werden", file, e);
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

		int rx = leftW + 20;
		int rw = this.width - rx - 14;
		int y = top + 8;

		// Werkzeuge
		int tw = (rw - 3 * 4) / 4;
		for (int i = 0; i < Tool.values().length; i++) {
			Tool t = Tool.values()[i];
			this.addRenderableWidget(new OmerWidgets.Flat(rx + i * (tw + 4), y, tw, 18, t.label, () -> tool = t, () -> tool == t));
		}
		y += 26;
		int half = (rw - 6) / 2;
		this.addRenderableWidget(new OmerWidgets.Cycle(rx, y, half, 20, "Pinsel", BRUSH_NAMES, () -> brush, v -> brush = v));
		this.addRenderableWidget(new OmerWidgets.Cycle(rx + half + 6, y, rw - half - 6, 20, "Größe",
				OemerConfig.EDITOR_SIZE_NAMES, () -> OemerConfig.get().editorSize, v -> {
			OemerConfig.get().editorSize = v;
			OemerConfig.save();
			newCanvas(OemerConfig.EDITOR_SCALE[v]);
			say("Neue leere Fläche: " + OemerConfig.EDITOR_SIZE_NAMES[v]);
			this.rebuildWidgets();
		}));
		y += 30;

		// Farben
		int sw = 14, gap = 4;
		int perRow = Math.max(6, Math.min(PALETTE.length, (rw + gap) / (sw + gap)));
		for (int i = 0; i < PALETTE.length; i++) {
			int c = PALETTE[i];
			int sx = rx + (i % perRow) * (sw + gap);
			int sy = y + (i / perRow) * (sw + gap);
			this.addRenderableWidget(new OmerWidgets.Swatch(sx, sy, sw, c, () -> color == c, () -> {
				color = c;
				if (tool == Tool.RADIERER || tool == Tool.PIPETTE) tool = Tool.PINSEL;
			}));
		}
		int rows = (PALETTE.length + perRow - 1) / perRow;
		int accentX = rx + (PALETTE.length % perRow) * (sw + gap);
		int accentY = y + (rows - 1) * (sw + gap);
		if (PALETTE.length % perRow == 0) {
			accentX = rx;
			accentY = y + rows * (sw + gap);
			rows++;
		}
		int ac = Theme.accent() | 0xFF000000;
		this.addRenderableWidget(new OmerWidgets.Swatch(accentX, accentY, sw, ac, () -> color == ac, () -> color = ac));
		y += rows * (sw + gap) + 6;

		this.addRenderableWidget(new OmerWidgets.Flat(rx, y, half, 18, "Heller", () -> color = shade(color, 1.18f)));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + half + 6, y, rw - half - 6, 18, "Dunkler", () -> color = shade(color, 0.82f)));
		y += 26;

		// Bearbeiten
		int third = (rw - 8) / 3;
		this.addRenderableWidget(new OmerWidgets.Flat(rx, y, third, 18, "Rückgängig", () -> {
			if (!canvas.undo()) say("Nichts zum Rückgängigmachen");
		}));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + third + 4, y, third, 18, "Wiederholen", () -> canvas.redo()));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + 2 * (third + 4), y, rw - 2 * (third + 4), 18, "Spiegeln", () -> {
			canvas.snapshot();
			canvas.mirror();
		}));
		y += 24;
		this.addRenderableWidget(new OmerWidgets.Flat(rx, y, half, 18, "Alles füllen", () -> {
			canvas.snapshot();
			canvas.clear(color);
		}));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + half + 6, y, rw - half - 6, 18, "Aktuelles Cape laden", this::loadCurrent));
		y += 30;

		// Speichern
		this.addRenderableWidget(new OmerWidgets.Flat(rx, y, half, 22, "Speichern", this::save).style(OmerWidgets.Style.PRIMARY));
		this.addRenderableWidget(new OmerWidgets.Flat(rx + half + 6, y, rw - half - 6, 22, "Als neues Cape", () -> {
			editingFile = null;
			save();
		}));

		// Zurück
		this.addRenderableWidget(new OmerWidgets.Flat(14, 12, 64, 20, "‹ Zurück", this::onClose).style(OmerWidgets.Style.GHOST));
	}

	private void loadCurrent() {
		String sel = OemerConfig.get().capeSelection;
		try {
			canvas.snapshot();
			BufferedImage img = sel.startsWith("design:") ? CapeManager.previewImage() : ImageTools.read(CapeManager.folder().resolve(sel));
			if (img != null) {
				canvas.loadFrom(img);
				editingFile = sel.startsWith("design:") ? null : sel;
				say("Geladen: " + CapeManager.displayName(sel));
			}
		} catch (Exception e) {
			say("Konnte das Cape nicht laden");
		}
	}

	private void save() {
		try {
			String name;
			if (editingFile != null) {
				canvas.saveAs(CapeManager.folder().resolve(editingFile));
				name = editingFile;
			} else {
				name = canvas.saveNew(CapeManager.folder());
				editingFile = name;
			}
			OemerConfig c = OemerConfig.get();
			c.capeSelection = name;
			c.customCape = true;
			OemerConfig.save();
			CapeManager.invalidate();
			say("Gespeichert: " + name.replace(".png", "") + " · ist jetzt dein Cape");
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Cape konnte nicht gespeichert werden", e);
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
				if (lastPx >= 0 && painting) canvas.line(lastPx, lastPy, px, py, size, ERASE);
				else canvas.brush(px, py, size, ERASE);
			}
			case EIMER -> canvas.fill(px, py, color);
			case PIPETTE -> {
				color = canvas.get(px, py) | 0xFF000000;
				if (!right) tool = Tool.PINSEL;
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
		Draw.text(g, "Cape-Editor", 0, 0, Theme.TEXT);
		g.pose().popMatrix();
		String sub = editingFile != null ? "bearbeitet: " + editingFile.replace(".png", "") : "neues Cape";
		Draw.text(g, sub, 90 + Math.round(Draw.width("Cape-Editor") * 1.35f) + 10, 17, Theme.TEXT_DIM);

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
				g.fill(px, py, px + pixel, py + pixel, canvas.get(x, y) | 0xFF000000);
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
