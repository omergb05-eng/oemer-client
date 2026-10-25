package com.oemer.client.gui;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.Looks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Der Hintergrund aller Ömer-Screens: schwarz, sanfte Lichter in der Akzentfarbe, funkelnde Partikel. */
public final class Backdrop {
	private Backdrop() {
	}

	private static final int COUNT = 70;
	private static final float[] PX = new float[COUNT];
	private static final float[] PY = new float[COUNT];
	private static final float[] SPEED = new float[COUNT];
	private static final float[] PHASE = new float[COUNT];
	private static final int[] SIZE = new int[COUNT];
	private static boolean init;

	private static void setup() {
		if (init) return;
		init = true;
		Random r = new Random(1337);
		for (int i = 0; i < COUNT; i++) {
			PX[i] = r.nextFloat();
			PY[i] = r.nextFloat();
			SPEED[i] = 0.004f + r.nextFloat() * 0.012f;
			PHASE[i] = r.nextFloat() * 6.28f;
			SIZE[i] = r.nextFloat() < 0.15f ? 2 : 1;
		}
	}

	public static void render(GuiGraphics g, int w, int h, boolean inWorld) {
		setup();
		OemerConfig c = OemerConfig.get();
		float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;

		boolean look = Looks.drawFullscreen(g, w, h);
		if (!look) {
			if (inWorld) {
				g.fill(0, 0, w, h, 0xC8050507);
			} else {
				Draw.vGradient(g, 0, 0, w, h, Theme.BACKGROUND, Theme.BACKGROUND_2);
			}
			// zwei weiche Lichtflecken, die langsam wandern
			glow(g, (int) (w * (0.22f + 0.06f * (float) Math.sin(t * 0.21))), (int) (h * (0.25f + 0.05f * (float) Math.cos(t * 0.17))),
					Math.max(w, h) / 3, Theme.accent(), 26);
			glow(g, (int) (w * (0.82f + 0.05f * (float) Math.cos(t * 0.13))), (int) (h * (0.85f + 0.04f * (float) Math.sin(t * 0.19))),
					Math.max(w, h) / 4, 0xFFFFFFFF, 14);
		}

		if (c.menuParticles) {
			for (int i = 0; i < COUNT; i++) {
				float y = (PY[i] - t * SPEED[i]) % 1f;
				if (y < 0) y += 1f;
				float x = PX[i] + 0.01f * (float) Math.sin(t * 0.5 + PHASE[i]);
				float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.7 + PHASE[i]);
				int a = Math.round(20 + 120 * tw * (SIZE[i] == 2 ? 1f : 0.7f));
				int px = Math.round(x * w), py = Math.round(y * h);
				int col = (i % 7 == 0) ? Theme.accent(a) : Theme.white(a);
				g.fill(px, py, px + SIZE[i], py + SIZE[i], col);
			}
		}

		// Vignette oben und unten
		Draw.vGradient(g, 0, 0, w, 40, 0x66000000, 0x00000000);
		Draw.vGradient(g, 0, h - 40, w, 40, 0x00000000, 0x66000000);
	}

	/** Runder, sehr weicher Lichtfleck aus vielen durchsichtigen Ringen. */
	private static void glow(GuiGraphics g, int cx, int cy, int radius, int color, int strength) {
		int steps = 7;
		for (int i = steps; i >= 1; i--) {
			int r = radius * i / steps;
			int a = Math.max(1, strength / steps);
			Draw.round(g, cx - r, cy - r, r * 2, r * 2, r, Draw.alpha(color, a));
		}
	}

	public static boolean inWorld() {
		return Minecraft.getInstance().level != null;
	}
}
