package gun_and_weapon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeItem;
import gun_and_weapon.item.GunbladeModeSwitch;
import gun_and_weapon.network.FireModeMessage;

/**
 * TACZ は IGun アイテムを持っている間、バニラの左クリック攻撃/右クリック使用を
 * キャンセルする (ClientPreventGunClick)。ガンブレードの近接モードでは
 * 剣として殴りたい/バレットステップしたいので、LOWEST 優先度で
 * キャンセルを取り消して通常のクリック動作を復元する。
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT)
public final class GunbladeClientInput {

	private GunbladeClientInput() {}

	/** 長押しでバーストに切り替わるまでの時間 (tick)。20tick = 1秒。 */
	private static final int BURST_HOLD_TICKS = 16;
	private static int attackHeldTicks = 0;
	private static boolean burstActive = false;

	/**
	 * 射撃モードで左クリックを {@link #BURST_HOLD_TICKS} 以上長押しすると
	 * 発射モードを BURST に切り替え、離すと SEMI に戻す。
	 * → 軽く押す = セミオート単発 / 長押し = バースト連射。
	 */
	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		ItemStack mainHand = mc.player.getMainHandItem();
		boolean isRangedGunblade = mainHand.getItem() instanceof GunbladeItem
				&& GunbladeItem.isRanged(mainHand)
				&& GunbladeModeSwitch.isGunbladeGun(mainHand);

		if (!isRangedGunblade || mc.screen != null) {
			// 対象外 / GUI を開いた → バースト解除
			if (burstActive) {
				GunAndWeaponMod.PACKET_HANDLER.sendToServer(new FireModeMessage(false));
				burstActive = false;
			}
			attackHeldTicks = 0;
			return;
		}

		if (mc.options.keyAttack.isDown()) {
			attackHeldTicks++;
			if (attackHeldTicks == BURST_HOLD_TICKS && !burstActive) {
				burstActive = true;
				GunAndWeaponMod.PACKET_HANDLER.sendToServer(new FireModeMessage(true));
				// 切替のフィードバック音
				mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.6f);
			}
		} else {
			if (burstActive) {
				GunAndWeaponMod.PACKET_HANDLER.sendToServer(new FireModeMessage(false));
				burstActive = false;
			}
			attackHeldTicks = 0;
		}
	}

	/**
	 * TACZ は IGun を持っていると RenderHandEvent を無条件キャンセルして
	 * 自前で銃を描画する。近接モード (ダミー銃ID) では TACZ は何も描かないため
	 * 手が空になってしまう → キャンセルを取り消してバニラ描画 (剣モデル) を復元。
	 */
	/**
	 * TACZ は IGun を持っていると バニラのクロスヘア (= 攻撃ゲージ) をキャンセルする
	 * (RenderCrosshairEvent、instanceof IGun だけで判定し GunId は見ない)。
	 * 近接モードでは剣として攻撃ゲージを表示したいので、キャンセルを取り消す。
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onRenderCrosshair(RenderGuiOverlayEvent.Pre event) {
		if (!event.isCanceled()) return;
		if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		ItemStack mainHand = mc.player.getMainHandItem();
		if (mainHand.getItem() instanceof GunbladeItem && GunbladeItem.isMelee(mainHand)) {
			event.setCanceled(false);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
		if (!event.isCanceled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		ItemStack stack = event.getItemStack();
		if (stack.getItem() instanceof GunbladeItem && GunbladeItem.isMelee(stack)) {
			event.setCanceled(false);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
		if (!event.isCanceled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		ItemStack mainHand = mc.player.getMainHandItem();
		if (mainHand.getItem() instanceof GunbladeItem && GunbladeItem.isMelee(mainHand)) {
			event.setCanceled(false);
			event.setSwingHand(true);
		}
	}
}
