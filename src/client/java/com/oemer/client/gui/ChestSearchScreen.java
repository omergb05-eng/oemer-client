package com.oemer.client.gui;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.ChestSearch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Chest Search: alle Minecraft-Items zum Anklicken. Gewählte Items lassen Kisten im Umkreis leuchten. */
public class ChestSearchScreen extends Screen {
	private record Entry(ItemStack stack, String id, String name, String lower) {
	}

	private static List<Entry> ALL;

	private final Screen parent;
	private EditBox search;
	private String filter = "";
	private double scroll;
	private int gridX, gridY, gridW, gridH, cols;
	private static final int CELL = 22;

	public ChestSearchScreen(Screen parent) {
		super(Component.literal("Chest Search"));
		this.parent = parent;
	}

	private static List<Entry> all() {
		if (ALL == null) {
			List<Entry> l = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) continue;
				ItemStack s = new ItemStack(item);
				String name = s.getHoverName().getString();
				l.add(new Entry(s, BuiltInRegistries.ITEM.getKey(item).toString(), name, name.toLowerCase(Locale.ROOT)));
			}
			l.sort(Comparator.comparing(Entry::lower));
			ALL = l;
		}
		return ALL;
	}

	private List<Entry> visible() {
		List<Entry> out = new ArrayList<>();
		for (Entry e : all()) {
			if (filter.isEmpty() || e.lower().contains(filter) || e.id().contains(filter)) out.add(e);
		}
		return out;
	}

	@Override
	protected void init() {
		gridX = 20;
		gridY = 80;
		gridW = this.width - 40;
		gridH = this.height - gridY - 40;
		cols = Math.max(1, gridW / CELL);

		this.addRenderableWidget(new OmerWidgets.Flat(14, 12, 64, 20, "‹ Zurück", this::onClose).style(OmerWidgets.Style.GHOST));
		int bw = Draw.width("Auswahl leeren") + 18;
		this.addRenderableWidget(new OmerWidgets.Flat(this.width - 14 - bw, 46, bw, 20, "Auswahl leeren", () -> ChestSearch.clearSelection())
				.tip("Alle gewählten Items abwählen"));
		search = new EditBox(this.font, 30, 50, Math.max(80, this.width - bw - 80), 12, Component.literal("Suche"));
		search.setBordered(false);
		search.setMaxLength(40);
		search.setHint(Component.literal("Item suchen … (z. B. Diamant)"));
		search.setResponder(v -> {
			filter = v.trim().toLowerCase(Locale.ROOT);
			scroll = 0;
		});
		this.addRenderableWidget(search);
		this.setInitialFocus(search);
	}

	private int rowsCount(int n) {
		return (n + cols - 1) / cols;
	}

	private double maxScroll(int n) {
		return Math.max(0, rowsCount(n) * CELL - (gridH - 8));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll = Math.max(0, Math.min(maxScroll(visible().size()), scroll - v * CELL * 2));
		return true;
	}

	private Entry at(double mx, double my, List<Entry> list) {
		if (mx < gridX || mx >= gridX + cols * CELL || my < gridY || my > gridY + gridH) return null;
		int col = (int) ((mx - gridX) / CELL);
		int row = (int) ((my - gridY - 4 + scroll) / CELL);
		int idx = row * cols + col;
		return row >= 0 && col < cols && idx >= 0 && idx < list.size() ? list.get(idx) : null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		Entry e = at(event.x(), event.y(), visible());
		if (e != null) {
			ChestSearch.toggle(e.id());
			return true;
		}
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Backdrop.render(g, this.width, this.height, this.minecraft.level != null);
		g.pose().pushMatrix();
		g.pose().translate(90, 14);
		g.pose().scale(1.35f);
		Draw.text(g, "Chest Search", 0, 0, Theme.TEXT);
		g.pose().popMatrix();
		Draw.glass(g, gridX - 6, gridY - 6, gridW + 12, gridH + 12, 8, Theme.panel(), Theme.white(0x2A));
		Draw.round(g, 22, 44, Math.max(100, this.width - Draw.width("Auswahl leeren") - 62), 22, 8, Theme.white(0x14));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		List<Entry> list = visible();
		scroll = Math.min(scroll, maxScroll(list.size()));
		OemerConfig c = OemerConfig.get();

		var cam = this.minecraft.player == null ? null : this.minecraft.player.position();
		int chests = cam == null ? 0 : ChestSearch.hits(cam).size();
		Draw.text(g, Draw.fit(c.searchItems.size() + " Item" + (c.searchItems.size() == 1 ? "" : "s") + " gewählt · " + chests
				+ " Kiste" + (chests == 1 ? "" : "n") + " im Umkreis von " + ChestSearch.RADIUS + " Blöcken · "
				+ ChestSearch.knownChests() + " Kisten bekannt", this.width - 40), 20, 70 - 2, Theme.TEXT_SOFT);

		g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
		int y0 = gridY + 4 - (int) scroll;
		Entry hover = null;
		for (int i = 0; i < list.size(); i++) {
			int x = gridX + (i % cols) * CELL;
			int y = y0 + (i / cols) * CELL;
			if (y + CELL < gridY || y > gridY + gridH) continue;
			Entry e = list.get(i);
			boolean sel = c.searchItems.contains(e.id());
			boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL && mouseY >= gridY && mouseY <= gridY + gridH;
			if (over) hover = e;
			if (sel) Draw.round(g, x, y, CELL - 2, CELL - 2, 4, Theme.accent(0x55));
			else if (over) Draw.round(g, x, y, CELL - 2, CELL - 2, 4, Theme.white(0x1C));
			if (sel) Draw.roundBorder(g, x, y, CELL - 2, CELL - 2, 4, Theme.accent(0xCC));
			g.item(e.stack(), x + 2, y + 2);
		}
		g.disableScissor();

		int max = (int) maxScroll(list.size());
		if (max > 0) {
			int barH = Math.max(20, gridH * gridH / (rowsCount(list.size()) * CELL));
			int barY = gridY + (int) ((gridH - barH) * (scroll / max));
			Draw.round(g, gridX + gridW + 2, barY, 3, barH, 1, Theme.white(0x60));
		}
		String hint = hover != null ? hover.name() + (c.searchItems.contains(hover.id()) ? " · gewählt (Klick = abwählen)" : " · Klick = suchen")
				: "Klick = Item suchen · gewählte Kisten leuchten dezent · Mausrad = scrollen";
		Draw.text(g, Draw.fit(hint, this.width - 40), 20, this.height - 22, hover != null ? Theme.TEXT : Theme.TEXT_FAINT);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
