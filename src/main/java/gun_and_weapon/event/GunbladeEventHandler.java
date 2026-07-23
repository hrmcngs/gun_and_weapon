package gun_and_weapon.event;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeModeSwitch;

@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID)
public class GunbladeEventHandler {

	private static final ResourceLocation GUNBLADE_GUN_ID = GunbladeModeSwitch.GUNBLADE_GUN_ID;

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

	/**
	 * TACZ ガンスミステーブルで作られた旧形式 (tacz:modern_kinetic_gun +
	 * GunId=gun_and_weapon:gunblade) を、インベントリ内で統合アイテムへ自動変換する。
	 * TACZ のレシピ結果は gun/ammo/attachment しか出せないため、テーブルからは
	 * 一旦 TACZ 銃として出てくる。それをここで拾って本来の姿に直す。
	 */
	@SubscribeEvent
	public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
		if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
		Player player = event.player;
		if (player.level().isClientSide()) return;
		if (player.tickCount % 100 != 0) return; // 旧形式の変換は5秒に1回で十分

		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!GunbladeModeSwitch.isLegacyGunForm(stack)) continue;
			ItemStack unified = new ItemStack(
					gun_and_weapon.init.GunAndWeaponItems.GUNBLADE_SWORD.get());
			if (stack.getTag() != null) unified.setTag(stack.getTag().copy());
			// テーブル製は射撃モードで受け取る (銃として作ったので自然)
			unified.getOrCreateTag().putString(
					gun_and_weapon.item.GunbladeItem.TAG_MODE, "ranged");
			unified.getOrCreateTag().putBoolean("maw:no_melee", true);
			inv.setItem(i, unified);
			GunAndWeaponMod.LOGGER.debug("Converted legacy gunblade to unified item (slot {})", i);
		}
	}

	/**
	 * 金床: 射撃モードの銃 + エンチャント本 でエンチャントを付与できるようにする。
	 * (TACZ の銃アイテムはバニラのエンチャント分類に一致しないため自前で処理。
	 *  剣モードはバニラの武器分類でそのまま金床/エンチャントテーブル対応)
	 */
	@SubscribeEvent
	public static void onAnvilUpdate(net.minecraftforge.event.AnvilUpdateEvent event) {
		ItemStack left = event.getLeft();
		ItemStack right = event.getRight();
		if (!GunbladeModeSwitch.isGunblade(left)) return;
		if (!(right.getItem() instanceof net.minecraft.world.item.EnchantedBookItem)) return;

		var bookEnchants = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(right);
		if (bookEnchants.isEmpty()) return;

		ItemStack result = left.copy();
		var current = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(result);
		int cost = 0;
		for (var entry : bookEnchants.entrySet()) {
			var ench = entry.getKey();
			int newLevel = entry.getValue();
			int curLevel = current.getOrDefault(ench, 0);
			// バニラ金床と同じ合成規則: 同レベルなら+1、それ以外は高い方
			int merged = curLevel == newLevel ? Math.min(newLevel + 1, ench.getMaxLevel()) : Math.max(curLevel, newLevel);
			current.put(ench, merged);
			cost += merged;
		}
		net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(current, result);
		event.setOutput(result);
		event.setCost(Math.max(1, cost));
		event.setMaterialCost(1);
	}
}
