package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Custom Sounds: für jeden Sound ein eigener Unterordner in
 * .minecraft/config/oemerclient/sounds/&lt;slot&gt;/ mit .ogg-Dateien.
 * Die ausgewählte Datei kommt ins Ressourcenpaket "Ömer Client Looks".
 */
public final class Sounds {
	private Sounds() {
	}

	/** Ein Sound, den du austauschen kannst. */
	public record Slot(String id, String name, String[] events) {
	}

	public static final Slot[] SLOTS = {
			new Slot("treffer", "Treffer", new String[]{
					"entity.player.attack.strong", "entity.player.attack.weak", "entity.player.attack.knockback",
					"entity.player.attack.sweep", "entity.player.attack.nodamage"}),
			new Slot("kritisch", "Kritischer Treffer", new String[]{"entity.player.attack.crit"}),
			new Slot("totem", "Totem", new String[]{"item.totem.use"}),
			new Slot("schaden", "Schaden", new String[]{"entity.player.hurt"}),
			new Slot("levelup", "Level-Up", new String[]{"entity.player.levelup"}),
			new Slot("xp", "XP einsammeln", new String[]{"entity.experience_orb.pickup"}),
			new Slot("klick", "Button-Klick", new String[]{"ui.button.click"}),
	};

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("sounds");
	}

	public static Path folder(Slot slot) {
		return folder().resolve(slot.id());
	}

	public static void createFolders() {
		for (Slot s : SLOTS) {
			try {
				Files.createDirectories(folder(s));
			} catch (Exception e) {
				OemerClient.LOGGER.warn("Sound-Ordner {} konnte nicht erstellt werden", s.id(), e);
			}
		}
	}

	/** Alle .ogg-Dateien im Unterordner, vorne "" = Standard-Sound. */
	public static List<String> choices(Slot slot) {
		List<String> list = new ArrayList<>();
		list.add("");
		try {
			Path dir = folder(slot);
			Files.createDirectories(dir);
			try (Stream<Path> s = Files.list(dir)) {
				s.filter(Files::isRegularFile)
						.map(p -> p.getFileName().toString())
						.filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".ogg"))
						.sorted(String.CASE_INSENSITIVE_ORDER)
						.forEach(list::add);
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Sound-Ordner {} konnte nicht gelesen werden", slot.id(), e);
		}
		return list;
	}

	public static String displayName(String file) {
		return file.isEmpty() ? "Standard" : CapeManager.shortName(file);
	}

	/** Die aktive Datei für diesen Slot, oder null. Prüft, ob es wirklich eine Ogg-Datei ist. */
	public static Path active(Slot slot) {
		OemerConfig c = OemerConfig.get();
		if (!c.customSounds) return null;
		String file = c.soundSelection.getOrDefault(slot.id(), "");
		if (file.isEmpty()) return null;
		Path p = folder(slot).resolve(file);
		return isOgg(p) ? p : null;
	}

	/** Echte Ogg-Datei? (fängt umbenannte mp3/wav ab, die Minecraft nicht abspielen kann) */
	public static boolean isOgg(Path p) {
		if (!Files.isRegularFile(p)) return false;
		try (InputStream in = Files.newInputStream(p)) {
			byte[] head = in.readNBytes(4);
			return head.length == 4 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S';
		} catch (Exception e) {
			return false;
		}
	}

	/** Für den Hinweis im Menü: wie viele Sounds sind gerade ersetzt? */
	public static int activeCount() {
		int n = 0;
		for (Slot s : SLOTS) {
			if (active(s) != null) n++;
		}
		return n;
	}

	/** Gibt es ausgewählte Dateien, die keine echten Ogg-Dateien sind? */
	public static List<String> invalid() {
		List<String> bad = new ArrayList<>();
		OemerConfig c = OemerConfig.get();
		for (Slot s : SLOTS) {
			String file = c.soundSelection.getOrDefault(s.id(), "");
			if (!file.isEmpty() && !isOgg(folder(s).resolve(file))) bad.add(file);
		}
		return bad;
	}
}
