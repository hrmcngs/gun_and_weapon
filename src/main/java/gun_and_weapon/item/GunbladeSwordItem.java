package gun_and_weapon.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import gun_and_weapon.attack.GunbladeAttacks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ガンブレード（近接モード）。
 *
 * 操作:
 *   右クリック          … バレットステップ
 *   スニーク+右クリック … ガード
 *   左クリック長押し     … チャージ → 離すとチャージスマッシュ
 *                          (本体MOD The four primitives and Weapons のチャージシステム。
 *                           スキル画面のチャージ枠に gun_and_weapon:charge_smash を設定)
 *   [V]                 … 射撃モードに切替 (TaCZ)
 */
public class GunbladeSwordItem extends SwordItem {

	public static final String TAG_AMMO_COUNT = "gunblade:ammo_count";
	public static final String TAG_MODE = "gunblade:mode";
	public static final int MAX_AMMO = 8;

	public GunbladeSwordItem() {
		super(new Tier() {
			public int getUses() {
				return 0;
			}

			public float getSpeed() {
				return 4f;
			}

			public float getAttackDamageBonus() {
				return 5f;
			}

			public int getLevel() {
				return 3;
			}

			public int getEnchantmentValue() {
				return 10;
			}

			public Ingredient getRepairIngredient() {
				return Ingredient.EMPTY;
			}
		}, 0, -2.4f, new Item.Properties().stacksTo(1));
	}

	@Override
	public int getUseDuration(ItemStack stack) {
		return 72000;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BLOCK;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (world.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}

		if (player.isShiftKeyDown()) {
			// Sneaking + right click = Guard mode (blocking)
			player.startUsingItem(hand);
			return InteractionResultHolder.consume(stack);
		} else {
			// Right click = Bullet Step
			GunbladeAttacks.executeBulletStep(world, player);
			return InteractionResultHolder.success(stack);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);
		if (world.isClientSide() || !(entity instanceof Player player) || !selected) {
			return;
		}
		displayHud(player, player.getPersistentData());
	}

	private void displayHud(Player player, CompoundTag data) {
		int ammo = data.getInt(TAG_AMMO_COUNT);
		player.displayClientMessage(
				Component.translatable("hud.gun_and_weapon.gunblade_melee", ammo, MAX_AMMO), true);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> list, TooltipFlag flag) {
		super.appendHoverText(stack, world, list, flag);
		list.add(Component.translatable("item.gun_and_weapon.gunblade_sword.tooltip.mode"));
		list.add(Component.translatable("item.gun_and_weapon.gunblade_sword.tooltip.controls"));
		list.add(Component.translatable("item.gun_and_weapon.gunblade_sword.tooltip.charge"));
		list.add(Component.translatable("item.gun_and_weapon.gunblade_sword.tooltip.switch"));
	}

	@Override
	public boolean isDamageable(ItemStack stack) {
		return false;
	}
}
