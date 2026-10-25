package com.oemer.client.mixin;

import com.oemer.client.feature.NameStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
	// Own Custom Name: dein Name (Namensschild) in deiner Farbe / Verlauf / Rainbow
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
	private void oemer$name(CallbackInfoReturnable<Component> cir) {
		if (NameStyle.active() && (Object) this == Minecraft.getInstance().player) {
			cir.setReturnValue(NameStyle.display());
		}
	}
}
