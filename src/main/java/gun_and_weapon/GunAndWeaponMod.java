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
import gun_and_weapon.network.ModeSwitchMessage;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
		addNetworkMessage(ModeSwitchMessage.class,
				(msg, buf) -> msg.encode(buf),
				buf -> new ModeSwitchMessage(buf),
				(msg, ctx) -> msg.handle(ctx));

		// Install TaCZ gun pack to game directory
		installGunPack();
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

	private void installGunPack() {
		try {
			Path gameDir = FMLPaths.GAMEDIR.get();
			Path taczDir = gameDir.resolve("tacz");
			Path packDir = taczDir.resolve("gunblade_pack");

			Path versionMarker = packDir.resolve(".version_1.0.2");
			if (Files.exists(versionMarker)) {
				LOGGER.info("Gunblade gun pack already installed (v1.0.2)");
				return;
			}

			Files.createDirectories(packDir);

			String basePath = "/assets/tacz/custom/gunblade_pack/";
			String[] files = {
					"gunpack.meta.json",
					"assets/tacz/animations/gunblade.animation.json",
					"assets/tacz/display/guns/gunblade_display.json",
					"assets/tacz/geo_models/gun/gunblade_geo.json",
					"assets/tacz/textures/gun/uv/gunblade.png",
					"assets/tacz/lang/en_us.json",
					"assets/tacz/lang/ja_jp.json",
					"data/tacz/custom/guns/tacz/gunblade.json",
					"data/tacz/data/guns/gunblade_data.json",
					"data/tacz/recipes/gun/gunblade.json"
			};

			for (String file : files) {
				Path target = packDir.resolve(file.replace('/', java.io.File.separatorChar));
				Files.createDirectories(target.getParent());
				try (InputStream is = GunAndWeaponMod.class.getResourceAsStream(basePath + file)) {
					if (is != null) {
						Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
					} else {
						LOGGER.warn("Gun pack resource not found: " + basePath + file);
					}
				}
			}

			Files.writeString(versionMarker, "1.0.2");
			LOGGER.info("Gunblade gun pack installed to: " + packDir);
		} catch (IOException e) {
			LOGGER.error("Failed to install gunblade gun pack", e);
		}
	}
}
