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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;


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

	/**
	 * チャージスマッシュ (Chuzume氏 Craftsman_Arms の charge_smash 再現)。
	 * 残弾を全て消費し、視線方向へ炎のリング3つと前方範囲ダメージを放つ。
	 * 消費した残弾が多いほどダメージが上がる (フル装填8発で本家と同じ14)。
	 *
	 * @return 発動できた場合 true (残弾0なら不発で false)
	 */
	public static boolean executeChargeSmash(Level world, Player player) {
		if (world.isClientSide()) return false;

		ItemStack mainHand = player.getMainHandItem();
		CompoundTag tag = mainHand.getTag();
		int ammo = tag != null ? tag.getInt("GunCurrentAmmoCount") : 0;
		if (ammo <= 0) {
			// 弾切れ: 不発 (チャージは維持したまま)
			world.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0f, 1.2f);
			return false;
		}
		// 残弾全消費 — 消費量でダメージスケール (8発 = 本家準拠の14)
		mainHand.getOrCreateTag().putInt("GunCurrentAmmoCount", 0);
		float damage = 8.0f + 0.75f * ammo;

		player.swing(InteractionHand.MAIN_HAND, true);

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		// 視線に垂直な炎リング用の直交基底
		Vec3 upRef = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = look.cross(upRef).normalize();
		Vec3 v = look.cross(u).normalize();

		if (world instanceof ServerLevel sl) {
			// 本家 charge_smash/shape: 前方2,4,6に半径1の炎リング(20点) + 中心に爆発と溶岩
			for (double d : new double[]{2.0, 4.0, 6.0}) {
				Vec3 center = eye.add(look.scale(d));
				for (int i = 0; i < 20; i++) {
					double t = (Math.PI * 2 * i) / 20;
					Vec3 p = center.add(u.scale(Math.cos(t))).add(v.scale(Math.sin(t)));
					sl.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				}
				sl.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
				sl.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 10, 0, 0, 0, 0.1);
			}
		}

		// 本家: 前方2.5と6の各半径2にダメージ14 + 弱体化/採掘疲労/鈍化 (10秒)
		java.util.Set<LivingEntity> hitTargets = new java.util.HashSet<>();
		for (double d : new double[]{2.5, 6.0}) {
			Vec3 zone = eye.add(look.scale(d));
			hitTargets.addAll(world.getEntitiesOfClass(LivingEntity.class,
					AABB.ofSize(zone, 4.0, 4.0, 4.0), e -> e != player && e.isAlive() && e.position().distanceTo(zone) <= 2.0));
		}
		for (LivingEntity target : hitTargets) {
			target.hurt(player.damageSources().playerAttack(player), damage);
			target.knockback(1.0f, player.getX() - target.getX(), player.getZ() - target.getZ());
			// 本家: effect give 1秒 amplifier10 (hidden)
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 10, false, false));
			target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 10, false, false));
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 10, false, false));
			world.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.5f, 1.0f);
		}

		// 本家の発射音: トライデント + 爆発 + 花火 + ブレイズ
		world.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 2.0f, 0.5f);
		world.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 1.5f);
		world.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 3.0f, 0.5f);
		world.playSound(null, player.blockPosition(), SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 2.0f, 1.5f);

		player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 60);
		return true;
	}
}
