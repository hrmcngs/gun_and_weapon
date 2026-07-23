package gun_and_weapon.event;

import java.util.Map;
import java.util.WeakHashMap;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.shooter.ShooterDataHolder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeItem;

/**
 * リロード時間を「実際に見えている装填アニメの長さ」に同期させる。
 *
 * ガンブレードのリロードは Lua ステートマシン (gunblade_state_machine.lua) が
 *   intro → 装填プッシュ×N → end
 * を組み立てる。プッシュ回数 N は不足弾数とアタッチメントで変わる:
 *   未装着=1発ずつ / 帯ストリップ=2発ずつ / 円スピードローダー=4発ずつ
 *
 * 一方、TACZ サーバー側のリロード時間は gun data の固定値
 * (空 3.4s / 戦術 2.6s) しか使えない。そこでリロード中の
 * {@link ShooterDataHolder#reloadTimestamp} を毎tickずらし、
 * 実効リロード時間 = アニメの合計時間 になるよう加速/減速する
 * (クライアント/サーバー両方の tick で同じ量をずらして同期を保つ)。
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID)
public final class SpeedloaderReloadHandler {

	private SpeedloaderReloadHandler() {}

	private static final ResourceLocation SPEEDLOADER = new ResourceLocation(GunAndWeaponMod.MODID, "speedloader");
	private static final ResourceLocation SPEED_STRIP = new ResourceLocation(GunAndWeaponMod.MODID, "speed_strip");

	// ===== gunblade_data.json / gunblade.animation.json と対応する定数 =====
	/** 装弾数 (gunblade_data.json の ammo_amount) */
	private static final int MAX_AMMO = 8;
	/** gun data のリロード cooldown (空 / 戦術) [秒] */
	private static final double DATA_COOLDOWN_EMPTY = 3.4;
	private static final double DATA_COOLDOWN_TACTICAL = 2.6;
	/** アニメの長さ [秒] (gunblade.animation.json と一致させること) */
	private static final double INTRO_EMPTY = 1.0;
	private static final double INTRO_TACTICAL = 0.5;
	private static final double PUSH_1 = 0.4;
	private static final double PUSH_2 = 0.55;
	private static final double PUSH_4 = 0.6;
	private static final double END_EMPTY = 0.7;
	private static final double END_TACTICAL = 0.6;

	/** リロード中プレイヤーごとの補正値 (client/server それぞれの Player インスタンスで別管理) */
	private static final Map<Player, Sync> ACTIVE = new WeakHashMap<>();

	private static final class Sync {
		double extraPerTickMs;
		double carry;
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Player player = event.player;
		ItemStack mainHand = player.getMainHandItem();
		if (!(mainHand.getItem() instanceof GunbladeItem) || !GunbladeItem.isRanged(mainHand)) {
			ACTIVE.remove(player);
			return;
		}
		try {
			ShooterDataHolder holder = IGunOperator.fromLivingEntity(player).getDataHolder();
			if (holder == null || holder.reloadStateType == null || !holder.reloadStateType.isReloading()) {
				ACTIVE.remove(player);
				return;
			}
			Sync sync = ACTIVE.get(player);
			if (sync == null) {
				sync = computeSync(mainHand);
				if (sync == null) return;
				ACTIVE.put(player, sync);
			}
			// 端数を持ち越しつつ timestamp をずらす (負 = 減速も可)
			sync.carry += sync.extraPerTickMs;
			long shift = (long) Math.floor(sync.carry);
			sync.carry -= shift;
			holder.reloadTimestamp -= shift;
		} catch (NoClassDefFoundError | NoSuchMethodError ignored) {
			// TACZ 不在/内部APIが変わった場合は何もしない
		}
	}

	/** リロード開始時点の不足弾数とアタッチメントから、目標リロード時間と毎tick補正量を求める */
	private static Sync computeSync(ItemStack gun) {
		IGun iGun = IGun.getIGunOrNull(gun);
		if (iGun == null) return null;
		int ammo = iGun.getCurrentAmmoCount(gun);
		int need = MAX_AMMO - ammo;
		if (need <= 0) return null;

		ResourceLocation attachment = iGun.getAttachmentId(gun, AttachmentType.EXTENDED_MAG);
		double push;
		int perPush;
		if (SPEEDLOADER.equals(attachment)) {
			push = PUSH_4; perPush = 4;
		} else if (SPEED_STRIP.equals(attachment)) {
			push = PUSH_2; perPush = 2;
		} else {
			push = PUSH_1; perPush = 1;
		}
		int pushes = (need + perPush - 1) / perPush;

		boolean empty = ammo <= 0;
		double visual = (empty ? INTRO_EMPTY : INTRO_TACTICAL)
				+ pushes * push
				+ (empty ? END_EMPTY : END_TACTICAL);
		double dataCooldown = empty ? DATA_COOLDOWN_EMPTY : DATA_COOLDOWN_TACTICAL;

		// サーバー経過時間を rate 倍で進める → 実効時間 = visual
		double rate = dataCooldown / visual;
		Sync sync = new Sync();
		sync.extraPerTickMs = 50.0 * (rate - 1.0);
		return sync;
	}
}
