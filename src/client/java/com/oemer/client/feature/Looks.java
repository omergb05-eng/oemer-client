package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Look-Modus: Hintergrundbild für Menü und Startscreen.
 * Fertige Vorlagen ("preset:0", "preset:1" …) oder eigene Bilder aus looks/.
 */
public final class Looks {
	private Looks() {
	}

	public static final String PRESET = "preset:";
	public static final String WEB = "web:";
	private static final Map<Integer, CompletableFuture<BufferedImage>> PAINTING = new ConcurrentHashMap<>();
	private static final Map<Integer, CompletableFuture<Boolean>> DOWNLOADS = new ConcurrentHashMap<>();

	/** Foto-Vorlagen (Unsplash-Lizenz: frei nutzbar). Werden beim ersten Anzeigen heruntergeladen. */
	public record Photo(String name, String author, String id) {
		public String url() {
			return "https://images.unsplash.com/" + id + "?ixlib=rb-4.1.0&q=82&fm=jpg&crop=entropy&cs=srgb&w=1920&fit=max";
		}
	}

	public static final Photo[] PHOTOS = {
			new Photo("Minecraft-Figuren", "Addy Spartacus", "photo-1759663176932-04c90aeec2b3"),
			new Photo("Erz-Blöcke", "Connor Gan", "photo-1697479670670-d2a299df749c"),
			new Photo("Burg im Wald", "Johny Goerend", "photo-1594845222818-9097c52dabb5"),
			new Photo("Rosa Berge", "simon", "photo-1500964757637-c85e8a162699"),
			new Photo("Schneeberg", "Isaac Maffeis", "photo-1670974636823-1341d802d5b4"),
			new Photo("Milchstraße", "Cosmic Timetraveler", "photo-1548625361-58a9b86aa83b"),
			new Photo("Sternennacht", "Greg Rakozy", "photo-1444703686981-a3abbc4d4fe3"),
			new Photo("Lila Wolken", "Ernest Brillo", "photo-1534271057238-c2c170a76672"),
			new Photo("Blaue Stunde", "Gena Okami", "photo-1549366970-4efc91d3af9f"),
			new Photo("Block-Würfel", "Shubham Dhage", "photo-1639762681057-408e52192e55"),
	};

	private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
			.followRedirects(java.net.http.HttpClient.Redirect.NORMAL).connectTimeout(java.time.Duration.ofSeconds(10)).build();

	public static Path photoFolder() {
		return folder().resolve("vorlagen");
	}

	public static Path photoFile(int i) {
		return photoFolder().resolve("vorlage_" + i + ".jpg");
	}

	/** Lädt die Foto-Vorlage einmal herunter (im Hintergrund). true = liegt bereit. */
	public static boolean photoReady(int i) {
		if (java.nio.file.Files.isRegularFile(photoFile(i))) return true;
		CompletableFuture<Boolean> f = DOWNLOADS.computeIfAbsent(i, k -> CompletableFuture.supplyAsync(() -> {
			try {
				java.nio.file.Files.createDirectories(photoFolder());
				Path tmp = photoFolder().resolve("vorlage_" + k + ".part");
				var r = HTTP.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(PHOTOS[k].url()))
						.timeout(java.time.Duration.ofSeconds(30)).header("User-Agent", "OemerClient").build(),
						java.net.http.HttpResponse.BodyHandlers.ofFile(tmp));
				if (r.statusCode() != 200) {
					java.nio.file.Files.deleteIfExists(tmp);
					return false;
				}
				java.nio.file.Files.move(tmp, photoFile(k), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				return true;
			} catch (Exception e) {
				com.oemer.client.OemerClient.LOGGER.warn("Vorlage {} konnte nicht geladen werden: {}", k, e.toString());
				return false;
			}
		}));
		if (f.isDone() && !f.getNow(false)) DOWNLOADS.remove(i); // beim nächsten Mal nochmal versuchen
		return false;
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("looks");
	}

	public static List<String> files() {
		return CapeManager.imageFiles(folder());
	}

	/** Alle Möglichkeiten: Foto-Vorlagen, deine Bilder, ältere Designs. */
	public static List<String> choices() {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < PHOTOS.length; i++) out.add(WEB + i);
		out.addAll(files());
		for (int i = 0; i < PresetArt.BACKGROUNDS.length; i++) out.add(PRESET + i);
		return out;
	}

	private static int index(String choice, String prefix, int max) {
		try {
			return Math.floorMod(Integer.parseInt(choice.substring(prefix.length())), max);
		} catch (Exception e) {
			return 0;
		}
	}

	public static String displayName(String choice) {
		if (choice.startsWith(WEB)) return PHOTOS[index(choice, WEB, PHOTOS.length)].name();
		if (choice.startsWith(PRESET)) {
			try {
				return PresetArt.BACKGROUNDS[Integer.parseInt(choice.substring(PRESET.length())) % PresetArt.BACKGROUNDS.length];
			} catch (NumberFormatException e) {
				return "Vorlage";
			}
		}
		return CapeManager.shortName(choice);
	}

	/** Textur für eine Wahl (Vorlage wird im Hintergrund gemalt, bis dahin null). */
	public static Textures.Tex texture(String choice, String slot) {
		return texture(choice, slot, 1920);
	}

	public static Textures.Tex texture(String choice, String slot, int maxSize) {
		if (choice.startsWith(WEB)) {
			int i = index(choice, WEB, PHOTOS.length);
			return photoReady(i) ? Textures.file(slot, photoFile(i), maxSize) : null;
		}
		if (choice.startsWith(PRESET)) {
			int i;
			try {
				i = Math.floorMod(Integer.parseInt(choice.substring(PRESET.length())), PresetArt.BACKGROUNDS.length);
			} catch (NumberFormatException e) {
				i = 0;
			}
			String stamp = "bg" + i;
			Textures.Tex t = Textures.cached(slot, stamp);
			if (t != null) return t;
			final int idx = i;
			CompletableFuture<BufferedImage> f = PAINTING.computeIfAbsent(i,
					k -> CompletableFuture.supplyAsync(() -> PresetArt.background(idx, 1600, 900)));
			if (!f.isDone()) return null;
			BufferedImage img = f.getNow(null);
			return img == null ? null : Textures.image(slot, stamp, img);
		}
		return Textures.file(slot, folder().resolve(choice), maxSize);
	}

	/** Die Textur vom gewählten Bild (auch für die Vorschau, wenn der Modus aus ist). */
	public static Textures.Tex selected() {
		OemerConfig c = OemerConfig.get();
		if (c.lookImage == null || c.lookImage.isEmpty()) c.lookImage = WEB + "0";
		if (!c.lookImage.startsWith(PRESET) && !c.lookImage.startsWith(WEB) && !files().contains(c.lookImage)) c.lookImage = WEB + "0";
		return texture(c.lookImage, "look");
	}

	/** Ist der Modus an und gibt es ein Bild? */
	public static boolean active() {
		return OemerConfig.get().lookMode && selected() != null;
	}

	/** Bild über den ganzen Screen + Abdunkeln (damit Text lesbar bleibt). */
	public static boolean drawFullscreen(GuiGraphicsExtractor g, int w, int h) {
		if (!OemerConfig.get().lookMode) return false;
		Textures.Tex t = selected();
		if (t == null) return false;
		Textures.drawCover(g, t, 0, 0, w, h);
		int dim = OemerConfig.LOOK_DIM[Math.floorMod(OemerConfig.get().lookDim, OemerConfig.LOOK_DIM.length)];
		g.fill(0, 0, w, h, dim << 24);
		return true;
	}
}
