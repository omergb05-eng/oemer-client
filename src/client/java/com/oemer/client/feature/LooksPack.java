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
		return c.customTotem && !c.totemSelection.isEmpty() && Files.isRegularFile(totemFolder().resolve(c.totemSelection));
	}

	/**
	 * Baut das Paket neu und lädt die Ressourcen neu (dauert ein paar Sekunden).
	 * Nur aufrufen, wenn sich wirklich etwas geändert hat.
	 */
	public static void apply(Minecraft mc) {
		try {
			Path dir = mc.getResourcePackDirectory().resolve(FOLDER);
			boolean active = totemActive();

			Files.createDirectories(dir);
			Files.writeString(dir.resolve("pack.mcmeta"), mcmeta(), StandardCharsets.UTF_8);

			Path totem = dir.resolve(TOTEM_PATH);
			if (active) {
				Files.createDirectories(totem.getParent());
				var img = ImageTools.squareCrop(ImageTools.read(totemFolder().resolve(OemerConfig.get().totemSelection)), 64);
				Files.write(totem, ImageTools.toPng(img));
			} else {
				Files.deleteIfExists(totem);
			}

			PackRepository repo = mc.getResourcePackRepository();
			repo.reload();
			List<String> selected = new ArrayList<>(repo.getSelectedIds());
			selected.remove(PACK_ID);
			if (active) {
				selected.add(PACK_ID); // ganz oben = gewinnt gegen andere Pakete
			}
			repo.setSelected(selected);
			mc.options.updateResourcePacks(repo);
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Ömer Client Looks konnte nicht gebaut werden", e);
		}
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
