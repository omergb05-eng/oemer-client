package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wetter aus: kein Regen, kein Gewitter (nur bei dir). */
@Mixin(Level.class)
public class LevelMixin {
	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (OemerConfig.get().noWeather && (Object) this instanceof ClientLevel) cir.setReturnValue(0f);
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (OemerConfig.get().noWeather && (Object) this instanceof ClientLevel) cir.setReturnValue(0f);
	}
}
