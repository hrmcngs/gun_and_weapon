package gun_and_weapon.init;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class GunAndWeaponTabs {

	public static final CreativeModeTab TAB_GUNBLADE = new CreativeModeTab("gun_and_weapon") {
		@Override
		public ItemStack makeIcon() {
			return new ItemStack(GunAndWeaponItems.GUNBLADE_SWORD.get());
		}
	};
}
