package gun_and_weapon.event;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.init.GunAndWeaponItems;

@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID)
public class GunbladeGuardHandler {

	@SubscribeEvent
	public static void onPlayerHurt(LivingHurtEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (player.level().isClientSide()) return;

		ItemStack useItem = player.getUseItem();
		if (useItem.isEmpty() || useItem.getItem() != GunAndWeaponItems.GUNBLADE_SWORD.get()) return;
		if (!player.isBlocking()) return;

		float damage = event.getAmount();
		int blockingDuration = useItem.getUseDuration() - player.getUseItemRemainingTicks();

		if (blockingDuration <= 5) {
			event.setAmount(0);
			event.setCanceled(true);
			if (event.getSource().getEntity() instanceof LivingEntity attacker) {
				attacker.hurt(player.damageSources().playerAttack(player), 4.0f);
				attacker.knockback(1.0f, attacker.getX() - player.getX(), attacker.getZ() - player.getZ());
			}
			if (player.level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.5, 0.5, 0.3);
				sl.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			}
			player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 2.0f);
		} else {
			event.setAmount(damage * 0.4f);
			if (player.level() instanceof ServerLevel sl)
				sl.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
			player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.0f);
		}
	}
}
