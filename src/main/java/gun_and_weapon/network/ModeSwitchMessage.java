package gun_and_weapon.network;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import gun_and_weapon.init.GunAndWeaponItems;
import gun_and_weapon.item.GunbladeSwordItem;

import java.util.function.Supplier;

public class ModeSwitchMessage {

	private static final ResourceLocation GUNBLADE_GUN_ID = new ResourceLocation("tacz", "gunblade");

	public ModeSwitchMessage() {}
	public ModeSwitchMessage(FriendlyByteBuf buf) {}
	public void encode(FriendlyByteBuf buf) {}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			Player player = ctx.get().getSender();
			if (player == null) return;
			ItemStack mainHand = player.getMainHandItem();
			if (mainHand.getItem() == GunAndWeaponItems.GUNBLADE_SWORD.get()) {
				switchToRangedMode(player);
			} else if (isGunbladeGun(mainHand)) {
				switchToMeleeMode(player, mainHand);
			}
		});
		ctx.get().setPacketHandled(true);
	}

	private void switchToRangedMode(Player player) {
		CompoundTag data = player.getPersistentData();
		int ammo = data.getInt(GunbladeSwordItem.TAG_AMMO_COUNT);
		ItemStack gunStack = createGunbladeGunStack(ammo);
		if (gunStack.isEmpty()) return;
		player.getInventory().setItem(player.getInventory().selected, gunStack);
		data.putString(GunbladeSwordItem.TAG_MODE, "ranged");
		player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.5f, 1.5f);
	}

	private void switchToMeleeMode(Player player, ItemStack gunStack) {
		CompoundTag data = player.getPersistentData();
		data.putInt(GunbladeSwordItem.TAG_AMMO_COUNT, readGunAmmo(gunStack));
		player.getInventory().setItem(player.getInventory().selected, new ItemStack(GunAndWeaponItems.GUNBLADE_SWORD.get()));
		data.putString(GunbladeSwordItem.TAG_MODE, "melee");
		data.putInt(GunbladeSwordItem.TAG_CHARGE_TICKS, 0);
		player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.5f, 1.5f);
	}

	private boolean isGunbladeGun(ItemStack stack) {
		try {
			IGun iGun = IGun.getIGunOrNull(stack);
			if (iGun != null) return GUNBLADE_GUN_ID.equals(iGun.getGunId(stack));
		} catch (NoClassDefFoundError ignored) {}
		return false;
	}

	private ItemStack createGunbladeGunStack(int ammo) {
		try {
			return GunItemBuilder.create().setId(GUNBLADE_GUN_ID).setAmmoCount(ammo).setFireMode(FireMode.SEMI).build();
		} catch (NoClassDefFoundError ignored) {}
		return ItemStack.EMPTY;
	}

	private int readGunAmmo(ItemStack gunStack) {
		try {
			IGun iGun = IGun.getIGunOrNull(gunStack);
			if (iGun != null) return iGun.getCurrentAmmoCount(gunStack);
		} catch (NoClassDefFoundError ignored) {}
		return 0;
	}
}
