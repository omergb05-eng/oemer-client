package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;

/**
 * Schaltet die Free Cam an und aus.
 * Der Schalter im Menü und die Taste ändern nur {@link OemerConfig#freecam},
 * hier wird es dann jeden Tick umgesetzt.
 */
public final class Freecam {
	private Freecam() {
	}

	private static FreeCamera camera;
	private static ClientInput playerInput;
	private static ClientInput dummyInput;
	private static CameraType rememberedView;

	public static boolean isEnabled() {
		return camera != null;
	}

	public static FreeCamera camera() {
		return camera;
	}

	/** Wird am Anfang jedes Ticks aufgerufen. */
	public static void preTick(Minecraft mc) {
		boolean want = OemerConfig.get().freecam && mc.player != null && mc.level != null;
		if (want && !isEnabled()) {
			enable(mc);
		} else if (!want && isEnabled()) {
			disable(mc);
		}

		// Spieler bleibt stehen, solange die Free Cam an ist
		if (isEnabled() && mc.player != null) {
			mc.player.input = dummyInput;
		}
	}

	/** Bei Tod, Dimensionswechsel oder Verlassen vom Server. */
	public static void forceOff() {
		OemerConfig.get().freecam = false;
		if (isEnabled()) {
			disable(Minecraft.getInstance());
		}
	}

	private static void enable(Minecraft mc) {
		mc.smartCull = false;
		rememberedView = mc.options.getCameraType();

		playerInput = mc.player.input;
		dummyInput = new ClientInput();
		dummyInput.keyPresses = new Input(false, false, false, false, false, playerInput.keyPresses.shift(), false);
		mc.player.input = dummyInput;

		if (mc.gameRenderer.getMainCamera().isDetached()) {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		}

		camera = new FreeCamera(-4200);
		camera.copyFromPlayer(mc.player);
		camera.spawn();
		mc.setCameraEntity(camera);

		mc.player.sendOverlayMessage(Component.literal("§fFree Cam §aan"));
	}

	private static void disable(Minecraft mc) {
		mc.smartCull = true;
		if (mc.player != null) {
			mc.setCameraEntity(mc.player);
		}
		camera.despawn();
		camera = null;

		if (mc.player != null && playerInput != null) {
			mc.player.input = playerInput;
			mc.player.sendOverlayMessage(Component.literal("§fFree Cam §caus"));
		}
		playerInput = null;
		dummyInput = null;

		if (rememberedView != null) {
			mc.options.setCameraType(rememberedView);
			rememberedView = null;
		}
	}
}
