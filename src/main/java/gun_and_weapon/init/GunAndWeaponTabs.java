package gun_and_weapon.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import gun_and_weapon.GunAndWeaponMod;

public class GunAndWeaponTabs {

	public static final DeferredRegister<CreativeModeTab> REGISTRY =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GunAndWeaponMod.MODID);

	public static final RegistryObject<CreativeModeTab> TAB_GUNBLADE = REGISTRY.register("gunblade",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.gun_and_weapon"))
					.icon(() -> new ItemStack(GunAndWeaponItems.GUNBLADE_SWORD.get()))
					.displayItems((params, output) -> {
						output.accept(GunAndWeaponItems.GUNBLADE_SWORD.get());
					})
					.build());
}
