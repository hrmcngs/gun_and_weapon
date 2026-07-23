package gun_and_weapon.client;

import com.tacz.guns.client.gui.overlay.GunHudOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeItem;

/**
 * 近接モードでも TACZ の残弾HUDを表示する。
 *
 * TACZ の {@link GunHudOverlay} は
 *   メインハンドの IGun → getGunId() → ClientGunIndex 解決
 * という流れで描画可否を決める。近接モードでは {@code getGunId} が
 * ダミーIDを返して TACZ の各システムを素通りさせているため、HUD も出ない。
 *
 * そこで描画の一瞬だけ「射撃モードのコピー」をメインハンドに差し込み、
 * TACZ の HUD 実装をそのまま呼んで描かせ、直後に元へ戻す。
 * クライアント側の 1 フレーム内で完結するのでサーバーやゲームロジックには影響しない。
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GunbladeMeleeHud {

	private GunbladeMeleeHud() {}

	@SubscribeEvent
	public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll("gunblade_melee_ammo", new MeleeAmmoOverlay());
	}

	private static class MeleeAmmoOverlay implements IGuiOverlay {
		private final GunHudOverlay taczHud = new GunHudOverlay();

		@Override
		public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
				float partialTick, int screenWidth, int screenHeight) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.options.hideGui) return;

			ItemStack mainHand = mc.player.getMainHandItem();
			if (!(mainHand.getItem() instanceof GunbladeItem) || !GunbladeItem.isMelee(mainHand)) {
				return; // 射撃モードは TACZ 本体の HUD がそのまま描く
			}

			// 射撃モード扱いのコピーを一瞬だけ持たせて TACZ の HUD を描かせる
			ItemStack rangedView = mainHand.copy();
			rangedView.getOrCreateTag().putString(GunbladeItem.TAG_MODE, "ranged");

			int slot = mc.player.getInventory().selected;
			mc.player.getInventory().setItem(slot, rangedView);
			try {
				taczHud.render(gui, graphics, partialTick, screenWidth, screenHeight);
			} catch (Throwable t) {
				GunAndWeaponMod.LOGGER.debug("Melee ammo HUD render failed", t);
			} finally {
				mc.player.getInventory().setItem(slot, mainHand);
			}
		}
	}
}
