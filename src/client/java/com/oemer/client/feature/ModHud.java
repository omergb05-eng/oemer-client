package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import com.oemer.client.gui.Draw;
import com.oemer.client.gui.Theme;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
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

		loadGold();
		// Sättigung: goldene Keulen genau in der Form der echten Hunger-Symbole
		for (int i = 0; i < 10; i++) {
			float part = Math.max(0f, Math.min(2f, sat - i * 2));
			if (part <= 0f) break;
			int x = right - i * 8 - 9;
			drawGold(g, part >= 2f ? goldFull : goldHalf, x, top, 0xFFFFFFFF);
		}

		// Vorschau: so viel Hunger bringt das Essen in deiner Hand (blinkt)
		FoodProperties f = heldFood(p);
		if (f != null) {
			int to = Math.min(20, food + f.nutrition());
			float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0));
			int a = Math.round(0x50 + 0x80 * pulse);
			for (int i = food / 2; i < (to + 1) / 2; i++) {
				int x = right - i * 8 - 9;
				int from = Math.max(food, i * 2), upTo = Math.min(to, i * 2 + 2);
				boolean full = upTo - from >= 2;
				drawGold(g, full ? goldFull : goldHalf, x, top, (a << 24) | 0xFFFFFF);
			}
		}
	}

	// ---- goldene Hunger-Symbole: aus den echten Minecraft-Symbolen umgefärbt, damit die Form identisch ist
	private static Textures.Tex goldFull, goldHalf;
	private static boolean goldTried;

	/** Zum Testen: true, wenn die goldenen Symbole fertig sind. */
	public static boolean goldReady() {
		return goldFull != null && goldHalf != null;
	}

	private static void drawGold(GuiGraphics g, Textures.Tex t, int x, int y, int color) {
		if (t == null) return;
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, 0f, 0f, 9, 9, t.width(), t.height(), t.width(), t.height(), color);
	}

	private static BufferedImage sprite(String name) {
		try {
			var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace("textures/gui/sprites/hud/" + name + ".png"));
			if (res.isEmpty()) return null;
			try (var in = res.get().open()) {
				return ImageIO.read(in);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static int gold(int argb) {
		int a = argb >>> 24;
		if (a == 0) return 0;
		int r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
		float lum = (0.3f * r + 0.59f * gr + 0.11f * b) / 255f;
		int min = Math.min(r, Math.min(gr, b)), max = Math.max(r, Math.max(gr, b));
		boolean bone = min > 150 && max - min < 60;
		int rr, gg, bb;
		if (bone) { // Knochen: helles Gelbweiß
			rr = 255; gg = 244; bb = 196;
		} else if (lum < 0.16f) { // Umriss: dunkles Goldbraun
			rr = 112; gg = 74; bb = 0;
		} else { // Fleisch: Gold, Schattierung bleibt wie im Original
			float k = Math.min(1.25f, 0.55f + 1.15f * lum);
			rr = Math.min(255, Math.round(255 * k));
			gg = Math.min(255, Math.round(196 * k));
			bb = Math.min(255, Math.round(48 * k));
		}
		return (a << 24) | (rr << 16) | (gg << 8) | bb;
	}

	private static void loadGold() {
		if (goldTried) return;
		goldTried = true;
		BufferedImage full = sprite("food_full"), half = sprite("food_half");
		if (full == null || full.getWidth() != 9 || full.getHeight() != 9) {
			full = fallbackFood();
			half = null;
		}
		BufferedImage gf = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
		BufferedImage gh = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 9; y++) {
			for (int x = 0; x < 9; x++) {
				int c = gold(full.getRGB(x, y));
				gf.setRGB(x, y, c);
				boolean filled;
				if (half != null && half.getWidth() == 9 && half.getHeight() == 9) {
					filled = half.getRGB(x, y) == full.getRGB(x, y); // gefüllte Hälfte sieht aus wie die volle Keule
				} else {
					filled = x >= 4;
				}
				gh.setRGB(x, y, filled ? c : 0);
			}
		}
		goldFull = Textures.image("food_gold_full", "v1", gf);
		goldHalf = Textures.image("food_gold_half", "v1", gh);
	}

	private static BufferedImage fallbackFood() {
		String[] rows = {
				"......ooo", "....ooHMo", "...oHMMMo", "..oHMMMMo", ".oMMMMMo.",
				"oBoMMMMo.", "BBBoooo..", "oBo......", "........."};
		BufferedImage img = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 9; y++) {
			for (int x = 0; x < 9; x++) {
				char c = rows[y].charAt(x);
				int col = switch (c) {
					case 'o' -> 0xFF301800;
					case 'H' -> 0xFFD07040;
					case 'M' -> 0xFF9A4A28;
					case 'B' -> 0xFFF0F0F0;
					default -> 0;
				};
				img.setRGB(x, y, col);
			}
		}
		return img;
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
