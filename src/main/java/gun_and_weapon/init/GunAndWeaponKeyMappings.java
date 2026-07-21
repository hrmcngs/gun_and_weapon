package gun_and_weapon.init;

import org.lwjgl.glfw.GLFW;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ReloadKey;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeSwordItem;
import gun_and_weapon.network.ModeSwitchMessage;

/**
 * ガンブレードのモード切替キー操作。
 *
 * TaCZ のリロードキー ([R]) を流用する:
 *   近接モード + [R]                  … 射撃モードに切替
 *                                       (剣は銃ではないので TaCZ 側のリロードは発動しない)
 *   射撃モード + [R] (弾倉満タン)     … 近接モードに切替 (リロード不要なので切替に使う)
 *   射撃モード + スニーク+[R]         … 残弾に関わらず即切替 (残弾はモード間で共有)
 *   射撃モード + [R] (弾倉に空き)     … 通常の TaCZ リロード
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT)
public class GunAndWeaponKeyMappings {

	private static final ResourceLocation GUNBLADE_GUN_ID = new ResourceLocation("gun_and_weapon", "gunblade");

	@SubscribeEvent
	public static void onKey(InputEvent.Key event) {
		if (event.getAction() != GLFW.GLFW_PRESS) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) return;

		try {
			if (!ReloadKey.RELOAD_KEY.matches(event.getKey(), event.getScanCode())) return;

			ItemStack mainHand = mc.player.getMainHandItem();
			if (mainHand.getItem() == GunAndWeaponItems.GUNBLADE_SWORD.get()) {
				// 近接モード: [R] で射撃モードへ
				GunAndWeaponMod.PACKET_HANDLER.sendToServer(new ModeSwitchMessage());
			} else {
				// 射撃モード: 弾倉が満タン、またはスニーク中なら近接モードへ
				// (弾倉に空きがある通常の [R] は TaCZ のリロードに任せる)
				IGun iGun = IGun.getIGunOrNull(mainHand);
				if (iGun != null && GUNBLADE_GUN_ID.equals(iGun.getGunId(mainHand))) {
					boolean full = iGun.getCurrentAmmoCount(mainHand) >= GunbladeSwordItem.MAX_AMMO;
					if (full || mc.player.isShiftKeyDown()) {
						GunAndWeaponMod.PACKET_HANDLER.sendToServer(new ModeSwitchMessage());
					}
				}
			}
		} catch (NoClassDefFoundError ignored) {
			// TaCZ が無い環境では何もしない
		}
	}
}
