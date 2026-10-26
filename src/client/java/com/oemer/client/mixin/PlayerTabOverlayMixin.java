package com.oemer.client.mixin;

import com.oemer.client.feature.NameShare;
import com.oemer.client.feature.NameStyle;
import com.oemer.client.feature.OmerBadge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
	// Dein Name in der Tab-Liste
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void oemer$name(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (NameStyle.active() && mc.getUser() != null && info.getProfile().id().equals(mc.getUser().getProfileId())) {
			cir.setReturnValue(NameStyle.display());
		}
		else if (info.getProfile().id() != null) {
			NameShare.Style st = NameShare.styleOf(info.getProfile().id());
			if (st != null) cir.setReturnValue(NameShare.styled(st, info.getProfile().name()));
		}
		// Ömer-Symbol vor dem Namen in der Tab-Liste
		if (OmerBadge.isOmer(info.getProfile().id())) {
			cir.setReturnValue(OmerBadge.prefix(cir.getReturnValue()));
		}
	}
}
