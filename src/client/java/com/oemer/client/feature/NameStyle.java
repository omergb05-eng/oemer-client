package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Dein eigener Anzeigename: eine Farbe, Verlauf oder Rainbow. Nur du siehst ihn (Tab-Liste, Chat, Namensschild bei dir). */
public final class NameStyle {
	private NameStyle() {
	}

	public static final String[] MODES = {"Eine Farbe", "Verlauf", "Rainbow"};
	public static final String[] COLOR_NAMES = {"Weiß", "Hellgrau", "Rot", "Orange", "Gelb", "Grün", "Türkis", "Hellblau", "Lila", "Pink", "Blau", "Gold"};
	public static final int[] COLORS = {0xFFFFFF, 0xB8B8C4, 0xFF4D5E, 0xFF9F43, 0xFFE45C, 0x57E39A, 0x3DDCD0, 0x5AA9FF, 0xB56CFF, 0xFF6FB5, 0x3B5BDB, 0xFFC83D};

	public static boolean active() {
		return OemerConfig.get().customName;
	}

	public static String realName() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getUser() == null ? "" : mc.getUser().getName();
	}

	/** Der Name, der angezeigt wird (leer = dein echter Name, nur eingefärbt). */
	public static String text() {
		String t = OemerConfig.get().customNameText.trim();
		return t.isEmpty() ? realName() : t;
	}

	public static MutableComponent display() {
		return styled(text());
	}

	public static MutableComponent styled(String text) {
		OemerConfig c = OemerConfig.get();
		MutableComponent out = Component.empty();
		int n = text.length();
		float hue0 = (System.currentTimeMillis() % 6000L) / 6000f;
		for (int i = 0; i < n; i++) {
			int rgb;
			switch (Math.floorMod(c.nameMode, 3)) {
				case 0 -> rgb = COLORS[Math.floorMod(c.nameColor1, COLORS.length)];
				case 1 -> rgb = lerp(COLORS[Math.floorMod(c.nameColor1, COLORS.length)], COLORS[Math.floorMod(c.nameColor2, COLORS.length)],
						n <= 1 ? 0f : i / (float) (n - 1));
				default -> rgb = java.awt.Color.HSBtoRGB(((hue0 - i * 0.045f) % 1f + 1f) % 1f, 0.7f, 1f) & 0xFFFFFF;
			}
			out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(Style.EMPTY.withColor(rgb).withBold(c.nameBold)));
		}
		return out;
	}

	private static int lerp(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** Ersetzt deinen echten Namen in einer Chat-Nachricht durch den bunten Namen. */
	public static Component replaceInChat(Component in) {
		try {
			String real = realName();
			if (real.isEmpty() || !OemerConfig.get().customName || !OemerConfig.get().nameInChat) return in;
			if (!in.getString().contains(real)) return in;
			return rebuild(in, real);
		} catch (Throwable t) {
			return in;
		}
	}

	private static MutableComponent rebuild(Component c, String real) {
		ComponentContents ct = c.getContents();
		MutableComponent out;
		if (ct instanceof PlainTextContents p && p.text().contains(real)) {
			out = Component.empty().withStyle(c.getStyle());
			String text = p.text();
			int at;
			while ((at = text.indexOf(real)) >= 0) {
				if (at > 0) out.append(Component.literal(text.substring(0, at)));
				out.append(display());
				text = text.substring(at + real.length());
			}
			if (!text.isEmpty()) out.append(Component.literal(text));
		} else if (ct instanceof TranslatableContents t) {
			Object[] args = t.getArgs();
			Object[] na = new Object[args.length];
			for (int i = 0; i < args.length; i++) na[i] = args[i] instanceof Component ac ? rebuild(ac, real) : args[i];
			out = MutableComponent.create(new TranslatableContents(t.getKey(), t.getFallback(), na)).withStyle(c.getStyle());
		} else {
			out = MutableComponent.create(ct).withStyle(c.getStyle());
		}
		for (Component sib : c.getSiblings()) out.append(rebuild(sib, real));
		return out;
	}
}
