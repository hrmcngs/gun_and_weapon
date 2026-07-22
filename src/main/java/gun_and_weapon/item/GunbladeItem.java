package gun_and_weapon.item;

import java.util.UUID;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;

import gun_and_weapon.attack.GunbladeAttacks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ガンブレード統合アイテム (単一アイテムID / NBTでモード切替)。
 *
 * TACZ の ModernKineticGunItem を継承しており、NBT の {@link #TAG_MODE} で:
 *   - "melee"  (既定): 剣として振る舞う。TACZ の射撃/リロードは無効化。
 *   - "ranged": TACZ の銃としてそのまま振る舞う。
 *
 * 同一スタックのままモードが切り替わるため、エンチャント・名前・残弾・
 * カスタムNBTなど全ての状態が自動的に引き継がれる。
 * MAW (The four primitives and Weapons) のスキル画面にも両モードとも
 * 同じアイテムIDとして認識される。
 */
public class GunbladeItem extends ModernKineticGunItem {

	public static final String TAG_MODE = "gunblade:mode";
	public static final int MAX_AMMO = 8;

	/** 近接モードの攻撃属性 (元データパック準拠: 攻撃力+6=計7 / 攻撃速度-2.4) */
	private static final UUID MELEE_DAMAGE_UUID = UUID.fromString("5c9271fa-6f42-4c8b-9df2-7e1a63b3c101");
	private static final UUID MELEE_SPEED_UUID = UUID.fromString("5c9271fa-6f42-4c8b-9df2-7e1a63b3c102");
	private static final Multimap<Attribute, AttributeModifier> MELEE_ATTRIBUTES = ImmutableMultimap.of(
			Attributes.ATTACK_DAMAGE, new AttributeModifier(MELEE_DAMAGE_UUID, "Gunblade melee damage", 6.0, AttributeModifier.Operation.ADDITION),
			Attributes.ATTACK_SPEED, new AttributeModifier(MELEE_SPEED_UUID, "Gunblade melee speed", -2.4, AttributeModifier.Operation.ADDITION));

	public static boolean isMelee(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag == null || !"ranged".equals(tag.getString(TAG_MODE));
	}

	public static boolean isRanged(ItemStack stack) {
		return !isMelee(stack);
	}

	// ===================================================================
	// TACZ 銃動作: 近接モードでは無効化
	// ===================================================================

	/** 近接モード時に返すダミー銃ID (どのガンパックにも未登録)。
	 *  TACZ は index 解決に失敗した銃を描画・射撃・HUD表示しないため、
	 *  近接モードでは TACZ の銃システムが素通りしてバニラ描画 (剣モデル) になる。
	 *  ※ null を返すと TACZ の isSame 等が NPE でクラッシュするのでダミーIDを使う。 */
	private static final net.minecraft.resources.ResourceLocation MELEE_DUMMY_GUN_ID =
			new net.minecraft.resources.ResourceLocation("gun_and_weapon", "gunblade_melee_dummy");

	@Override
	public net.minecraft.resources.ResourceLocation getGunId(ItemStack stack) {
		if (isMelee(stack)) return MELEE_DUMMY_GUN_ID;
		return super.getGunId(stack);
	}

	@Override
	public void shoot(ShooterDataHolder data, ItemStack stack, Supplier<Float> pitch, Supplier<Float> yaw, LivingEntity shooter) {
		if (isMelee(stack)) return;
		super.shoot(data, stack, pitch, yaw, shooter);
	}

	@Override
	public boolean startReload(ShooterDataHolder data, ItemStack stack, LivingEntity shooter) {
		if (isMelee(stack)) return false;
		return super.startReload(data, stack, shooter);
	}

	@Override
	public boolean startBolt(ShooterDataHolder data, ItemStack stack, LivingEntity shooter) {
		if (isMelee(stack)) return false;
		return super.startBolt(data, stack, shooter);
	}

	@Override
	public void fireSelect(ShooterDataHolder data, ItemStack stack) {
		if (isMelee(stack)) return;
		super.fireSelect(data, stack);
	}

	@Override
	public void melee(ShooterDataHolder data, LivingEntity shooter, ItemStack stack) {
		if (isMelee(stack)) return; // 近接モードは通常攻撃で戦う (TACZの銃剣モーション不要)
		super.melee(data, shooter, stack);
	}

	/**
	 * TACZ の AbstractGunItem は腕振りアニメを常にキャンセルする (return true)。
	 * 近接モードでは通常武器と同じように腕を振らせる。
	 */
	@Override
	public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
		if (isMelee(stack)) return false;
		return super.onEntitySwing(stack, entity);
	}

	// ===================================================================
	// 剣動作 (近接モード)
	// ===================================================================

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (isRanged(stack)) {
			return super.use(world, player, hand);
		}
		if (world.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}
		if (player.isShiftKeyDown()) {
			// スニーク+右クリック = ガード
			player.startUsingItem(hand);
			return InteractionResultHolder.consume(stack);
		}
		// 右クリック = バレットステップ
		GunbladeAttacks.executeBulletStep(world, player);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public int getUseDuration(ItemStack stack) {
		return isMelee(stack) ? 72000 : super.getUseDuration(stack);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return isMelee(stack) ? UseAnim.BLOCK : super.getUseAnimation(stack);
	}

	@Override
	public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
		if (slot == EquipmentSlot.MAINHAND && isMelee(stack)) {
			return MELEE_ATTRIBUTES;
		}
		return super.getAttributeModifiers(slot, stack);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);
		if (world.isClientSide() || !(entity instanceof Player player) || !selected) return;
		if (!isMelee(stack)) return;
		int ammo = getCurrentAmmoCount(stack);
		player.displayClientMessage(
				Component.translatable("hud.gun_and_weapon.gunblade_melee", ammo, MAX_AMMO), true);
	}

	@Override
	public Component getName(ItemStack stack) {
		// 近接モードは通常のアイテム名、射撃モードは TACZ の銃名
		return isMelee(stack) ? Component.translatable(this.getDescriptionId(stack)) : super.getName(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> list, TooltipFlag flag) {
		super.appendHoverText(stack, world, list, flag);
		if (isMelee(stack)) {
			list.add(Component.translatable("item.gun_and_weapon.gunblade_sword.tooltip.mode"));
			list.add(Component.translatable("item.gun_and_weapon.gunblade_sword.tooltip.controls"));
			list.add(Component.translatable("item.gun_and_weapon.gunblade_sword.tooltip.charge"));
			list.add(Component.translatable("item.gun_and_weapon.gunblade_sword.tooltip.switch"));
		}
	}

	// ===================================================================
	// エンチャント (サバイバル対応)
	// ===================================================================

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return stack.getCount() == 1;
	}

	@Override
	public int getEnchantmentValue() {
		return 10;
	}

	/** エンチャントテーブルで武器エンチャント (鋭さ等) を許可する */
	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
		if (enchantment.category == EnchantmentCategory.WEAPON) return true;
		return super.canApplyAtEnchantingTable(stack, enchantment);
	}
}
