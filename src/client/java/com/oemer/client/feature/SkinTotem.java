package com.oemer.client.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oemer.client.OemerClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Totem aus deinem eigenen Skin: holt den Skin von Mojang (oder nimmt Steve, wenn offline)
 * und baut daraus eine kleine Figur, so wie bei bekannten "Skin-Totem"-Paketen.
 */
public final class SkinTotem {
	private SkinTotem() {
	}

	private static volatile BufferedImage skin;
	private static CompletableFuture<Void> loading;

	private static Path cacheFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("cache").resolve("skin.png");
	}

	/** Startet das Laden (beim Spielstart). */
	public static void prepare() {
		if (loading != null) return;
		loading = CompletableFuture.runAsync(() -> {
			try {
				BufferedImage online = download();
				if (online != null) {
					skin = online;
					Files.createDirectories(cacheFile().getParent());
					ImageIO.write(online, "png", cacheFile().toFile());
					return;
				}
			} catch (Throwable t) {
				OemerClient.LOGGER.info("Skin konnte nicht geladen werden ({}), nehme gespeicherten oder Steve", t.toString());
			}
			try {
				if (Files.isRegularFile(cacheFile())) {
					skin = ImageIO.read(cacheFile().toFile());
				}
			} catch (Throwable ignored) {
			}
		});
	}

	/** Skin von Mojang über deine UUID. */
	private static BufferedImage download() throws Exception {
		UUID id = Minecraft.getInstance().getUser().getProfileId();
		if (id == null) return null;
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpResponse<String> profile = http.send(HttpRequest.newBuilder(URI.create(
						"https://sessionserver.mojang.com/session/minecraft/profile/" + id.toString().replace("-", "")))
				.timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
		if (profile.statusCode() != 200) return null;
		JsonObject json = JsonParser.parseString(profile.body()).getAsJsonObject();
		for (var p : json.getAsJsonArray("properties")) {
			JsonObject prop = p.getAsJsonObject();
			if (!"textures".equals(prop.get("name").getAsString())) continue;
			String decoded = new String(Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8);
			JsonObject tex = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
			if (tex == null || !tex.has("SKIN")) return null;
			String url = tex.getAsJsonObject("SKIN").get("url").getAsString().replace("http://", "https://");
			HttpResponse<InputStream> img = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
					HttpResponse.BodyHandlers.ofInputStream());
			if (img.statusCode() != 200) return null;
			try (InputStream in = img.body()) {
				return ImageIO.read(in);
			}
		}
		return null;
	}

	/** Der Steve-Skin aus Minecraft selbst (wenn kein eigener da ist). */
	private static BufferedImage steve() {
		try {
			var res = Minecraft.getInstance().getResourceManager()
					.getResource(Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"));
			if (res.isPresent()) {
				try (InputStream in = res.get().open()) {
					return ImageIO.read(in);
				}
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Steve-Skin nicht gefunden", e);
		}
		return null;
	}

	/** Die fertige Totem-Figur (64x64). Nie null. */
	public static BufferedImage image() {
		prepare();
		BufferedImage s = skin != null ? skin : steve();
		if (s == null) {
			s = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		}
		return figure(ImageToolsSkin.normalize(s));
	}

	/** Baut aus einem 64x64-Skin eine 32x32-Figur und vergrößert sie scharf auf 64x64. */
	static BufferedImage figure(BufferedImage skin) {
		BufferedImage f = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		// Kopf (8x8 → 16x16) + Hut-Ebene
		copy(skin, 8, 8, 8, 8, f, 8, 0, 16, 16, false);
		copy(skin, 40, 8, 8, 8, f, 8, 0, 16, 16, true);
		// Körper (8x12 → 10x10) + Jacke
		copy(skin, 20, 20, 8, 12, f, 11, 16, 10, 10, false);
		copy(skin, 20, 36, 8, 12, f, 11, 16, 10, 10, true);
		// Arme (4x12 → 4x9)
		copy(skin, 44, 20, 4, 12, f, 7, 16, 4, 9, false);
		copy(skin, 36, 52, 4, 12, f, 21, 16, 4, 9, false);
		// Beine (4x12 → 5x6)
		copy(skin, 4, 20, 4, 12, f, 11, 26, 5, 6, false);
		copy(skin, 20, 52, 4, 12, f, 16, 26, 5, 6, false);

		BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				out.setRGB(x, y, f.getRGB(x / 2, y / 2));
			}
		}
		return out;
	}

	private static void copy(BufferedImage src, int sx, int sy, int sw, int sh,
							 BufferedImage dst, int dx, int dy, int dw, int dh, boolean overlay) {
		for (int y = 0; y < dh; y++) {
			for (int x = 0; x < dw; x++) {
				int px = sx + Math.min(sw - 1, x * sw / dw);
				int py = sy + Math.min(sh - 1, y * sh / dh);
				int c = src.getRGB(px, py);
				if ((c >>> 24) < 128) {
					if (overlay) continue;
					// schmale Arme (Alex): leere Spalte → Nachbarpixel nehmen
					c = src.getRGB(Math.max(sx, px - 1), py);
					if ((c >>> 24) < 128) continue;
				}
				dst.setRGB(dx + x, dy + y, c | 0xFF000000);
			}
		}
	}

	/** Alte 64x32-Skins auf 64x64 bringen (linke Seite = gespiegelte rechte). */
	static final class ImageToolsSkin {
		static BufferedImage normalize(BufferedImage s) {
			if (s.getWidth() == 64 && s.getHeight() == 64) return s;
			BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			int w = Math.min(64, s.getWidth()), h = Math.min(64, s.getHeight());
			for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setRGB(x, y, s.getRGB(x, y));
			if (s.getHeight() == 32) {
				// linker Arm/Bein aus rechtem spiegeln
				for (int y = 0; y < 12; y++) for (int x = 0; x < 4; x++) {
					out.setRGB(36 + x, 52 + y, s.getRGB(44 + 3 - x, 20 + y));
					out.setRGB(20 + x, 52 + y, s.getRGB(4 + 3 - x, 20 + y));
				}
			}
			return out;
		}
	}
}
