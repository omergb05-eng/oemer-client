package com.oemer.client.feature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Eine komplett eingelesene Schematic: jeder Block an seinem Platz.
 * Unterstützt .litematic (mehrere Bereiche) und .schem (Sponge 2/3).
 * Index eines Blocks: (y * sizeZ + z) * sizeX + x, -1 = Luft.
 */
public final class SchematicGrid {
	public final String name;
	public final int sx, sy, sz;
	/** Zustände wie "minecraft:oak_stairs[facing=east,half=bottom]". */
	public final String[] palette;
	/** Nur die Block-ID pro Palette-Eintrag, z. B. "minecraft:oak_stairs". */
	public final String[] ids;
	public final int[] blocks;
	public final int solid;

	private SchematicGrid(String name, int sx, int sy, int sz, String[] palette, int[] blocks) {
		this.name = name;
		this.sx = sx;
		this.sy = sy;
		this.sz = sz;
		this.palette = palette;
		this.blocks = blocks;
		this.ids = new String[palette.length];
		for (int i = 0; i < palette.length; i++) {
			int br = palette[i].indexOf('[');
			ids[i] = br >= 0 ? palette[i].substring(0, br) : palette[i];
		}
		int n = 0;
		for (int b : blocks) if (b >= 0) n++;
		this.solid = n;
	}

	public int at(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return -1;
		return blocks[(y * sz + z) * sx + x];
	}

	private static final long MAX_VOLUME = 48_000_000L;

	private static boolean isAir(String id) {
		return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")
				|| id.equals("minecraft:structure_void") || id.equals("air");
	}

	private static int num(Object o) {
		return o instanceof Number n ? n.intValue() : 0;
	}

	/** Palette-Eintrag aus Name + Eigenschaften zu einem festen Text machen (Eigenschaften sortiert). */
	private static String key(String name, Map<String, String> props) {
		if (!name.contains(":")) name = "minecraft:" + name;
		if (props == null || props.isEmpty()) return name;
		StringBuilder sb = new StringBuilder(name).append('[');
		boolean first = true;
		for (var e : new TreeMap<>(props).entrySet()) {
			if (!first) sb.append(',');
			sb.append(e.getKey()).append('=').append(e.getValue());
			first = false;
		}
		return sb.append(']').toString();
	}

	public static SchematicGrid load(Path file) throws IOException {
		Map<String, Object> root = Schematic.read(file);
		String n = file.getFileName().toString();
		if (n.toLowerCase(Locale.ROOT).endsWith(".litematic")) return loadLitematic(n, root);
		return loadSponge(n, root);
	}

	// ------------------------------------------------------------ Litematica

	private record Region(int px, int py, int pz, int ax, int ay, int az, int minX, int minY, int minZ,
						  List<Object> palette, long[] states) {
	}

	@SuppressWarnings("unchecked")
	private static SchematicGrid loadLitematic(String name, Map<String, Object> root) throws IOException {
		if (!(root.get("Regions") instanceof Map<?, ?> regionsObj)) throw new IOException("Keine Regions in der Litematic-Datei");
		List<Region> regions = new ArrayList<>();
		int gMinX = Integer.MAX_VALUE, gMinY = Integer.MAX_VALUE, gMinZ = Integer.MAX_VALUE;
		int gMaxX = Integer.MIN_VALUE, gMaxY = Integer.MIN_VALUE, gMaxZ = Integer.MIN_VALUE;
		for (Object ro : regionsObj.values()) {
			if (!(ro instanceof Map)) continue;
			Map<String, Object> r = (Map<String, Object>) ro;
			Map<String, Object> size = (Map<String, Object>) r.get("Size");
			Map<String, Object> pos = (Map<String, Object>) r.get("Position");
			List<Object> pal = (List<Object>) r.get("BlockStatePalette");
			long[] states = (long[]) r.get("BlockStates");
			if (size == null || pos == null || pal == null || states == null) continue;
			int sizeX = num(size.get("x")), sizeY = num(size.get("y")), sizeZ = num(size.get("z"));
			int px = num(pos.get("x")), py = num(pos.get("y")), pz = num(pos.get("z"));
			int minX = px + (sizeX < 0 ? sizeX + 1 : 0), minY = py + (sizeY < 0 ? sizeY + 1 : 0), minZ = pz + (sizeZ < 0 ? sizeZ + 1 : 0);
			int ax = Math.abs(sizeX), ay = Math.abs(sizeY), az = Math.abs(sizeZ);
			if (ax == 0 || ay == 0 || az == 0) continue;
			regions.add(new Region(px, py, pz, ax, ay, az, minX, minY, minZ, pal, states));
			gMinX = Math.min(gMinX, minX);
			gMinY = Math.min(gMinY, minY);
			gMinZ = Math.min(gMinZ, minZ);
			gMaxX = Math.max(gMaxX, minX + ax - 1);
			gMaxY = Math.max(gMaxY, minY + ay - 1);
			gMaxZ = Math.max(gMaxZ, minZ + az - 1);
		}
		if (regions.isEmpty()) throw new IOException("Die Schematic ist leer");
		int sx = gMaxX - gMinX + 1, sy = gMaxY - gMinY + 1, sz = gMaxZ - gMinZ + 1;
		if ((long) sx * sy * sz > MAX_VOLUME) throw new IOException("Die Schematic ist zu groß (" + sx + "×" + sy + "×" + sz + ")");
		int[] grid = new int[sx * sy * sz];
		Arrays.fill(grid, -1);
		List<String> palette = new ArrayList<>();
		Map<String, Integer> index = new HashMap<>();
		for (Region r : regions) {
			int[] map = new int[r.palette.size()];
			for (int i = 0; i < map.length; i++) {
				Map<String, Object> entry = (Map<String, Object>) r.palette.get(i);
				Map<String, String> props = new HashMap<>();
				if (entry.get("Properties") instanceof Map<?, ?> pm) {
					for (var e : pm.entrySet()) props.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
				}
				String bn = String.valueOf(entry.get("Name"));
				if (isAir(bn)) {
					map[i] = -1;
					continue;
				}
				String k = key(bn, props);
				Integer id = index.get(k);
				if (id == null) {
					id = palette.size();
					palette.add(k);
					index.put(k, id);
				}
				map[i] = id;
			}
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(r.palette.size() - 1));
			long mask = (1L << bits) - 1;
			long volume = (long) r.ax * r.ay * r.az;
			int ox = r.minX - gMinX, oy = r.minY - gMinY, oz = r.minZ - gMinZ;
			for (long i = 0; i < volume; i++) {
				long bitIndex = i * bits;
				int start = (int) (bitIndex >> 6);
				int offset = (int) (bitIndex & 63);
				if (start >= r.states.length) break;
				long value = r.states[start] >>> offset;
				if (offset + bits > 64 && start + 1 < r.states.length) value |= r.states[start + 1] << (64 - offset);
				int idx = (int) (value & mask);
				if (idx >= map.length || map[idx] < 0) continue;
				int x = (int) (i % r.ax), z = (int) ((i / r.ax) % r.az), y = (int) (i / ((long) r.ax * r.az));
				grid[((oy + y) * sz + (oz + z)) * sx + (ox + x)] = map[idx];
			}
		}
		return new SchematicGrid(name, sx, sy, sz, palette.toArray(new String[0]), grid);
	}

	// ------------------------------------------------------- Sponge (.schem)

	@SuppressWarnings("unchecked")
	private static SchematicGrid loadSponge(String name, Map<String, Object> root) throws IOException {
		Map<String, Object> s = root;
		if (root.get("Schematic") instanceof Map) s = (Map<String, Object>) root.get("Schematic");
		Map<String, Object> paletteMap;
		byte[] data;
		if (s.get("Blocks") instanceof Map) {
			Map<String, Object> b = (Map<String, Object>) s.get("Blocks");
			paletteMap = (Map<String, Object>) b.get("Palette");
			data = (byte[]) b.get("Data");
		} else {
			paletteMap = (Map<String, Object>) s.get("Palette");
			data = (byte[]) s.get("BlockData");
		}
		int w = num(s.get("Width")), h = num(s.get("Height")), l = num(s.get("Length"));
		if (paletteMap == null || data == null || w <= 0 || h <= 0 || l <= 0) throw new IOException("Unbekanntes Schematic-Format");
		if ((long) w * h * l > MAX_VOLUME) throw new IOException("Die Schematic ist zu groß (" + w + "×" + h + "×" + l + ")");

		int maxId = 0;
		for (Object v : paletteMap.values()) maxId = Math.max(maxId, num(v));
		String[] byId = new String[maxId + 1];
		for (var e : paletteMap.entrySet()) byId[num(e.getValue())] = e.getKey();
		// Palette ohne Luft neu nummerieren, Eigenschaften sortieren
		int[] remap = new int[byId.length];
		List<String> palette = new ArrayList<>();
		Map<String, Integer> index = new HashMap<>();
		for (int i = 0; i < byId.length; i++) {
			remap[i] = -1;
			String st = byId[i];
			if (st == null) continue;
			String bn = st;
			Map<String, String> props = new HashMap<>();
			int br = st.indexOf('[');
			if (br >= 0) {
				bn = st.substring(0, br);
				for (String kv : st.substring(br + 1, st.length() - 1).split(",")) {
					int eq = kv.indexOf('=');
					if (eq > 0) props.put(kv.substring(0, eq), kv.substring(eq + 1));
				}
			}
			if (isAir(bn)) continue;
			String k = key(bn, props);
			Integer id = index.get(k);
			if (id == null) {
				id = palette.size();
				palette.add(k);
				index.put(k, id);
			}
			remap[i] = id;
		}
		int[] grid = new int[w * h * l];
		int i = 0, pos = 0;
		while (i < data.length && pos < grid.length) {
			int value = 0, shift = 0;
			byte b;
			do {
				b = data[i++];
				value |= (b & 0x7F) << shift;
				shift += 7;
			} while ((b & 0x80) != 0 && i < data.length);
			grid[pos++] = value < remap.length ? remap[value] : -1;
		}
		// Sponge: x + z * W + y * W * L  – genau unser Index
		return new SchematicGrid(name, w, h, l, palette.toArray(new String[0]), grid);
	}
}
