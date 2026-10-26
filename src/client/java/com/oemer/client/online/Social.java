package com.oemer.client.online;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.ImageTools;
import com.oemer.client.feature.NameShare;
import com.oemer.client.feature.LooksPack;
import com.oemer.client.feature.Music;
import com.oemer.client.feature.YouTubeMusic;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Alles Soziale: Konto, Freunde, Anfragen, Chat, geteilte Skins und gemeinsames Musikhören.
 * Läuft im Hintergrund; die Oberfläche liest nur den aktuellen Stand (version() ändert sich bei jeder Änderung).
 */
public final class Social {
	private Social() {
	}

	public record Listening(String id, String title, String artist, String service, long pos, long at, boolean playing) {
	}

	public record Msg(String id, String from, String text, long ts) {
	}

	public record Request(String uid, String name, long ts) {
	}

	public static final class Friend {
		public final String uid;
		public volatile String name = "…";
		public volatile String mc = "";
		public volatile long seen;
		public volatile Listening listening;
		volatile long skinsAt;

		Friend(String uid) {
			this.uid = uid;
		}

		public boolean online() {
			return seen > 0 && serverNow() - seen < 75_000;
		}

		public boolean hasCape() {
			return FriendSkins.hasCape(mc);
		}

		public boolean hasTotem() {
			return FriendSkins.hasTotem(uid);
		}
	}

	public static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
	private static final Gson GSON = new Gson();
	private static final ScheduledExecutorService TICKER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Ömer Online");
		t.setDaemon(true);
		return t;
	});
	private static final ExecutorService ACTIONS = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "Ömer Online Aktion");
		t.setDaemon(true);
		return t;
	});

	private static volatile Firebase.Session session;
	private static volatile String username = "";
	private static volatile String status = "";
	private static volatile boolean busy;
	private static volatile long clockOffset;
	private static volatile long watchUntil;
	private static volatile boolean started;
	private static volatile boolean firstSync = true;
	private static volatile String openChat = "";
	private static final AtomicInteger VERSION = new AtomicInteger();
	private static final Map<String, Friend> FRIENDS = new ConcurrentHashMap<>();
	private static volatile List<Request> requests = List.of();
	private static final Map<String, List<Msg>> CHATS = new ConcurrentHashMap<>();
	private static final Map<String, Long> INBOX_SEEN = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> SHARING = new ConcurrentHashMap<>();
	/** Wer darf bei mir mithören? (uid → erlaubt) */
	private static final Map<String, Boolean> LISTEN_OK = new ConcurrentHashMap<>();
	/** Bei wem habe ich angefragt und warte auf die Erlaubnis? (uid → Zeitpunkt) */
	private static final Map<String, Long> LISTEN_PENDING = new ConcurrentHashMap<>();
	private static volatile String publishedSig = "";
	private static volatile boolean published = true; // es könnte noch ein alter Eintrag auf dem Server liegen
	private static volatile String skinSig = "";
	private static volatile int tickNr;

	// ------------------------------------------------------------ Abfragen

	public static boolean loggedIn() {
		return session != null && !username.isEmpty();
	}

	/** Eingeloggt, aber noch ohne Username (z. B. wenn die Anmeldung beim Anlegen abgebrochen ist). */
	public static boolean needsName() {
		return session != null && username.isEmpty();
	}

	public static String username() {
		return username;
	}

	public static String email() {
		Firebase.Session s = session;
		return s == null ? "" : s.email;
	}

	public static String status() {
		return status;
	}

	public static boolean busy() {
		return busy;
	}

	public static int version() {
		return VERSION.get();
	}

	public static long serverNow() {
		return System.currentTimeMillis() + clockOffset;
	}

	public static List<Request> requests() {
		return requests;
	}

	public static List<Friend> friends() {
		List<Friend> list = new ArrayList<>(FRIENDS.values());
		list.sort(Comparator.comparing((Friend f) -> !f.online()).thenComparing(f -> f.name.toLowerCase(Locale.ROOT)));
		return list;
	}

	public static Friend friend(String uid) {
		return FRIENDS.get(uid);
	}

	public static List<Msg> chat(String uid) {
		List<Msg> l = CHATS.get(uid);
		if (l == null) return List.of();
		synchronized (l) {
			return new ArrayList<>(l);
		}
	}

	public static String myUid() {
		Firebase.Session s = session;
		return s == null ? "" : s.uid;
	}

	public static int unread(String uid) {
		long read = OemerConfig.get().chatRead.getOrDefault(uid, 0L);
		int n = 0;
		for (Msg m : chat(uid)) {
			if (!m.from.equals(myUid()) && m.ts > read) n++;
		}
		return n;
	}

	public static int unreadTotal() {
		int n = 0;
		for (String uid : FRIENDS.keySet()) n += unread(uid);
		return n;
	}

	/** Darf dieser Freund meine Musik sehen? (Standard: ja) */
	public static boolean musicVisibleFor(String uid) {
		return SHARING.getOrDefault(uid, true);
	}

	/** Die Oberfläche meldet sich damit, solange sie Freunde zeigt: dann wird öfter aktualisiert. */
	public static void watch() {
		watchUntil = System.currentTimeMillis() + 6000;
	}

	public static void setOpenChat(String uid) {
		openChat = uid == null ? "" : uid;
	}

	private static void bump() {
		VERSION.incrementAndGet();
	}

	public static void setStatus(String s) {
		say(s);
	}

	private static void say(String s) {
		status = s;
		bump();
	}

	// ------------------------------------------------------------ Start

	private static Path accountFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("account.json");
	}

	private static void saveAccount() {
		Firebase.Session s = session;
		try {
			if (s == null) {
				Files.deleteIfExists(accountFile());
				return;
			}
			JsonObject o = new JsonObject();
			o.addProperty("email", s.email);
			o.addProperty("uid", s.uid);
			o.addProperty("username", username);
			o.addProperty("refreshToken", s.refreshToken);
			Files.createDirectories(accountFile().getParent());
			Files.writeString(accountFile(), GSON.toJson(o));
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Konto konnte nicht gespeichert werden", e);
		}
	}

	/** Beim Spielstart: gespeicherte Anmeldung wieder aufnehmen und den Hintergrunddienst starten. */
	public static synchronized void start() {
		if (started) return;
		started = true;
		TICKER.scheduleWithFixedDelay(() -> {
			try {
				tick();
			} catch (Throwable t) {
				OemerClient.LOGGER.debug("Online-Takt: {}", t.toString());
			}
		}, 3, 2, TimeUnit.SECONDS);
		ACTIONS.execute(() -> {
			try {
				if (!Files.isRegularFile(accountFile()) || !Firebase.configured()) return;
				JsonObject o = JsonParser.parseString(Files.readString(accountFile())).getAsJsonObject();
				Firebase.Session s = Firebase.refresh(o.get("uid").getAsString(), o.get("refreshToken").getAsString(), o.get("email").getAsString());
				session = s;
				username = o.has("username") ? o.get("username").getAsString() : "";
				saveAccount();
				afterLogin();
			} catch (Firebase.FirebaseException e) {
				if (!"NETZ".equals(e.code)) {
					session = null;
					saveAccount();
				}
			} catch (Exception e) {
				OemerClient.LOGGER.debug("Gespeicherte Anmeldung ging nicht: {}", e.toString());
			}
		});
	}

	private static synchronized Firebase.Session sess() throws Firebase.FirebaseException {
		Firebase.Session s = session;
		if (s == null) throw new Firebase.FirebaseException("LOGIN", "Nicht angemeldet");
		if (s.expiresAt - System.currentTimeMillis() < 300_000) {
			s = Firebase.refresh(s.uid, s.refreshToken, s.email);
			session = s;
			saveAccount();
		}
		return s;
	}

	// ---------------------------------------------------------- Konto

	public interface Done {
		void run(boolean ok);
	}

	private static void run(Done done, Action action) {
		ACTIONS.execute(() -> {
			busy = true;
			bump();
			boolean ok = false;
			try {
				action.run();
				ok = true;
			} catch (Firebase.FirebaseException e) {
				status = e.getMessage();
			} catch (Exception e) {
				status = "Fehler: " + e;
			} finally {
				busy = false;
				bump();
			}
			if (done != null) done.run(ok);
		});
	}

	private interface Action {
		void run() throws Exception;
	}

	public static void register(String email, String password, String name, Done done) {
		run(done, () -> {
			if (!Firebase.configured()) throw new Firebase.FirebaseException("CONF", "Trag zuerst die Server-Daten ein (Firebase)");
			if (!NAME.matcher(name).matches()) throw new Firebase.FirebaseException("NAME", "Username: 3–16 Zeichen, nur Buchstaben, Zahlen und _");
			if (password.length() < 6) throw new Firebase.FirebaseException("PW", "Das Passwort braucht mindestens 6 Zeichen");
			Firebase.Session s = Firebase.signUp(email.trim(), password);
			try {
				claim(s, name);
			} catch (Firebase.FirebaseException e) {
				try {
					Firebase.deleteAccount(s);
				} catch (Exception ignored) {
				}
				throw e;
			}
			adopt(s, name);
			say("Konto erstellt – willkommen, " + name + "!");
		});
	}

	/** Username belegen (1 Name = 1 Konto) und Profil anlegen. */
	private static void claim(Firebase.Session s, String name) throws Firebase.FirebaseException {
		String key = name.toLowerCase(Locale.ROOT);
		try {
			Firebase.put(s, "usernames/" + key, new JsonPrimitive(s.uid));
		} catch (Firebase.FirebaseException e) {
			if ("DENIED".equals(e.code)) throw new Firebase.FirebaseException("NAME", "Dieser Username ist schon vergeben");
			throw e;
		}
		try {
			JsonObject p = new JsonObject();
			p.addProperty("name", name);
			p.addProperty("mc", myMcUuid());
			p.add("seen", Firebase.serverTime());
			Firebase.put(s, "profiles/" + s.uid, p);
		} catch (Firebase.FirebaseException e) {
			try {
				Firebase.delete(s, "usernames/" + key);
			} catch (Exception ignored) {
			}
			throw e;
		}
	}

	public static void login(String email, String password, Done done) {
		run(done, () -> {
			if (!Firebase.configured()) throw new Firebase.FirebaseException("CONF", "Trag zuerst die Server-Daten ein (Firebase)");
			Firebase.Session s = Firebase.signIn(email.trim(), password);
			JsonElement p = Firebase.get(s, "profiles/" + s.uid + "/name");
			if (p.isJsonPrimitive()) {
				adopt(s, p.getAsString());
				say("Angemeldet als " + username);
			} else {
				session = s;
				username = "";
				saveAccount();
				say("Fast geschafft: such dir noch einen Username aus");
			}
		});
	}

	/** Für Konten ohne Username. */
	public static void claimName(String name, Done done) {
		run(done, () -> {
			if (!NAME.matcher(name).matches()) throw new Firebase.FirebaseException("NAME", "Username: 3–16 Zeichen, nur Buchstaben, Zahlen und _");
			Firebase.Session s = sess();
			claim(s, name);
			adopt(s, name);
			say("Willkommen, " + name + "!");
		});
	}

	public static void resetPassword(String email, Done done) {
		run(done, () -> {
			if (!Firebase.configured()) throw new Firebase.FirebaseException("CONF", "Trag zuerst die Server-Daten ein (Firebase)");
			if (email.isBlank()) throw new Firebase.FirebaseException("MAIL", "Schreib oben deine E-Mail rein");
			Firebase.sendPasswordReset(email.trim());
			say("E-Mail zum Zurücksetzen ist unterwegs (auch im Spam nachsehen)");
		});
	}

	public static void logout() {
		ACTIONS.execute(() -> {
			try {
				Firebase.Session s = session;
				if (s != null && loggedIn()) {
					try {
						Firebase.delete(s, "listening/" + s.uid);
					} catch (Exception ignored) {
					}
				}
			} finally {
				session = null;
				username = "";
				FRIENDS.clear();
				CHATS.clear();
				INBOX_SEEN.clear();
				SHARING.clear();
				LISTEN_OK.clear();
				LISTEN_PENDING.clear();
				requests = List.of();
				publishedSig = "";
				skinSig = "";
				firstSync = true;
				FriendSkins.clear();
				NameShare.clear();
				saveAccount();
				say("Abgemeldet");
			}
		});
	}

	private static void adopt(Firebase.Session s, String name) {
		session = s;
		username = name;
		saveAccount();
		afterLogin();
	}

	private static void afterLogin() {
		firstSync = true;
		publishedSig = "";
		published = true;
		skinSig = "";
		tickNr = 0;
		bump();
		ACTIONS.execute(() -> {
			try {
				heartbeat();
				loadSharing();
				refreshFriends();
				pollInbox();
				pollListening();
			} catch (Exception e) {
				OemerClient.LOGGER.debug("Erster Abgleich: {}", e.toString());
			}
			syncSkinsLater();
		});
	}

	private static String myMcUuid() {
		try {
			return Minecraft.getInstance().getUser().getProfileId().toString();
		} catch (Throwable t) {
			return "";
		}
	}

	// ---------------------------------------------------------- Freunde

	/** Anfrage per Username schicken (oder die Anfrage des anderen annehmen, wenn er dich schon gefragt hat). */
	public static void addFriend(String rawName, Done done) {
		run(done, () -> {
			Firebase.Session s = sess();
			String name = rawName.trim();
			if (!NAME.matcher(name).matches()) throw new Firebase.FirebaseException("NAME", "So ein Username kann es nicht geben");
			JsonElement e = Firebase.get(s, "usernames/" + name.toLowerCase(Locale.ROOT));
			if (!e.isJsonPrimitive()) throw new Firebase.FirebaseException("NONE", "Den Username gibt es nicht");
			String uid = e.getAsString();
			if (uid.equals(s.uid)) throw new Firebase.FirebaseException("SELF", "Das bist du selbst");
			if (FRIENDS.containsKey(uid)) throw new Firebase.FirebaseException("DUP", "Ihr seid schon befreundet");
			for (Request r : requests) {
				if (r.uid.equals(uid)) {
					acceptNow(s, uid);
					say(r.name + " ist jetzt dein Freund");
					return;
				}
			}
			JsonObject o = new JsonObject();
			o.addProperty("name", username);
			o.add("ts", Firebase.serverTime());
			Firebase.put(s, "requests/" + uid + "/" + s.uid, o);
			say("Anfrage an " + name + " geschickt");
		});
	}

	private static void acceptNow(Firebase.Session s, String uid) throws Firebase.FirebaseException {
		JsonObject u = new JsonObject();
		u.addProperty("friends/" + s.uid + "/" + uid, true);
		u.addProperty("friends/" + uid + "/" + s.uid, true);
		u.add("requests/" + s.uid + "/" + uid, com.google.gson.JsonNull.INSTANCE);
		Firebase.multi(s, u);
		refreshFriends();
	}

	public static void accept(String uid, Done done) {
		run(done, () -> {
			acceptNow(sess(), uid);
			say("Neuer Freund!");
		});
	}

	public static void decline(String uid, Done done) {
		run(done, () -> {
			Firebase.delete(sess(), "requests/" + sess().uid + "/" + uid);
			requests = requests.stream().filter(r -> !r.uid.equals(uid)).toList();
			say("Anfrage abgelehnt");
		});
	}

	public static void removeFriend(String uid, Done done) {
		run(done, () -> {
			Firebase.Session s = sess();
			JsonObject u = new JsonObject();
			u.add("friends/" + s.uid + "/" + uid, com.google.gson.JsonNull.INSTANCE);
			u.add("friends/" + uid + "/" + s.uid, com.google.gson.JsonNull.INSTANCE);
			Firebase.multi(s, u);
			FRIENDS.remove(uid);
			say("Freund entfernt");
		});
	}

	/** Meine Musik für genau diesen Freund zeigen oder verstecken. */
	public static void setMusicFor(String uid, boolean visible, Done done) {
		SHARING.put(uid, visible);
		bump();
		run(done, () -> {
			Firebase.put(sess(), "sharing/" + sess().uid + "/" + uid, new com.google.gson.JsonPrimitive(visible));
			publishedSig = "";
		});
	}

	private static void loadSharing() throws Firebase.FirebaseException {
		Firebase.Session s = sess();
		JsonElement e = Firebase.get(s, "sharing/" + s.uid);
		if (e.isJsonObject()) {
			for (var en : e.getAsJsonObject().entrySet()) {
				if (en.getValue().isJsonPrimitive()) SHARING.put(en.getKey(), en.getValue().getAsBoolean());
			}
		}
		JsonElement la = Firebase.get(s, "listenAllow/" + s.uid);
		if (la.isJsonObject()) {
			for (var en : la.getAsJsonObject().entrySet()) {
				if (en.getValue().isJsonPrimitive()) LISTEN_OK.put(en.getKey(), en.getValue().getAsBoolean());
			}
		}
	}

	// ---------------------------------------------------------- Mithören nur mit Erlaubnis

	/** Steuer-Nachrichten im Chat (werden im Chat als Hinweis gezeigt, nicht als Text). */
	public static final String LA_REQ = "~LA:REQ~", LA_OK = "~LA:OK~", LA_NO = "~LA:NO~";

	public static boolean isControl(String text) {
		return LA_REQ.equals(text) || LA_OK.equals(text) || LA_NO.equals(text);
	}

	/** Lesbarer Text für eine Chat-Nachricht (Steuer-Nachrichten werden übersetzt). */
	public static String shown(Msg m, String friendName) {
		boolean mine = m.from.equals(myUid());
		return switch (m.text) {
			case LA_REQ -> mine ? "♪ Du hast angefragt, bei " + friendName + " mitzuhören" : "♪ " + friendName + " möchte bei dir mithören";
			case LA_OK -> mine ? "✔ Du erlaubst " + friendName + " das Mithören" : "✔ " + friendName + " erlaubt dir das Mithören";
			case LA_NO -> mine ? "✕ Du hast das Mithören bei dir beendet" : "✕ " + friendName + " erlaubt dir das Mithören nicht";
			default -> m.text;
		};
	}

	/** Darf dieser Freund bei mir mithören? */
	public static boolean mayListen(String uid) {
		return LISTEN_OK.getOrDefault(uid, false);
	}

	/** Offene Anfrage dieses Freundes an mich (letzte Steuer-Nachricht ist von ihm eine Anfrage)? */
	public static boolean incomingListenRequest(String uid) {
		if (mayListen(uid)) return false;
		List<Msg> l = chat(uid);
		String me = myUid();
		synchronized (l) {
			for (int i = l.size() - 1; i >= 0; i--) {
				Msg m = l.get(i);
				if (!isControl(m.text)) continue;
				return LA_REQ.equals(m.text) && !m.from.equals(me);
			}
		}
		return false;
	}

	/** Erlauben (true) oder Widerrufen/Ablehnen (false). Der Freund bekommt eine Nachricht. */
	public static void allowListen(String uid, boolean allow, Done done) {
		LISTEN_OK.put(uid, allow);
		bump();
		run(done, () -> {
			Firebase.Session s = sess();
			Firebase.put(s, "listenAllow/" + s.uid + "/" + uid, new com.google.gson.JsonPrimitive(allow));
			sendRaw(s, uid, allow ? LA_OK : LA_NO);
			say(allow ? "Mithören erlaubt" : "Mithören beendet");
		});
	}

	private static void refreshFriends() throws Firebase.FirebaseException {
		Firebase.Session s = sess();
		JsonElement fe = Firebase.get(s, "friends/" + s.uid);
		List<String> ids = new ArrayList<>();
		if (fe.isJsonObject()) ids.addAll(fe.getAsJsonObject().keySet());
		StringBuilder sigBefore = new StringBuilder();
		for (Friend f : FRIENDS.values()) sigBefore.append(f.uid).append(f.name).append(f.mc);
		FRIENDS.keySet().removeIf(id -> !ids.contains(id));
		for (String id : ids) {
			Friend f = FRIENDS.computeIfAbsent(id, Friend::new);
			try {
				JsonElement p = Firebase.get(s, "profiles/" + id);
				if (p.isJsonObject()) {
					JsonObject o = p.getAsJsonObject();
					if (o.has("name")) f.name = o.get("name").getAsString();
					if (o.has("mc")) f.mc = o.get("mc").getAsString();
					if (o.has("seen") && o.get("seen").isJsonPrimitive()) f.seen = o.get("seen").getAsLong();
				}
			} catch (Firebase.FirebaseException ignored) {
			}
		}
		JsonElement re = Firebase.get(s, "requests/" + s.uid);
		List<Request> rs = new ArrayList<>();
		if (re.isJsonObject()) {
			for (var en : re.getAsJsonObject().entrySet()) {
				if (!en.getValue().isJsonObject()) continue;
				JsonObject o = en.getValue().getAsJsonObject();
				rs.add(new Request(en.getKey(), o.has("name") ? o.get("name").getAsString() : "?", o.has("ts") ? o.get("ts").getAsLong() : 0));
			}
		}
		rs.sort(Comparator.comparingLong(Request::ts));
		List<Request> before = requests;
		requests = rs;
		StringBuilder sigAfter = new StringBuilder();
		for (Friend f : FRIENDS.values()) sigAfter.append(f.uid).append(f.name).append(f.mc);
		boolean changed = rs.size() != before.size() || !sigBefore.toString().equals(sigAfter.toString());
		if (!firstSync && rs.size() > before.size() && OemerConfig.get().chatToasts) {
			Request r = rs.get(rs.size() - 1);
			notice("§b" + r.name + "§r möchte dein Freund sein (Ömer Client → Freunde)");
		}
		if (changed) bump();
	}

	private static void heartbeat() throws Firebase.FirebaseException {
		Firebase.Session s = sess();
		JsonObject o = new JsonObject();
		o.add("seen", Firebase.serverTime());
		long t0 = System.currentTimeMillis();
		JsonElement r = Firebase.patchResult(s, "profiles/" + s.uid, o);
		long t1 = System.currentTimeMillis();
		if (r.isJsonObject() && r.getAsJsonObject().has("seen")) {
			clockOffset = r.getAsJsonObject().get("seen").getAsLong() - (t0 + t1) / 2;
		}
	}

	// ---------------------------------------------------------- Musik

	private static void pollListening() {
		Firebase.Session s;
		try {
			s = sess();
		} catch (Exception e) {
			return;
		}
		boolean changed = false;
		for (Friend f : FRIENDS.values()) {
			try {
				JsonElement e = Firebase.get(s, "listening/" + f.uid);
				Listening now = null;
				if (e.isJsonObject()) {
					JsonObject o = e.getAsJsonObject();
					now = new Listening(str(o, "id"), str(o, "title"), str(o, "artist"), str(o, "service"),
							lng(o, "pos"), lng(o, "at"), o.has("playing") && o.get("playing").getAsBoolean());
				}
				if (!java.util.Objects.equals(now, f.listening)) {
					f.listening = now;
					changed = true;
				}
			} catch (Firebase.FirebaseException e) {
				if (f.listening != null) {
					f.listening = null;
					changed = true;
				}
			}
		}
		if (changed) bump();
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
	}

	private static long lng(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsLong() : 0;
	}

	/** Welche Stelle im Song läuft beim Freund gerade? */
	public static long positionOf(Listening l) {
		long p = l.pos;
		if (l.playing) p += Math.max(0, serverNow() - l.at);
		return p;
	}

	private static void publishListening() throws Firebase.FirebaseException {
		Firebase.Session s = sess();
		OemerConfig c = OemerConfig.get();
		Music.Now now = c.shareMusic ? Music.now() : null;
		YouTubeMusic.Track yt = Music.youtube() ? YouTubeMusic.current() : null;
		if (now == null) {
			if (published) {
				Firebase.delete(s, "listening/" + s.uid);
				published = false;
			}
			publishedSig = "";
			return;
		}
		String id = yt != null ? yt.id : "";
		String sig = now.service() + "|" + id + "|" + now.title() + "|" + now.playing();
		boolean drift = sig.equals(publishedSig) && tickNr % 6 != 0; // alle ~12 s die Stelle auffrischen
		if (drift) return;
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("title", cut(now.title(), 120));
		o.addProperty("artist", cut(now.artist(), 120));
		o.addProperty("service", now.service());
		o.addProperty("pos", now.progressMs());
		o.add("at", Firebase.serverTime());
		o.addProperty("playing", now.playing());
		Firebase.put(s, "listening/" + s.uid, o);
		publishedSig = sig;
		published = true;
	}

	private static String cut(String s, int n) {
		return s == null ? "" : s.length() > n ? s.substring(0, n) : s;
	}

	/** Beim Freund mithören: sein Song startet bei dir, an derselben Stelle. */
	public static void listenAlong(String uid, Done done) {
		run(done, () -> {
			Firebase.Session s = sess();
			Friend fr = FRIENDS.get(uid);
			String who = fr == null ? "Dein Freund" : fr.name;
			JsonElement ok = Firebase.get(s, "listenAllow/" + uid + "/" + s.uid);
			if (!(ok.isJsonPrimitive() && ok.getAsBoolean())) {
				long last = LISTEN_PENDING.getOrDefault(uid, 0L);
				if (serverNow() - last > 120_000) {
					sendRaw(s, uid, LA_REQ);
					LISTEN_PENDING.put(uid, serverNow());
					throw new Firebase.FirebaseException("ASK", "Anfrage an " + who + " geschickt – er muss das Mithören erst erlauben");
				}
				throw new Firebase.FirebaseException("WAIT", who + " hat noch nicht geantwortet (Anfrage ist schon raus)");
			}
			LISTEN_PENDING.remove(uid);
			JsonElement e = Firebase.get(s, "listening/" + uid);
			if (!e.isJsonObject()) throw new Firebase.FirebaseException("NONE", "Dein Freund hört gerade nichts (oder teilt es nicht)");
			JsonObject o = e.getAsJsonObject();
			Listening l = new Listening(str(o, "id"), str(o, "title"), str(o, "artist"), str(o, "service"),
					lng(o, "pos"), lng(o, "at"), o.has("playing") && o.get("playing").getAsBoolean());
			if (l.id.isEmpty() || !"YouTube".equals(l.service)) {
				throw new Firebase.FirebaseException("SVC", "Dieser Song läuft über " + (l.service.isEmpty() ? "Spotify" : l.service) + " – Mitlauschen geht nur bei YouTube");
			}
			OemerConfig c = OemerConfig.get();
			c.musicService = 0;
			OemerConfig.save();
			Minecraft.getInstance().execute(() -> YouTubeMusic.play(new YouTubeMusic.Track(l.id, l.title, l.artist)));
			say("Du hörst jetzt mit: " + l.title);
			// Sobald der Player läuft, an die richtige Stelle springen
			ACTIONS.execute(() -> {
				for (int i = 0; i < 12; i++) {
					try {
						Thread.sleep(1500);
					} catch (InterruptedException ex) {
						return;
					}
					YouTubeMusic.Track cur = YouTubeMusic.current();
					if (cur != null && cur.id.equals(l.id) && YouTubeMusic.pageConnected() && YouTubeMusic.duration() > 0) {
						long target = positionOf(l);
						if (target > 2000) YouTubeMusic.seek(target);
						return;
					}
				}
			});
		});
	}

	// ---------------------------------------------------------- Chat

	public static void send(String uid, String text, Done done) {
		String t = text.strip();
		if (t.isEmpty() || isControl(t)) return;
		if (t.length() > 300) t = t.substring(0, 300);
		final String msg = t;
		run(done, () -> {
			sendRaw(sess(), uid, msg);
			status = "";
		});
	}

	private static void sendRaw(Firebase.Session s, String uid, String msg) throws Firebase.FirebaseException {
		long ts = serverNow();
		String id = "m" + ts + Integer.toString((int) (Math.random() * 36 * 36 * 36), 36);
		JsonObject m = new JsonObject();
		m.addProperty("from", s.uid);
		m.addProperty("text", msg);
		m.addProperty("ts", ts);
		JsonObject u = new JsonObject();
		u.add("messages/" + s.uid + "/" + uid + "/" + id, m);
		u.add("messages/" + uid + "/" + s.uid + "/" + id, m);
		u.addProperty("inbox/" + uid + "/" + s.uid, ts);
		Firebase.multi(s, u);
		List<Msg> l = CHATS.computeIfAbsent(uid, k -> Collections.synchronizedList(new ArrayList<>()));
		l.add(new Msg(id, s.uid, msg, ts));
		bump();
	}

	/** Chat mit diesem Freund ist offen: alles als gelesen markieren und frisch laden. */
	public static void markRead(String uid) {
		List<Msg> l = chat(uid);
		long newest = 0;
		for (Msg m : l) newest = Math.max(newest, m.ts);
		OemerConfig c = OemerConfig.get();
		if (newest > c.chatRead.getOrDefault(uid, 0L)) {
			c.chatRead.put(uid, newest);
			OemerConfig.save();
			bump();
		}
	}

	public static void loadChat(String uid) {
		ACTIONS.execute(() -> {
			try {
				fetchChat(sess(), uid);
			} catch (Exception ignored) {
			}
		});
	}

	private static void fetchChat(Firebase.Session s, String uid) throws Firebase.FirebaseException {
		JsonElement e = Firebase.get(s, "messages/" + s.uid + "/" + uid, "orderBy=%22ts%22&limitToLast=60");
		List<Msg> list = new ArrayList<>();
		if (e.isJsonObject()) {
			for (var en : e.getAsJsonObject().entrySet()) {
				if (!en.getValue().isJsonObject()) continue;
				JsonObject o = en.getValue().getAsJsonObject();
				list.add(new Msg(en.getKey(), str(o, "from"), str(o, "text"), lng(o, "ts")));
			}
		}
		list.sort(Comparator.comparingLong(Msg::ts));
		List<Msg> old = CHATS.get(uid);
		int oldN = old == null ? 0 : old.size();
		java.util.Set<String> known = new java.util.HashSet<>();
		if (old != null) synchronized (old) {
			for (Msg m : old) known.add(m.id);
		}
		CHATS.put(uid, Collections.synchronizedList(list));
		bump();
		if (!firstSync) {
			Friend f = FRIENDS.get(uid);
			for (Msg m : list) {
				if (known.contains(m.id) || m.from.equals(s.uid) || f == null) continue;
				if (LA_REQ.equals(m.text) && !mayListen(uid)) {
					notice("§e" + f.name + "§r möchte bei dir mithören – Ömer Client → Freunde → Chat, dort erlauben oder ablehnen");
				} else if (LA_OK.equals(m.text) && LISTEN_PENDING.containsKey(uid)) {
					notice("§a" + f.name + "§r hat das Mithören erlaubt");
					listenAlong(uid, null);
				} else if (LA_NO.equals(m.text)) {
					LISTEN_PENDING.remove(uid);
					notice("§c" + f.name + "§r erlaubt das Mithören nicht");
				} else if (OemerConfig.get().chatToasts && !uid.equals(openChat) && !isControl(m.text)) {
					notice("§b" + f.name + "§r: " + m.text);
				}
			}
		}
		if (uid.equals(openChat)) markRead(uid);
	}

	private static void pollInbox() throws Firebase.FirebaseException {
		Firebase.Session s = sess();
		JsonElement e = Firebase.get(s, "inbox/" + s.uid);
		if (!e.isJsonObject()) return;
		for (var en : e.getAsJsonObject().entrySet()) {
			if (!en.getValue().isJsonPrimitive() || !FRIENDS.containsKey(en.getKey())) continue;
			long ts = en.getValue().getAsLong();
			if (ts > INBOX_SEEN.getOrDefault(en.getKey(), 0L)) {
				INBOX_SEEN.put(en.getKey(), ts);
				fetchChat(s, en.getKey());
			}
		}
	}

	/** Kleine Benachrichtigung oben rechts im Spiel (abschaltbar über "Benachrichtigungen"). */
	private static void notice(String text) {
		if (!OemerConfig.get().chatToasts) return;
		Notify.push(text);
	}

	// ---------------------------------------------------------- Skins

	/** Eigenes Cape/Totem hochladen (wird auf dem Spiel-Thread vorbereitet, weil Texturen dort leben). */
	public static void syncSkinsLater() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			try {
				if (!loggedIn()) return;
				OemerConfig c = OemerConfig.get();
				String cape = null, totem = null;
				int capeFrames = 1, capeMs = 100;
				if (c.shareSkins) {
					if (c.customCape) {
						CapeManager.Shared anim = CapeManager.sharedAnimation();
						if (anim != null) {
							cape = Base64.getEncoder().encodeToString(anim.png());
							capeFrames = anim.frames();
							capeMs = anim.ms();
						} else {
							BufferedImage img = CapeManager.previewImage();
							if (img != null) cape = Base64.getEncoder().encodeToString(ImageTools.toPng(img));
							if (cape != null && cape.length() > 55_000) cape = null;
						}
					}
					if (c.customTotem) {
						BufferedImage img = LooksPack.totemImage();
						String b = Base64.getEncoder().encodeToString(ImageTools.toPng(ImageTools.squareContain(img, 96)));
						if (b.length() > 110_000) b = Base64.getEncoder().encodeToString(ImageTools.toPng(ImageTools.squareContain(img, 64)));
						totem = b.length() > 110_000 ? null : b;
					}
				}
				final String fc = cape, ft = totem;
				final int ff = capeFrames, fms = capeMs;
				final String fn = NameShare.ownJson();
				byte[] lp = fn == null ? null : NameShare.ownPng();
				final String fl = lp == null ? null : Base64.getEncoder().encodeToString(lp);
				String sig = (fc == null ? "-" : Integer.toHexString(fc.hashCode()) + fc.length() + "x" + ff) + "/" + (ft == null ? "-" : Integer.toHexString(ft.hashCode()) + ft.length())
						+ "/" + (fn == null ? "-" : Integer.toHexString(fn.hashCode())) + "/" + (fl == null ? "-" : Integer.toHexString(fl.hashCode()) + fl.length());
				if (sig.equals(skinSig)) return;
				ACTIONS.execute(() -> {
					try {
						Firebase.Session s = sess();
						if (fc == null && ft == null && fn == null && fl == null) {
							Firebase.delete(s, "skins/" + s.uid);
						} else {
							JsonObject o = new JsonObject();
							if (fc != null) {
								o.addProperty("cape", fc);
								if (ff > 1) {
									o.addProperty("capeFrames", ff);
									o.addProperty("capeMs", fms);
								}
							}
							if (ft != null) o.addProperty("totem", ft);
							if (fn != null) o.addProperty("name", fn);
							if (fl != null) o.addProperty("logo", fl);
							Firebase.put(s, "skins/" + s.uid, o);
						}
						skinSig = sig;
					} catch (Exception e) {
						OemerClient.LOGGER.debug("Skins hochladen: {}", e.toString());
					}
				});
			} catch (Throwable t) {
				OemerClient.LOGGER.debug("Skins vorbereiten: {}", t.toString());
			}
		});
	}

	private static void fetchSkins(boolean force) {
		Firebase.Session s;
		try {
			s = sess();
		} catch (Exception e) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Friend f : FRIENDS.values()) {
			if (!force && now - f.skinsAt < 60_000) continue;
			f.skinsAt = now;
			try {
				JsonElement e = Firebase.get(s, "skins/" + f.uid);
				byte[] cape = null, totem = null, logo = null;
				int capeFrames = 1, capeMs = 100;
				String nameJson = null;
				if (e.isJsonObject()) {
					JsonObject o = e.getAsJsonObject();
					if (o.has("cape")) cape = Base64.getDecoder().decode(o.get("cape").getAsString());
					if (o.has("capeFrames")) capeFrames = o.get("capeFrames").getAsInt();
					if (o.has("capeMs")) capeMs = o.get("capeMs").getAsInt();
					if (o.has("totem")) totem = Base64.getDecoder().decode(o.get("totem").getAsString());
					if (o.has("name")) nameJson = o.get("name").getAsString();
					if (o.has("logo")) logo = Base64.getDecoder().decode(o.get("logo").getAsString());
				}
				FriendSkins.update(f.uid, f.mc, cape, capeFrames, capeMs, totem);
				NameShare.setFriend(f.mc, nameJson, logo);
				bump();
			} catch (Exception ignored) {
			}
		}
	}

	/** Für den Test: alles sofort abgleichen. */
	public static void syncNow() {
		try {
			heartbeat();
			refreshFriends();
			pollInbox();
			pollListening();
			fetchSkins(true);
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Abgleich: {}", e.toString());
		}
	}

	public static void publishNow() {
		try {
			publishListening();
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Musik teilen: {}", e.toString());
		}
	}

	// ---------------------------------------------------------- Takt

	private static void tick() throws Exception {
		if (!loggedIn()) return;
		int n = tickNr++;
		try {
			publishListening();
		} catch (Firebase.FirebaseException e) {
			if ("LOGIN".equals(e.code)) return;
		}
		pollInbox();
		if (n % 15 == 0) heartbeat();
		if (n % 5 == 1 || (System.currentTimeMillis() < watchUntil && n % 3 == 0)) refreshFriends();
		boolean watching = System.currentTimeMillis() < watchUntil;
		if (watching ? n % 2 == 0 : n % 15 == 7) pollListening();
		if (n % 5 == 2) fetchSkins(false);
		if (n % 15 == 4) {
			firstSync = false;
			syncSkinsLater();
		}
		if (n == 3) firstSync = false;
	}
}
