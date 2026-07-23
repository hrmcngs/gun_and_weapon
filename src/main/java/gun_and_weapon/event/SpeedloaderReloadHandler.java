package gun_and_weapon.event;

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
 * スピードローダー系アタッチメントによるリロード短縮。
 *
 * TACZ 1.1.7 のアタッチメント modifier にはリロード時間の項目が無いため、
 * リロード中の {@link ShooterDataHolder#reloadTimestamp} を毎tick少しずつ
 * 過去にずらして経過時間を加速する (サーバー/クライアント両方の tick で
 * 同じ量をずらすので、弾数反映とアニメ状態遷移が揃って早まる)。
 *
 *   - 円状 (speedloader): 1tick+25ms → 実効 1.5倍速 (約33%短縮)
 *   - 帯状 (speed_strip): 1tick+6ms  → 実効 1.12倍速 (約10%短縮)
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID)
public final class SpeedloaderReloadHandler {

	private SpeedloaderReloadHandler() {}

	private static final ResourceLocation SPEEDLOADER = new ResourceLocation(GunAndWeaponMod.MODID, "speedloader");
	private static final ResourceLocation SPEED_STRIP = new ResourceLocation(GunAndWeaponMod.MODID, "speed_strip");

	/** 1tick (50ms) ごとに追加で経過させる時間 (ms) */
	private static final long SPEEDLOADER_EXTRA_MS = 25;
	private static final long SPEED_STRIP_EXTRA_MS = 6;

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Player player = event.player;
		ItemStack mainHand = player.getMainHandItem();
		if (!(mainHand.getItem() instanceof GunbladeItem)) return;
		if (!GunbladeItem.isRanged(mainHand)) return;
		try {
			IGunOperator operator = IGunOperator.fromLivingEntity(player);
			ShooterDataHolder holder = operator.getDataHolder();
			if (holder == null || holder.reloadStateType == null) return;
			if (!holder.reloadStateType.isReloading()) return;

			IGun iGun = IGun.getIGunOrNull(mainHand);
			if (iGun == null) return;
			ResourceLocation attachment = iGun.getAttachmentId(mainHand, AttachmentType.EXTENDED_MAG);
			long extra;
			if (SPEEDLOADER.equals(attachment)) {
				extra = SPEEDLOADER_EXTRA_MS;
			} else if (SPEED_STRIP.equals(attachment)) {
				extra = SPEED_STRIP_EXTRA_MS;
			} else {
				return;
			}
			holder.reloadTimestamp -= extra;
		} catch (NoClassDefFoundError | NoSuchMethodError ignored) {
			// TACZ 不在/内部APIが変わった場合は何もしない
		}
	}
}
