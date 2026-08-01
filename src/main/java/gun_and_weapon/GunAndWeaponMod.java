/*
 *    MCreator note:
 *
 *    If you lock base mod element files, you can edit this file and it won't get overwritten.
 *    If you change your modid or package, you need to apply these changes to this file MANUALLY.
 *
 *    Settings in @Mod annotation WON'T be changed in case of the base mod element
 *    files lock too, so you need to set them manually here in such case.
 *
 *    If you do not lock base mod element files in Workspace settings, this file
 *    will be REGENERATED on each build.
 *
 */
package gun_and_weapon;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;

import gun_and_weapon.init.GunAndWeaponItems;
import gun_and_weapon.init.GunAndWeaponTabs;

import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.AbstractMap;

@Mod("gun_and_weapon")
public class GunAndWeaponMod {
	public static final Logger LOGGER = LogManager.getLogger(GunAndWeaponMod.class);
	public static final String MODID = "gun_and_weapon";

	public GunAndWeaponMod() {
		MinecraftForge.EVENT_BUS.register(this);

		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

		// Register items and creative tabs
		GunAndWeaponItems.REGISTRY.register(bus);
		GunAndWeaponTabs.REGISTRY.register(bus);

		// Register network messages
		addNetworkMessage(gun_and_weapon.network.FireModeMessage.class,
				(msg, buf) -> msg.encode(buf),
				buf -> new gun_and_weapon.network.FireModeMessage(buf),
				(msg, ctx) -> msg.handle(ctx));

		// 属性演出の設定 (config/gun_and_weapon-common.toml)
		net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
				net.minecraftforge.fml.config.ModConfig.Type.COMMON,
				gun_and_weapon.config.GunAndWeaponConfig.SPEC);
		bus.addListener(gun_and_weapon.config.GunAndWeaponConfig::onLoad);

		// Register MAW (The four primitives and Weapons) special skills
		bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
				event.enqueueWork(gun_and_weapon.init.GunAndWeaponSkills::register));

		// Install TaCZ gun pack into <gamedir>/tacz/gunblade_pack.
		// geo は剣モデル (gunblade_sword.json) から毎起動時に生成される
		// (剣モデルが唯一のソース — TaczGeoGenerator 参照)。
		gun_and_weapon.gunpack.GunPackInstaller.install();
	}

	private static final String PROTOCOL_VERSION = "1";
	public static final SimpleChannel PACKET_HANDLER = NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID, MODID), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
	private static int messageID = 0;

	public static <T> void addNetworkMessage(Class<T> messageType, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> messageConsumer) {
		PACKET_HANDLER.registerMessage(messageID, messageType, encoder, decoder, messageConsumer);
		messageID++;
	}

	private static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

	public static void queueServerWork(int tick, Runnable action) {
		workQueue.add(new AbstractMap.SimpleEntry(action, tick));
	}

	@SubscribeEvent
	public void tick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			List<AbstractMap.SimpleEntry<Runnable, Integer>> actions = new ArrayList<>();
			workQueue.forEach(work -> {
				work.setValue(work.getValue() - 1);
				if (work.getValue() == 0)
					actions.add(work);
			});
			actions.forEach(e -> e.getKey().run());
			workQueue.removeAll(actions);
		}
	}

}
