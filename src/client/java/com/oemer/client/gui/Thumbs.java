package com.oemer.client.gui;

import com.oemer.client.OemerClient;
import com.oemer.client.feature.Textures;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Vorschaubilder für Kacheln: werden im Hintergrund gemalt/geladen und dann als Textur hochgeladen. */
public final class Thumbs {
	private Thumbs() {
	}

	private static final Map<String, CompletableFuture<BufferedImage>> JOBS = new ConcurrentHashMap<>();

	/** key muss sich ändern, wenn sich das Bild ändert (z. B. Datei + Änderungszeit). null = noch nicht fertig. */
	public static Textures.Tex get(String key, Supplier<BufferedImage> painter) {
		String slot = "th_" + Integer.toHexString(key.hashCode());
		Textures.Tex t = Textures.cached(slot, key);
		if (t != null) return t;
		CompletableFuture<BufferedImage> f = JOBS.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
			try {
				return painter.get();
			} catch (Throwable e) {
				OemerClient.LOGGER.debug("Vorschau {} ging nicht", k, e);
				return null;
			}
		}));
		if (!f.isDone()) return null;
		BufferedImage img = f.getNow(null);
		if (img == null) return null;
		t = Textures.image(slot, key, img);
		JOBS.remove(key);
		return t;
	}
}
