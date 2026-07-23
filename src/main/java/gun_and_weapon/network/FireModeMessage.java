package gun_and_weapon.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import gun_and_weapon.item.GunbladeItem;
import gun_and_weapon.item.GunbladeModeSwitch;

import java.util.function.Supplier;

/**
 * クライアント → サーバー: 射撃モードの発射モードを SEMI / BURST に切り替える。
 *
 * 左クリックを一定時間長押しすると burst=true が送られ、離すと burst=false が送られる。
 * これにより「軽く押す＝セミオート単発 / 長押し＝バースト連射」を実現する。
 */
public class FireModeMessage {

	private final boolean burst;

	public FireModeMessage(boolean burst) {
		this.burst = burst;
	}

	public FireModeMessage(FriendlyByteBuf buf) {
		this.burst = buf.readBoolean();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBoolean(burst);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			Player player = ctx.get().getSender();
			if (player == null) return;
			ItemStack mainHand = player.getMainHandItem();
			// 統合アイテムの射撃モードのみ対象
			if (!(mainHand.getItem() instanceof GunbladeItem)) return;
			if (!GunbladeItem.isRanged(mainHand)) return;
			if (!GunbladeModeSwitch.isGunbladeGun(mainHand)) return;

			CompoundTag tag = mainHand.getOrCreateTag();
			String target = burst ? "BURST" : "SEMI";
			if (!target.equals(tag.getString("GunFireMode"))) {
				tag.putString("GunFireMode", target);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
