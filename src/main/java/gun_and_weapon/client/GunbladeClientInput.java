package gun_and_weapon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeItem;

/**
 * TACZ は IGun アイテムを持っている間、バニラの左クリック攻撃/右クリック使用を
 * キャンセルする (ClientPreventGunClick)。ガンブレードの近接モードでは
 * 剣として殴りたい/バレットステップしたいので、LOWEST 優先度で
 * キャンセルを取り消して通常のクリック動作を復元する。
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT)
public final class GunbladeClientInput {

	private GunbladeClientInput() {}

	/**
	 * TACZ は IGun を持っていると RenderHandEvent を無条件キャンセルして
	 * 自前で銃を描画する。近接モード (ダミー銃ID) では TACZ は何も描かないため
	 * 手が空になってしまう → キャンセルを取り消してバニラ描画 (剣モデル) を復元。
	 */
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
