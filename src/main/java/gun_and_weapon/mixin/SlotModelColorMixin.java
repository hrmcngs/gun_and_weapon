package gun_and_weapon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;

import net.minecraft.world.item.ItemDisplayContext;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link SlotModel#renderToBuffer} に渡した色を実際に反映させる。
 *
 * <p>TACZ の {@code SlotModel} は色 {@code (r, g, b, a)} を引数で受け取るのに、
 * 中では色を取らない {@code BedrockPart.render(pose, ctx, buffer, light, overlay)} を呼んでおり、
 * そちらは内部で白 {@code (1,1,1,1)} を渡す。 つまり<b>指定した色は捨てられる</b>。
 * マズルフラッシュ ({@code MuzzleFlashRender.doRender}) はこの経路で描かれるため、
 * 呼び出し側で色を差し替えても見た目が白いままだった。</p>
 *
 * <p>ここでは色付きの {@code BedrockPart.render(..., r, g, b, a)} に差し替えて描く。
 * 白 ( 既定色 ) が渡された場合は何もしないので、スロットアイコンなど
 * 色を指定していない他の描画は TACZ 本来の経路のまま。</p>
 */
@Mixin(value = SlotModel.class, remap = false)
public abstract class SlotModelColorMixin {

	@Shadow
	@Final
	private BedrockPart bone;

	@Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true, remap = true, require = 0)
	private void gunAndWeapon$applyColor(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
			float red, float green, float blue, float alpha, CallbackInfo ci) {
		// 色指定なし ( 白 ) は TACZ 本来の挙動に任せる
		if (red == 1.0f && green == 1.0f && blue == 1.0f && alpha == 1.0f) return;
		if (bone == null) return;
		bone.render(poseStack, ItemDisplayContext.GUI, buffer, light, overlay, red, green, blue, alpha);
		ci.cancel();
	}
}
