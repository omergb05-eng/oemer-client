package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import com.oemer.client.OemerKeys;
import net.minecraft.client.Minecraft;

/** Just Zoom: Taste halten = ranzoomen, Mausrad = stärker/schwächer. */
public final class Zoom {
	private Zoom() {
	}

	private static double current = 1.0;
	private static double scrollMul = 1.0;
	private static long last = System.nanoTime();

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return OemerConfig.get().zoomOn && OemerKeys.zoom != null && OemerKeys.zoom.isDown() && mc.screen == null && mc.player != null;
	}

	/** Pro Bild einmal aufrufen: rechnet die weiche Zoom-Animation weiter. */
	public static double update() {
		OemerConfig c = OemerConfig.get();
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - last) / 1e9);
		last = now;
		boolean on = active();
		if (!on) scrollMul = 1.0;
		double target = on ? OemerConfig.ZOOM_FACTORS[c.zoomLevel] * scrollMul : 1.0;
		if (c.zoomSmooth) {
			current += (target - current) * Math.min(1.0, dt * 14.0);
			if (Math.abs(current - target) < 0.002) current = target;
		} else {
			current = target;
		}
		return current;
	}

	public static double factor() {
		return current;
	}

	/** Mausrad beim Zoomen. true = Scrollen verbraucht. */
	public static boolean scroll(double amount) {
		if (!active() || !OemerConfig.get().zoomScroll || amount == 0) return false;
		scrollMul = Math.max(0.25, Math.min(8.0, scrollMul * (amount > 0 ? 1.25 : 0.8)));
		return true;
	}
}
