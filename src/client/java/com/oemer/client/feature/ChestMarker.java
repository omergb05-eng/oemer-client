package com.oemer.client.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oemer.client.OemerConfig;
import com.oemer.client.OemerConfig.MarkedChest;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.gui.screen().v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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
import java.util.Map;

/**
 * Material List: Kisten markieren (Sneaken + Rechtsklick mit Spitzhacke).
 * Wenn du eine markierte Kiste öffnest, merkt sich der Client ihren Inhalt und zählt ihn in der Material List mit.
 * Markierte Kisten leuchten in der Welt.
 */
public final class ChestMarker {
	private ChestMarker() {
	}

	private static BlockPos pending;
	private static long pendingAt;

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ClientLevel)) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockEntity be = level.getBlockEntity(pos);
			if (!(be instanceof Container)) return InteractionResult.PASS;
			if (player.isShiftKeyDown() && player.getItemInHand(hand).is(ItemTags.PICKAXES)) {
				toggle(pos);
				return InteractionResult.FAIL; // nichts an den Server schicken
			}
			MarkedChest m = find(pos);
			if (m != null) {
				pending = new BlockPos(m.x, m.y, m.z);
				pendingAt = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			if (pending == null || System.currentTimeMillis() - pendingAt > 4000) return;
			BlockPos target = pending;
			pending = null;
			ScreenEvents.afterTick(screen).register(s -> record(acs, target, false));
			ScreenEvents.remove(screen).register(s -> record(acs, target, true));
		});

		LevelRenderEvents.COLLECT_SUBMITS.register(ChestMarker::render);
	}

	// --------------------------------------------------------------- Welt

	/** Welt + Dimension, damit Kisten aus anderen Welten nicht mitzählen. */
	public static String worldKey() {
		Minecraft mc = Minecraft.getInstance();
		String base = "?";
		try {
			if (mc.getSingleplayerServer() != null) base = "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
			else if (mc.getCurrentServer() != null) base = "mp:" + mc.getCurrentServer().ip;
		} catch (Exception ignored) {
		}
		String dim = mc.level == null ? "" : mc.level.dimension().toString();
		return base + "|" + dim;
	}

	public static List<MarkedChest> here() {
		List<MarkedChest> out = new ArrayList<>();
		if (Minecraft.getInstance().level == null) return out;
		String key = worldKey();
		for (MarkedChest m : OemerConfig.get().markedChests) if (key.equals(m.world)) out.add(m);
		return out;
	}

	/** Markierte Kiste an dieser Stelle (auch die andere Hälfte einer Doppelkiste). */
	public static MarkedChest find(BlockPos pos) {
		for (MarkedChest m : here()) {
			if (m.x == pos.getX() && m.y == pos.getY() && m.z == pos.getZ()) return m;
		}
		BlockPos other = otherHalf(pos);
		if (other != null) {
			for (MarkedChest m : here()) {
				if (m.x == other.getX() && m.y == other.getY() && m.z == other.getZ()) return m;
			}
		}
		return null;
	}

	private static BlockPos otherHalf(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		BlockState s = mc.level.getBlockState(pos);
		if (!(s.getBlock() instanceof ChestBlock) || s.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
		return pos.relative(ChestBlock.getConnectedDirection(s));
	}

	public static void toggle(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		MarkedChest m = find(pos);
		if (m != null) {
			OemerConfig.get().markedChests.remove(m);
			say(mc, "§fKiste §centfernt §7aus der Material List");
		} else {
			MarkedChest n = new MarkedChest();
			n.world = worldKey();
			n.x = pos.getX();
			n.y = pos.getY();
			n.z = pos.getZ();
			OemerConfig.get().markedChests.add(n);
			say(mc, "§fKiste §amarkiert §7– einmal öffnen, dann zählt sie mit");
		}
		OemerConfig.save();
	}

	public static void remove(MarkedChest m) {
		OemerConfig.get().markedChests.remove(m);
		OemerConfig.save();
	}

	private static void say(Minecraft mc, String text) {
		if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(text));
	}

	/** Inhalt der offenen Kiste merken (alle Plätze, die nicht dein Inventar sind). */
	static void record(AbstractContainerScreen<?> screen, BlockPos target, boolean save) {
		MarkedChest m = find(target);
		if (m == null) return;
		Map<String, Integer> items = new HashMap<>();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			ItemStack s = slot.getItem();
			if (s.isEmpty()) continue;
			items.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
		}
		m.items = items;
		m.updated = System.currentTimeMillis();
		if (save) OemerConfig.save();
	}

	/** Für Tests: Inhalt direkt setzen. */
	public static void setContents(BlockPos pos, Map<String, Integer> items) {
		MarkedChest m = find(pos);
		if (m != null) {
			m.items = new HashMap<>(items);
			m.updated = System.currentTimeMillis();
		}
	}

	/** Wie viele davon liegen in deinen markierten Kisten? */
	public static int count(Item item) {
		if (!OemerConfig.get().chestCount) return 0;
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		int n = 0;
		for (MarkedChest m : here()) n += m.items.getOrDefault(id, 0);
		return n;
	}

	// ------------------------------------------------------------ Leuchten

	private static void render(LevelRenderContext ctx) {
		OemerConfig c = OemerConfig.get();
		if (!c.chestHighlight || !c.materialList) return;
		List<MarkedChest> list = here();
		if (list.isEmpty()) return;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		if (cam == null) return;
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.debugQuads(), (po, vc) -> draw(po, vc, cam, list));
	}

	private static void draw(PoseStack.Pose po, VertexConsumer vc, Vec3 cam, List<MarkedChest> list) {
		Matrix4f m = po.pose();
		float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
		int a = Math.round(0x38 + 0x30 * pulse);
		int color = (a << 24) | (com.oemer.client.gui.Theme.accent() & 0xFFFFFF);
		for (MarkedChest mc2 : list) {
			double dx = mc2.x - cam.x, dy = mc2.y - cam.y, dz = mc2.z - cam.z;
			if (dx * dx + dy * dy + dz * dz > 96 * 96) continue;
			float e = 0.03f;
			BoxDraw.box(vc, m, (float) dx - e, (float) dy - e, (float) dz - e, (float) dx + 1 + e, (float) dy + 1 + e, (float) dz + 1 + e, color);
		}
	}

}
