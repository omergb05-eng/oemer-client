package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Ressourcenpaket "Ömer Client Looks" in .minecraft/resourcepacks/.
 * Der Client baut es selbst aus deinen Bildern (gerade: das Totem) und schaltet es ein.
 */
public final class LooksPack {
	private LooksPack() {
	}

	public static final String FOLDER = "Oemer Client Looks";
	/** Auflösung vom Totem im Ressourcenpaket. */
	public static final int TOTEM_SIZE = 256;
	public static final String PACK_ID = "file/" + FOLDER;
	private static final String TOTEM_PATH = "assets/minecraft/textures/item/totem_of_undying.png";

	public static Path totemFolder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("totems");
	}

	public static List<String> totemChoices() {
		return CapeManager.imageFiles(totemFolder());
	}

	public static String displayName(String file) {
		return file.isEmpty() ? "(kein Bild)" : CapeManager.shortName(file);
	}

	/** true, wenn ein Totem-Bild ausgewählt ist und existiert. */
	private static boolean totemActive() {
		OemerConfig c = OemerConfig.get();
		if (!c.customTotem) return false;
		if (c.totemMode == 1 || c.totemMode == 2) return true; // Mein Skin / Vorlage
		return !c.totemSelection.isEmpty() && Files.isRegularFile(totemFolder().resolve(c.totemSelection));
	}

	/** Das fertige Totem-Bild (64x64) für die aktuelle Einstellung. */
	public static java.awt.image.BufferedImage totemImage() throws java.io.IOException {
		OemerConfig c = OemerConfig.get();
		// 256x256 statt 64x64: Fotos bleiben scharf, Pixel-Art wird scharf vergrößert
		if (c.totemMode == 1) return ImageTools.squareContain(SkinTotem.image(), TOTEM_SIZE);
		if (c.totemMode == 2) return PresetArt.totem(c.totemPreset, TOTEM_SIZE);
		var src = ImageTools.read(totemFolder().resolve(c.totemSelection));
		return c.totemFit == 1 ? ImageTools.squareCrop(src, TOTEM_SIZE) : ImageTools.squareContain(src, TOTEM_SIZE);
	}

	/**
	 * Baut das Paket neu und lädt die Ressourcen neu (dauert ein paar Sekunden).
	 * Nur aufrufen, wenn sich wirklich etwas geändert hat.
	 */
	public static void apply(Minecraft mc) {
		try {
			Path dir = mc.getResourcePackDirectory().resolve(FOLDER);
			boolean totem = totemActive();

			Files.createDirectories(dir);
			Files.writeString(dir.resolve("pack.mcmeta"), mcmeta(), StandardCharsets.UTF_8);

			// ---- Totem
			Path totemFile = dir.resolve(TOTEM_PATH);
			if (totem) {
				Files.createDirectories(totemFile.getParent());
				Files.write(totemFile, ImageTools.toPng(totemImage()));
			} else {
				Files.deleteIfExists(totemFile);
			}

			// ---- Sounds: jede ausgewählte Ogg-Datei + sounds.json, das die Original-Sounds ersetzt
			Path soundDir = dir.resolve("assets/oemerclient/sounds/custom");
			Path soundsJson = dir.resolve("assets/minecraft/sounds.json");
			StringBuilder json = new StringBuilder("{\n");
			int sounds = 0;
			Files.createDirectories(soundDir);
			for (Sounds.Slot slot : Sounds.SLOTS) {
				Path target = soundDir.resolve(slot.id() + ".ogg");
				Path src = Sounds.active(slot);
				if (src == null) {
					Files.deleteIfExists(target);
					continue;
				}
				Files.copy(src, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				for (String event : slot.events()) {
					if (sounds++ > 0) json.append(",\n");
					json.append("  \"").append(event).append("\": {\"replace\": true, \"sounds\": [{\"name\": \"oemerclient:custom/")
							.append(slot.id()).append("\"}]}");
				}
			}
			json.append("\n}\n");
			if (sounds > 0) {
				Files.createDirectories(soundsJson.getParent());
				Files.writeString(soundsJson, json.toString(), StandardCharsets.UTF_8);
			} else {
				Files.deleteIfExists(soundsJson);
			}

			// ---- Textur-Mods: Clear Glass, Dark GUI, No Bedrock
			int tweaks = PackTweaks.write(mc, dir);

			boolean active = totem || sounds > 0 || tweaks > 0;
			PackRepository repo = mc.getResourcePackRepository();
			repo.reload();
			List<String> selected = new ArrayList<>(repo.getSelectedIds());
			List<String> before = new ArrayList<>(selected);
			boolean was = selected.remove(PACK_ID);
			if (active) {
				selected.add(PACK_ID); // ganz oben = gewinnt gegen andere Pakete
			}
			repo.setSelected(selected);
			if (!selected.equals(before)) {
				mc.options.updateResourcePacks(repo);
			} else if (active || was) {
				// gleiche Pakete, aber neuer Inhalt (anderes Totem/Sound) → trotzdem neu laden
				mc.reloadResourcePacks();
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Ömer Client Looks konnte nicht gebaut werden", e);
		}
	}

	/** Pfad der eingebauten Sound-Datei im Paket (für den Test). */
	public static Path soundsJson(Minecraft mc) {
		return mc.getResourcePackDirectory().resolve(FOLDER).resolve("assets/minecraft/sounds.json");
	}

	private static String mcmeta() {
		int format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
		return """
				{
				  "pack": {
				    "description": "§fÖmer Client §7Looks – wird automatisch erstellt",
				    "pack_format": %d,
				    "min_format": %d,
				    "max_format": %d
				  }
				}
				""".formatted(format, format, format);
	}
}
