package gun_and_weapon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import gun_and_weapon.util.GunElements;

/**
 * MAW の属性が載っている銃のマズルフラッシュを属性色で描く。
 *
 * <p>TACZ の {@code MuzzleFlashRender.doRender} はフラッシュ板を 2 回
 * ( {@code entityTranslucent} + 発光の {@code energySwirl} ) 描いており、
 * どちらも色は {@code (1,1,1,1)} 固定。 その描画呼び出しを横取りして色だけ差し替える。</p>
 *
 * <p>マズルフラッシュは {@code MuzzleFlashRender.render} が {@code isSelf} で
 * 弾いているとおり<b>自分の銃にしか出ない</b>ので、色は
 * クライアント側プレイヤーのメインハンド ( = 撃った銃 ) から引けばよい。
 * 属性が無ければ何もしないので、通常の TACZ の見た目は変わらない。</p>
 *
 * <p>色 3 つを差し替えるだけなら {@code @ModifyArgs} が素直だが、
 * これは実行時に {@code org.spongepowered.asm.synthetic.args.Args$*} を生成する仕組みで、
 * この環境では {@code NoClassDefFoundError} になってクラッシュする。
 * そのため描画呼び出しごと {@code @Redirect} で受けている。</p>
 */
@Mixin(value = MuzzleFlashRender.class, remap = false)
public abstract class MuzzleFlashColorMixin {

	@Redirect(
			method = "doRender",
			at = @At(
					value = "INVOKE",
					target = "Lcom/tacz/guns/client/model/SlotModel;renderToBuffer("
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
					remap = true),
			require = 0)
	private static void gunAndWeapon$tintMuzzleFlash(SlotModel model, PoseStack poseStack, VertexConsumer buffer,
			int light, int overlay, float red, float green, float blue, float alpha) {
		float[] color = gunAndWeapon$elementColor();
		if (color != null) {
			red = color[0];
			green = color[1];
			blue = color[2];
		}
		model.renderToBuffer(poseStack, buffer, light, overlay, red, green, blue, alpha);
	}

	/** 撃った銃 ( = 自分のメインハンド ) の属性色。 属性が無ければ null。 */
	private static float[] gunAndWeapon$elementColor() {
		if (!gun_and_weapon.config.GunAndWeaponConfig.muzzleFlashColor) return null;
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : GunElements.emissiveColor(player.getMainHandItem());
	}
}
