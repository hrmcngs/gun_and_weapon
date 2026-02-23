package gun_and_weapon.attack;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class GunbladeParticles {

	public static void spawnChargeParticles(Level world, Player player, int chargeTicks) {
		if (!(world instanceof ServerLevel sl)) return;

		double x = player.getX();
		double y = player.getY() + 1.0;
		double z = player.getZ();

		if (chargeTicks == 10) {
			// Stage 1: Small enchant particles
			sl.sendParticles(ParticleTypes.ENCHANT, x, y, z, 5, 0.3, 0.3, 0.3, 0.1);
		} else if (chargeTicks == 20) {
			// Stage 2: More intense with end rod
			sl.sendParticles(ParticleTypes.ENCHANT, x, y, z, 10, 0.5, 0.5, 0.5, 0.2);
			sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 3, 0.2, 0.2, 0.2, 0.05);
		} else if (chargeTicks == 30) {
			// Stage 3: Full charge flash
			sl.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
			sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 15, 0.5, 0.5, 0.5, 0.1);
			sl.sendParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.05);
		} else if (chargeTicks > 30 && chargeTicks % 5 == 0) {
			// Maintaining full charge: subtle glow
			sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 2, 0.3, 0.3, 0.3, 0.02);
		}
	}
}
