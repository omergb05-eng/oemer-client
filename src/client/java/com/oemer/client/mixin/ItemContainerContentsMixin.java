package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mit Shulker Box Tooltip die Text-Liste weglassen (das Raster zeigt alles). */
@Mixin(ItemContainerContents.class)
public class ItemContainerContentsMixin {
	@Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$hideText(CallbackInfo ci) {
		if (OemerConfig.get().shulkerTooltip) ci.cancel();
	}
}
