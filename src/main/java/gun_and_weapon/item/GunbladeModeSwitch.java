package gun_and_weapon.item;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;

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
 * 統合アイテム (GunbladeItem) の NBT フラグを切り替えるだけなので、
 * エンチャント・名前・残弾・カスタムNBTなど全状態が自動的に維持される。
 * オフハンド持ち替えキー ([F]) を GunbladeEventHandler が横取りして呼ぶ。
 */
public final class GunbladeModeSwitch {

	public static final ResourceLocation GUNBLADE_GUN_ID = new ResourceLocation("gun_and_weapon", "gunblade");

	private GunbladeModeSwitch() {}

	/** メインハンドがガンブレード (統合アイテム or 旧TACZ銃形態) なら true */
	public static boolean isGunblade(ItemStack stack) {
		return stack.getItem() instanceof GunbladeItem || isLegacyGunForm(stack);
	}

	/** 旧バージョンで作られた TACZ 銃形態 (tacz:modern_kinetic_gun + GunId) か */
	public static boolean isLegacyGunForm(ItemStack stack) {
		if (stack.getItem() instanceof GunbladeItem) return false;
		try {
			IGun iGun = IGun.getIGunOrNull(stack);
			if (iGun != null) return GUNBLADE_GUN_ID.equals(iGun.getGunId(stack));
		} catch (NoClassDefFoundError ignored) {}
		return false;
	}

	/** 互換用: 射撃モードのガンブレードか (統合アイテムranged or 旧形態) */
	public static boolean isGunbladeGun(ItemStack stack) {
		if (stack.getItem() instanceof GunbladeItem) return GunbladeItem.isRanged(stack);
		return isLegacyGunForm(stack);
	}

	/** メインハンドのガンブレードのモードを切り替える */
	public static void toggle(Player player) {
		ItemStack stack = player.getMainHandItem();

		// 旧TACZ銃形態 → 統合アイテムへ移行 (全NBT維持・近接モードで開始)
		if (isLegacyGunForm(stack)) {
			ItemStack unified = new ItemStack(GunAndWeaponItems.GUNBLADE_SWORD.get());
			if (stack.getTag() != null) unified.setTag(stack.getTag().copy());
			unified.getOrCreateTag().putString(GunbladeItem.TAG_MODE, "melee");
			unified.getOrCreateTag().remove("maw:no_melee");
			player.getInventory().setItem(player.getInventory().selected, unified);
			playSwitchSound(player, false);
			return;
		}

		if (!(stack.getItem() instanceof GunbladeItem)) return;

		if (GunbladeItem.isMelee(stack)) {
			// → 射撃モード: ガンパックが読み込まれているか確認
			try {
				if (TimelessAPI.getCommonGunIndex(GUNBLADE_GUN_ID).isEmpty()) {
					GunAndWeaponMod.LOGGER.warn("Gun index not found: {} (gun pack not loaded?)", GUNBLADE_GUN_ID);
					player.displayClientMessage(Component.translatable("message.gun_and_weapon.gun_pack_missing"), true);
					return;
				}
			} catch (NoClassDefFoundError e) {
				return;
			}
			CompoundTag tag = stack.getOrCreateTag();
			// 初回切替時に TACZ 用 NBT を初期化 (以後はスタック内で維持される)
			if (!tag.contains("GunId")) {
				tag.putString("GunId", GUNBLADE_GUN_ID.toString());
			}
			if (!tag.contains("GunFireMode") || "BURST".equals(tag.getString("GunFireMode"))) {
				// バーストは廃止 (1クリックで全弾発射になるため)。既存アイテムも矯正する
				tag.putString("GunFireMode", "SEMI");
			}
			if (!tag.contains("GunCurrentAmmoCount")) {
				tag.putInt("GunCurrentAmmoCount", 0);
			}
			tag.putString(GunbladeItem.TAG_MODE, "ranged");
			// 射撃モード中は MAW の近接システム (コンボ/チャージ/回避) を無効化
			tag.putBoolean("maw:no_melee", true);
			playSwitchSound(player, true);
		} else {
			CompoundTag meleeTag = stack.getOrCreateTag();
			meleeTag.putString(GunbladeItem.TAG_MODE, "melee");
			meleeTag.remove("maw:no_melee");
			playSwitchSound(player, false);
		}
	}

	private static void playSwitchSound(Player player, boolean toRanged) {
		player.level().playSound(null, player.blockPosition(),
				toRanged ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
				SoundSource.PLAYERS, 0.5f, 1.5f);
	}
}
