package gun_and_weapon.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;

import gun_and_weapon.GunAndWeaponMod;

public class GunAndWeaponTabs {
	private static final ResourceLocation KID_CARD_GUN =
			new ResourceLocation("kid1412", "card_gun");
	private static final ResourceLocation KID_PLAYING_CARD =
			new ResourceLocation("kid1412", "playing_card");

	private static ItemStack kidCardGun() {
		return GunItemBuilder.create()
				.setId(KID_CARD_GUN)
				.setAmmoCount(12)
				.build();
	}

	private static ItemStack kidPlayingCards() {
		return AmmoItemBuilder.create()
				.setId(KID_PLAYING_CARD)
				.setCount(32)
				.build();
	}

	public static final DeferredRegister<CreativeModeTab> REGISTRY =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GunAndWeaponMod.MODID);

	public static final RegistryObject<CreativeModeTab> TAB_GUNBLADE = REGISTRY.register("gunblade",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.gun_and_weapon"))
					.icon(() -> new ItemStack(GunAndWeaponItems.GUNBLADE_SWORD.get()))
					.displayItems((params, output) -> {
						output.accept(GunAndWeaponItems.GUNBLADE_SWORD.get());
						output.accept(kidCardGun());
						output.accept(kidPlayingCards());
					})
					.build());
}
