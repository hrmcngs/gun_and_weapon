package gun_and_weapon.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.tacz.guns.entity.EntityKineticBullet;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import the_four_primitives_and_weapons.damage.ElementType;

import gun_and_weapon.GunAndWeaponMod;
import gun_and_weapon.util.GunElements;

/**
 * MAW の属性が載っている銃で撃ったとき、弾道の軌跡にその属性のパーティクルを撒く。
 *
 * <p>軌跡の「色」そのものは TACZ 本来の曳光弾を属性色に染めて表現する
 * ({@code gun_and_weapon.mixin.BulletTracerColorMixin})。 こちらはその上に重ねる
 * 質感 — 炎なら FLAME、氷なら雪片、電気なら電光 — だけを担当する。
 * MAW の {@code ElementalParticles} は土台として必ず属性色の dust を撒く作りなので、
 * dust 抜きで使えるようアクセント部分だけをここに写している
 * (対応は {@code ElementalParticles#emit} と同じ)。</p>
 *
 * <p>風 / 聖 / 闇 / 消滅 / 侵食 / 瘴気 は MAW 側でも dust だけの属性なので、
 * ここでは何も撒かない ( = 曳光弾の色だけで表現される)。</p>
 *
 * <p>実装: 弾 ({@link EntityKineticBullet}) が湧いた瞬間に撃った側の銃の属性を記録し、
 * サーバー tick の終わりに「前 tick の位置 → 現在位置」の線分を等間隔にサンプリングして
 * その点に粒を置く。 TACZ の弾は 1 tick で数十ブロック進むため、tick 毎の線分を繋ぐことで
 * 初めて連続した軌跡になる (弾の現在地に出すだけでは点が飛ぶ)。</p>
 *
 * <p>属性が 2 つ付いている武器 (MAW の属性ペア) では 1 点ごとに主属性・副属性を
 * 交互に置く。 火 + 魂の 1:1 ペアは MAW 側で燐火 (SOUL_FIRE) に統合されるため、
 * そのまま単一の燐火の軌跡になる。</p>
 */
@Mod.EventBusSubscriber(modid = GunAndWeaponMod.MODID)
public final class ElementalBulletTrailHandler {

	/** 同時に軌跡を描く弾の上限 (フルオート連射時のパーティクル過多を防ぐ)。 */
	private static final int MAX_TRACKED_BULLETS = 64;

	/** 軌跡上の粒の間隔 (ブロック)。 dust の線ではなくアクセントなので粗めに置く。 */
	private static final double SAMPLE_STEP = 3.0;

	/** 1 tick / 1 発あたりのサンプル点の上限。 弾が速すぎる場合は間隔が広がる。 */
	private static final int MAX_SAMPLES_PER_TICK = 8;

	/** 1 tick に全弾合計で置く点の目安。 ショットガンのペレットなど同時多発時はここから山分けする。 */
	private static final int SAMPLE_BUDGET_PER_TICK = 32;

	/** 山分けしても 1 発あたり最低これだけは置く (完全に消えないように)。 */
	private static final int MIN_SAMPLES_PER_BULLET = 2;

	/** 粒の散布幅 (軌跡が滲みすぎないよう斬撃より控えめ)。 */
	private static final double SPREAD = 0.05;

	/** 着弾 / 弾切れ時に散らす粒の数と散布幅。 */
	private static final int IMPACT_COUNT = 4;
	private static final double IMPACT_SPREAD = 0.15;

	/** 粒を送る距離 (ブロック)。 バニラ既定の 32 では狙撃時に軌跡の先が見えない。 */
	private static final double VIEW_DISTANCE = 128.0;
	private static final double VIEW_DISTANCE_SQ = VIEW_DISTANCE * VIEW_DISTANCE;

	/** 血属性のアクセント: レッドストーンブロックの破壊パーティクル ( 赤い破片 )。 */
	private static final BlockParticleOption BLOOD_CHUNK =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());

	private static final List<Trail> TRACKED = new ArrayList<>();

	private ElementalBulletTrailHandler() {}

	/** 追跡中の 1 発分の状態。 */
	private static final class Trail {
		final EntityKineticBullet bullet;
		final ElementType primary;
		final ElementType secondary;
		/** 軌跡全体を通した点の連番 (tick を跨いでも主副の交互を保つ)。 */
		int step;

		Trail(EntityKineticBullet bullet, ElementType primary, ElementType secondary) {
			this.bullet = bullet;
			this.primary = primary;
			this.secondary = secondary;
		}

		/** {@code step} 番目の点に使う属性 (副属性があれば交互)。 */
		ElementType typeAt(int step) {
			if (secondary == ElementType.NONE) return primary;
			return (step & 1) == 0 ? primary : secondary;
		}
	}

	// ===================================================================
	// 発射: 銃の属性を弾に紐付ける
	// ===================================================================

	@SubscribeEvent
	public static void onBulletSpawn(EntityJoinLevelEvent event) {
		if (!(event.getLevel() instanceof ServerLevel)) return;
		if (!(event.getEntity() instanceof EntityKineticBullet bullet)) return;
		if (TRACKED.size() >= MAX_TRACKED_BULLETS) return;
		if (!(bullet.getOwner() instanceof LivingEntity shooter)) return;

		ItemStack gun = GunElements.findGun(shooter, bullet.getGunId());
		ElementType primary = GunElements.primary(gun);
		if (primary == ElementType.NONE) return;
		ElementType secondary = GunElements.secondary(gun);

		// 主副とも dust だけの属性なら撒くものが無い (曳光弾の色だけで表現される)。
		if (!hasAccent(primary) && !hasAccent(secondary)) return;

		TRACKED.add(new Trail(bullet, primary, secondary));
	}

	// ===================================================================
	// 飛行中: 前 tick の位置から現在位置までを繋いで撒く
	// ===================================================================

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		if (TRACKED.isEmpty()) return;

		int maxSamples = Math.min(MAX_SAMPLES_PER_TICK,
				Math.max(MIN_SAMPLES_PER_BULLET, SAMPLE_BUDGET_PER_TICK / TRACKED.size()));

		Iterator<Trail> it = TRACKED.iterator();
		while (it.hasNext()) {
			Trail trail = it.next();
			EntityKineticBullet bullet = trail.bullet;
			if (!(bullet.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			// 着弾 / 寿命切れで discard された弾も、消える直前の線分だけは撒いてから外す
			// (これを飛ばすと着弾点の手前で軌跡が途切れる)。
			drawSegment(level, trail, maxSamples);
			if (bullet.isRemoved()) {
				emitImpact(level, trail);
				it.remove();
			}
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		TRACKED.clear();
	}

	/** 前 tick の位置 (xo/yo/zo) から現在位置までを等間隔にサンプリングして粒を置く。 */
	private static void drawSegment(ServerLevel level, Trail trail, int maxSamples) {
		EntityKineticBullet bullet = trail.bullet;
		double x0 = bullet.xo;
		double y0 = bullet.yo;
		double z0 = bullet.zo;
		double dx = bullet.getX() - x0;
		double dy = bullet.getY() - y0;
		double dz = bullet.getZ() - z0;

		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-4) return;

		int samples = (int) Math.ceil(length / SAMPLE_STEP);
		if (samples > maxSamples) samples = maxSamples;

		for (int i = 1; i <= samples; i++) {
			double t = (double) i / samples;
			int step = trail.step++;
			emitAccent(level, trail.typeAt(step),
					x0 + dx * t, y0 + dy * t, z0 + dz * t, 1, SPREAD);
		}
	}

	/** 着弾点 (寿命切れなら消滅点) に属性の粒を散らす。 */
	private static void emitImpact(ServerLevel level, Trail trail) {
		double x = trail.bullet.getX();
		double y = trail.bullet.getY();
		double z = trail.bullet.getZ();
		if (trail.secondary == ElementType.NONE) {
			emitAccent(level, trail.primary, x, y, z, IMPACT_COUNT, IMPACT_SPREAD);
		} else {
			int half = Math.max(1, IMPACT_COUNT / 2);
			emitAccent(level, trail.primary, x, y, z, half, IMPACT_SPREAD);
			emitAccent(level, trail.secondary, x, y, z, half, IMPACT_SPREAD);
		}
	}

	// ===================================================================
	// 属性ごとのアクセント (MAW の ElementalParticles から dust を除いたもの)
	// ===================================================================

	/** dust 以外の粒を持つ属性か。 */
	private static boolean hasAccent(ElementType type) {
		return accentOf(type) != null;
	}

	/** 属性の質感を出す粒。 dust だけの属性 (風/聖/闇/消滅/侵食/瘴気) は null。 */
	private static ParticleOptions accentOf(ElementType type) {
		if (type == null) return null;
		switch (type) {
			case ICE:       return ParticleTypes.SNOWFLAKE;
			case ELECTRIC:
			case THUNDER:   return ParticleTypes.ELECTRIC_SPARK;
			case WATER:     return ParticleTypes.SPLASH;
			case BLOOD:     return BLOOD_CHUNK;
			case FIRE:      return ParticleTypes.FLAME;
			case SOUL:      return ParticleTypes.SOUL;
			case SOUL_FIRE: return ParticleTypes.SOUL_FIRE_FLAME;
			default:        return null;
		}
	}

	/** 魂系は 2 種類目を薄く重ねる (MAW の斬撃と同じ組み合わせ)。 */
	private static ParticleOptions subAccentOf(ElementType type) {
		if (type == ElementType.SOUL) return ParticleTypes.SCULK_SOUL;
		if (type == ElementType.SOUL_FIRE) return ParticleTypes.SOUL;
		return null;
	}

	private static void emitAccent(ServerLevel level, ElementType type,
			double x, double y, double z, int count, double spread) {
		ParticleOptions accent = accentOf(type);
		if (accent == null) return;
		sendLongDistance(level, accent, x, y, z, count, spread);

		ParticleOptions sub = subAccentOf(type);
		if (sub != null) sendLongDistance(level, sub, x, y, z, Math.max(1, count / 2), spread);
	}

	/**
	 * 粒を送る。
	 *
	 * <p>{@link ServerLevel#sendParticles} の既定は 32 ブロック先までしか届かず、
	 * 数十ブロック飛ぶ弾では軌跡の先端が撃った本人に見えない。
	 * ここでは long distance 指定で {@link #VIEW_DISTANCE} まで届かせる。</p>
	 */
	private static void sendLongDistance(ServerLevel level, ParticleOptions particle,
			double x, double y, double z, int count, double spread) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(x, y, z) > VIEW_DISTANCE_SQ) continue;
			level.sendParticles(player, particle, true, x, y, z, count, spread, spread, spread, 0.01);
		}
	}
}
