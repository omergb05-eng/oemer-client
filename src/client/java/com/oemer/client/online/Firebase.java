package com.oemer.client.online;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oemer.client.OemerClient;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Dünner Firebase-Client über die offiziellen REST-Schnittstellen:
 * Anmeldung (E-Mail + Passwort) und Realtime Database. Im Test läuft er gegen den Firebase-Emulator.
 */
public final class Firebase {
	private Firebase() {
	}

	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL).build();

	/** Eingeloggte Sitzung. */
	public static final class Session {
		public final String uid;
		public volatile String idToken;
		public final String refreshToken;
		public volatile long expiresAt;
		public final String email;

		Session(String uid, String idToken, String refreshToken, long expiresAt, String email) {
			this.uid = uid;
			this.idToken = idToken;
			this.refreshToken = refreshToken;
			this.expiresAt = expiresAt;
			this.email = email;
		}
	}

	public static class FirebaseException extends Exception {
		public final String code;

		public FirebaseException(String code, String message) {
			super(message);
			this.code = code;
		}
	}

	// ----------------------------------------------------------- Einstellungen

	private static String apiKey = "";
	private static String dbUrl = "";
	private static boolean loaded;

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("firebase.json");
	}

	/** Server-Werte (Web-API-Schlüssel + Datenbank-Adresse) – im Test zeigt alles auf den Emulator. */
	private static synchronized void load() {
		if (loaded) return;
		loaded = true;
		try {
			if (Files.isRegularFile(file())) {
				JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
				apiKey = o.has("apiKey") ? o.get("apiKey").getAsString() : "";
				dbUrl = o.has("databaseUrl") ? o.get("databaseUrl").getAsString() : "";
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("firebase.json konnte nicht gelesen werden", e);
		}
		if (apiKey.isEmpty()) apiKey = OnlineDefaults.API_KEY;
		if (dbUrl.isEmpty()) dbUrl = OnlineDefaults.DATABASE_URL;
	}

	public static synchronized void setServer(String key, String url) {
		load();
		apiKey = key == null ? "" : key.trim();
		dbUrl = url == null ? "" : url.trim();
		try {
			Files.createDirectories(file().getParent());
			JsonObject o = new JsonObject();
			o.addProperty("apiKey", apiKey);
			o.addProperty("databaseUrl", dbUrl);
			Files.writeString(file(), GSON.toJson(o));
		} catch (Exception e) {
			OemerClient.LOGGER.warn("firebase.json konnte nicht gespeichert werden", e);
		}
	}

	public static String apiKey() {
		load();
		return emulator() != null ? "fake-key" : apiKey;
	}

	public static String databaseUrl() {
		load();
		return dbUrl;
	}

	/** Host des Firebase-Emulators (nur für Tests), sonst null. */
	public static String emulator() {
		return System.getProperty("oemer.firebase.emulator");
	}

	public static boolean configured() {
		load();
		return emulator() != null || (!apiKey.isEmpty() && !dbUrl.isEmpty());
	}

	private static String authBase() {
		return emulator() != null ? "http://" + emulator() + ":9099/identitytoolkit.googleapis.com/v1/accounts:" : "https://identitytoolkit.googleapis.com/v1/accounts:";
	}

	private static String tokenUrl() {
		return emulator() != null ? "http://" + emulator() + ":9099/securetoken.googleapis.com/v1/token?key=fake-key"
				: "https://securetoken.googleapis.com/v1/token?key=" + enc(apiKey());
	}

	private static String dbBase() {
		if (emulator() != null) return "http://" + emulator() + ":9000";
		String u = databaseUrl();
		while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
		return u;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	// ----------------------------------------------------------------- Anmeldung

	private static JsonObject post(String url, JsonObject body) throws FirebaseException {
		try {
			HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build(),
					HttpResponse.BodyHandlers.ofString());
			JsonObject o = r.body().isEmpty() ? new JsonObject() : JsonParser.parseString(r.body()).getAsJsonObject();
			if (r.statusCode() >= 400) {
				String code = "UNBEKANNT";
				try {
					code = o.getAsJsonObject("error").get("message").getAsString();
				} catch (Exception ignored) {
				}
				throw new FirebaseException(code, translate(code));
			}
			return o;
		} catch (FirebaseException e) {
			throw e;
		} catch (Exception e) {
			throw new FirebaseException("NETZ", "Keine Verbindung zum Server");
		}
	}

	private static String translate(String code) {
		String c = code.split(" ")[0].split(":")[0];
		return switch (c) {
			case "EMAIL_EXISTS" -> "Zu dieser E-Mail gibt es schon ein Konto";
			case "INVALID_EMAIL", "MISSING_EMAIL" -> "Die E-Mail sieht nicht richtig aus";
			case "WEAK_PASSWORD", "MISSING_PASSWORD" -> "Das Passwort ist zu kurz (mindestens 6 Zeichen)";
			case "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND" -> "E-Mail oder Passwort stimmt nicht";
			case "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Zu viele Versuche – warte kurz";
			case "USER_DISABLED" -> "Dieses Konto ist gesperrt";
			case "OPERATION_NOT_ALLOWED" -> "E-Mail/Passwort-Anmeldung ist im Firebase-Projekt nicht eingeschaltet";
			case "API_KEY_INVALID", "INVALID_API_KEY" -> "Der Web-API-Schlüssel stimmt nicht";
			default -> "Fehler: " + code;
		};
	}

	public static Session signUp(String email, String password) throws FirebaseException {
		JsonObject b = new JsonObject();
		b.addProperty("email", email);
		b.addProperty("password", password);
		b.addProperty("returnSecureToken", true);
		return session(post(authBase() + "signUp?key=" + enc(apiKey()), b), email);
	}

	public static Session signIn(String email, String password) throws FirebaseException {
		JsonObject b = new JsonObject();
		b.addProperty("email", email);
		b.addProperty("password", password);
		b.addProperty("returnSecureToken", true);
		return session(post(authBase() + "signInWithPassword?key=" + enc(apiKey()), b), email);
	}

	private static Session session(JsonObject o, String email) {
		long exp = System.currentTimeMillis() + Long.parseLong(o.has("expiresIn") ? o.get("expiresIn").getAsString() : "3600") * 1000L;
		return new Session(o.get("localId").getAsString(), o.get("idToken").getAsString(), o.get("refreshToken").getAsString(), exp, email);
	}

	/** Neuen Zugangsschlüssel holen, wenn der alte (fast) abgelaufen ist. */
	public static Session refresh(String uid, String refreshToken, String email) throws FirebaseException {
		try {
			String form = "grant_type=refresh_token&refresh_token=" + enc(refreshToken);
			HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(tokenUrl())).timeout(Duration.ofSeconds(12))
					.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
					HttpResponse.BodyHandlers.ofString());
			JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
			if (r.statusCode() >= 400) throw new FirebaseException("TOKEN", "Bitte neu anmelden");
			long exp = System.currentTimeMillis() + Long.parseLong(o.get("expires_in").getAsString()) * 1000L;
			return new Session(uid, o.get("id_token").getAsString(), o.get("refresh_token").getAsString(), exp, email);
		} catch (FirebaseException e) {
			throw e;
		} catch (Exception e) {
			throw new FirebaseException("NETZ", "Keine Verbindung zum Server");
		}
	}

	public static void sendPasswordReset(String email) throws FirebaseException {
		JsonObject b = new JsonObject();
		b.addProperty("requestType", "PASSWORD_RESET");
		b.addProperty("email", email);
		post(authBase() + "sendOobCode?key=" + enc(apiKey()), b);
	}

	public static void deleteAccount(Session s) throws FirebaseException {
		JsonObject b = new JsonObject();
		b.addProperty("idToken", s.idToken);
		post(authBase() + "delete?key=" + enc(apiKey()), b);
	}

	// ------------------------------------------------------------ Datenbank

	/** Zeitstempel vom Server: {".sv":"timestamp"}. */
	public static JsonObject serverTime() {
		JsonObject o = new JsonObject();
		o.addProperty(".sv", "timestamp");
		return o;
	}

	public static JsonElement get(Session s, String path) throws FirebaseException {
		return db("GET", s, path, "", null);
	}

	public static JsonElement get(Session s, String path, String query) throws FirebaseException {
		return db("GET", s, path, query, null);
	}

	public static void put(Session s, String path, JsonElement value) throws FirebaseException {
		db("PUT", s, path, "", value);
	}

	/** Mehrere Pfade auf einmal (alles oder nichts). Schlüssel sind Pfade ab Wurzel, null = löschen. */
	public static void multi(Session s, JsonObject updates) throws FirebaseException {
		db("PATCH", s, "", "", updates);
	}

	public static void patch(Session s, String path, JsonObject value) throws FirebaseException {
		db("PATCH", s, path, "", value);
	}

	/** Wie patch, gibt aber zurück, was der Server wirklich gespeichert hat (mit echter Serverzeit). */
	public static JsonElement patchResult(Session s, String path, JsonObject value) throws FirebaseException {
		return db("PATCH", s, path, "", value);
	}

	public static void delete(Session s, String path) throws FirebaseException {
		db("DELETE", s, path, "", null);
	}

	private static JsonElement db(String method, Session s, String path, String query, JsonElement body) throws FirebaseException {
		StringBuilder url = new StringBuilder(dbBase()).append('/').append(path).append(".json?");
		if (emulator() != null) url.append("ns=demo-oemer-default-rtdb&");
		if (s != null) url.append("auth=").append(enc(s.idToken)).append('&');
		url.append(query);
		try {
			HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url.toString())).timeout(Duration.ofSeconds(12));
			if (body != null) rb.header("Content-Type", "application/json");
			rb.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
			HttpResponse<String> r = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() == 401 || r.statusCode() == 403) throw new FirebaseException("DENIED", "Nicht erlaubt");
			if (r.statusCode() >= 400) throw new FirebaseException("HTTP" + r.statusCode(), "Server-Fehler " + r.statusCode());
			return r.body() == null || r.body().isBlank() ? JsonNull.INSTANCE : JsonParser.parseString(r.body());
		} catch (FirebaseException e) {
			throw e;
		} catch (Exception e) {
			throw new FirebaseException("NETZ", "Keine Verbindung zum Server");
		}
	}
}
