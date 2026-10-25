package com.oemer.client.mixin;

import com.oemer.client.OemerConfig;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TNT Timer: Sekunden bis zur Explosion über dem TNT. */
@Mixin(TntRenderer.class)
public class TntRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/PrimedTnt;Lnet/minecraft/client/renderer/entity/state/TntRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void oemer$timer(PrimedTnt tnt, TntRenderState state, float partialTick, CallbackInfo ci) {
		if (!OemerConfig.get().tntTimer) return;
		float seconds = Math.max(0f, (tnt.getFuse() - partialTick) / 20f);
		String color = seconds > 2.5f ? "§a" : seconds > 1f ? "§e" : "§c";
		state.nameTag = Component.literal(color + String.format(java.util.Locale.ROOT, "%.1f", seconds) + "s");
		state.nameTagAttachment = new Vec3(0, tnt.getBbHeight() + 0.5, 0);
	}
}
