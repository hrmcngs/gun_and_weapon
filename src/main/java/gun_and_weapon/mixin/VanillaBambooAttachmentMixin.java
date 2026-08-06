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
		poseStack.scale(0.21f, 0.21f, 0.21f);
		// 横倒しの竹を3本束ねて肩当てを作る。
		renderStockCane(minecraft, buffers, poseStack, stem,
				-0.55, 0.15, 0.45, 4, -2.0f, light);
		renderStockCane(minecraft, buffers, poseStack, stem,
				0.05, -0.5, 0.25, 4, 1.5f, light);
		renderStockCane(minecraft, buffers, poseStack, stem,
				0.58, 0.12, 0.4, 4, 3.0f, light);
		// 銃へ食い込む短い地下茎。左右へ開いて「根を張る」輪郭を作る。
		renderStockCane(minecraft, buffers, poseStack, stem,
				-0.35, -0.2, -0.2, 2, -28.0f, light);
		renderStockCane(minecraft, buffers, poseStack, stem,
				0.35, -0.25, -0.2, 2, 28.0f, light);

		// 画像のような密生株: 同じ地下茎から高さと傾きの違う竹を生やす。
		renderColumn(minecraft, buffers, poseStack, stem, smallLeaves, largeLeaves,
				0.0, 0.0, 0.0, 4, -2.0f, light);
		renderColumn(minecraft, buffers, poseStack, stem, smallLeaves, largeLeaves,
				-0.75, -0.15, 0.3, 3, -9.0f, light);
		renderColumn(minecraft, buffers, poseStack, stem, smallLeaves, largeLeaves,
				0.68, -0.05, 0.18, 4, 7.0f, light);
		renderColumn(minecraft, buffers, poseStack, stem, smallLeaves, largeLeaves,
				-0.12, -0.2, -0.68, 3, 5.0f, light);
		renderColumn(minecraft, buffers, poseStack, stem, smallLeaves, largeLeaves,
				0.35, -0.25, 0.72, 2, -12.0f, light);
		poseStack.popPose();
	}

	private static void renderStockCane(Minecraft minecraft, MultiBufferSource.BufferSource buffers,
			PoseStack poseStack, BlockState stem, double x, double y, double z,
			int length, float spread, int light) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.mulPose(Axis.YP.rotationDegrees(spread));
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
		for (int segment = 0; segment < length; segment++) {
			poseStack.pushPose();
			poseStack.translate(0, segment, 0);
			minecraft.getBlockRenderer().renderSingleBlock(
					stem, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void renderColumn(Minecraft minecraft, MultiBufferSource.BufferSource buffers,
			PoseStack poseStack, BlockState stem, BlockState smallLeaves, BlockState largeLeaves,
			double x, double y, double z, int height, float lean, int light) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.mulPose(Axis.ZP.rotationDegrees(lean));
		for (int level = 0; level < height; level++) {
			poseStack.pushPose();
			poseStack.translate(0, level, 0);
			BlockState state = level == height - 1
					? largeLeaves
					: (level == height - 2 ? smallLeaves : stem);
			minecraft.getBlockRenderer().renderSingleBlock(
					state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}
		poseStack.popPose();
	}
}
