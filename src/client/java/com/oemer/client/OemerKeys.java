package com.oemer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.oemer.client.feature.Clipper;
import com.oemer.client.feature.KitProfiles;
import com.oemer.client.feature.Music;
import com.oemer.client.feature.Spotify;
import com.oemer.client.feature.SchematicPlacer;
import com.oemer.client.gui.MaterialListScreen;
import com.oemer.client.gui.OmerScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Alle Tasten vom Ömer Client – frei belegbar im Menü unter Kits → Tasten oder Settings → Tasten. */
public final class OemerKeys {
	private OemerKeys() {
	}

	public record Entry(KeyMapping key, String label, String group) {
	}

	public static final List<Entry> ALL = new ArrayList<>();

	public static KeyMapping menu, freecam, fullbright, zoom, kitNext, materialToggle, materialOpen,
			cosmeticsToggle, crosshairToggle, weatherToggle, musicPlay, musicNext, musicPrev, musicOpen,
			placeToggle, placeLayerUp, placeLayerDown, placeHere, clipSave, clipToggle, musicVolUp, musicVolDown, chestSearch;
	public static final KeyMapping[] KIT = new KeyMapping[OemerConfig.KITS.length];

	private static KeyMapping.Category category;

	private static KeyMapping key(String id, int glfw, String label, String group) {
		KeyMapping k = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.oemerclient." + id,
				InputConstants.Type.KEYBOARD, glfw, category));
		ALL.add(new Entry(k, label, group));
		return k;
	}

	public static void register() {
		category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "main"));
		int none = InputConstants.UNKNOWN.getValue();
		menu = key("menu", InputConstants.KEY_RSHIFT, "Menü öffnen", "Allgemein");
		freecam = key("freecam", InputConstants.KEY_F4, "Free Cam an/aus", "Mods");
		fullbright = key("fullbright", InputConstants.KEY_G, "Fullbright an/aus", "Mods");
		zoom = key("zoom", InputConstants.KEY_C, "Zoom (halten)", "Mods");
		weatherToggle = key("weather", none, "Wetter an/aus", "Mods");
		crosshairToggle = key("crosshair", none, "Eigenes Crosshair an/aus", "Mods");
		cosmeticsToggle = key("cosmetics", none, "Cosmetics an/aus", "Mods");
		materialToggle = key("material_toggle", none, "Material List an/aus", "Material List");
		materialOpen = key("material_open", none, "Material List öffnen", "Material List");
		kitNext = key("kit_next", InputConstants.KEY_K, "Nächstes Kit", "Kits");
		for (int i = 0; i < KIT.length; i++) {
			KIT[i] = key("kit_" + (i + 1), none, "Kit: " + OemerConfig.KITS[i], "Kits");
		}
		musicPlay = key("music_play", none, "Musik Play/Pause", "Musik");
		musicNext = key("music_next", none, "Nächster Song", "Musik");
		musicPrev = key("music_prev", none, "Vorheriger Song", "Musik");
		musicOpen = key("music_open", none, "Musik-Player öffnen", "Musik");
		musicVolUp = key("music_vol_up", InputConstants.KEY_UP, "Musik lauter", "Musik");
		musicVolDown = key("music_vol_down", InputConstants.KEY_DOWN, "Musik leiser", "Musik");
		clipSave = key("clip_save", InputConstants.KEY_F9, "Clip speichern (letzte Sekunden)", "Clipper");
		clipToggle = key("clip_toggle", none, "Clipper an/aus", "Clipper");
		placeToggle = key("place_toggle", none, "Schematic anzeigen an/aus", "Schematic");
		placeLayerUp = key("place_layer_up", InputConstants.KEY_PAGEUP, "Ebene höher", "Schematic");
		placeLayerDown = key("place_layer_down", InputConstants.KEY_PAGEDOWN, "Ebene tiefer", "Schematic");
		placeHere = key("place_here", none, "Schematic hier setzen", "Schematic");
		chestSearch = key("chest_search", InputConstants.KEY_J, "Chest Search: Items wählen", "Mods");
	}

	private static void say(Minecraft mc, String name, boolean on) {
		if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("§f" + name + (on ? " §aan" : " §caus")));
	}

	private static void volume(Minecraft mc, int delta) {
		if (mc.gui.screen() != null || mc.player == null) return;
		int v;
		if (Music.youtube()) {
			Music.volume(delta);
			v = OemerConfig.get().ytVolume;
		} else {
			Spotify.Track t = Spotify.track();
			v = Math.max(0, Math.min(100, (t == null || t.volume() < 0 ? 50 : t.volume()) + delta));
			Music.volume(delta);
		}
		mc.player.sendOverlayMessage(Component.literal("§fMusik-Lautstärke §b" + v + " %"));
	}

	public static void tick(Minecraft mc) {
		OemerConfig c = OemerConfig.get();
		while (menu.consumeClick()) {
			if (mc.gui.screen() == null) mc.gui.setScreen(new OmerScreen(null));
		}
		while (freecam.consumeClick()) {
			if (mc.player != null) c.freecam = !c.freecam;
		}
		while (fullbright.consumeClick()) {
			c.fullbright = !c.fullbright;
			OemerConfig.save();
			say(mc, "Fullbright", c.fullbright);
		}
		while (weatherToggle.consumeClick()) {
			c.noWeather = !c.noWeather;
			OemerConfig.save();
			say(mc, "Wetter aus", c.noWeather);
		}
		while (crosshairToggle.consumeClick()) {
			c.customCrosshair = !c.customCrosshair;
			OemerConfig.save();
			say(mc, "Crosshair", c.customCrosshair);
		}
		while (cosmeticsToggle.consumeClick()) {
			c.cosmeticsOn = !c.cosmeticsOn;
			OemerConfig.save();
			say(mc, "Cosmetics", c.cosmeticsOn);
		}
		while (materialToggle.consumeClick()) {
			c.materialList = !c.materialList;
			OemerConfig.save();
			say(mc, "Material List", c.materialList);
		}
		while (materialOpen.consumeClick()) {
			if (mc.gui.screen() == null) mc.gui.setScreen(new MaterialListScreen(null));
		}
		while (clipSave.consumeClick()) {
			if (mc.level != null) Clipper.saveRequested(mc);
		}
		while (clipToggle.consumeClick()) {
			c.clipperOn = !c.clipperOn;
			OemerConfig.save();
			say(mc, "Clipper", c.clipperOn);
		}
		while (placeToggle.consumeClick()) {
			c.placeOn = !c.placeOn;
			OemerConfig.save();
			say(mc, "Schematic", c.placeOn);
		}
		while (placeLayerUp.consumeClick()) {
			if (mc.gui.screen() == null && c.placeOn) SchematicPlacer.layer(1);
		}
		while (placeLayerDown.consumeClick()) {
			if (mc.gui.screen() == null && c.placeOn) SchematicPlacer.layer(-1);
		}
		while (placeHere.consumeClick()) {
			if (mc.level != null) {
				if (!SchematicPlacer.setLooking(mc)) SchematicPlacer.setHere(mc);
				say(mc, "Schematic gesetzt", true);
			}
		}
		while (chestSearch.consumeClick()) {
			if (mc.gui.screen() == null && mc.level != null) {
				if (c.chestSearch) mc.gui.setScreen(new com.oemer.client.gui.ChestSearchScreen(null));
				else say(mc, "Chest Search", false);
			}
		}
		while (kitNext.consumeClick()) KitProfiles.next();
		for (int i = 0; i < KIT.length; i++) {
			while (KIT[i].consumeClick()) KitProfiles.switchTo(i);
		}
		while (musicPlay.consumeClick()) Music.playPause();
		while (musicNext.consumeClick()) Music.next();
		while (musicPrev.consumeClick()) Music.previous();
		while (musicVolUp.consumeClick()) volume(mc, 5);
		while (musicVolDown.consumeClick()) volume(mc, -5);
		while (musicOpen.consumeClick()) {
			if (mc.gui.screen() == null) OmerScreen.openMusic();
		}
	}
}
