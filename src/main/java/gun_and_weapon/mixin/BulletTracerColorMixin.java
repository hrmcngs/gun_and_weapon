package gun_and_weapon.mixin;

import java.util.Optional;

import com.tacz.guns.entity.EntityKineticBullet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import the_four_primitives_and_weapons.damage.ElementType;
import the_four_primitives_and_weapons.damage.ElementalParticles;

import gun_and_weapon.util.GunElements;

/**
 * MAW の属性が載っている銃で撃った弾は、TACZ 本来の曳光弾 (トレーサー) を
 * その属性の色で描く。
 *
 * <p>TACZ は {@code tacz:tracer_override} という persistent data による色差し替えの
 * 口を用意しているが、これはクライアント側のエンティティにしか効かない
 * (サーバーの persistent data は同期されない)。 そのため色はここで直接返す。
 * 弾の持ち主 ( = 撃った人 ) はクライアントにも同期されているので、
 * その手持ちの銃の NBT から属性を引けば、追加の通信は要らない。</p>
 *
 * <p>{@code isTracerAmmo} も併せて true にする。 TACZ はガンパックの
 * {@code tracer_count_interval} が 0 の銃 ( ガンブレードもこれ ) では
 * 曳光弾を一切描かないため、これをしないと色を返しても何も出ない。
 * 属性が無い銃には触らないので、通常の TACZ の見た目は変わらない。</p>
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class BulletTracerColorMixin {

	/** 解決済みフラグ。 属性なしの結果もキャッシュする (毎フレーム NBT を引かないため)。 */
	@Unique
	private boolean gunAndWeapon$elementResolved;

	/** 属性色 (RGBA)。 属性が無ければ null。 */
	@Unique
	private float[] gunAndWeapon$tracerColor;

	@Inject(method = "getTracerColorOverride", at = @At("HEAD"), cancellable = true, require = 0)
	private void gunAndWeapon$elementalTracerColor(CallbackInfoReturnable<Optional<float[]>> cir) {
		float[] color = gunAndWeapon$elementColor();
		if (color != null) cir.setReturnValue(Optional.of(color));
	}

	@Inject(method = "isTracerAmmo", at = @At("HEAD"), cancellable = true, require = 0)
	private void gunAndWeapon$forceElementalTracer(CallbackInfoReturnable<Boolean> cir) {
		if (gunAndWeapon$elementColor() != null) cir.setReturnValue(true);
	}

	@Unique
	private float[] gunAndWeapon$elementColor() {
		if (gunAndWeapon$elementResolved) return gunAndWeapon$tracerColor;

		EntityKineticBullet self = (EntityKineticBullet) (Object) this;
		// spawn 直後は readSpawnData がまだ owner を入れていないことがある。
		// 未解決のままにしておけば次のフレームで引き直せる。
		if (!(self.getOwner() instanceof LivingEntity shooter)) return null;

		ItemStack gun = GunElements.findGun(shooter, self.getGunId());
		ElementType type = GunElements.primary(gun);
		Vector3f color = type == ElementType.NONE ? null : ElementalParticles.colorOf(type);

		gunAndWeapon$tracerColor = color == null
				? null
				: new float[] { color.x(), color.y(), color.z(), 1.0f };
		gunAndWeapon$elementResolved = true;
		return gunAndWeapon$tracerColor;
	}
}
