package com.oemer.client.gui;

import com.oemer.client.OemerConfig;

/** Farben vom Ömer-Client-Design: clean, weiß, durchsichtig, mit einer Akzentfarbe. */
public final class Theme {
	private Theme() {
	}

	private static final int[] ACCENTS = {
			0xFFFFFFFF, // Weiß
			0xFF8FD3FF, // Eisblau
			0xFFFF9FD2, // Rosa
			0xFFC3A2FF, // Lila
			0xFF8FF0C4  // Mint
	};

	/** Wie stark das Weiß durchscheint – je nach Layout-Einstellung. */
	private static final int[] PANEL_ALPHA = {0x0E, 0x16, 0x22};

	public static final int BACKGROUND = 0xFF060608;
	public static final int BACKGROUND_2 = 0xFF0E0E13;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_SOFT = 0xFFD6D6DC;
	public static final int TEXT_DIM = 0xFF8C8C96;
	public static final int TEXT_FAINT = 0xFF55555E;
	public static final int GOOD = 0xFF7BE3A3;
	public static final int BAD = 0xFFFF7A7A;

	/** Galaxy-Design aktiv? */
	public static boolean galaxy() {
		return OemerConfig.get().designStyle == 1;
	}

	public static int accent() {
		int idx = Math.floorMod(OemerConfig.get().accent, ACCENTS.length);
		// Im Galaxy-Design wird "Weiß" zu einem hellen Pink-Lila
		if (galaxy() && idx == 0) return 0xFFF0A8FF;
		return ACCENTS[idx];
	}

	/** Zweite Farbe für Verläufe (Galaxy: Blau-Lila). */
	public static int accent2() {
		return galaxy() ? 0xFF8F7BFF : accent();
	}

	/** "Weiß" mit Transparenz – im Galaxy-Design ganz leicht lila getönt. */
	public static int white(int alpha) {
		int rgb = galaxy() ? 0xF1E6FF : 0xFFFFFF;
		return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
	}

	/** Akzentfarbe mit gewünschter Transparenz (0-255). */
	public static int accent(int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (accent() & 0xFFFFFF);
	}

	private static int base() {
		return PANEL_ALPHA[Math.floorMod(OemerConfig.get().menuOpacity, PANEL_ALPHA.length)];
	}

	/** Große Glas-Fläche. */
	public static int panel() {
		return white(base());
	}

	/** Karten/Buttons: hover = 0..1 (für weiche Übergänge). */
	public static int element(float hover) {
		return white(Math.round(base() + 0x0A + 0x1E * hover));
	}

	public static int element(boolean hovered) {
		return element(hovered ? 1f : 0f);
	}

	public static int border(float hover) {
		return white(Math.round(0x24 + 0x5C * hover));
	}

	public static int border(boolean hovered) {
		return border(hovered ? 1f : 0f);
	}
}
