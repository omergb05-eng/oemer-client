package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import com.oemer.client.feature.NameStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	// Dein eigenes Namensschild auch in der 3. Person (F5) zeigen
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void oemer$showSelf(Avatar entity, double distSq, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (NameStyle.active() && OemerConfig.get().nameShowSelf && entity == mc.player && !mc.options.getCameraType().isFirstPerson()
				&& mc.getCameraEntity() == mc.player) {
			cir.setReturnValue(true);
		}
	}
}
