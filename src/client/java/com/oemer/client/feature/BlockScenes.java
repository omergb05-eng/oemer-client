package com.oemer.client.feature;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Menü-Hintergründe im echten Minecraft-Look: Pixel-Landschaften (320x180, werden scharf hochgezogen)
 * aus Blöcken mit dem typischen Rauschen. Alles wird selbst gemalt – nichts wird heruntergeladen.
 */
public final class BlockScenes {
	private BlockScenes() {
	}

	public static final String[] NAMES = {"Minecraft Tag", "Nether", "The End", "Tiefe Höhle", "Mond-Nacht"};
	public static final int W = 320, H = 180;

	public static BufferedImage paint(int index) {
		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		switch (Math.floorMod(index, NAMES.length)) {
			case 1 -> nether(img);
			case 2 -> end(img);
			case 3 -> cave(img);
			case 4 -> night(img);
			default -> day(img);
		}
		return img;
	}

	// ------------------------------------------------------------- Helfer

	private static int rgb(int r, int g, int b) {
		return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	/** Minecraft-Rauschen: Grundfarbe plus pro Pixel eine kleine Abweichung (jeder 2x2-Block gleich). */
	private static int noisy(int r, int g, int b, int x, int y, int seed, int amp) {
		long h = (x / 2) * 73856093L ^ (y / 2) * 19349663L ^ seed * 83492791L;
		h ^= h >>> 13;
		h *= 0x5bd1e995L;
		int n = (int) ((h >>> 8) % (amp * 2 + 1)) - amp;
		return rgb(r + n, g + n, b + n);
	}

	private static void set(BufferedImage img, int x, int y, int c) {
		if (x >= 0 && y >= 0 && x < W && y < H) img.setRGB(x, y, c);
	}

	private static void rect(BufferedImage img, int x, int y, int w, int h, int c) {
		for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) set(img, xx, yy, c);
	}

	/** Himmel als Farbbänder (wie Minecraft-Farbverläufe in Pixel-Stufen). */
	private static void sky(BufferedImage img, int top, int bottom, int bands) {
		for (int y = 0; y < H; y++) {
			float t = Math.min(1f, (y / (float) H) * 1.4f);
			t = Math.round(t * bands) / (float) bands;
			int r = (int) (((top >> 16) & 255) * (1 - t) + ((bottom >> 16) & 255) * t);
			int g = (int) (((top >> 8) & 255) * (1 - t) + ((bottom >> 8) & 255) * t);
			int b = (int) ((top & 255) * (1 - t) + (bottom & 255) * t);
			for (int x = 0; x < W; x++) img.setRGB(x, y, rgb(r, g, b));
		}
	}

	private static int height(int x, double base, double amp, double seed) {
		double v = Math.sin(x * 0.021 + seed) * 0.5 + Math.sin(x * 0.047 + seed * 2.3) * 0.3 + Math.sin(x * 0.11 + seed * 0.7) * 0.2;
		return (int) Math.round(base + v * amp) / 2 * 2; // in 2-Pixel-Stufen
	}

	private static void cloud(BufferedImage img, int x, int y, int w, int c) {
		rect(img, x + 2, y, w - 4, 2, c);
		rect(img, x, y + 2, w, 3, c);
		rect(img, x + 4, y + 5, w - 8, 2, c);
	}

	private static void stars(BufferedImage img, int count, long seed, int maxY, int c1, int c2) {
		Random r = new Random(seed);
		for (int i = 0; i < count; i++) {
			int x = r.nextInt(W), y = r.nextInt(maxY);
			set(img, x, y, r.nextInt(5) == 0 ? c2 : c1);
		}
	}

	// ------------------------------------------------------------- Tag

	private static void day(BufferedImage img) {
		sky(img, 0x5C9CF0, 0xBFE0FF, 7);
		rect(img, 252, 20, 14, 14, rgb(255, 245, 170)); // Sonne
		rect(img, 250, 22, 18, 10, rgb(255, 245, 170));
		rect(img, 254, 18, 10, 18, rgb(255, 245, 170));
		int white = rgb(255, 255, 255);
		cloud(img, 40, 24, 36, white);
		cloud(img, 150, 44, 44, white);
		cloud(img, 232, 70, 32, white);
		for (int x = 0; x < W; x++) {
			int top = height(x, 120, 22, 1.3);
			for (int y = top; y < H; y++) {
				int depth = y - top;
				int c;
				if (depth < 4) c = noisy(96, 170, 60, x, y, 1, 14);          // Gras
				else if (depth < 28) c = noisy(134, 96, 67, x, y, 2, 14);    // Erde
				else c = noisy(125, 125, 125, x, y, 3, 12);                  // Stein
				if (depth >= 28 && (x * 7 + y * 13) % 97 == 0) c = rgb(60, 220, 230); // Diamant-Pünktchen
				if (depth >= 14 && (x * 11 + y * 5) % 131 == 0) c = rgb(30, 30, 30);   // Kohle
				set(img, x, y, c);
			}
		}
		tree(img, 70, height(70, 120, 22, 1.3));
		tree(img, 210, height(210, 120, 22, 1.3));
		tree(img, 280, height(280, 120, 22, 1.3));
	}

	private static void tree(BufferedImage img, int x, int ground) {
		for (int y = ground - 26; y < ground; y++) rect(img, x, y, 4, 1, noisy(102, 81, 49, x, y, 5, 8));
		for (int dy = -18; dy <= 4; dy++) {
			int half = dy < -10 ? 8 : 12;
			for (int dx = -half; dx <= half + 4; dx++) {
				int px = x + dx, py = ground - 30 + dy;
				if (Math.abs(dx - 2) + Math.abs(dy + 7) / 2 > 17) continue;
				set(img, px, py, noisy(54, 130, 40, px, py, 6, 16));
			}
		}
	}

	// ------------------------------------------------------------- Nether

	private static void nether(BufferedImage img) {
		sky(img, 0x2A0608, 0x6A1410, 6);
		for (int x = 0; x < W; x++) {
			int ceil = height(x, 26, 14, 0.4);
			for (int y = 0; y < ceil; y++) set(img, x, y, noisy(110, 40, 40, x, y, 7, 16));
			int floor = height(x, 140, 12, 2.1);
			for (int y = floor; y < H; y++) {
				int c = noisy(112, 42, 42, x, y, 8, 16);
				if (y > floor + 22) c = lava(x, y);
				set(img, x, y, c);
			}
		}
		Random r = new Random(11);
		for (int i = 0; i < 26; i++) { // Glowstone-Klumpen
			int x = r.nextInt(W), y = r.nextInt(14) + 16;
			rect(img, x, y, 4, 3, noisy(255, 200, 90, x, y, 9, 18));
		}
		for (int i = 0; i < 40; i++) { // Funken
			set(img, r.nextInt(W), 40 + r.nextInt(110), rgb(255, 150 + r.nextInt(80), 40));
		}
	}

	private static int lava(int x, int y) {
		int wave = (int) (Math.sin(x * 0.2 + y * 0.35) * 18);
		return noisy(235 + wave / 3, 110 + wave, 30, x, y, 10, 20);
	}

	// ------------------------------------------------------------- End

	private static void end(BufferedImage img) {
		sky(img, 0x07030F, 0x1A0B2E, 6);
		stars(img, 140, 21, H * 3 / 4, rgb(200, 170, 255), rgb(255, 255, 255));
		for (int x = 0; x < W; x++) { // lila Nebel-Band
			for (int y = 50; y < 90; y++) {
				double v = Math.sin(x * 0.03 + y * 0.09) * 0.5 + 0.5;
				if (v > 0.55 && (x + y) % 2 == 0) set(img, x, y, rgb(90 + (int) (50 * v), 30, 130 + (int) (60 * v)));
			}
		}
		int[] px = {40, 92, 210, 262};
		int[] ph = {70, 96, 84, 108};
		for (int i = 0; i < px.length; i++) { // Obsidian-Säulen mit Kristall
			for (int y = H - ph[i]; y < H; y++) rect(img, px[i], y, 10, 1, noisy(24, 12, 40, px[i], y, 12, 6));
			rect(img, px[i] + 3, H - ph[i] - 5, 4, 5, rgb(255, 80, 190));
			rect(img, px[i] + 2, H - ph[i] - 7, 6, 2, rgb(255, 150, 220));
		}
		for (int x = 0; x < W; x++) { // End-Stein-Insel
			int top = height(x, 150, 6, 0.9);
			for (int y = top; y < H; y++) set(img, x, y, noisy(222, 222, 160, x, y, 13, 12));
		}
	}

	// ------------------------------------------------------------- Höhle

	private static void cave(BufferedImage img) {
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				double tunnel = Math.abs(y - (92 + Math.sin(x * 0.03) * 22 + Math.sin(x * 0.09) * 6)) / 40.0;
				int c;
				if (tunnel < 1.0) {
					c = noisy(24, 26, 34, x, y, 14, 5); // dunkler Gang
				} else {
					c = noisy(70, 72, 82, x, y, 15, 12); // Deepslate
					if (((x / 16) + (y / 16)) % 2 == 0) c = rgb(((c >> 16) & 255) - 6, ((c >> 8) & 255) - 6, (c & 255) - 6);
				}
				img.setRGB(x, y, c);
			}
		}
		Random r = new Random(33);
		int[] ores = {rgb(60, 230, 230), rgb(250, 210, 60), rgb(220, 40, 40), rgb(240, 240, 240)};
		for (int i = 0; i < 38; i++) { // Erze in der Wand
			int x = r.nextInt(W), y = r.nextInt(H);
			int c = ores[r.nextInt(ores.length)];
			rect(img, x, y, 3, 3, c);
			set(img, x + 3, y + 1, c);
		}
		for (int i = 0; i < 9; i++) { // Fackeln mit Schein
			int x = 20 + i * 36 + r.nextInt(12);
			int y = (int) (92 + Math.sin(x * 0.03) * 22) + 30;
			for (int dy = -14; dy <= 14; dy++) {
				for (int dx = -14; dx <= 14; dx++) {
					int d = Math.abs(dx) + Math.abs(dy);
					if (d < 15 && (dx + dy) % 2 == 0) {
						int base = img.getRGB(Math.max(0, Math.min(W - 1, x + dx)), Math.max(0, Math.min(H - 1, y + dy)));
						int add = (15 - d) * 5;
						set(img, x + dx, y + dy, rgb(((base >> 16) & 255) + add, ((base >> 8) & 255) + add / 2, (base & 255)));
					}
				}
			}
			rect(img, x, y, 2, 6, rgb(120, 80, 40));
			rect(img, x, y - 3, 2, 3, rgb(255, 200, 60));
		}
	}

	// ------------------------------------------------------------- Nacht

	private static void night(BufferedImage img) {
		sky(img, 0x050816, 0x1B2A55, 7);
		stars(img, 170, 5, H / 2, rgb(190, 200, 255), rgb(255, 255, 255));
		rect(img, 70, 22, 16, 16, rgb(235, 235, 220)); // Mond
		rect(img, 74, 26, 4, 4, rgb(200, 200, 190));
		rect(img, 80, 31, 4, 4, rgb(200, 200, 190));
		for (int x = 0; x < W; x++) {
			int top = height(x, 128, 18, 4.2);
			for (int y = top; y < H; y++) {
				int depth = y - top;
				int c = depth < 4 ? noisy(38, 92, 52, x, y, 16, 10) : depth < 26 ? noisy(70, 52, 40, x, y, 17, 10) : noisy(70, 72, 84, x, y, 18, 10);
				set(img, x, y, c);
			}
		}
		Random r = new Random(8);
		for (int i = 0; i < 30; i++) { // Glühwürmchen
			int x = r.nextInt(W), y = 100 + r.nextInt(60);
			set(img, x, y, rgb(255, 240, 120));
			set(img, x + 1, y, rgb(160, 150, 70));
		}
	}
}
