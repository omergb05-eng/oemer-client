package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
	// No Explosion Particles: die großen Explosions-Wolken werden gar nicht erst erzeugt
	@Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$noExplosion(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz,
								   CallbackInfoReturnable<Particle> cir) {
		if (OemerConfig.get().noExplosionParticles
				&& (options.getType() == ParticleTypes.EXPLOSION || options.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
			cir.setReturnValue(null);
		}
	}
}
