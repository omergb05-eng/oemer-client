package com.oemer.client.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oemer.client.OemerConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fullbright: dreht die Helligkeit fürs Licht ganz hoch, ohne deine echte Einstellung zu ändern. */
@Mixin(LightTexture.class)
public class LightTextureMixin {
	@Expression("this.minecraft.options.gamma().get()")
	@Definition(id = "minecraft", field = "Lnet/minecraft/client/renderer/LightTexture;minecraft:Lnet/minecraft/client/Minecraft;")
	@Definition(id = "options", field = "Lnet/minecraft/client/Minecraft;options:Lnet/minecraft/client/Options;")
	@Definition(id = "gamma", method = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")
	@Definition(id = "get", method = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;")
	@WrapOperation(method = "updateLightTexture", at = @At("MIXINEXTRAS:EXPRESSION"))
	@SuppressWarnings("unchecked")
	private <T> T oemer$fullbright(OptionInstance<T> instance, Operation<T> original) {
		return OemerConfig.get().fullbright ? (T) (Double) 16.0 : original.call(instance);
	}
}
