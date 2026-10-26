package com.oemer.client.feature;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.minecraft.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube-Musik, kostenlos und ohne Premium.
 * Der Ömer-Player läuft als kleine Webseite auf deinem PC (http://127.0.0.1:43822) mit dem offiziellen
 * YouTube-Player. Minecraft steuert ihn: Play/Pause, Weiter, Lautstärke, Warteschlange, Bibliothek, Playlists.
 */
public final class YouTubeMusic {
	private YouTubeMusic() {
	}

	/** Ein Song (wird so in der Config gespeichert). */
	public static class Track {
		public String id = "";
		public String title = "";
		public String artist = "";

		public Track() {
		}

		public Track(String id, String title, String artist) {
			this.id = id;
			this.title = title == null ? "" : title;
			this.artist = artist == null ? "" : artist;
		}

		public String thumb() {
			return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
		}

		public String shownTitle() {
			return title.isEmpty() ? "YouTube-Video " + id : title;
		}
	}

	/** Eigene Playlist (in der Config gespeichert). */
	public static class Playlist {
		public String name = "";
		public List<Track> tracks = new ArrayList<>();

		public Playlist() {
		}

		public Playlist(String name) {
			this.name = name;
		}
	}

	public static final int PORT = 43822;
	public static final String GUIDE = "https://console.cloud.google.com/apis/library/youtube.googleapis.com";
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final ExecutorService EXEC = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "Ömer YouTube");
		t.setDaemon(true);
		return t;
	});
	private static final Pattern ID = Pattern.compile("(?:v=|youtu\\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})");
	private static final Pattern BARE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
	private static final Pattern LIST = Pattern.compile("[?&]list=([A-Za-z0-9_-]{10,})");

	// ---------------------------------------------------------- Zustand
	private static final List<Track> QUEUE = Collections.synchronizedList(new ArrayList<>());
	private static volatile int index = -1;
	private static volatile boolean wantPlay;
	private static volatile int seq;          // ändert sich bei jedem neuen Song
	private static volatile int seekSeq;
	private static volatile long seekMs;
	// vom Browser gemeldet
	private static volatile String pageVideo = "";
	private static volatile boolean pagePlaying;
	private static volatile long pagePos, pageDur, pageStatusAt, lastPoll;
	private static volatile String message = "";
	private static volatile boolean pageNeedsClick = true;
	private static final List<Track> RESULTS = Collections.synchronizedList(new ArrayList<>());
	private static volatile boolean searching;
	private static volatile int lastEndedSeq = -1;
	/** true = du hast den Song selbst gewählt (dann wird bei einem Fehler nicht einfach weitergesprungen). */
	private static volatile boolean userPicked;
	private static volatile int errorStreak;

	private static ServerSocket server;
	private static int port = PORT;

	// ----------------------------------------------------------- Abfragen

	public static String url() {
		return "http://127.0.0.1:" + port + "/";
	}

	public static Track current() {
		synchronized (QUEUE) {
			return index >= 0 && index < QUEUE.size() ? QUEUE.get(index) : null;
		}
	}

	public static List<Track> queue() {
		synchronized (QUEUE) {
			return new ArrayList<>(QUEUE);
		}
	}

	public static int index() {
		return index;
	}

	public static List<Track> results() {
		synchronized (RESULTS) {
			return new ArrayList<>(RESULTS);
		}
	}

	public static boolean searching() {
		return searching;
	}

	public static String message() {
		return message;
	}

	/** Ist die Player-Seite gerade offen (meldet sich regelmäßig)? */
	public static boolean pageConnected() {
		return System.currentTimeMillis() - lastPoll < 4000;
	}

	public static boolean needsClick() {
		return pageConnected() && pageNeedsClick;
	}

	public static boolean playing() {
		return wantPlay;
	}

	public static long position() {
		long p = pagePos;
		if (pagePlaying && pageStatusAt > 0) p += System.currentTimeMillis() - pageStatusAt;
		return pageDur > 0 ? Math.min(p, pageDur) : p;
	}

	public static long duration() {
		return pageDur;
	}

	public static boolean hasKey() {
		return !OemerConfig.get().ytApiKey.isBlank();
	}

	// ---------------------------------------------------------- Steuern

	/** Startet den kleinen Webserver für die Player-Seite (einmal beim Spielstart). */
	public static synchronized void start() {
		if (server != null) return;
		for (int p = PORT; p < PORT + 5; p++) {
			try {
				server = new ServerSocket(p, 50, InetAddress.getLoopbackAddress());
				port = p;
				break;
			} catch (Exception e) {
				OemerClient.LOGGER.debug("Port {} belegt", p);
			}
		}
		if (server == null) {
			message = "Player konnte nicht starten (Port belegt)";
			return;
		}
		Thread t = new Thread(() -> {
			while (!server.isClosed()) {
				try {
					Socket s = server.accept();
					EXEC.execute(() -> handle(s));
				} catch (Exception e) {
					if (!server.isClosed()) OemerClient.LOGGER.debug("Player-Server", e);
				}
			}
		}, "Ömer YouTube Server");
		t.setDaemon(true);
		t.start();
		OemerClient.LOGGER.info("YouTube-Player bereit auf {}", url());
	}

	public static void openPlayer() {
		start();
		Util.getPlatform().openUri(url());
	}

	private static void ensurePage() {
		if (!pageConnected()) openPlayer();
	}

	/** Liste abspielen, beginnend bei start. */
	public static void playList(List<Track> tracks, int start) {
		if (tracks.isEmpty()) return;
		List<Track> list = new ArrayList<>(tracks);
		int s = Math.max(0, Math.min(start, list.size() - 1));
		if (OemerConfig.get().ytShuffle) {
			Track first = list.remove(s);
			Collections.shuffle(list, new Random());
			list.add(0, first);
			s = 0;
		}
		synchronized (QUEUE) {
			QUEUE.clear();
			QUEUE.addAll(list);
			index = s;
		}
		userPicked = true;
		startCurrent();
	}

	public static void play(Track t) {
		synchronized (QUEUE) {
			QUEUE.add(Math.max(0, index + 1), t);
			index = Math.max(0, index + 1);
		}
		userPicked = true;
		startCurrent();
	}

	/** Hinten an die Warteschlange hängen. */
	public static void enqueue(Track t) {
		synchronized (QUEUE) {
			QUEUE.add(t);
			if (index < 0) index = 0;
		}
		if (current() == t) startCurrent();
		message = "In der Warteschlange: " + t.shownTitle();
	}

	public static void jump(int i) {
		synchronized (QUEUE) {
			if (i < 0 || i >= QUEUE.size()) return;
			index = i;
		}
		userPicked = true;
		startCurrent();
	}

	private static void startCurrent() {
		seq++;
		wantPlay = true;
		pagePos = 0;
		pageDur = 0;
		message = "";
		ensurePage();
	}

	public static void playPause() {
		if (current() == null) {
			List<Track> lib = OemerConfig.get().ytLibrary;
			if (!lib.isEmpty()) playList(lib, 0);
			return;
		}
		wantPlay = !wantPlay;
		if (wantPlay) ensurePage();
	}

	public static void next() {
		userPicked = false;
		synchronized (QUEUE) {
			if (QUEUE.isEmpty()) return;
			if (index + 1 >= QUEUE.size()) {
				if (!OemerConfig.get().ytRepeat) {
					wantPlay = false;
					return;
				}
				index = 0;
			} else {
				index++;
			}
		}
		startCurrent();
	}

	public static void previous() {
		userPicked = false;
		if (position() > 4000) {
			seek(0);
			return;
		}
		synchronized (QUEUE) {
			if (QUEUE.isEmpty()) return;
			index = Math.max(0, index - 1);
		}
		startCurrent();
	}

	public static void seek(long ms) {
		seekMs = Math.max(0, ms);
		seekSeq++;
		pagePos = seekMs;
	}

	public static void volume(int delta) {
		OemerConfig c = OemerConfig.get();
		c.ytVolume = Math.max(0, Math.min(100, c.ytVolume + delta));
		OemerConfig.save();
	}

	public static void toggleShuffle() {
		OemerConfig c = OemerConfig.get();
		c.ytShuffle = !c.ytShuffle;
		OemerConfig.save();
	}

	// --------------------------------------------------------- Bibliothek

	public static boolean inLibrary(String id) {
		for (Track t : OemerConfig.get().ytLibrary) if (t.id.equals(id)) return true;
		return false;
	}

	public static void toggleLibrary(Track t) {
		List<Track> lib = OemerConfig.get().ytLibrary;
		if (!lib.removeIf(x -> x.id.equals(t.id))) lib.add(0, t);
		OemerConfig.save();
	}

	public static void addToPlaylist(String name, Track t) {
		OemerConfig c = OemerConfig.get();
		Playlist pl = null;
		for (Playlist p : c.ytPlaylists) if (p.name.equalsIgnoreCase(name)) pl = p;
		if (pl == null) {
			pl = new Playlist(name);
			c.ytPlaylists.add(pl);
		}
		final Playlist target = pl;
		if (target.tracks.stream().noneMatch(x -> x.id.equals(t.id))) target.tracks.add(t);
		OemerConfig.save();
	}

	public static void deletePlaylist(Playlist p) {
		OemerConfig.get().ytPlaylists.remove(p);
		OemerConfig.save();
	}

	/** Neue, leere Playlist (Name wird bei Bedarf durchnummeriert). */
	public static Playlist createPlaylist(String name) {
		OemerConfig c = OemerConfig.get();
		String base = name == null || name.isBlank() ? "Meine Playlist" : name.trim();
		String n = base;
		int k = 2;
		while (true) {
			final String test = n;
			if (c.ytPlaylists.stream().noneMatch(p -> p.name.equalsIgnoreCase(test))) break;
			n = base + " " + k++;
		}
		Playlist p = new Playlist(n);
		c.ytPlaylists.add(p);
		OemerConfig.save();
		return p;
	}

	public static void addToPlaylist(Playlist p, Track t) {
		if (p.tracks.stream().noneMatch(x -> x.id.equals(t.id))) p.tracks.add(t);
		OemerConfig.save();
	}

	public static void removeFromPlaylist(Playlist p, Track t) {
		p.tracks.removeIf(x -> x.id.equals(t.id));
		OemerConfig.save();
	}

	public static void removeFromLibrary(Track t) {
		OemerConfig.get().ytLibrary.removeIf(x -> x.id.equals(t.id));
		OemerConfig.save();
	}

	public static void clearLibrary() {
		OemerConfig.get().ytLibrary.clear();
		OemerConfig.save();
	}

	/** Song aus der Warteschlange nehmen. Läuft er gerade, geht es mit dem nächsten weiter. */
	public static void removeFromQueue(int i) {
		boolean restart = false;
		synchronized (QUEUE) {
			if (i < 0 || i >= QUEUE.size()) return;
			QUEUE.remove(i);
			if (QUEUE.isEmpty()) {
				index = -1;
				wantPlay = false;
			} else if (i < index) {
				index--;
			} else if (i == index) {
				if (index >= QUEUE.size()) {
					index = OemerConfig.get().ytRepeat ? 0 : QUEUE.size() - 1;
					wantPlay = wantPlay && OemerConfig.get().ytRepeat;
				}
				restart = wantPlay;
			}
		}
		if (restart) startCurrent();
	}

	public static void clearQueue() {
		synchronized (QUEUE) {
			QUEUE.clear();
			index = -1;
		}
		wantPlay = false;
		seq++;
	}

	// ------------------------------------------- Suchen / Link einfügen

	/**
	 * Eingabe aus dem Suchfeld: YouTube-Link → Song, Playlist-Link → Playlist importieren,
	 * sonst → Suche (braucht einen kostenlosen YouTube-API-Schlüssel).
	 */
	public static void submit(String input) {
		String q = input == null ? "" : input.trim();
		if (q.isEmpty()) return;
		Matcher list = LIST.matcher(q);
		Matcher id = ID.matcher(q);
		if (list.find() && !id.find()) {
			importPlaylist(list.group(1));
			return;
		}
		id.reset();
		if (id.find() || BARE_ID.matcher(q).matches()) {
			String vid = BARE_ID.matcher(q).matches() ? q : id.group(1);
			searching = true;
			EXEC.execute(() -> {
				Track t = resolve(vid);
				List<Track> res = new ArrayList<>();
				String msg;
				if (embeddable(vid)) {
					res.add(t);
					msg = "Gefunden: " + t.shownTitle();
				} else if (hasKey()) {
					try {
						res.addAll(alternatives(t, 5));
					} catch (Exception e) {
						OemerClient.LOGGER.warn("YouTube-Ersatzsuche", e);
					}
					msg = res.isEmpty() ? "„" + t.shownTitle() + "“ darf nicht eingebettet werden und ich habe keine andere Version gefunden"
							: "Das Original lässt Einbetten nicht zu – das sind Versionen, die funktionieren";
				} else {
					res.add(t);
					msg = "„" + t.shownTitle() + "“ darf nicht eingebettet werden – mit YouTube-Schlüssel suche ich automatisch eine andere Version";
				}
				synchronized (RESULTS) {
					RESULTS.clear();
					RESULTS.addAll(res);
				}
				searching = false;
				message = msg;
			});
			return;
		}
		search(q);
	}

	public static void search(String q) {
		if (!hasKey()) {
			message = "Für die Suche: kostenlosen YouTube-Schlüssel eintragen – oder einfach einen YouTube-Link einfügen";
			return;
		}
		searching = true;
		message = "Suche …";
		EXEC.execute(() -> {
			try {
				String u = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=25&q="
						+ URLEncoder.encode(q, StandardCharsets.UTF_8) + "&key=" + URLEncoder.encode(OemerConfig.get().ytApiKey.trim(), StandardCharsets.UTF_8);
				JsonObject o = getJson(u);
				List<Track> out = new ArrayList<>();
				for (JsonElement e : o.getAsJsonArray("items")) {
					JsonObject it = e.getAsJsonObject();
					String vid = it.getAsJsonObject("id").get("videoId").getAsString();
					JsonObject sn = it.getAsJsonObject("snippet");
					out.add(new Track(vid, unescape(sn.get("title").getAsString()), unescape(sn.get("channelTitle").getAsString())));
				}
				int before = out.size();
				out = onlyEmbeddable(out);
				synchronized (RESULTS) {
					RESULTS.clear();
					RESULTS.addAll(out);
				}
				message = before == 0 ? "Nichts gefunden" : out.isEmpty() ? "Alle Treffer verbieten das Einbetten – such nach einer anderen Version (z. B. „lyrics“)"
						: out.size() + " Ergebnisse" + (out.size() < before ? " (" + (before - out.size()) + " gesperrte ausgeblendet)" : "");
			} catch (Exception e) {
				message = "Suche ging nicht: " + e.getMessage();
				OemerClient.LOGGER.warn("YouTube-Suche", e);
			} finally {
				searching = false;
			}
		});
	}

	private static final Map<String, Boolean> EMBED = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Set<String> ALT_TRIED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Darf das Video auf anderen Seiten (also in unserem Player) laufen? YouTube antwortet bei gesperrten mit 401. */
	public static boolean embeddable(String vid) {
		Boolean known = EMBED.get(vid);
		if (known != null) return known;
		try {
			HttpResponse<Void> r = HTTP.send(HttpRequest.newBuilder(URI.create("https://www.youtube.com/oembed?format=json&url="
					+ URLEncoder.encode("https://www.youtube.com/watch?v=" + vid, StandardCharsets.UTF_8)))
					.timeout(Duration.ofSeconds(8)).header("User-Agent", "OemerClient").build(), HttpResponse.BodyHandlers.discarding());
			int c = r.statusCode();
			boolean ok = !(c == 401 || c == 403 || c == 404);
			EMBED.put(vid, ok);
			return ok;
		} catch (Exception e) {
			return true; // nicht sicher -> lieber versuchen
		}
	}

	private static List<Track> onlyEmbeddable(List<Track> in) {
		List<java.util.concurrent.CompletableFuture<Boolean>> fs = new ArrayList<>();
		for (Track t : in) fs.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> embeddable(t.id), EXEC));
		List<Track> out = new ArrayList<>();
		for (int i = 0; i < in.size(); i++) {
			boolean ok = true;
			try {
				ok = fs.get(i).get(10, java.util.concurrent.TimeUnit.SECONDS);
			} catch (Exception ignored) {
			}
			if (ok) out.add(in.get(i));
		}
		return out;
	}

	/** Suchtext für eine andere Version desselben Songs, z. B. "Madison Beer - bad enough lyrics". */
	public static String altQuery(Track t) {
		String title = t.title.replaceAll("[\\(\\[][^\\)\\]]*[\\)\\]]", " ").replaceAll("(?i)official|music video|video|audio|lyrics|HD|4K", " ")
				.replaceAll("\\s+", " ").trim();
		String artist = t.artist.replaceAll("(?i)VEVO|\\s*-\\s*Topic|official", "").replaceAll("\\s+", " ").trim();
		String q = title.contains(" - ") || artist.isEmpty() ? title : artist + " " + title;
		return q.trim() + " lyrics";
	}

	/** Andere Versionen des Songs, die sich einbetten lassen (braucht den YouTube-Schlüssel). */
	private static List<Track> alternatives(Track failed, int max) throws Exception {
		String u = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=15&q="
				+ URLEncoder.encode(altQuery(failed), StandardCharsets.UTF_8) + "&key=" + URLEncoder.encode(OemerConfig.get().ytApiKey.trim(), StandardCharsets.UTF_8);
		JsonObject o = getJson(u);
		List<Track> found = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("items")) {
			JsonObject it = e.getAsJsonObject();
			String vid = it.getAsJsonObject("id").get("videoId").getAsString();
			if (vid.equals(failed.id)) continue;
			JsonObject sn = it.getAsJsonObject("snippet");
			found.add(new Track(vid, unescape(sn.get("title").getAsString()), unescape(sn.get("channelTitle").getAsString())));
		}
		List<Track> ok = onlyEmbeddable(found);
		return ok.size() > max ? new ArrayList<>(ok.subList(0, max)) : ok;
	}

	/** Titel und Kanal über YouTube oEmbed (ohne Schlüssel). */
	public static Track resolve(String vid) {
		try {
			JsonObject o = getJson("https://www.youtube.com/oembed?format=json&url="
					+ URLEncoder.encode("https://www.youtube.com/watch?v=" + vid, StandardCharsets.UTF_8));
			return new Track(vid, o.get("title").getAsString(), o.has("author_name") ? o.get("author_name").getAsString() : "");
		} catch (Exception e) {
			return new Track(vid, "", "");
		}
	}

	/** YouTube-Playlist als eigene Playlist speichern. */
	public static void importPlaylist(String listId) {
		message = "Playlist wird geladen …";
		if (hasKey()) {
			EXEC.execute(() -> {
				try {
					List<Track> out = new ArrayList<>();
					String token = "";
					String name = "YouTube-Playlist";
					try {
						JsonObject meta = getJson("https://www.googleapis.com/youtube/v3/playlists?part=snippet&id=" + listId + "&key="
								+ URLEncoder.encode(OemerConfig.get().ytApiKey.trim(), StandardCharsets.UTF_8));
						JsonArray items = meta.getAsJsonArray("items");
						if (!items.isEmpty()) name = unescape(items.get(0).getAsJsonObject().getAsJsonObject("snippet").get("title").getAsString());
					} catch (Exception ignored) {
					}
					for (int page = 0; page < 6; page++) {
						JsonObject o = getJson("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId="
								+ listId + (token.isEmpty() ? "" : "&pageToken=" + token) + "&key="
								+ URLEncoder.encode(OemerConfig.get().ytApiKey.trim(), StandardCharsets.UTF_8));
						for (JsonElement e : o.getAsJsonArray("items")) {
							JsonObject sn = e.getAsJsonObject().getAsJsonObject("snippet");
							String vid = sn.getAsJsonObject("resourceId").get("videoId").getAsString();
							String title = unescape(sn.get("title").getAsString());
							if (title.equals("Deleted video") || title.equals("Private video")) continue;
							out.add(new Track(vid, title, sn.has("videoOwnerChannelTitle") ? unescape(sn.get("videoOwnerChannelTitle").getAsString()) : ""));
						}
						if (!o.has("nextPageToken")) break;
						token = o.get("nextPageToken").getAsString();
					}
					savePlaylist(name, out);
				} catch (Exception e) {
					message = "Playlist ging nicht: " + e.getMessage();
				}
			});
		} else {
			// ohne Schlüssel: die Player-Seite liest die Playlist mit dem YouTube-Player aus
			pendingImport = listId;
			ensurePage();
		}
	}

	private static volatile String pendingImport = "";

	private static void savePlaylist(String name, List<Track> tracks) {
		if (tracks.isEmpty()) {
			message = "Playlist ist leer oder privat";
			return;
		}
		OemerConfig c = OemerConfig.get();
		String n = name;
		int k = 2;
		while (true) {
			final String test = n;
			if (c.ytPlaylists.stream().noneMatch(p -> p.name.equalsIgnoreCase(test))) break;
			n = name + " " + k++;
		}
		Playlist p = new Playlist(n);
		p.tracks.addAll(tracks);
		c.ytPlaylists.add(p);
		OemerConfig.save();
		message = "Playlist „" + n + "“ gespeichert (" + tracks.size() + " Songs)";
	}

	// ---------------------------------------------------------------- HTTP

	private static JsonObject getJson(String url) throws Exception {
		HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
				.header("User-Agent", "OemerClient").build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() >= 400) {
			String why = "HTTP " + r.statusCode();
			try {
				why = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("error").get("message").getAsString();
			} catch (Exception ignored) {
			}
			throw new Exception(why);
		}
		return JsonParser.parseString(r.body()).getAsJsonObject();
	}

	private static String unescape(String s) {
		return s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">");
	}

	private static void handle(Socket s) {
		try (s) {
			s.setSoTimeout(5000);
			InputStream in = s.getInputStream();
			ByteArrayOutputStream head = new ByteArrayOutputStream();
			int b, last4 = 0;
			while ((b = in.read()) != -1) {
				head.write(b);
				last4 = (last4 << 8) | b;
				if (last4 == 0x0D0A0D0A || head.size() > 16384) break;
			}
			String[] lines = head.toString(StandardCharsets.UTF_8).split("\r\n");
			if (lines.length == 0 || lines[0].isEmpty()) return;
			String[] req = lines[0].split(" ");
			if (req.length < 2) return;
			Map<String, String> headers = new HashMap<>();
			for (int i = 1; i < lines.length; i++) {
				int c = lines[i].indexOf(':');
				if (c > 0) headers.put(lines[i].substring(0, c).trim().toLowerCase(), lines[i].substring(c + 1).trim());
			}
			// Schutz: nur unsere eigene Seite darf den Player steuern
			String host = headers.getOrDefault("host", "");
			if (!host.equals("127.0.0.1:" + port) && !host.equals("localhost:" + port)) {
				send(s.getOutputStream(), 403, "text/plain", "nein".getBytes(StandardCharsets.UTF_8));
				return;
			}
			String origin = headers.get("origin");
			String target = req[1];
			String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
			Map<String, String> q = query(target);
			if (path.startsWith("/api/") && origin != null && !origin.equals("http://" + host)) {
				send(s.getOutputStream(), 403, "text/plain", "nein".getBytes(StandardCharsets.UTF_8));
				return;
			}
			byte[] body;
			String type = "application/json; charset=utf-8";
			switch (path) {
				case "/", "/index.html" -> {
					try (InputStream page = YouTubeMusic.class.getResourceAsStream("/assets/oemerclient/web/player.html")) {
						body = page == null ? "Seite fehlt".getBytes(StandardCharsets.UTF_8) : page.readAllBytes();
					}
					type = "text/html; charset=utf-8";
				}
				case "/api/state" -> {
					lastPoll = System.currentTimeMillis();
					body = GSON.toJson(state()).getBytes(StandardCharsets.UTF_8);
				}
				case "/api/status" -> {
					lastPoll = System.currentTimeMillis();
					pageVideo = q.getOrDefault("id", "");
					pagePlaying = "1".equals(q.get("playing"));
					pagePos = parseLong(q.get("pos"));
					pageDur = parseLong(q.get("dur"));
					pageNeedsClick = "1".equals(q.get("click"));
					pageStatusAt = System.currentTimeMillis();
					if (pagePlaying) errorStreak = 0;
					Track cur = current();
					String title = q.getOrDefault("title", "");
					if (cur != null && cur.id.equals(pageVideo) && cur.title.isEmpty() && !title.isEmpty()) {
						cur.title = title;
						cur.artist = q.getOrDefault("author", "");
					}
					body = "{}".getBytes(StandardCharsets.UTF_8);
				}
				case "/api/cmd" -> {
					command(q);
					body = GSON.toJson(state()).getBytes(StandardCharsets.UTF_8);
				}
				case "/api/search" -> {
					submit(q.getOrDefault("q", ""));
					body = "{}".getBytes(StandardCharsets.UTF_8);
				}
				default -> {
					send(s.getOutputStream(), 404, "text/plain", "?".getBytes(StandardCharsets.UTF_8));
					return;
				}
			}
			send(s.getOutputStream(), 200, type, body);
		} catch (Exception e) {
			OemerClient.LOGGER.debug("Player-Anfrage", e);
		}
	}

	/** Ein Video lässt sich nicht abspielen. Selbst gewählt = stehen bleiben und sagen warum; sonst höchstens 3 überspringen. */
	static void handleError(int code) {
		Track t = current();
		String name = t == null ? "Das Video" : "„" + t.shownTitle() + "“";
		String why = switch (code) {
			case 101, 150 -> "darf nicht eingebettet werden (der Besitzer hat es verboten) – such eine andere Version";
			case 100 -> "wurde nicht gefunden oder ist privat";
			case 153 -> "ließ sich vom Player nicht starten (YouTube-Fehler 153)";
			default -> "lässt sich nicht abspielen (YouTube-Fehler " + code + ")";
		};
		if ((code == 101 || code == 150) && t != null && hasKey() && ALT_TRIED.add(t.id)) {
			// Selbst eine passende, einbettbare Version suchen und die gesperrte ersetzen
			final Track failed = t;
			wantPlay = false;
			message = name + " darf nicht eingebettet werden – ich suche eine Version, die funktioniert …";
			EXEC.execute(() -> {
				try {
					List<Track> alts = alternatives(failed, 3);
					if (alts.isEmpty()) {
						message = name + " " + why;
						return;
					}
					Track alt = alts.get(0);
					boolean swapped = false;
					synchronized (QUEUE) {
						int i = QUEUE.indexOf(failed);
						if (i >= 0) {
							QUEUE.set(i, alt);
							swapped = i == index;
						}
					}
					if (swapped) {
						errorStreak = 0;
						startCurrent();
						message = "Original gesperrt – spiele stattdessen: " + alt.shownTitle();
					} else {
						message = name + " ersetzt durch: " + alt.shownTitle();
					}
				} catch (Exception e) {
					OemerClient.LOGGER.warn("YouTube-Ersatzsuche", e);
					message = name + " " + why;
				}
			});
			return;
		}
		if (userPicked || errorStreak >= 3) {
			wantPlay = false;
			message = name + " " + why;
			return;
		}
		errorStreak++;
		next();
		message = name + " " + why + " – übersprungen";
	}

	private static void command(Map<String, String> q) {
		String d = q.getOrDefault("do", "");
		switch (d) {
			case "toggle" -> playPause();
			case "play" -> {
				if (!wantPlay) playPause();
			}
			case "pause" -> wantPlay = false;
			case "next" -> next();
			case "prev" -> previous();
			case "jump" -> jump((int) parseLong(q.get("i")));
			case "vol" -> {
				OemerConfig.get().ytVolume = (int) Math.max(0, Math.min(100, parseLong(q.get("v"))));
				OemerConfig.save();
			}
			case "seek" -> seek(parseLong(q.get("ms")));
			case "shuffle" -> toggleShuffle();
			case "repeat" -> {
				OemerConfig.get().ytRepeat = !OemerConfig.get().ytRepeat;
				OemerConfig.save();
			}
			case "ended" -> {
				int s = (int) parseLong(q.get("seq"));
				if (s == seq && lastEndedSeq != s) {
					lastEndedSeq = s;
					next();
				}
			}
			case "error" -> {
				int s = (int) parseLong(q.get("seq"));
				int code = (int) parseLong(q.get("code"));
				if (s == seq && lastEndedSeq != s) {
					lastEndedSeq = s;
					handleError(code);
				}
			}
			case "playid" -> {
				String id = q.getOrDefault("id", "");
				if (BARE_ID.matcher(id).matches()) {
					Track t = findKnown(id);
					play(t != null ? t : new Track(id, q.getOrDefault("title", ""), q.getOrDefault("artist", "")));
				}
			}
			case "queueid" -> {
				String id = q.getOrDefault("id", "");
				if (BARE_ID.matcher(id).matches()) {
					Track t = findKnown(id);
					enqueue(t != null ? t : new Track(id, q.getOrDefault("title", ""), q.getOrDefault("artist", "")));
				}
			}
			case "like" -> {
				String id = q.getOrDefault("id", "");
				Track t = findKnown(id);
				if (t != null) toggleLibrary(t);
			}
			case "newpl" -> {
				Playlist p = createPlaylist(q.getOrDefault("name", ""));
				String id = q.getOrDefault("id", "");
				if (BARE_ID.matcher(id).matches()) {
					Track t = findKnown(id);
					addToPlaylist(p, t != null ? t : new Track(id, q.getOrDefault("title", ""), q.getOrDefault("artist", "")));
				}
			}
			case "addpl" -> {
				int pi = (int) parseLong(q.get("p"));
				List<Playlist> pls = OemerConfig.get().ytPlaylists;
				String id = q.getOrDefault("id", "");
				if (pi >= 0 && pi < pls.size() && BARE_ID.matcher(id).matches()) {
					Track t = findKnown(id);
					addToPlaylist(pls.get(pi), t != null ? t : new Track(id, q.getOrDefault("title", ""), q.getOrDefault("artist", "")));
				}
			}
			case "unqueue" -> removeFromQueue((int) parseLong(q.get("i")));
			case "clearqueue" -> clearQueue();
			case "unlib" -> {
				Track t = findKnown(q.getOrDefault("id", ""));
				if (t != null) removeFromLibrary(t);
			}
			case "unpl" -> {
				int p = (int) parseLong(q.get("p"));
				List<Playlist> pls = OemerConfig.get().ytPlaylists;
				if (p >= 0 && p < pls.size()) {
					String id = q.getOrDefault("id", "");
					pls.get(p).tracks.removeIf(x -> x.id.equals(id));
					OemerConfig.save();
				}
			}
			case "delpl" -> {
				int p = (int) parseLong(q.get("p"));
				List<Playlist> pls = OemerConfig.get().ytPlaylists;
				if (p >= 0 && p < pls.size()) deletePlaylist(pls.get(p));
			}
			case "playlib" -> playList(OemerConfig.get().ytLibrary, (int) parseLong(q.get("i")));
			case "playpl" -> {
				int p = (int) parseLong(q.get("p"));
				List<Playlist> pls = OemerConfig.get().ytPlaylists;
				if (p >= 0 && p < pls.size()) playList(pls.get(p).tracks, (int) parseLong(q.get("i")));
			}
			case "imported" -> {
				// Player-Seite hat eine Playlist ohne Schlüssel ausgelesen: ids=a,b,c
				pendingImport = "";
				String ids = q.getOrDefault("ids", "");
				String name = q.getOrDefault("name", "YouTube-Playlist");
				List<String> list = new ArrayList<>();
				for (String id : ids.split(",")) if (BARE_ID.matcher(id).matches()) list.add(id);
				EXEC.execute(() -> {
					List<Track> tracks = new ArrayList<>();
					for (String id : list) tracks.add(resolve(id));
					savePlaylist(name.isBlank() ? "YouTube-Playlist" : name, tracks);
				});
			}
			default -> {
			}
		}
	}

	private static Track findKnown(String id) {
		Track c = current();
		if (c != null && c.id.equals(id)) return c;
		for (Track t : results()) if (t.id.equals(id)) return t;
		for (Track t : OemerConfig.get().ytLibrary) if (t.id.equals(id)) return t;
		for (Playlist p : OemerConfig.get().ytPlaylists) for (Track t : p.tracks) if (t.id.equals(id)) return t;
		for (Track t : queue()) if (t.id.equals(id)) return t;
		return null;
	}

	private static Map<String, Object> state() {
		OemerConfig c = OemerConfig.get();
		Map<String, Object> m = new HashMap<>();
		Track cur = current();
		m.put("seq", seq);
		m.put("videoId", cur == null ? "" : cur.id);
		m.put("title", cur == null ? "" : cur.shownTitle());
		m.put("artist", cur == null ? "" : cur.artist);
		m.put("play", wantPlay && cur != null);
		m.put("volume", c.ytVolume);
		m.put("seekSeq", seekSeq);
		m.put("seekMs", seekMs);
		m.put("index", index);
		m.put("queue", queue());
		m.put("library", c.ytLibrary);
		m.put("playlists", c.ytPlaylists);
		m.put("results", results());
		m.put("shuffle", c.ytShuffle);
		m.put("repeat", c.ytRepeat);
		m.put("hasKey", hasKey());
		m.put("message", message);
		m.put("importList", pendingImport);
		return m;
	}

	private static long parseLong(String s) {
		try {
			return s == null ? 0 : (long) Double.parseDouble(s);
		} catch (Exception e) {
			return 0;
		}
	}

	private static Map<String, String> query(String target) {
		Map<String, String> m = new HashMap<>();
		int i = target.indexOf('?');
		if (i < 0) return m;
		for (String part : target.substring(i + 1).split("&")) {
			int e = part.indexOf('=');
			if (e <= 0) continue;
			m.put(URLDecoder.decode(part.substring(0, e), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(e + 1), StandardCharsets.UTF_8));
		}
		return m;
	}

	private static void send(OutputStream out, int code, String type, byte[] body) throws Exception {
		String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: " + type
				+ "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
		out.write(head.getBytes(StandardCharsets.UTF_8));
		out.write(body);
		out.flush();
	}
}
