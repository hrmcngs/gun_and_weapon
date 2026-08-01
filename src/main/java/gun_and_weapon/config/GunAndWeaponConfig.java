package gun_and_weapon.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 属性演出の設定 ({@code config/gun_and_weapon-common.toml})。
 *
 * <p>「重い」ときにどれが原因か切り分けられるよう、演出ごとに個別で切れるようにしてある。
 * 一番負荷が高いのは弾道のパーティクル ( 撒いた数 × 生存時間ぶんだけ画面上に粒が残る ) で、
 * 曳光弾とマズルフラッシュの着色は描画回数を増やさないため実質ゼロコスト。</p>
 *
 * <p>値は {@code ForgeConfigSpec} から直接読まず static フィールドに写している。
 * ミックスインや毎 tick の処理から読むため、未ロード時の例外やロック取得を避ける。</p>
 */
public final class GunAndWeaponConfig {

	public static final ForgeConfigSpec SPEC;

	private static final ForgeConfigSpec.BooleanValue TRAIL_PARTICLES_SPEC;
	private static final ForgeConfigSpec.IntValue TRAIL_BUDGET_SPEC;
	private static final ForgeConfigSpec.BooleanValue TRACER_COLOR_SPEC;
	private static final ForgeConfigSpec.BooleanValue MUZZLE_FLASH_COLOR_SPEC;

	/** 弾道に属性のパーティクルを撒くか。 重いと感じたらまずこれを false に。 */
	public static volatile boolean trailParticles = true;

	/** 1 tick に全弾合計で置くパーティクルの上限。 */
	public static volatile int trailBudgetPerTick = 12;

	/** TACZ の曳光弾を属性色にするか。 */
	public static volatile boolean tracerColor = true;

	/** マズルフラッシュを属性色にするか。 */
	public static volatile boolean muzzleFlashColor = true;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.comment("MAW の属性が載った銃の演出").push("elemental");

		TRAIL_PARTICLES_SPEC = builder
				.comment("弾道に属性のパーティクルを撒く (重いときはまずこれを false に)")
				.define("trailParticles", true);
		TRAIL_BUDGET_SPEC = builder
				.comment("1 tick に全弾合計で置くパーティクルの上限 (小さいほど軽い)")
				.defineInRange("trailBudgetPerTick", 12, 1, 64);
		TRACER_COLOR_SPEC = builder
				.comment("TACZ の曳光弾を属性色にする (描画回数は増えないので軽い)")
				.define("tracerColor", true);
		MUZZLE_FLASH_COLOR_SPEC = builder
				.comment("マズルフラッシュを属性色にする (描画回数は増えないので軽い)")
				.define("muzzleFlashColor", true);

		builder.pop();
		SPEC = builder.build();
	}

	private GunAndWeaponConfig() {}

	/** 設定のロード / リロード時に static フィールドへ写す。 */
	public static void onLoad(ModConfigEvent event) {
		if (event.getConfig().getSpec() != SPEC) return;
		trailParticles = TRAIL_PARTICLES_SPEC.get();
		trailBudgetPerTick = TRAIL_BUDGET_SPEC.get();
		tracerColor = TRACER_COLOR_SPEC.get();
		muzzleFlashColor = MUZZLE_FLASH_COLOR_SPEC.get();
	}
}
