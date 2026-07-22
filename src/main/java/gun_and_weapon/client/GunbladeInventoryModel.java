package gun_and_weapon.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeItem;
import gun_and_weapon.item.GunbladeModeSwitch;

import javax.annotation.Nullable;

/**
 * ガンブレード統合アイテムのモデル切替。
 *
 * アイテムモデル (gunblade_sword.json = 剣の3Dモデル) をラッパーで包み:
 *   - 近接モード: 常に剣の3Dモデル (バニラ描画・全コンテキスト)
 *   - 射撃モード: GUI/インベントリ = 剣の3Dモデル、
 *                 手持ち・地面・額縁など = TACZ レンダラー (geoモデル)
 *
 * 旧形式 (tacz:modern_kinetic_gun) のガンブレードも GUI で剣モデル表示にする。
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GunbladeInventoryModel {

	private GunbladeInventoryModel() {}

	@SubscribeEvent
	public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
		var models = event.getModels();

		// 統合アイテム: 射撃モードの時だけ手持ち等を TACZ レンダラーへ
		var bladeKey = new ModelResourceLocation(GunAndWeaponMod.MODID, "gunblade_sword", "inventory");
		BakedModel swordModel = models.get(bladeKey);
		if (swordModel != null) {
			models.put(bladeKey, new UnifiedModelWrapper(swordModel));
		}

		// 旧形式 (tacz:modern_kinetic_gun) の GUI 表示も剣モデルに
		var gunKey = new ModelResourceLocation("tacz", "modern_kinetic_gun", "inventory");
		BakedModel gunModel = models.get(gunKey);
		if (gunModel != null && swordModel != null) {
			models.put(gunKey, new LegacyGunModelWrapper(gunModel, swordModel));
		}
		GunAndWeaponMod.LOGGER.info("Gunblade model wrappers installed");
	}

	/** 委譲ベース */
	private static class Delegate implements BakedModel {
		protected final BakedModel original;

		Delegate(BakedModel original) {
			this.original = original;
		}

		@Override
		public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
				@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
			return original.getQuads(state, side, rand);
		}

		@Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
		@Override public boolean isGui3d() { return original.isGui3d(); }
		@Override public boolean usesBlockLight() { return original.usesBlockLight(); }
		@Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
		@Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
		@Override public ItemOverrides getOverrides() { return original.getOverrides(); }
		@Override public ItemTransforms getTransforms() { return original.getTransforms(); }
	}

	/** BEWLR (TACZ レンダラー) に描画させるためのフラグモデル */
	private static class CustomRendererFlag extends Delegate {
		CustomRendererFlag(BakedModel original) {
			super(original);
		}

		@Override
		public boolean isCustomRenderer() {
			return true;
		}

		@Override
		public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean applyLeftHandTransform) {
			return this;
		}
	}

	/** 統合アイテム用: モードとコンテキストで描画先を切り替える */
	private static class UnifiedModelWrapper extends Delegate {
		private final ItemOverrides overrides;
		private final BakedModel rangedModel;

		UnifiedModelWrapper(BakedModel swordModel) {
			super(swordModel);
			BakedModel taczFlag = new CustomRendererFlag(swordModel);
			// 射撃モード: GUIは剣、その他 (手持ち/地面/額縁...) は TACZ レンダラー
			BakedModel rangedSwitching = new Delegate(swordModel) {
				@Override
				public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean applyLeftHandTransform) {
					if (context == ItemDisplayContext.GUI) {
						return original.applyTransform(context, poseStack, applyLeftHandTransform);
					}
					return taczFlag;
				}
			};
			this.rangedModel = rangedSwitching;
			this.overrides = new ItemOverrides() {
				@Override
				public BakedModel resolve(BakedModel model, ItemStack stack,
						@Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
					if (stack.getItem() instanceof GunbladeItem && GunbladeItem.isRanged(stack)) {
						return rangedModel;
					}
					return swordModel; // 近接モード: 剣モデルそのまま
				}
			};
		}

		@Override
		public ItemOverrides getOverrides() {
			return overrides;
		}
	}

	/** 旧形式 (tacz:modern_kinetic_gun) 用: GUI だけ剣モデル */
	private static class LegacyGunModelWrapper extends Delegate {
		private final ItemOverrides overrides;

		LegacyGunModelWrapper(BakedModel original, BakedModel swordModel) {
			super(original);
			this.overrides = new ItemOverrides() {
				@Override
				public BakedModel resolve(BakedModel model, ItemStack stack,
						@Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
					BakedModel resolved = original.getOverrides().resolve(original, stack, level, entity, seed);
					if (resolved == null) resolved = original;
					if (GunbladeModeSwitch.isLegacyGunForm(stack)) {
						BakedModel base = resolved;
						return new Delegate(base) {
							@Override
							public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean applyLeftHandTransform) {
								if (context == ItemDisplayContext.GUI) {
									return swordModel.applyTransform(context, poseStack, applyLeftHandTransform);
								}
								return base.applyTransform(context, poseStack, applyLeftHandTransform);
							}
						};
					}
					return resolved;
				}
			};
		}

		@Override
		public ItemOverrides getOverrides() {
			return overrides;
		}
	}
}
