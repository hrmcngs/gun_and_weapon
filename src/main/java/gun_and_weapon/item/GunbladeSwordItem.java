package gun_and_weapon.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import gun_and_weapon.attack.GunbladeParticles;

import javax.annotation.Nullable;
import java.util.List;

public class GunbladeSwordItem extends SwordItem {

	public static final String TAG_CHARGE_TICKS = "gunblade:charge_ticks";
	public static final String TAG_AMMO_COUNT = "gunblade:ammo_count";
	public static final String TAG_MODE = "gunblade:mode";
	public static final int MAX_CHARGE_TICKS = 30;
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

		CompoundTag data = player.getPersistentData();
		int charge = data.getInt(TAG_CHARGE_TICKS);

		if (charge >= MAX_CHARGE_TICKS) {
			// Full charge + right click = Charge Smash
			GunbladeAttacks.executeChargeSmash(world, player);
			data.putInt(TAG_CHARGE_TICKS, 0);
			return InteractionResultHolder.success(stack);
		} else if (player.isShiftKeyDown()) {
			// Sneaking + right click = Guard mode (blocking)
			player.startUsingItem(hand);
			return InteractionResultHolder.consume(stack);
		} else {
			// Not sneaking, not charged = Bullet Step
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

		CompoundTag data = player.getPersistentData();

		// Charge system: increment while sneaking
		if (player.isShiftKeyDown() && !player.isUsingItem()) {
			int charge = data.getInt(TAG_CHARGE_TICKS);
			if (charge < MAX_CHARGE_TICKS + 10) {
				data.putInt(TAG_CHARGE_TICKS, charge + 1);
			}
			GunbladeParticles.spawnChargeParticles(world, player, charge + 1);

			// Sound at charge milestones
			if (charge + 1 == 10) {
				world.playSound(null, player.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5f, 0.8f);
			} else if (charge + 1 == 20) {
				world.playSound(null, player.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5f, 1.2f);
			} else if (charge + 1 == MAX_CHARGE_TICKS) {
				world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 2.0f);
			}
		} else if (!player.isUsingItem()) {
			// Reset charge when not sneaking (and not guarding)
			data.putInt(TAG_CHARGE_TICKS, 0);
		}

		// Display HUD
		displayHud(player, data);
	}

	private void displayHud(Player player, CompoundTag data) {
		int charge = data.getInt(TAG_CHARGE_TICKS);
		int ammo = data.getInt(TAG_AMMO_COUNT);

		StringBuilder chargeBar = new StringBuilder();
		if (charge >= MAX_CHARGE_TICKS) {
			chargeBar.append("\u00a7a\u00a7l[MAX]\u00a7r");
		} else {
			int filled = (int) ((charge / (float) MAX_CHARGE_TICKS) * 10);
			chargeBar.append("\u00a7e[");
			for (int i = 0; i < 10; i++) {
				chargeBar.append(i < filled ? "|" : " ");
			}
			chargeBar.append("]");
		}

		String display = "\u00a7b\u2694 MELEE " + chargeBar + " \u00a77Ammo: " + ammo + "/" + MAX_AMMO;
		player.displayClientMessage(Component.literal(display), true);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> list, TooltipFlag flag) {
		super.appendHoverText(stack, world, list, flag);
		list.add(Component.literal("\u00a77\u30e2\u30fc\u30c9: \u00a7e\u8fd1\u63a5\u30e2\u30fc\u30c9"));
		list.add(Component.literal("\u00a77\u30b9\u30cb\u30fc\u30af\u3067\u30c1\u30e3\u30fc\u30b8\u3001\u53f3\u30af\u30ea\u30c3\u30af\u3067\u7279\u6b8a\u653b\u6483"));
		list.add(Component.literal("\u00a78[V]\u3067\u5c04\u6483\u30e2\u30fc\u30c9\u306b\u5207\u66ff"));
	}

	@Override
	public boolean isDamageable(ItemStack stack) {
		return false;
	}
}
