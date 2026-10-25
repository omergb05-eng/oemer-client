package com.oemer.client.mixin;

import com.oemer.client.feature.Freecam;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	// Tod oder Dimensionswechsel = Free Cam aus
	@Inject(method = "handleRespawn", at = @At("TAIL"))
	private void oemer$respawn(CallbackInfo ci) {
		Freecam.forceOff();
	}
}
