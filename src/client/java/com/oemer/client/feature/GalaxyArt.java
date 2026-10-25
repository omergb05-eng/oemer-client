package com.oemer.client.feature;

import com.oemer.client.OemerClient;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Malt das Galaxy-Design: dunkelblauer Weltraum, lila/pinker Nebel, Sterne
 * und ein leuchtender Planet mit Ring und Strahlen. Wird einmal im Hintergrund erzeugt.
 */
public final class GalaxyArt {
	private GalaxyArt() {
	}

	public static final int W = 1600, H = 900;
	/** Planet-Mitte in Bild-Koordinaten (für das Leuchten im Menü). */
	public static final float PLANET_X = 0.73f, PLANET_Y = 0.36f, PLANET_R = 0.125f; // Radius relativ zur Höhe

	private static CompletableFuture<BufferedImage> future;

	/** Startet das Malen im Hintergrund (beim Spielstart aufrufen). */
	public static void prepare() {
		if (future == null) {
			future = CompletableFuture.supplyAsync(() -> {
				try {
					return paint(W, H, 7);
				} catch (Throwable t) {
					OemerClient.LOGGER.warn("Galaxy-Design konnte nicht gemalt werden", t);
					return null;
				}
			});
		}
	}

	/** Fertiges Bild oder null, solange es noch gemalt wird. */
	public static BufferedImage image() {
		prepare();
		return future.isDone() ? future.getNow(null) : null;
	}

	private static Graphics2D smooth(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		return g;
	}

	private static Color c(int rgb, int alpha) {
		return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, alpha)));
	}

	/** Weicher, runder Lichtfleck. */
	private static void blob(Graphics2D g, float x, float y, float r, int rgb, int alpha) {
		if (r < 1) return;
		RadialGradientPaint p = new RadialGradientPaint(new Point2D.Float(x, y), r,
				new float[]{0f, 0.45f, 1f}, new Color[]{c(rgb, alpha), c(rgb, alpha / 3), c(rgb, 0)});
		g.setPaint(p);
		g.fill(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
	}

	public static BufferedImage paint(int w, int h, long seed) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		Random rnd = new Random(seed);

		// ---- Grund: dunkelblau → tiefes Lila
		g.setPaint(new GradientPaint(0, 0, new Color(0x060A22), w, h, new Color(0x12082E)));
		g.fillRect(0, 0, w, h);

		// ---- Nebel: große Farbwolken
		blob(g, w * 0.22f, h * 0.30f, w * 0.48f, 0x6A35D8, 120);
		blob(g, w * 0.62f, h * 0.70f, w * 0.42f, 0xFF4FA3, 120);
		blob(g, w * 0.80f, h * 0.45f, w * 0.30f, 0xFF6FC0, 70);
		blob(g, w * 0.10f, h * 0.85f, w * 0.36f, 0x1F4BFF, 130);
		blob(g, w * 0.02f, h * 0.40f, w * 0.25f, 0x1A2E9E, 120);
		blob(g, w * 0.92f, h * 0.15f, w * 0.30f, 0xC23BFF, 85);
		blob(g, w * 0.45f, h * 0.45f, w * 0.25f, 0x8F5BFF, 60);
		// feine Wolken-Struktur
		for (int i = 0; i < 260; i++) {
			float x = (float) (rnd.nextGaussian() * 0.28 + 0.45) * w;
			float y = (float) (rnd.nextGaussian() * 0.22 + 0.5) * h;
			float r = 30 + rnd.nextFloat() * 150;
			int[] cols = {0x7A44FF, 0xFF5DB1, 0x3D6BFF, 0xB34BFF, 0xFF8FD0};
			blob(g, x, y, r, cols[rnd.nextInt(cols.length)], 10 + rnd.nextInt(22));
		}
		// dunkle Staubbänder für Tiefe
		for (int i = 0; i < 40; i++) {
			float x = rnd.nextFloat() * w, y = rnd.nextFloat() * h;
			blob(g, x, y, 60 + rnd.nextFloat() * 160, 0x03040F, 18 + rnd.nextInt(26));
		}

		// ---- Sterne
		for (int i = 0; i < 1400; i++) {
			float x = rnd.nextFloat() * w, y = rnd.nextFloat() * h;
			float s = rnd.nextFloat();
			int a = 60 + rnd.nextInt(195);
			int col = s > 0.92f ? 0xFFD6F2 : (s > 0.84f ? 0xBFD4FF : 0xFFFFFF);
			float size = s > 0.985f ? 2.4f : (s > 0.9f ? 1.6f : 1.0f);
			g.setColor(c(col, a));
			g.fill(new Ellipse2D.Float(x, y, size, size));
		}
		// helle Funkelsterne mit Kreuz-Schein
		for (int i = 0; i < 26; i++) {
			float x = rnd.nextFloat() * w, y = rnd.nextFloat() * h;
			float len = 6 + rnd.nextFloat() * 16;
			int col = rnd.nextBoolean() ? 0xFFFFFF : 0xFFC7EA;
			blob(g, x, y, len * 1.4f, col, 90);
			g.setPaint(new GradientPaint(x - len, y, c(col, 0), x, y, c(col, 220), true));
			g.fill(new java.awt.geom.Rectangle2D.Float(x - len, y - 0.6f, len * 2, 1.2f));
			g.setPaint(new GradientPaint(x, y - len, c(col, 0), x, y, c(col, 220), true));
			g.fill(new java.awt.geom.Rectangle2D.Float(x - 0.6f, y - len, 1.2f, len * 2));
		}

		// ---- Planet
		float px = w * PLANET_X, py = h * PLANET_Y, pr = h * PLANET_R;

		// äußeres Leuchten
		blob(g, px, py, pr * 3.4f, 0xFF6EC7, 70);
		blob(g, px, py, pr * 2.2f, 0xB77BFF, 110);

		// Strahlen rund um den Planeten
		for (int i = 0; i < 36; i++) {
			double a = (Math.PI * 2 * i / 36) + rnd.nextGaussian() * 0.06;
			float len = pr * (1.2f + rnd.nextFloat() * rnd.nextFloat() * 3.4f);
			float width = (float) (0.02 + rnd.nextFloat() * 0.06);
			Path2D ray = new Path2D.Float();
			ray.moveTo(px + Math.cos(a - width) * pr * 0.95, py + Math.sin(a - width) * pr * 0.95);
			ray.lineTo(px + Math.cos(a) * (pr + len), py + Math.sin(a) * (pr + len));
			ray.lineTo(px + Math.cos(a + width) * pr * 0.95, py + Math.sin(a + width) * pr * 0.95);
			ray.closePath();
			int col = rnd.nextInt(3) == 0 ? 0xFFB3E6 : (rnd.nextBoolean() ? 0xD9B8FF : 0xFFFFFF);
			g.setPaint(new RadialGradientPaint(new Point2D.Float(px, py), pr + len,
					new float[]{0f, (pr / (pr + len)), 1f},
					new Color[]{c(col, 0), c(col, 55 + rnd.nextInt(70)), c(col, 0)}));
			g.fill(ray);
		}

		// Ring – hinterer Teil (wird vom Planeten verdeckt)
		AffineTransform ringTf = AffineTransform.getRotateInstance(Math.toRadians(-17), px, py);
		Shape ringBack = ringTf.createTransformedShape(new Arc2D.Float(px - pr * 2.25f, py - pr * 0.55f,
				pr * 4.5f, pr * 1.1f, 0, 180, Arc2D.OPEN));
		drawRing(g, ringBack, pr);

		// Planetenkugel mit Licht von links oben
		Ellipse2D body = new Ellipse2D.Float(px - pr, py - pr, pr * 2, pr * 2);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(px - pr * 0.45f, py - pr * 0.5f), pr * 2.1f,
				new float[]{0f, 0.25f, 0.6f, 1f},
				new Color[]{new Color(0xFFE8F7), new Color(0xF29BE0), new Color(0x7A3BD6), new Color(0x140B38)},
				MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fill(body);
		// Wolkenbänder
		java.awt.Shape oldClip = g.getClip();
		g.setClip(body);
		for (int i = 0; i < 9; i++) {
			float by = py - pr + (i + 0.5f) * (pr * 2 / 9f) + (float) rnd.nextGaussian() * 3;
			g.setColor(c(i % 2 == 0 ? 0xFF9AD8 : 0x7C4DFF, 34));
			g.fill(new Ellipse2D.Float(px - pr * 1.3f, by - pr * 0.06f, pr * 2.6f, pr * (0.08f + rnd.nextFloat() * 0.07f)));
		}
		// Schatten rechts unten
		g.setPaint(new RadialGradientPaint(new Point2D.Float(px + pr * 0.9f, py + pr * 0.9f), pr * 1.7f,
				new float[]{0f, 1f}, new Color[]{c(0x05041A, 200), c(0x05041A, 0)}));
		g.fill(body);
		g.setClip(oldClip);
		// Atmosphären-Rand
		g.setStroke(new BasicStroke(pr * 0.06f));
		g.setPaint(new GradientPaint(px - pr, py - pr, c(0xFFD1F0, 200), px + pr, py + pr, c(0x8A5BFF, 40)));
		g.draw(body);

		// Ring – vorderer Teil
		Shape ringFront = ringTf.createTransformedShape(new Arc2D.Float(px - pr * 2.25f, py - pr * 0.55f,
				pr * 4.5f, pr * 1.1f, 180, 180, Arc2D.OPEN));
		drawRing(g, ringFront, pr);

		// ---- kleiner zweiter Mond
		float mx = w * 0.18f, my = h * 0.22f, mr = h * 0.03f;
		blob(g, mx, my, mr * 3, 0x9FB8FF, 60);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(mx - mr * 0.4f, my - mr * 0.4f), mr * 2,
				new float[]{0f, 1f}, new Color[]{new Color(0xE9F0FF), new Color(0x3A3F8F)}));
		g.fill(new Ellipse2D.Float(mx - mr, my - mr, mr * 2, mr * 2));

		// ---- leichte Vignette
		g.setComposite(AlphaComposite.SrcOver);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), w * 0.75f,
				new float[]{0.55f, 1f}, new Color[]{c(0x000000, 0), c(0x000000, 150)}));
		g.fillRect(0, 0, w, h);

		g.dispose();
		return img;
	}

	private static void drawRing(Graphics2D g, Shape ring, float pr) {
		g.setStroke(new BasicStroke(pr * 0.22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(c(0xC9A6FF, 70));
		g.draw(ring);
		g.setStroke(new BasicStroke(pr * 0.10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(c(0xFFD0F0, 150));
		g.draw(ring);
		g.setStroke(new BasicStroke(pr * 0.025f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(c(0xFFFFFF, 210));
		g.draw(ring);
	}
}
