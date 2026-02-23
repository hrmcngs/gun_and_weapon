package gun_and_weapon.attack;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import gun_and_weapon.item.GunbladeSwordItem;

import java.util.Comparator;
import java.util.List;

public class GunbladeAttacks {

	/**
	 * Bullet Step: Launch player forward along look direction.
	 * Requires food level >= 6, consumes 2 hunger.
	 * Deals 8 damage to first entity hit along the path.
	 */
	public static void executeBulletStep(Level world, Player player) {
		if (world.isClientSide()) return;

		// Check hunger requirement
		int foodLevel = player.getFoodData().getFoodLevel();
		if (foodLevel < 6) {
			// Weak bullet step for low hunger
			executeWeakBulletStep(world, player);
			return;
		}

		// Consume hunger
		player.getFoodData().setFoodLevel(foodLevel - 2);

		// Launch player forward
		Vec3 look = player.getLookAngle();
		double launchStrength = 1.8;
		player.setDeltaMovement(
				look.x * launchStrength,
				Math.max(look.y * launchStrength, 0.3),
				look.z * launchStrength);
		player.hurtMarked = true;

		// Apply movement effects
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 3, 4, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.JUMP, 3, 3, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 0, false, false));

		// Swing animation
		player.swing(InteractionHand.MAIN_HAND, true);

		// Hit detection along the dash path
		Vec3 start = player.getEyePosition();
		double maxRange = 5.0;
		boolean hit = false;

		for (double r = 0; r < maxRange && !hit; r += 0.5) {
			Vec3 checkPos = start.add(look.scale(r));
			List<LivingEntity> targets = world.getEntitiesOfClass(
					LivingEntity.class,
					AABB.ofSize(checkPos, 2.0, 2.0, 2.0),
					e -> e != player && e.isAlive());

			if (!targets.isEmpty()) {
				LivingEntity target = targets.stream()
						.min(Comparator.comparingDouble(e -> e.distanceToSqr(checkPos)))
						.orElse(null);

				if (target != null) {
					target.hurt(DamageSource.playerAttack(player), 8.0f);
					target.knockback(0.8f,
							player.getX() - target.getX(),
							player.getZ() - target.getZ());

					if (world instanceof ServerLevel sl) {
						sl.sendParticles(ParticleTypes.CRIT,
								target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
								15, 0.3, 0.3, 0.3, 0.1);
					}
					hit = true;
				}
			}
		}

		// Trail particles
		if (world instanceof ServerLevel sl) {
			for (double r = 0; r < 3.0; r += 0.3) {
				Vec3 p = player.position().add(look.scale(r));
				sl.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 0.5, p.z, 2, 0.1, 0.1, 0.1, 0.01);
			}
		}

		// Sound
		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.5f);

		// Cooldown 1 second
		player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 20);
	}

	/**
	 * Weak Bullet Step: reduced movement when hunger is low.
	 */
	private static void executeWeakBulletStep(Level world, Player player) {
		Vec3 look = player.getLookAngle();
		double launchStrength = 0.8;
		player.setDeltaMovement(
				look.x * launchStrength,
				Math.max(look.y * launchStrength, 0.2),
				look.z * launchStrength);
		player.hurtMarked = true;

		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 0, false, false));

		player.swing(InteractionHand.MAIN_HAND, true);

		// Reduced hit detection
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(look.scale(3.0));
		List<LivingEntity> targets = world.getEntitiesOfClass(
				LivingEntity.class,
				AABB.ofSize(start.add(look.scale(1.5)), 3.0, 3.0, 3.0),
				e -> e != player && e.isAlive());

		if (!targets.isEmpty()) {
			LivingEntity target = targets.stream()
					.min(Comparator.comparingDouble(e -> e.distanceTo(player)))
					.orElse(null);
			if (target != null) {
				target.hurt(DamageSource.playerAttack(player), 4.0f);
			}
		}

		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.0f);
		player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 30);
	}

	/**
	 * Charge Smash: AoE attack dealing 14 damage in radius.
	 * Applies Slowness II and Weakness I to hit entities.
	 * Consumes 1 ammo.
	 */
	public static void executeChargeSmash(Level world, Player player) {
		if (world.isClientSide()) return;

		// Consume 1 ammo
		CompoundTag data = player.getPersistentData();
		int ammo = data.getInt(GunbladeSwordItem.TAG_AMMO_COUNT);
		if (ammo > 0) {
			data.putInt(GunbladeSwordItem.TAG_AMMO_COUNT, ammo - 1);
		}

		player.swing(InteractionHand.MAIN_HAND, true);

		double radius = 4.0;
		Vec3 center = player.position();
		Vec3 look = player.getLookAngle();

		// Primary damage zone: in front of the player
		Vec3 frontCenter = center.add(look.x * 2.5, 0, look.z * 2.5);

		List<LivingEntity> targets = world.getEntitiesOfClass(
				LivingEntity.class,
				AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
				e -> e != player && e.isAlive() && e.distanceTo(player) <= radius);

		for (LivingEntity target : targets) {
			// 14 damage
			target.hurt(DamageSource.playerAttack(player), 14.0f);

			// Knockback away from player
			double dx = target.getX() - player.getX();
			double dz = target.getZ() - player.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > 0) {
				target.knockback(1.5f, -dx / dist, -dz / dist);
			}

			// Debuffs
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
			target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 0));
		}

		// Particle effects
		if (world instanceof ServerLevel sl) {
			// Expanding ring of sweep attack particles
			for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 12) {
				for (double r = 1.0; r <= radius; r += 1.0) {
					double px = center.x + Math.cos(angle) * r;
					double pz = center.z + Math.sin(angle) * r;
					sl.sendParticles(ParticleTypes.SWEEP_ATTACK, px, center.y + 0.5, pz, 1, 0, 0, 0, 0);
				}
			}
			// Central explosion
			sl.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1, center.z, 3, 0.5, 0.5, 0.5, 0);
			sl.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.5, center.z, 20, 1.5, 0.3, 1.5, 0.05);
		}

		// Sound
		world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.2f);
		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);

		// Cooldown 3 seconds
		player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 60);
	}
}
