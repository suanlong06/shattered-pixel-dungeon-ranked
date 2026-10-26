package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

/**
 * spd-ranked (A1): Glicko-2 (Glickman 2012), one game per rating period, which is how
 * a Duel result is applied. Ratings use the Glicko scale (1500 / 350 / 0.06).
 */
public final class Glicko2 {

	public static final double TAU = 0.5;
	private static final double SCALE = 173.7178;
	private static final double EPS = 0.000001;

	public static final class Rating {
		public final double rating, rd, vol;

		public Rating(double rating, double rd, double vol) {
			this.rating = rating;
			this.rd = rd;
			this.vol = vol;
		}

		public static Rating initial() { return new Rating(1500, 350, 0.06); }

		@Override
		public String toString() { return String.format("%.1f/%.1f/%.4f", rating, rd, vol); }
	}

	private Glicko2() {}

	/** @param score 1 win, 0.5 draw, 0 loss for {@code p} */
	public static Rating update(Rating p, Rating opp, double score) {
		double mu = (p.rating - 1500) / SCALE, phi = p.rd / SCALE;
		double muJ = (opp.rating - 1500) / SCALE, phiJ = opp.rd / SCALE;

		double g = 1 / Math.sqrt(1 + 3 * phiJ * phiJ / (Math.PI * Math.PI));
		double e = 1 / (1 + Math.exp(-g * (mu - muJ)));
		double v = 1 / (g * g * e * (1 - e));
		double delta = v * g * (score - e);

		// new volatility, Illinois algorithm (step 5)
		double a = Math.log(p.vol * p.vol);
		double A = a, B;
		if (delta * delta > phi * phi + v) {
			B = Math.log(delta * delta - phi * phi - v);
		} else {
			int k = 1;
			while (f(a - k * TAU, delta, phi, v, a) < 0) k++;
			B = a - k * TAU;
		}
		double fA = f(A, delta, phi, v, a), fB = f(B, delta, phi, v, a);
		while (Math.abs(B - A) > EPS) {
			double C = A + (A - B) * fA / (fB - fA);
			double fC = f(C, delta, phi, v, a);
			if (fC * fB <= 0) {
				A = B;
				fA = fB;
			} else {
				fA /= 2;
			}
			B = C;
			fB = fC;
		}
		double vol = Math.exp(A / 2);

		double phiStar = Math.sqrt(phi * phi + vol * vol);
		double phiNew = 1 / Math.sqrt(1 / (phiStar * phiStar) + 1 / v);
		double muNew = mu + phiNew * phiNew * g * (score - e);
		return new Rating(muNew * SCALE + 1500, phiNew * SCALE, vol);
	}

	private static double f(double x, double delta, double phi, double v, double a) {
		double ex = Math.exp(x);
		double d = phi * phi + v + ex;
		return ex * (delta * delta - d) / (2 * d * d) - (x - a) / (TAU * TAU);
	}
}
