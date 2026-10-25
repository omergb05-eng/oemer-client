package com.oemer.client.mixin;

import com.oemer.client.feature.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Shadow
	protected abstract EntityRenderState extractEntity(Entity entity, float partialTick);

	// In der Free Cam siehst du deinen eigenen Spieler
	@Inject(method = "extractVisibleEntities", at = @At("RETURN"))
	private void oemer$showPlayer(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState renderState, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.player != null && Freecam.isEnabled()) {
			TickRateManager tickRate = mc.level.tickRateManager();
			float partial = deltaTracker.getGameTimeDeltaPartialTick(!tickRate.isEntityFrozen(mc.player));
			renderState.entityRenderStates.add(this.extractEntity(mc.player, partial));
		}
	}
}
