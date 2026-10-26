package com.oemer.client.feature;

import java.awt.image.BufferedImage;
import java.util.Random;

/** Minecraft-artiger Menü-Hintergrund: Wand aus dunklen Steinblöcken (16x16 Pixel pro Block, mit Fugen und Rauschen). */
public final class BlockArt {
	private BlockArt() {
	}

	public static final int W = 320, H = 180;
	private static BufferedImage image;

	public static BufferedImage image() {
		if (image == null) image = paint();
		return image;
	}

	private static BufferedImage paint() {
		BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(2611);
		for (int by = 0; by * 16 < H; by++) {
			for (int bx = 0; bx * 16 < W; bx++) {
				int base = 30 + r.nextInt(14);
				for (int y = 0; y < 16 && by * 16 + y < H; y++) {
					for (int x = 0; x < 16 && bx * 16 + x < W; x++) {
						int n = base + r.nextInt(12);
						if (x == 0 || y == 0) n -= 9;          // dunkle Fuge oben/links
						else if (x == 15 || y == 15) n += 4;   // heller Rand unten/rechts
						n = Math.max(8, Math.min(90, n));
						int rgb = (n << 16) | ((n + 1) << 8) | (n + 6);
						out.setRGB(bx * 16 + x, by * 16 + y, 0xFF000000 | rgb);
					}
				}
			}
		}
		return out;
	}
}
