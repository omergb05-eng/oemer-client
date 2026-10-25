package com.oemer.client.test;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.FreeCamera;
import com.oemer.client.feature.Freecam;
import com.oemer.client.feature.ImageTools;
import com.oemer.client.feature.Looks;
import com.oemer.client.feature.LooksPack;
import com.oemer.client.feature.MaterialList;
import com.oemer.client.feature.NameStyle;
import com.oemer.client.feature.OwnFiles;
import com.oemer.client.feature.Sounds;
import com.oemer.client.gui.CapeEditorScreen;
import com.oemer.client.gui.MaterialListScreen;
import com.oemer.client.gui.OmerScreen;
import com.oemer.client.gui.SpotifyScreen;
import com.oemer.client.feature.Crosshair;
import com.oemer.client.feature.CustomCosmetics;
import com.oemer.client.feature.Spotify;
import com.oemer.client.feature.ChestMarker;
import com.oemer.client.feature.DistantHorizonsBridge;
import com.oemer.client.feature.GraphicsPresets;
import com.oemer.client.feature.KitProfiles;
import com.oemer.client.feature.PackTweaks;
import com.oemer.client.feature.PresetArt;
import com.oemer.client.feature.ShulkerPreview;
import com.oemer.client.feature.SpriteCosmetics;
import com.oemer.client.feature.YouTubeMusic;
import com.oemer.client.feature.Zoom;
import com.oemer.client.online.Firebase;
import com.oemer.client.online.FriendSkins;
import com.oemer.client.online.Social;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonNull;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Startet Minecraft automatisch (in GitHub) und prüft den Ömer Client.
 * Jede Prüfung schreibt eine Zeile "[OEMER-TEST] OK/FAIL ..." ins Log.
 */
@SuppressWarnings("UnstableApiUsage")
public class OemerClientGameTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	private void check(String name, boolean ok, String detail) {
		String line = "[OEMER-TEST] " + (ok ? "OK   " : "FAIL ") + name + (detail.isEmpty() ? "" : "  (" + detail + ")");
		System.out.println(line);
		if (!ok) {
			failures.add(name + " " + detail);
		}
	}

	/** Klickt einen Ömer-Button (eigene Buttons kennt der Fabric-Klick nicht). */
	private static void click(ClientGameTestContext ctx, String text) {
		boolean found = ctx.computeOnClient(mc -> {
			if (mc.screen == null) {
				return false;
			}
			for (var child : mc.screen.children()) {
				if (child instanceof AbstractButton b && b.getMessage().getString().equals(text)) {
					b.onPress(new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
					return true;
				}
			}
			return false;
		});
		if (!found) {
			throw new AssertionError("Button nicht gefunden: " + text);
		}
		ctx.waitTicks(3);
	}

	/** Läuft in einem normalen Thread (der Test-Thread darf Minecraft nicht direkt anfassen). */
	private static void off(Runnable r) {
		Thread t = new Thread(r, "Ömer Test Hintergrund");
		t.start();
		try {
			t.join(30000);
		} catch (InterruptedException ignored) {
		}
	}

	private interface Cond {
		boolean ok() throws Exception;
	}

	private static boolean until(ClientGameTestContext ctx, int maxTicks, Cond c) {
		for (int i = 0; i < maxTicks; i += 4) {
			try {
				if (c.ok()) return true;
			} catch (Exception ignored) {
			}
			ctx.waitTicks(4);
		}
		try {
			return c.ok();
		} catch (Exception e) {
			return false;
		}
	}

	private static byte[] solidPng(int w, int h, int argb) throws Exception {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) img.setRGB(x, y, argb);
		return ImageTools.toPng(img);
	}

	private static boolean denied(Firebase.Session s, String path) {
		try {
			Firebase.get(s, path);
			return false;
		} catch (Firebase.FirebaseException e) {
			return "DENIED".equals(e.code);
		}
	}

	private Firebase.Session bob;
	private String bobMc = "";

	private Firebase.Session makeUser(String email, String name, String mc) throws Exception {
		Firebase.Session s = Firebase.signUp(email, "geheim123");
		Firebase.put(s, "usernames/" + name.toLowerCase(), new JsonPrimitive(s.uid));
		JsonObject prof = new JsonObject();
		prof.addProperty("name", name);
		prof.addProperty("mc", mc);
		prof.add("seen", Firebase.serverTime());
		Firebase.put(s, "profiles/" + s.uid, prof);
		return s;
	}

	private void onlineTest(ClientGameTestContext ctx, String page) {
		try {
			onlineTestInner(ctx, page);
		} catch (Throwable t) {
			t.printStackTrace();
			check("online-exception", false, t.toString());
		}
	}

	private void onlineTestInner(ClientGameTestContext ctx, String page) throws Exception {
		if (Firebase.emulator() == null) {
			check("online-emulator", false, "Systemeigenschaft oemer.firebase.emulator fehlt");
			return;
		}
		httpGet(page + "api/state");

		// Anmelde- und Registrier-Formular
		OmerScreen.sectionForTest("FREUNDE", null);
		OmerScreen.friendsForTest(2, null, false);
		ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
		ctx.waitTicks(10);
		check("friends-nav", ctx.computeOnClient(mc -> mc.screen.children().stream().anyMatch(c -> c instanceof AbstractButton b && b.getMessage().getString().equals("Konto erstellen") == false && b.getMessage().getString().equals("Anmelden"))), "");
		ctx.takeScreenshot("50-freunde-anmelden");
		OmerScreen.friendsForTest(2, null, true);
		ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
		ctx.waitTicks(10);
		ctx.takeScreenshot("50b-freunde-registrieren");

		// Konto anlegen: 1 Username = 1 Konto
		Social.register("omer@test.de", "geheim123", "Omer", null);
		check("acc-register", until(ctx, 300, Social::loggedIn) && Social.username().equals("Omer"), Social.status());
		Social.register("zweiter@test.de", "geheim123", "omer", null);
		check("acc-username-unique", until(ctx, 300, () -> Social.status().contains("vergeben")) && Social.username().equals("Omer"), Social.status());
		Social.register("kurz@test.de", "geheim123", "ab", null);
		check("acc-username-invalid", until(ctx, 200, () -> Social.status().contains("3–16")), Social.status());
		Path acc = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("account.json");
		String accText = Files.isRegularFile(acc) ? Files.readString(acc) : "";
		check("acc-saved-no-password", accText.contains("refreshToken") && !accText.contains("geheim123"), "");

		// Abmelden / falsches Passwort / Anmelden
		Social.logout();
		check("acc-logout", until(ctx, 200, () -> !Social.loggedIn()), "");
		Social.login("omer@test.de", "falsch!", null);
		check("acc-wrong-password", until(ctx, 300, () -> Social.status().contains("stimmt nicht")) && !Social.loggedIn(), Social.status());
		Social.login("omer@test.de", "geheim123", null);
		check("acc-login", until(ctx, 300, Social::loggedIn) && Social.username().equals("Omer"), Social.status());

		// Zweites Konto "Anna" (Freund) und "Carl" (Anfrage)
		bobMc = java.util.UUID.randomUUID().toString();
		bob = makeUser("anna@test.de", "Anna", bobMc);
		Firebase.Session carl = makeUser("carl@test.de", "Carl", java.util.UUID.randomUUID().toString());
		String me = Social.myUid();

		Social.addFriend("gibtesnicht", null);
		check("friend-unknown", until(ctx, 200, () -> Social.status().contains("gibt es nicht")), Social.status());
		Social.addFriend("Omer", null);
		check("friend-self", until(ctx, 200, () -> Social.status().contains("selbst")), Social.status());
		Social.addFriend("anna", null);
		check("friend-request-sent", until(ctx, 300, () -> Firebase.get(bob, "requests/" + bob.uid).getAsJsonObject().has(me)), "");
		// Anna nimmt an (alles auf einmal, wie der Client)
		JsonObject acceptOps = new JsonObject();
		acceptOps.addProperty("friends/" + bob.uid + "/" + me, true);
		acceptOps.addProperty("friends/" + me + "/" + bob.uid, true);
		acceptOps.add("requests/" + bob.uid + "/" + me, JsonNull.INSTANCE);
		Firebase.multi(bob, acceptOps);
		off(Social::syncNow);
		check("friend-accepted", Social.friend(bob.uid) != null && Social.friend(bob.uid).name.equals("Anna"), "");
		check("friend-online", Social.friend(bob.uid) != null && Social.friend(bob.uid).online(), "");

		// Carl schickt eine Anfrage → erscheint, wird abgelehnt
		JsonObject req = new JsonObject();
		req.addProperty("name", "Carl");
		req.add("ts", Firebase.serverTime());
		Firebase.put(carl, "requests/" + me + "/" + carl.uid, req);
		off(Social::syncNow);
		check("request-incoming", Social.requests().size() == 1 && Social.requests().get(0).name().equals("Carl"), "" + Social.requests().size());

		// Cape + Totem teilen
		ctx.runOnClient(mc -> {
			OemerConfig c = OemerConfig.get();
			c.customCape = true;
			c.capeSelection = "design:3";
			c.customTotem = true;
			c.totemMode = 2;
			c.totemPreset = 0;
			c.shareSkins = true;
		});
		off(Social::syncSkinsLater);
		check("skins-upload", until(ctx, 400, () -> {
			var o = Firebase.get(bob, "skins/" + me).getAsJsonObject();
			return o.has("cape") && o.has("totem");
		}), "");
		check("skins-stranger-denied", denied(carl, "skins/" + me), "");
		// Anna teilt rotes Cape + grünes Totem
		JsonObject sk = new JsonObject();
		sk.addProperty("cape", java.util.Base64.getEncoder().encodeToString(solidPng(64, 32, 0xFFE02020)));
		sk.addProperty("totem", java.util.Base64.getEncoder().encodeToString(solidPng(96, 96, 0xFF20C040)));
		Firebase.put(bob, "skins/" + bob.uid, sk);
		off(Social::syncNow);
		check("skins-friend-received", FriendSkins.hasCape(bobMc) && FriendSkins.hasTotem(bob.uid), "");
		check("skins-cape-texture", ctx.computeOnClient(mc -> FriendSkins.capeFor(java.util.UUID.fromString(bobMc)) != null), "");

		// Chat
		Social.send(bob.uid, "Hallo Anna!", null);
		check("chat-sent", until(ctx, 300, () -> {
			var o = Firebase.get(bob, "messages/" + bob.uid + "/" + me).getAsJsonObject();
			return o.entrySet().stream().anyMatch(e -> e.getValue().getAsJsonObject().get("text").getAsString().equals("Hallo Anna!"));
		}), "");
		long ts = Social.serverNow();
		JsonObject m = new JsonObject();
		m.addProperty("from", bob.uid);
		m.addProperty("text", "Hey Ömer, hörst du mit? 🎵");
		m.addProperty("ts", ts);
		JsonObject ops = new JsonObject();
		ops.add("messages/" + bob.uid + "/" + me + "/mx1", m);
		ops.add("messages/" + me + "/" + bob.uid + "/mx1", m);
		ops.addProperty("inbox/" + me + "/" + bob.uid, ts);
		Firebase.multi(bob, ops);
		off(Social::syncNow);
		check("chat-received", Social.chat(bob.uid).size() == 2 && Social.unread(bob.uid) == 1, Social.chat(bob.uid).size() + " Nachrichten, " + Social.unread(bob.uid) + " ungelesen");

		// Musik teilen
		ctx.runOnClient(mc -> YouTubeMusic.play(new YouTubeMusic.Track("jNQXAC9IVRw", "Me at the zoo", "jawed")));
		httpGet(page + "api/state");
		off(Social::publishNow);
		check("music-published", until(ctx, 200, () -> Firebase.get(bob, "listening/" + me).getAsJsonObject().get("title").getAsString().equals("Me at the zoo")), "");
		Social.setMusicFor(bob.uid, false, null);
		check("music-hidden-for-friend", until(ctx, 300, () -> denied(bob, "listening/" + me)), "");
		Social.setMusicFor(bob.uid, true, null);
		check("music-visible-again", until(ctx, 300, () -> !denied(bob, "listening/" + me)), "");
		ctx.runOnClient(mc -> OemerConfig.get().shareMusic = false);
		off(Social::publishNow);
		check("music-global-off", until(ctx, 200, () -> Firebase.get(bob, "listening/" + me).isJsonNull()), "");
		ctx.runOnClient(mc -> OemerConfig.get().shareMusic = true);

		// Anna hört Musik → Ömer sieht es und hört mit
		JsonObject l = new JsonObject();
		l.addProperty("id", "dQw4w9WgXcQ");
		l.addProperty("title", "Fools (Lyrics)");
		l.addProperty("artist", "Madison Beer");
		l.addProperty("service", "YouTube");
		l.addProperty("pos", 30000);
		l.add("at", Firebase.serverTime());
		l.addProperty("playing", true);
		Firebase.put(bob, "listening/" + bob.uid, l);
		off(Social::syncNow);
		check("music-friend-visible", Social.friend(bob.uid).listening != null && Social.friend(bob.uid).listening.title().startsWith("Fools"), "");
		check("music-position-advances", Social.friend(bob.uid).listening != null && Social.positionOf(Social.friend(bob.uid).listening) >= 30000, "");

		// Screenshots: Liste, Chat
		OmerScreen.friendsForTest(0, null, false);
		ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
		ctx.waitTicks(20);
		ctx.takeScreenshot("51-freunde-liste");
		String bobUid = bob.uid;
		OmerScreen.friendsForTest(1, bobUid, false);
		ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
		ctx.waitTicks(20);
		ctx.takeScreenshot("52-freunde-chat");
		check("chat-marked-read", until(ctx, 100, () -> Social.unread(bobUid) == 0), "");
		OmerScreen.friendsForTest(2, null, false);
		ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
		ctx.waitTicks(20);
		ctx.takeScreenshot("53-freunde-konto");

		// Mitlauschen (YouTube) und Spotify (nicht möglich)
		Social.listenAlong(bob.uid, null);
		check("listen-along", until(ctx, 300, () -> YouTubeMusic.current() != null && YouTubeMusic.current().id.equals("dQw4w9WgXcQ")), "");
		httpGet(page + "api/cmd?do=clearqueue");
		l.addProperty("service", "Spotify");
		l.addProperty("id", "");
		Firebase.put(bob, "listening/" + bob.uid, l);
		Social.listenAlong(bob.uid, null);
		check("listen-spotify-refused", until(ctx, 300, () -> Social.status().contains("nur bei YouTube")), Social.status());

		// Anfrage ablehnen
		Social.decline(carl.uid, null);
		check("request-declined", until(ctx, 300, () -> Social.requests().isEmpty()), "");
		ctx.setScreen(() -> new TitleScreen());
		ctx.waitTicks(5);
	}

	/** Im Spiel: ein „Freund“ mit geteiltem Cape bekommt es auf seinen Spieler. */
	private void onlineWorldCheck(ClientGameTestContext ctx) {
		if (Firebase.emulator() == null || bob == null) return;
		boolean capeOnPlayer = ctx.computeOnClient(mc -> {
			var uuid = java.util.UUID.fromString(bobMc);
			var rp = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(uuid, "Anna"));
			var cape = rp.getSkin().cape();
			return cape != null && cape.equals(FriendSkins.capeFor(uuid));
		});
		check("friend-cape-on-player", capeOnPlayer, "");
		ctx.runOnClient(mc -> {
			var uuid = java.util.UUID.fromString(bobMc);
			var rp = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(uuid, "Anna"));
			rp.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 2.5);
			rp.setYRot(0);
			rp.setYHeadRot(0);
			rp.setXRot(0);
			mc.level.addEntity(rp);
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot("54-freund-cape-in-welt");
		ctx.runOnClient(mc -> {
			for (var e : mc.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.client.player.RemotePlayer) e.discard();
			}
		});
		// Aufräumen: Freund entfernen, abmelden
		Social.removeFriend(bob.uid, null);
		Social.logout();
		check("online-cleanup", until(ctx, 300, () -> !Social.loggedIn()), "");
	}

	/** Buntes Testbild (Verlauf mit Kreis). */
	private static java.awt.image.BufferedImage testImage(java.awt.Color a, java.awt.Color b) {
		var img = new java.awt.image.BufferedImage(800, 450, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		var g = img.createGraphics();
		g.setPaint(new java.awt.GradientPaint(0, 0, a, 800, 450, b));
		g.fillRect(0, 0, 800, 450);
		g.setColor(new java.awt.Color(255, 255, 255, 200));
		g.fillOval(300, 125, 200, 200);
		g.dispose();
		return img;
	}

	private static String httpGet(String url) {
		try {
			var client = java.net.http.HttpClient.newHttpClient();
			var r = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
					java.net.http.HttpResponse.BodyHandlers.ofString());
			return r.body();
		} catch (Exception e) {
			return "Fehler: " + e;
		}
	}

	private static boolean hasButton(Minecraft mc, String text) {
		return mc.screen != null && Screens.getButtons(mc.screen).stream()
				.anyMatch(w -> w.getMessage().getString().equals(text));
	}

	private static void copyAsset(String name, Path target) {
		try (InputStream in = OemerClientGameTest.class.getResourceAsStream("/test-assets/" + name)) {
			Files.createDirectories(target.getParent());
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void shotFreecam(ClientGameTestContext ctx, double dx, double dy, double dz, float yaw, float pitch, String name) {
		ctx.runOnClient(mc -> {
			if (!Freecam.isEnabled()) OemerConfig.get().freecam = true;
		});
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			if (Freecam.isEnabled()) {
				Freecam.camera().snapTo(mc.player.getX() + dx, mc.player.getY() + dy, mc.player.getZ() + dz, yaw, pitch);
			}
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot(name);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		// ---------------------------------------------- Test-Dateien
		ctx.runOnClient(mc -> {
			try {
				Files.createDirectories(CapeManager.folder());
				Files.write(CapeManager.folder().resolve("test-bild.png"), ImageTools.toPng(testImage(java.awt.Color.PINK, java.awt.Color.MAGENTA)));
				Files.createDirectories(LooksPack.totemFolder());
				Files.write(LooksPack.totemFolder().resolve("test-totem.png"), ImageTools.toPng(testImage(java.awt.Color.ORANGE, java.awt.Color.RED)));
				Files.createDirectories(Looks.folder());
				Files.write(Looks.folder().resolve("test-look.png"), ImageTools.toPng(testImage(new java.awt.Color(0x1B2A4A), new java.awt.Color(0xB04A8F))));
				// Flügel-PNG wie ein eigenes Bild: beide Flügel nebeneinander
				var wing = PresetArt.featherWing(PresetArt.WingStyle.GALAXY, 256);
				var pair = new java.awt.image.BufferedImage(512, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
				var pg = pair.createGraphics();
				pg.drawImage(PresetArt.mirror(wing), 0, 0, null);
				pg.drawImage(wing, 256, 0, null);
				pg.dispose();
				Files.createDirectories(CustomCosmetics.folder());
				Files.write(CustomCosmetics.folder().resolve("test-fluegel.png"), ImageTools.toPng(pair));
				Files.write(CustomCosmetics.folder().resolve("test-stern.png"), ImageTools.toPng(PresetArt.star(256, 0xFFC21A)));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			copyAsset("haus.litematic", MaterialList.folder().resolve("haus.litematic"));
			copyAsset("beep.ogg", Sounds.folder(Sounds.SLOTS[0]).resolve("beep.ogg"));
		});

		// ---------------------------------------------- Startscreen
		ctx.waitFor(mc -> mc.screen != null && mc.getOverlay() == null, 1200);
		ctx.waitTicks(20);
		String firstScreen = ctx.computeOnClient(mc -> mc.screen == null ? "null" : mc.screen.getClass().getName());
		check("first-screen-title", firstScreen.equals(TitleScreen.class.getName()), firstScreen);
		if (!firstScreen.equals(TitleScreen.class.getName())) {
			ctx.takeScreenshot("00-erster-screen");
			ctx.setScreen(() -> new TitleScreen());
		}
		ctx.waitForScreen(TitleScreen.class);
		check("startscreen-button", ctx.computeOnClient(mc -> hasButton(mc, "Ömer Client")), "");
		ctx.takeScreenshot("01-startscreen");

		// ---------------------------------------------- Menü: alle Bereiche
		click(ctx, "Ömer Client");
		check("menu-opens", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
		ctx.waitTicks(10);
		ctx.takeScreenshot("02-mods");
		click(ctx, "Just Zoom");
		ctx.takeScreenshot("02b-mod-zoom");
		click(ctx, "‹ Alle Mods");
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof OmerScreen o) o.searchForTest("no");
		});
		ctx.waitTicks(3);
		ctx.takeScreenshot("02c-mods-suche");
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof OmerScreen o) o.searchForTest("");
		});
		ctx.waitTicks(2);
		click(ctx, "Material List");
		ctx.takeScreenshot("02d-mod-material");
		click(ctx, "‹ Alle Mods");
		click(ctx, "Custom Crosshair");
		ctx.takeScreenshot("02e-mod-crosshair");
		click(ctx, "Settings");
		ctx.takeScreenshot("03-settings");
		ctx.runOnClient(mc -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -10));
		ctx.waitTicks(3);
		ctx.takeScreenshot("03b-settings-tasten");
		click(ctx, "Kits");
		ctx.takeScreenshot("04-kits");
		click(ctx, "Design");
		ctx.waitFor(mc -> Looks.photoReady(0) && Looks.photoReady(3), 600);
		ctx.waitTicks(40);
		check("background-download", ctx.computeOnClient(mc -> Looks.photoReady(0)), "");
		ctx.takeScreenshot("05-design");
		ctx.runOnClient(mc -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -8));
		ctx.waitTicks(10);
		ctx.takeScreenshot("05b-design-gescrollt");
		click(ctx, "Musik");
		ctx.takeScreenshot("06-musik-youtube");
		click(ctx, "Spotify");
		ctx.takeScreenshot("06b-musik-spotify");
		click(ctx, "YouTube");
		click(ctx, "Skin");
		click(ctx, "Cape");
		ctx.waitTicks(10);
		ctx.takeScreenshot("07-skin-cape");
		click(ctx, "Totem");
		ctx.waitTicks(10);
		ctx.takeScreenshot("07b-skin-totem");
		click(ctx, "Cosmetics");
		ctx.waitTicks(10);
		ctx.takeScreenshot("07c-skin-cosmetics");
		click(ctx, "Sounds");
		ctx.takeScreenshot("07d-skin-sounds");

		// ---------------------------------------------- YouTube-Musik (Player-Seite + Titel ohne Schlüssel)
		String page = ctx.computeOnClient(mc -> {
			YouTubeMusic.start();
			return YouTubeMusic.url();
		});
		String html = httpGet(page);
		check("yt-page", html.contains("Ömer Music") && html.contains("iframe_api"), html.length() + " Zeichen");
		String state = httpGet(page + "api/state");
		check("yt-state", state.contains("\"volume\""), state.length() > 80 ? state.substring(0, 80) : state);
		YouTubeMusic.Track zoo = YouTubeMusic.resolve("jNQXAC9IVRw");
		check("yt-oembed", zoo.title.toLowerCase().contains("zoo"), zoo.title);

		// ---------------------------------------------- Songs löschen, Playlists im Spiel
	httpGet(page + "api/state"); // Player-Seite gilt als verbunden → im Browser öffnet sich nichts
	YouTubeMusic.Track[] tr = {
			new YouTubeMusic.Track("jNQXAC9IVRw", "Me at the zoo", "jawed"),
			new YouTubeMusic.Track("dQw4w9WgXcQ", "Madison Beer - Fools (Lyrics)", "Good Taste Only"),
			new YouTubeMusic.Track("9bZkp7q19f0", "Madison Beer - bittersweet (Lyrics)", "Vibe Music"),
			new YouTubeMusic.Track("kJQP7kiw5Fk", "Madison Beer - yes baby (Lyrics)", "Vibe Music")};
	ctx.runOnClient(mc -> {
		OemerConfig c = OemerConfig.get();
		c.ytLibrary.clear();
		c.ytPlaylists.clear();
		YouTubeMusic.clearQueue();
		for (YouTubeMusic.Track t : tr) YouTubeMusic.toggleLibrary(t);
	});
	check("lib-add", ctx.computeOnClient(mc -> OemerConfig.get().ytLibrary.size() == 4), "");
	ctx.runOnClient(mc -> YouTubeMusic.removeFromLibrary(tr[0]));
	check("lib-remove", ctx.computeOnClient(mc -> OemerConfig.get().ytLibrary.size() == 3 && !YouTubeMusic.inLibrary(tr[0].id)), "");

	YouTubeMusic.Playlist[] pls = new YouTubeMusic.Playlist[2];
	ctx.runOnClient(mc -> {
		pls[0] = YouTubeMusic.createPlaylist("Test Liste");
		pls[1] = YouTubeMusic.createPlaylist("Test Liste");
		for (int i = 1; i < 4; i++) YouTubeMusic.addToPlaylist(pls[0], tr[i]);
		YouTubeMusic.addToPlaylist(pls[0], tr[1]); // doppelt → ignoriert
	});
	check("playlist-create", pls[0].name.equals("Test Liste") && pls[0].tracks.size() == 3, pls[0].name + " " + pls[0].tracks.size());
	check("playlist-unique-name", pls[1].name.equals("Test Liste 2"), pls[1].name);
	ctx.runOnClient(mc -> YouTubeMusic.removeFromPlaylist(pls[0], tr[2]));
	check("playlist-track-remove", pls[0].tracks.size() == 2 && pls[0].tracks.stream().noneMatch(t -> t.id.equals(tr[2].id)), "");

	// Menü: Meine Songs mit ✕, Playlist-Verwaltung, Song zu Playlist
	OmerScreen.sectionForTest("MUSIK", null);
	OmerScreen.musicForTest(null, null);
	ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
	ctx.waitTicks(30);
	ctx.takeScreenshot("11a-musik-loeschen");
	ctx.runOnClient(mc -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -12));
	ctx.waitTicks(10);
	ctx.takeScreenshot("11b-musik-playlists");
	OmerScreen.musicForTest(null, tr[3]);
	ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
	ctx.waitTicks(20);
	ctx.takeScreenshot("11c-musik-zu-playlist");
	OmerScreen.musicForTest(pls[0], null);
	ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
	ctx.waitTicks(30);
	ctx.takeScreenshot("11d-musik-playlist-offen");
	check("playlist-open-menu", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
	OmerScreen.musicForTest(null, null);

	// Warteschlange: Song entfernen, aktueller Song entfernen, leeren
	httpGet(page + "api/state");
	ctx.runOnClient(mc -> {
		YouTubeMusic.enqueue(tr[1]);
		YouTubeMusic.enqueue(tr[2]);
		YouTubeMusic.enqueue(tr[3]);
	});
	check("queue-add", YouTubeMusic.queue().size() == 3 && YouTubeMusic.current() != null && YouTubeMusic.current().id.equals(tr[1].id), "");
	ctx.runOnClient(mc -> YouTubeMusic.removeFromQueue(2));
	check("queue-remove", YouTubeMusic.queue().size() == 2 && YouTubeMusic.index() == 0, "size=" + YouTubeMusic.queue().size());
	httpGet(page + "api/state");
	ctx.runOnClient(mc -> YouTubeMusic.removeFromQueue(0));
	check("queue-remove-current", YouTubeMusic.queue().size() == 1 && YouTubeMusic.current() != null && YouTubeMusic.current().id.equals(tr[2].id), "");
	httpGet(page + "api/cmd?do=clearqueue");
	check("queue-clear", YouTubeMusic.queue().isEmpty() && YouTubeMusic.current() == null, "");
	// dieselben Löschbefehle wie in der Player-Seite
	httpGet(page + "api/cmd?do=unlib&id=" + tr[1].id);
	check("web-unlib", !YouTubeMusic.inLibrary(tr[1].id), "");
	httpGet(page + "api/cmd?do=unpl&p=0&id=" + tr[3].id);
	check("web-unpl", pls[0].tracks.size() == 1, "" + pls[0].tracks.size());
	httpGet(page + "api/cmd?do=delpl&p=1");
	check("web-delpl", ctx.computeOnClient(mc -> OemerConfig.get().ytPlaylists.size() == 1), "");
	ctx.runOnClient(mc -> {
		YouTubeMusic.clearLibrary();
		YouTubeMusic.deletePlaylist(pls[0]);
	});
	check("lib-playlist-clear", ctx.computeOnClient(mc -> OemerConfig.get().ytLibrary.isEmpty() && OemerConfig.get().ytPlaylists.isEmpty()), "");

	// ---------------------------------------------- Freunde (online, gegen den Firebase-Emulator)
	onlineTest(ctx, page);

	// ---------------------------------------------- Eigene Dateien löschen (Design, Cape, Totem, Cosmetic, Crosshair, Schematic, Kit)
	ctx.runOnClient(mc -> {
		try {
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, 0xFFFF4F9A);
			for (Path dir : new Path[]{Looks.folder(), CapeManager.folder(), LooksPack.totemFolder(), CustomCosmetics.folder(), Crosshair.folder()}) {
				Files.createDirectories(dir);
				ImageIO.write(img, "png", dir.resolve("zz_del.png").toFile());
			}
			Files.createDirectories(MaterialList.folder());
			Files.writeString(MaterialList.folder().resolve("zz_del.litematic"), "x");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		OemerConfig c = OemerConfig.get();
		c.lookImage = "zz_del.png";
		c.lookMode = true;
	});
	OmerScreen.sectionForTest("DESIGN", null);
	ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
	ctx.waitFor(mc -> Looks.photoReady(0), 600);
	ctx.waitTicks(40);
	ctx.takeScreenshot("11e-design-loeschen");
	ctx.runOnClient(mc -> OwnFiles.look("zz_del.png"));
	check("delete-look", ctx.computeOnClient(mc -> !Files.exists(Looks.folder().resolve("zz_del.png")) && OemerConfig.get().lookImage.equals("web:0")), "");
	ctx.runOnClient(mc -> {
		OemerConfig.get().capeSelection = "zz_del.png";
		OwnFiles.cape("zz_del.png");
	});
	check("delete-cape", ctx.computeOnClient(mc -> !Files.exists(CapeManager.folder().resolve("zz_del.png")) && OemerConfig.get().capeSelection.equals("design:0")), "");
	boolean[] wasTotem = new boolean[1];
	ctx.runOnClient(mc -> {
		OemerConfig c = OemerConfig.get();
		c.totemMode = 0;
		c.totemSelection = "zz_del.png";
		wasTotem[0] = OwnFiles.totem("zz_del.png");
	});
	check("delete-totem", wasTotem[0] && ctx.computeOnClient(mc -> !Files.exists(LooksPack.totemFolder().resolve("zz_del.png")) && OemerConfig.get().totemSelection.isEmpty()), "");
	ctx.runOnClient(mc -> {
		SpriteCosmetics.equip(SpriteCosmetics.Slot.HEAD, "custom:zz_del.png");
		OwnFiles.cosmetic("zz_del.png");
	});
	check("delete-cosmetic", ctx.computeOnClient(mc -> !Files.exists(CustomCosmetics.folder().resolve("zz_del.png"))
			&& SpriteCosmetics.equipped(SpriteCosmetics.Slot.HEAD).isEmpty()), "");
	ctx.runOnClient(mc -> {
		OemerConfig.get().crosshairSelection = "zz_del.png";
		OwnFiles.crosshair("zz_del.png");
	});
	check("delete-crosshair", ctx.computeOnClient(mc -> !Files.exists(Crosshair.folder().resolve("zz_del.png"))
			&& OemerConfig.get().crosshairSelection.equals("design:0")), "");
	ctx.runOnClient(mc -> OwnFiles.schematic("zz_del.litematic"));
	check("delete-schematic", ctx.computeOnClient(mc -> !Files.exists(MaterialList.folder().resolve("zz_del.litematic"))), "");
	check("delete-safe-path", ctx.computeOnClient(mc -> !OwnFiles.remove(Looks.folder(), "../oemerclient.json") && !OwnFiles.remove(Looks.folder(), "a/b.png")), "");
	check("delete-kit", ctx.computeOnClient(mc -> {
		KitProfiles.save(4);
		boolean saved = KitProfiles.hasSaved(4);
		KitProfiles.delete(4);
		return saved && !KitProfiles.hasSaved(4);
	}), "");

	// ---------------------------------------------- Spotify (ohne Konto: nur prüfen, was offline geht)
		check("spotify-pkce", ctx.computeOnClient(mc -> {
			try {
				return Spotify.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk").equals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
			} catch (Exception e) {
				return false;
			}
		}), "");
		check("spotify-status", ctx.computeOnClient(mc -> Spotify.status() == Spotify.Status.NO_CLIENT_ID), "");
		ctx.setScreen(() -> new SpotifyScreen(new OmerScreen(null)));
		ctx.waitTicks(3);
		ctx.takeScreenshot("11e-spotify-screen");
		click(ctx, "‹ Zurück");

		// ---------------------------------------------- Totem-, Crosshair- und Cosmetic-Editor
		ctx.setScreen(() -> new CapeEditorScreen(new OmerScreen(null), CapeEditorScreen.Mode.TOTEM));
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof CapeEditorScreen ed) {
				// kleines Herz
				int[][] heart = {{4,4},{5,3},{6,3},{7,4},{8,4},{9,3},{10,3},{11,4},{3,5},{12,5},{3,6},{12,6},{4,7},{11,7},{5,8},{10,8},{6,9},{9,9},{7,10},{8,10}};
				for (int[] p : heart) ed.paintForTest(p[0], p[1], 0xFFFF4F9A, CapeEditorScreen.Tool.PINSEL);
				ed.paintForTest(7, 6, 0xFFFF8FC7, CapeEditorScreen.Tool.EIMER);
			}
		});
		ctx.waitTicks(2);
		ctx.takeScreenshot("12b-totem-editor");
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof CapeEditorScreen ed) ed.saveForTest();
		});
		String tsel = ctx.computeOnClient(mc -> OemerConfig.get().totemSelection);
		check("totem-editor-saves", tsel.startsWith("Mein Totem") && ctx.computeOnClient(mc -> OemerConfig.get().totemMode == 0), tsel);

		ctx.setScreen(() -> new CapeEditorScreen(new OmerScreen(null), CapeEditorScreen.Mode.CROSSHAIR));
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof CapeEditorScreen ed) {
				for (int i = 2; i <= 12; i++) {
					if (i < 6 || i > 8) {
						ed.paintForTest(i, 7, 0xFFFFFFFF, CapeEditorScreen.Tool.PINSEL);
						ed.paintForTest(7, i, 0xFFFFFFFF, CapeEditorScreen.Tool.PINSEL);
					}
				}
				ed.paintForTest(7, 7, 0xFFFF4F9A, CapeEditorScreen.Tool.PINSEL);
				ed.saveForTest();
			}
		});
		ctx.waitTicks(2);
		ctx.takeScreenshot("12c-crosshair-editor");
		String csel = ctx.computeOnClient(mc -> OemerConfig.get().crosshairSelection);
		check("crosshair-editor-saves", csel.startsWith("Mein Crosshair") && ctx.computeOnClient(mc -> Crosshair.active()), csel);

		ctx.setScreen(() -> new CapeEditorScreen(new OmerScreen(null), CapeEditorScreen.Mode.COSMETIC));
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof CapeEditorScreen ed) {
				// kleiner Stern
				int[][] star = {{7,1},{8,1},{7,2},{8,2},{6,3},{9,3},{1,5},{2,5},{3,5},{4,5},{5,5},{6,5},{9,5},{10,5},{11,5},{12,5},{13,5},{14,5},
						{3,6},{12,6},{4,7},{11,7},{5,8},{10,8},{4,9},{11,9},{3,10},{7,10},{8,10},{12,10},{2,11},{6,11},{9,11},{13,11},{2,12},{5,12},{10,12},{13,12}};
				// gefüllter Stern Zeile für Zeile
				String[] rows = {
						"       ##       ", "       ##       ", "      ####      ", "      ####      ",
						"##############  ", " ############   ", "  ##########    ", "   ########     ",
						"   ########     ", "  ####  ####    ", "  ###    ###    ", " ##        ##   "};
				for (int y = 0; y < rows.length; y++) {
					for (int x = 0; x < rows[y].length(); x++) {
						if (rows[y].charAt(x) == '#') ed.paintForTest(x, y + 2, (y < 4 ? 0xFFFFE066 : 0xFFFFC21A), CapeEditorScreen.Tool.PINSEL);
					}
				}
				ed.saveForTest();
			}
		});
		ctx.waitTicks(2);
		ctx.takeScreenshot("12d-cosmetic-editor");
		boolean cosSaved = ctx.computeOnClient(mc -> CustomCosmetics.files().stream().anyMatch(f -> f.startsWith("Mein Cosmetic"))
				&& SpriteCosmetics.anyEquipped());
		check("cosmetic-editor-saves", cosSaved, "");
		ctx.setScreen(() -> new OmerScreen(new TitleScreen()));
		ctx.waitTicks(3);

		// ---------------------------------------------- Cape-Editor
		click(ctx, "Skin");
		click(ctx, "Cape");
		click(ctx, "Cape-Editor");
		check("editor-opens", ctx.computeOnClient(mc -> mc.screen instanceof CapeEditorScreen), "");
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof CapeEditorScreen ed) {
				var cv = ed.canvas();
				// schwarzer Hintergrund, rosa Streifen, weißes Ö-ähnliches Muster
				ed.paintForTest(0, 0, 0xFF0B0B0F, CapeEditorScreen.Tool.EIMER);
				for (int y = 0; y < cv.height; y++) {
					ed.paintForTest(0, y, 0xFFFF8FC7, CapeEditorScreen.Tool.PINSEL);
					ed.paintForTest(cv.width - 1, y, 0xFFFF8FC7, CapeEditorScreen.Tool.PINSEL);
				}
				int[][] o = {{3, 5}, {4, 4}, {5, 4}, {6, 5}, {6, 6}, {6, 7}, {6, 8}, {5, 9}, {4, 9}, {3, 8}, {3, 7}, {3, 6}, {3, 2}, {6, 2}};
				for (int[] p : o) ed.paintForTest(p[0], p[1], 0xFFFFFFFF, CapeEditorScreen.Tool.PINSEL);
				for (int x = 2; x < 8; x++) ed.paintForTest(x, 12, 0xFFFF8FC7, CapeEditorScreen.Tool.PINSEL);
			}
		});
		ctx.waitTicks(3);
		ctx.takeScreenshot("12-cape-editor");
		ctx.runOnClient(mc -> {
			if (mc.screen instanceof CapeEditorScreen ed) ed.saveForTest();
		});
		ctx.waitTicks(3);
		String sel = ctx.computeOnClient(mc -> OemerConfig.get().capeSelection);
		boolean saved = ctx.computeOnClient(mc -> Files.isRegularFile(CapeManager.folder().resolve(OemerConfig.get().capeSelection)));
		check("editor-saves", sel.startsWith("Mein Cape") && saved, sel);
		ctx.takeScreenshot("13-cape-editor-gespeichert");
		click(ctx, "‹ Zurück");
		ctx.takeScreenshot("14-skins-cape-eigenes");
		click(ctx, "Fertig");
		check("menu-closes", ctx.computeOnClient(mc -> mc.screen instanceof TitleScreen), "");

		// ---------------------------------------------- Welt
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("time set midnight");
			ctx.waitTicks(20);

			int[] p = ctx.computeOnClient(mc -> new int[]{
					(int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ())});
			int x = p[0], y = p[1], z = p[2];
			sp.getServer().runCommand("fill " + (x - 4) + " " + y + " " + (z - 4) + " " + (x + 4) + " " + (y + 5) + " " + (z + 8) + " air");
			sp.getServer().runCommand("fill " + (x - 4) + " " + (y - 1) + " " + (z + 4) + " " + (x + 4) + " " + (y + 5) + " " + (z + 4) + " stone");
			ctx.runOnClient(mc -> {
				mc.player.setYRot(0);
				mc.player.setXRot(0);
			});
			sp.getClientWorld().waitForChunksRender();
			ctx.waitTicks(10);
			ctx.takeScreenshot("20-welt-nacht");

			// ------------------------------------------ Fullbright
			ctx.getInput().pressKey(GLFW.GLFW_KEY_G);
			ctx.waitTicks(5);
			check("fullbright-key", ctx.computeOnClient(mc -> OemerConfig.get().fullbright), "");
			ctx.takeScreenshot("21-fullbright");

			// ------------------------------------------ Free Cam
			double[] player0 = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
			ctx.getInput().pressKey(GLFW.GLFW_KEY_F4);
			ctx.waitTicks(3);
			check("freecam-on", ctx.computeOnClient(mc -> Freecam.isEnabled() && mc.getCameraEntity() instanceof FreeCamera), "");
			ctx.getInput().holdKeyFor(o -> o.keyUp, 40);
			ctx.waitTicks(5);
			double[] cam = ctx.computeOnClient(mc -> Freecam.isEnabled()
					? new double[]{Freecam.camera().getX(), Freecam.camera().getY(), Freecam.camera().getZ()}
					: new double[]{0, 0, 0});
			double[] player1 = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
			String camInfo = String.format("cam z=%.2f, wall z=%d, start z=%.2f", cam[2], z + 4, player0[2]);
			check("freecam-moves", cam[2] > player0[2] + 1.5, camInfo);
			check("freecam-collision", cam[2] < z + 4.0, camInfo);
			check("player-stays", Math.abs(player1[0] - player0[0]) < 0.3 && Math.abs(player1[2] - player0[2]) < 0.3,
					String.format("player moved %.2f / %.2f", player1[0] - player0[0], player1[2] - player0[2]));
			ctx.getInput().pressKey(GLFW.GLFW_KEY_F4);
			ctx.waitTicks(3);
			check("freecam-off", ctx.computeOnClient(mc -> !Freecam.isEnabled() && mc.getCameraEntity() == mc.player), "");

			// ------------------------------------------ Cape aus dem Editor
			ctx.runOnClient(mc -> {
				OemerConfig.get().customCape = true;
				CapeManager.invalidate();
			});
			ctx.waitTicks(3);
			String capePath = ctx.computeOnClient(mc -> {
				var cape = mc.player.getSkin().cape();
				return cape == null ? "null" : cape.texturePath().toString();
			});
			check("cape-from-editor", capePath.startsWith("oemerclient:cape/"), capePath);
			shotFreecam(ctx, 0, 0.6, -2.6, 0, 15, "22-cape-editor-im-spiel");

			// ------------------------------------------ Eigene Bild-Cosmetics (Flügel, Kopf, Begleiter)
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.cosmeticsOn = true;
				c.customCape = false;
				c.equipped.clear();
				c.equipped.put("BACK", "custom:test-fluegel.png");
			});
			ctx.waitTicks(20);
			check("cosmetic-wings-equipped", ctx.computeOnClient(mc ->
					SpriteCosmetics.find(SpriteCosmetics.equipped(SpriteCosmetics.Slot.BACK), SpriteCosmetics.Slot.BACK) != null), "");
			shotFreecam(ctx, 0, 0.9, -3.0, 0, 10, "23-cosmetics-fluegel-hinten");
			shotFreecam(ctx, 2.6, 0.9, -1.0, 69, 8, "23b-cosmetics-fluegel-seite");
			shotFreecam(ctx, 0, 0.9, 3.0, 180, 10, "24-cosmetics-fluegel-vorne");
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.equipped.put("HEAD", "custom:test-stern.png");
				c.equipped.put("COMPANION", "custom:test-stern.png");
			});
			ctx.waitTicks(10);
			shotFreecam(ctx, 0, 1.2, 3.2, 180, 12, "25-cosmetics-kopf-begleiter");
			ctx.runOnClient(mc -> {
				OemerConfig.get().equipped.clear();
				OemerConfig.get().freecam = false;
				OemerConfig.get().customCape = true;
			});
			ctx.waitTicks(3);

			// ------------------------------------------ Crosshair im Spiel
			ctx.waitTicks(3);
			ctx.takeScreenshot("27b-crosshair-eigenes");
			ctx.runOnClient(mc -> {
				OemerConfig.get().crosshairSelection = "design:3";
				OemerConfig.get().crosshairColor = 2;
				OemerConfig.get().crosshairSize = 2;
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot("27c-crosshair-design");

			// ------------------------------------------ Kit-HUD + Material List
			sp.getServer().runCommand("give @a totem_of_undying 3");
			sp.getServer().runCommand("give @a end_crystal 32");
			sp.getServer().runCommand("give @a obsidian 64");
			sp.getServer().runCommand("give @a oak_planks 64");
			sp.getServer().runCommand("give @a stone_bricks 128");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.kit = 1;
				c.kitHud = true;
				c.kitHudCorner = 0;
				c.schematic = "haus.litematic";
				c.materialList = true;
				c.materialListCorner = 1;
				MaterialList.reload();
			});
			ctx.waitTicks(5);
			int totems = ctx.computeOnClient(mc -> MaterialList.have(Items.TOTEM_OF_UNDYING));
			check("kit-count", totems == 3, "totems=" + totems);
			int entries = ctx.computeOnClient(mc -> MaterialList.entries().size());
			check("material-entries", entries == 8, "entries=" + entries + " " + ctx.computeOnClient(mc -> MaterialList.error()));
			ctx.takeScreenshot("28-hud-kit-material");

			ctx.setScreen(() -> new MaterialListScreen(null));
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				var first = MaterialList.entries().get(0);
				if (!MaterialList.isDone(first)) MaterialList.toggleDone(first);
			});
			ctx.waitTicks(2);
			check("material-done", ctx.computeOnClient(mc -> MaterialList.isDone(MaterialList.entries().get(0))), "");
			ctx.takeScreenshot("29-material-list-screen");
			ctx.setScreen(() -> null);
			ctx.waitTicks(3);

			// ------------------------------------------ Kiste markieren (Sneaken + Rechtsklick mit Spitzhacke) und mitzählen
			BlockPos chest = new BlockPos(x, y + 1, z + 2);
			sp.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " chest");
			sp.getServer().runCommand("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container.0 with oak_planks 64");
			sp.getServer().runCommand("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container.1 with stone_bricks 32");
			sp.getServer().runCommand("item replace entity @a weapon.mainhand with diamond_pickaxe");
			ctx.runOnClient(mc -> {
				mc.player.setYRot(0);
				mc.player.setXRot(0);
			});
			ctx.waitTicks(10);
			int planksBefore = ctx.computeOnClient(mc -> MaterialList.haveTotal(Items.OAK_PLANKS));
			ctx.getInput().holdKey(o -> o.keyShift);
			ctx.waitTicks(3);
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(3);
			ctx.getInput().releaseKey(o -> o.keyShift);
			ctx.waitTicks(3);
			boolean marked = ctx.computeOnClient(mc -> ChestMarker.find(chest) != null);
			check("chest-mark", marked, "hit=" + ctx.computeOnClient(mc -> String.valueOf(mc.hitResult == null ? null : mc.hitResult.getLocation())));
			if (!marked) ctx.runOnClient(mc -> ChestMarker.toggle(chest));
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(10);
			ctx.takeScreenshot("29b-markierte-kiste-offen");
			ctx.setScreen(() -> null);
			ctx.waitTicks(5);
			int planksAfter = ctx.computeOnClient(mc -> MaterialList.haveTotal(Items.OAK_PLANKS));
			check("chest-count", planksAfter == planksBefore + 64, planksBefore + " -> " + planksAfter);
			ctx.runOnClient(mc -> {
				OemerConfig.get().freecam = true;
			});
			shotFreecam(ctx, 2.5, 1.6, -1.0, 40, 12, "29c-kiste-leuchtet");
			ctx.runOnClient(mc -> OemerConfig.get().freecam = false);
			ctx.waitTicks(3);
			sp.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " air");

			// ------------------------------------------ Kits = eigene Einstellungen pro Kit
			ctx.runOnClient(mc -> {
				OemerConfig.get().kitAutoSave = true;
				OemerConfig.get().kit = 0;
				mc.options.keyDrop.setKey(InputConstants.getKey("key.keyboard.q"));
				mc.options.keyHotbarSlots[4].setKey(InputConstants.getKey("key.keyboard.5"));
				mc.options.fov().set(70);
				net.minecraft.client.KeyMapping.resetMapping();
				KitProfiles.save(0);
				KitProfiles.switchTo(1);  // CPVP: startet mit Survival-Einstellungen
				mc.options.keyHotbarSlots[4].setKey(InputConstants.getKey("key.keyboard.q"));
				mc.options.keyDrop.setKey(InputConstants.UNKNOWN);
				mc.options.fov().set(100);
				net.minecraft.client.KeyMapping.resetMapping();
				KitProfiles.save(1);
				KitProfiles.switchTo(0);
			});
			ctx.waitTicks(3);
			String survival = ctx.computeOnClient(mc -> mc.options.keyDrop.saveString() + "|" + mc.options.keyHotbarSlots[4].saveString() + "|" + mc.options.fov().get());
			check("kit-survival", survival.equals("key.keyboard.q|key.keyboard.5|70"), survival);
			ctx.runOnClient(mc -> KitProfiles.switchTo(1));
			ctx.waitTicks(3);
			String cpvp = ctx.computeOnClient(mc -> mc.options.keyDrop.saveString() + "|" + mc.options.keyHotbarSlots[4].saveString() + "|" + mc.options.fov().get());
			check("kit-cpvp", cpvp.equals("key.keyboard.unknown|key.keyboard.q|100"), cpvp);
			ctx.runOnClient(mc -> KitProfiles.switchTo(0));
			ctx.waitTicks(3);

			// ------------------------------------------ Mods
			// Zoom
			ctx.getInput().holdKey(GLFW.GLFW_KEY_C);
			ctx.waitTicks(15);
			double zoom = ctx.computeOnClient(mc -> Zoom.factor());
			check("zoom", zoom > 3.0, String.format("%.2f", zoom));
			ctx.takeScreenshot("40-zoom");
			ctx.getInput().releaseKey(GLFW.GLFW_KEY_C);
			ctx.waitTicks(10);
			// Wetter aus
			sp.getServer().runCommand("weather thunder");
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> OemerConfig.get().noWeather = true);
			ctx.waitTicks(2);
			check("weather-off", ctx.computeOnClient(mc -> mc.level.getRainLevel(1f) == 0f), "");
			sp.getServer().runCommand("weather clear");
			// TNT Timer + Note Block Tuner
			ctx.runOnClient(mc -> {
				OemerConfig.get().tntTimer = true;
				OemerConfig.get().noteTuner = true;
				OemerConfig.get().fullbright = true;
			});
			sp.getServer().runCommand("setblock " + x + " " + (y + 1) + " " + (z + 2) + " note_block[note=7]");
			sp.getServer().runCommand("summon tnt " + (x + 1.5) + " " + y + " " + (z + 3.0) + " {fuse:200}");
			ctx.waitTicks(20);
			ctx.takeScreenshot("41-tnt-timer-note-tuner");
			sp.getServer().runCommand("kill @e[type=tnt]");
			sp.getServer().runCommand("setblock " + x + " " + (y + 1) + " " + (z + 2) + " air");
			// AppleSkin + Smaller Totem + Low Shield
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.appleSkin = true;
				c.smallerTotem = true;
				c.lowShield = true;
			});
			sp.getServer().runCommand("effect give @a hunger 3 200");
			ctx.waitTicks(70);
			sp.getServer().runCommand("item replace entity @a weapon.mainhand with cooked_beef 5");
			sp.getServer().runCommand("item replace entity @a weapon.offhand with shield");
			ctx.waitTicks(10);
			ctx.takeScreenshot("42-appleskin-schild");
			sp.getServer().runCommand("item replace entity @a weapon.mainhand with totem_of_undying");
			ctx.waitTicks(10);
			ctx.takeScreenshot("43-smaller-totem");
			// Shulker Box Tooltip (Daten)
			boolean shulker = ctx.computeOnClient(mc -> {
				OemerConfig.get().shulkerTooltip = true;
				ItemStack box = new ItemStack(Items.PURPLE_SHULKER_BOX);
				box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.TOTEM_OF_UNDYING, 1), new ItemStack(Items.END_CRYSTAL, 64))));
				return ShulkerPreview.of(box) != null;
			});
			check("shulker-tooltip", shulker, "");
			// Low Fire
			ctx.runOnClient(mc -> OemerConfig.get().lowFire = false);
			sp.getServer().runCommand("setblock " + x + " " + (y - 1) + " " + z + " netherrack");
			sp.getServer().runCommand("setblock " + x + " " + y + " " + z + " fire");
			ctx.waitTicks(40);
			ctx.takeScreenshot("44a-feuer-normal");
			ctx.runOnClient(mc -> OemerConfig.get().lowFire = true);
			ctx.waitTicks(3);
			ctx.takeScreenshot("44-low-fire");
			sp.getServer().runCommand("setblock " + x + " " + y + " " + z + " air");
			sp.getServer().runCommand("setblock " + x + " " + (y - 1) + " " + z + " grass_block");
			sp.getServer().runCommand("effect give @a fire_resistance 30 1");
			// Grafik-Stufen
			ctx.runOnClient(mc -> GraphicsPresets.apply(0));
			check("graphics-low", ctx.computeOnClient(mc -> mc.options.renderDistance().get() == 6), "");
			ctx.runOnClient(mc -> GraphicsPresets.apply(2));
			// Distant Horizons
			check("distant-horizons-loaded", ctx.computeOnClient(mc -> DistantHorizonsBridge.installed()), "");
			// Textur-Mods: Clear Glass, Dark GUI, NoBedrock
			sp.getServer().runCommand("fill " + (x - 2) + " " + (y) + " " + (z + 3) + " " + (x + 2) + " " + (y + 2) + " " + (z + 3) + " glass");
			sp.getServer().runCommand("setblock " + (x - 1) + " " + (y + 1) + " " + (z + 3) + " pink_stained_glass");
			sp.getServer().runCommand("setblock " + (x + 1) + " " + (y + 1) + " " + (z + 3) + " bedrock");
			// Netherportal (Obsidian-Rahmen + Portal) für Transparente Netherportale
			sp.getServer().runCommand("fill " + (x - 2) + " " + (y - 1) + " " + (z + 2) + " " + (x + 1) + " " + (y + 3) + " " + (z + 2) + " obsidian");
			sp.getServer().runCommand("fill " + (x - 1) + " " + y + " " + (z + 2) + " " + x + " " + (y + 2) + " " + (z + 2) + " nether_portal[axis=x]");
			ctx.waitTicks(10);
			ctx.takeScreenshot("45-glas-vorher");
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.clearGlass = true;
				c.darkGui = true;
				c.noBedrock = true;
				c.clearPortal = true;
				c.portalLevel = 1;
				LooksPack.apply(mc);
			});
			ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
			ctx.waitTicks(20);
			check("portal-texture-faded", ctx.computeOnClient(mc -> {
				try {
					var img = javax.imageio.ImageIO.read(mc.getResourcePackDirectory().resolve(LooksPack.FOLDER)
							.resolve("assets/minecraft/textures/block/nether_portal.png").toFile());
					int max = 0;
					for (int yy = 0; yy < img.getHeight(); yy++) for (int xx = 0; xx < img.getWidth(); xx++) max = Math.max(max, img.getRGB(xx, yy) >>> 24);
					return max > 0 && max <= 100;
				} catch (Exception e) {
					return false;
				}
			}), "");
			check("dark-gui-textures", ctx.computeOnClient(mc -> PackTweaks.countDark(mc)) > 20, "");
			check("pack-tweaks-written", ctx.computeOnClient(mc -> Files.isRegularFile(mc.getResourcePackDirectory()
					.resolve(LooksPack.FOLDER).resolve("assets/minecraft/textures/block/bedrock.png"))), "");
			ctx.takeScreenshot("46-clear-glass-nobedrock");
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			ctx.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
			ctx.waitTicks(5);
			ctx.takeScreenshot("47-dark-gui-inventar");
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.clearGlass = false;
				c.darkGui = false;
				c.noBedrock = false;
				c.clearPortal = false;
				c.fullbright = true;
			});
			sp.getServer().runCommand("fill " + (x - 2) + " " + (y - 1) + " " + (z + 2) + " " + (x + 1) + " " + (y + 3) + " " + (z + 2) + " air");

			// ------------------------------------------ No Explosion Particles (nur die Wolken fehlen)
			boolean[] expl = new boolean[2];
			ctx.runOnClient(mc -> {
				OemerConfig.get().noExplosionParticles = false;
				expl[0] = mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, x, y + 2, z + 6, 0, 0, 0) != null;
				OemerConfig.get().noExplosionParticles = true;
				expl[1] = mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, x, y + 2, z + 6, 0, 0, 0) == null
						&& mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, x, y + 2, z + 6, 0, 0, 0) == null;
				OemerConfig.get().noExplosionParticles = false;
			});
			check("explosion-particles-normal", expl[0], "");
			check("no-explosion-particles", expl[1], "");

			// ------------------------------------------ Own Custom Name (Farbe, Verlauf, Rainbow)
			boolean[] nm = new boolean[8];
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.customName = true;
				c.customNameText = "ÖmerKing";
				c.nameMode = 0;
				c.nameColor1 = 9;
				nm[0] = mc.player.getDisplayName().getString().equals("ÖmerKing");
				var one = NameStyle.display().toFlatList(net.minecraft.network.chat.Style.EMPTY);
				nm[1] = one.size() == 8 && one.stream().allMatch(s -> s.getStyle().getColor().getValue() == NameStyle.COLORS[9]);
				c.nameMode = 1;
				c.nameColor1 = 2;
				c.nameColor2 = 7;
				var grad = NameStyle.display().toFlatList(net.minecraft.network.chat.Style.EMPTY);
				nm[2] = grad.get(0).getStyle().getColor().getValue() == NameStyle.COLORS[2] && grad.get(7).getStyle().getColor().getValue() == NameStyle.COLORS[7];
				c.nameMode = 2;
				var rb = NameStyle.display().toFlatList(net.minecraft.network.chat.Style.EMPTY);
				nm[3] = rb.get(0).getStyle().getColor().getValue() != rb.get(4).getStyle().getColor().getValue();
				String real = mc.getUser().getName();
				var chat = NameStyle.replaceInChat(net.minecraft.network.chat.Component.translatable("chat.type.text",
						net.minecraft.network.chat.Component.literal(real), "Hallo"));
				nm[4] = chat.getString().contains("ÖmerKing") && !chat.getString().contains(real) && chat.getString().contains("Hallo");
				var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				nm[5] = info != null && mc.gui.getTabList().getNameForDisplay(info).getString().equals("ÖmerKing");
				c.nameInChat = false;
				nm[6] = NameStyle.replaceInChat(net.minecraft.network.chat.Component.literal("<" + real + "> hi")).getString().contains(real);
				c.nameInChat = true;
				c.customName = false;
				nm[7] = mc.player.getDisplayName().getString().equals(real);
				c.customName = true;
			});
			check("name-display", nm[0], "");
			check("name-one-color", nm[1], "");
			check("name-gradient", nm[2], "");
			check("name-rainbow", nm[3], "");
			check("name-chat", nm[4], "");
			check("name-tablist", nm[5], "");
			check("name-chat-off", nm[6], "");
			check("name-off-real", nm[7], "");
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.nameMode = 1;
				c.nameColor1 = 8;
				c.nameColor2 = 9;
				c.nameBold = true;
				mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.translatable("chat.type.text",
						net.minecraft.network.chat.Component.literal(mc.getUser().getName()), "Hallo Leute, mein Name ist jetzt bunt!"));
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			});
			ctx.waitTicks(10);
			ctx.getInput().holdKey(o -> o.keyPlayerList);
			ctx.waitTicks(5);
			ctx.takeScreenshot("48-eigener-name-tab-chat");
			ctx.getInput().releaseKey(o -> o.keyPlayerList);
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(10);
			ctx.takeScreenshot("49-eigener-name-schild");
			ctx.runOnClient(mc -> {
				mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
				OemerConfig.get().customName = false;
			});

			// ------------------------------------------ Sounds + Totem (Ressourcenpaket)
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.customSounds = true;
				c.soundSelection.put("treffer", "beep.ogg");
				c.customTotem = true;
				c.totemSelection = "test-totem.png";
				LooksPack.apply(mc);
			});
			ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
			ctx.waitTicks(10);
			boolean packOn = ctx.computeOnClient(mc -> mc.getResourcePackRepository().getSelectedIds().contains(LooksPack.PACK_ID));
			String compat = ctx.computeOnClient(mc -> {
				var pack = mc.getResourcePackRepository().getPack(LooksPack.PACK_ID);
				return pack == null ? "kein Pack" : String.valueOf(pack.getCompatibility());
			});
			check("looks-pack-active", packOn, compat);
			check("looks-pack-compatible", compat.equals("COMPATIBLE"), compat);
			String json = ctx.computeOnClient(mc -> {
				try {
					return Files.readString(LooksPack.soundsJson(mc));
				} catch (Exception e) {
					return "fehlt";
				}
			});
			check("sounds-json", json.contains("entity.player.attack.strong") && json.contains("oemerclient:custom/treffer"),
					json.length() > 60 ? json.substring(0, 60) : json);
			check("sounds-count", ctx.computeOnClient(mc -> Sounds.activeCount()) == 1, "");
			sp.getServer().runCommand("give @a totem_of_undying");
			ctx.waitTicks(10);
			ctx.takeScreenshot("30-totem");
			ctx.runOnClient(mc -> {
				OemerConfig.get().totemMode = 1;
				LooksPack.apply(mc);
			});
			ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
			ctx.waitTicks(10);
			ctx.takeScreenshot("30b-totem-skin");

			// ------------------------------------------ Menü im Spiel + Look-Modus
			ctx.runOnClient(mc -> {
				OemerConfig.get().lookMode = true;
				OemerConfig.get().lookImage = "test-look.png";
			});
			ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			ctx.waitTicks(10);
			check("menu-key-ingame", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
			ctx.takeScreenshot("31-menu-ingame-look");
			click(ctx, "Skin");
			click(ctx, "Cosmetics");
			ctx.waitTicks(10);
			ctx.takeScreenshot("32-menu-ingame-cosmetics-vorschau");
			click(ctx, "Kits");
			ctx.waitTicks(3);
			ctx.takeScreenshot("32c-menu-ingame-kits");
			ctx.runOnClient(mc -> {
				OemerConfig.get().lookMode = false;
				OemerConfig.get().designStyle = 1;
			});
			ctx.setScreen(() -> new OmerScreen(null));
			ctx.waitTicks(20);
			ctx.takeScreenshot("32b-galaxy-ingame");
			ctx.setScreen(() -> null);

			onlineWorldCheck(ctx);

			ctx.setScreen(() -> new PauseScreen(true));
			ctx.waitTicks(2);
			check("pause-button", ctx.computeOnClient(mc -> hasButton(mc, "Ömer Client")), "");
			ctx.takeScreenshot("33-pause");
			ctx.setScreen(() -> null);
		}

		// ---------------------------------------------- Startscreen mit Look
		ctx.runOnClient(mc -> {
			OemerConfig.get().lookMode = false;
			OemerConfig.get().lookTitle = true;
			OemerConfig.get().designStyle = 1;
		});
		ctx.waitForScreen(TitleScreen.class);
		ctx.waitTicks(30);
		ctx.takeScreenshot("34-startscreen-galaxy");
		click(ctx, "Ömer Client");
		ctx.waitTicks(15);
		ctx.takeScreenshot("35-galaxy-menu");
		click(ctx, "Skin");
		click(ctx, "Cape");
		ctx.takeScreenshot("36-galaxy-skins");
		ctx.setScreen(() -> new TitleScreen());
		ctx.runOnClient(mc -> {
			OemerConfig.get().lookMode = true;
			OemerConfig.get().lookImage = Looks.WEB + "3";
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot("37-startscreen-look");

		System.out.println("[OEMER-TEST] fertig, Fehler: " + failures.size());
		if (!failures.isEmpty()) {
			throw new AssertionError("Ömer-Client-Test fehlgeschlagen: " + failures);
		}
	}
}
