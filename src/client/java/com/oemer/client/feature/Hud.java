package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Draw;
import com.oemer.client.gui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Die HUD-Anzeigen im Spiel: Kit-HUD und Material List. */
public final class Hud {
	private Hud() {
	}

	/** Welche Items das Kit-HUD pro Kit zählt (Reihenfolge = Anzeige). */
	private static final String[][] KIT_ITEMS = {
			{}, // Normal World: Koordinaten statt Items
			{"totem_of_undying", "end_crystal", "obsidian", "respawn_anchor", "glowstone", "experience_bottle", "golden_apple", "ender_pearl"},
			{"totem_of_undying", "golden_apple", "enchanted_golden_apple", "ender_pearl", "splash_potion", "experience_bottle"},
			{"totem_of_undying", "shield", "golden_apple", "ender_pearl", "cobweb", "experience_bottle"},
			{"golden_apple", "arrow", "water_bucket", "lava_bucket", "cobweb", "cobblestone"},
			{"totem_of_undying", "golden_apple", "ender_pearl", "firework_rocket", "experience_bottle", "cooked_beef"},
			{"mace", "wind_charge", "totem_of_undying", "ender_pearl", "golden_apple", "experience_bottle"},
	};

	private static final List<List<Item>> KIT_CACHE = new ArrayList<>();

	private static List<Item> kitItems(int kit) {
		if (KIT_CACHE.isEmpty()) {
			for (String[] ids : KIT_ITEMS) {
				List<Item> list = new ArrayList<>();
				for (String id : ids) {
					Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
					if (item != Items.AIR) list.add(item);
				}
				KIT_CACHE.add(list);
			}
		}
		return KIT_CACHE.get(Math.floorMod(kit, KIT_CACHE.size()));
	}

	/** Kurzbeschreibung fürs Menü. */
	public static String kitDescription(int kit) {
		return switch (Math.floorMod(kit, KIT_ITEMS.length)) {
			case 0 -> "Zeigt Koordinaten, Richtung und FPS.";
			case 1 -> "Zählt Totems, Kristalle, Obsidian, Anker, XP …";
			case 2 -> "Zählt Totems, Äpfel, Perlen, Tränke, XP.";
			case 3 -> "Zählt Totems, Schilde, Äpfel, Perlen, Netze.";
			case 4 -> "Zählt Äpfel, Pfeile, Eimer, Netze, Blöcke.";
			case 5 -> "Zählt Totems, Äpfel, Perlen, Raketen, Essen.";
			default -> "Zählt Mace, Wind Charges, Totems, Perlen …";
		};
	}

	public static void render(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;
		OemerConfig c = OemerConfig.get();
		if (c.kitHud) renderKit(g, mc, c);
		if (c.materialList) renderMaterials(g, mc, c);
	}

	// ------------------------------------------------------------ Kit-HUD

	private static void renderKit(GuiGraphics g, Minecraft mc, OemerConfig c) {
		List<String> lines = new ArrayList<>();
		List<ItemStack> icons = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		if (Math.floorMod(c.kit, KIT_ITEMS.length) == 0) {
			var p = mc.player;
			lines.add(String.format("X %d  Y %d  Z %d", (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ())));
			lines.add("Richtung " + p.getDirection().getName());
			lines.add(mc.getFps() + " FPS");
			for (int i = 0; i < lines.size(); i++) {
				icons.add(ItemStack.EMPTY);
				colors.add(Theme.TEXT);
			}
		} else {
			for (Item item : kitItems(c.kit)) {
				int n = MaterialList.have(item);
				icons.add(new ItemStack(item));
				lines.add(String.valueOf(n));
				colors.add(n == 0 ? Theme.BAD : Theme.TEXT);
			}
		}

		String title = OemerConfig.KITS[Math.floorMod(c.kit, OemerConfig.KITS.length)];
		box(g, c.kitHudCorner, c, title, lines, icons, colors, 0);
	}

	// ------------------------------------------------------- Material List

	private static void renderMaterials(GuiGraphics g, Minecraft mc, OemerConfig c) {
		List<MaterialList.Entry> all = MaterialList.entries();
		List<String> lines = new ArrayList<>();
		List<ItemStack> icons = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		String title = c.schematic.isEmpty() ? "Material List" : CapeManager.shortName(c.schematic);

		if (c.schematic.isEmpty()) {
			lines.add("Keine Schematic gewählt");
			icons.add(ItemStack.EMPTY);
			colors.add(Theme.TEXT_DIM);
		} else if (!MaterialList.error().isEmpty()) {
			lines.add(MaterialList.error());
			icons.add(ItemStack.EMPTY);
			colors.add(Theme.BAD);
		} else {
			int shown = 0, hidden = 0;
			for (MaterialList.Entry e : all) {
				if (MaterialList.isDone(e)) continue;
				int have = MaterialList.have(e.item());
				if (shown >= 10) {
					hidden++;
					continue;
				}
				icons.add(e.icon());
				lines.add(Draw.fit(e.name(), 92) + "  " + have + "/" + e.needed());
				colors.add(have >= e.needed() ? Theme.GOOD : Theme.TEXT);
				shown++;
			}
			if (shown == 0) {
				lines.add("Alles erledigt ✔");
				icons.add(ItemStack.EMPTY);
				colors.add(Theme.GOOD);
			}
			if (hidden > 0) {
				lines.add("+ " + hidden + " weitere");
				icons.add(ItemStack.EMPTY);
				colors.add(Theme.TEXT_DIM);
			}
		}
		// gleiche Ecke wie Kit-HUD? Dann darunter/darüber stapeln
		int offset = 0;
		if (c.kitHud && c.kitHudCorner == c.materialListCorner) {
			offset = lastHeight + 6;
		}
		box(g, c.materialListCorner, c, title, lines, icons, colors, offset);
	}

	// --------------------------------------------------------------- Box

	private static int lastHeight;

	private static void box(GuiGraphics g, int corner, OemerConfig c, String title,
							List<String> lines, List<ItemStack> icons, List<Integer> colors, int offset) {
		float scale = OemerConfig.HUD_SCALES[Math.floorMod(c.hudScale, OemerConfig.HUD_SCALES.length)];
		int rowH = 18;
		int pad = 6;
		int textW = Draw.width(title);
		boolean anyIcon = false;
		for (int i = 0; i < lines.size(); i++) {
			boolean icon = !icons.get(i).isEmpty();
			anyIcon |= icon;
			textW = Math.max(textW, Draw.width(lines.get(i)) + (icon ? 20 : 0));
		}
		int w = textW + pad * 2;
		int h = pad + 11 + lines.size() * rowH + pad - 2;
		lastHeight = Math.round(h * scale);

		int sw = g.guiWidth(), sh = g.guiHeight();
		int margin = 6;
		float bx = (corner % 2 == 0) ? margin : sw - margin - w * scale;
		float by = (corner < 2) ? margin + offset : sh - margin - h * scale - offset;
		if (corner >= 2) by -= 0; // unten: Hotbar ist in der Mitte, Ecken sind frei

		g.pose().pushMatrix();
		g.pose().translate(bx, by);
		g.pose().scale(scale);

		Draw.round(g, 0, 0, w, h, 5, 0x9C0B0B0F);
		Draw.roundBorder(g, 0, 0, w, h, 5, Theme.white(0x30));
		Draw.round(g, pad, pad + 2, 2, 7, 1, Theme.accent());
		Draw.text(g, title, pad + 6, pad + 1, Theme.TEXT);

		int y = pad + 13;
		for (int i = 0; i < lines.size(); i++) {
			ItemStack icon = icons.get(i);
			int tx = pad;
			if (!icon.isEmpty()) {
				g.renderItem(icon, pad, y);
				tx += 20;
			} else if (anyIcon) {
				tx += 0;
			}
			Draw.textShadow(g, lines.get(i), tx, y + 4, colors.get(i));
			y += rowH;
		}
		g.pose().popMatrix();
	}
}
