package gun_and_weapon.gunpack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 剣モデル (Java Block/Item) を display.gui の回転角でレンダリングして
 * TACZ 用 slot / hud アイコン PNG を生成する。tools/make_icons.py の Java 移植。
 * インベントリの剣アイテムと同じ構図のアイコンになる。
 */
public final class IconRenderer {

	private IconRenderer() {}

	private record Quad(double[][] corners, double[] uv) {}

	/** slot(64x64) / hud(180x60) を書き出す */
	public static void render(InputStream modelIs, InputStream textureIs,
			OutputStream slotOut, OutputStream hudOut) throws Exception {
		JsonObject model = new Gson().fromJson(
				new InputStreamReader(modelIs, StandardCharsets.UTF_8), JsonObject.class);
		BufferedImage tex = ImageIO.read(textureIs);

		double[] guiRot = {30, 225, 0};
		if (model.has("display")) {
			JsonObject disp = model.getAsJsonObject("display");
			if (disp.has("gui") && disp.getAsJsonObject("gui").has("rotation")) {
				JsonArray r = disp.getAsJsonObject("gui").getAsJsonArray("rotation");
				guiRot = new double[]{r.get(0).getAsDouble(), r.get(1).getAsDouble(), r.get(2).getAsDouble()};
			}
		}

		List<Quad> quads = buildQuads(model, guiRot);
		ImageIO.write(rasterize(quads, tex, 64, 64), "png", slotOut);
		ImageIO.write(rasterize(quads, tex, 180, 60), "png", hudOut);
	}

	private static List<Quad> buildQuads(JsonObject model, double[] guiRot) {
		List<Quad> quads = new ArrayList<>();
		for (JsonElement ee : model.getAsJsonArray("elements")) {
			JsonObject e = ee.getAsJsonObject();
			double[] f = vec(e.getAsJsonArray("from"));
			double[] t = vec(e.getAsJsonArray("to"));
			String axis = null;
			double angle = 0;
			double[] origin = {8, 8, 8};
			if (e.has("rotation")) {
				JsonObject rot = e.getAsJsonObject("rotation");
				angle = Math.toRadians(rot.get("angle").getAsDouble());
				axis = rot.get("axis").getAsString();
				if (rot.has("origin")) origin = vec(rot.getAsJsonArray("origin"));
			}
			for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject("faces").entrySet()) {
				double[] uv = vec4(entry.getValue().getAsJsonObject().getAsJsonArray("uv"));
				double[][] c = faceCorners(entry.getKey(), f, t);
				double[][] out = new double[4][];
				for (int i = 0; i < 4; i++) {
					double[] p = c[i];
					if (angle != 0) p = rotAxis(p, axis, angle, origin);
					out[i] = guiTransform(p, guiRot);
				}
				quads.add(new Quad(out, uv));
			}
		}
		return quads;
	}

	/** vanilla の面ごとのUV隅: {top-left, top-right, bottom-left, bottom-right} */
	private static double[][] faceCorners(String name, double[] f, double[] t) {
		double x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
		double[][] c = switch (name) {
			case "north" -> new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x1, y0, z0}};
			case "south" -> new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x0, y0, z1}};
			case "west" -> new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z0}};
			case "east" -> new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z1}};
			case "up" -> new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x0, y1, z1}};
			default -> new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x0, y0, z0}}; // down
		};
		double[] br = {c[1][0] + c[2][0] - c[0][0], c[1][1] + c[2][1] - c[0][1], c[1][2] + c[2][2] - c[0][2]};
		return new double[][]{c[0], c[1], c[2], br};
	}

	private static double[] rotAxis(double[] p, String axis, double ang, double[] o) {
		double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
		double c = Math.cos(ang), s = Math.sin(ang);
		double nx = x, ny = y, nz = z;
		switch (axis) {
			case "x" -> { ny = y * c - z * s; nz = y * s + z * c; }
			case "y" -> { nx = x * c + z * s; nz = -x * s + z * c; }
			case "z" -> { nx = x * c - y * s; ny = x * s + y * c; }
		}
		return new double[]{nx + o[0], ny + o[1], nz + o[2]};
	}

	/** 中心へ移動 → rotationXYZ (Z→Y→X の順にベクトルへ適用) */
	private static double[] guiTransform(double[] p, double[] rotDeg) {
		double x = p[0] - 8, y = p[1] - 8, z = p[2] - 8;
		double rx = Math.toRadians(rotDeg[0]), ry = Math.toRadians(rotDeg[1]), rz = Math.toRadians(rotDeg[2]);
		double c = Math.cos(rz), s = Math.sin(rz);
		double tx = x * c - y * s; double ty = x * s + y * c; x = tx; y = ty;
		c = Math.cos(ry); s = Math.sin(ry);
		tx = x * c + z * s; double tz = -x * s + z * c; x = tx; z = tz;
		c = Math.cos(rx); s = Math.sin(rx);
		ty = y * c - z * s; tz = y * s + z * c; y = ty; z = tz;
		return new double[]{x, y, z};
	}

	private static BufferedImage rasterize(List<Quad> quads, BufferedImage tex, int width, int height) {
		int pad = 2;
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (Quad q : quads) {
			for (double[] c : q.corners()) {
				minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
				minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
			}
		}
		double scale = Math.min((width - 2.0 * pad) / (maxX - minX + 1e-9),
				(height - 2.0 * pad) / (maxY - minY + 1e-9));
		double ox = (maxX + minX) / 2, oy = (maxY + minY) / 2;

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		double[][] zbuf = new double[height][width];
		for (double[] row : zbuf) java.util.Arrays.fill(row, -1e9);

		for (Quad q : quads) {
			double[][] cs = q.corners();
			double[] uv = q.uv();
			double w3 = dist(cs[0], cs[1]), h3 = dist(cs[0], cs[2]);
			int nu = Math.max(2, (int) (w3 * scale * 1.8));
			int nv = Math.max(2, (int) (h3 * scale * 1.8));
			for (int i = 0; i < nu; i++) {
				double fu = (i + 0.5) / nu;
				for (int j = 0; j < nv; j++) {
					double fv = (j + 0.5) / nv;
					double px3 = 0, py3 = 0, pz3 = 0;
					for (int k = 0; k < 3; k++) {
						double top = cs[0][k] + (cs[1][k] - cs[0][k]) * fu;
						double bot = cs[2][k] + (cs[3][k] - cs[2][k]) * fu;
						double val = top + (bot - top) * fv;
						if (k == 0) px3 = val; else if (k == 1) py3 = val; else pz3 = val;
					}
					int px = (int) ((px3 - ox) * scale + width / 2.0);
					int py = (int) ((oy - py3) * scale + height / 2.0);
					if (px < 0 || px >= width || py < 0 || py >= height) continue;
					if (pz3 <= zbuf[py][px]) continue;
					double u = uv[0] + (uv[2] - uv[0]) * fu;
					double v = uv[1] + (uv[3] - uv[1]) * fv;
					int txp = clamp((int) (u / 16.0 * tex.getWidth()), tex.getWidth() - 1);
					int typ = clamp((int) (v / 16.0 * tex.getHeight()), tex.getHeight() - 1);
					int argb = tex.getRGB(txp, typ);
					if (((argb >>> 24) & 0xFF) < 10) continue;
					zbuf[py][px] = pz3;
					img.setRGB(px, py, argb | 0xFF000000);
				}
			}
		}
		return img;
	}

	private static int clamp(int v, int max) {
		return Math.max(0, Math.min(v, max));
	}

	private static double dist(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double[] vec(JsonArray a) {
		return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
	}

	private static double[] vec4(JsonArray a) {
		return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble()};
	}
}
