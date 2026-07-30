package gun_and_weapon.util;

import com.tacz.guns.api.item.IGun;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import the_four_primitives_and_weapons.damage.ElementType;
import the_four_primitives_and_weapons.damage.ElementalDamageUtils;

/**
 * 「撃った銃に載っている MAW の属性」を解くための共通処理。
 *
 * <p>サーバー側の軌跡パーティクル
 * ({@link gun_and_weapon.event.ElementalBulletTrailHandler}) と
 * クライアント側の曳光弾の着色
 * ({@code gun_and_weapon.mixin.BulletTracerColorMixin}) の両方から使う。
 * どちらも同じ銃・同じ属性を指す必要があるので判定はここに一本化する。</p>
 *
 * <p>MAW が無い / API が変わった環境では常に {@link ElementType#NONE} を返し、
 * 演出そのものが無効になるだけで機能は壊れない。</p>
 */
public final class GunElements {

	private GunElements() {}

	/**
	 * 弾の gun ID と一致する手持ちの銃を探す。
	 * (オフハンド側から撃つ場合があるため、メイン → オフの順で照合する)
	 *
	 * @return 一致する銃。 見つからなければメインハンド
	 */
	public static ItemStack findGun(LivingEntity shooter, ResourceLocation gunId) {
		ItemStack main = shooter.getMainHandItem();
		if (isGunWithId(main, gunId)) return main;
		ItemStack off = shooter.getOffhandItem();
		if (isGunWithId(off, gunId)) return off;
		return main;
	}

	private static boolean isGunWithId(ItemStack stack, ResourceLocation gunId) {
		if (gunId == null || stack.isEmpty()) return false;
		IGun iGun = IGun.getIGunOrNull(stack);
		return iGun != null && gunId.equals(iGun.getGunId(stack));
	}

	/**
	 * 銃の主属性。 火+魂の 1:1 ペアは MAW 側で燐火に統合されるため
	 * {@code getEffectiveElementType} を使う。
	 */
	public static ElementType primary(ItemStack gun) {
		try {
			ElementType type = ElementalDamageUtils.getEffectiveElementType(gun);
			return type == null ? ElementType.NONE : type;
		} catch (NoClassDefFoundError | NoSuchMethodError e) {
			return ElementType.NONE;
		}
	}

	/**
	 * 銃の副属性 (MAW の属性ペア)。 燐火に統合済みの場合は単色として扱うので
	 * {@link ElementType#NONE} を返す。
	 */
	public static ElementType secondary(ItemStack gun) {
		try {
			if (ElementalDamageUtils.getEffectiveElementType(gun) == ElementType.SOUL_FIRE) return ElementType.NONE;
			ElementType type = ElementalDamageUtils.getSecondaryElementType(gun);
			return type == null ? ElementType.NONE : type;
		} catch (NoClassDefFoundError | NoSuchMethodError e) {
			return ElementType.NONE;
		}
	}
}
