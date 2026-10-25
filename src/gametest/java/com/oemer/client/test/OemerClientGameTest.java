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

		// ---------------------------------------------- Menü: alle Tabs + Unter-Reiter
		click(ctx, "Ömer Client");
		check("menu-opens", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
		ctx.waitTicks(10);
		ctx.takeScreenshot("02-settings-allgemein");
		click(ctx, "Kits");
		ctx.takeScreenshot("03-settings-kits");
		click(ctx, "Material List");
		ctx.takeScreenshot("04-settings-material");
		click(ctx, "Skins");
		click(ctx, "Cape");
		ctx.takeScreenshot("05-skins-cape");
		click(ctx, "Cosmetics");
		ctx.takeScreenshot("06-skins-cosmetics");
		click(ctx, "Totem");
		ctx.takeScreenshot("07-skins-totem");
		click(ctx, "Sounds");
		ctx.takeScreenshot("08-skins-sounds");
		ctx.runOnClient(mc -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -10));
		ctx.waitTicks(3);
		ctx.takeScreenshot("08b-skins-sounds-gescrollt");
		click(ctx, "Layout");
		ctx.takeScreenshot("09-layout-design");
		click(ctx, "HUD");
		ctx.takeScreenshot("10-layout-hud");
		click(ctx, "Graphics");
		ctx.takeScreenshot("11-graphics-looks");

		// ---------------------------------------------- Cape-Editor
		click(ctx, "Skins");
		click(ctx, "Cape");
		click(ctx, "Cape-Editor öffnen");
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

			// ------------------------------------------ Cosmetics
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.cosHalo = true;
				c.cosWings = true;
				c.cosEars = true;
				c.cosColor = 0;
			});
			shotFreecam(ctx, 0, 0.9, -2.8, 0, 12, "23-cosmetics-hinten");
			shotFreecam(ctx, 0, 0.9, 2.6, 180, 12, "24-cosmetics-vorne");
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.cosHalo = false;
				c.cosEars = false;
				c.cosWings = false;
				c.cosCrown = true;
				c.cosHorns = false;
			});
			shotFreecam(ctx, 0, 1.2, 3.0, 180, 14, "25-cosmetics-krone");
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.cosCrown = false;
				c.cosTopHat = true;
			});
			shotFreecam(ctx, 0, 1.2, 3.0, 180, 14, "26-cosmetics-zylinder");
			ctx.runOnClient(mc -> {
				OemerConfig c = OemerConfig.get();
				c.cosTopHat = false;
				c.cosHorns = true;
				c.cosColor = 6;
			});
			shotFreecam(ctx, 0, 1.2, 3.0, 180, 14, "27-cosmetics-hoerner");
			ctx.runOnClient(mc -> OemerConfig.get().freecam = false);
			ctx.waitTicks(3);

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

			// ------------------------------------------ Menü im Spiel + Look-Modus
			ctx.runOnClient(mc -> {
				OemerConfig.get().lookMode = true;
				OemerConfig.get().lookImage = "test-look.png";
			});
			ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			ctx.waitTicks(10);
			check("menu-key-ingame", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
			ctx.takeScreenshot("31-menu-ingame-look");
			click(ctx, "Skins");
			click(ctx, "Cosmetics");
			ctx.waitTicks(5);
			ctx.takeScreenshot("32-menu-ingame-cosmetics-vorschau");
			ctx.runOnClient(mc -> OemerConfig.get().lookMode = false);
			ctx.setScreen(() -> null);

			ctx.setScreen(() -> new PauseScreen(true));
			ctx.waitTicks(2);
			check("pause-button", ctx.computeOnClient(mc -> hasButton(mc, "Ömer Client")), "");
			ctx.takeScreenshot("33-pause");
			ctx.setScreen(() -> null);
		}

		// ---------------------------------------------- Startscreen mit Look
		ctx.runOnClient(mc -> {
			OemerConfig.get().lookMode = true;
			OemerConfig.get().lookTitle = true;
		});
		ctx.waitForScreen(TitleScreen.class);
		ctx.waitTicks(10);
		ctx.takeScreenshot("34-startscreen-look");

		System.out.println("[OEMER-TEST] fertig, Fehler: " + failures.size());
		if (!failures.isEmpty()) {
			throw new AssertionError("Ömer-Client-Test fehlgeschlagen: " + failures);
		}
	}
}
