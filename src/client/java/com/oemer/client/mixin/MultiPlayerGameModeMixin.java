package com.oemer.client.mixin;

import com.oemer.client.feature.Freecam;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In der Free Cam kannst du nur schauen, nichts anklicken. */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void oemer$useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (Freecam.isEnabled()) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void oemer$interact(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (Freecam.isEnabled()) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}

	@Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
	private void oemer$interactAt(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (Freecam.isEnabled()) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void oemer$attack(Player player, Entity target, CallbackInfo ci) {
		if (Freecam.isEnabled()) {
			ci.cancel();
		}
	}
}
