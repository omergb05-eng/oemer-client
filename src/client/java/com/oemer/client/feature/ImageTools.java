package com.oemer.client.feature;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Bild-Werkzeuge ohne Minecraft-Klassen:
 * Bilder zuschneiden, in Cape-Form bringen und eigene Cape-Designs malen.
 */
public final class ImageTools {
	private ImageTools() {
	}

	/** Auflösung der Capes: 16 = 1024x512 Pixel, damit Bilder scharf bleiben. */
	public static final int CAPE_SCALE = 16;

	public static BufferedImage read(Path file) throws IOException {
		BufferedImage img = ImageIO.read(file.toFile());
		if (img == null) {
			throw new IOException("Kein Bild: " + file.getFileName());
		}
		return toArgb(img);
	}

	/** Verkleinert sehr große Bilder (z. B. 4K-Fotos), damit die Grafikkarte sie gut packt. */
	public static BufferedImage limit(BufferedImage src, int maxSize) {
		int w = src.getWidth(), h = src.getHeight();
		if (w <= maxSize && h <= maxSize) {
			return src;
		}
		double s = (double) maxSize / Math.max(w, h);
		int nw = Math.max(1, (int) Math.round(w * s));
		int nh = Math.max(1, (int) Math.round(h * s));
		BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(out);
		g.drawImage(src, 0, 0, nw, nh, null);
		g.dispose();
		return out;
	}

	public static byte[] toPng(BufferedImage img) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	private static BufferedImage toArgb(BufferedImage src) {
		if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
			return src;
		}
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return out;
	}

	private static Graphics2D smooth(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return g;
	}

	/** Schneidet die Mitte quadratisch aus und skaliert auf size x size (für das Totem). */
	public static BufferedImage squareCrop(BufferedImage src, int size) {
		int s = Math.min(src.getWidth(), src.getHeight());
		int x = (src.getWidth() - s) / 2;
		int y = (src.getHeight() - s) / 2;
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(out);
		g.drawImage(src, 0, 0, size, size, x, y, x + s, y + s, null);
		g.dispose();
		return out;
	}

	/**
	 * Ganzes Bild in ein Quadrat (Rest bleibt durchsichtig). Kleine Pixel-Art wird scharf vergrößert,
	 * große Bilder weich verkleinert. Perfekt für PNGs mit Transparenz als Totem.
	 */
	public static BufferedImage squareContain(BufferedImage src, int size) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		double s = Math.min((double) size / src.getWidth(), (double) size / src.getHeight());
		int dw = Math.max(1, (int) Math.round(src.getWidth() * s));
		int dh = Math.max(1, (int) Math.round(src.getHeight() * s));
		int ox = (size - dw) / 2, oy = (size - dh) / 2;
		if (src.getWidth() <= size && src.getHeight() <= size) {
			for (int y = 0; y < dh; y++) {
				for (int x = 0; x < dw; x++) {
					out.setRGB(ox + x, oy + y, src.getRGB(Math.min(src.getWidth() - 1, x * src.getWidth() / dw),
							Math.min(src.getHeight() - 1, y * src.getHeight() / dh)));
				}
			}
		} else {
			Graphics2D g = smooth(out);
			g.drawImage(src, ox, oy, dw, dh, null);
			g.dispose();
		}
		return out;
	}

	/** Schneidet so aus, dass das Seitenverhältnis w:h passt (Mitte bleibt). */
	private static void drawCover(Graphics2D g, BufferedImage src, int dx, int dy, int dw, int dh) {
		double target = (double) dw / dh;
		double have = (double) src.getWidth() / src.getHeight();
		int sx = 0, sy = 0, sw = src.getWidth(), sh = src.getHeight();
		if (have > target) {
			sw = (int) Math.round(sh * target);
			sx = (src.getWidth() - sw) / 2;
		} else {
			sh = (int) Math.round(sw / target);
			sy = (src.getHeight() - sh) / 2;
		}
		g.drawImage(src, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, null);
	}

	/**
	 * Macht aus einem beliebigen Bild eine Cape-Textur.
	 * Ist es schon eine Cape-Datei (Seitenverhältnis 2:1, z. B. 64x32), wird sie direkt benutzt.
	 * Sonst kommt das Bild groß auf die Rückseite vom Cape und auf die Elytra.
	 */
	public static BufferedImage toCape(BufferedImage src) {
		if (src.getWidth() == src.getHeight() * 2 && src.getWidth() >= 64) {
			return src;
		}
		int s = CAPE_SCALE;
		BufferedImage out = new BufferedImage(64 * s, 32 * s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(out);

		Color edge = averageColor(src);
		g.setColor(edge);
		// Ränder vom Cape (oben, unten, Seiten)
		g.fillRect(0, 0, 22 * s, 17 * s);

		// Außenseite (sieht man von hinten) und Innenseite
		drawCover(g, src, 1 * s, 1 * s, 10 * s, 16 * s);
		drawCover(g, src, 12 * s, 1 * s, 10 * s, 16 * s);

		// Elytra-Bereich, damit sie mit Cape-Bild nicht unsichtbar ist
		g.fillRect(22 * s, 0, 24 * s, 22 * s);
		drawCover(g, src, 24 * s, 2 * s, 10 * s, 20 * s);
		g.dispose();
		return out;
	}

	private static Color averageColor(BufferedImage src) {
		long r = 0, gr = 0, b = 0, n = 0;
		int stepX = Math.max(1, src.getWidth() / 32);
		int stepY = Math.max(1, src.getHeight() / 32);
		for (int y = 0; y < src.getHeight(); y += stepY) {
			for (int x = 0; x < src.getWidth(); x += stepX) {
				int argb = src.getRGB(x, y);
				if ((argb >>> 24) < 32) {
					continue;
				}
				r += (argb >> 16) & 0xFF;
				gr += (argb >> 8) & 0xFF;
				b += argb & 0xFF;
				n++;
			}
		}
		if (n == 0) {
			return Color.WHITE;
		}
		return new Color((int) (r / n), (int) (gr / n), (int) (b / n));
	}

	// ----------------------------------------------------- eigene Designs

	/** Die eingebauten Ömer-Designs. */
	public static final String[] DESIGNS = {"Ömer Weiß", "Ömer Schwarz", "Ömer Glas", "Ömer Verlauf", "Ömer Galaxy"};

	public static BufferedImage design(int index, int accentRgb) {
		int w = 10 * CAPE_SCALE, h = 16 * CAPE_SCALE;
		BufferedImage face = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(face);
		Color accent = new Color(accentRgb & 0xFFFFFF);

		switch (Math.floorMod(index, DESIGNS.length)) {
			case 0 -> { // Weiß mit schwarzem Ö
				g.setColor(new Color(0xF4F4F4));
				g.fillRect(0, 0, w, h);
				drawLogo(g, w, h, new Color(0x111111), accent);
			}
			case 1 -> { // Schwarz mit weißem Ö
				g.setColor(new Color(0x0B0B0B));
				g.fillRect(0, 0, w, h);
				drawLogo(g, w, h, new Color(0xFFFFFF), accent);
			}
			case 2 -> { // Glas: hell, leicht durchsichtig wirkend
				g.setPaint(new GradientPaint(0, 0, new Color(0xFFFFFF), 0, h, new Color(0xD9E6EE)));
				g.fillRect(0, 0, w, h);
				g.setColor(new Color(255, 255, 255, 160));
				g.fillRect(w / 10, h / 14, w - w / 5, h - h / 7);
				drawLogo(g, w, h, new Color(0x202020), accent);
			}
			case 4 -> { // Galaxy: Ausschnitt mit dem Planeten
				BufferedImage gal = GalaxyArt.paint(480, 270, 7);
				int ch = 270, cw = Math.round(270f * w / h);
				int sx = Math.max(0, Math.min(480 - cw, Math.round(480 * GalaxyArt.PLANET_X) - cw / 2));
				g.drawImage(gal, 0, 0, w, h, sx, 0, sx + cw, ch, null);
				g.setColor(new Color(255, 255, 255, 40));
				g.fillRect(0, h * 3 / 4 - 2, w, Math.max(2, h / 40) + 4);
				drawLogo(g, w, h, new Color(0xFFFFFF), new Color(0xFFB3E6));
			}
			default -> { // Verlauf in der Akzentfarbe
				g.setPaint(new GradientPaint(0, 0, accent, 0, h, new Color(0x0B0B0B)));
				g.fillRect(0, 0, w, h);
				drawLogo(g, w, h, new Color(0xFFFFFF), new Color(0xFFFFFF));
			}
		}
		g.dispose();
		return toCape(face);
	}

	private static void drawLogo(Graphics2D g, int w, int h, Color text, Color line) {
		g.setColor(text);
		g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, w * 7 / 10));
		java.awt.FontMetrics fm = g.getFontMetrics();
		String s = "Ö";
		g.drawString(s, (w - fm.stringWidth(s)) / 2, h / 2 + fm.getAscent() / 3);
		g.setColor(line);
		g.fillRect(w / 4, h * 3 / 4, w / 2, Math.max(2, h / 40));
	}
}
