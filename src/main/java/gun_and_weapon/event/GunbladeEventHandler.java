package gun_and_weapon.event;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeModeSwitch;
import gun_and_weapon.item.GunbladeSwordItem;

@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID)
public class GunbladeEventHandler {

	private static final ResourceLocation GUNBLADE_GUN_ID = GunbladeModeSwitch.GUNBLADE_GUN_ID;

	@SubscribeEvent
	public static void onGunFire(GunFireEvent event) {
		if (!event.getLogicalSide().isServer()) return;
		if (!(event.getShooter() instanceof Player player)) return;

		ItemStack gunStack = event.getGunItemStack();
		IGun iGun = IGun.getIGunOrNull(gunStack);
		if (iGun == null) return;
		if (!GUNBLADE_GUN_ID.equals(iGun.getGunId(gunStack))) return;

		CompoundTag data = player.getPersistentData();
		data.putInt(GunbladeSwordItem.TAG_AMMO_COUNT, iGun.getCurrentAmmoCount(gunStack));
	}

	/**
	 * オフハンド持ち替えキー ([F]) をモード切替に流用する。
	 * ガンブレードを持っている間は通常のオフハンド持ち替えをキャンセルし、
	 * 近接⇔射撃モードを切り替える (普通に持ち替えたい場合はインベントリ経由)。
	 */
	@SubscribeEvent
	public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (player.level().isClientSide()) return;
		if (!GunbladeModeSwitch.isGunblade(player.getMainHandItem())) return;

		event.setCanceled(true);
		GunbladeModeSwitch.toggle(player);
	}
}
