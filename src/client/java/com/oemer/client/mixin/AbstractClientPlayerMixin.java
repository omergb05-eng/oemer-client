package com.oemer.client.mixin;

import com.oemer.client.feature.CapeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gibt deinem eigenen Spieler das Ömer-Cape – und Freunden ihr geteiltes Cape. */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void oemer$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		Minecraft mc = Minecraft.getInstance();
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (self != mc.player) {
			// Freunde, die ihr Cape mit dir teilen
			ClientAsset.Texture friendCape = com.oemer.client.online.FriendSkins.capeFor(self.getUUID());
			if (friendCape != null) {
				PlayerSkin skin = cir.getReturnValue();
				cir.setReturnValue(new PlayerSkin(skin.body(), friendCape, null, skin.model(), skin.secure()));
			}
			return;
		}
		ClientAsset.Texture cape = CapeManager.currentTexture();
		if (cape == null) {
			return;
		}
		PlayerSkin skin = cir.getReturnValue();
		// Elytra nimmt automatisch das Cape-Bild, wenn sie keine eigene Textur hat
		cir.setReturnValue(new PlayerSkin(skin.body(), cape, null, skin.model(), skin.secure()));
	}
}
