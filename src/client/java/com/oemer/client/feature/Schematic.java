package com.oemer.client.feature;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Liest Schematic-Dateien und zählt, welche Blöcke man braucht.
 * Unterstützt Litematica (.litematic) und WorldEdit/Sponge (.schem, Version 2 und 3).
 * Eigener kleiner NBT-Leser, damit es nicht von Minecraft-Interna abhängt.
 */
public final class Schematic {
	private Schematic() {
	}

	/** Ergebnis: Item-ID (z. B. "minecraft:stone") → Anzahl, sortiert nach Anzahl. */
	public static Map<String, Integer> countMaterials(Path file) throws IOException {
		Map<String, Object> root = readNbt(file);
		Map<String, Integer> blocks = new HashMap<>();
		String name = file.getFileName().toString().toLowerCase();

		if (name.endsWith(".litematic")) {
			readLitematic(root, blocks);
		} else {
			readSponge(root, blocks);
		}

		// nach Anzahl sortieren
		List<Map.Entry<String, Integer>> list = new ArrayList<>(blocks.entrySet());
		list.sort((a, b) -> b.getValue() - a.getValue());
		Map<String, Integer> sorted = new LinkedHashMap<>();
		for (var e : list) {
			sorted.put(e.getKey(), e.getValue());
		}
		return sorted;
	}

	// ------------------------------------------------------------ Litematica

	@SuppressWarnings("unchecked")
	private static void readLitematic(Map<String, Object> root, Map<String, Integer> out) throws IOException {
		Object regionsObj = root.get("Regions");
		if (!(regionsObj instanceof Map)) {
			throw new IOException("Keine Regions in der Litematic-Datei");
		}
		for (Object regionObj : ((Map<String, Object>) regionsObj).values()) {
			if (!(regionObj instanceof Map)) continue;
			Map<String, Object> region = (Map<String, Object>) regionObj;
			Map<String, Object> size = (Map<String, Object>) region.get("Size");
			List<Object> palette = (List<Object>) region.get("BlockStatePalette");
			long[] states = (long[]) region.get("BlockStates");
			if (size == null || palette == null || states == null) continue;

			long volume = Math.abs((long) num(size.get("x")) * num(size.get("y")) * num(size.get("z")));
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
			long mask = (1L << bits) - 1;
			long[] counts = new long[palette.size()];

			for (long i = 0; i < volume; i++) {
				long bitIndex = i * bits;
				int start = (int) (bitIndex >> 6);
				int offset = (int) (bitIndex & 63);
				if (start >= states.length) break;
				long value = states[start] >>> offset;
				if (offset + bits > 64 && start + 1 < states.length) {
					value |= states[start + 1] << (64 - offset);
				}
				int idx = (int) (value & mask);
				if (idx < counts.length) counts[idx]++;
			}

			for (int p = 0; p < palette.size(); p++) {
				if (counts[p] == 0) continue;
				Map<String, Object> entry = (Map<String, Object>) palette.get(p);
				String blockName = String.valueOf(entry.get("Name"));
				Map<String, String> props = new HashMap<>();
				Object propsObj = entry.get("Properties");
				if (propsObj instanceof Map) {
					for (var e : ((Map<String, Object>) propsObj).entrySet()) {
						props.put(e.getKey(), String.valueOf(e.getValue()));
					}
				}
				add(out, blockName, props, counts[p]);
			}
		}
	}

	// ------------------------------------------------------- Sponge (.schem)

	@SuppressWarnings("unchecked")
	private static void readSponge(Map<String, Object> root, Map<String, Integer> out) throws IOException {
		Map<String, Object> s = root;
		if (root.get("Schematic") instanceof Map) {
			s = (Map<String, Object>) root.get("Schematic"); // Version 3
		}
		Map<String, Object> paletteMap;
		byte[] data;
		if (s.get("Blocks") instanceof Map) { // Version 3
			Map<String, Object> blocks = (Map<String, Object>) s.get("Blocks");
			paletteMap = (Map<String, Object>) blocks.get("Palette");
			data = (byte[]) blocks.get("Data");
		} else { // Version 2
			paletteMap = (Map<String, Object>) s.get("Palette");
			data = (byte[]) s.get("BlockData");
		}
		if (paletteMap == null || data == null) {
			throw new IOException("Unbekanntes Schematic-Format");
		}

		String[] byId = new String[paletteMap.size() + 1];
		for (var e : paletteMap.entrySet()) {
			int id = num(e.getValue());
			if (id >= byId.length) {
				byId = java.util.Arrays.copyOf(byId, id + 1);
			}
			byId[id] = e.getKey();
		}

		long[] counts = new long[byId.length];
		int i = 0;
		while (i < data.length) {
			int value = 0, shift = 0;
			byte b;
			do {
				b = data[i++];
				value |= (b & 0x7F) << shift;
				shift += 7;
			} while ((b & 0x80) != 0 && i < data.length);
			if (value < counts.length) counts[value]++;
		}

		for (int id = 0; id < byId.length; id++) {
			if (counts[id] == 0 || byId[id] == null) continue;
			String state = byId[id]; // z. B. minecraft:oak_slab[type=double]
			String blockName = state;
			Map<String, String> props = new HashMap<>();
			int br = state.indexOf('[');
			if (br >= 0) {
				blockName = state.substring(0, br);
				String inner = state.substring(br + 1, state.length() - 1);
				for (String kv : inner.split(",")) {
					int eq = kv.indexOf('=');
					if (eq > 0) props.put(kv.substring(0, eq), kv.substring(eq + 1));
				}
			}
			add(out, blockName, props, counts[id]);
		}
	}

	// --------------------------------------------------- Block → Item

	private static void add(Map<String, Integer> out, String block, Map<String, String> props, long count) {
		String item = toItem(block, props);
		if (item == null) return;
		long amount = count;
		if ("double".equals(props.get("type")) && block.endsWith("_slab")) {
			amount *= 2;
		}
		out.merge(item, (int) Math.min(Integer.MAX_VALUE, amount), Integer::sum);
	}

	/** Welches Item man für diesen Block braucht. null = zählt nicht (Luft, zweite Hälfte usw.). */
	static String toItem(String block, Map<String, String> props) {
		if (!block.contains(":")) block = "minecraft:" + block;
		String ns = block.substring(0, block.indexOf(':') + 1);
		String id = block.substring(block.indexOf(':') + 1);

		switch (id) {
			case "air", "cave_air", "void_air", "piston_head", "moving_piston", "bubble_column",
				 "nether_portal", "end_portal", "end_gateway", "fire", "soul_fire", "light" -> {
				return null;
			}
			case "water" -> { return "0".equals(props.getOrDefault("level", "0")) ? ns + "water_bucket" : null; }
			case "lava" -> { return "0".equals(props.getOrDefault("level", "0")) ? ns + "lava_bucket" : null; }
			case "redstone_wire" -> { return ns + "redstone"; }
			case "tripwire" -> { return ns + "string"; }
			case "redstone_wall_torch" -> { return ns + "redstone_torch"; }
			case "wall_torch" -> { return ns + "torch"; }
			case "soul_wall_torch" -> { return ns + "soul_torch"; }
			case "carrots" -> { return ns + "carrot"; }
			case "potatoes" -> { return ns + "potato"; }
			case "beetroots" -> { return ns + "beetroot_seeds"; }
			case "wheat" -> { return ns + "wheat_seeds"; }
			case "cocoa" -> { return ns + "cocoa_beans"; }
			case "sweet_berry_bush" -> { return ns + "sweet_berries"; }
			case "bamboo_sapling" -> { return ns + "bamboo"; }
			case "kelp_plant" -> { return ns + "kelp"; }
			case "big_dripleaf_stem" -> { return null; }
			case "cave_vines", "cave_vines_plant" -> { return ns + "glow_berries"; }
			case "twisting_vines_plant" -> { return ns + "twisting_vines"; }
			case "weeping_vines_plant" -> { return ns + "weeping_vines"; }
			case "pumpkin_stem", "attached_pumpkin_stem" -> { return ns + "pumpkin_seeds"; }
			case "melon_stem", "attached_melon_stem" -> { return ns + "melon_seeds"; }
			default -> { }
		}

		// zweite Hälften nicht doppelt zählen
		if ("upper".equals(props.get("half")) && (id.endsWith("_door") || isTallPlant(id))) return null;
		if ("head".equals(props.get("part")) && id.endsWith("_bed")) return null;

		// Wand-Varianten → normales Item
		if (id.contains("_wall_") && (id.endsWith("_sign") || id.endsWith("_banner") || id.endsWith("_head")
				|| id.endsWith("_skull") || id.endsWith("_fan") || id.endsWith("_hanging_sign"))) {
			id = id.replace("_wall_", "_");
		}
		if (id.startsWith("potted_")) {
			return ns + "flower_pot";
		}
		return ns + id;
	}

	private static boolean isTallPlant(String id) {
		return id.equals("tall_grass") || id.equals("large_fern") || id.equals("sunflower") || id.equals("lilac")
				|| id.equals("rose_bush") || id.equals("peony") || id.equals("tall_seagrass") || id.equals("pitcher_plant")
				|| id.equals("small_dripleaf");
	}

	private static int num(Object o) {
		return o instanceof Number n ? n.intValue() : 0;
	}

	// ------------------------------------------------------- NBT-Leser

	private static Map<String, Object> readNbt(Path file) throws IOException {
		try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) {
			raw.mark(2);
			int b1 = raw.read(), b2 = raw.read();
			raw.reset();
			InputStream in = (b1 == 0x1f && b2 == 0x8b) ? new GZIPInputStream(raw) : raw;
			DataInputStream data = new DataInputStream(new BufferedInputStream(in));
			int type = data.readUnsignedByte();
			if (type != 10) throw new IOException("Keine NBT-Datei");
			readString(data); // Name vom Wurzel-Tag
			@SuppressWarnings("unchecked")
			Map<String, Object> root = (Map<String, Object>) readTag(data, 10, 0);
			return root;
		}
	}

	private static String readString(DataInputStream in) throws IOException {
		int len = in.readUnsignedShort();
		byte[] b = new byte[len];
		in.readFully(b);
		return new String(b, StandardCharsets.UTF_8); // (modifiziertes UTF-8 reicht für Blocknamen)
	}

	private static Object readTag(DataInputStream in, int type, int depth) throws IOException {
		if (depth > 512) throw new IOException("NBT zu tief verschachtelt");
		switch (type) {
			case 1: return in.readByte();
			case 2: return in.readShort();
			case 3: return in.readInt();
			case 4: return in.readLong();
			case 5: return in.readFloat();
			case 6: return in.readDouble();
			case 7: {
				int n = in.readInt();
				byte[] b = new byte[n];
				in.readFully(b);
				return b;
			}
			case 8: return readString(in);
			case 9: {
				int elementType = in.readUnsignedByte();
				int n = in.readInt();
				List<Object> list = new ArrayList<>(Math.max(0, Math.min(n, 4096)));
				for (int i = 0; i < n; i++) list.add(readTag(in, elementType, depth + 1));
				return list;
			}
			case 10: {
				Map<String, Object> map = new LinkedHashMap<>();
				while (true) {
					int t = in.readUnsignedByte();
					if (t == 0) break;
					String name = readString(in);
					map.put(name, readTag(in, t, depth + 1));
				}
				return map;
			}
			case 11: {
				int n = in.readInt();
				int[] a = new int[n];
				for (int i = 0; i < n; i++) a[i] = in.readInt();
				return a;
			}
			case 12: {
				int n = in.readInt();
				long[] a = new long[n];
				for (int i = 0; i < n; i++) a[i] = in.readLong();
				return a;
			}
			default:
				throw new IOException("Unbekannter NBT-Typ " + type);
		}
	}
}
