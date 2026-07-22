package gun_and_weapon.item;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.init.GunAndWeaponItems;

/**
 * ガンブレードの近接⇔射撃モード切替 (サーバーサイド)。
 *
 * オフハンド持ち替えキー ([F]) を GunbladeEventHandler が横取りして呼ぶ。
 * 残弾はプレイヤーの永続NBTでモード間共有される。
 */
public final class GunbladeModeSwitch {

	public static final ResourceLocation GUNBLADE_GUN_ID = new ResourceLocation("gun_and_weapon", "gunblade");

	private GunbladeModeSwitch() {}

	/** メインハンドがガンブレード (どちらのモードでも) なら true */
	public static boolean isGunblade(ItemStack stack) {
		if (stack.getItem() == GunAndWeaponItems.GUNBLADE_SWORD.get()) return true;
		return isGunbladeGun(stack);
	}

	public static boolean isGunbladeGun(ItemStack stack) {
		try {
			IGun iGun = IGun.getIGunOrNull(stack);
			if (iGun != null) return GUNBLADE_GUN_ID.equals(iGun.getGunId(stack));
		} catch (NoClassDefFoundError ignored) {}
		return false;
	}

	/** メインハンドのガンブレードのモードを切り替える */
	public static void toggle(Player player) {
		ItemStack mainHand = player.getMainHandItem();
		if (mainHand.getItem() == GunAndWeaponItems.GUNBLADE_SWORD.get()) {
			switchToRanged(player);
		} else if (isGunbladeGun(mainHand)) {
			switchToMelee(player, mainHand);
		}
	}

	private static void switchToRanged(Player player) {
		CompoundTag data = player.getPersistentData();
		int ammo = Math.max(0, Math.min(data.getInt(GunbladeSwordItem.TAG_AMMO_COUNT), GunbladeSwordItem.MAX_AMMO));
		ItemStack gunStack = createGunStack(ammo);
		if (gunStack.isEmpty()) {
			// ガンパックが読み込まれていない (gun index 未登録) 場合は切り替えない
			player.displayClientMessage(Component.translatable("message.gun_and_weapon.gun_pack_missing"), true);
			return;
		}
		player.getInventory().setItem(player.getInventory().selected, gunStack);
		data.putString(GunbladeSwordItem.TAG_MODE, "ranged");
		player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.5f, 1.5f);
	}

	private static void switchToMelee(Player player, ItemStack gunStack) {
		CompoundTag data = player.getPersistentData();
		data.putInt(GunbladeSwordItem.TAG_AMMO_COUNT, readGunAmmo(gunStack));
		player.getInventory().setItem(player.getInventory().selected, new ItemStack(GunAndWeaponItems.GUNBLADE_SWORD.get()));
		data.putString(GunbladeSwordItem.TAG_MODE, "melee");
		player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.5f, 1.5f);
	}

	private static ItemStack createGunStack(int ammo) {
		try {
			// gun index 未登録のまま組み立てると「不明な銃」になるため確認する
			if (TimelessAPI.getCommonGunIndex(GUNBLADE_GUN_ID).isEmpty()) {
				GunAndWeaponMod.LOGGER.warn("Gun index not found: {} (gun pack not loaded?)", GUNBLADE_GUN_ID);
				return ItemStack.EMPTY;
			}
			return GunItemBuilder.create().setId(GUNBLADE_GUN_ID).setAmmoCount(ammo).setFireMode(FireMode.SEMI).build();
		} catch (NoClassDefFoundError ignored) {}
		return ItemStack.EMPTY;
	}

	private static int readGunAmmo(ItemStack gunStack) {
		try {
			IGun iGun = IGun.getIGunOrNull(gunStack);
			if (iGun != null) return iGun.getCurrentAmmoCount(gunStack);
		} catch (NoClassDefFoundError ignored) {}
		return 0;
	}
}
