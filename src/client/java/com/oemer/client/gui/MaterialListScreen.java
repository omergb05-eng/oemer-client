package com.oemer.client.gui;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.CapeManager;
import com.oemer.client.feature.MaterialList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Die Materialliste zum Abhaken (wie bei Litematica). */
public class MaterialListScreen extends Screen {
	private final Screen parent;
	private static boolean onlyMissing;
	private double scroll;
	private int listX, listY, listW, listH;
	private static final int ROW = 22;

	public MaterialListScreen(Screen parent) {
		super(Component.literal("Material List"));
		this.parent = parent;
	}

	private List<MaterialList.Entry> rows() {
		List<MaterialList.Entry> out = new ArrayList<>();
		for (MaterialList.Entry e : MaterialList.entries()) {
			if (onlyMissing && (MaterialList.isDone(e) || MaterialList.have(e.item()) >= e.needed())) continue;
			out.add(e);
		}
		return out;
	}

	@Override
	protected void init() {
		listX = 20;
		listY = 74;
		listW = this.width - 40;
		listH = this.height - listY - 40;

		this.addRenderableWidget(new OmerWidgets.Flat(14, 12, 64, 20, "‹ Zurück", this::onClose).style(OmerWidgets.Style.GHOST));
		int bw = 110;
		int x = this.width - 14 - bw;
		this.addRenderableWidget(new OmerWidgets.Flat(x, 46, bw, 18, "Neu laden", () -> {
			MaterialList.reload();
			scroll = 0;
		}));
		x -= bw + 6;
		this.addRenderableWidget(new OmerWidgets.Flat(x, 46, bw, 18, "Haken entfernen", () -> {
			OemerConfig.get().materialDone.clear();
			OemerConfig.save();
		}));
		x -= bw + 6;
		this.addRenderableWidget(new OmerWidgets.Toggle(x - 30, 46, bw + 30, 18, "Nur fehlende", () -> onlyMissing, v -> {
			onlyMissing = v;
			scroll = 0;
		}, false));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - v * ROW * 2));
		return true;
	}

	private int maxScroll() {
		return Math.max(0, rows().size() * ROW - (listH - 8));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		double mx = event.x(), my = event.y();
		if (mx < listX || mx > listX + listW || my < listY || my > listY + listH) return false;
		int idx = (int) ((my - listY - 4 + scroll) / ROW);
		List<MaterialList.Entry> rows = rows();
		if (idx >= 0 && idx < rows.size()) {
			MaterialList.toggleDone(rows.get(idx));
			return true;
		}
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Backdrop.render(g, this.width, this.height, this.minecraft.level != null);
		g.pose().pushMatrix();
		g.pose().translate(90, 14);
		g.pose().scale(1.35f);
		Draw.text(g, "Material List", 0, 0, Theme.TEXT);
		g.pose().popMatrix();
		String file = OemerConfig.get().schematic;
		Draw.text(g, file.isEmpty() ? "keine Schematic gewählt" : CapeManager.shortName(file),
				90 + Math.round(Draw.width("Material List") * 1.35f) + 10, 17, Theme.TEXT_DIM);
		Draw.glass(g, listX - 6, listY - 6, listW + 12, listH + 12, 8, Theme.panel(), Theme.white(0x2A));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);

		List<MaterialList.Entry> rows = rows();
		scroll = Math.min(scroll, maxScroll());

		// Zusammenfassung
		int total = 0, missing = 0;
		for (MaterialList.Entry e : MaterialList.entries()) {
			total += e.needed();
			if (!MaterialList.isDone(e)) missing += Math.max(0, e.needed() - MaterialList.have(e.item()));
		}
		Draw.text(g, MaterialList.entries().size() + " Blöcke · " + total + " insgesamt · " + missing + " fehlen noch",
				listX, 51, Theme.TEXT_SOFT);

		if (!MaterialList.error().isEmpty()) {
			Draw.centered(g, MaterialList.error(), this.width / 2, listY + 30, Theme.BAD);
			return;
		}
		if (rows.isEmpty()) {
			Draw.centered(g, file().isEmpty() ? "Leg eine .litematic oder .schem in den Ordner schematics/"
					: "Nichts mehr zu tun ✔", this.width / 2, listY + 30, Theme.TEXT_DIM);
			return;
		}

		int nameW = listW - 260;
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		int y0 = listY + 4 - (int) scroll;
		for (int i = 0; i < rows.size(); i++) {
			int y = y0 + i * ROW;
			if (y + ROW < listY || y > listY + listH) continue;
			MaterialList.Entry e = rows.get(i);
			boolean done = MaterialList.isDone(e);
			int have = MaterialList.have(e.item());
			boolean enough = have >= e.needed();
			boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + ROW
					&& mouseY >= listY && mouseY <= listY + listH;

			if (i % 2 == 0) Draw.round(g, listX, y, listW, ROW - 2, 4, Theme.white(0x08));
			if (hover) Draw.round(g, listX, y, listW, ROW - 2, 4, Theme.white(0x14));

			g.renderItem(e.icon(), listX + 6, y + 2);
			int textColor = done ? Theme.TEXT_FAINT : Theme.TEXT;
			Draw.text(g, Draw.fit(e.name(), nameW), listX + 28, y + 7, textColor);
			String need = MaterialList.amount(e.needed(), e.icon().getMaxStackSize());
			Draw.text(g, need, listX + listW - 250, y + 7, done ? Theme.TEXT_FAINT : Theme.TEXT_SOFT);
			String hv = "hast " + have;
			Draw.text(g, hv, listX + listW - 120, y + 7, done ? Theme.TEXT_FAINT : (enough ? Theme.GOOD : Theme.TEXT_DIM));

			// Haken
			int bx = listX + listW - 24, by = y + 4;
			Draw.round(g, bx, by, 14, 14, 4, done ? Theme.accent() : Theme.white(0x22));
			if (done) Draw.text(g, "✔", bx + 3, by + 3, (Theme.accent() & 0xFFFFFF) == 0xFFFFFF ? 0xFF0B0B0F : 0xFFFFFFFF);
		}
		g.disableScissor();

		// Scrollbalken
		int max = maxScroll();
		if (max > 0) {
			int barH = Math.max(20, listH * listH / (rows.size() * ROW));
			int barY = listY + (int) ((listH - barH) * (scroll / max));
			Draw.round(g, listX + listW + 2, barY, 3, barH, 1, Theme.white(0x60));
		}
		Draw.text(g, "Klick = abhaken · Mausrad = scrollen", listX, this.height - 22, Theme.TEXT_FAINT);
	}

	private static String file() {
		return OemerConfig.get().schematic;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
