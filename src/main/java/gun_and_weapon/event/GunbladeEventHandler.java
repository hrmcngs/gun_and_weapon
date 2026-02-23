package gun_and_weapon.event;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeSwordItem;

@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID)
public class GunbladeEventHandler {

	private static final ResourceLocation GUNBLADE_GUN_ID = new ResourceLocation("tacz", "gunblade");

	@SubscribeEvent
	public static void onGunFire(GunFireEvent event) {
		if (!event.getLogicalSide().isServer()) return;
		if (!(event.getShooter() instanceof Player player)) return;

		ItemStack gunStack = event.getGunItemStack();
		IGun iGun = IGun.getIGunOrNull(gunStack);
		if (iGun == null) return;

		ResourceLocation gunId = iGun.getGunId(gunStack);
		if (!GUNBLADE_GUN_ID.equals(gunId)) return;

		// Sync ammo count to persistent data for mode switching
		CompoundTag data = player.getPersistentData();
		int currentAmmo = iGun.getCurrentAmmoCount(gunStack);
		data.putInt(GunbladeSwordItem.TAG_AMMO_COUNT, currentAmmo);
	}
}
