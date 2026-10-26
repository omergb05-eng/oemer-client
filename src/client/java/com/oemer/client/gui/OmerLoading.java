package com.oemer.client.gui;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.feature.Textures;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Der Ömer-Ladescreen: dunkel, weiche Lichter, Logo in der Mitte, Text + Balken unten.
 * Ersetzt das Mojang-Logo beim Start und den Ladebildschirm beim Betreten einer Welt.
 */
public final class OmerLoading {
	private OmerLoading() {
	}

	/** true = Minecraft soll gerade nichts zeichnen (das Mojang-Logo wird unterdrückt). */
	public static volatile boolean suppress;

	public static volatile int overlayFrames, levelFrames, progressFrames;

	private static boolean fontOk;
	private static Textures.Tex logo;
	private static boolean logoTried;
	private static float shown;
	private static final Set<String> SHOT_DONE = new HashSet<>();
	private static final java.util.Map<String, Integer> KIND_FRAMES = new java.util.HashMap<>();
	private static volatile String shotPending;

	private static final String[] TIPS = {
			"Rechts-Shift öffnet das Ömer-Menü",
			"F9 speichert die letzten Sekunden als Clip",
			"Im Menü bei Mods stellst du alles ein",
			"Schematics legst du im Ordner „schematics“ ab",
			"Mit Freunden kannst du chatten und Musik teilen",
	};

	public static boolean enabled() {
		try {
			return OemerConfig.get().customLoading;
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean fontReady() {
		if (fontOk) return true;
		try {
			Font f = Minecraft.getInstance().font;
			if (f != null && f.width("Ömer Client") > 0) fontOk = true;
		} catch (Throwable ignored) {
		}
		return fontOk;
	}

	private static Textures.Tex logo() {
		if (logo != null || logoTried) return logo;
		logoTried = true;
		try (InputStream in = OmerLoading.class.getResourceAsStream("/assets/oemerclient/textures/gui/logo.png")) {
			if (in != null) {
				BufferedImage img = ImageIO.read(in);
				logo = Textures.image("loading-logo", "v1", img);
			}
		} catch (Throwable t) {
			OemerClient.LOGGER.debug("Ladescreen-Logo: {}", t.toString());
		}
		return logo;
	}

	private static String version() {
		try {
			return FabricLoader.getInstance().getModContainer(OemerClient.MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		} catch (Throwable t) {
			return "";
		}
	}

	private static void glow(GuiGraphicsExtractor g, int cx, int cy, int radius, int color, int strength, float alpha) {
		int steps = 7;
		for (int i = steps; i >= 1; i--) {
			int r = radius * i / steps;
			int a = Math.max(1, Math.round(strength / (float) steps * alpha));
			Draw.round(g, cx - r, cy - r, r * 2, r * 2, r, Draw.alpha(color, a));
		}
	}

	/**
	 * Malt den ganzen Ladescreen.
	 * @param alpha    1 = voll sichtbar, 0 = weg (beim Ausblenden)
	 * @param progress 0..1, oder negativ = unbestimmt (Balken wandert)
	 * @param kind     "overlay", "level" oder "progress" (nur fürs Testen)
	 */
	public static void draw(GuiGraphicsExtractor g, float alpha, float progress, String status, String kind) {
		if (alpha <= 0.01f) return;
		int w = g.guiWidth(), h = g.guiHeight();
		float t = (System.currentTimeMillis() % 1_000_000L) / 1000f;
		alpha = Math.min(1f, alpha);

		// Hintergrund
		Draw.vGradient(g, 0, 0, w, h, Draw.fade(0xFF07070B, alpha), Draw.fade(0xFF12101C, alpha));
		glow(g, (int) (w * (0.25f + 0.05f * (float) Math.sin(t * 0.3))), (int) (h * 0.3f), Math.max(w, h) / 3, Theme.accent(), 30, alpha);
		glow(g, (int) (w * (0.8f + 0.04f * (float) Math.cos(t * 0.23))), (int) (h * 0.85f), Math.max(w, h) / 4, 0xFFFFFFFF, 12, alpha);
		// Funkeln
		for (int i = 0; i < 46; i++) {
			float px = ((i * 0.6180339f) % 1f);
			float py = ((i * 0.3819660f + 0.37f) % 1f);
			float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.4 + i * 1.7);
			int a = Math.round((14 + 90 * tw) * alpha);
			int x = Math.round(px * w), y = Math.round(py * h);
			g.fill(x, y, x + (i % 9 == 0 ? 2 : 1), y + (i % 9 == 0 ? 2 : 1), Draw.alpha(i % 6 == 0 ? Theme.accent() : 0xFFFFFFFF, a));
		}
		Draw.vGradient(g, 0, 0, w, 36, Draw.fade(0x66000000, alpha), 0x00000000);
		Draw.vGradient(g, 0, h - 60, w, 60, 0x00000000, Draw.fade(0x88000000, alpha));

		// Logo
		int ls = Math.max(48, Math.min(120, Math.round(Math.min(w, h) * 0.2f)));
		int lx = (w - ls) / 2;
		int ly = Math.round(h * 0.34f) - ls / 2;
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.6);
		glow(g, w / 2, ly + ls / 2, Math.round(ls * (0.95f + 0.12f * pulse)), Theme.accent(), 34, alpha);
		Textures.Tex tex = logo();
		if (tex != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, tex.id(), lx, ly, 0f, 0f, ls, ls, tex.width(), tex.height(), tex.width(), tex.height(),
					Draw.fade(0xFFFFFFFF, alpha));
		} else {
			Draw.round(g, lx, ly, ls, ls, ls / 5, Draw.fade(0xFFFFFFFF, alpha));
		}

		boolean text = fontReady();
		if (text) {
			try {
				g.pose().pushMatrix();
				g.pose().translate(w / 2f, ly + ls + 14);
				g.pose().scale(2f);
				Draw.centered(g, "§lÖmer Client", 0, 0, Draw.fade(Theme.TEXT, alpha));
				g.pose().popMatrix();
				String v = version();
				if (!v.isEmpty()) Draw.centered(g, "Version " + v, w / 2, ly + ls + 34, Draw.fade(Theme.TEXT_DIM, alpha));
			} catch (Throwable ignored) {
			}
		}

		// Balken
		int bw = Math.max(160, Math.min(360, Math.round(w * 0.42f)));
		int bh = 6;
		int bx = (w - bw) / 2;
		int by = Math.max(h - 62, ly + ls + 34 + 34);
		Draw.round(g, bx, by, bw, bh, 3, Draw.fade(0x30FFFFFF, alpha));
		if (progress >= 0) {
			shown += (Math.max(0f, Math.min(1f, progress)) - shown) * 0.12f;
			int fw = Math.max(bh, Math.round(bw * shown));
			Draw.round(g, bx, by, fw, bh, 3, Draw.fade(Theme.accent(0xFF), alpha));
			Draw.round(g, bx, by, fw, 2, 1, Draw.fade(0x55FFFFFF, alpha));
		} else {
			float seg = bw * 0.28f;
			float pos = ((t * 0.55f) % 1.4f) * (bw + seg) - seg;
			int x0 = Math.max(0, Math.round(pos)), x1 = Math.min(bw, Math.round(pos + seg));
			if (x1 - x0 > 2) Draw.round(g, bx + x0, by, x1 - x0, bh, 3, Draw.fade(Theme.accent(0xFF), alpha));
		}
		if (text) {
			try {
				Draw.centered(g, status, w / 2, by - 16, Draw.fade(Theme.TEXT_SOFT, alpha));
				if (progress >= 0) Draw.centered(g, Math.round(shown * 100) + " %", w / 2, by + 12, Draw.fade(Theme.TEXT_DIM, alpha));
				Draw.centered(g, TIPS[(int) ((System.currentTimeMillis() / 6000) % TIPS.length)], w / 2, h - 22, Draw.fade(Theme.TEXT_FAINT, alpha));
			} catch (Throwable ignored) {
			}
		}

		// Für den CI-Test: ein Bild vom Ladescreen speichern
		if (kind != null && System.getenv("OEMER_LOADSHOT") != null && !SHOT_DONE.contains(kind) && alpha >= 0.99f) {
			int n = KIND_FRAMES.merge(kind, 1, Integer::sum);
			if (n >= 5) shotPending = kind;
		}
	}

	/** Jeden Tick (nur für den CI-Test). */
	public static void tick(Minecraft mc) {
		String kind = shotPending;
		if (kind == null || SHOT_DONE.contains(kind)) return;
		shotPending = null;
		SHOT_DONE.add(kind);
		try {
			Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
				try {
					int w = image.getWidth(), h = image.getHeight();
					int[] px = image.getPixelsABGR();
					BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
					for (int i = 0; i < px.length; i++) {
						int p = px[i];
						out.setRGB(i % w, i / w, ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF));
					}
					Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
					Files.createDirectories(dir);
					String name = switch (kind) {
						case "overlay" -> "60-ladescreen-start.png";
						case "level" -> "61-ladescreen-welt.png";
						default -> "62-ladescreen-" + kind + ".png";
					};
					ImageIO.write(out, "png", dir.resolve(name).toFile());
				} catch (Throwable t) {
					OemerClient.LOGGER.warn("Ladescreen-Bild nicht gespeichert", t);
				} finally {
					image.close();
				}
			});
		} catch (Throwable t) {
			OemerClient.LOGGER.warn("Ladescreen-Bild: {}", t.toString());
		}
	}
}
