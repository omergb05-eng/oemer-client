package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Draw;
import com.oemer.client.gui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

	public static void render(GuiGraphicsExtractor g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) return;
		OemerConfig c = OemerConfig.get();
		if (c.kitHud) renderKit(g, mc, c);
		if (c.materialList) renderMaterials(g, mc, c);
		if (c.spotifyHud) renderSpotify(g, c);
		if (c.armorHud) renderArmor(g, mc, c);
		com.oemer.client.online.Notify.render(g, g.guiWidth());
	}

	// ------------------------------------------------------------ Armor-HUD

	private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
			net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};

	/** Was die Rüstungs-Anzeige gerade zeigt (auch für Tests). */
	public static List<ItemStack> armorStacks(net.minecraft.world.entity.player.Player p, boolean hands) {
		return armorStacks(p, hands, false);
	}

	/** gear = Schwert, Axt und Schild aus der Hotbar (auch wenn sie gerade nicht in der Hand sind). */
	public static List<ItemStack> armorStacks(net.minecraft.world.entity.player.Player p, boolean hands, boolean gear) {
		List<ItemStack> list = new ArrayList<>();
		for (var slot : ARMOR_SLOTS) list.add(p.getItemBySlot(slot));
		if (hands) {
			list.add(p.getMainHandItem());
			list.add(p.getOffhandItem());
		}
		if (gear) {
			List<ItemStack> src = new ArrayList<>();
			for (int i = 0; i < 9; i++) src.add(p.getInventory().getItem(i));
			src.add(p.getOffhandItem());
			boolean sword = false, axe = false, shield = false;
			for (ItemStack s : src) {
				if (s.isEmpty() || !s.isDamageableItem()) continue;
				boolean already = false;
				for (ItemStack o : list) if (o == s) already = true;
				if (already) continue;
				if (!sword && s.is(net.minecraft.tags.ItemTags.SWORDS)) { list.add(s); sword = true; }
				else if (!axe && s.is(net.minecraft.tags.ItemTags.AXES)) { list.add(s); axe = true; }
				else if (!shield && s.is(net.minecraft.world.item.Items.SHIELD)) { list.add(s); shield = true; }
			}
		}
		return list;
	}

	/** Text neben dem Symbol: je nach Einstellung Prozent, Zahl oder beides. */
	public static String armorText(int mode, int left, int max, float f) {
		String pct = Math.round(f * 100) + " %";
		String num = left + "/" + max;
		return switch (mode) {
			case 1 -> num;
			case 3 -> pct + " · " + num;
			default -> pct;
		};
	}

	private static void renderArmor(GuiGraphicsExtractor g, Minecraft mc, OemerConfig c) {
		List<ItemStack> stacks = armorStacks(mc.player, c.armorHudHands, c.armorHudGear);
		List<ItemStack> shown = new ArrayList<>();
		for (ItemStack s : stacks) if (!s.isEmpty()) shown.add(s);
		if (shown.isEmpty()) return;
		float scale = OemerConfig.HUD_SCALES[Math.floorMod(c.hudScale, OemerConfig.HUD_SCALES.length)];
		int rowH = 20;
		int w = c.armorHudValue == 3 ? 128 : c.armorHudValue == 1 ? 86 : c.armorHudValue == 0 ? 60 : 30;
		int h = shown.size() * rowH + 6;
		int sw = g.guiWidth(), sh = g.guiHeight();
		float bx, by;
		switch (c.armorHudPos) {
			case 1 -> {
				bx = sw - 6 - w * scale;
				by = sh / 2f - h * scale / 2f;
			}
			case 2 -> {
				bx = sw / 2f - 91 - 8 - w * scale;
				by = sh - 4 - h * scale;
			}
			case 3 -> {
				bx = sw / 2f + 91 + 8 + 30;
				by = sh - 4 - h * scale;
			}
			default -> {
				bx = 6;
				by = sh / 2f - h * scale / 2f;
			}
		}
		g.pose().pushMatrix();
		g.pose().translate(bx, by);
		g.pose().scale(scale);
		Draw.round(g, 0, 0, w, h, 6, 0xA00B0B0F);
		Draw.roundBorder(g, 0, 0, w, h, 6, Theme.white(0x28));
		int y = 3;
		for (ItemStack s : shown) {
			g.item(s, 4, y + 1);
			if (c.armorHudValue != 2 && s.isDamageableItem()) {
				int max = s.getMaxDamage(), left = max - s.getDamageValue();
				float f = max <= 0 ? 1f : Math.max(0f, Math.min(1f, left / (float) max));
				int col = java.awt.Color.HSBtoRGB(f * 0.33f, 0.75f, 1f) | 0xFF000000;
				String text = armorText(c.armorHudValue, left, max, f);
				Draw.text(g, text, 24, y + 6, col);
			} else if (c.armorHudValue != 2 && s.getCount() > 1) {
				Draw.text(g, "x" + s.getCount(), 24, y + 6, Theme.TEXT_SOFT);
			}
			if (s.isDamageableItem() && c.armorHudValue == 2) {
				int max = s.getMaxDamage(), left = max - s.getDamageValue();
				float f = max <= 0 ? 1f : Math.max(0f, Math.min(1f, left / (float) max));
				int col = java.awt.Color.HSBtoRGB(f * 0.33f, 0.75f, 1f) | 0xFF000000;
				int bh = Math.max(1, Math.round(16 * f));
				g.fill(23, y + 1, 26, y + 17, Theme.white(0x20));
				g.fill(23, y + 17 - bh, 26, y + 17, col);
			}
			y += rowH;
		}
		g.pose().popMatrix();
	}


	// ------------------------------------------------------------- Spotify

	private static void renderSpotify(GuiGraphicsExtractor g, OemerConfig c) {
		Music.Now t = Music.now();
		if (t == null) return;
		float scale = OemerConfig.HUD_SCALES[Math.floorMod(c.hudScale, OemerConfig.HUD_SCALES.length)];
		int w = 150, h = 38, cs = 28;
		int corner = c.spotifyHudCorner;
		int sw = g.guiWidth(), sh = g.guiHeight(), m = 6;
		float bx = (corner % 2 == 0) ? m : sw - m - w * scale;
		float by = (corner < 2) ? m : sh - m - h * scale;
		// nicht über die anderen HUDs in derselben Ecke legen
		if (c.kitHud && c.kitHudCorner == corner || c.materialList && c.materialListCorner == corner) {
			by = corner < 2 ? by + lastHeight + 6 : by - lastHeight - 6;
		}
		g.pose().pushMatrix();
		g.pose().translate(bx, by);
		g.pose().scale(scale);
		Draw.round(g, 0, 0, w, h, 6, 0xB00B0B0F);
		Draw.roundBorder(g, 0, 0, w, h, 6, Theme.white(0x30));
		java.awt.image.BufferedImage cover = Spotify.cover(t.imageUrl());
		Draw.round(g, 5, 5, cs, cs, 3, Theme.white(0x18));
		if (cover != null) {
			Textures.Tex tex = Textures.image("music_cover", t.imageUrl(), cover);
			if (tex != null) Textures.drawCover(g, tex, 5, 5, cs, cs);
		}
		int tx = 5 + cs + 7, tw = w - tx - 6;
		Draw.text(g, Draw.fit(t.title(), tw), tx, 7, Theme.TEXT);
		Draw.text(g, Draw.fit(t.artist(), tw), tx, 17, Theme.TEXT_DIM);
		float f = t.durationMs() <= 0 ? 0 : (float) t.progressMs() / t.durationMs();
		Draw.round(g, tx, 29, tw, 2, 1, Theme.white(0x26));
		Draw.round(g, tx, 29, Math.max(2, Math.round(tw * f)), 2, 1, Theme.accent());
		if (!t.playing()) Draw.text(g, "❚❚", w - 14, 7, Theme.TEXT_DIM);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------ Kit-HUD

	private static void renderKit(GuiGraphicsExtractor g, Minecraft mc, OemerConfig c) {
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

	private static void renderMaterials(GuiGraphicsExtractor g, Minecraft mc, OemerConfig c) {
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
				int have = MaterialList.haveTotal(e.item());
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

	private static void box(GuiGraphicsExtractor g, int corner, OemerConfig c, String title,
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
				g.item(icon, pad, y);
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
