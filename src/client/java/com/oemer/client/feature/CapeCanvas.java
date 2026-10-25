package com.oemer.client.feature;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Die Malfläche vom Cape-Editor: die Rückseite vom Cape (das, was andere von hinten sehen).
 * Klassisch 10x16 Pixel oder HD 20x32 Pixel.
 */
public class CapeCanvas {
	public final int width;
	public final int height;
	public final int scale; // 1 = klassisch, 2 = HD
	private int[] pixels;
	private final Deque<int[]> undo = new ArrayDeque<>();
	private final Deque<int[]> redo = new ArrayDeque<>();

	public CapeCanvas(int scale) {
		this.scale = scale;
		this.width = 10 * scale;
		this.height = 16 * scale;
		this.pixels = new int[width * height];
		Arrays.fill(pixels, 0xFFF4F4F4);
	}

	public int get(int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) return 0;
		return pixels[y * width + x];
	}

	public void set(int x, int y, int argb) {
		if (x < 0 || y < 0 || x >= width || y >= height) return;
		pixels[y * width + x] = argb;
	}

	/** Pinsel mit Größe 1 (1 Pixel), 2 (2x2) oder 3 (Kreuz 3x3). */
	public void brush(int x, int y, int size, int argb) {
		if (size <= 1) {
			set(x, y, argb);
		} else if (size == 2) {
			set(x, y, argb);
			set(x + 1, y, argb);
			set(x, y + 1, argb);
			set(x + 1, y + 1, argb);
		} else {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					set(x + dx, y + dy, argb);
				}
			}
		}
	}

	/** Linie zwischen zwei Punkten, damit schnelles Ziehen keine Lücken macht. */
	public void line(int x0, int y0, int x1, int y1, int size, int argb) {
		int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
		int err = dx + dy;
		while (true) {
			brush(x0, y0, size, argb);
			if (x0 == x1 && y0 == y1) break;
			int e2 = 2 * err;
			if (e2 >= dy) { err += dy; x0 += sx; }
			if (e2 <= dx) { err += dx; y0 += sy; }
		}
	}

	/** Eimer: füllt alle zusammenhängenden Pixel mit derselben Farbe. */
	public void fill(int x, int y, int argb) {
		int target = get(x, y);
		if (target == argb || x < 0 || y < 0 || x >= width || y >= height) return;
		Deque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[]{x, y});
		while (!stack.isEmpty()) {
			int[] p = stack.pop();
			int px = p[0], py = p[1];
			if (px < 0 || py < 0 || px >= width || py >= height || get(px, py) != target) continue;
			set(px, py, argb);
			stack.push(new int[]{px + 1, py});
			stack.push(new int[]{px - 1, py});
			stack.push(new int[]{px, py + 1});
			stack.push(new int[]{px, py - 1});
		}
	}

	public void clear(int argb) {
		Arrays.fill(pixels, argb);
	}

	/** Spiegelt links/rechts. */
	public void mirror() {
		int[] copy = pixels.clone();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				pixels[y * width + x] = copy[y * width + (width - 1 - x)];
			}
		}
	}

	// ------------------------------------------------------- Rückgängig

	/** Vor jeder Änderung aufrufen. */
	public void snapshot() {
		undo.push(pixels.clone());
		if (undo.size() > 64) undo.removeLast();
		redo.clear();
	}

	public boolean undo() {
		if (undo.isEmpty()) return false;
		redo.push(pixels);
		pixels = undo.pop();
		return true;
	}

	public boolean redo() {
		if (redo.isEmpty()) return false;
		undo.push(pixels);
		pixels = redo.pop();
		return true;
	}

	// ------------------------------------------------ Laden & Speichern

	/** Nimmt ein Bild als Vorlage: Cape-Dateien (2:1) → die Rückseite, sonst das ganze Bild. */
	public void loadFrom(BufferedImage src) {
		int sx = 0, sy = 0, sw = src.getWidth(), sh = src.getHeight();
		if (src.getWidth() == src.getHeight() * 2 && src.getWidth() >= 64) {
			int u = src.getWidth() / 64; // Pixel pro Cape-Pixel
			sx = u;
			sy = u;
			sw = 10 * u;
			sh = 16 * u;
		} else {
			// Mitte im Seitenverhältnis 10:16 ausschneiden
			double target = 10.0 / 16.0;
			double have = (double) sw / sh;
			if (have > target) {
				int nw = (int) Math.round(sh * target);
				sx = (sw - nw) / 2;
				sw = nw;
			} else {
				int nh = (int) Math.round(sw / target);
				sy = (sh - nh) / 2;
				sh = nh;
			}
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				pixels[y * width + x] = average(src, sx + x * sw / width, sy + y * sh / height,
						Math.max(1, sw / width), Math.max(1, sh / height));
			}
		}
	}

	private static int average(BufferedImage img, int x0, int y0, int w, int h) {
		long a = 0, r = 0, g = 0, b = 0;
		int n = 0;
		for (int y = y0; y < Math.min(img.getHeight(), y0 + h); y++) {
			for (int x = x0; x < Math.min(img.getWidth(), x0 + w); x++) {
				int c = img.getRGB(x, y);
				a += c >>> 24;
				r += (c >> 16) & 255;
				g += (c >> 8) & 255;
				b += c & 255;
				n++;
			}
		}
		if (n == 0) return 0xFFFFFFFF;
		int alpha = (int) (a / n) < 128 ? 0 : 255; // Capes kennen keine halbe Transparenz
		return (alpha << 24) | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
	}

	/**
	 * Baut eine echte Cape-Datei (64x32 bzw. 128x64), so wie Minecraft sie erwartet:
	 * Rückseite, Innenseite, Ränder und die Elytra bekommen alle deine Farben.
	 */
	public BufferedImage toCapeImage() {
		int u = scale;
		BufferedImage out = new BufferedImage(64 * u, 32 * u, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int c = opaque(get(x, y));
				out.setRGB(u + x, u + y, c);                         // Rückseite (sieht man von hinten)
				out.setRGB(12 * u + (width - 1 - x), u + y, c);      // Innenseite (gespiegelt)
			}
		}
		// Oberkante und Unterkante
		for (int x = 0; x < width; x++) {
			for (int k = 0; k < u; k++) {
				out.setRGB(u + x, k, opaque(get(x, 0)));                       // oben
				out.setRGB(11 * u + x, k, opaque(get(x, height - 1)));         // unten
			}
		}
		// Seiten
		for (int y = 0; y < height; y++) {
			for (int k = 0; k < u; k++) {
				out.setRGB(k, u + y, opaque(get(0, y)));                       // links
				out.setRGB(11 * u + k, u + y, opaque(get(width - 1, y)));      // rechts
			}
		}
		// Elytra (Bereich 22..46 x 0..22): Muster gestreckt drauf
		for (int y = 0; y < 22 * u; y++) {
			for (int x = 0; x < 24 * u; x++) {
				int px = Math.min(width - 1, x * width / (24 * u));
				int py = Math.min(height - 1, y * height / (22 * u));
				out.setRGB(22 * u + x, y, opaque(get(px, py)));
			}
		}
		return out;
	}

	private static int opaque(int c) {
		return (c >>> 24) < 128 ? 0xFF000000 : (0xFF000000 | c);
	}

	/** Speichert als "Mein Cape N.png" im capes-Ordner und gibt den Dateinamen zurück. */
	public String saveNew(Path folder) throws java.io.IOException {
		Files.createDirectories(folder);
		int n = 1;
		while (Files.exists(folder.resolve("Mein Cape " + n + ".png"))) n++;
		String name = "Mein Cape " + n + ".png";
		Files.write(folder.resolve(name), ImageTools.toPng(toCapeImage()));
		return name;
	}

	/** Überschreibt eine bestehende Datei. */
	public void saveAs(Path file) throws java.io.IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, ImageTools.toPng(toCapeImage()));
	}
}
