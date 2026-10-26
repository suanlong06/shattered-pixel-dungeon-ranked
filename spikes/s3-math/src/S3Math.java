import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Spike S3: in kết quả RNG và hàm toán ở dạng bit chính xác, để so JVM (HotSpot) với ART.
 * Mỗi dòng: "<nhóm>\t<hàm>\t<chỉ số>\t<input hex>\t<output hex>".
 * Tham số: [spd] -> thêm com.watabou.utils.Random (cần SPD-classes trên classpath).
 */
public class S3Math {

	static final long[] SEEDS = {0L, 1L, 42L, 12345L, 987654321L, -1L, Long.MIN_VALUE, 0x5DEECE66DL};
	static final int DRAWS = 64;

	public static void main(String[] args) {
		PrintStream out = new PrintStream(System.out, false);
		randomSection(out);
		mathSection(out);
		if (args.length > 0 && args[0].equals("spd")) SpdRandom.print(out);
		out.flush();
	}

	static String hd(double d) { return Long.toHexString(Double.doubleToRawLongBits(d)); }
	static String hf(float f) { return Integer.toHexString(Float.floatToRawIntBits(f)); }

	static void randomSection(PrintStream out) {
		for (long seed : SEEDS) {
			java.util.Random r;
			r = new java.util.Random(seed);
			for (int i = 0; i < DRAWS; i++) out.println("jur\tnextInt\t" + seed + ":" + i + "\t-\t" + Integer.toHexString(r.nextInt()));
			r = new java.util.Random(seed);
			for (int i = 0; i < DRAWS; i++) out.println("jur\tnextIntBound\t" + seed + ":" + i + "\t-\t" + r.nextInt(1 + i * 37));
			r = new java.util.Random(seed);
			for (int i = 0; i < DRAWS; i++) out.println("jur\tnextLong\t" + seed + ":" + i + "\t-\t" + Long.toHexString(r.nextLong()));
			r = new java.util.Random(seed);
			for (int i = 0; i < DRAWS; i++) out.println("jur\tnextFloat\t" + seed + ":" + i + "\t-\t" + hf(r.nextFloat()));
			r = new java.util.Random(seed);
			for (int i = 0; i < DRAWS; i++) out.println("jur\tnextDouble\t" + seed + ":" + i + "\t-\t" + hd(r.nextDouble()));
			r = new java.util.Random(seed);
			for (int i = 0; i < DRAWS; i++) out.println("jur\tnextGaussian\t" + seed + ":" + i + "\t-\t" + hd(r.nextGaussian()));
		}
	}

	/** ~2000 input cố định: giá trị biên + bit pattern sinh bằng splitmix64 (không dùng java.util.Random). */
	static double[] inputs() {
		List<Double> v = new ArrayList<>();
		double[] special = {0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 2.0, 10.0, 0.1, Math.PI, -Math.PI, Math.PI / 2, Math.PI / 4,
				Math.E, 1e-300, 1e300, -1e300, 1e308, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, -Double.MIN_VALUE,
				Double.MIN_NORMAL, Double.MIN_NORMAL / 3, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
				0.9999999999999999, 1.0000000000000002, 1e22, 1e23, 4503599627370496.5, 2.5, -2.5, 3.5, 0.49999999999999994,
				710.0, 709.782712893384, -745.1332191019411, 1e-10, 100.0, 1000.0, 1e6, 1e15, 1e16, 1e17, 123456789.123,
				0.7853981633974483, 1.5707963267948966, 3.141592653589793, 6.283185307179586, 1e10, 1e100, -1e-5};
		for (double d : special) v.add(d);
		long s = 0x9E3779B97F4A7C15L;
		while (v.size() < 1000) { s = mix(s); v.add(Double.longBitsToDouble(s)); }
		while (v.size() < 1700) { s = mix(s); v.add(((s >>> 11) * 0x1.0p-53 - 0.5) * 20.0); } // [-10, 10)
		while (v.size() < 2000) { s = mix(s); v.add(((s >>> 11) * 0x1.0p-53 - 0.5) * 2e6); }
		double[] a = new double[v.size()];
		for (int i = 0; i < a.length; i++) a[i] = v.get(i);
		return a;
	}

	static long mix(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	static void mathSection(PrintStream out) {
		double[] in = inputs();
		String[] un = {"sin", "cos", "tan", "asin", "acos", "atan", "exp", "log", "sqrt", "cbrt", "floor", "round"};
		DoubleUnaryOperator[] m = {Math::sin, Math::cos, Math::tan, Math::asin, Math::acos, Math::atan, Math::exp, Math::log,
				Math::sqrt, Math::cbrt, Math::floor, d -> (double) Math.round(d)};
		DoubleUnaryOperator[] sm = {StrictMath::sin, StrictMath::cos, StrictMath::tan, StrictMath::asin, StrictMath::acos,
				StrictMath::atan, StrictMath::exp, StrictMath::log, StrictMath::sqrt, StrictMath::cbrt, StrictMath::floor,
				d -> (double) StrictMath.round(d)};
		for (int f = 0; f < un.length; f++) {
			for (int i = 0; i < in.length; i++) {
				String x = hd(in[i]);
				out.println("Math\t" + un[f] + "\t" + i + "\t" + x + "\t" + hd(m[f].applyAsDouble(in[i])));
				out.println("StrictMath\t" + un[f] + "\t" + i + "\t" + x + "\t" + hd(sm[f].applyAsDouble(in[i])));
			}
		}
		// round(float) dùng nhiều trong SPD
		for (int i = 0; i < in.length; i++) {
			float x = (float) in[i];
			out.println("Math\troundF\t" + i + "\t" + hf(x) + "\t" + Math.round(x));
		}
		String[] bin = {"pow", "atan2", "hypot"};
		DoubleBinaryOperator[] mb = {Math::pow, Math::atan2, Math::hypot};
		DoubleBinaryOperator[] smb = {StrictMath::pow, StrictMath::atan2, StrictMath::hypot};
		for (int f = 0; f < bin.length; f++) {
			for (int i = 0; i < in.length; i++) {
				double a = in[i], b = in[(i * 7 + 3) % in.length];
				if (f == 0 && i >= 1000) b = in[(i * 7 + 3) % 54] ; // số mũ nhỏ/biên cho pow
				String x = hd(a) + "," + hd(b);
				out.println("Math\t" + bin[f] + "\t" + i + "\t" + x + "\t" + hd(mb[f].applyAsDouble(a, b)));
				out.println("StrictMath\t" + bin[f] + "\t" + i + "\t" + x + "\t" + hd(smb[f].applyAsDouble(a, b)));
			}
		}
	}
}
