package com.oemer.client.gui;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.GalaxyArt;
import com.oemer.client.feature.Looks;
import com.oemer.client.feature.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

	public static void render(GuiGraphicsExtractor g, int w, int h, boolean inWorld) {
		setup();
		OemerConfig c = OemerConfig.get();
		float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;

		boolean look = Looks.drawFullscreen(g, w, h);
		if (!look && Theme.galaxy() && drawGalaxy(g, w, h, t, inWorld ? 0x60 : 0x00)) {
			look = true;
		}
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
				int col = (i % 7 == 0) ? Theme.accent(a) : (Theme.galaxy() && i % 5 == 0 ? Draw.alpha(0xFF9FC4FF, a) : Theme.white(a));
				g.fill(px, py, px + SIZE[i], py + SIZE[i], col);
			}
		}

		// Vignette oben und unten
		Draw.vGradient(g, 0, 0, w, 40, 0x66000000, 0x00000000);
		Draw.vGradient(g, 0, h - 40, w, 40, 0x00000000, 0x66000000);
	}

	/**
	 * Galaxy-Hintergrund: großes Bild, das ganz langsam schwebt, dazu ein pulsierendes Leuchten am Planeten.
	 * @return false, solange das Bild noch gemalt wird.
	 */
	public static boolean drawGalaxy(GuiGraphicsExtractor g, int w, int h, float t, int darken) {
		java.awt.image.BufferedImage img = GalaxyArt.image();
		if (img == null) return false;
		Textures.Tex tex = Textures.image("galaxy", "v1", img);
		if (tex == null) return false;
		// etwas größer zeichnen und sanft verschieben (Schwebe-Effekt)
		float zoom = 1.06f;
		int dw = Math.round(w * zoom), dh = Math.round(h * zoom);
		int ox = Math.round((dw - w) / 2f * (1 + 0.8f * (float) Math.sin(t * 0.07)));
		int oy = Math.round((dh - h) / 2f * (1 + 0.8f * (float) Math.cos(t * 0.05)));
		Textures.drawCover(g, tex, -ox, -oy, dw, dh);

		// Planet-Position auf dem Bildschirm berechnen (gleiche Rechnung wie drawCover)
		double scale = Math.max((double) dw / GalaxyArt.W, (double) dh / GalaxyArt.H);
		double visW = dw / scale, visH = dh / scale;
		double u0 = (GalaxyArt.W - visW) / 2, v0 = (GalaxyArt.H - visH) / 2;
		int px = (int) Math.round(-ox + (GalaxyArt.W * GalaxyArt.PLANET_X - u0) * scale);
		int py = (int) Math.round(-oy + (GalaxyArt.H * GalaxyArt.PLANET_Y - v0) * scale);
		int pr = (int) Math.round(GalaxyArt.H * GalaxyArt.PLANET_R * scale);
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.1);
		glow(g, px, py, Math.round(pr * (2.2f + 0.4f * pulse)), 0xFFFF7AD0, Math.round(10 + 14 * pulse));

		if (darken > 0) g.fill(0, 0, w, h, darken << 24);
		return true;
	}

	/** Runder, sehr weicher Lichtfleck aus vielen durchsichtigen Ringen. */
	private static void glow(GuiGraphicsExtractor g, int cx, int cy, int radius, int color, int strength) {
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
