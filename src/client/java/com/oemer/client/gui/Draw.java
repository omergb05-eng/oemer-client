package com.oemer.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Zeichen-Helfer für das Ömer-Design.
 * Alles wird aus einfachen Rechtecken gebaut (abgerundete Ecken, Schatten, Verläufe),
 * damit es mit jeder Minecraft-Version gleich clean aussieht.
 */
public final class Draw {
	private Draw() {
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Wie weit jede Zeile bei einer Rundung mit Radius r eingerückt ist. */
	private static int inset(int r, int row) {
		double dy = r - row - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	/** Gefülltes Rechteck mit runden Ecken. Überlappt sich nirgends, darum klappt es auch mit Transparenz. */
	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) return;
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		if (r == 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			g.fill(x + in, y + i, x + w - in, y + i + 1, color);
			g.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
		}
	}

	/** 1-Pixel-Rahmen mit runden Ecken. */
	public static void roundBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if (w <= 1 || h <= 1) return;
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		if (r == 0) {
			g.fill(x, y, x + w, y + 1, color);
			g.fill(x, y + h - 1, x + w, y + h, color);
			g.fill(x, y + 1, x + 1, y + h - 1, color);
			g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
			return;
		}
		// gerade Kanten
		g.fill(x + r, y, x + w - r, y + 1, color);
		g.fill(x + r, y + h - 1, x + w - r, y + h, color);
		g.fill(x, y + r, x + 1, y + h - r, color);
		g.fill(x + w - 1, y + r, x + w, y + h - r, color);
		// Ecken: pro Zeile die Rand-Pixel zwischen dieser und der vorherigen Einrückung
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			int end = i == 0 ? r : Math.max(in + 1, inset(r, i - 1));
			g.fill(x + in, y + i, x + end, y + i + 1, color);
			g.fill(x + w - end, y + i, x + w - in, y + i + 1, color);
			g.fill(x + in, y + h - 1 - i, x + end, y + h - i, color);
			g.fill(x + w - end, y + h - 1 - i, x + w - in, y + h - i, color);
		}
	}

	/** Weicher Schatten unter einer Karte (mehrere sehr durchsichtige Schichten). */
	public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int strength) {
		for (int i = 4; i >= 1; i--) {
			int a = Math.max(0, Math.min(255, strength / (i + 1)));
			round(g, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, (a << 24));
		}
	}

	/** Glas-Karte: Schatten, Fläche, Rand und ein heller Streifen oben (Licht-Kante). */
	public static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border) {
		shadow(g, x, y, w, h, r, 90);
		round(g, x, y, w, h, r, fill);
		roundBorder(g, x, y, w, h, r, border);
		// Licht-Kante oben
		g.fill(x + r + 1, y + 1, x + w - r - 1, y + 2, Theme.white(0x22));
	}

	/** Senkrechter Verlauf aus vielen dünnen Streifen (geht immer). */
	public static void vGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
		if (h <= 0) return;
		int steps = Math.min(h, 48);
		for (int i = 0; i < steps; i++) {
			int y0 = y + h * i / steps;
			int y1 = y + h * (i + 1) / steps;
			g.fill(x, y0, x + w, y1, mix(top, bottom, (float) i / Math.max(1, steps - 1)));
		}
	}

	/** Waagerechter Verlauf. */
	public static void hGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int left, int right) {
		if (w <= 0) return;
		int steps = Math.min(w, 48);
		for (int i = 0; i < steps; i++) {
			int x0 = x + w * i / steps;
			int x1 = x + w * (i + 1) / steps;
			g.fill(x0, y, x1, y + h, mix(left, right, (float) i / Math.max(1, steps - 1)));
		}
	}

	/** Mischt zwei ARGB-Farben. t=0 → a, t=1 → b. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
				| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	/** Gleiche Farbe, andere Transparenz (0-255). */
	public static int alpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
	}

	/** Multipliziert die Transparenz (für Ein-/Ausblenden). */
	public static int fade(int color, float f) {
		int a = (int) ((color >>> 24) * Math.max(0, Math.min(1, f)));
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.text(font(), s, x, y, color, false);
	}

	public static void textShadow(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.text(font(), s, x, y, color, true);
	}

	public static void centered(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
		g.text(font(), s, cx - font().width(s) / 2, y, color, false);
	}

	public static int width(String s) {
		return font().width(s);
	}

	/** Kürzt Text mit "…", wenn er nicht passt. */
	public static String fit(String s, int maxWidth) {
		if (font().width(s) <= maxWidth) return s;
		String dots = "…";
		int dw = font().width(dots);
		StringBuilder b = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (font().width(b.toString() + c) + dw > maxWidth) break;
			b.append(c);
		}
		return b + dots;
	}

	/** Weiche Annäherung für Animationen (unabhängig von den FPS). */
	public static float approach(float current, float target, float speedPerSecond, float deltaSeconds) {
		float k = 1f - (float) Math.exp(-speedPerSecond * deltaSeconds);
		return current + (target - current) * k;
	}
}
