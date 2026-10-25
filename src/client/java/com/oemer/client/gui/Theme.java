package com.oemer.client.gui;

import com.oemer.client.OemerConfig;

/** Farben vom Ömer-Client-Design: clean, weiß, durchsichtig. */
public final class Theme {
	private Theme() {
	}

	private static final int[] ACCENTS = {
			0xFFFFFFFF, // Weiß
			0xFF9AD8FF, // Eisblau
			0xFFFFB3D9, // Rosa
			0xFFC9A7FF, // Lila
			0xFF9CF2C9  // Mint
	};

	/** Wie stark das Weiß durchscheint – je nach Layout-Einstellung. */
	private static final int[] PANEL_ALPHA = {0x10, 0x1C, 0x2C};

	public static final int BACKGROUND = 0xFF000000;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFF9A9A9A;
	public static final int TEXT_FAINT = 0xFF5E5E5E;

	public static int accent() {
		return ACCENTS[Math.floorMod(OemerConfig.get().accent, ACCENTS.length)];
	}

	/** Weiß mit gewünschter Transparenz (0-255). */
	public static int white(int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | 0xFFFFFF;
	}

	/** Akzentfarbe mit gewünschter Transparenz (0-255). */
	public static int accent(int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (accent() & 0xFFFFFF);
	}

	public static int panel() {
		return white(PANEL_ALPHA[Math.floorMod(OemerConfig.get().menuOpacity, PANEL_ALPHA.length)]);
	}

	public static int element(boolean hovered) {
		int base = PANEL_ALPHA[Math.floorMod(OemerConfig.get().menuOpacity, PANEL_ALPHA.length)];
		return white(hovered ? base + 0x24 : base + 0x0C);
	}

	public static int border(boolean hovered) {
		return white(hovered ? 0x90 : 0x38);
	}
}
