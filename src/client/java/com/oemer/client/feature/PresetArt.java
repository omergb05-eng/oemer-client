package com.oemer.client.feature;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Alle eingebauten Bild-Vorlagen: Flügel, Heiligenscheine, Kronen, Begleiter, Totems.
 * Werden einmal in hoher Auflösung gemalt (kein Download nötig).
 */
public final class PresetArt {
	private PresetArt() {
	}

	// ============================================================ Helfer

	static Graphics2D smooth(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		return g;
	}

	static Color c(int rgb, int a) {
		return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, a)));
	}

	static void blob(Graphics2D g, float x, float y, float r, int rgb, int alpha) {
		if (r < 1) return;
		g.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), r, new float[]{0f, 0.5f, 1f},
				new Color[]{c(rgb, alpha), c(rgb, alpha / 3), c(rgb, 0)}));
		g.fill(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
	}

	/** Weicher Schein um eine Form (mehrere breite, durchsichtige Striche). */
	static void glowAround(Graphics2D g, Shape s, int rgb, int alpha, float width) {
		for (int i = 6; i >= 1; i--) {
			g.setStroke(new BasicStroke(width * i / 6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(c(rgb, alpha / 6));
			g.draw(s);
		}
	}

	/** Nebel-Textur mit Sternen (für Galaxy-Sachen). */
	static BufferedImage nebula(int w, int h, long seed, int[] colors, int base) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		Random r = new Random(seed);
		g.setColor(new Color(base));
		g.fillRect(0, 0, w, h);
		for (int i = 0; i < 90; i++) {
			blob(g, r.nextFloat() * w, r.nextFloat() * h, w * (0.08f + r.nextFloat() * 0.3f), colors[r.nextInt(colors.length)], 40 + r.nextInt(90));
		}
		for (int i = 0; i < w * h / 900; i++) {
			float s = r.nextFloat() < 0.08f ? 2.4f : 1.2f;
			g.setColor(c(0xFFFFFF, 120 + r.nextInt(135)));
			g.fill(new Ellipse2D.Float(r.nextFloat() * w, r.nextFloat() * h, s, s));
		}
		for (int i = 0; i < 10; i++) {
			float x = r.nextFloat() * w, y = r.nextFloat() * h, len = 4 + r.nextFloat() * 8;
			g.setColor(c(0xFFFFFF, 200));
			g.fill(new Rectangle2D.Float(x - len, y - 0.6f, len * 2, 1.2f));
			g.fill(new Rectangle2D.Float(x - 0.6f, y - len, 1.2f, len * 2));
		}
		g.dispose();
		return img;
	}

	/** Spiegelt links/rechts. */
	public static BufferedImage mirror(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, src.getWidth(), 0, -src.getWidth(), src.getHeight(), null);
		g.dispose();
		return out;
	}

	// ============================================================ Flügel

	/** Ein Feder-Tropfen von (x,y) in Richtung angle, Länge len, Breite wid. */
	static Shape feather(double x, double y, double angle, double len, double wid) {
		Path2D p = new Path2D.Double();
		p.moveTo(0, 0);
		p.curveTo(len * 0.25, -wid, len * 0.8, -wid * 0.9, len, 0);
		p.curveTo(len * 0.8, wid * 0.7, len * 0.25, wid * 0.8, 0, 0);
		p.closePath();
		AffineTransform t = new AffineTransform();
		t.translate(x, y);
		t.rotate(angle);
		return t.createTransformedShape(p);
	}

	/** Punkt auf einer quadratischen Kurve. */
	private static double[] quad(double t, double x0, double y0, double cx, double cy, double x1, double y1) {
		double u = 1 - t;
		return new double[]{u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1};
	}

	/**
	 * Federn eines Flügels (zeigt nach rechts, Ansatz links). Ein Flügelbogen geht vom Ansatz
	 * nach oben rechts, die Federn hängen nach unten/außen – oben großer Flügel, unten kleiner.
	 * Reihenfolge: hinten zuerst (lange Schwungfedern), vorne zuletzt (Deckfedern).
	 */
	static List<Shape> wingFeathers(int s) {
		List<Shape> f = new ArrayList<>();
		double rx = s * 0.05, ry = s * 0.50;            // Ansatz
		double cx = s * 0.30, cy = s * 0.02;            // Bogen
		double tx = s * 0.74, ty = s * 0.07;            // Spitze
		// lange Schwungfedern
		int n = 14;
		for (int i = 0; i < n; i++) {
			double t = i / (double) (n - 1);
			double[] p = quad(0.12 + t * 0.88, rx, ry, cx, cy, tx, ty);
			double ang = Math.toRadians(98 - t * 82);
			double len = s * (0.30 + 0.26 * Math.sin(t * Math.PI * 0.78));
			f.add(feather(p[0], p[1], ang, len, s * (0.045 + 0.012 * t)));
		}
		// mittlere Federn
		for (int i = 0; i < 11; i++) {
			double t = i / 10.0;
			double[] p = quad(0.08 + t * 0.85, rx, ry, cx, cy, tx, ty);
			double ang = Math.toRadians(92 - t * 76);
			f.add(feather(p[0], p[1], ang, s * (0.18 + 0.12 * t), s * 0.05));
		}
		// Deckfedern direkt am Bogen
		for (int i = 0; i < 10; i++) {
			double t = i / 9.0;
			double[] p = quad(0.05 + t * 0.9, rx, ry, cx, cy, tx, ty);
			double ang = Math.toRadians(70 - t * 50);
			f.add(feather(p[0] - s * 0.01, p[1] - s * 0.005, ang, s * (0.11 + 0.06 * t), s * 0.045));
		}
		// kleiner unterer Flügel
		double lx = s * 0.06, ly = s * 0.58, lcx = s * 0.22, lcy = s * 0.56, ltx = s * 0.46, lty = s * 0.62;
		for (int i = 0; i < 8; i++) {
			double t = i / 7.0;
			double[] p = quad(t, lx, ly, lcx, lcy, ltx, lty);
			double ang = Math.toRadians(100 - t * 62);
			f.add(feather(p[0], p[1], ang, s * (0.20 + 0.08 * Math.sin(t * Math.PI)), s * 0.04));
		}
		for (int i = 0; i < 6; i++) {
			double t = i / 5.0;
			double[] p = quad(t, lx, ly, lcx, lcy, ltx, lty);
			f.add(feather(p[0], p[1] - s * 0.005, Math.toRadians(80 - t * 50), s * 0.11, s * 0.04));
		}
		// alles etwas verkleinern, damit nichts über den Rand geht
		AffineTransform fit = wingFit(s);
		f.replaceAll(fit::createTransformedShape);
		return f;
	}

	static AffineTransform wingFit(int s) {
		AffineTransform t = new AffineTransform();
		t.translate(s * 0.02, s * 0.1);
		t.scale(0.8, 0.8);
		return t;
	}

	static Area union(List<Shape> shapes) {
		Area a = new Area();
		for (Shape s : shapes) a.add(new Area(s));
		return a;
	}

	public enum WingStyle { GALAXY, ANGEL, FIRE, ICE, SAKURA }

	/** Flügel mit Federn, zeigt nach rechts (Ansatz links). */
	public static BufferedImage featherWing(WingStyle style, int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		List<Shape> feathers = wingFeathers(s);
		Area all = union(feathers);

		int glow, edge, line;
		BufferedImage fill;
		switch (style) {
			case ANGEL -> {
				glow = 0xFFFFFF;
				edge = 0xB9C4D8;
				line = 0xFFFFFF;
				fill = gradientFill(s, 0xFFFFFF, 0xDCE3EF);
			}
			case FIRE -> {
				glow = 0xFF7A1A;
				edge = 0x9E1B00;
				line = 0xFFE27A;
				fill = gradientFill(s, 0xFFD43B, 0xFF3D00);
			}
			case ICE -> {
				glow = 0x8FE9FF;
				edge = 0x2C7FB8;
				line = 0xFFFFFF;
				fill = gradientFill(s, 0xE6FBFF, 0x5CC8F0);
			}
			case SAKURA -> {
				glow = 0xFFB3D9;
				edge = 0xC2577F;
				line = 0xFFFFFF;
				fill = gradientFill(s, 0xFFE3F0, 0xFF8FC0);
			}
			default -> {
				glow = 0xD58CFF;
				edge = 0x2A0F57;
				line = 0xF3DDFF;
				fill = nebula(s, s, 42, new int[]{0x8A3BFF, 0xC562FF, 0x5A2BD6, 0xFF6FD2, 0x3D57FF, 0xE9A6FF}, 0x24104F);
			}
		}

		// Schein
		glowAround(g, all, glow, 110, s * 0.05f);

		// Federn einzeln von hinten nach vorne, jede mit Rand und Mittel-Linie
		java.awt.Shape oldClip = g.getClip();
		for (int i = feathers.size() - 1; i >= 0; i--) {
			Shape f = feathers.get(i);
			g.setClip(f);
			g.drawImage(fill, 0, 0, null);
			// leichtes Licht oben
			g.setPaint(new GradientPaint(0, 0, c(0xFFFFFF, 40), 0, s, c(0x000000, 50)));
			g.fill(f);
			g.setClip(oldClip);
			g.setStroke(new BasicStroke(s * 0.006f));
			g.setColor(c(edge, 200));
			g.draw(f);
		}
		// Glanz-Kante oben am Flügelbogen
		Path2D arm = new Path2D.Double();
		arm.moveTo(s * 0.05, s * 0.50);
		arm.quadTo(s * 0.30, s * 0.02, s * 0.74, s * 0.07);
		g.setStroke(new BasicStroke(s * 0.012f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(c(line, 150));
		g.draw(wingFit(s).createTransformedShape(arm));
		g.dispose();
		return img;
	}

	private static BufferedImage gradientFill(int s, int top, int bottom) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		g.setPaint(new GradientPaint(0, 0, new Color(top), s, s, new Color(bottom)));
		g.fillRect(0, 0, s, s);
		g.dispose();
		return img;
	}

	/** Fledermaus-/Dämonenflügel, zeigt nach rechts. */
	public static BufferedImage demonWing(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		double rx = s * 0.06, ry = s * 0.45;
		double[][] tips = {{0.62, 0.06}, {0.92, 0.22}, {0.97, 0.52}, {0.82, 0.78}, {0.55, 0.9}};
		GeneralPath m = new GeneralPath();
		m.moveTo(rx, ry);
		m.lineTo(s * 0.35, s * 0.12);
		m.lineTo(s * tips[0][0], s * tips[0][1]);
		for (int i = 1; i < tips.length; i++) {
			double px = s * tips[i - 1][0], py = s * tips[i - 1][1];
			double qx = s * tips[i][0], qy = s * tips[i][1];
			// nach innen gebogene Haut zwischen den Fingern
			m.quadTo((px + qx) / 2 - (qx - rx) * 0.12, (py + qy) / 2 - (qy - ry) * 0.12, qx, qy);
		}
		m.quadTo(s * 0.3, s * 0.72, rx, ry + s * 0.05);
		m.closePath();
		glowAround(g, m, 0xFF2A2A, 120, s * 0.04f);
		g.setPaint(new RadialGradientPaint(new Point2D.Double(rx, ry), s * 0.95f, new float[]{0f, 0.6f, 1f},
				new Color[]{new Color(0x3A0610), new Color(0x7A0E1C), new Color(0xB3182A)}));
		g.fill(m);
		// Knochen
		g.setStroke(new BasicStroke(s * 0.018f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0x1A0206));
		g.draw(new java.awt.geom.Line2D.Double(rx, ry, s * 0.35, s * 0.12));
		g.draw(new java.awt.geom.Line2D.Double(s * 0.35, s * 0.12, s * tips[0][0], s * tips[0][1]));
		for (int i = 1; i < tips.length; i++) {
			g.draw(new java.awt.geom.Line2D.Double(s * 0.35, s * 0.12, s * tips[i][0], s * tips[i][1]));
		}
		g.setStroke(new BasicStroke(s * 0.006f));
		g.setColor(c(0xFF6A6A, 120));
		g.draw(m);
		g.dispose();
		return img;
	}

	/** Schmetterlingsflügel, zeigt nach rechts. */
	public static BufferedImage butterflyWing(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		Shape upper = AffineTransform.getRotateInstance(Math.toRadians(-14), s * 0.06, s * 0.5)
				.createTransformedShape(new Ellipse2D.Double(s * 0.04, s * 0.12, s * 0.84, s * 0.44));
		Shape lower = AffineTransform.getRotateInstance(Math.toRadians(22), s * 0.06, s * 0.52)
				.createTransformedShape(new Ellipse2D.Double(s * 0.04, s * 0.48, s * 0.52, s * 0.3));
		Area a = new Area(upper);
		a.add(new Area(lower));
		glowAround(g, a, 0x9AD8FF, 120, s * 0.05f);
		g.setPaint(new LinearGradientPaint(0, 0, s, s, new float[]{0f, 0.5f, 1f},
				new Color[]{new Color(0xFF8FD0), new Color(0xB98CFF), new Color(0x6FD3FF)}));
		g.fill(a);
		// Flecken
		Random r = new Random(5);
		g.setColor(c(0xFFFFFF, 150));
		for (int i = 0; i < 14; i++) {
			double x = s * (0.3 + r.nextDouble() * 0.55), y = s * (0.15 + r.nextDouble() * 0.6);
			double d = s * (0.02 + r.nextDouble() * 0.05);
			Ellipse2D e = new Ellipse2D.Double(x, y, d, d);
			if (a.contains(e.getCenterX(), e.getCenterY())) g.fill(e);
		}
		g.setStroke(new BasicStroke(s * 0.014f));
		g.setColor(new Color(0x2A1440));
		g.draw(a);
		// Adern
		g.setStroke(new BasicStroke(s * 0.006f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 0; i < 6; i++) {
			double ang = Math.toRadians(-60 + i * 22);
			Shape vein = new java.awt.geom.Line2D.Double(s * 0.06, s * 0.5, s * 0.06 + Math.cos(ang) * s * 0.75, s * 0.5 + Math.sin(ang) * s * 0.42);
			java.awt.Shape clip = g.getClip();
			g.setClip(a);
			g.draw(vein);
			g.setClip(clip);
		}
		g.dispose();
		return img;
	}

	// ======================================================= Kopf-Sachen

	/** Heiligenschein von oben gesehen (Ring). */
	public static BufferedImage halo(int s, int inner, int outer, boolean stars) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		float cx = s / 2f, r = s * 0.36f, w = s * 0.07f;
		Shape ring = new Ellipse2D.Float(cx - r, cx - r, r * 2, r * 2);
		glowAround(g, ring, outer, 160, s * 0.16f);
		g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setPaint(new GradientPaint(0, 0, new Color(inner), s, s, new Color(outer)));
		g.draw(ring);
		g.setStroke(new BasicStroke(w * 0.35f));
		g.setColor(c(0xFFFFFF, 210));
		g.draw(ring);
		if (stars) {
			Random rnd = new Random(3);
			for (int i = 0; i < 9; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				float x = cx + (float) Math.cos(a) * r, y = cx + (float) Math.sin(a) * r;
				blob(g, x, y, s * 0.04f, 0xFFFFFF, 230);
			}
		}
		g.dispose();
		return img;
	}

	/** Eine Seite der Krone (wird 4x um den Kopf gesetzt). */
	public static BufferedImage crownSide(int w, int gem) {
		int h = w / 2;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		GeneralPath p = new GeneralPath();
		p.moveTo(0, h);
		p.lineTo(0, h * 0.45);
		int spikes = 3;
		for (int i = 0; i < spikes; i++) {
			double x0 = w * (i / (double) spikes), x1 = w * ((i + 1) / (double) spikes);
			double mid = (x0 + x1) / 2;
			p.lineTo(x0 + (x1 - x0) * 0.18, h * 0.42);
			p.lineTo(mid, h * 0.04);
			p.lineTo(x1 - (x1 - x0) * 0.18, h * 0.42);
		}
		p.lineTo(w, h * 0.45);
		p.lineTo(w, h);
		p.closePath();
		g.setPaint(new GradientPaint(0, 0, new Color(0xFFF1A6), 0, h, new Color(0xD79A1E)));
		g.fill(p);
		g.setPaint(new GradientPaint(0, h * 0.7f, new Color(0xFFE27A), 0, h, new Color(0xB07812)));
		g.fill(new Rectangle2D.Double(0, h * 0.68, w, h * 0.32));
		g.setStroke(new BasicStroke(w * 0.012f));
		g.setColor(new Color(0x8A5A0A));
		g.draw(p);
		// Steine
		for (int i = 0; i < spikes; i++) {
			double mid = w * ((i + 0.5) / spikes);
			double d = w * 0.07;
			g.setPaint(new RadialGradientPaint(new Point2D.Double(mid - d * 0.2, h * 0.84 - d * 0.2), (float) d,
					new float[]{0f, 1f}, new Color[]{new Color(0xFFFFFF), new Color(gem)}));
			g.fill(new Ellipse2D.Double(mid - d / 2, h * 0.84 - d / 2, d, d));
			blob(g, (float) mid, (float) (h * 0.12), (float) (w * 0.035), 0xFFFFFF, 220);
		}
		g.dispose();
		return img;
	}

	/** Blumenkranz-Seite. */
	public static BufferedImage flowerSide(int w) {
		int h = w / 2;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		g.setStroke(new BasicStroke(h * 0.12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0x3E9B4F));
		Path2D vine = new Path2D.Double();
		vine.moveTo(0, h * 0.75);
		vine.curveTo(w * 0.3, h * 0.55, w * 0.6, h * 0.95, w, h * 0.72);
		g.draw(vine);
		Random r = new Random(9);
		int[] cols = {0xFF8FC7, 0xFFFFFF, 0xFFD23F, 0xC3A2FF};
		for (int i = 0; i < 5; i++) {
			float x = w * (0.1f + i * 0.2f), y = h * (0.55f + (i % 2) * 0.12f);
			float pr = h * 0.17f;
			int col = cols[r.nextInt(cols.length)];
			for (int k = 0; k < 5; k++) {
				double a = k * Math.PI * 2 / 5;
				g.setColor(new Color(col));
				g.fill(new Ellipse2D.Double(x + Math.cos(a) * pr * 0.7 - pr * 0.55, y + Math.sin(a) * pr * 0.7 - pr * 0.55, pr * 1.1, pr * 1.1));
			}
			g.setColor(new Color(0xFFC21A));
			g.fill(new Ellipse2D.Double(x - pr * 0.35, y - pr * 0.35, pr * 0.7, pr * 0.7));
		}
		g.dispose();
		return img;
	}

	/** Katzenohr (eins, wird zweimal gesetzt). */
	public static BufferedImage catEar(int s, int fur) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		GeneralPath p = new GeneralPath();
		p.moveTo(s * 0.08, s * 0.98);
		p.quadTo(s * 0.12, s * 0.3, s * 0.5, s * 0.04);
		p.quadTo(s * 0.88, s * 0.3, s * 0.92, s * 0.98);
		p.closePath();
		g.setColor(new Color(fur));
		g.fill(p);
		GeneralPath in = new GeneralPath();
		in.moveTo(s * 0.26, s * 0.96);
		in.quadTo(s * 0.3, s * 0.42, s * 0.5, s * 0.24);
		in.quadTo(s * 0.7, s * 0.42, s * 0.74, s * 0.96);
		in.closePath();
		g.setColor(new Color(0xFFB3D1));
		g.fill(in);
		g.setStroke(new BasicStroke(s * 0.03f));
		g.setColor(c(0x000000, 90));
		g.draw(p);
		g.dispose();
		return img;
	}

	/** Teufelshorn (eins). */
	public static BufferedImage horn(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		GeneralPath p = new GeneralPath();
		p.moveTo(s * 0.2, s);
		p.curveTo(s * 0.1, s * 0.6, s * 0.3, s * 0.2, s * 0.8, s * 0.04);
		p.curveTo(s * 0.5, s * 0.3, s * 0.55, s * 0.7, s * 0.7, s);
		p.closePath();
		g.setPaint(new GradientPaint(0, s, new Color(0x5A0A12), s, 0, new Color(0xFF3B4E)));
		g.fill(p);
		g.setStroke(new BasicStroke(s * 0.025f));
		g.setColor(new Color(0x2A0206));
		g.draw(p);
		g.dispose();
		return img;
	}

	// ========================================================== Begleiter

	public static BufferedImage planet(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		float cx = s / 2f, r = s * 0.24f;
		blob(g, cx, cx, s * 0.5f, 0xFF7AD0, 120);
		AffineTransform tf = AffineTransform.getRotateInstance(Math.toRadians(-18), cx, cx);
		Shape back = tf.createTransformedShape(new java.awt.geom.Arc2D.Float(cx - r * 2, cx - r * 0.5f, r * 4, r, 0, 180, java.awt.geom.Arc2D.OPEN));
		Shape front = tf.createTransformedShape(new java.awt.geom.Arc2D.Float(cx - r * 2, cx - r * 0.5f, r * 4, r, 180, 180, java.awt.geom.Arc2D.OPEN));
		g.setStroke(new BasicStroke(r * 0.16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(c(0xFFD0F0, 220));
		g.draw(back);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(cx - r * 0.4f, cx - r * 0.4f), r * 2f, new float[]{0f, 0.35f, 1f},
				new Color[]{new Color(0xFFE8F7), new Color(0xD58BFF), new Color(0x2B1366)}));
		g.fill(new Ellipse2D.Float(cx - r, cx - r, r * 2, r * 2));
		g.setColor(c(0xFFD0F0, 230));
		g.draw(front);
		g.dispose();
		return img;
	}

	public static BufferedImage heart(int s, int col) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		GeneralPath p = new GeneralPath();
		p.moveTo(s * 0.5, s * 0.88);
		p.curveTo(s * 0.05, s * 0.58, s * 0.05, s * 0.18, s * 0.3, s * 0.16);
		p.curveTo(s * 0.42, s * 0.15, s * 0.48, s * 0.24, s * 0.5, s * 0.3);
		p.curveTo(s * 0.52, s * 0.24, s * 0.58, s * 0.15, s * 0.7, s * 0.16);
		p.curveTo(s * 0.95, s * 0.18, s * 0.95, s * 0.58, s * 0.5, s * 0.88);
		p.closePath();
		glowAround(g, p, col, 120, s * 0.1f);
		g.setPaint(new RadialGradientPaint(new Point2D.Double(s * 0.38, s * 0.35), s * 0.6f, new float[]{0f, 1f},
				new Color[]{new Color(0xFFFFFF), new Color(col)}));
		g.fill(p);
		g.dispose();
		return img;
	}

	public static BufferedImage star(int s, int col) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		GeneralPath p = new GeneralPath();
		for (int i = 0; i < 10; i++) {
			double a = -Math.PI / 2 + i * Math.PI / 5;
			double r = (i % 2 == 0 ? 0.42 : 0.18) * s;
			double x = s / 2.0 + Math.cos(a) * r, y = s / 2.0 + Math.sin(a) * r;
			if (i == 0) p.moveTo(x, y);
			else p.lineTo(x, y);
		}
		p.closePath();
		glowAround(g, p, col, 140, s * 0.1f);
		g.setPaint(new RadialGradientPaint(new Point2D.Double(s * 0.45, s * 0.4), s * 0.5f, new float[]{0f, 1f},
				new Color[]{new Color(0xFFFFFF), new Color(col)}));
		g.fill(p);
		g.dispose();
		return img;
	}

	public static BufferedImage ghost(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		GeneralPath p = new GeneralPath();
		p.moveTo(s * 0.18, s * 0.9);
		p.lineTo(s * 0.18, s * 0.45);
		p.curveTo(s * 0.18, s * 0.08, s * 0.82, s * 0.08, s * 0.82, s * 0.45);
		p.lineTo(s * 0.82, s * 0.9);
		for (int i = 0; i < 4; i++) {
			double x0 = s * (0.82 - i * 0.16);
			p.quadTo(x0 - s * 0.04, s * 0.8, x0 - s * 0.08, s * 0.9);
			p.quadTo(x0 - s * 0.12, s * 0.8, x0 - s * 0.16, s * 0.9);
		}
		p.closePath();
		glowAround(g, p, 0xB9E6FF, 110, s * 0.08f);
		g.setColor(new Color(0xF4F8FF));
		g.fill(p);
		g.setColor(new Color(0x1A1A2E));
		g.fill(new Ellipse2D.Double(s * 0.34, s * 0.36, s * 0.1, s * 0.14));
		g.fill(new Ellipse2D.Double(s * 0.56, s * 0.36, s * 0.1, s * 0.14));
		g.setColor(new Color(0xFF8FC7));
		g.fill(new Ellipse2D.Double(s * 0.28, s * 0.52, s * 0.1, s * 0.06));
		g.fill(new Ellipse2D.Double(s * 0.62, s * 0.52, s * 0.1, s * 0.06));
		g.dispose();
		return img;
	}

	// ============================================================== Totems

	public static final String[] TOTEMS = {"Ömer Totem", "Galaxy-Planet", "Herz", "Stern", "Diamant", "Krone", "Geist"};

	public static BufferedImage totem(int index, int s) {
		return switch (Math.floorMod(index, TOTEMS.length)) {
			case 1 -> planet(s);
			case 2 -> heart(s, 0xFF3B6B);
			case 3 -> star(s, 0xFFC21A);
			case 4 -> diamond(s);
			case 5 -> crownTotem(s);
			case 6 -> ghost(s);
			default -> omerTotem(s);
		};
	}

	static BufferedImage omerTotem(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		java.awt.geom.RoundRectangle2D r = new java.awt.geom.RoundRectangle2D.Float(s * 0.14f, s * 0.14f, s * 0.72f, s * 0.72f, s * 0.22f, s * 0.22f);
		glowAround(g, r, 0xFFFFFF, 120, s * 0.08f);
		g.setPaint(new GradientPaint(0, 0, new Color(0xFFFFFF), s, s, new Color(0xD9DDE8)));
		g.fill(r);
		g.setColor(new Color(0x0B0B0F));
		g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, (int) (s * 0.5)));
		java.awt.FontMetrics fm = g.getFontMetrics();
		g.drawString("Ö", (s - fm.stringWidth("Ö")) / 2, s / 2 + fm.getAscent() / 3);
		g.dispose();
		return img;
	}

	static BufferedImage diamond(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		GeneralPath p = new GeneralPath();
		p.moveTo(s * 0.5, s * 0.9);
		p.lineTo(s * 0.12, s * 0.4);
		p.lineTo(s * 0.3, s * 0.16);
		p.lineTo(s * 0.7, s * 0.16);
		p.lineTo(s * 0.88, s * 0.4);
		p.closePath();
		glowAround(g, p, 0x6FE7FF, 140, s * 0.1f);
		g.setPaint(new GradientPaint(0, 0, new Color(0xE6FFFF), s, s, new Color(0x1FB5D6)));
		g.fill(p);
		g.setStroke(new BasicStroke(s * 0.012f));
		g.setColor(c(0xFFFFFF, 200));
		g.draw(new java.awt.geom.Line2D.Double(s * 0.12, s * 0.4, s * 0.88, s * 0.4));
		g.draw(new java.awt.geom.Line2D.Double(s * 0.3, s * 0.16, s * 0.5, s * 0.9));
		g.draw(new java.awt.geom.Line2D.Double(s * 0.7, s * 0.16, s * 0.5, s * 0.9));
		g.draw(new java.awt.geom.Line2D.Double(s * 0.5, s * 0.16, s * 0.38, s * 0.4));
		g.draw(new java.awt.geom.Line2D.Double(s * 0.5, s * 0.16, s * 0.62, s * 0.4));
		g.dispose();
		return img;
	}

	static BufferedImage crownTotem(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		BufferedImage side = crownSide(s, 0xE5484D);
		blob(g, s / 2f, s * 0.55f, s * 0.5f, 0xFFD23F, 90);
		g.drawImage(side, 0, s / 4, null);
		g.dispose();
		return img;
	}

	// ======================================================= Hintergründe

	public static final String[] BACKGROUNDS = {"Galaxy", "Nordlicht", "Synthwave", "Sakura", "Ozean", "Roter Nebel"};

	public static BufferedImage background(int index, int w, int h) {
		return switch (Math.floorMod(index, BACKGROUNDS.length)) {
			case 1 -> aurora(w, h);
			case 2 -> synthwave(w, h);
			case 3 -> sakura(w, h);
			case 4 -> ocean(w, h);
			case 5 -> redNebula(w, h);
			default -> GalaxyArt.paint(w, h, 7);
		};
	}

	static void stars(Graphics2D g, int w, int h, int count, long seed, float maxY) {
		Random r = new Random(seed);
		for (int i = 0; i < count; i++) {
			float s = r.nextFloat() < 0.05f ? 2.2f : 1.1f;
			g.setColor(c(0xFFFFFF, 60 + r.nextInt(190)));
			g.fill(new Ellipse2D.Float(r.nextFloat() * w, r.nextFloat() * h * maxY, s, s));
		}
	}

	static BufferedImage aurora(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		g.setPaint(new GradientPaint(0, 0, new Color(0x020814), 0, h, new Color(0x0A1F2E)));
		g.fillRect(0, 0, w, h);
		stars(g, w, h, 900, 11, 0.8f);
		Random r = new Random(4);
		int[] cols = {0x3DFFB0, 0x2FE0FF, 0x9B6BFF, 0x5BFF8A};
		for (int band = 0; band < 4; band++) {
			int col = cols[band];
			float baseY = h * (0.25f + band * 0.09f);
			for (int i = 0; i < 480; i++) {
				float x = i / 480f * w;
				float y = baseY + (float) Math.sin(i * 0.0233 + band) * h * 0.06f + (float) Math.sin(i * 0.007 + band * 2) * h * 0.08f;
				float len = h * (0.18f + 0.15f * (float) Math.sin(i * 0.0167 + band * 1.3) + r.nextFloat() * 0.015f);
				g.setPaint(new GradientPaint(x, y, c(col, 0), x, y + len * 0.3f, c(col, 26), true));
				g.fill(new Rectangle2D.Float(x, y, w / 480f + 3, len * 0.6f));
				g.setPaint(new GradientPaint(x, y + len * 0.2f, c(col, 22), x, y + len, c(col, 0)));
				g.fill(new Rectangle2D.Float(x, y + len * 0.2f, w / 480f + 3, len * 0.8f));
			}
		}
		// Berge
		g.setColor(new Color(0x03080C));
		GeneralPath m = new GeneralPath();
		m.moveTo(0, h);
		float x = 0;
		Random mr = new Random(8);
		m.lineTo(0, h * 0.8);
		while (x < w) {
			x += w * (0.04f + mr.nextFloat() * 0.08f);
			m.lineTo(x, h * (0.68f + mr.nextFloat() * 0.16f));
		}
		m.lineTo(w, h);
		m.closePath();
		g.fill(m);
		g.dispose();
		return img;
	}

	static BufferedImage synthwave(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		float horizon = h * 0.62f;
		g.setPaint(new LinearGradientPaint(0, 0, 0, horizon, new float[]{0f, 0.6f, 1f},
				new Color[]{new Color(0x0D0221), new Color(0x2A0A4A), new Color(0x8A1F7A)}));
		g.fillRect(0, 0, w, (int) horizon + 1);
		stars(g, w, h, 600, 21, 0.5f);
		// Sonne mit Streifen
		float sr = h * 0.24f, sx = w / 2f, sy = horizon - h * 0.02f;
		blob(g, sx, sy - sr * 0.3f, sr * 2.2f, 0xFF4FA3, 120);
		Shape sun = new Ellipse2D.Float(sx - sr, sy - sr * 1.6f, sr * 2, sr * 2);
		Area sunA = new Area(sun);
		for (int i = 0; i < 7; i++) {
			float yy = sy - sr * 0.6f + i * sr * 0.17f;
			sunA.subtract(new Area(new Rectangle2D.Float(sx - sr, yy, sr * 2, 2 + i * 1.6f)));
		}
		sunA.intersect(new Area(new Rectangle2D.Float(0, 0, w, horizon)));
		g.setPaint(new GradientPaint(0, sy - sr * 1.6f, new Color(0xFFE66D), 0, sy + sr * 0.4f, new Color(0xFF2E93)));
		g.fill(sunA);
		// Boden mit Gitter
		g.setPaint(new GradientPaint(0, horizon, new Color(0x1A0533), 0, h, new Color(0x050012)));
		g.fillRect(0, (int) horizon, w, h - (int) horizon);
		g.setStroke(new BasicStroke(1.6f));
		for (int i = 0; i < 14; i++) {
			float t = (float) Math.pow(i / 14f, 1.8);
			float y = horizon + t * (h - horizon);
			g.setColor(c(0xFF4FD8, (int) (80 + 150 * t)));
			g.draw(new java.awt.geom.Line2D.Float(0, y, w, y));
		}
		for (int i = -16; i <= 16; i++) {
			g.setColor(c(0xFF4FD8, 140));
			g.draw(new java.awt.geom.Line2D.Float(w / 2f + i * w * 0.012f, horizon, w / 2f + i * w * 0.09f, h));
		}
		g.setPaint(new GradientPaint(0, horizon - 4, c(0xFF8FE0, 220), 0, horizon + 18, c(0xFF8FE0, 0)));
		g.fillRect(0, (int) horizon - 4, w, 22);
		g.dispose();
		return img;
	}

	static BufferedImage sakura(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.6f, 1f},
				new Color[]{new Color(0xFFD6E8), new Color(0xFFB3CF), new Color(0xE98FB0)}));
		g.fillRect(0, 0, w, h);
		blob(g, w * 0.75f, h * 0.3f, h * 0.35f, 0xFFFFFF, 160);
		// Hügel
		for (int k = 0; k < 3; k++) {
			g.setColor(new Color(new int[]{0xE5A0C0, 0xC97BA0, 0xA65A82}[k]));
			GeneralPath p = new GeneralPath();
			p.moveTo(0, h);
			for (int i = 0; i <= 20; i++) {
				float x = i / 20f * w;
				p.lineTo(x, h * (0.7f + k * 0.08f) + (float) Math.sin(i * 0.8 + k * 2) * h * 0.04f);
			}
			p.lineTo(w, h);
			p.closePath();
			g.fill(p);
		}
		// Baum mit Blüten
		g.setColor(new Color(0x5A2E3A));
		g.setStroke(new BasicStroke(h * 0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Path2D tr = new Path2D.Float();
		tr.moveTo(w * 0.17, h * 0.9);
		tr.curveTo(w * 0.19, h * 0.7, w * 0.15, h * 0.55, w * 0.22, h * 0.4);
		g.draw(tr);
		g.setStroke(new BasicStroke(h * 0.025f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		double[][] branches = {{0.2, 0.5, 0.08, 0.36}, {0.21, 0.45, 0.33, 0.3}, {0.22, 0.4, 0.2, 0.22}, {0.19, 0.6, 0.29, 0.5}};
		for (double[] br : branches) {
			g.draw(new java.awt.geom.QuadCurve2D.Double(w * br[0], h * br[1], w * (br[0] + br[2]) / 2, h * (br[1] - 0.06), w * br[2], h * br[3]));
		}
		Random r = new Random(13);
		for (int i = 0; i < 260; i++) {
			float x = w * 0.21f + (float) r.nextGaussian() * w * 0.1f, y = h * 0.33f + (float) r.nextGaussian() * h * 0.1f;
			blob(g, x, y, h * (0.02f + r.nextFloat() * 0.05f), r.nextBoolean() ? 0xFF8FC0 : 0xFFE0EE, 200);
		}
		// fallende Blätter
		for (int i = 0; i < 120; i++) {
			g.setColor(c(0xFF9FC8, 120 + r.nextInt(120)));
			float s = 3 + r.nextFloat() * 5;
			g.fill(new Ellipse2D.Float(r.nextFloat() * w, r.nextFloat() * h, s * 1.6f, s));
		}
		g.dispose();
		return img;
	}

	static BufferedImage ocean(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = smooth(img);
		g.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 0.5f, 1f},
				new Color[]{new Color(0x2FB3E0), new Color(0x0A4C8A), new Color(0x021630)}));
		g.fillRect(0, 0, w, h);
		// Lichtstrahlen von oben
		Random r = new Random(17);
		g.setComposite(AlphaComposite.SrcOver);
		for (int i = 0; i < 14; i++) {
			float x = w * (0.1f + r.nextFloat() * 0.8f), wid = w * (0.02f + r.nextFloat() * 0.05f);
			GeneralPath p = new GeneralPath();
			p.moveTo(x - wid / 3, 0);
			p.lineTo(x + wid / 3, 0);
			p.lineTo(x + wid * 2 + w * 0.05f, h);
			p.lineTo(x + wid * 0.5f, h);
			p.closePath();
			g.setPaint(new GradientPaint(0, 0, c(0xD9F6FF, 70), 0, h * 0.9f, c(0xD9F6FF, 0)));
			g.fill(p);
		}
		// Blasen
		for (int i = 0; i < 90; i++) {
			float s = 3 + r.nextFloat() * 12;
			float x = r.nextFloat() * w, y = r.nextFloat() * h;
			g.setColor(c(0xFFFFFF, 60));
			g.setStroke(new BasicStroke(1.4f));
			g.draw(new Ellipse2D.Float(x, y, s, s));
		}
		// Seegras und Sandboden
		g.setColor(new Color(0x021A2E));
		GeneralPath ground = new GeneralPath();
		ground.moveTo(0, h);
		for (int i = 0; i <= 24; i++) ground.lineTo(i / 24f * w, h * 0.9f + (float) Math.sin(i * 0.9) * h * 0.02f);
		ground.lineTo(w, h);
		ground.closePath();
		g.fill(ground);
		g.setStroke(new BasicStroke(w * 0.006f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 0; i < 46; i++) {
			float x = r.nextFloat() * w, len = h * (0.12f + r.nextFloat() * 0.25f);
			Path2D weed = new Path2D.Float();
			weed.moveTo(x, h * 0.93f);
			weed.curveTo(x + w * 0.02f, h * 0.93f - len * 0.35f, x - w * 0.02f, h * 0.93f - len * 0.7f, x + w * 0.01f, h * 0.93f - len);
			g.setColor(new Color(r.nextBoolean() ? 0x0B4D3E : 0x07362F));
			g.draw(weed);
		}
		g.dispose();
		return img;
	}

	static BufferedImage redNebula(int w, int h) {
		BufferedImage img = nebula(w, h, 77, new int[]{0xFF3B3B, 0xFF8A3D, 0xC2185B, 0x7B1FA2, 0xFF5E7E}, 0x0A0306);
		Graphics2D g = smooth(img);
		blob(g, w * 0.3f, h * 0.6f, h * 0.18f, 0xFFD6A0, 200);
		g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), w * 0.75f,
				new float[]{0.55f, 1f}, new Color[]{c(0x000000, 0), c(0x000000, 150)}));
		g.fillRect(0, 0, w, h);
		g.dispose();
		return img;
	}
}
