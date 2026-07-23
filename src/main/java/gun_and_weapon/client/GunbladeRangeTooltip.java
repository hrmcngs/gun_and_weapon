package gun_and_weapon.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeItem;

/**
 * 本体MOD (MAW) の WeaponRangeTooltip と同じ見た目をガンブレードにも適用する。
 *
 * MAW側の処理は the_four_primitives_and_weapons 名前空間のアイテム限定のため、
 * gun_and_weapon のガンブレードには効かず、生の「+0.5 Entity Reach」行が出てしまう。
 * → ここで Entity/Block Reach の属性行を消し、代わりに MAW と同じ緑スタイルの
 *   「3.5 攻撃範囲」( 基礎リーチ3.0 + weapon_stats の attack_range ) を
 *   攻撃速度の行の直後に挿入する。翻訳キーも MAW のものを使うので表記が揃う。
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT)
public final class GunbladeRangeTooltip {

	private GunbladeRangeTooltip() {}

	/** 素の近接攻撃リーチ ( 表示上の基礎値、MAW の WeaponRangeTooltip と同値 )。 */
	private static final double BASE_REACH = 3.0;

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		try {
			ItemStack stack = event.getItemStack();
			if (stack.isEmpty() || !(stack.getItem() instanceof GunbladeItem)) return;
			if (!GunbladeItem.isMelee(stack)) return;

			List<Component> tip = event.getToolTip();

			// 自動追加された Reach 系の行を除去 ( 重複防止 )。
			String entityReachName = I18n.get(ForgeMod.ENTITY_REACH.get().getDescriptionId());
			String blockReachName = I18n.get(ForgeMod.BLOCK_REACH.get().getDescriptionId());
			tip.removeIf(c -> {
				String s = c.getString();
				return s.contains(entityReachName) || s.contains(blockReachName);
			});

			// 攻撃速度の行の直後に挿入する ( 無ければ表示しない )。
			String atkSpeedName = I18n.get(Attributes.ATTACK_SPEED.getDescriptionId());
			int idx = -1;
			for (int i = 0; i < tip.size(); i++) {
				if (tip.get(i).getString().contains(atkSpeedName)) { idx = i; break; }
			}
			if (idx < 0) return;

			double range = BASE_REACH + attackRangeBonus(stack);
			Component line = Component.translatable(
					"attribute.modifier.equals.0",
					ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(range),
					Component.translatable("attribute.name.the_four_primitives_and_weapons.attack_range"))
					.withStyle(ChatFormatting.DARK_GREEN);
			tip.add(idx + 1, line);
		} catch (Throwable ignored) {
			// no-op: ツールチップ描画は失敗しても無視 ( クラッシュ防止 )
		}
	}

	/** weapon_stats の attack_range ( 未設定=0 )。本体MODが無い場合も 0。 */
	private static double attackRangeBonus(ItemStack stack) {
		try {
			return the_four_primitives_and_weapons.skill.WeaponStatsRegistry.attackRangeBonus(stack);
		} catch (NoClassDefFoundError | NoSuchMethodError ignored) {
			return 0.0;
		}
	}
}
