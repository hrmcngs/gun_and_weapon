package gun_and_weapon.init;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.network.ModeSwitchMessage;

public class GunAndWeaponKeyMappings {

	public static final KeyMapping MODE_SWITCH = new KeyMapping(
			"key.gun_and_weapon.mode_switch",
			GLFW.GLFW_KEY_V,
			"key.categories.gun_and_weapon");

	@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static class ModEvents {
		@SubscribeEvent
		public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
			event.register(MODE_SWITCH);
		}
	}

	@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT)
	public static class ClientEvents {
		@SubscribeEvent
		public static void onClientTick(TickEvent.ClientTickEvent event) {
			if (event.phase == TickEvent.Phase.END) {
				while (MODE_SWITCH.consumeClick()) {
					GunAndWeaponMod.PACKET_HANDLER.sendToServer(new ModeSwitchMessage());
				}
			}
		}
	}
}
