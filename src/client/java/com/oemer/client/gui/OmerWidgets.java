package com.oemer.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Alle eigenen Buttons im Ömer-Client-Design. */
public final class OmerWidgets {
	private OmerWidgets() {
	}

	/** Zeichnet die Glas-Fläche mit 1px Rand. */
	static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w, y + 1, border);
		g.fill(x, y + h - 1, x + w, y + h, border);
		g.fill(x, y + 1, x + 1, y + h - 1, border);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
	}

	static Font font() {
		return Minecraft.getInstance().font;
	}

	static void centered(GuiGraphics g, String text, int centerX, int y, int color) {
		Font font = font();
		g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
	}

	// ---------------------------------------------------------------------

	/** Normaler Klick-Button, z. B. "Ömer Client" im Hauptmenü oder die Tabs. */
	public static class Flat extends AbstractButton {
		private final Runnable action;
		private final BooleanSupplier selected;

		public Flat(int x, int y, int w, int h, String label, Runnable action) {
			this(x, y, w, h, label, action, () -> false);
		}

		public Flat(int x, int y, int w, int h, String label, Runnable action, BooleanSupplier selected) {
			super(x, y, w, h, Component.literal(label));
			this.action = action;
			this.selected = selected;
		}

		@Override
		protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean hover = this.isHovered();
			boolean sel = this.selected.getAsBoolean();
			int fill = sel ? Theme.white(0x46) : Theme.element(hover);
			int border = sel ? Theme.accent(0xD0) : Theme.border(hover);
			frame(g, getX(), getY(), width, height, fill, border);
			if (sel) {
				// kleine Linie unter dem aktiven Tab
				g.fill(getX() + 4, getY() + height - 2, getX() + width - 4, getY() + height - 1, Theme.accent());
			}
			int color = (sel || hover) ? Theme.TEXT : 0xFFDADADA;
			centered(g, getMessage().getString(), getX() + width / 2, getY() + (height - 8) / 2, color);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	// ---------------------------------------------------------------------

	/** An/Aus-Schalter mit Text links und Schiebeschalter rechts. */
	public static class Toggle extends AbstractButton {
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;
		private final boolean soon;
		private float anim;

		/**
		 * @param soon true = Funktion kommt in einer späteren Version, zeigt "bald" an.
		 */
		public Toggle(int x, int y, int w, int h, String label, BooleanSupplier getter, Consumer<Boolean> setter, boolean soon) {
			super(x, y, w, h, Component.literal(label));
			this.getter = getter;
			this.setter = setter;
			this.soon = soon;
			this.anim = getter.getAsBoolean() ? 1f : 0f;
		}

		@Override
		protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean hover = this.isHovered();
			boolean on = getter.getAsBoolean();
			anim += ((on ? 1f : 0f) - anim) * 0.35f;

			frame(g, getX(), getY(), width, height, Theme.element(hover), Theme.border(hover));

			Font font = font();
			int textY = getY() + (height - 8) / 2;
			String label = getMessage().getString();
			g.drawString(font, label, getX() + 8, textY, Theme.TEXT, false);
			if (soon) {
				g.drawString(font, "bald", getX() + 12 + font.width(label), textY, Theme.TEXT_FAINT, false);
			}

			// Schiebeschalter
			int tw = 22;
			int th = 10;
			int tx = getX() + width - tw - 8;
			int ty = getY() + (height - th) / 2;
			int track = on ? Theme.accent(0xB0) : Theme.white(0x30);
			frame(g, tx, ty, tw, th, track, on ? Theme.accent(0xFF) : Theme.white(0x60));
			int knob = tx + 1 + Math.round(anim * (tw - th));
			// An = dunkler Knopf auf hellem Grund, Aus = weißer Knopf
			g.fill(knob, ty + 1, knob + th - 2, ty + th - 1, on ? 0xFF111111 : 0xFFFFFFFF);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			setter.accept(!getter.getAsBoolean());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	// ---------------------------------------------------------------------

	/** Auswahl, die beim Klicken weiterspringt (z. B. Kit Select). */
	public static class Cycle extends AbstractButton {
		private final String[] options;
		private final IntSupplier getter;
		private final IntConsumer setter;

		public Cycle(int x, int y, int w, int h, String label, String[] options, IntSupplier getter, IntConsumer setter) {
			super(x, y, w, h, Component.literal(label));
			this.options = options;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean hover = this.isHovered();
			frame(g, getX(), getY(), width, height, Theme.element(hover), Theme.border(hover));

			Font font = font();
			int textY = getY() + (height - 8) / 2;
			g.drawString(font, getMessage().getString(), getX() + 8, textY, Theme.TEXT, false);

			String value = "< " + options[Math.floorMod(getter.getAsInt(), options.length)] + " >";
			g.drawString(font, value, getX() + width - 8 - font.width(value), textY, Theme.accent(), false);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			setter.accept(Math.floorMod(getter.getAsInt() + 1, options.length));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
