package com.oemer.client.mixin;

import com.oemer.client.feature.NameShare;
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
		Player self = (Player) (Object) this;
		if (NameStyle.active() && self == Minecraft.getInstance().player) {
			cir.setReturnValue(NameStyle.display());
		} else if (self != Minecraft.getInstance().player) {
			// Freunde mit dem Ömer Client: ihr Namensstil
			NameShare.Style st = NameShare.styleOf(self.getUUID());
			if (st != null) cir.setReturnValue(NameShare.styled(st, self.getGameProfile().name()));
		}
	}
}
