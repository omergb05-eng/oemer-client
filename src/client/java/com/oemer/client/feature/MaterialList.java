package com.oemer.client.feature;

import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Die Materialliste der ausgewählten Schematic (wie bei Litematica). */
public final class MaterialList {
	private MaterialList() {
	}

	/** Eine Zeile: welches Item, wie viele gebraucht. */
	public record Entry(String id, Item item, ItemStack icon, String name, int needed) {
	}

	private static String loadedStamp = "";
	private static List<Entry> entries = Collections.emptyList();
	private static String error = "";

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("oemerclient").resolve("schematics");
	}

	/** Alle Schematics im Ordner (.litematic, .schem). */
	public static List<String> files() {
		List<String> list = new ArrayList<>();
		try {
			Files.createDirectories(folder());
			try (Stream<Path> s = Files.list(folder())) {
				s.filter(Files::isRegularFile)
						.map(p -> p.getFileName().toString())
						.filter(n -> {
							String l = n.toLowerCase(Locale.ROOT);
							return l.endsWith(".litematic") || l.endsWith(".schem");
						})
						.sorted(String.CASE_INSENSITIVE_ORDER)
						.forEach(list::add);
			}
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Schematic-Ordner konnte nicht gelesen werden", e);
		}
		return list;
	}

	/** Lädt neu, wenn eine andere Datei gewählt ist oder sie sich geändert hat. */
	public static List<Entry> entries() {
		String file = OemerConfig.get().schematic;
		if (file == null || file.isEmpty()) {
			entries = Collections.emptyList();
			loadedStamp = "";
			error = "";
			return entries;
		}
		Path p = folder().resolve(file);
		String stamp;
		try {
			stamp = file + "@" + Files.getLastModifiedTime(p).toMillis();
		} catch (Exception e) {
			entries = Collections.emptyList();
			error = "Datei nicht gefunden";
			loadedStamp = "";
			return entries;
		}
		if (stamp.equals(loadedStamp)) {
			return entries;
		}
		loadedStamp = stamp;
		try {
			Map<String, Integer> counts = Schematic.countMaterials(p);
			List<Entry> list = new ArrayList<>();
			for (var e : counts.entrySet()) {
				Item item = item(e.getKey());
				ItemStack icon = new ItemStack(item == Items.AIR ? Items.BARRIER : item);
				String name = item == Items.AIR ? e.getKey() : icon.getHoverName().getString();
				list.add(new Entry(e.getKey(), item, icon, name, e.getValue()));
			}
			entries = list;
			error = "";
		} catch (Exception e) {
			OemerClient.LOGGER.warn("Schematic {} konnte nicht gelesen werden", file, e);
			entries = Collections.emptyList();
			error = "Datei kaputt oder unbekanntes Format";
		}
		return entries;
	}

	public static String error() {
		return error;
	}

	public static void reload() {
		loadedStamp = "";
	}

	static Item item(String id) {
		int colon = id.indexOf(':');
		Identifier key = colon < 0
				? Identifier.withDefaultNamespace(id)
				: Identifier.fromNamespaceAndPath(id.substring(0, colon), id.substring(colon + 1));
		return BuiltInRegistries.ITEM.getValue(key);
	}

	/** Wie viele davon hast du gerade im Inventar? */
	public static int have(Item item) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || item == Items.AIR) return 0;
		Inventory inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.is(item)) n += s.getCount();
		}
		return n;
	}

	public static boolean isDone(Entry e) {
		return OemerConfig.get().materialDone.contains(e.id());
	}

	public static void toggleDone(Entry e) {
		List<String> done = OemerConfig.get().materialDone;
		if (!done.remove(e.id())) done.add(e.id());
		OemerConfig.save();
	}

	/** Anzahl als "3 St. + 12" oder "2 Kisten" (1 Kiste = 27 Stacks). */
	public static String amount(int count, int stackSize) {
		if (stackSize <= 1 || count < stackSize) return String.valueOf(count);
		int perBox = stackSize * 27;
		if (count >= perBox) {
			int boxes = count / perBox;
			int rest = (count % perBox) / stackSize;
			return count + " (" + boxes + " Kiste" + (boxes > 1 ? "n" : "") + (rest > 0 ? " + " + rest + " St." : "") + ")";
		}
		int stacks = count / stackSize, rest = count % stackSize;
		return count + " (" + stacks + " St." + (rest > 0 ? " + " + rest : "") + ")";
	}
}
