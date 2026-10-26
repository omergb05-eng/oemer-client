package com.oemer.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.oemer.client.feature.ChestSearch;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chest Search: Tippst du im Suchfeld (z. B. ein "e"), darf das die Kiste nicht schließen. */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$searchTyping(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (event.key() != InputConstants.KEY_ESCAPE && ChestSearch.typing()) {
			ChestSearch.boxKey(event);
			cir.setReturnValue(true);
		}
	}
}
