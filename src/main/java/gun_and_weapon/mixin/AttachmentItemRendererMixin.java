package gun_and_weapon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gun_and_weapon.GunAndWeaponMod;

/**
 * TACZ はインベントリ (GUI) 内のアタッチメントを常に平面のスロットアイコンで
 * 描画する (AttachmentItemRenderer が GUI コンテキストを decal 描画に固定)。
 * このミックスインで gun_and_weapon のアタッチメントに限り、GUI 内でも
 * 3D ジオモデルを斜め見下ろしで描画する。
 */
@Mixin(value = AttachmentItemRenderer.class, remap = false)
public abstract class AttachmentItemRendererMixin {
	private static final ResourceLocation BAMBOO_STOCK =
			new ResourceLocation("gun_and_weapon", "bamboo_shoot_stock");

	/** GUI 内での傾き (バニラのブロックアイテム風の斜め見下ろし) */
	private static final float GUI_PITCH_DEG = -30f;
	private static final float GUI_YAW_DEG = 45f;
	/** スロット内での大きさ。 */
	private static final float GUI_SCALE = 2.0f;
	/** 長い竹ストック専用。64pxスロットから葉先がはみ出さない倍率。 */
	private static final float BAMBOO_GUI_SCALE = 1.25f;
	/**
	 * モデル中身の中心位置 (エンティティモデル座標、ブロック単位)。
	 * Bedrock ジオは y' = 24 - geoY で読み込まれるため、geo原点付近に作った
	 * モデルの中身は y' ≈ 23px = 1.44 ブロックに位置する。
	 * 回転前にこの分を原点へ引き戻してから回すことで、スロット中央に収まる。
	 */
	private static final float CONTENT_CENTER_Y = 23f / 16f;

	@Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true, remap = true, require = 0)
	private void gunAndWeapon$render3dInGui(ItemStack stack, ItemDisplayContext displayContext,
			PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
		if (displayContext != ItemDisplayContext.GUI) return;
		IAttachment iAttachment = IAttachment.getIAttachmentOrNull(stack);
		if (iAttachment == null) return;
		ResourceLocation id = iAttachment.getAttachmentId(stack);
		if (id == null || !GunAndWeaponMod.MODID.equals(id.getNamespace())) return;

		TimelessAPI.getClientAttachmentIndex(id).ifPresent(index -> {
			BedrockAttachmentModel model = index.getAttachmentModel();
			ResourceLocation texture = index.getModelTexture();
			if (model == null || texture == null) return;
			poseStack.pushPose();
			// スロット中央 (単位立方体の中心) へ配置
			boolean bamboo = BAMBOO_STOCK.equals(id);
			// 竹は細長く、通常の原点では右上へ外れるため専用の画面位置を使う。
			poseStack.translate(bamboo ? 0.32 : 0.5, bamboo ? 0.60 : 0.5, 0.5);
			// Bedrock (y下向き) → GUI (y上向き) の反転 + 拡大
			float guiScale = bamboo ? BAMBOO_GUI_SCALE : GUI_SCALE;
			poseStack.scale(-guiScale, -guiScale, guiScale);
			// 斜め見下ろし (この時点ではモデル中心が原点に来ているので中心周りに回る)
			poseStack.mulPose(Axis.XP.rotationDegrees(GUI_PITCH_DEG));
			poseStack.mulPose(Axis.YP.rotationDegrees(GUI_YAW_DEG));
			// モデル中身の中心を原点へ引き戻す (最後に書く = 頂点に最初に適用される)
			// 長い竹は根元ではなく全長の中央をスロット中央へ合わせる。
			poseStack.translate(0, -CONTENT_CENTER_Y,
					bamboo ? -0.57 : 0);
			model.render(stack, ItemStack.EMPTY, poseStack, ItemDisplayContext.GUI,
					RenderType.entityCutout(texture), light, overlay);
			poseStack.popPose();
			ci.cancel();
		});
	}
}
