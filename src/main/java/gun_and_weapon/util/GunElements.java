package gun_and_weapon.util;

import com.tacz.guns.api.item.IGun;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector3f;

import the_four_primitives_and_weapons.damage.ElementType;
import the_four_primitives_and_weapons.damage.ElementalDamageUtils;
import the_four_primitives_and_weapons.damage.ElementalParticles;

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

	/** 発光描画に乗せるときの最低彩度。 */
	private static final float MIN_SATURATION = 0.85f;

	/**
	 * 発光描画 ( 曳光弾 / マズルフラッシュ ) に乗せる属性色 (RGBA)。 属性が無ければ null。
	 *
	 * <p>これらは白いテクスチャに色を<b>乗算</b>して描かれるので、渡す色がそのまま見た目になる。
	 * ところが MAW の属性色は dust 用の淡い色で、聖 {@code (1.00, 0.97, 0.72)} や
	 * 雷 {@code (0.80, 0.90, 1.00)} のように<b>ほぼ白</b>のものが多く、そのまま乗算しても
	 * 白いフラッシュのままで「色が変わっていない」ように見える
	 * ( 明度を上げるだけでも同じ。 白に近い色は明るくしても白 )。</p>
	 *
	 * <p>そこで色相は保ったまま、彩度を {@link #MIN_SATURATION} 以上・明度を最大に引き上げてから渡す。
	 * 聖なら鮮やかな金、雷なら鮮やかな青、闇なら鮮やかな紫になる。</p>
	 */
	public static float[] emissiveColor(ItemStack gun) {
		ElementType type = primary(gun);
		if (type == ElementType.NONE) return null;
		Vector3f color;
		try {
			color = ElementalParticles.colorOf(type);
		} catch (NoClassDefFoundError | NoSuchMethodError e) {
			return null;
		}
		if (color == null) return null;
		return vivid(color);
	}

	/** 色相を保ったまま彩度・明度を上げる (HSV で S >= {@link #MIN_SATURATION}, V = 1)。 */
	private static float[] vivid(Vector3f color) {
		float r = color.x();
		float g = color.y();
		float b = color.z();
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		// 真っ黒 / 無彩色は色相が無いので白のまま (乗算しても元の見た目を壊さない)
		if (max <= 0.001f || delta <= 0.001f) return new float[] { 1.0f, 1.0f, 1.0f, 1.0f };

		float saturation = Math.max(delta / max, MIN_SATURATION);
		// HSV の逆算: 明度 1 で ch = 1 - S * (max - ch) / delta ( (max-ch)/delta が色相を決める比 )
		return new float[] {
				1.0f - saturation * (max - r) / delta,
				1.0f - saturation * (max - g) / delta,
				1.0f - saturation * (max - b) / delta,
				1.0f };
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
