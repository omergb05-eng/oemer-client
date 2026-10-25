package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Darkness: der Dunkelheits-Effekt (Warden/Sculk) wird für dich nicht angezeigt. */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Unique
	private boolean oemer$hideDarkness(Holder<MobEffect> effect) {
		return OemerConfig.get().noDarkness && effect != null && effect.equals(MobEffects.DARKNESS)
				&& (Object) this instanceof LocalPlayer;
	}

	@Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$has(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
		if (oemer$hideDarkness(effect)) cir.setReturnValue(false);
	}

	@Inject(method = "getEffect", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$get(Holder<MobEffect> effect, CallbackInfoReturnable<MobEffectInstance> cir) {
		if (oemer$hideDarkness(effect)) cir.setReturnValue(null);
	}

	@Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$blend(Holder<MobEffect> effect, float partialTick, CallbackInfoReturnable<Float> cir) {
		if (oemer$hideDarkness(effect)) cir.setReturnValue(0f);
	}
}
