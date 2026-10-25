package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Theme;
import com.oemer.client.online.Social;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Das kleine Ömer-Client-Symbol (Mond + Haus) über dem Namen – bei dir und deinen Freunden. */
public final class OmerBadge {
	private OmerBadge() {
	}

	/** Zeichen im Ömer-Font (font/logo.json). */
	public static final String GLYPH = "";

	private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "logo"));
	/** Wie oft das Symbol über einem Namen gemalt wurde (für Tests). */
	public static volatile int drawn;
	private static int cacheVersion = -1;
	private static Set<String> friendIds = Set.of();

	public static boolean enabled() {
		return OemerConfig.get().nameIcon;
	}

	/** Das Symbol als Text-Baustein (in der Akzentfarbe). */
	public static MutableComponent icon() {
		return Component.literal(GLYPH).withStyle(Style.EMPTY.withFont(FONT).withColor(Theme.accent() & 0xFFFFFF));
	}

	/** Symbol + Leerzeichen vor einen Namen setzen. */
	public static Component prefix(Component name) {
		return Component.empty().append(icon()).append(Component.literal(" ")).append(name);
	}

	private static String norm(String s) {
		return s == null ? "" : s.replace("-", "").toLowerCase(Locale.ROOT);
	}

	private static void refresh() {
		int v = Social.version();
		if (v == cacheVersion) return;
		cacheVersion = v;
		Set<String> ids = new HashSet<>();
		try {
			for (Social.Friend f : Social.friends()) {
				String id = norm(f.mc);
				if (!id.isEmpty()) ids.add(id);
			}
		} catch (Throwable ignored) {
		}
		friendIds = ids;
	}

	/** Hat dieser Spieler den Ömer Client (du selbst oder ein Freund, der ihn nutzt)? */
	public static boolean isOmer(UUID id) {
		if (id == null || !enabled()) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getUser() != null && id.equals(mc.getUser().getProfileId())) return true;
		refresh();
		return friendIds.contains(norm(id.toString()));
	}

	/** Nur für Tests: diese Minecraft-UUID als Ömer-Spieler behandeln. */
	public static void forTest(String mcUuid) {
		Set<String> ids = new HashSet<>(friendIds);
		ids.add(norm(mcUuid));
		friendIds = ids;
		cacheVersion = Social.version();
	}
}
