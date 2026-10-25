package com.oemer.client.test;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.FreeCamera;
import com.oemer.client.feature.Freecam;
import com.oemer.client.gui.OmerScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
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
		ctx.clickScreenButton("Ömer Client");
		check("menu-opens", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
		ctx.takeScreenshot("02-menu-settings");
		ctx.clickScreenButton("Skins");
		ctx.takeScreenshot("03-menu-skins");
		ctx.clickScreenButton("Layout");
		ctx.takeScreenshot("04-menu-layout");
		ctx.clickScreenButton("Graphics");
		ctx.takeScreenshot("05-menu-graphics");
		ctx.clickScreenButton("Fertig");
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

			// ------------------------------------------ Menü im Spiel
			ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			ctx.waitTicks(3);
			check("menu-key-ingame", ctx.computeOnClient(mc -> mc.screen instanceof OmerScreen), "");
			ctx.takeScreenshot("10-menu-ingame");
			ctx.setScreen(() -> null);

			ctx.setScreen(() -> new PauseScreen(true));
			ctx.waitTicks(2);
			check("pause-button", ctx.computeOnClient(mc -> hasButton(mc, "Ömer Client")), "");
			ctx.takeScreenshot("11-pause");
			ctx.setScreen(() -> null);
		}

		System.out.println("[OEMER-TEST] fertig, Fehler: " + failures.size());
		if (!failures.isEmpty()) {
			throw new AssertionError("Ömer-Client-Test fehlgeschlagen: " + failures);
		}
	}
}
