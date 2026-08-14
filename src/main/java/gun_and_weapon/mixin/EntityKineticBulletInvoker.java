package gun_and_weapon.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;

import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 壁通過後もTaCZ本来の弾丸命中処理を呼び出すためのInvoker。 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public interface EntityKineticBulletInvoker {
	@Invoker("onHitEntity")
	void gunAndWeapon$invokeOnHitEntity(TacHitResult hitResult, Vec3 start, Vec3 end);
}
