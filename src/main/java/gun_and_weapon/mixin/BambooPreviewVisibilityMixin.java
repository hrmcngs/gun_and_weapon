package gun_and_weapon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blockbench用に転記したバニラ形状を実機では隠し、本物のブロックモデルだけを表示する。 */
@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class BambooPreviewVisibilityMixin {
	private static final ResourceLocation ID =
			new ResourceLocation("gun_and_weapon", "bamboo_shoot_stock");

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void gunAndWeapon$hidePreview(ItemStack attachment, ItemStack gun, PoseStack poseStack,
			ItemDisplayContext context, RenderType renderType, int light, int overlay, CallbackInfo ci) {
		IAttachment api = IAttachment.getIAttachmentOrNull(attachment);
		if (api == null || !ID.equals(api.getAttachmentId(attachment))) return;
		BedrockPart preview = ((BedrockAttachmentModel) (Object) this).getNode("preview_vanilla_bamboo");
		if (preview != null) preview.visible = false;
	}
}
