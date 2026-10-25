package com.oemer.client.mixin;

import com.oemer.client.feature.ShulkerPreview;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Shulker Box Tooltip: Inhalt als Kästchen-Raster im Tooltip. */
@Mixin(Item.class)
public class ItemMixin {
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true, require = 0)
	private void oemer$shulker(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (cir.getReturnValue().isPresent()) return;
		TooltipComponent t = ShulkerPreview.of(stack);
		if (t != null) cir.setReturnValue(Optional.of(t));
	}
}
