package com.oemer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.oemer.client.feature.KitProfiles;
import com.oemer.client.feature.Music;
import com.oemer.client.gui.MaterialListScreen;
import com.oemer.client.gui.OmerScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

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
			cosmeticsToggle, crosshairToggle, weatherToggle, musicPlay, musicNext, musicPrev, musicOpen;
	public static final KeyMapping[] KIT = new KeyMapping[OemerConfig.KITS.length];

	private static KeyMapping.Category category;

	private static KeyMapping key(String id, int glfw, String label, String group) {
		KeyMapping k = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.oemerclient." + id,
				InputConstants.Type.KEYSYM, glfw, category));
		ALL.add(new Entry(k, label, group));
		return k;
	}

	public static void register() {
		category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(OemerClient.MOD_ID, "main"));
		int none = GLFW.GLFW_KEY_UNKNOWN;
		menu = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT, "Menü öffnen", "Allgemein");
		freecam = key("freecam", GLFW.GLFW_KEY_F4, "Free Cam an/aus", "Mods");
		fullbright = key("fullbright", GLFW.GLFW_KEY_G, "Fullbright an/aus", "Mods");
		zoom = key("zoom", GLFW.GLFW_KEY_C, "Zoom (halten)", "Mods");
		weatherToggle = key("weather", none, "Wetter an/aus", "Mods");
		crosshairToggle = key("crosshair", none, "Eigenes Crosshair an/aus", "Mods");
		cosmeticsToggle = key("cosmetics", none, "Cosmetics an/aus", "Mods");
		materialToggle = key("material_toggle", none, "Material List an/aus", "Material List");
		materialOpen = key("material_open", none, "Material List öffnen", "Material List");
		kitNext = key("kit_next", GLFW.GLFW_KEY_K, "Nächstes Kit", "Kits");
		for (int i = 0; i < KIT.length; i++) {
			KIT[i] = key("kit_" + (i + 1), none, "Kit: " + OemerConfig.KITS[i], "Kits");
		}
		musicPlay = key("music_play", none, "Musik Play/Pause", "Musik");
		musicNext = key("music_next", none, "Nächster Song", "Musik");
		musicPrev = key("music_prev", none, "Vorheriger Song", "Musik");
		musicOpen = key("music_open", none, "Musik-Player öffnen", "Musik");
	}

	private static void say(Minecraft mc, String name, boolean on) {
		if (mc.player != null) mc.player.displayClientMessage(Component.literal("§f" + name + (on ? " §aan" : " §caus")), true);
	}

	public static void tick(Minecraft mc) {
		OemerConfig c = OemerConfig.get();
		while (menu.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new OmerScreen(null));
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
			if (mc.screen == null) mc.setScreen(new MaterialListScreen(null));
		}
		while (kitNext.consumeClick()) KitProfiles.next();
		for (int i = 0; i < KIT.length; i++) {
			while (KIT[i].consumeClick()) KitProfiles.switchTo(i);
		}
		while (musicPlay.consumeClick()) Music.playPause();
		while (musicNext.consumeClick()) Music.next();
		while (musicPrev.consumeClick()) Music.previous();
		while (musicOpen.consumeClick()) {
			if (mc.screen == null) OmerScreen.openMusic();
		}
	}
}
