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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.TacHitResult;

import gun_and_weapon.mixin.EntityKineticBulletInvoker;

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
	private static final ResourceLocation BAMBOO_SHOOT_STOCK =
			new ResourceLocation(MODID, "bamboo_shoot_stock");

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

	/**
	 * TaCZ の pierce はエンティティ貫通数であり、ブロック貫通には使われない。
	 * 竹ストックを装着した銃の弾だけブロック命中処理をキャンセルし、弾道を
	 * そのまま次のtickへ進めることで壁の向こう側へ到達させる。
	 */
	@SubscribeEvent
	public void bambooStockWallPierce(AmmoHitBlockEvent event) {
		Entity owner = event.getAmmo().getOwner();
		if (!(owner instanceof LivingEntity living)) return;

		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack gunStack = living.getItemInHand(hand);
			IGun gun = IGun.getIGunOrNull(gunStack);
			if (gun == null || !event.getAmmo().getGunId().equals(gun.getGunId(gunStack))) continue;
			if (BAMBOO_SHOOT_STOCK.equals(gun.getAttachmentId(gunStack, AttachmentType.STOCK))) {
				event.setCanceled(true);

				// TaCZ はブロック命中位置でエンティティ探索区間を打ち切るため、
				// 壁の裏からこのtick本来の終点までを改めて探索する。
				EntityKineticBullet bullet = event.getAmmo();
				var movement = bullet.getDeltaMovement();
				var direction = movement.normalize();
				var behindWall = event.getHitResult().getLocation().add(direction.scale(0.01));
				var tickEnd = bullet.position().add(movement);
				if (behindWall.distanceToSqr(tickEnd) > 1.0E-6) {
					for (EntityKineticBullet.EntityResult result :
							EntityUtil.findEntitiesOnPath(bullet, behindWall, tickEnd)) {
						((EntityKineticBulletInvoker) bullet).gunAndWeapon$invokeOnHitEntity(
								new TacHitResult(result), behindWall, tickEnd);
					}
				}
				return;
			}
		}
	}

}
