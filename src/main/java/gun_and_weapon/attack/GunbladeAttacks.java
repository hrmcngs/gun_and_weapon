package gun_and_weapon.attack;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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

	public static void executeBulletStep(Level world, Player player) {
		if (world.isClientSide()) return;

		int foodLevel = player.getFoodData().getFoodLevel();
		if (foodLevel < 6) {
			executeWeakBulletStep(world, player);
			return;
		}

		player.getFoodData().setFoodLevel(foodLevel - 2);

		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.x * 1.8, Math.max(look.y * 1.8, 0.3), look.z * 1.8);
		player.hurtMarked = true;

		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 3, 4, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.JUMP, 3, 3, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 0, false, false));
		player.swing(InteractionHand.MAIN_HAND, true);

		Vec3 start = player.getEyePosition();
		boolean hit = false;
		for (double r = 0; r < 5.0 && !hit; r += 0.5) {
			Vec3 checkPos = start.add(look.scale(r));
			List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class,
					AABB.ofSize(checkPos, 2.0, 2.0, 2.0), e -> e != player && e.isAlive());
			if (!targets.isEmpty()) {
				LivingEntity target = targets.stream()
						.min(Comparator.comparingDouble(e -> e.distanceToSqr(checkPos))).orElse(null);
				if (target != null) {
					target.hurt(player.damageSources().playerAttack(player), 8.0f);
					target.knockback(0.8f, player.getX() - target.getX(), player.getZ() - target.getZ());
					if (world instanceof ServerLevel sl)
						sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
					hit = true;
				}
			}
		}

		if (world instanceof ServerLevel sl)
			for (double r = 0; r < 3.0; r += 0.3) {
				Vec3 p = player.position().add(look.scale(r));
				sl.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 0.5, p.z, 2, 0.1, 0.1, 0.1, 0.01);
			}

		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.5f);
		player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 20);
	}

	private static void executeWeakBulletStep(Level world, Player player) {
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.x * 0.8, Math.max(look.y * 0.8, 0.2), look.z * 0.8);
		player.hurtMarked = true;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 0, false, false));
		player.swing(InteractionHand.MAIN_HAND, true);

		List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class,
				AABB.ofSize(player.getEyePosition().add(look.scale(1.5)), 3.0, 3.0, 3.0), e -> e != player && e.isAlive());
		if (!targets.isEmpty()) {
			LivingEntity target = targets.stream().min(Comparator.comparingDouble(e -> e.distanceTo(player))).orElse(null);
			if (target != null) target.hurt(player.damageSources().playerAttack(player), 4.0f);
		}
		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.0f);
		player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 30);
	}

	public static void executeChargeSmash(Level world, Player player) {
		if (world.isClientSide()) return;

		CompoundTag data = player.getPersistentData();
		int ammo = data.getInt(GunbladeSwordItem.TAG_AMMO_COUNT);
		if (ammo > 0) data.putInt(GunbladeSwordItem.TAG_AMMO_COUNT, ammo - 1);

		player.swing(InteractionHand.MAIN_HAND, true);
		double radius = 4.0;
		Vec3 center = player.position();

		List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class,
				AABB.ofSize(center, radius * 2, radius * 2, radius * 2), e -> e != player && e.isAlive() && e.distanceTo(player) <= radius);
		for (LivingEntity target : targets) {
			target.hurt(player.damageSources().playerAttack(player), 14.0f);
			double dx = target.getX() - player.getX();
			double dz = target.getZ() - player.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > 0) target.knockback(1.5f, -dx / dist, -dz / dist);
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
			target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 0));
		}

		if (world instanceof ServerLevel sl) {
			for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 12)
				for (double r = 1.0; r <= radius; r += 1.0)
					sl.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x + Math.cos(angle) * r, center.y + 0.5, center.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
			sl.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1, center.z, 3, 0.5, 0.5, 0.5, 0);
			sl.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.5, center.z, 20, 1.5, 0.3, 1.5, 0.05);
		}
		world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.2f);
		world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 60);
	}
}
