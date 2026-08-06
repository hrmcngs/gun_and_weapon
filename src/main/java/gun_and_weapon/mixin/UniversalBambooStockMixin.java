package gun_and_weapon.mixin;

import com.tacz.guns.util.AllowAttachmentTagMatcher;

import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 筍ストックを、ガンパック側の個別 allow_attachments 設定に依存せず装着可能にする。
 *
 * <p>スロットの種類までは増やさない。銃モデルに stock ボーンがなく、そもそも
 * stock スロットを提供しない銃へ強制装着するとモデル位置を決められないため、
 * 「全ての stock 対応銃」に対してのみ汎用化する。</p>
 */
@Mixin(value = AllowAttachmentTagMatcher.class, remap = false)
public abstract class UniversalBambooStockMixin {

	private static final ResourceLocation BAMBOO_SHOOT_STOCK =
			new ResourceLocation("gun_and_weapon", "bamboo_shoot_stock");

	@Inject(method = "match", at = @At("HEAD"), cancellable = true, require = 0)
	private static void gunAndWeapon$allowBambooStockOnEveryStockGun(
			ResourceLocation gunId, ResourceLocation attachmentId,
			CallbackInfoReturnable<Boolean> cir) {
		if (BAMBOO_SHOOT_STOCK.equals(attachmentId)) {
			cir.setReturnValue(true);
		}
	}
}
