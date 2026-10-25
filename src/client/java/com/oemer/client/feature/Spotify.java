package com.oemer.client.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Spotify im Spiel: anmelden (sicher mit PKCE, ohne Passwort), aktuellen Song sehen,
 * Play/Pause, vor/zurück, Zufall, Lautstärke und Playlists starten.
 *
 * Du brauchst eine eigene (kostenlose) App im Spotify-Developer-Dashboard mit der
 * Redirect-URI {@value #REDIRECT}. Steuern geht laut Spotify nur mit Premium.
 */
public final class Spotify {
	private Spotify() {
	}

	public static final String REDIRECT = "http://127.0.0.1:43821/callback";
	public static final String DASHBOARD = "https://developer.spotify.com/dashboard";
	private static final int PORT = 43821;
	private static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing "
			+ "playlist-read-private playlist-read-collaborative";
	private static final String API = "https://api.spotify.com/v1";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// ------------------------------------------------------------ Zustand

	public enum Status { NO_CLIENT_ID, NOT_CONNECTED, WAITING_FOR_BROWSER, CONNECTED, ERROR }

	public record Track(String title, String artist, String album, String imageUrl, long progressMs, long durationMs,
						boolean playing, boolean shuffle, int volume, String device, long fetchedAt) {
		/** Fortschritt, weitergerechnet seit dem letzten Abfragen. */
		public long progressNow() {
			long p = playing ? progressMs + (System.currentTimeMillis() - fetchedAt) : progressMs;
			return Math.min(durationMs, Math.max(0, p));
		}
	}

	public record Playlist(String name, String uri, String imageUrl, int tracks) {
	}

	private static volatile Status status = Status.NOT_CONNECTED;
	private static volatile String message = "";
	private static volatile Track track;
	private static volatile List<Playlist> playlists = Collections.emptyList();
	private static volatile boolean loadingPlaylists;

	/** Cover-Bilder (URL → Bild), werden im Menü als Textur hochgeladen. */
	private static final Map<String, BufferedImage> COVERS = Collections.synchronizedMap(new HashMap<>());

	private static final class Tokens {
		String access = "";
		String refresh = "";
		long expiresAt;
	}

	private static Tokens tokens = new Tokens();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Ömer Spotify");
		t.setDaemon(true);
		return t;
	});
	private static BooleanSupplier pollWanted = () -> false;

	public static Status status() {
		if (OemerConfig.get().spotifyClientId.isBlank() && status != Status.CONNECTED) return Status.NO_CLIENT_ID;
		return status;
	}

	public static String message() {
		return message;
	}

	public static Track track() {
		return track;
	}

	public static List<Playlist> playlists() {
		return playlists;
	}

	public static boolean loadingPlaylists() {
		return loadingPlaylists;
	}

	public static BufferedImage cover(String url) {
		if (url == null || url.isEmpty()) return null;
		BufferedImage img = COVERS.get(url);
		if (img == null && !COVERS.containsKey(url)) {
			COVERS.put(url, null);
			EXEC.execute(() -> {
				try {
					HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).build(),
							HttpResponse.BodyHandlers.ofInputStream());
					try (InputStream in = r.body()) {
						BufferedImage b = ImageIO.read(in);
						if (b != null) COVERS.put(url, ImageTools.limit(b, 128));
					}
				} catch (Exception e) {
					OemerClient.LOGGER.debug("Cover nicht geladen: {}", url);
				}
			});
		}
		return img;
	}

	// --------------------------------------------------------------- Start

	private static Path tokenFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("spotify.json");
	}

	/** Beim Spielstart: gespeicherte Anmeldung laden und regelmäßig den Song abfragen. */
	public static void init(BooleanSupplier wanted) {
		pollWanted = wanted;
		try {
			if (Files.isRegularFile(tokenFile())) {
				Tokens t = GSON.fromJson(Files.readString(tokenFile()), Tokens.class);
				if (t != null && t.refresh != null && !t.refresh.isEmpty()) {
					tokens = t;
					status = Status.CONNECTED;
				}
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Spotify-Anmeldung konnte nicht geladen werden", e);
		}
		EXEC.scheduleWithFixedDelay(() -> {
			try {
				if (status == Status.CONNECTED && pollWanted.getAsBoolean()) refreshPlayback();
			} catch (Throwable t) {
				OemerClient.LOGGER.debug("Spotify-Abfrage fehlgeschlagen", t);
			}
		}, 2, 3, TimeUnit.SECONDS);
	}

	private static void saveTokens() {
		try {
			Files.createDirectories(tokenFile().getParent());
			Files.writeString(tokenFile(), GSON.toJson(tokens), StandardCharsets.UTF_8);
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Spotify-Anmeldung konnte nicht gespeichert werden", e);
		}
	}

	public static void disconnect() {
		tokens = new Tokens();
		track = null;
		playlists = Collections.emptyList();
		status = Status.NOT_CONNECTED;
		message = "Abgemeldet";
		try {
			Files.deleteIfExists(tokenFile());
		} catch (Exception ignored) {
		}
	}

	// ------------------------------------------------------------- Anmelden

	/** PKCE: Zufallswort → SHA-256 → Base64url. */
	public static String challenge(String verifier) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
	}

	private static String random(int len) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		SecureRandom r = new SecureRandom();
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < len; i++) b.append(chars.charAt(r.nextInt(chars.length())));
		return b.toString();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/** Öffnet den Browser zum Anmelden und wartet auf die Antwort von Spotify. */
	public static void connect() {
		String clientId = OemerConfig.get().spotifyClientId.trim();
		if (clientId.isEmpty()) {
			message = "Erst die Client-ID eintragen";
			return;
		}
		if (status == Status.WAITING_FOR_BROWSER) return;
		status = Status.WAITING_FOR_BROWSER;
		message = "Im Browser anmelden …";
		EXEC.execute(() -> {
			try (ServerSocket server = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))) {
				server.setSoTimeout(180_000);
				String verifier = random(64);
				String state = random(16);
				String url = "https://accounts.spotify.com/authorize?response_type=code"
						+ "&client_id=" + enc(clientId)
						+ "&scope=" + enc(SCOPES)
						+ "&redirect_uri=" + enc(REDIRECT)
						+ "&state=" + state
						+ "&code_challenge_method=S256&code_challenge=" + challenge(verifier);
				Util.getPlatform().openUri(url);

				String code = null, error = null;
				while (code == null && error == null) {
					try (Socket s = server.accept()) {
						BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
						String line = in.readLine();
						Map<String, String> q = query(line);
						boolean ours = line != null && line.startsWith("GET /callback");
						if (ours && state.equals(q.get("state"))) {
							code = q.get("code");
							error = q.get("error");
						}
						String html = ours
								? "<html><body style='background:#0b0b10;color:#fff;font-family:sans-serif;text-align:center;padding-top:80px'>"
								+ "<h1>" + (code != null ? "Fertig!" : "Nicht geklappt") + "</h1><p>Du kannst zurück zu Minecraft.</p></body></html>"
								: "";
						byte[] body = html.getBytes(StandardCharsets.UTF_8);
						OutputStream out = s.getOutputStream();
						out.write(("HTTP/1.1 " + (ours ? "200 OK" : "404 Not Found") + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
								+ body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
						out.write(body);
						out.flush();
						if (!ours) code = null;
					}
				}
				if (code == null) {
					status = Status.ERROR;
					message = "Abgelehnt: " + error;
					return;
				}
				JsonObject t = postToken("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
						+ "&client_id=" + enc(clientId) + "&code_verifier=" + enc(verifier));
				applyToken(t);
				status = Status.CONNECTED;
				message = "Verbunden ✔";
				refreshPlayback();
				loadPlaylists();
			} catch (java.net.SocketTimeoutException e) {
				status = Status.ERROR;
				message = "Zeit abgelaufen – nochmal verbinden";
			} catch (java.net.BindException e) {
				status = Status.ERROR;
				message = "Port " + PORT + " ist belegt";
			} catch (Exception e) {
				OemerClient.LOGGER.warn("Spotify-Anmeldung fehlgeschlagen", e);
				status = Status.ERROR;
				message = "Fehler: " + e.getMessage();
			}
		});
	}

	static Map<String, String> query(String requestLine) {
		Map<String, String> map = new HashMap<>();
		if (requestLine == null) return map;
		int q = requestLine.indexOf('?');
		int sp = requestLine.indexOf(' ', q + 1);
		if (q < 0) return map;
		String qs = requestLine.substring(q + 1, sp < 0 ? requestLine.length() : sp);
		for (String kv : qs.split("&")) {
			int eq = kv.indexOf('=');
			if (eq > 0) {
				map.put(URLDecoder.decode(kv.substring(0, eq), StandardCharsets.UTF_8),
						URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8));
			}
		}
		return map;
	}

	private static JsonObject postToken(String form) throws Exception {
		HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			throw new IllegalStateException("Token " + r.statusCode() + ": " + r.body());
		}
		return JsonParser.parseString(r.body()).getAsJsonObject();
	}

	private static void applyToken(JsonObject t) {
		tokens.access = t.get("access_token").getAsString();
		if (t.has("refresh_token")) tokens.refresh = t.get("refresh_token").getAsString();
		tokens.expiresAt = System.currentTimeMillis() + (t.get("expires_in").getAsLong() - 30) * 1000L;
		saveTokens();
	}

	private static synchronized void ensureToken() throws Exception {
		if (System.currentTimeMillis() < tokens.expiresAt && !tokens.access.isEmpty()) return;
		JsonObject t = postToken("grant_type=refresh_token&refresh_token=" + enc(tokens.refresh)
				+ "&client_id=" + enc(OemerConfig.get().spotifyClientId.trim()));
		applyToken(t);
	}

	// ------------------------------------------------------------------ API

	private record Resp(int code, String body) {
	}

	private static Resp api(String method, String path, String json) throws Exception {
		ensureToken();
		for (int attempt = 0; attempt < 2; attempt++) {
			HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
					.header("Authorization", "Bearer " + tokens.access)
					.timeout(Duration.ofSeconds(8));
			HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json);
			if (json != null) b.header("Content-Type", "application/json");
			b.method(method, body);
			HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() == 401 && attempt == 0) {
				tokens.expiresAt = 0;
				ensureToken();
				continue;
			}
			return new Resp(r.statusCode(), r.body());
		}
		return new Resp(401, "");
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static String firstImage(JsonObject o) {
		if (o == null || !o.has("images") || !o.get("images").isJsonArray()) return "";
		JsonArray imgs = o.getAsJsonArray("images");
		if (imgs.isEmpty()) return "";
		// kleinstes Bild, das noch gut aussieht
		String best = "";
		int bestSize = Integer.MAX_VALUE;
		for (JsonElement e : imgs) {
			JsonObject im = e.getAsJsonObject();
			int w = im.has("width") && !im.get("width").isJsonNull() ? im.get("width").getAsInt() : 300;
			if (w >= 64 && w < bestSize) {
				bestSize = w;
				best = str(im, "url");
			}
		}
		return best.isEmpty() ? str(imgs.get(0).getAsJsonObject(), "url") : best;
	}

	/** Aktuellen Song abfragen. */
	public static void refreshPlayback() throws Exception {
		Resp r = api("GET", "/me/player", null);
		if (r.code() == 204 || r.body().isBlank()) {
			track = null;
			message = "Öffne Spotify auf einem Gerät und starte Musik";
			return;
		}
		if (r.code() != 200) {
			message = "Spotify antwortet " + r.code();
			return;
		}
		JsonObject p = JsonParser.parseString(r.body()).getAsJsonObject();
		JsonObject item = p.has("item") && p.get("item").isJsonObject() ? p.getAsJsonObject("item") : null;
		JsonObject device = p.has("device") && p.get("device").isJsonObject() ? p.getAsJsonObject("device") : null;
		if (item == null) {
			track = null;
			message = "Gerade läuft nichts";
			return;
		}
		StringBuilder artists = new StringBuilder();
		if (item.has("artists")) {
			for (JsonElement a : item.getAsJsonArray("artists")) {
				if (!artists.isEmpty()) artists.append(", ");
				artists.append(str(a.getAsJsonObject(), "name"));
			}
		} else if (item.has("show")) {
			artists.append(str(item.getAsJsonObject("show"), "name"));
		}
		JsonObject album = item.has("album") ? item.getAsJsonObject("album") : null;
		String img = album != null ? firstImage(album) : firstImage(item);
		int volume = device != null && device.has("volume_percent") && !device.get("volume_percent").isJsonNull()
				? device.get("volume_percent").getAsInt() : -1;
		track = new Track(str(item, "name"), artists.toString(), album == null ? "" : str(album, "name"), img,
				p.has("progress_ms") && !p.get("progress_ms").isJsonNull() ? p.get("progress_ms").getAsLong() : 0,
				item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 1,
				p.has("is_playing") && p.get("is_playing").getAsBoolean(),
				p.has("shuffle_state") && p.get("shuffle_state").getAsBoolean(),
				volume, device == null ? "" : str(device, "name"), System.currentTimeMillis());
		message = "";
		cover(img);
	}

	/** Deine Playlists laden. */
	public static void loadPlaylists() {
		if (status != Status.CONNECTED) return;
		loadingPlaylists = true;
		EXEC.execute(() -> {
			try {
				Resp r = api("GET", "/me/playlists?limit=50", null);
				if (r.code() != 200) {
					message = "Playlists: Fehler " + r.code();
					return;
				}
				List<Playlist> list = new ArrayList<>();
				for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("items")) {
					if (!e.isJsonObject()) continue;
					JsonObject pl = e.getAsJsonObject();
					int count = 0;
					for (String k : new String[]{"items", "tracks"}) {
						if (pl.has(k) && pl.get(k).isJsonObject() && pl.getAsJsonObject(k).has("total")) {
							count = pl.getAsJsonObject(k).get("total").getAsInt();
						}
					}
					list.add(new Playlist(str(pl, "name"), str(pl, "uri"), firstImage(pl), count));
				}
				playlists = list;
			} catch (Exception ex) {
				message = "Playlists: " + ex.getMessage();
			} finally {
				loadingPlaylists = false;
			}
		});
	}

	// -------------------------------------------------------------- Steuern

	private static void action(String method, String path, String json, String okText) {
		if (status != Status.CONNECTED) {
			message = "Erst mit Spotify verbinden";
			return;
		}
		EXEC.execute(() -> {
			try {
				Resp r = api(method, path, json);
				if (r.code() == 403) {
					message = "Spotify erlaubt Steuern nur mit Premium";
				} else if (r.code() == 404) {
					message = "Kein aktives Gerät – öffne Spotify und spiel kurz etwas ab";
				} else if (r.code() >= 300) {
					message = "Fehler " + r.code();
				} else {
					message = okText;
				}
				Thread.sleep(350);
				refreshPlayback();
			} catch (Exception e) {
				message = "Fehler: " + e.getMessage();
			}
		});
	}

	public static void playPause() {
		Track t = track;
		if (t != null && t.playing()) action("PUT", "/me/player/pause", null, "Pause");
		else action("PUT", "/me/player/play", null, "Play");
	}

	public static void next() {
		action("POST", "/me/player/next", null, "Nächster Song");
	}

	public static void previous() {
		action("POST", "/me/player/previous", null, "Vorheriger Song");
	}

	public static void toggleShuffle() {
		Track t = track;
		boolean on = t == null || !t.shuffle();
		action("PUT", "/me/player/shuffle?state=" + on, null, on ? "Zufall an" : "Zufall aus");
	}

	public static void volume(int delta) {
		Track t = track;
		int v = Math.max(0, Math.min(100, (t == null || t.volume() < 0 ? 50 : t.volume()) + delta));
		action("PUT", "/me/player/volume?volume_percent=" + v, null, "Lautstärke " + v + "%");
	}

	public static void playPlaylist(Playlist p) {
		JsonObject body = new JsonObject();
		body.addProperty("context_uri", p.uri());
		action("PUT", "/me/player/play", body.toString(), "Spielt: " + p.name());
	}

	public static String time(long ms) {
		long s = ms / 1000;
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}
}
