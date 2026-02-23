package gun_and_weapon.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.item.GunbladeSwordItem;

public class GunAndWeaponItems {

	public static final DeferredRegister<Item> REGISTRY =
			DeferredRegister.create(ForgeRegistries.ITEMS, GunAndWeaponMod.MODID);

	public static final RegistryObject<Item> GUNBLADE_SWORD =
			REGISTRY.register("gunblade_sword", GunbladeSwordItem::new);
}
