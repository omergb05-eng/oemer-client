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
import net.minecraft.client.renderer.rendertype.RenderTypes;
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

	private record Cell(int x, int y, int z, int color) {
	}

	private static volatile SchematicGrid grid;
	private static volatile String gridFile = "";
	private static volatile String error = "";
	private static volatile boolean loading;
	private static Block[] paletteBlocks = new Block[0];
	private static int[] paletteColors = new int[0];

	private static volatile List<Cell> shown = List.of();
	private static volatile int missing, wrong, correct;

	// laufender Durchgang
	private static List<Cell> building = new ArrayList<>();
	private static int scanY = -1, scanMin, scanMax, wMinX, wMaxX, wMinZ, wMaxZ;
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
		shown = List.of();
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
				for (int i = 0; i < blocks.length; i++) {
					Identifier id = Identifier.tryParse(g.ids[i]);
					Block b = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
					blocks[i] = b;
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
				paletteColors = colors;
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
		return "In Reichweite: fehlt " + missing + " · falsch " + wrong + " · passt " + correct;
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
		return shown.size();
	}

	// ------------------------------------------------------ Abgleich

	/** Jeden Tick ein Stück: vergleicht das Hologramm mit der echten Welt. */
	public static void tick(Minecraft mc) {
		OemerConfig c = OemerConfig.get();
		if (!c.placeOn || !c.placeSet || c.placeFile.isEmpty()) {
			if (!shown.isEmpty()) shown = List.of();
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
			scanMin = Math.max(lo, py - range);
			scanMax = Math.min(hi, py + range);
			if (scanMin > scanMax || wMinX > wMaxX || wMinZ > wMaxZ) {
				shown = List.of();
				missing = wrong = correct = 0;
				nextScan = System.currentTimeMillis() + 500;
				return;
			}
			building = new ArrayList<>();
			bMissing = bWrong = bCorrect = 0;
			scanY = scanMin;
		}

		int budget = 30000;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (scanY <= scanMax && budget > 0) {
			int y = scanY;
			for (int fx = wMinX; fx <= wMaxX; fx++) {
				for (int fz = wMinZ; fz <= wMaxZ; fz++) {
					budget--;
					int bi = blockAt(g, c.placeRot, c.placeMirror, fx, y, fz);
					if (bi < 0) continue;
					pos.set(c.placeX + fx, c.placeY + y, c.placeZ + fz);
					if (!level.hasChunkAt(pos)) continue;
					BlockState st = level.getBlockState(pos);
					Block want = bi < paletteBlocks.length ? paletteBlocks[bi] : null;
					if (want != null && st.getBlock() == want) {
						bCorrect++;
						if (c.placeShowCorrect && building.size() < 6000) building.add(new Cell(pos.getX(), pos.getY(), pos.getZ(), 0x3040E070));
					} else if (st.isAir() || !st.getFluidState().isEmpty()) {
						bMissing++;
						if (building.size() < 6000) {
							int col = bi < paletteColors.length ? paletteColors[bi] : 0xB0B0B0;
							building.add(new Cell(pos.getX(), pos.getY(), pos.getZ(), 0x78000000 | col));
						}
					} else {
						bWrong++;
						if (c.placeShowWrong && building.size() < 6000) building.add(new Cell(pos.getX(), pos.getY(), pos.getZ(), 0xA0FF2A2A));
					}
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

	private static void render(WorldRenderContext ctx) {
		if (!active()) return;
		SchematicGrid g = grid;
		if (g == null) return;
		OemerConfig c = OemerConfig.get();
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.getLevelRenderState().cameraRenderState.pos;
		if (cam == null) return;
		PoseStack pose = ctx.matrices();
		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugQuads());
		Matrix4f m = pose.last().pose();
		float e = 0.04f;
		for (Cell cell : shown) {
			float dx = (float) (cell.x - cam.x), dy = (float) (cell.y - cam.y), dz = (float) (cell.z - cam.z);
			BoxDraw.box(vc, m, dx + e, dy + e, dz + e, dx + 1 - e, dy + 1 - e, dz + 1 - e, cell.color);
		}
		if (c.placeBox) {
			float x0 = (float) (c.placeX - cam.x), y0 = (float) (c.placeY - cam.y), z0 = (float) (c.placeZ - cam.z);
			float x1 = x0 + finalW(), y1 = y0 + g.sy, z1 = z0 + finalD();
			int col = 0xD0000000 | (Theme.accent() & 0xFFFFFF);
			float t = 0.05f;
			// 4 senkrechte Kanten, 4 unten, 4 oben
			float[][] xz = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
			for (float[] p : xz) BoxDraw.box(vc, m, p[0] - t, y0 - t, p[1] - t, p[0] + t, y1 + t, p[1] + t, col);
			for (float y : new float[]{y0, y1}) {
				BoxDraw.box(vc, m, x0 - t, y - t, z0 - t, x1 + t, y + t, z0 + t, col);
				BoxDraw.box(vc, m, x0 - t, y - t, z1 - t, x1 + t, y + t, z1 + t, col);
				BoxDraw.box(vc, m, x0 - t, y - t, z0 - t, x0 + t, y + t, z1 + t, col);
				BoxDraw.box(vc, m, x1 - t, y - t, z0 - t, x1 + t, y + t, z1 + t, col);
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
