package com.oemer.client.gui;

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
import java.util.function.Supplier;

/** Alle eigenen Buttons im Ömer-Client-Design. */
public final class OmerWidgets {
	private OmerWidgets() {
	}

	/** Alte Hilfsfunktion (eckiger Rahmen), wird noch an ein paar Stellen benutzt. */
	static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
		Draw.round(g, x, y, w, h, 3, fill);
		Draw.roundBorder(g, x, y, w, h, 3, border);
	}

	static Font font() {
		return Draw.font();
	}

	static void centered(GuiGraphics g, String text, int centerX, int y, int color) {
		Draw.centered(g, text, centerX, y, color);
	}

	/** Gemeinsame Basis: weiche Hover-Animation und optionaler Tooltip. */
	public abstract static class Base extends AbstractButton {
		protected float hover;
		private long lastNanos = System.nanoTime();
		private String tooltip;

		protected Base(int x, int y, int w, int h, String label) {
			super(x, y, w, h, Component.literal(label));
		}

		/** Kleiner Erklärtext, der beim Drüberfahren erscheint. */
		public Base tip(String text) {
			this.tooltip = text;
			return this;
		}

		protected float delta() {
			long now = System.nanoTime();
			float d = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
			lastNanos = now;
			return d;
		}

		@Override
		protected final void renderContents(GuiGraphics g, int mouseX, int mouseY, float partial) {
			float d = delta();
			hover = Draw.approach(hover, this.isHovered() && this.active ? 1f : 0f, 14f, d);
			draw(g, mouseX, mouseY, d);
			if (tooltip != null && this.isHovered()) {
				g.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY);
			}
		}

		protected abstract void draw(GuiGraphics g, int mouseX, int mouseY, float delta);

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	// ---------------------------------------------------------------------

	/** Stil für {@link Flat}. */
	public enum Style {
		/** Glas-Button. */
		GLASS,
		/** Weißer, gefüllter Button (Hauptaktion). */
		PRIMARY,
		/** Kleiner runder Reiter (für Unter-Reiter). */
		PILL,
		/** Nur Text, ohne Fläche. */
		GHOST,
		/** Haupt-Reiter: Hintergrund malt der Screen (gleitende Markierung). */
		TAB
	}

	/** Normaler Klick-Button, z. B. "Ömer Client" im Hauptmenü, Tabs, "Speichern". */
	public static class Flat extends Base {
		private final Runnable action;
		private final BooleanSupplier selected;
		private Style style = Style.GLASS;

		public Flat(int x, int y, int w, int h, String label, Runnable action) {
			this(x, y, w, h, label, action, () -> false);
		}

		public Flat(int x, int y, int w, int h, String label, Runnable action, BooleanSupplier selected) {
			super(x, y, w, h, label);
			this.action = action;
			this.selected = selected;
		}

		public Flat style(Style style) {
			this.style = style;
			return this;
		}

		@Override
		protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean sel = this.selected.getAsBoolean();
			int x = getX(), y = getY(), w = width, h = height;
			int r = style == Style.PILL ? h / 2 : 4;
			String label = Draw.fit(getMessage().getString(), w - 8);
			int textY = y + (h - 8) / 2 + 1;
			int textColor;

			switch (style) {
				case PRIMARY -> {
					Draw.shadow(g, x, y, w, h, r, 60);
					Draw.round(g, x, y, w, h, r, Draw.mix(0xFFEDEDF2, 0xFFFFFFFF, hover));
					textColor = 0xFF0B0B0F;
				}
				case TAB -> textColor = sel ? 0xFF0B0B0F : Draw.mix(Theme.TEXT_DIM, Theme.TEXT, hover);
				case GHOST -> {
					if (hover > 0.01f) Draw.round(g, x, y, w, h, r, Theme.white(Math.round(0x14 * hover)));
					textColor = Draw.mix(Theme.TEXT_DIM, Theme.TEXT, hover);
				}
				default -> {
					if (sel) {
						// ausgewählt = clean weiß gefüllt, dunkle Schrift
						Draw.round(g, x, y, w, h, r, 0xF2FFFFFF);
						textColor = 0xFF0B0B0F;
					} else {
						Draw.round(g, x, y, w, h, r, Theme.element(hover));
						Draw.roundBorder(g, x, y, w, h, r, Theme.border(hover * 0.8f));
						textColor = Draw.mix(Theme.TEXT_SOFT, Theme.TEXT, hover);
					}
				}
			}
			if (!this.active) textColor = Theme.TEXT_FAINT;
			Draw.centered(g, label, x + w / 2, textY, textColor);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}
	}

	// ---------------------------------------------------------------------

	/** An/Aus-Schalter: Text links, Schiebeschalter rechts. */
	public static class Toggle extends Base {
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;
		private final boolean soon;
		private float anim;

		/** @param soon true = Funktion kommt später, zeigt "bald" an. */
		public Toggle(int x, int y, int w, int h, String label, BooleanSupplier getter, Consumer<Boolean> setter, boolean soon) {
			super(x, y, w, h, label);
			this.getter = getter;
			this.setter = setter;
			this.soon = soon;
			this.anim = getter.getAsBoolean() ? 1f : 0f;
		}

		@Override
		protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
			boolean on = getter.getAsBoolean();
			anim = Draw.approach(anim, on ? 1f : 0f, 16f, delta);
			int x = getX(), y = getY(), w = width, h = height;

			Draw.round(g, x, y, w, h, 4, Theme.element(hover));
			Draw.roundBorder(g, x, y, w, h, 4, Draw.mix(Theme.border(hover), Theme.accent(0x70), anim * 0.6f));
			// kleiner Akzent-Strich links, wenn an
			if (anim > 0.02f) {
				int bh = Math.round((h - 10) * anim);
				Draw.round(g, x + 3, y + (h - bh) / 2, 2, Math.max(2, bh), 1, Theme.accent(Math.round(0xE0 * anim)));
			}

			int textY = y + (h - 8) / 2 + 1;
			String label = getMessage().getString();
			int maxLabel = w - 52;
			String shown = Draw.fit(label, maxLabel);
			Draw.text(g, shown, x + 10, textY, Draw.mix(Theme.TEXT_SOFT, Theme.TEXT, Math.max(hover, anim)));
			if (soon) {
				Draw.text(g, "bald", x + 14 + Draw.width(shown), textY, Theme.TEXT_FAINT);
			}

			// Schiebeschalter (Pille)
			int tw = 24, th = 12;
			int tx = x + w - tw - 8;
			int ty = y + (h - th) / 2;
			int trackOff = Theme.white(0x26);
			int trackOn = Theme.accent(0xF0);
			Draw.round(g, tx, ty, tw, th, th / 2, Draw.mix(trackOff, trackOn, anim));
			int knobSize = th - 4;
			int kx = tx + 2 + Math.round(anim * (tw - knobSize - 4));
			int knobOn = (Theme.accent() & 0xFFFFFF) == 0xFFFFFF ? 0xFF0B0B0F : 0xFFFFFFFF;
			Draw.round(g, kx, ty + 2, knobSize, knobSize, knobSize / 2, Draw.mix(0xFFFFFFFF, knobOn, anim));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			setter.accept(!getter.getAsBoolean());
		}
	}

	// ---------------------------------------------------------------------

	/** Auswahl, die beim Klicken weiterspringt (z. B. Kit Select). */
	public static class Cycle extends Base {
		private final Supplier<String[]> options;
		private final IntSupplier getter;
		private final IntConsumer setter;

		public Cycle(int x, int y, int w, int h, String label, String[] options, IntSupplier getter, IntConsumer setter) {
			this(x, y, w, h, label, () -> options, getter, setter);
		}

		public Cycle(int x, int y, int w, int h, String label, Supplier<String[]> options, IntSupplier getter, IntConsumer setter) {
			super(x, y, w, h, label);
			this.options = options;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = width, h = height;
			Draw.round(g, x, y, w, h, 4, Theme.element(hover));
			Draw.roundBorder(g, x, y, w, h, 4, Theme.border(hover));

			int textY = y + (h - 8) / 2 + 1;
			String label = getMessage().getString();
			String[] opts = options.get();
			String value = opts.length == 0 ? "-" : opts[Math.floorMod(getter.getAsInt(), opts.length)];
			int valueMax = Math.max(30, w - Draw.width(label) - 40);
			value = Draw.fit(value, valueMax);

			Draw.text(g, label, x + 10, textY, Draw.mix(Theme.TEXT_SOFT, Theme.TEXT, hover));
			// Wert in einer kleinen Pille
			int vw = Draw.width(value) + 18;
			int vx = x + w - vw - 6;
			int vy = y + 4;
			int vh = h - 8;
			Draw.round(g, vx, vy, vw, vh, vh / 2, Theme.white(Math.round(0x10 + 0x10 * hover)));
			Draw.text(g, value, vx + 9, textY, Theme.accent());
			Draw.text(g, "›", vx + vw - 7, textY, Draw.alpha(Theme.TEXT_DIM, Math.round(0x80 + 0x7F * hover)));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			String[] opts = options.get();
			if (opts.length > 0) {
				setter.accept(Math.floorMod(getter.getAsInt() + 1, opts.length));
			}
		}
	}

	// ---------------------------------------------------------------------

	/** Farbfeld (für Paletten). */
	public static class Swatch extends Base {
		private final int color;
		private final BooleanSupplier selected;
		private final Runnable action;

		public Swatch(int x, int y, int size, int color, BooleanSupplier selected, Runnable action) {
			super(x, y, size, size, "");
			this.color = color;
			this.selected = selected;
			this.action = action;
		}

		@Override
		protected void draw(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), s = width;
			boolean sel = selected.getAsBoolean();
			if (sel) {
				Draw.round(g, x - 2, y - 2, s + 4, s + 4, 4, 0xFFFFFFFF);
			} else if (hover > 0.01f) {
				Draw.round(g, x - 1, y - 1, s + 2, s + 2, 4, Theme.white(Math.round(0x90 * hover)));
			}
			if ((color >>> 24) == 0) {
				// durchsichtig = Schachbrett
				for (int i = 0; i < 4; i++) {
					int cx = x + (i % 2) * s / 2, cy = y + (i / 2) * s / 2;
					g.fill(cx, cy, cx + s / 2, cy + s / 2, (i == 0 || i == 3) ? 0xFF5A5A5A : 0xFF8A8A8A);
				}
			} else {
				Draw.round(g, x, y, s, s, 3, color | 0xFF000000);
			}
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}
	}
}
