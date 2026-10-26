package com.oemer.client.online;

import com.oemer.client.gui.Draw;
import com.oemer.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Kleine Benachrichtigungen oben rechts im Spiel (neue Nachricht, Freundesanfrage, Mithören). */
public final class Notify {
	private Notify() {
	}

	private static final long SHOW_MS = 5000, FADE_MS = 400;
	private static final int MAX = 3;

	private record Entry(String text, long at) {
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	/** Wie viele Benachrichtigungen es insgesamt gab (für Tests). */
	public static volatile int shown;

	/** Von jedem Thread aufrufbar. */
	public static void push(String text) {
		Minecraft.getInstance().execute(() -> {
			ENTRIES.add(new Entry(text, System.currentTimeMillis()));
			while (ENTRIES.size() > MAX) ENTRIES.remove(0);
			shown++;
		});
	}

	/** Wie viele gerade zu sehen sind (für Tests). */
	public static int visible() {
		return ENTRIES.size();
	}

	public static void render(GuiGraphicsExtractor g, int screenW) {
		if (ENTRIES.isEmpty()) return;
		long now = System.currentTimeMillis();
		ENTRIES.removeIf(e -> now - e.at() > SHOW_MS);
		int y = 6;
		for (Entry e : ENTRIES) {
			long age = now - e.at();
			float f = Math.min(1f, Math.min(age / (float) FADE_MS, (SHOW_MS - age) / (float) FADE_MS));
			String text = Draw.fit(e.text(), 190);
			int w = Draw.width(text) + 24, h = 22;
			int x = screenW - w - 6;
			Draw.glass(g, x, y, w, h, 4, Draw.fade(0xE0101016, f), Draw.fade(Theme.accent(0xAA), f));
			g.fill(x + 6, y + 5, x + 8, y + h - 5, Draw.fade(Theme.accent(), f));
			Draw.text(g, text, x + 14, y + 7, Draw.fade(Theme.TEXT, f));
			y += h + 4;
		}
	}
}
