package com.oemer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.oemer.client.feature.ChestMarker;
import com.oemer.client.feature.Cosmetics;
import com.oemer.client.feature.DistantHorizonsBridge;
import com.oemer.client.feature.ModHud;
import com.oemer.client.feature.ShulkerPreview;
import com.oemer.client.feature.YouTubeMusic;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import com.oemer.client.feature.Crosshair;
import com.oemer.client.feature.GalaxyArt;
import com.oemer.client.feature.SkinTotem;
import com.oemer.client.feature.Spotify;
import com.oemer.client.gui.Backdrop;
import com.oemer.client.gui.SpotifyScreen;
import com.oemer.client.feature.Freecam;
import com.oemer.client.feature.Hud;
import com.oemer.client.feature.Looks;
import com.oemer.client.feature.Sounds;
import com.oemer.client.gui.OmerScreen;
import com.oemer.client.gui.OmerWidgets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.gui.screen().v1.ScreenEvents;
import net.fabricmc.fabric.api.client.gui.screen().v1.Screens;
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
			.orElse("6.0.0");

	@Override
	public void onInitializeClient() {
		OemerConfig.load();
		createFolders();
		Sounds.createFolders();
		OemerKeys.register();

		// Galaxy-Bild und Skin schon im Hintergrund vorbereiten
		GalaxyArt.prepare();
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			SkinTotem.prepare();
			YouTubeMusic.start();
			DistantHorizonsBridge.disableUpdater();
		});

		// Spotify: nur abfragen, wenn man es sieht
		Spotify.init(() -> {
			Minecraft mc = Minecraft.getInstance();
			return (OemerConfig.get().spotifyHud && mc.level != null)
					|| mc.gui.screen() instanceof OmerScreen || mc.gui.screen() instanceof SpotifyScreen;
		});

		// Eigenes Crosshair statt dem normalen
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, tracker) -> {
			if (!Crosshair.render(graphics, tracker)) {
				original.render(graphics, tracker);
			}
		});

		// Kit-HUD + Material List im Spiel
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), Hud::render);
		// AppleSkin + Note Block Tuner
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Identifier.fromNamespaceAndPath(MOD_ID, "appleskin"), ModHud::food);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "notetuner"), ModHud::noteTuner);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> ModHud.foodTooltip(stack, lines));
		TooltipComponentCallback.EVENT.register(data -> data instanceof ShulkerPreview.Data d ? new ShulkerPreview.Client(d) : null);

		// Material List: Kisten markieren
		ChestMarker.register();

		// Cosmetics an den Spieler-Renderer hängen
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer avatar) {
				helper.register(new Cosmetics.Layer(avatar));
			}
		});

		ClientTickEvents.START_CLIENT_TICK.register(Freecam::preTick);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			OemerKeys.tick(mc);
			DistantHorizonsBridge.tick();
			com.oemer.client.feature.SchematicPlacer.tick(mc);
			com.oemer.client.feature.Clipper.tick(mc);
			com.oemer.client.gui.OmerLoading.tick(mc);
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				addTitleButton(client, screen);
				// Look-Modus: dein Bild statt Panorama
				ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> {
					if (OemerConfig.get().lookTitle) {
						if (!Looks.drawFullscreen(g, s.width, s.height) && OemerConfig.get().designStyle == 1) {
							Backdrop.drawGalaxy(g, s.width, s.height, (System.currentTimeMillis() % 1_000_000L) / 1000f, 0x20);
						}
					}
				});
			} else if (screen instanceof PauseScreen) {
				Screens.getButtons(screen).add(new OmerWidgets.Flat(6, 6, 90, 20, "Ömer Client",
						() -> client.gui.setScreen(new OmerScreen(screen))));
			}
		});

		com.oemer.client.online.Social.start();
		com.oemer.client.feature.SchematicPlacer.init();

		LOGGER.info("Ömer Client {} geladen", VERSION);
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
					() -> client.gui.setScreen(new OmerScreen(screen))));
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
				"Ömer Client", () -> client.gui.setScreen(new OmerScreen(screen))).style(OmerWidgets.Style.PRIMARY));
	}

	/** Ordner für eigene Bilder & Sounds (für die nächsten Versionen). */
	private static void createFolders() {
		Path base = FabricLoader.getInstance().getConfigDir().resolve("oemerclient");
		for (String name : new String[]{"capes", "totems", "looks", "sounds", "schematics", "cosmetics", "crosshairs", "kits"}) {
			try {
				Files.createDirectories(base.resolve(name));
			} catch (Exception e) {
				LOGGER.warn("Ordner {} konnte nicht erstellt werden", name, e);
			}
		}
	}
}
