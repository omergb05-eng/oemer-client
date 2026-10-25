package com.oemer.client.test;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.FreeCamera;
import com.oemer.client.feature.Freecam;
import com.oemer.client.feature.ImageTools;
import com.oemer.client.feature.Looks;
import com.oemer.client.feature.LooksPack;
import com.oemer.client.feature.MaterialList;
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

import java.io.InputStream;
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
			shotFreecam(ctx, 2.2, 0.9, -1.6, -55, 10, "23b-cosmetics-fluegel-seite");
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
			shotFreecam(ctx, 2.5, 1.5, -1.0, -35, 25, "29c-kiste-leuchtet");
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
			ctx.runOnClient(mc -> OemerConfig.get().lowFire = true);
			sp.getServer().runCommand("setblock " + x + " " + y + " " + z + " fire");
			ctx.waitTicks(15);
			ctx.takeScreenshot("44-low-fire");
			sp.getServer().runCommand("setblock " + x + " " + y + " " + z + " air");
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
			ctx.waitTicks(10);
			ctx.takeScreenshot("45-glas-vorher");
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.clearGlass = true;
				c.darkGui = true;
				c.noBedrock = true;
				LooksPack.apply(mc);
			});
			ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
			ctx.waitTicks(20);
			check("dark-gui-textures", ctx.computeOnClient(mc -> PackTweaks.countDark(mc)) > 20, "");
			check("pack-tweaks-written", ctx.computeOnClient(mc -> Files.isRegularFile(mc.getResourcePackDirectory()
					.resolve(LooksPack.FOLDER).resolve("assets/minecraft/textures/block/bedrock.png"))), "");
			ctx.takeScreenshot("46-clear-glass-nobedrock");
			ctx.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
			ctx.waitTicks(5);
			ctx.takeScreenshot("47-dark-gui-inventar");
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.clearGlass = false;
				c.darkGui = false;
				c.noBedrock = false;
				c.fullbright = true;
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
