package com.oemer.client.feature;

import com.oemer.client.OemerConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Shulker Box Tooltip: zeigt den Inhalt als 9x3-Raster mit Items. */
public final class ShulkerPreview {
	private ShulkerPreview() {
	}

	public record Data(NonNullList<ItemStack> items) implements TooltipComponent {
	}

	/** Tooltip-Daten für Shulker-Kisten (und andere Items mit Inhalt), sonst null. */
	public static TooltipComponent of(ItemStack stack) {
		if (!OemerConfig.get().shulkerTooltip) return null;
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null || contents.equals(ItemContainerContents.EMPTY)) return null;
		NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
		contents.copyInto(list);
		boolean any = false;
		for (ItemStack s : list) any |= !s.isEmpty();
		return any ? new Data(list) : null;
	}

	/** Zeichnet das Raster im Tooltip. */
	public static class Client implements ClientTooltipComponent {
		private final NonNullList<ItemStack> items;

		public Client(Data data) {
			this.items = data.items();
		}

		private int rows() {
			return Math.max(1, (items.size() + 8) / 9);
		}

		@Override
		public int getHeight(Font font) {
			return rows() * 18 + 6;
		}

		@Override
		public int getWidth(Font font) {
			return 9 * 18 + 2;
		}

		@Override
		public void renderImage(Font font, int x, int y, int w, int h, GuiGraphics g) {
			for (int i = 0; i < items.size(); i++) {
				int sx = x + 1 + (i % 9) * 18, sy = y + 2 + (i / 9) * 18;
				g.fill(sx, sy, sx + 17, sy + 17, 0x40000000);
				g.fill(sx, sy, sx + 17, sy + 1, 0x30FFFFFF);
				ItemStack s = items.get(i);
				if (s.isEmpty()) continue;
				g.renderItem(s, sx, sy);
				g.renderItemDecorations(font, s, sx, sy);
			}
		}
	}
}
