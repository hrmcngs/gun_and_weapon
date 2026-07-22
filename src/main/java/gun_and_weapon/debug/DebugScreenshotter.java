package gun_and_weapon.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import gun_and_weapon.GunAndWeaponMod;

/**
 * デバッグ用 (環境変数 GUNBLADE_AUTOSHOT がある時だけ有効):
 *   - 5秒ごとに自動スクリーンショット (run/screenshots/gunblade_debug_N.png)
 *   - run/debug_cmd.txt にコマンドを書くと毎tick読み取って実行し、ファイルを削除する:
 *       fp / tp / tpf   … 視点切替 (一人称/三人称背面/三人称前面)
 *       shot            … 即時スクリーンショット (gunblade_manual_N.png)
 *       /<command>      … サーバーコマンド実行 (例: /time set day)
 *   通常プレイでは何もしない。
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID, value = Dist.CLIENT)
public class DebugScreenshotter {

	private static final boolean ENABLED = System.getenv("GUNBLADE_AUTOSHOT") != null;
	private static int tick = 0;
	private static int manualShot = 0;

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (!ENABLED || event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		tick++;
		if (tick % 100 == 0) {
			Screenshot.grab(mc.gameDirectory, "gunblade_debug_" + (tick / 100) + ".png",
					mc.getMainRenderTarget(), component -> {});
		}

		Path cmdFile = mc.gameDirectory.toPath().resolve("debug_cmd.txt");
		if (!Files.exists(cmdFile)) return;
		List<String> lines;
		try {
			lines = Files.readAllLines(cmdFile);
			Files.delete(cmdFile);
		} catch (Exception e) {
			return;
		}
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty()) continue;
			try {
				execute(mc, line);
			} catch (Exception e) {
				GunAndWeaponMod.LOGGER.error("debug_cmd failed: {}", line, e);
			}
		}
	}

	private static void execute(Minecraft mc, String line) {
		switch (line) {
			case "fp" -> mc.options.setCameraType(CameraType.FIRST_PERSON);
			case "reload" -> mc.reloadResourcePacks();
			case "closegui" -> mc.setScreen(null);
			case "combo" -> {
				// MAWの通常攻撃(コンボ)をサーバー側で直接実行
				MinecraftServer server = mc.getSingleplayerServer();
				if (server != null && mc.player != null) {
					var uuid = mc.player.getUUID();
					server.execute(() -> {
						var sp = server.getPlayerList().getPlayer(uuid);
						if (sp != null) {
							try {
								the_four_primitives_and_weapons.events.ChargedAttackHandler.performNormalAttack(sp);
								GunAndWeaponMod.LOGGER.info("[combo-debug] performNormalAttack called");
							} catch (Throwable t) {
								GunAndWeaponMod.LOGGER.error("[combo-debug] failed", t);
							}
						}
					});
				}
			}
			case "lclickon" -> mc.options.keyAttack.setDown(true);
			case "lclickoff" -> mc.options.keyAttack.setDown(false);
			case "attackpig" -> {
				// 照準に依存しない攻撃検証: 最寄りの豚を直接攻撃
				var level = mc.level;
				var pigs = level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,
						mc.player.getBoundingBox().inflate(5));
				if (!pigs.isEmpty()) {
					mc.gameMode.attack(mc.player, pigs.get(0));
					mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					GunAndWeaponMod.LOGGER.info("[click-debug] attacked pig directly");
				} else {
					GunAndWeaponMod.LOGGER.info("[click-debug] no pig nearby");
				}
			}
			case "attack" -> {
				// 左クリック攻撃の完全な再現 (ForgeHooks onClickInput → 各modのハンドラ → 攻撃)
				try {
					var m = Minecraft.class.getDeclaredMethod("startAttack");
					m.setAccessible(true);
					m.invoke(mc);
				} catch (Exception e) {
					GunAndWeaponMod.LOGGER.error("attack debug failed", e);
				}
			}
			case "toggle" -> {
				MinecraftServer server = mc.getSingleplayerServer();
				if (server != null && mc.player != null) {
					var uuid = mc.player.getUUID();
					server.execute(() -> {
						var sp = server.getPlayerList().getPlayer(uuid);
						if (sp != null) gun_and_weapon.item.GunbladeModeSwitch.toggle(sp);
					});
				}
			}
			case "slot1" -> mc.player.getInventory().selected = 0;
			case "slot2" -> mc.player.getInventory().selected = 1;
			case "slot3" -> mc.player.getInventory().selected = 2;
			case "tp" -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			case "tpf" -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case "shot" -> {
				manualShot++;
				Screenshot.grab(mc.gameDirectory, "gunblade_manual_" + manualShot + ".png",
						mc.getMainRenderTarget(), component -> {});
			}
			default -> {
				if (line.startsWith("/")) {
					MinecraftServer server = mc.getSingleplayerServer();
					if (server != null) {
						String cmd = line;
						server.execute(() -> server.getCommands().performPrefixedCommand(
								server.createCommandSourceStack(), cmd));
					}
				}
			}
		}
	}
}
