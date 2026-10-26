import com.watabou.utils.Random;

import java.io.PrintStream;

/** Tách riêng để S3Math chạy được khi không có SPD-classes trên classpath. */
class SpdRandom {
	static void print(PrintStream out) {
		for (long seed : S3Math.SEEDS) {
			Random.pushGenerator(seed);
			for (int i = 0; i < S3Math.DRAWS; i++) {
				out.println("spd\tInt\t" + seed + ":" + i + "\t-\t" + Integer.toHexString(Random.Int()));
				out.println("spd\tIntMax\t" + seed + ":" + i + "\t-\t" + Random.Int(1 + i * 37));
				out.println("spd\tLong\t" + seed + ":" + i + "\t-\t" + Long.toHexString(Random.Long()));
				out.println("spd\tFloat\t" + seed + ":" + i + "\t-\t" + S3Math.hf(Random.Float()));
				out.println("spd\tNormalIntRange\t" + seed + ":" + i + "\t-\t" + Random.NormalIntRange(0, 100));
				out.println("spd\tNormalFloat\t" + seed + ":" + i + "\t-\t" + S3Math.hf(Random.NormalFloat(0f, 10f)));
			}
			Random.popGenerator();
		}
	}
}
