package com.oemer.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oemer.client.OemerClient;
import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Schematic als Hologramm in der Welt (wie Litematica): selbst platzieren, verschieben, drehen, spiegeln,
 * Ebene für Ebene anzeigen. Blau/bunt = fehlt noch, rot = falscher Block, grün = passt.
 */
public final class SchematicPlacer {
	private SchematicPlacer() {
	}

	/** Ein Durchgang: alle sichtbaren Hologramm-Blöcke als einfache Zahlen-Listen (spart Speicher, auch bei Millionen Blöcken). */
	private static final class Cells {
		int n;
		int[] x = new int[1024], y = new int[1024], z = new int[1024], pal = new int[1024];
		byte[] kind = new byte[1024], mask = new byte[1024];

		void add(int px, int py, int pz, int palette, int k, int m) {
			if (n == x.length) {
				int cap = n * 2;
				x = java.util.Arrays.copyOf(x, cap);
				y = java.util.Arrays.copyOf(y, cap);
				z = java.util.Arrays.copyOf(z, cap);
				pal = java.util.Arrays.copyOf(pal, cap);
				kind = java.util.Arrays.copyOf(kind, cap);
				mask = java.util.Arrays.copyOf(mask, cap);
			}
			x[n] = px;
			y[n] = py;
			z[n] = pz;
			pal[n] = palette;
			kind[n] = (byte) k;
			mask[n] = (byte) m;
			n++;
		}
	}

	/** Die Flächen (Texturen) eines Blockzustands. */
	private static final class QuadSet {
		BakedQuad[][] dir = new BakedQuad[6][];
		BakedQuad[] general = new BakedQuad[0];
		boolean empty = true;
	}

	private static final int KIND_MISSING = 0, KIND_WRONG = 1, KIND_CORRECT = 2;
	private static final int MAX_CELLS = 3_000_000;
	private static final int FULL_BRIGHT = 0xF000F0;

	private static volatile SchematicGrid grid;
	private static volatile String gridFile = "";
	private static volatile String error = "";
	private static volatile boolean loading;
	private static Block[] paletteBlocks = new Block[0];
	private static BlockState[] paletteStates = new BlockState[0];
	private static int[] paletteColors = new int[0];
	private static boolean[] paletteAir = new boolean[0];
	private static boolean[] paletteOpaque = new boolean[0];

	private static volatile Cells shown = new Cells();
	private static volatile int missing, wrong, correct;
	private static volatile boolean capped;

	// laufender Durchgang
	private static Cells building = new Cells();
	private static int scanY = -1, scanMin, scanMax, wMinX, wMaxX, wMinZ, wMaxZ, layerLo, layerHi;

	// Zeichnen
	private static QuadSet[] quadsByPal = new QuadSet[0];
	private static int quadsKey = -1;
	private static Object quadsEpoch;
	private static final java.util.Map<BlockState, QuadSet> QUAD_CACHE = new java.util.HashMap<>();
	private static boolean[] drawn = new boolean[0];
	private static float texDist = 48, flatDist = 700;
	private static int bMissing, bWrong, bCorrect;
	private static long nextScan;
	private static String scanSig = "";

	public static void init() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(SchematicPlacer::render);
	}

	public static Path folder() {
		return MaterialList.folder();
	}

	// ----------------------------------------------------------- Laden

	public static SchematicGrid grid() {
		return grid;
	}

	public static String error() {
		return error;
	}

	public static boolean loading() {
		return loading;
	}

	public static void reload() {
		grid = null;
		gridFile = "";
		shown = new Cells();
		scanY = -1;
	}

	private static void ensureLoaded(String file) {
		if (file.equals(gridFile) || loading) return;
		if (file.isEmpty()) {
			grid = null;
			gridFile = "";
			return;
		}
		loading = true;
		gridFile = file;
		error = "";
		CompletableFuture.runAsync(() -> {
			try {
				SchematicGrid g = SchematicGrid.load(folder().resolve(file));
				Block[] blocks = new Block[g.palette.length];
				int[] colors = new int[g.palette.length];
				BlockState[] states = new BlockState[g.palette.length];
				boolean[] air = new boolean[g.palette.length];
				boolean[] opaque = new boolean[g.palette.length];
				for (int i = 0; i < blocks.length; i++) {
					Identifier id = Identifier.tryParse(g.ids[i]);
					Block b = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
					blocks[i] = b;
					if (b != null) {
						BlockState st = parseState(b, g.palette[i]);
						states[i] = st;
						air[i] = st.isAir();
						try {
							opaque[i] = st.canOcclude() && st.isSolidRender();
						} catch (Throwable t) {
							opaque[i] = st.canOcclude();
						}
					}
					int col = 0xB0B0B0;
					try {
						if (b != null) {
							int mc = b.defaultMapColor().col;
							if (mc != 0) col = mc;
						}
					} catch (Throwable ignored) {
					}
					colors[i] = col & 0xFFFFFF;
				}
				paletteBlocks = blocks;
				paletteStates = states;
				paletteAir = air;
				paletteOpaque = opaque;
				paletteColors = colors;
				quadsKey = -1;
				grid = g;
				scanY = -1;
			} catch (Throwable t) {
				error = t.getMessage() == null ? t.toString() : t.getMessage();
				OemerClient.LOGGER.warn("Schematic {} konnte nicht geladen werden", file, t);
				grid = null;
			} finally {
				loading = false;
			}
		});
	}

	/** "minecraft:oak_stairs[facing=east,half=bottom]" -> echter Blockzustand. */
	private static BlockState parseState(Block b, String text) {
		BlockState st = b.defaultBlockState();
		int i = text.indexOf('['), j = text.lastIndexOf(']');
		if (i < 0 || j < i) return st;
		for (String part : text.substring(i + 1, j).split(",")) {
			int eq = part.indexOf('=');
			if (eq <= 0) continue;
			Property<?> prop = b.getStateDefinition().getProperty(part.substring(0, eq).trim());
			if (prop != null) st = withValue(st, prop, part.substring(eq + 1).trim());
		}
		return st;
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState st, Property<T> prop, String value) {
		return prop.getValue(value).map(v -> st.setValue(prop, v)).orElse(st);
	}

	// ----------------------------------------------------- Umrechnen

	public static int finalW() {
		SchematicGrid g = grid;
		return g == null ? 0 : (OemerConfig.get().placeRot % 2 == 0 ? g.sx : g.sz);
	}

	public static int finalD() {
		SchematicGrid g = grid;
		return g == null ? 0 : (OemerConfig.get().placeRot % 2 == 0 ? g.sz : g.sx);
	}

	/** Palette-Nummer an einem Punkt im gedrehten Hologramm (x/z ab der Ecke), -1 = nichts. */
	static int blockAt(SchematicGrid g, int rot, int mirror, int fx, int y, int fz) {
		int x, z;
		switch (rot) {
			case 1 -> {
				z = g.sz - 1 - fx;
				x = fz;
			}
			case 2 -> {
				x = g.sx - 1 - fx;
				z = g.sz - 1 - fz;
			}
			case 3 -> {
				z = fx;
				x = g.sx - 1 - fz;
			}
			default -> {
				x = fx;
				z = fz;
			}
		}
		if (mirror == 1) x = g.sx - 1 - x;
		else if (mirror == 2) z = g.sz - 1 - z;
		return g.at(x, y, z);
	}

	public static int layers() {
		SchematicGrid g = grid;
		return g == null ? 0 : g.sy;
	}

	// -------------------------------------------------------- Steuern

	public static void setHere(Minecraft mc) {
		if (mc.player == null) return;
		OemerConfig c = OemerConfig.get();
		c.placeX = (int) Math.floor(mc.player.getX());
		c.placeY = (int) Math.floor(mc.player.getY());
		c.placeZ = (int) Math.floor(mc.player.getZ());
		c.placeSet = true;
		c.placeOn = true;
		scanY = -1;
		OemerConfig.save();
	}

	public static boolean setLooking(Minecraft mc) {
		if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			BlockPos p = bhr.getBlockPos().relative(bhr.getDirection());
			OemerConfig c = OemerConfig.get();
			c.placeX = p.getX();
			c.placeY = p.getY();
			c.placeZ = p.getZ();
			c.placeSet = true;
			c.placeOn = true;
			scanY = -1;
			OemerConfig.save();
			return true;
		}
		return false;
	}

	public static void move(int dx, int dy, int dz) {
		OemerConfig c = OemerConfig.get();
		int s = OemerConfig.PLACE_STEPS[c.placeStep];
		c.placeX += dx * s;
		c.placeY += dy * s;
		c.placeZ += dz * s;
		scanY = -1;
		OemerConfig.save();
	}

	public static void rotate() {
		OemerConfig c = OemerConfig.get();
		c.placeRot = (c.placeRot + 1) % 4;
		scanY = -1;
		OemerConfig.save();
	}

	public static void changed() {
		scanY = -1;
		OemerConfig.save();
	}

	public static void layer(int delta) {
		OemerConfig c = OemerConfig.get();
		int max = Math.max(0, layers() - 1);
		c.placeLayer = Math.max(0, Math.min(max, c.placeLayer + delta));
		scanY = -1;
		OemerConfig.save();
	}

	public static void layerToPlayer(Minecraft mc) {
		if (mc.player == null) return;
		OemerConfig c = OemerConfig.get();
		int y = (int) Math.floor(mc.player.getY()) - c.placeY;
		c.placeLayer = Math.max(0, Math.min(Math.max(0, layers() - 1), y));
		scanY = -1;
		OemerConfig.save();
	}

	public static boolean active() {
		OemerConfig c = OemerConfig.get();
		return c.placeOn && c.placeSet && !c.placeFile.isEmpty();
	}

	public static String stats() {
		if (grid == null) return loading ? "Lade Schematic …" : (error.isEmpty() ? "" : "Fehler: " + error);
		return (capped ? "(Sehr groß – es werden die ersten 3 Mio. sichtbaren Blöcke gezeigt) " : "") + "Fehlt " + missing + " · falsch " + wrong + " · passt " + correct;
	}

	public static int missing() {
		return missing;
	}

	public static int wrong() {
		return wrong;
	}

	public static int correct() {
		return correct;
	}

	public static int shownCount() {
		return shown.n;
	}

	// ------------------------------------------------------ Abgleich

	private static boolean occluder(SchematicGrid g, int rot, int mirror, int fx, int y, int fz) {
		if (y < layerLo || y > layerHi) return false;
		int bi = blockAt(g, rot, mirror, fx, y, fz);
		return bi >= 0 && bi < paletteOpaque.length && paletteOpaque[bi];
	}

	/** Jeden Tick ein Stück: vergleicht das Hologramm mit der echten Welt. */
	public static void tick(Minecraft mc) {
		OemerConfig c = OemerConfig.get();
		if (!c.placeOn || !c.placeSet || c.placeFile.isEmpty()) {
			if (shown.n != 0) shown = new Cells();
			return;
		}
		ensureLoaded(c.placeFile);
		SchematicGrid g = grid;
		ClientLevel level = mc.level;
		if (g == null || level == null || mc.player == null) return;

		int fw = finalW(), fd = finalD();
		if (scanY < 0) {
			if (System.currentTimeMillis() < nextScan) return;
			int range = OemerConfig.PLACE_RANGES[c.placeRange];
			int px = (int) Math.floor(mc.player.getX()) - c.placeX;
			int pz = (int) Math.floor(mc.player.getZ()) - c.placeZ;
			int py = (int) Math.floor(mc.player.getY()) - c.placeY;
			wMinX = Math.max(0, px - range);
			wMaxX = Math.min(fw - 1, px + range);
			wMinZ = Math.max(0, pz - range);
			wMaxZ = Math.min(fd - 1, pz + range);
			int lo = 0, hi = g.sy - 1;
			int L = Math.min(c.placeLayer, g.sy - 1);
			switch (c.placeLayerMode) {
				case 1 -> lo = hi = L;
				case 2 -> hi = L;
				case 3 -> lo = L;
				default -> {
				}
			}
			layerLo = lo;
			layerHi = hi;
			scanMin = Math.max(lo, py - range);
			scanMax = Math.min(hi, py + range);
			if (scanMin > scanMax || wMinX > wMaxX || wMinZ > wMaxZ) {
				shown = new Cells();
				missing = wrong = correct = 0;
				nextScan = System.currentTimeMillis() + 500;
				return;
			}
			building = new Cells();
			capped = false;
			bMissing = bWrong = bCorrect = 0;
			scanY = scanMin;
		}

		int budget = 60000;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Cells out = building;
		int rot = c.placeRot, mir = c.placeMirror;
		while (scanY <= scanMax && budget > 0) {
			int y = scanY;
			for (int fx = wMinX; fx <= wMaxX; fx++) {
				for (int fz = wMinZ; fz <= wMaxZ; fz++) {
					budget--;
					int bi = blockAt(g, rot, mir, fx, y, fz);
					if (bi < 0 || (bi < paletteAir.length && paletteAir[bi])) continue;
					pos.set(c.placeX + fx, c.placeY + y, c.placeZ + fz);
					if (!level.hasChunkAt(pos)) continue;
					BlockState st = level.getBlockState(pos);
					Block want = bi < paletteBlocks.length ? paletteBlocks[bi] : null;
					int kind;
					if (want != null && st.getBlock() == want) {
						bCorrect++;
						if (!c.placeShowCorrect) continue;
						kind = KIND_CORRECT;
					} else if (st.isAir() || !st.getFluidState().isEmpty()) {
						bMissing++;
						kind = KIND_MISSING;
					} else {
						bWrong++;
						if (!c.placeShowWrong) continue;
						kind = KIND_WRONG;
					}
					// Welche Seiten sieht man? (Seiten, die an einen festen Block der Schematic grenzen, nicht)
					int mask = 0;
					Direction[] dirs = Direction.values();
					for (int d = 0; d < 6; d++) {
						Direction dir = dirs[d];
						if (!occluder(g, rot, mir, fx + dir.getStepX(), y + dir.getStepY(), fz + dir.getStepZ())) mask |= 1 << d;
					}
					boolean full = bi < paletteOpaque.length && paletteOpaque[bi];
					if (mask == 0 && full) continue;
					if (out.n >= MAX_CELLS) {
						capped = true;
						continue;
					}
					out.add(pos.getX(), pos.getY(), pos.getZ(), bi, kind, mask);
				}
			}
			scanY++;
		}
		if (scanY > scanMax) {
			shown = building;
			missing = bMissing;
			wrong = bWrong;
			correct = bCorrect;
			scanY = -1;
			nextScan = System.currentTimeMillis() + 400;
		}
	}

	// ---------------------------------------------------- Zeichnen

	private static QuadSet buildQuads(Minecraft mc, BlockState state) {
		QuadSet qs = new QuadSet();
		try {
			BlockStateModel model = mc.getBlockRenderer().getBlockModel(state);
			List<BlockModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);
			List<BakedQuad>[] byDir = new List[6];
			for (int i = 0; i < 6; i++) byDir[i] = new ArrayList<>();
			List<BakedQuad> general = new ArrayList<>();
			Direction[] dirs = Direction.values();
			for (BlockModelPart part : parts) {
				for (int i = 0; i < 6; i++) byDir[i].addAll(part.getQuads(dirs[i]));
				general.addAll(part.getQuads(null));
			}
			int total = general.size();
			for (int i = 0; i < 6; i++) {
				qs.dir[i] = byDir[i].toArray(new BakedQuad[0]);
				total += qs.dir[i].length;
			}
			qs.general = general.toArray(new BakedQuad[0]);
			qs.empty = total == 0;
		} catch (Throwable t) {
			OemerClient.LOGGER.debug("Schematic: Block-Modell nicht lesbar", t);
			for (int i = 0; i < 6; i++) qs.dir[i] = new BakedQuad[0];
		}
		return qs;
	}

	/** Passende Texturen für jeden Palette-Eintrag (mit Drehung und Spiegelung). */
	private static void ensureQuads(Minecraft mc, OemerConfig c) {
		Object epoch = mc.getBlockRenderer().getBlockModel(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		int key = c.placeRot * 3 + c.placeMirror;
		if (key == quadsKey && epoch == quadsEpoch && quadsByPal.length == paletteStates.length) return;
		if (epoch != quadsEpoch) QUAD_CACHE.clear();
		quadsEpoch = epoch;
		quadsKey = key;
		Rotation rot = switch (c.placeRot) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		Mirror mirror = switch (c.placeMirror) {
			case 1 -> Mirror.FRONT_BACK;
			case 2 -> Mirror.LEFT_RIGHT;
			default -> Mirror.NONE;
		};
		QuadSet[] res = new QuadSet[paletteStates.length];
		for (int i = 0; i < res.length; i++) {
			BlockState base = paletteStates[i];
			if (base == null) {
				res[i] = new QuadSet();
				continue;
			}
			BlockState st = base.mirror(mirror).rotate(rot);
			QuadSet qs = QUAD_CACHE.get(st);
			if (qs == null) {
				qs = buildQuads(mc, st);
				QUAD_CACHE.put(st, qs);
			}
			res[i] = qs;
		}
		quadsByPal = res;
	}

	/** Ob die Seite d von der Kamera aus gesehen wird (dx/dy/dz = Block minus Kamera). */
	private static boolean faceToCamera(int d, float dx, float dy, float dz) {
		return switch (d) {
			case 0 -> dy > 0;
			case 1 -> dy < -1;
			case 2 -> dz > 0;
			case 3 -> dz < -1;
			case 4 -> dx > 0;
			default -> dx < -1;
		};
	}

	private static void render(WorldRenderContext ctx) {
		if (!active()) return;
		SchematicGrid g = grid;
		if (g == null) return;
		OemerConfig c = OemerConfig.get();
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.getLevelRenderState().cameraRenderState.pos;
		if (cam == null) return;
		PoseStack pose = ctx.matrices();
		Cells cells = shown;
		int n = cells.n;
		float t = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000f;
		if (drawn.length < n) drawn = new boolean[Math.max(n, drawn.length * 2)];
		java.util.Arrays.fill(drawn, 0, n, false);

		// ---- Pass 1: echte Texturen (nahe Blöcke)
		int texQuads = 0;
		if (c.placeTextures && n > 0) {
			ensureQuads(mc, c);
			VertexConsumer tex = ctx.consumers().getBuffer(RenderTypes.translucentMovingBlock());
			float maxSq = texDist * texDist;
			final float S = 0.985f;
			for (int i = 0; i < n && texQuads < 220_000; i++) {
				float dx = (float) (cells.x[i] - cam.x), dy = (float) (cells.y[i] - cam.y), dz = (float) (cells.z[i] - cam.z);
				float cx = dx + 0.5f, cy = dy + 0.5f, cz = dz + 0.5f;
				if (cx * cx + cy * cy + cz * cz > maxSq) continue;
				int pal = cells.pal[i];
				QuadSet qs = pal < quadsByPal.length ? quadsByPal[pal] : null;
				if (qs == null || qs.empty) continue;
				int kind = cells.kind[i];
				float r, gr, b, a;
				switch (kind) {
					case KIND_WRONG -> {
						r = 1f;
						gr = 0.45f;
						b = 0.45f;
						a = 0.70f;
					}
					case KIND_CORRECT -> {
						r = 0.6f;
						gr = 1f;
						b = 0.7f;
						a = 0.35f;
					}
					default -> {
						r = 0.80f;
						gr = 0.92f;
						b = 1f;
						a = 0.60f;
					}
				}
				if (c.placeGhost) a *= 0.86f + 0.14f * (float) Math.sin(t * 2.2f + (cells.x[i] + cells.y[i] + cells.z[i]) * 0.45f);
				int tint = pal < paletteColors.length ? paletteColors[pal] : 0xFFFFFF;
				pose.pushPose();
				pose.translate(cx, cy, cz);
				pose.scale(S, S, S);
				pose.translate(-0.5f, -0.5f, -0.5f);
				int mask = cells.mask[i];
				boolean fullCube = pal < paletteOpaque.length && paletteOpaque[pal];
				for (int d = 0; d < 6; d++) {
					if ((mask & (1 << d)) == 0) continue;
					if (fullCube && !faceToCamera(d, dx, dy, dz)) continue;
					for (BakedQuad q : qs.dir[d]) {
						if (q.isTinted()) {
							tex.putBulkData(pose.last(), q, r * ((tint >> 16 & 255) / 255f), gr * ((tint >> 8 & 255) / 255f), b * ((tint & 255) / 255f), a, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
						} else {
							tex.putBulkData(pose.last(), q, r, gr, b, a, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
						}
						texQuads++;
					}
				}
				for (BakedQuad q : qs.general) {
					if (q.isTinted()) {
						tex.putBulkData(pose.last(), q, r * ((tint >> 16 & 255) / 255f), gr * ((tint >> 8 & 255) / 255f), b * ((tint & 255) / 255f), a, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
					} else {
						tex.putBulkData(pose.last(), q, r, gr, b, a, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
					}
					texQuads++;
				}
				pose.popPose();
				drawn[i] = true;
			}
			// Gleichzeitig nicht zu viel zeichnen: Texturen-Radius passt sich der Last an
			if (texQuads > 150_000) texDist = Math.max(10f, texDist * 0.85f);
			else if (texQuads < 70_000 && texDist < 128f) texDist = Math.min(128f, texDist * 1.04f);
		}

		// ---- Pass 2: ferne Blöcke / Blöcke ohne Modell als einfache, farbige Flächen
		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugQuads());
		Matrix4f m = pose.last().pose();
		float e = 0.012f;
		float flatSq = flatDist * flatDist;
		int flatQuads = 0;
		for (int i = 0; i < n && flatQuads < 450_000; i++) {
			if (drawn[i]) continue;
			float dx = (float) (cells.x[i] - cam.x), dy = (float) (cells.y[i] - cam.y), dz = (float) (cells.z[i] - cam.z);
			float cx = dx + 0.5f, cy = dy + 0.5f, cz = dz + 0.5f;
			if (cx * cx + cy * cy + cz * cz > flatSq) continue;
			int pal = cells.pal[i];
			int color = switch (cells.kind[i]) {
				case KIND_WRONG -> 0xA0FF2A2A;
				case KIND_CORRECT -> 0x3040E070;
				default -> 0x78000000 | (pal < paletteColors.length ? paletteColors[pal] : 0xB0B0B0);
			};
			int mask = cells.mask[i];
			for (int d = 0; d < 6; d++) {
				if ((mask & (1 << d)) == 0 || !faceToCamera(d, dx, dy, dz)) continue;
				BoxDraw.face(vc, m, d, dx + e, dy + e, dz + e, dx + 1 - e, dy + 1 - e, dz + 1 - e, color);
				flatQuads++;
			}
		}
		if (flatQuads > 300_000) flatDist = Math.max(40f, flatDist * 0.85f);
		else if (flatQuads < 150_000 && flatDist < 4000f) flatDist = Math.min(4000f, flatDist * 1.05f);

		if (c.placeBox) {
			float x0 = (float) (c.placeX - cam.x), y0 = (float) (c.placeY - cam.y), z0 = (float) (c.placeZ - cam.z);
			float x1 = x0 + finalW(), y1 = y0 + g.sy, z1 = z0 + finalD();
			int col = 0xD0000000 | (Theme.accent() & 0xFFFFFF);
			float tt = 0.05f;
			// 4 senkrechte Kanten, 4 unten, 4 oben
			float[][] xz = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
			for (float[] p : xz) BoxDraw.box(vc, m, p[0] - tt, y0 - tt, p[1] - tt, p[0] + tt, y1 + tt, p[1] + tt, col);
			for (float y : new float[]{y0, y1}) {
				BoxDraw.box(vc, m, x0 - tt, y - tt, z0 - tt, x1 + tt, y + tt, z0 + tt, col);
				BoxDraw.box(vc, m, x0 - tt, y - tt, z1 - tt, x1 + tt, y + tt, z1 + tt, col);
				BoxDraw.box(vc, m, x0 - tt, y - tt, z0 - tt, x0 + tt, y + tt, z1 + tt, col);
				BoxDraw.box(vc, m, x1 - tt, y - tt, z0 - tt, x1 + tt, y + tt, z1 + tt, col);
			}
			// aktuelle Ebene als Fläche markieren
			if (c.placeLayerMode != 0) {
				int L = Math.min(c.placeLayer, g.sy - 1);
				float ly = y0 + L;
				int lc = 0x30000000 | (Theme.accent() & 0xFFFFFF);
				BoxDraw.box(vc, m, x0, ly, z0, x1, ly + 0.02f, z1, lc);
			}
		}
	}

}
