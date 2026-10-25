package com.oemer.client.test;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.ImageTools;
import com.oemer.client.feature.LooksPack;
import com.oemer.client.feature.FreeCamera;
import com.oemer.client.feature.Freecam;
import com.oemer.client.gui.OmerScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

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
		ctx.waitTicks(2);
	}

	/** Buntes Testbild (Verlauf mit Kreis). */
	private static java.awt.image.BufferedImage testImage() {
		var img = new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		var g = img.createGraphics();
		g.setPaint(new java.awt.GradientPaint(0, 0, java.awt.Color.PINK, 400, 300, java.awt.Color.MAGENTA));
		g.fillRect(0, 0, 400, 300);
		g.setColor(java.awt.Color.WHITE);
		g.fillOval(120, 70, 160, 160);
		g.dispose();
		return img;
	}

	private static boolean hasButton(Minecraft mc, String text) {
		return mc.screen != null && Screens.getButtons(mc.screen).stream()
				.anyMatch(w -> w.getMessage().getString().equals(text));
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		// ---------------------------------------------- Startscreen
		ctx.waitForScreen(TitleScreen.class);
		check("startscreen-button", ctx.computeOnClient(mc -> hasButton(mc, "Ömer Client")), "");
		ctx.takeScreenshot("01-startscreen");

		// ---------------------------------------------- Menü + Tabs
		click(ctx, "Ömer Client");
		check("menu-opens", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
		ctx.takeScreenshot("02-menu-settings");
		click(ctx, "Skins");
		ctx.takeScreenshot("03-menu-skins");
		click(ctx, "Layout");
		ctx.takeScreenshot("04-menu-layout");
		click(ctx, "Graphics");
		ctx.takeScreenshot("05-menu-graphics");
		click(ctx, "Fertig");
		check("menu-closes", ctx.computeOnClient(mc -> mc.screen instanceof TitleScreen), "");

		// ---------------------------------------------- Welt
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("time set midnight");
			ctx.waitTicks(20);

			// Platz schaffen und eine Steinwand 4 Blöcke vor dem Spieler (Richtung Süden)
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
			ctx.takeScreenshot("06-welt-nacht");

			// ------------------------------------------ Fullbright (Taste G)
			ctx.getInput().pressKey(GLFW.GLFW_KEY_G);
			ctx.waitTicks(5);
			check("fullbright-key", ctx.computeOnClient(mc -> OemerConfig.get().fullbright), "");
			ctx.takeScreenshot("07-fullbright");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_G);
			ctx.waitTicks(5);
			check("fullbright-off", ctx.computeOnClient(mc -> !OemerConfig.get().fullbright), "");

			// ------------------------------------------ Free Cam (Taste F4)
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
			ctx.takeScreenshot("08-freecam");

			// Hochfliegen und zurückschauen, damit man den eigenen Spieler sieht
			ctx.getInput().holdKeyFor(o -> o.keyJump, 20);
			ctx.runOnClient(mc -> {
				if (Freecam.isEnabled()) {
					Freecam.camera().setYRot(180);
					Freecam.camera().setXRot(45);
				}
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("09-freecam-spieler");

			ctx.getInput().pressKey(GLFW.GLFW_KEY_F4);
			ctx.waitTicks(3);
			check("freecam-off", ctx.computeOnClient(mc -> !Freecam.isEnabled() && mc.getCameraEntity() == mc.player), "");

			// ------------------------------------------ Cape
			ctx.runOnClient(mc -> {
				OemerConfig.get().fullbright = true; // damit man nachts das Cape sieht
				OemerConfig.get().customCape = true;
				OemerConfig.get().capeSelection = "design:1";
				CapeManager.invalidate();
			});
			ctx.waitTicks(3);
			String capePath = ctx.computeOnClient(mc -> {
				var cape = mc.player.getSkin().cape();
				return cape == null ? "null" : cape.texturePath().toString();
			});
			check("cape-design", capePath.startsWith("oemerclient:cape/"), capePath);

			// eigenes Bild als Cape
			ctx.runOnClient(mc -> {
				try {
					java.nio.file.Files.createDirectories(CapeManager.folder());
					java.nio.file.Files.write(CapeManager.folder().resolve("test-bild.png"), ImageTools.toPng(testImage()));
					java.nio.file.Files.createDirectories(LooksPack.totemFolder());
					java.nio.file.Files.write(LooksPack.totemFolder().resolve("test-totem.png"), ImageTools.toPng(testImage()));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				OemerConfig.get().capeSelection = "test-bild.png";
				CapeManager.invalidate();
			});
			ctx.waitTicks(3);
			capePath = ctx.computeOnClient(mc -> {
				var cape = mc.player.getSkin().cape();
				return cape == null ? "null" : cape.texturePath().toString();
			});
			check("cape-image", capePath.startsWith("oemerclient:cape/"), capePath);

			// Kamera hinter den Spieler, damit man das Cape sieht
			ctx.getInput().pressKey(GLFW.GLFW_KEY_F4);
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> {
				if (Freecam.isEnabled()) {
					Freecam.camera().snapTo(mc.player.getX(), mc.player.getY() + 0.6, mc.player.getZ() - 2.6, 0, 15);
				}
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("10-cape-bild");
			ctx.runOnClient(mc -> {
				OemerConfig.get().capeSelection = "design:3";
				CapeManager.invalidate();
			});
			ctx.waitTicks(3);
			ctx.takeScreenshot("11-cape-design");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_F4);
			ctx.waitTicks(3);

			// ------------------------------------------ Totem
			ctx.runOnClient(mc -> {
				OemerConfig.get().customTotem = true;
				OemerConfig.get().totemSelection = "test-totem.png";
				LooksPack.apply(mc);
			});
			ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
			ctx.waitTicks(10);
			boolean packOn = ctx.computeOnClient(mc -> mc.getResourcePackRepository().getSelectedIds().contains(LooksPack.PACK_ID));
			check("totem-pack-active", packOn, "");
			sp.getServer().runCommand("give @a totem_of_undying");
			ctx.waitTicks(10);
			ctx.takeScreenshot("12-totem");

			// ------------------------------------------ Menü im Spiel
			ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			ctx.waitTicks(3);
			check("menu-key-ingame", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
			click(ctx, "Skins");
			ctx.takeScreenshot("13-menu-ingame-skins");
			ctx.setScreen(() -> null);

			ctx.setScreen(() -> new PauseScreen(true));
			ctx.waitTicks(2);
			check("pause-button", ctx.computeOnClient(mc -> hasButton(mc, "Ömer Client")), "");
			ctx.takeScreenshot("14-pause");
			ctx.setScreen(() -> null);
		}

		System.out.println("[OEMER-TEST] fertig, Fehler: " + failures.size());
		if (!failures.isEmpty()) {
			throw new AssertionError("Ömer-Client-Test fehlgeschlagen: " + failures);
		}
	}
}
