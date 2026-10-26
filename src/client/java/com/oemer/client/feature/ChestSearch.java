package com.oemer.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oemer.client.OemerConfig;
import com.oemer.client.gui.ChestSearchScreen;
import com.oemer.client.gui.OmerWidgets;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Chest Search: in jeder Kiste oben ein Suchfeld (passende Items leuchten), dazu eine Item-Auswahl auf einer Taste.
 * Gewählte Items lassen alle Kisten im Umkreis von 20 Blöcken leicht leuchten, in denen sie liegen
 * (der Client kennt den Inhalt jeder Kiste, die du einmal geöffnet hast).
 */
public final class ChestSearch {
	private ChestSearch() {
	}

	public static final int RADIUS = 20;

	/** Suchtext im offenen Kisten-Fenster. */
	public static String query = "";
	private static EditBox box;

	private static BlockPos pending;
	private static long pendingAt;
	/** Bekannter Kisten-Inhalt: Welt|x,y,z → Item-ID → Anzahl. */
	private static final Map<String, Map<String, Integer>> KNOWN = new HashMap<>();
	/** Wie viele Kisten gerade leuchten (für Tests). */
	public static volatile int glowing;

	public static boolean on() {
		return OemerConfig.get().chestSearch;
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ClientLevel) || !on()) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			if (level.getBlockEntity(pos) instanceof Container) {
				pending = pos;
				pendingAt = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs) || !(acs.getMenu() instanceof ChestMenu)) return;
			box = null;
			if (!on()) return;
			BlockPos target = pending != null && System.currentTimeMillis() - pendingAt < 4000 ? pending : null;
			pending = null;
			addSearchBox(screen, w);
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> highlightSlots(acs, s, g));
			if (target != null) {
				ScreenEvents.afterTick(screen).register(s -> record(acs, target));
				ScreenEvents.remove(screen).register(s -> {
					record(acs, target);
					box = null;
					query = "";
				});
			} else {
				ScreenEvents.remove(screen).register(s -> {
					box = null;
					query = "";
				});
			}
		});

		LevelRenderEvents.COLLECT_SUBMITS.register(ChestSearch::render);
	}

	private static void addSearchBox(net.minecraft.client.gui.screens.Screen screen, int w) {
		Minecraft mc = Minecraft.getInstance();
		query = "";
		int bw = 150;
		int x = w / 2 - bw / 2 - 16;
		box = new EditBox(mc.font, x + 4, 7, bw - 8, 12, Component.literal("Suche"));
		box.setBordered(false);
		box.setMaxLength(40);
		box.setHint(Component.literal("Kiste durchsuchen … (z. B. Dia)"));
		box.setResponder(v -> query = v.trim().toLowerCase(Locale.ROOT));
		List<net.minecraft.client.gui.components.AbstractWidget> widgets = Screens.getWidgets(screen);
		widgets.add(new OmerWidgets.Flat(x - 2, 3, bw + 4, 20, "", () -> {
			if (box != null) screen.setFocused(box);
		}));
		widgets.add(box);
		widgets.add(new OmerWidgets.Flat(x + bw + 6, 3, 74, 20, "Items …", () -> mc.gui.setScreen(new ChestSearchScreen(screen)))
				.tip("Alle Items durchsuchen – Kisten mit diesen Items im Umkreis leuchten"));
	}

	/** Treffer in der offenen Kiste leuchten gold, alles andere in der Kiste wird abgedunkelt. */
	private static void highlightSlots(AbstractContainerScreen<?> acs, net.minecraft.client.gui.screens.Screen s,
			net.minecraft.client.gui.GuiGraphicsExtractor g) {
		if (!on() || !OemerConfig.get().chestSearchSlots || !searching() || !(acs.getMenu() instanceof ChestMenu menu)) return;
		int left = (s.width - 176) / 2;
		int top = (s.height - (114 + menu.getRowCount() * 18)) / 2;
		float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory) continue;
			int x = left + slot.x, y = top + slot.y;
			if (matches(slot.getItem())) {
				g.fill(x, y, x + 16, y + 16, ((0x30 + Math.round(0x28 * pulse)) << 24) | 0xFFD76A);
				com.oemer.client.gui.Draw.roundBorder(g, x - 1, y - 1, 18, 18, 2, 0xFFFFD76A);
			} else {
				g.fill(x, y, x + 16, y + 16, 0x99101018);
			}
		}
	}

	/** Das Suchfeld im Kisten-Fenster hat gerade den Fokus (Tasten wie E dürfen dann das Fenster nicht schließen). */
	public static boolean typing() {
		return box != null && box.isFocused() && box.canConsumeInput();
	}

	public static boolean boxKey(net.minecraft.client.input.KeyEvent event) {
		return box != null && box.keyPressed(event);
	}

	// ------------------------------------------------------------- Treffer

	public static Set<String> selected() {
		return Set.copyOf(OemerConfig.get().searchItems);
	}

	public static void toggle(String id) {
		List<String> l = OemerConfig.get().searchItems;
		if (!l.remove(id)) l.add(id);
		OemerConfig.save();
	}

	public static void clearSelection() {
		OemerConfig.get().searchItems.clear();
		OemerConfig.save();
	}

	public static String idOf(ItemStack s) {
		return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
	}

	/** Passt das Item zum Suchtext oder zu einem gewählten Item? */
	public static boolean matches(ItemStack s) {
		if (s.isEmpty()) return false;
		if (!query.isEmpty()) {
			String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
			if (name.contains(query) || idOf(s).contains(query)) return true;
		}
		return OemerConfig.get().searchItems.contains(idOf(s));
	}

	/** Ist etwas gesucht (Text oder Auswahl)? */
	public static boolean searching() {
		return !query.isEmpty() || !OemerConfig.get().searchItems.isEmpty();
	}

	// ------------------------------------------------------------- Merken

	private static String key(BlockPos p) {
		return ChestMarker.worldKey() + "|" + p.getX() + "," + p.getY() + "," + p.getZ();
	}

	private static BlockPos otherHalf(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		BlockState s = mc.level.getBlockState(pos);
		if (!(s.getBlock() instanceof ChestBlock) || s.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
		return pos.relative(ChestBlock.getConnectedDirection(s));
	}

	static void record(AbstractContainerScreen<?> screen, BlockPos target) {
		Map<String, Integer> items = new HashMap<>();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack s = slot.getItem();
			if (s.isEmpty()) continue;
			items.merge(idOf(s), s.getCount(), Integer::sum);
		}
		remember(target, items);
	}

	/** Inhalt einer Kiste merken (bei Doppelkisten für beide Hälften). Auch für Tests. */
	public static void remember(BlockPos pos, Map<String, Integer> items) {
		KNOWN.put(key(pos), new HashMap<>(items));
		BlockPos other = otherHalf(pos);
		if (other != null) KNOWN.put(key(other), new HashMap<>(items));
	}

	public static int knownChests() {
		return KNOWN.size();
	}

	/** Alle bekannten Kisten im Umkreis, in denen mindestens eines der gewählten Items liegt. */
	public static List<BlockPos> hits(Vec3 from) {
		List<BlockPos> out = new ArrayList<>();
		Set<String> want = selected();
		if (want.isEmpty()) return out;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return out;
		String prefix = ChestMarker.worldKey() + "|";
		var it = KNOWN.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			if (!e.getKey().startsWith(prefix)) continue;
			String[] xyz = e.getKey().substring(prefix.length()).split(",");
			BlockPos p = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
			double dx = p.getX() + 0.5 - from.x, dy = p.getY() + 0.5 - from.y, dz = p.getZ() + 0.5 - from.z;
			if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) continue;
			BlockEntity be = mc.level.getBlockEntity(p);
			if (!(be instanceof Container)) {
				it.remove(); // Kiste ist weg
				continue;
			}
			for (String id : e.getValue().keySet()) {
				if (want.contains(id)) {
					out.add(p);
					break;
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------- Leuchten

	private static void render(LevelRenderContext ctx) {
		OemerConfig c = OemerConfig.get();
		if (!c.chestSearch || !c.chestSearchGlow || c.searchItems.isEmpty()) {
			glowing = 0;
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		if (cam == null) return;
		List<BlockPos> list = hits(cam);
		glowing = list.size();
		if (list.isEmpty()) return;
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.debugQuads(), (po, vc) -> draw(po, vc, cam, list));
	}

	private static void draw(PoseStack.Pose po, VertexConsumer vc, Vec3 cam, List<BlockPos> list) {
		Matrix4f m = po.pose();
		float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 450.0));
		int a = Math.round(0x1C + 0x20 * pulse); // dezent: nur ein leichter Schein
		int color = (a << 24) | 0xFFD76A;
		for (BlockPos p : list) {
			float dx = (float) (p.getX() - cam.x), dy = (float) (p.getY() - cam.y), dz = (float) (p.getZ() - cam.z);
			float e = 0.02f;
			BoxDraw.box(vc, m, dx - e, dy - e, dz - e, dx + 1 + e, dy + 1 + e, dz + 1 + e, color);
		}
	}
}
