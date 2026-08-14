package gun_and_weapon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.functional.AttachmentRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 筍ストックの根元から、偽物ではなくバニラの竹ブロックモデルを直接描画する。 */
@Mixin(value = AttachmentRender.class, remap = false)
public abstract class VanillaBambooAttachmentMixin {

	private static final ResourceLocation BAMBOO_SHOOT_STOCK =
			new ResourceLocation("gun_and_weapon", "bamboo_shoot_stock");

	@Inject(method = "renderAttachment", at = @At("TAIL"), require = 0)
	private static void gunAndWeapon$renderVanillaBamboo(ItemStack attachment, ItemStack gun,
			PoseStack poseStack, ItemDisplayContext context, int light, int overlay, CallbackInfo ci) {
		IAttachment api = IAttachment.getIAttachmentOrNull(attachment);
		if (api == null || !BAMBOO_SHOOT_STOCK.equals(api.getAttachmentId(attachment))) return;

		Minecraft minecraft = Minecraft.getInstance();
		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
		BlockState stem = Blocks.BAMBOO.defaultBlockState()
				.setValue(BambooStalkBlock.AGE, 1)
				.setValue(BambooStalkBlock.STAGE, 0)
				.setValue(BambooStalkBlock.LEAVES, BambooLeaves.NONE);
		BlockState smallLeaves = stem.setValue(BambooStalkBlock.LEAVES, BambooLeaves.SMALL);
		BlockState largeLeaves = stem
				.setValue(BambooStalkBlock.LEAVES, BambooLeaves.LARGE)
				.setValue(BambooStalkBlock.STAGE, 1);

		poseStack.pushPose();
		// ストックも植物も、すべて Minecraft 本体の竹ブロックモデルで構成する。
		poseStack.translate(-0.08, -0.02, -0.14);
		poseStack.scale(0.38f, 0.38f, 0.38f);
		// 一本だけの竹を横向きにし、後半の節からバニラの葉を生やす。
		renderStockCane(minecraft, buffers, poseStack, stem, smallLeaves, largeLeaves,
				0.0, -0.15, 0.10, 3, 0.0f, light);
		poseStack.popPose();
	}

	private static void renderStockCane(Minecraft minecraft, MultiBufferSource.BufferSource buffers,
			PoseStack poseStack, BlockState stem, BlockState smallLeaves, BlockState largeLeaves,
			double x, double y, double z,
			int length, float spread, int light) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.mulPose(Axis.YP.rotationDegrees(spread));
		// stock 接続点から銃の後方へ伸ばす（+90度では銃本体側へ食い込む）。
		poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
		for (int segment = 0; segment < length; segment++) {
			poseStack.pushPose();
			poseStack.translate(0, segment, 0);
			BlockState state = segment == length - 1
					? largeLeaves
					: (segment == length - 2 ? smallLeaves : stem);
			minecraft.getBlockRenderer().renderSingleBlock(
					state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}
		poseStack.popPose();
	}
}
