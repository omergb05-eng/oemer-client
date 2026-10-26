package com.oemer.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
	static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		Draw.round(g, x, y, w, h, 3, fill);
		Draw.roundBorder(g, x, y, w, h, 3, border);
	}

	static Font font() {
		return Draw.font();
	}

	static void centered(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
		Draw.centered(g, text, centerX, y, color);
	}

	/** Wenn ein Screen verkleinert zeichnet: Faktor für Tooltip-Positionen (echte Pixel). */
	public static float tooltipScale = 1f;

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
		protected final void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
			float d = delta();
			hover = Draw.approach(hover, this.isHovered() && this.active ? 1f : 0f, 14f, d);
			draw(g, mouseX, mouseY, d);
			if (tooltip != null && this.isHovered()) {
				g.setTooltipForNextFrame(Component.literal(tooltip), Math.round(mouseX * tooltipScale), Math.round(mouseY * tooltipScale));
			}
		}

		protected abstract void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta);

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
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
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
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
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
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
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
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
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
	// ---------------------------------------------------------------------

	/** Bild-Karte (Kleiderschrank, Vorlagen): großes Bild, Name, ausgerüstet = Akzent-Rahmen. */
	public static class Tile extends Base {
		private final Supplier<com.oemer.client.feature.Textures.Tex> image;
		private final BooleanSupplier selected;
		private final Runnable action;
		private String badge = "";
		private boolean wide;
		private Runnable onDelete;
		private double clickX = -1, clickY = -1;

		public Tile(int x, int y, int w, int h, String label, Supplier<com.oemer.client.feature.Textures.Tex> image,
					BooleanSupplier selected, Runnable action) {
			super(x, y, w, h, label);
			this.image = image;
			this.selected = selected;
			this.action = action;
		}

		/** Kleines Schild oben links, z. B. "Eigenes". */
		public Tile badge(String text) {
			this.badge = text;
			return this;
		}

		/** Kleines ✕ oben rechts: löscht das Bild (die Frage „Wirklich löschen?“ stellt der Aufrufer). */
		public Tile deletable(Runnable onDelete) {
			this.onDelete = onDelete;
			return this;
		}

		/** Bild füllt die Fläche (für Hintergründe), statt reinzupassen. */
		public Tile cover() {
			this.wide = true;
			return this;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = width, h = height;
			boolean sel = selected.getAsBoolean();
			int lift = Math.round(hover * 2);
			y -= lift;
			Draw.shadow(g, x, y, w, h, 6, Math.round(50 + 40 * hover));
			Draw.round(g, x, y, w, h, 6, sel ? Theme.accent(0x30) : Theme.white(Math.round(0x0E + 0x0C * hover)));
			// Bildfläche
			int ih = h - 18;
			Draw.round(g, x + 3, y + 3, w - 6, ih - 3, 4, 0x50000000);
			var tex = image.get();
			if (tex != null) {
				if (wide) {
					com.oemer.client.feature.Textures.drawCover(g, tex, x + 3, y + 3, w - 6, ih - 3);
				} else {
					int pad = 5;
					com.oemer.client.feature.Textures.drawContain(g, tex, x + pad, y + pad, w - pad * 2, ih - pad - 2);
				}
			} else {
				// wird noch gemalt → kleine Ladepunkte
				int dots = (int) ((System.currentTimeMillis() / 250) % 4);
				Draw.centered(g, ".".repeat(dots), x + w / 2, y + ih / 2 - 4, Theme.TEXT_DIM);
			}
			String label = Draw.fit(getMessage().getString(), w - 8);
			Draw.centered(g, label, x + w / 2, y + h - 13, sel ? Theme.TEXT : Draw.mix(Theme.TEXT_SOFT, Theme.TEXT, hover));
			if (!badge.isEmpty()) {
				int bw = Draw.width(badge) + 8;
				Draw.round(g, x + 5, y + 5, bw, 11, 5, 0xC0000000);
				Draw.text(g, badge, x + 9, y + 7, Theme.TEXT_SOFT);
			}
			if (onDelete != null) {
				boolean over = mouseX >= x + w - 19 && mouseX < x + w - 3 && mouseY >= y + 3 && mouseY < y + 19;
				Draw.round(g, x + w - 17, y + 5, 12, 12, 6, over ? 0xF0D83A3A : 0xB0000000);
				Draw.text(g, "✕", x + w - 14, y + 7, over ? 0xFFFFFFFF : Theme.TEXT_SOFT);
			}
			if (sel) {
				Draw.roundBorder(g, x, y, w, h, 6, Theme.accent(0xF0));
				int kx = onDelete != null ? x + w - 31 : x + w - 17;
				Draw.round(g, kx, y + 5, 12, 12, 6, Theme.accent(0xF0));
				int knob = (Theme.accent() & 0xFFFFFF) == 0xFFFFFF ? 0xFF0B0B0F : 0xFFFFFFFF;
				Draw.text(g, "✔", kx + 3, y + 7, knob);
			} else {
				Draw.roundBorder(g, x, y, w, h, 6, Theme.border(hover));
			}
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			clickX = event.x();
			clickY = event.y();
			return super.mouseClicked(event, doubleClick);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			boolean corner = onDelete != null && clickX >= getX() + width - 20 && clickY <= getY() + 20;
			clickX = -1;
			clickY = -1;
			if (corner) onDelete.run();
			else action.run();
		}
	}

	// ---------------------------------------------------------------------

	/**
	 * Mod-Kachel: Symbol, Name, kurzer Text, rechts ein Schalter.
	 * Klick auf den Schalter = an/aus, Klick auf die Kachel = Einstellungen öffnen.
	 */
	public static class ModCard extends Base {
		private final String icon;
		private final String desc;
		private final BooleanSupplier on;
		private final Consumer<Boolean> set;
		private final Runnable open;
		private float anim;
		private double clickX = -1;

		public ModCard(int x, int y, int w, int h, String icon, String label, String desc,
					   BooleanSupplier on, Consumer<Boolean> set, Runnable open) {
			super(x, y, w, h, label);
			this.icon = icon;
			this.desc = desc;
			this.on = on;
			this.set = set;
			this.open = open;
			this.anim = on != null && on.getAsBoolean() ? 1f : 0f;
		}

		private int switchX() {
			return getX() + width - 34;
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			clickX = event.x();
			return super.mouseClicked(event, doubleClick);
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = width, h = height;
			boolean isOn = on != null && on.getAsBoolean();
			anim = Draw.approach(anim, isOn ? 1f : 0f, 16f, delta);
			Draw.round(g, x, y, w, h, 6, Theme.element(hover));
			Draw.roundBorder(g, x, y, w, h, 6, Draw.mix(Theme.border(hover), Theme.accent(0x80), anim * 0.7f));
			// Symbol im runden Feld
			int is = h - 14;
			Draw.round(g, x + 7, y + 7, is, is, 5, Draw.mix(Theme.white(0x16), Theme.accent(0xE0), anim));
			int iconColor = anim > 0.5f && (Theme.accent() & 0xFFFFFF) == 0xFFFFFF ? 0xFF0B0B0F : 0xFFFFFFFF;
			g.pose().pushMatrix();
			g.pose().translate(x + 7 + is / 2f, y + 7 + is / 2f - 5f);
			g.pose().scale(1.4f);
			Draw.centered(g, icon, 0, 0, iconColor);
			g.pose().popMatrix();
			int tx = x + 13 + is;
			int maxW = (on != null ? switchX() - 6 : x + w - 18) - tx;
			Draw.text(g, Draw.fit(getMessage().getString(), maxW), tx, y + h / 2 - 9, Theme.TEXT);
			Draw.text(g, Draw.fit(desc, maxW), tx, y + h / 2 + 2, Theme.TEXT_DIM);
			if (on != null) {
				int tw = 24, th = 12;
				int sx = switchX(), sy = y + (h - th) / 2;
				Draw.round(g, sx, sy, tw, th, th / 2, Draw.mix(Theme.white(0x26), Theme.accent(0xF0), anim));
				int k = th - 4;
				int kx = sx + 2 + Math.round(anim * (tw - k - 4));
				int knobOn = (Theme.accent() & 0xFFFFFF) == 0xFFFFFF ? 0xFF0B0B0F : 0xFFFFFFFF;
				Draw.round(g, kx, sy + 2, k, k, k / 2, Draw.mix(0xFFFFFFFF, knobOn, anim));
			} else {
				Draw.text(g, "›", x + w - 14, y + h / 2 - 4, Draw.mix(Theme.TEXT_DIM, Theme.TEXT, hover));
			}
		}

		@Override
		public void onPress(InputWithModifiers input) {
			boolean onSwitch = on != null && clickX >= switchX() - 4;
			clickX = -1;
			if (onSwitch) set.accept(!on.getAsBoolean());
			else open.run();
		}
	}

	// ---------------------------------------------------------------------

	/** Zeile für eine Taste: Name links, Taste rechts. Klick → nächste Taste drücken. */
	public static class KeyRow extends Base {
		private final Supplier<String> key;
		private final BooleanSupplier listening;
		private final BooleanSupplier conflict;
		private final Runnable action;
		private final String hint;

		public KeyRow(int x, int y, int w, int h, String label, String hint, Supplier<String> key,
					  BooleanSupplier listening, BooleanSupplier conflict, Runnable action) {
			super(x, y, w, h, label);
			this.hint = hint;
			this.key = key;
			this.listening = listening;
			this.conflict = conflict;
			this.action = action;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = width, h = height;
			boolean lis = listening.getAsBoolean();
			Draw.round(g, x, y, w, h, 4, Theme.element(hover));
			Draw.roundBorder(g, x, y, w, h, 4, lis ? Theme.accent(0xD0) : Theme.border(hover));
			int textY = y + (h - 8) / 2 + 1;
			String value = lis ? "> Taste drücken <" : key.get();
			int vw = Math.max(44, Draw.width(value) + 16);
			int vx = x + w - vw - 5;
			String label = Draw.fit(getMessage().getString(), vx - x - 14);
			Draw.text(g, label, x + 10, hint.isEmpty() ? textY : y + 4, Draw.mix(Theme.TEXT_SOFT, Theme.TEXT, hover));
			if (!hint.isEmpty()) Draw.text(g, Draw.fit(hint, vx - x - 14), x + 10, y + h - 11, Theme.TEXT_FAINT);
			int vy = y + 4, vh = h - 8;
			boolean bad = !lis && conflict.getAsBoolean();
			Draw.round(g, vx, vy, vw, vh, 4, lis ? Theme.accent(0xE0) : Theme.white(Math.round(0x14 + 0x10 * hover)));
			Draw.roundBorder(g, vx, vy, vw, vh, 4, bad ? 0xFFE5484D : Theme.white(0x30));
			int col = lis ? ((Theme.accent() & 0xFFFFFF) == 0xFFFFFF ? 0xFF0B0B0F : 0xFFFFFFFF) : bad ? 0xFFFF8A8E : Theme.TEXT;
			Draw.centered(g, value, vx + vw / 2, textY, col);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}
	}
	// ---------------------------------------------------------------------

	/** Überschrift / Text-Zeile (nicht klickbar). */
	public static class Label extends Base {
		private final boolean big;
		private final int color;

		public Label(int x, int y, int w, int h, String text, boolean big, int color) {
			super(x, y, w, h, text);
			this.big = big;
			this.color = color;
			this.active = false;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			String t = getMessage().getString();
			if (big) {
				g.pose().pushMatrix();
				g.pose().translate(getX(), getY() + height - 12);
				g.pose().scale(1.15f);
				Draw.text(g, Draw.fit(t, Math.round(width / 1.15f)), 0, 0, color);
				g.pose().popMatrix();
				g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, Theme.white(0x14));
			} else {
				Draw.text(g, Draw.fit(t, width), getX(), getY() + (height - 8) / 2, color);
			}
		}

		@Override
		public void onPress(InputWithModifiers input) {
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			return false;
		}
	}

	/** Fläche mit eigenem Zeichnen (Vorschau, Now Playing …). Optional klickbar. */
	public static class Painter extends Base {
		public interface Paint {
			void paint(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY);
		}

		private final Paint paint;
		private final Runnable action;

		public Painter(int x, int y, int w, int h, Paint paint, Runnable action) {
			super(x, y, w, h, "");
			this.paint = paint;
			this.action = action;
			this.active = action != null;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			paint.paint(g, getX(), getY(), width, height, mouseX, mouseY);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (action != null) action.run();
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			return action != null && super.mouseClicked(event, doubleClick);
		}
	}
}
