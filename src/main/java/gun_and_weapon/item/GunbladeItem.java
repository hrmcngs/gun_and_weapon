package gun_and_weapon.item;

import java.util.UUID;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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

	/** 近接モードの攻撃属性 UUID */
	private static final UUID MELEE_DAMAGE_UUID = UUID.fromString("5c9271fa-6f42-4c8b-9df2-7e1a63b3c101");
	private static final UUID MELEE_SPEED_UUID = UUID.fromString("5c9271fa-6f42-4c8b-9df2-7e1a63b3c102");
	private static final UUID MELEE_REACH_UUID = UUID.fromString("5c9271fa-6f42-4c8b-9df2-7e1a63b3c103");

	/** weapon_stats JSON が読めない場合の既定値 (元データパック準拠: 総攻撃力7 / 攻撃速度-2.4) */
	private static final double DEFAULT_ATTACK_DAMAGE = 7.0;
	private static final double DEFAULT_ATTACK_SPEED = -2.4;

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
		// 右クリックスロットに MAW の「回避」が設定されている場合はバレットステップを
		// 出さず、MAW の回避システムに任せる (二重発動防止)。
		if (isDodgeSelected(player, stack)) {
			return InteractionResultHolder.pass(stack);
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

	/**
	 * GunFireMode タグが未初期化 (UNKNOWN) だと、TACZ の ShootKey がクリックの度に
	 * 「Unknown fire mode, unable to shoot」をチャットへ出してしまう
	 * (近接モードの新品ガンブレードはタグが無いので毎回出る)。
	 * → UNKNOWN は SEMI として扱い、メッセージを抑止する。
	 */
	@Override
	public FireMode getFireMode(ItemStack stack) {
		FireMode mode = super.getFireMode(stack);
		return mode == FireMode.UNKNOWN ? FireMode.SEMI : mode;
	}

	@Override
	public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
		if (slot == EquipmentSlot.MAINHAND && isMelee(stack)) {
			return buildMeleeAttributes(stack);
		}
		return super.getAttributeModifiers(slot, stack);
	}

	/**
	 * 近接モードの属性を data/gun_and_weapon/weapon_stats/weapons.json から組み立てる。
	 *
	 * 本来これは本体MODの WeaponStatsMixin が全武器に対して行う処理だが、
	 * そのミックスインは mixins.json に登録されておらず適用されないため、
	 * ガンブレードは自前で JSON を読んで反映する
	 * (将来ミックスインが有効化されても同じ値で上書きされるだけなので競合しない)。
	 *
	 * JSON が読めない / 本体MODが無い場合は既定値にフォールバックする。
	 */
	private static Multimap<Attribute, AttributeModifier> buildMeleeAttributes(ItemStack stack) {
		double totalDamage = DEFAULT_ATTACK_DAMAGE;
		double speed = DEFAULT_ATTACK_SPEED;
		Double reach = null;

		try {
			var stats = the_four_primitives_and_weapons.skill.WeaponStatsRegistry.getStats(stack);
			if (stats != null) {
				if (!Float.isNaN(stats.attackDamage)) {
					totalDamage = stats.attackDamage;
				} else if (!Float.isNaN(stats.damageBonus)) {
					totalDamage = DEFAULT_ATTACK_DAMAGE + stats.damageBonus;
				}
				if (!Float.isNaN(stats.attackSpeed)) speed = stats.attackSpeed;
				if (!Float.isNaN(stats.attackRange)) reach = (double) stats.attackRange;
			}
		} catch (NoClassDefFoundError | NoSuchMethodError ignored) {
			// 本体MODが無い/APIが変わった場合は既定値のまま
		}

		ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
		// バニラは「基礎1 + modifier」で総攻撃力になる
		builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
				MELEE_DAMAGE_UUID, "Gunblade melee damage", totalDamage - 1.0, AttributeModifier.Operation.ADDITION));
		builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
				MELEE_SPEED_UUID, "Gunblade melee speed", speed, AttributeModifier.Operation.ADDITION));
		if (reach != null && reach != 0.0) {
			builder.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
					MELEE_REACH_UUID, "Gunblade melee reach", reach, AttributeModifier.Operation.ADDITION));
		}
		return builder.build();
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

	/**
	 * MAW のスキル画面で右クリックスロットに "dodge" (回避) が
	 * 設定されているか。設定されていればバレットステップは出さない。
	 * MAW が無い環境では常に false。
	 */
	private static boolean isDodgeSelected(Player player, ItemStack stack) {
		try {
			return "dodge".equals(
					the_four_primitives_and_weapons.events.DodgeAndBattouHandler
							.resolveRightClickMotion(player, stack));
		} catch (NoClassDefFoundError | NoSuchMethodError e) {
			return false;
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
