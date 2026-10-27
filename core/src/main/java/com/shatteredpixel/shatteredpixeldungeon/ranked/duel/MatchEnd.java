package com.shatteredpixel.shatteredpixeldungeon.ranked.duel;

/**
 * spd-ranked (A2): decides how a Duel ends, following the DECISIONS L19 defaults.
 * Spec: docs/A2-duel-end.md. Pure logic, no game state and no RNG, so the server
 * and the client reach the same verdict from the same reports.
 *
 * Order of checks:
 * 1. forfeit, then disconnect longer than the limit: that player loses;
 * 2. a player who killed the final boss wins (earliest clear first, then fewer turns);
 * 3. one dead and the other alive: the living player wins;
 * 4. both dead: deeper floor, then later death, then more turns played; else draw;
 * 5. time limit reached with both alive: deeper floor, then more bosses killed,
 *    then fewer turns; else draw;
 * otherwise the match is still running.
 */
public final class MatchEnd {

	public enum Outcome { ONGOING, A_WINS, B_WINS, DRAW }

	public enum Reason {
		NONE, FORFEIT, DISCONNECT, BOSS_CLEARED, DEATH,
		BOTH_DEAD_DEPTH, BOTH_DEAD_TIME, BOTH_DEAD_TURNS,
		TIMEOUT_DEPTH, TIMEOUT_BOSSES, TIMEOUT_TURNS, TIE
	}

	/** Rule data, so limits change without touching the logic. */
	public static final class Rules {
		public final int finalBossDepth;
		public final long timeLimitMs;
		public final long disconnectLimitMs;

		public Rules(int finalBossDepth, long timeLimitMs, long disconnectLimitMs) {
			this.finalBossDepth = finalBossDepth;
			this.timeLimitMs = timeLimitMs;
			this.disconnectLimitMs = disconnectLimitMs;
		}

		/** L19: disconnect over 5 minutes loses. The time limit is a placeholder until S5 measures runs. */
		public static Rules defaults() {
			return new Rules(15, 90L * 60_000L, 5L * 60_000L);
		}
	}

	/** One player's state as reported to the judge. Times are server match time in ms. */
	public static final class Player {
		public int depth = 1;               // deepest floor reached
		public int bossesKilled = 0;
		public long turns = 0;
		public long deathAt = -1;           // -1: alive
		public long clearedAt = -1;         // -1: final boss not killed yet
		public long disconnectedSince = -1; // -1: connected
		public boolean forfeited = false;

		boolean dead() { return deathAt >= 0; }
		boolean cleared() { return clearedAt >= 0; }
	}

	public static final class Verdict {
		public final Outcome outcome;
		public final Reason reason;

		public Verdict(Outcome outcome, Reason reason) {
			this.outcome = outcome;
			this.reason = reason;
		}

		public boolean over() { return outcome != Outcome.ONGOING; }

		@Override
		public String toString() { return outcome + "/" + reason; }
	}

	private MatchEnd() {}

	public static Verdict judge(Player a, Player b, long now, Rules rules) {
		if (a.forfeited || b.forfeited) {
			return loser(a.forfeited, b.forfeited, Reason.FORFEIT);
		}
		boolean aGone = a.disconnectedSince >= 0 && now - a.disconnectedSince > rules.disconnectLimitMs;
		boolean bGone = b.disconnectedSince >= 0 && now - b.disconnectedSince > rules.disconnectLimitMs;
		if (aGone || bGone) {
			return loser(aGone, bGone, Reason.DISCONNECT);
		}

		if (a.cleared() || b.cleared()) {
			if (!b.cleared()) return win(true, Reason.BOSS_CLEARED);
			if (!a.cleared()) return win(false, Reason.BOSS_CLEARED);
			int c = Long.compare(b.clearedAt, a.clearedAt); // earlier clear wins
			if (c == 0) c = Long.compare(b.turns, a.turns); // then fewer turns
			return c == 0 ? draw() : win(c > 0, Reason.BOSS_CLEARED);
		}

		if (a.dead() != b.dead()) {
			return win(b.dead(), Reason.DEATH);
		}
		if (a.dead()) {
			int c = Integer.compare(a.depth, b.depth);
			if (c != 0) return win(c > 0, Reason.BOTH_DEAD_DEPTH);
			c = Long.compare(a.deathAt, b.deathAt);
			if (c != 0) return win(c > 0, Reason.BOTH_DEAD_TIME);
			c = Long.compare(a.turns, b.turns);
			if (c != 0) return win(c > 0, Reason.BOTH_DEAD_TURNS);
			return draw();
		}

		if (now >= rules.timeLimitMs) {
			int c = Integer.compare(a.depth, b.depth);
			if (c != 0) return win(c > 0, Reason.TIMEOUT_DEPTH);
			c = Integer.compare(a.bossesKilled, b.bossesKilled);
			if (c != 0) return win(c > 0, Reason.TIMEOUT_BOSSES);
			c = Long.compare(b.turns, a.turns);
			if (c != 0) return win(c > 0, Reason.TIMEOUT_TURNS);
			return draw();
		}

		return new Verdict(Outcome.ONGOING, Reason.NONE);
	}

	private static Verdict loser(boolean aLoses, boolean bLoses, Reason reason) {
		if (aLoses && bLoses) return new Verdict(Outcome.DRAW, reason);
		return win(bLoses, reason);
	}

	private static Verdict win(boolean aWins, Reason reason) {
		return new Verdict(aWins ? Outcome.A_WINS : Outcome.B_WINS, reason);
	}

	private static Verdict draw() {
		return new Verdict(Outcome.DRAW, Reason.TIE);
	}
}
