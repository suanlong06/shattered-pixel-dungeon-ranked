package com.shatteredpixel.shatteredpixeldungeon.ranked.duel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd.Outcome;
import com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd.Player;
import com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd.Reason;
import com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd.Rules;
import com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd.Verdict;
import org.junit.jupiter.api.Test;

/** spd-ranked (A2): headless tests for the L19 match-end rules. */
class MatchEndTest {

	private static final Rules R = new Rules(15, 60_000L, 5_000L);

	private static Player p(int depth, long turns) {
		Player p = new Player();
		p.depth = depth;
		p.turns = turns;
		return p;
	}

	/** Checks the verdict, and that swapping the players mirrors it. */
	private static void check(Outcome o, Reason r, Player a, Player b, long now) {
		Verdict v = MatchEnd.judge(a, b, now, R);
		assertEquals(o, v.outcome, v.toString());
		assertEquals(r, v.reason, v.toString());
		Verdict s = MatchEnd.judge(b, a, now, R);
		Outcome mirror = o == Outcome.A_WINS ? Outcome.B_WINS : o == Outcome.B_WINS ? Outcome.A_WINS : o;
		assertEquals(mirror, s.outcome, "swapped " + s);
		assertEquals(r, s.reason, "swapped " + s);
	}

	@Test void ongoing() {
		check(Outcome.ONGOING, Reason.NONE, p(3, 100), p(4, 90), 1_000);
	}

	@Test void deathLoses() {
		Player a = p(9, 500), b = p(4, 200);
		a.deathAt = 10_000;
		check(Outcome.B_WINS, Reason.DEATH, a, b, 10_000);
	}

	@Test void bothDeadDeeperWins() {
		Player a = p(6, 300), b = p(5, 900);
		a.deathAt = 1_000; b.deathAt = 9_000;
		check(Outcome.A_WINS, Reason.BOTH_DEAD_DEPTH, a, b, 9_000);
	}

	@Test void bothDeadSameDepthLaterDeathWins() {
		Player a = p(5, 300), b = p(5, 900);
		a.deathAt = 8_000; b.deathAt = 2_000;
		check(Outcome.A_WINS, Reason.BOTH_DEAD_TIME, a, b, 8_000);
	}

	@Test void bothDeadSameTimeMoreTurnsWins() {
		Player a = p(5, 300), b = p(5, 900);
		a.deathAt = 4_000; b.deathAt = 4_000;
		check(Outcome.B_WINS, Reason.BOTH_DEAD_TURNS, a, b, 4_000);
	}

	@Test void bothDeadFullTieDraws() {
		Player a = p(5, 300), b = p(5, 300);
		a.deathAt = 4_000; b.deathAt = 4_000;
		check(Outcome.DRAW, Reason.TIE, a, b, 4_000);
	}

	@Test void bossClearWins() {
		Player a = p(15, 2000), b = p(15, 1000);
		a.clearedAt = 30_000; a.bossesKilled = 3;
		check(Outcome.A_WINS, Reason.BOSS_CLEARED, a, b, 30_000);
		b.deathAt = 31_000; // opponent dying later does not change it
		check(Outcome.A_WINS, Reason.BOSS_CLEARED, a, b, 31_000);
	}

	@Test void bothClearedEarlierWinsThenFewerTurns() {
		Player a = p(15, 2000), b = p(15, 1000);
		a.clearedAt = 30_000; b.clearedAt = 30_001;
		check(Outcome.A_WINS, Reason.BOSS_CLEARED, a, b, 30_001);
		b.clearedAt = 30_000;
		check(Outcome.B_WINS, Reason.BOSS_CLEARED, a, b, 30_001);
	}

	@Test void timeoutDepthThenBossesThenTurns() {
		check(Outcome.ONGOING, Reason.NONE, p(8, 900), p(7, 100), 59_999);
		check(Outcome.A_WINS, Reason.TIMEOUT_DEPTH, p(8, 900), p(7, 100), 60_000);
		Player a = p(8, 900), b = p(8, 100);
		a.bossesKilled = 1;
		check(Outcome.A_WINS, Reason.TIMEOUT_BOSSES, a, b, 60_000);
		check(Outcome.B_WINS, Reason.TIMEOUT_TURNS, p(8, 900), p(8, 100), 60_000);
		check(Outcome.DRAW, Reason.TIE, p(8, 100), p(8, 100), 60_000);
	}

	@Test void forfeitLosesBeforeEverythingElse() {
		Player a = p(15, 100), b = p(2, 50);
		a.clearedAt = 5_000; a.forfeited = true;
		check(Outcome.B_WINS, Reason.FORFEIT, a, b, 5_000);
	}

	@Test void shortDisconnectResumesLongDisconnectLoses() {
		Player a = p(4, 100), b = p(4, 100);
		a.disconnectedSince = 1_000;
		check(Outcome.ONGOING, Reason.NONE, a, b, 6_000); // exactly the limit: RESUME allowed
		check(Outcome.B_WINS, Reason.DISCONNECT, a, b, 6_001);
		b.disconnectedSince = 1_000;
		check(Outcome.DRAW, Reason.DISCONNECT, a, b, 6_001);
	}

	@Test void defaultsMatchL19() {
		assertEquals(5L * 60_000L, Rules.defaults().disconnectLimitMs);
		assertEquals(15, Rules.defaults().finalBossDepth);
	}

	// L19 pause: own clocks

	@Test void clearWaitsForTheOtherOwnClock() {
		Player a = p(15, 900), b = p(12, 800);
		a.clearedAt = 30_000;
		b.pausedMs = 5_000;
		// b's own clock is at 28 s: b may still clear before 30 s
		check(Outcome.ONGOING, Reason.NONE, a, b, 33_000);
		check(Outcome.A_WINS, Reason.BOSS_CLEARED, a, b, 35_000);
		b.deathAt = 20_000;
		check(Outcome.A_WINS, Reason.BOSS_CLEARED, a, b, 33_000);
	}

	@Test void earlierOwnClearWins() {
		Player a = p(15, 900), b = p(15, 800);
		a.clearedAt = 30_000; // own clock
		b.clearedAt = 31_000;
		check(Outcome.A_WINS, Reason.BOSS_CLEARED, a, b, 40_000);
	}

	@Test void timeoutNeedsBothOwnClocks() {
		Player a = p(5, 900), b = p(4, 800);
		b.pausedMs = 10_000;
		check(Outcome.ONGOING, Reason.NONE, a, b, 65_000);
		check(Outcome.A_WINS, Reason.TIMEOUT_DEPTH, a, b, 70_000);
	}
}
