package com.oemer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.oemer.client.feature.Cosmetics;
import com.oemer.client.feature.Freecam;
import com.oemer.client.feature.Hud;
import com.oemer.client.feature.Looks;
import com.oemer.client.feature.Sounds;
import com.oemer.client.gui.OmerScreen;
import com.oemer.client.gui.OmerWidgets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class OemerClient implements ClientModInitializer {
	public static final String MOD_ID = "oemerclient";
	public static final Logger LOGGER = LoggerFactory.getLogger("Ömer Client");
	public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("4.0.0");

	private static KeyMapping menuKey;
	private static KeyMapping freecamKey;
	private static KeyMapping fullbrightKey;

	@Override
	public void onInitializeClient() {
		OemerConfig.load();
		createFolders();
		Sounds.createFolders();
		registerKeys();

		// Kit-HUD + Material List im Spiel
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), Hud::render);

		// Cosmetics an den Spieler-Renderer hängen
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer avatar) {
				helper.register(new Cosmetics.Layer(avatar));
			}
		});

		ClientTickEvents.START_CLIENT_TICK.register(Freecam::preTick);
		ClientTickEvents.END_CLIENT_TICK.register(OemerClient::handleKeys);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				addTitleButton(client, screen);
				// Look-Modus: dein Bild statt Panorama
				ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> {
					if (OemerConfig.get().lookTitle) {
						Looks.drawFullscreen(g, s.width, s.height);
					}
				});
			} else if (screen instanceof PauseScreen) {
				Screens.getButtons(screen).add(new OmerWidgets.Flat(6, 6, 90, 20, "Ömer Client",
						() -> client.setScreen(new OmerScreen(screen))));
			}
		});

		LOGGER.info("Ömer Client {} geladen", VERSION);
	}

	// ------------------------------------------------------------ Tasten

	private static void registerKeys() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.oemerclient.menu",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		freecamKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.oemerclient.freecam",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F4, category));
		fullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.oemerclient.fullbright",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
	}

	private static void handleKeys(Minecraft mc) {
		OemerConfig c = OemerConfig.get();

		while (menuKey.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new OmerScreen(null));
			}
		}

		while (freecamKey.consumeClick()) {
			if (mc.player != null) {
				c.freecam = !c.freecam;
			}
		}

		while (fullbrightKey.consumeClick()) {
			c.fullbright = !c.fullbright;
			OemerConfig.save();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal(c.fullbright ? "§fFullbright §aan" : "§fFullbright §caus"), true);
			}
		}
	}

	// ------------------------------------------------------- Startscreen

	/** Setzt "Ömer Client" direkt unter den Multiplayer-Button. */
	private static void addTitleButton(Minecraft client, Screen screen) {
		List<AbstractWidget> buttons = Screens.getButtons(screen);
		String multiplayer = Component.translatable("menu.multiplayer").getString();

		AbstractWidget mp = null;
		for (AbstractWidget w : buttons) {
			if (w.getMessage().getString().equals(multiplayer)) {
				mp = w;
				break;
			}
		}

		if (mp == null) {
			// Falls Minecraft das Menü ändert: oben links anzeigen
			buttons.add(new OmerWidgets.Flat(6, 6, 90, 20, "Ömer Client",
					() -> client.setScreen(new OmerScreen(screen))));
			return;
		}

		int step = 24;
		int mpY = mp.getY();
		// Alles unter Multiplayer ein Stück nach unten schieben (nicht die Fußzeile)
		for (AbstractWidget w : buttons) {
			if (w.getY() > mpY && w.getY() < screen.height - 30) {
				w.setY(w.getY() + step);
			}
		}

		buttons.add(new OmerWidgets.Flat(mp.getX(), mpY + step, mp.getWidth(), mp.getHeight(),
				"Ömer Client", () -> client.setScreen(new OmerScreen(screen))));
	}

	/** Ordner für eigene Bilder & Sounds (für die nächsten Versionen). */
	private static void createFolders() {
		Path base = FabricLoader.getInstance().getConfigDir().resolve("oemerclient");
		for (String name : new String[]{"capes", "totems", "looks", "sounds", "schematics"}) {
			try {
				Files.createDirectories(base.resolve(name));
			} catch (Exception e) {
				LOGGER.warn("Ordner {} konnte nicht erstellt werden", name, e);
			}
		}
	}
}
