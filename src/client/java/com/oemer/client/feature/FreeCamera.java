package com.oemer.client.feature;

import com.mojang.authlib.GameProfile;
import com.oemer.client.OemerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Unsichtbare Kamera, die frei fliegt.
 * Sie hat eine normale Hitbox, darum stößt sie an Blöcke und fliegt NICHT durch Wände.
 *
 * Aufbau angelehnt an die Freecam-Mod (MIT-Lizenz, github.com/MinecraftFreecam/Freecam).
 */
public class FreeCamera extends AbstractClientPlayer {
	private static final double DIAGONAL = Mth.sin((float) Math.toRadians(45));

	private final KeyboardInput keyboardInput;

	public FreeCamera(int id) {
		super(Minecraft.getInstance().level, new GameProfile(UUID.randomUUID(), "OemerCam"));
		setId(id);
		setPose(Pose.SWIMMING); // kleine Hitbox (0,6 Blöcke), passt durch 1er-Lücken
		getAbilities().flying = true;
		keyboardInput = new KeyboardInput(Minecraft.getInstance().options);
	}

	/** Setzt die Kamera auf die Augenhöhe vom Spieler. */
	public void copyFromPlayer(Entity player) {
		double y = player.getPose() == Pose.SWIMMING
				? player.getY()
				: player.getY() - player.getEyeHeight(Pose.SWIMMING) + player.getEyeHeight(player.getPose());
		snapTo(player.getX(), y, player.getZ(), player.getYRot(), player.getXRot());
	}

	public void spawn() {
		((ClientLevel) level()).addEntity(this);
	}

	public void despawn() {
		((ClientLevel) level()).removeEntity(getId(), RemovalReason.DISCARDED);
	}

	@Override
	public void tick() {
		keyboardInput.tick();
		doMotion();
		super.tick();
	}

	private void doMotion() {
		getAbilities().setFlyingSpeed(0);

		double speed = OemerConfig.get().freecamSpeedValue() * (isSprinting() ? 1.5 : 1.0);
		float yaw = getYRot();
		Vec3 forward = Vec3.directionFromRotation(0, yaw);
		Vec3 side = Vec3.directionFromRotation(0, yaw + 90);

		double vx = 0, vy = 0, vz = 0;
		boolean straight = false, strafe = false;
		var keys = keyboardInput.keyPresses;

		if (keys.forward()) { vx += forward.x * speed; vz += forward.z * speed; straight = true; }
		if (keys.backward()) { vx -= forward.x * speed; vz -= forward.z * speed; straight = true; }
		if (keys.right()) { vx += side.x * speed; vz += side.z * speed; strafe = true; }
		if (keys.left()) { vx -= side.x * speed; vz -= side.z * speed; strafe = true; }
		if (straight && strafe) { vx *= DIAGONAL; vz *= DIAGONAL; }
		if (keys.jump()) vy += speed;
		if (keys.shift()) vy -= speed;

		setDeltaMovement(vx, vy, vz);
		getAbilities().flying = true;
		setOnGround(false);
	}

	// Damit die Kamera sich jeden Tick bewegt (mit Kollision)
	@Override
	public boolean isEffectiveAi() {
		return true;
	}

	@Override
	public boolean canSimulateMovement() {
		return true;
	}

	@Override
	protected void applyInput() {
		Vec2 move = keyboardInput.getMoveVector();
		if (move.lengthSquared() != 0.0F) {
			move = move.scale(0.98F);
		}
		this.xxa = move.x;
		this.zza = move.y;
		this.jumping = keyboardInput.keyPresses.jump();
		Minecraft mc = Minecraft.getInstance();
		boolean fwd = keyboardInput.keyPresses.forward();
		this.setSprinting(fwd && (mc.options.keySprint.isDown() || this.isSprinting()));
	}

	@Override
	public float getViewXRot(float partialTick) {
		return this.getXRot();
	}

	@Override
	public float getViewYRot(float partialTick) {
		return this.getYRot();
	}

	// ---- Kleinigkeiten, damit es sich clean anfühlt ----

	@Override
	protected void checkFallDamage(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	public boolean isInWater() {
		return false;
	}

	@Override
	public boolean canCollideWith(Entity other) {
		return false;
	}

	@Override
	public void setPose(Pose pose) {
		super.setPose(Pose.SWIMMING);
	}

	@Override
	protected boolean updateIsUnderwater() {
		this.wasUnderwater = this.isEyeInFluid(FluidTags.WATER);
		return this.wasUnderwater;
	}

	@Override
	protected void doWaterSplashEffect() {
	}
}
