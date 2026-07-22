package gun_and_weapon.init;

import net.minecraft.world.entity.player.Player;

import the_four_primitives_and_weapons.skill.PlayerSkillData.AttackSlot;
import the_four_primitives_and_weapons.skill.SkillRegistry;
import the_four_primitives_and_weapons.skill.SkillRegistry.MotionCategory;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.attack.GunbladeAttacks;

import java.util.EnumSet;
import java.util.Set;

/**
 * 本体MOD「The four primitives and Weapons」のスキルシステムへ
 * ガンブレードの特殊技を登録する。
 *
 * ここで登録したモーションIDを
 *   data/gun_and_weapon/weapon_types/weapons.json
 * の motions / special_weapons に書くことで、スキル画面のスロットに
 * 割り当てられるようになる。
 */
public class GunAndWeaponSkills {

	/** チャージスマッシュのモーションID (weapons.json と一致させること) */
	public static final String CHARGE_SMASH = "gun_and_weapon:charge_smash";

	/**
	 * 特殊技を登録する。FMLCommonSetupEvent から一度だけ呼ぶ。
	 * 本体MODが無い環境でも落ちないよう NoClassDefFoundError を握りつぶす。
	 */
	public static void register() {
		try {
			// 右クリック / チャージ / 通常コンボの全スロットに割り当て可能にする。
			Set<AttackSlot> slots = EnumSet.of(
					AttackSlot.RIGHT_CLICK, AttackSlot.CHARGED,
					AttackSlot.FIRST_HIT, AttackSlot.SECOND_HIT, AttackSlot.THIRD_HIT);

			SkillRegistry.register(CHARGE_SMASH,
					"チャージスマッシュ",
					"残弾を全て消費し、前方へ炎のリングと爆風を放つ (消費弾数でダメージ上昇 / 残弾0では不発)",
					MotionCategory.SPECIAL, slots, "GunbladeItem",
					(Player player, float chargePercent) ->
							GunbladeAttacks.executeChargeSmash(player.level(), player));

			GunAndWeaponMod.LOGGER.info("Registered MAW skill: {}", CHARGE_SMASH);
		} catch (NoClassDefFoundError e) {
			GunAndWeaponMod.LOGGER.warn("The four primitives and Weapons not found, skipping skill registration");
		}
	}
}
