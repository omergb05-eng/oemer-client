package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Draw;
import com.oemer.client.gui.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Locale;

/** Kleine HUD-Mods: AppleSkin (Sättigung + Vorschau) und Note Block Tuner. */
public final class ModHud {
	private ModHud() {
	}

	// ------------------------------------------------------------ AppleSkin

	public static void food(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (!OemerConfig.get().appleSkin || p == null || mc.options.hideGui || p.isCreative() || p.isSpectator()) return;
		if (p.getVehicle() instanceof LivingEntity) return; // dann zeigt Minecraft die Reittier-Herzen
		int right = g.guiWidth() / 2 + 91;
		int top = g.guiHeight() - 39;
		int food = p.getFoodData().getFoodLevel();
		float sat = p.getFoodData().getSaturationLevel();

		// Sättigung: goldener Rahmen um die Keulen
		for (int i = 0; i < 10; i++) {
			float part = Math.max(0f, Math.min(2f, sat - i * 2));
			if (part <= 0f) break;
			int x = right - i * 8 - 9;
			int col = part >= 2f ? 0xD0FFC23A : 0x80FFC23A;
			g.fill(x, top, x + 9, top + 1, col);
			g.fill(x, top + 8, x + 9, top + 9, col);
			g.fill(x, top, x + 1, top + 9, col);
			g.fill(x + 8, top, x + 9, top + 9, col);
		}

		// Vorschau: so viel Hunger bringt das Essen in deiner Hand (blinkt)
		FoodProperties f = heldFood(p);
		if (f != null) {
			int to = Math.min(20, food + f.nutrition());
			float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0));
			int a = Math.round(0x30 + 0x60 * pulse);
			for (int point = food + 1; point <= to; point++) {
				int i = (point - 1) / 2;
				int x = right - i * 8 - 9;
				boolean half = point % 2 == 1;
				// Keulen werden von rechts gefüllt: erst rechte, dann linke Hälfte
				int x0 = half ? x + 4 : x;
				int x1 = half ? x + 9 : x + 5;
				g.fill(x0 + (half ? 0 : 1), top + 1, x1 - (half ? 1 : 0), top + 8, (a << 24) | 0xFFFFFF);
			}
		}
	}

	private static FoodProperties heldFood(Player p) {
		for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
			FoodProperties f = s.get(DataComponents.FOOD);
			if (f != null) return f;
		}
		return null;
	}

	/** Tooltip-Zeile für Essen (AppleSkin). */
	public static void foodTooltip(ItemStack stack, List<Component> lines) {
		if (!OemerConfig.get().appleSkin) return;
		FoodProperties f = stack.get(DataComponents.FOOD);
		if (f == null) return;
		float satGain = f.saturation();
		lines.add(Component.literal("§6🍗 +" + f.nutrition() + " Hunger  §e✦ +" + String.format(Locale.ROOT, "%.1f", satGain) + " Sättigung"));
	}

	// ----------------------------------------------------- Note Block Tuner

	private static final String[] NOTES = {"F#", "G", "G#", "A", "A#", "H", "C", "C#", "D", "D#", "E", "F"};

	public static String noteName(int note) {
		int octave = note < 6 ? 3 : note < 18 ? 4 : 5;
		return NOTES[note % 12] + octave;
	}

	public static String instrumentName(NoteBlockInstrument i) {
		return switch (i) {
			case HARP -> "Klavier";
			case BASEDRUM -> "Bass-Trommel";
			case SNARE -> "Snare";
			case HAT -> "Hi-Hat";
			case BASS -> "Bass";
			case FLUTE -> "Flöte";
			case BELL -> "Glocke";
			case GUITAR -> "Gitarre";
			case CHIME -> "Glockenspiel";
			case XYLOPHONE -> "Xylophon";
			case IRON_XYLOPHONE -> "Vibraphon";
			case COW_BELL -> "Kuhglocke";
			case DIDGERIDOO -> "Didgeridoo";
			case BIT -> "8-Bit";
			case BANJO -> "Banjo";
			case PLING -> "Pling";
			default -> i.getSerializedName();
		};
	}

	public static void noteTuner(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!OemerConfig.get().noteTuner || mc.player == null || mc.level == null || mc.options.hideGui) return;
		HitResult hit = mc.hitResult;
		if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
		BlockState state = mc.level.getBlockState(bh.getBlockPos());
		if (!(state.getBlock() instanceof NoteBlock)) return;
		int note = state.getValue(NoteBlock.NOTE);
		NoteBlockInstrument inst = state.getValue(NoteBlock.INSTRUMENT);
		int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
		String title = "♪ " + noteName(note) + "  ·  " + instrumentName(inst);
		int w = Math.max(25 * 5 + 12, Draw.width(title) + 16);
		int x = cx - w / 2;
		Draw.round(g, x, y, w, 40, 5, 0xC00B0B0F);
		Draw.roundBorder(g, x, y, w, 40, 5, Theme.white(0x30));
		Draw.centered(g, title, cx, y + 5, Theme.TEXT);
		// Mini-Klaviatur: 25 Töne
		int kx = cx - (25 * 5) / 2;
		for (int i = 0; i < 25; i++) {
			String n = NOTES[i % 12];
			boolean black = n.endsWith("#");
			int col = i == note ? Theme.accent() : black ? 0xFF2A2A30 : 0xFFE8E8EE;
			g.fill(kx + i * 5, y + 17, kx + i * 5 + 4, y + 27, col);
		}
		Draw.centered(g, "Ton " + note + " von 24 · Rechtsklick = höher", cx, y + 30, Theme.TEXT_DIM);
	}
}
