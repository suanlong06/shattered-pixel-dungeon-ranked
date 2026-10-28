package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HubTest {

	static final class FakeLink implements Hub.Link {
		final List<Frame> got = new ArrayList<>();

		@Override
		public void send(Frame f) { got.add(f); }

		Frame last(String type) {
			for (int i = got.size() - 1; i >= 0; i--) if (got.get(i).type.equals(type)) return got.get(i);
			return null;
		}
	}

	Storage db;
	Hub hub;
	Hub.Config cfg;
	long now = 1_000_000;

	@BeforeEach
	void setUp() throws Exception {
		db = new Storage("jdbc:sqlite::memory:");
		db.addSeeds(Arrays.asList(11L, 22L, 33L));
		cfg = new Hub.Config();
		hub = new Hub(cfg, db, now);
	}

	@AfterEach
	void tearDown() throws Exception { db.close(); }

	FakeLink hello(String key, String platform) throws Exception {
		FakeLink l = new FakeLink();
		hub.connect(l);
		JsonObject d = new JsonObject();
		d.addProperty("protocol", Frame.PROTOCOL);
		d.addProperty("build", "dev");
		d.addProperty("platform", platform);
		d.addProperty("deviceKey", key + "-device-key-000");
		d.addProperty("name", key);
		hub.receive(l, new Frame(Frame.HELLO, d), now);
		assertNotNull(l.last(Frame.WELCOME), l.got.toString());
		return l;
	}

	void send(FakeLink l, String type, Object... kv) throws Exception {
		JsonObject d = new JsonObject();
		for (int i = 0; i < kv.length; i += 2) {
			if (kv[i + 1] instanceof Boolean) d.addProperty((String) kv[i], (Boolean) kv[i + 1]);
			else d.addProperty((String) kv[i], (Number) kv[i + 1]);
		}
		hub.receive(l, new Frame(type, d), now);
	}

	/** two players queued, matched, ready, and the countdown over */
	FakeLink[] running() throws Exception {
		FakeLink a = hello("alice", "desktop"), b = hello("bob", "desktop");
		send(a, Frame.QUEUE);
		send(b, Frame.QUEUE);
		hub.tick(now);
		assertNotNull(a.last(Frame.MATCH_FOUND));
		assertNull(a.last(Frame.START), "seed must stay secret until both are ready");
		send(a, Frame.READY);
		send(b, Frame.READY);
		Frame start = a.last(Frame.START);
		assertEquals(11L, start.l("seed", -1));
		assertEquals(start.l("seed", -1), b.last(Frame.START).l("seed", -2));
		now += cfg.countdownMs;
		hub.tick(now);
		return new FakeLink[]{a, b};
	}

	@Test
	void refusesWrongProtocolAndBuild() throws Exception {
		FakeLink l = new FakeLink();
		hub.connect(l);
		JsonObject d = new JsonObject();
		d.addProperty("protocol", Frame.PROTOCOL);
		d.addProperty("build", "other");
		d.addProperty("platform", "desktop");
		d.addProperty("deviceKey", "0123456789abcdef");
		d.addProperty("name", "x");
		hub.receive(l, new Frame(Frame.HELLO, d), now);
		assertEquals("build mismatch", l.last(Frame.ERROR).s("message"));
		send(l, Frame.QUEUE);
		assertEquals("HELLO first", l.last(Frame.ERROR).s("message"));
	}

	@Test
	void platformsHaveSeparateQueues() throws Exception {
		FakeLink a = hello("alice", "desktop"), b = hello("bob", "android");
		send(a, Frame.QUEUE);
		send(b, Frame.QUEUE);
		hub.tick(now + 600_000);
		assertNull(a.last(Frame.MATCH_FOUND));
		assertEquals(2, hub.queued());
	}

	@Test
	void telemetryIsStoredPerSideAndCheckedFirst() throws Exception {
		FakeLink[] p = running();
		String id = p[0].last(Frame.START).matchId;
		JsonObject d = new JsonObject();
		d.addProperty("body", "telemetry=v1\ncount.damage=3\n\nrun.end=DIED\n");
		hub.receive(p[1], new Frame(Frame.TELEMETRY, 0, id, d), now);
		assertEquals("telemetry=v1\ncount.damage=3\nrun.end=DIED\n", db.telemetry(id, 1));
		assertNull(db.telemetry(id, 0));

		JsonObject bad = new JsonObject();
		bad.addProperty("body", "telemetry=v1\nno equals sign\n");
		hub.receive(p[0], new Frame(Frame.TELEMETRY, 0, id, bad), now);
		assertEquals("bad telemetry", p[0].last(Frame.ERROR).s("message"));
		assertNull(db.telemetry(id, 0));

		assertNull(Telemetry.clean("count.damage=3\n", 1000), "must start with the version");
		assertNull(Telemetry.clean("telemetry=v1\n", 5), "too long");
		assertNull(Telemetry.clean("telemetry=v1\nbad key!=1\n", 1000));
	}

	@Test
	void winnerByBossClearGetsRating() throws Exception {
		FakeLink[] p = running();
		send(p[0], Frame.PROGRESS, "depth", 5, "turns", 900, "bosses", 1);
		assertEquals(5, p[1].last(Frame.OPPONENT).i("depth", 0));
		send(p[1], Frame.PROGRESS, "depth", 3, "turns", 700);
		now += 60_000;
		send(p[0], Frame.CLEARED, "depth", 15, "turns", 5000, "bosses", 3);
		Frame ra = p[0].last(Frame.RESULT), rb = p[1].last(Frame.RESULT);
		assertEquals("WIN", ra.s("outcome"));
		assertEquals("LOSS", rb.s("outcome"));
		assertEquals("BOSS_CLEARED", ra.s("reason"));
		assertTrue(ra.data.get("ratingAfter").getAsDouble() > 1500);
		assertTrue(rb.data.get("ratingAfter").getAsDouble() < 1500);
		assertEquals(0, hub.activeMatches());
	}

	@Test
	void clearBeforeFinalBossDepthIsRefused() throws Exception {
		FakeLink[] p = running();
		send(p[0], Frame.CLEARED, "depth", 10, "turns", 10);
		assertNotNull(p[0].last(Frame.ERROR));
		assertNull(p[0].last(Frame.RESULT));
	}

	@Test
	void progressCannotGoBackwards() throws Exception {
		FakeLink[] p = running();
		send(p[0], Frame.PROGRESS, "depth", 4, "turns", 100);
		send(p[0], Frame.PROGRESS, "depth", 3, "turns", 120);
		assertEquals("bad progress", p[0].last(Frame.ERROR).s("message"));
	}

	@Test
	void deathLosesAndResignStrikes() throws Exception {
		FakeLink[] p = running();
		send(p[1], Frame.DIED, "depth", 2, "turns", 50);
		assertEquals("WIN", p[0].last(Frame.RESULT).s("outcome"));
		assertEquals("DEATH", p[0].last(Frame.RESULT).s("reason"));

		send(p[0], Frame.QUEUE);
		send(p[1], Frame.QUEUE);
		now += 60_000; // the first result split their ratings; let the window widen
		hub.tick(now);
		send(p[0], Frame.READY);
		send(p[1], Frame.READY);
		now += cfg.countdownMs;
		hub.tick(now);
		send(p[0], Frame.RESIGN);
		assertEquals("LOSS", p[0].last(Frame.RESULT).s("outcome"));
		assertEquals("FORFEIT", p[0].last(Frame.RESULT).s("reason"));
		assertEquals(1, db.strikesSince(1, 0));
	}

	@Test
	void shortDisconnectResumesLongOneLoses() throws Exception {
		FakeLink[] p = running();
		Frame found = p[0].last(Frame.MATCH_FOUND);
		String token = found.s("resumeToken");
		send(p[0], Frame.PROGRESS, "depth", 2, "turns", 40);

		hub.disconnect(p[0], now);
		assertFalse(p[1].last(Frame.OPPONENT).data.get("connected").getAsBoolean());
		now += 4 * 60_000;
		hub.tick(now);
		assertNull(p[1].last(Frame.RESULT));

		FakeLink back = hello("alice", "desktop");
		JsonObject d = new JsonObject();
		d.addProperty("resumeToken", token);
		hub.receive(back, new Frame(Frame.RESUME, d), now);
		Frame r = back.last(Frame.RESUMED);
		assertEquals(11L, r.l("seed", -1));
		assertEquals(40L, r.l("turns", -1));

		hub.disconnect(back, now);
		now += 5 * 60_000 + 1;
		hub.tick(now);
		assertEquals("WIN", p[1].last(Frame.RESULT).s("outcome"));
		assertEquals("DISCONNECT", p[1].last(Frame.RESULT).s("reason"));
	}

	@Test
	void resumeTokenOfAnotherPlayerIsRefused() throws Exception {
		FakeLink[] p = running();
		String token = p[0].last(Frame.MATCH_FOUND).s("resumeToken");
		JsonObject d = new JsonObject();
		d.addProperty("resumeToken", token);
		hub.receive(p[1], new Frame(Frame.RESUME, d), now);
		assertEquals("token belongs to another player", p[1].last(Frame.ERROR).s("message"));
	}

	@Test
	void bothDisconnectedVoids() throws Exception {
		FakeLink[] p = running();
		hub.disconnect(p[0], now);
		hub.disconnect(p[1], now);
		now += 5 * 60_000 + 1;
		hub.tick(now);
		assertEquals(0, hub.activeMatches());
		assertEquals("VOIDED", db.matchState(firstMatchId(p)));
		assertEquals(firstMatchId(p), db.seedUsedBy(11), "a seed sent in START stays used");
	}

	@Test
	void readyTimeoutVoidsAndStrikesTheIdle() throws Exception {
		FakeLink a = hello("alice", "desktop"), b = hello("bob", "desktop");
		send(a, Frame.QUEUE);
		send(b, Frame.QUEUE);
		hub.tick(now);
		send(a, Frame.READY);
		now += cfg.readyTimeoutMs + 1;
		hub.tick(now);
		assertEquals("VOID", a.last(Frame.RESULT).s("outcome"));
		assertEquals(0, db.strikesSince(1, 0));
		assertEquals(1, db.strikesSince(2, 0));
		assertNull(db.seedUsedBy(11), "START never went out, so the seed goes back to the pool");
		assertEquals(3, db.seedsLeft());
	}

	@Test
	void leavingBeforeStartGivesTheSeedBack() throws Exception {
		FakeLink a = hello("alice", "desktop"), b = hello("bob", "desktop");
		send(a, Frame.QUEUE);
		send(b, Frame.QUEUE);
		hub.tick(now);
		hub.disconnect(b, now);
		assertNull(db.seedUsedBy(11));
		// the next match gets the same, still secret seed
		FakeLink c = hello("carol", "desktop");
		send(a, Frame.QUEUE);
		send(c, Frame.QUEUE);
		hub.tick(now);
		send(a, Frame.READY);
		send(c, Frame.READY);
		assertEquals(11L, a.last(Frame.START).l("seed", -1));
	}

	@Test
	void strikesLockTheQueue() throws Exception {
		FakeLink a = hello("alice", "desktop");
		for (int i = 0; i < cfg.strikeLimit; i++) db.strike(1, now, "TEST");
		send(a, Frame.QUEUE);
		assertTrue(a.last(Frame.ERROR).s("message").startsWith("queue locked"));
		now += cfg.strikeWindowMs + 1;
		send(a, Frame.QUEUE);
		assertNotNull(a.last(Frame.QUEUED));
	}

	@Test
	void seedsAreUsedOnce() throws Exception {
		running();
		assertEquals(Long.valueOf(22L), db.takeSeed("x"));
		assertEquals(Long.valueOf(33L), db.takeSeed("y"));
		assertNull(db.takeSeed("z"));
	}

	@Test
	void verifierCanRejectAVerdict() throws Exception {
		cfg.verifier = (e, done) -> done.accept(new Hub.Check(Hub.Result.REJECT, "x", "y"));
		FakeLink[] p = running();
		send(p[1], Frame.DIED, "depth", 2, "turns", 50);
		assertEquals("VOID", p[0].last(Frame.RESULT).s("outcome"));
		assertEquals("REJECTED", p[0].last(Frame.RESULT).s("reason"));
	}

	@Test
	void restartVoidsUnfinishedMatches() throws Exception {
		FakeLink[] p = running();
		new Hub(cfg, db, now);
		assertEquals("VOIDED", db.matchState(firstMatchId(p)));
		assertEquals(firstMatchId(p), db.seedUsedBy(11));
	}

	@Test
	void restartBeforeStartGivesTheSeedBack() throws Exception {
		FakeLink a = hello("alice", "desktop"), b = hello("bob", "desktop");
		send(a, Frame.QUEUE);
		send(b, Frame.QUEUE);
		hub.tick(now);
		String id = a.last(Frame.MATCH_FOUND).matchId;
		new Hub(cfg, db, now);
		assertEquals("VOIDED", db.matchState(id));
		assertNull(db.seedUsedBy(11));
	}

	// L6: seed reroll vote

	@Test
	void rerollNeedsBothVotesAndStartsOver() throws Exception {
		FakeLink[] p = running();
		String id = firstMatchId(p);
		send(p[0], Frame.PROGRESS, "depth", 4, "turns", 300);
		send(p[0], Frame.VOTE_REROLL);
		Frame v = p[1].last(Frame.REROLL_VOTE);
		assertTrue(v.data.get("theirs").getAsBoolean());
		assertFalse(v.data.get("mine").getAsBoolean());
		int starts = count(p[1], Frame.START);
		send(p[1], Frame.VOTE_REROLL);
		assertEquals(starts + 1, count(p[1], Frame.START));
		Frame start = p[0].last(Frame.START);
		assertEquals(22L, start.l("seed", -1));
		assertEquals(1, start.i("reroll", 0));
		assertEquals(22L, db.matchSeed(id));
		// the revealed seed never goes back to the pool
		assertEquals(id, db.seedUsedBy(11L));
		assertEquals(1, p[1].last(Frame.OPPONENT).i("depth", 0));
		// reports wait for the new countdown, then start from floor 1 again
		send(p[0], Frame.PROGRESS, "depth", 2, "turns", 10);
		assertEquals("no running match", p[0].last(Frame.ERROR).s("message"));
		now += cfg.countdownMs;
		hub.tick(now);
		p[0].got.clear();
		send(p[0], Frame.PROGRESS, "depth", 2, "turns", 10);
		assertNull(p[0].last(Frame.ERROR));
		// one reroll per match
		send(p[0], Frame.VOTE_REROLL);
		assertEquals("no reroll left", p[0].last(Frame.ERROR).s("message"));
	}

	@Test
	void rerollClosesPastFloorFive() throws Exception {
		FakeLink[] p = running();
		send(p[1], Frame.PROGRESS, "depth", 6, "turns", 400);
		send(p[0], Frame.VOTE_REROLL);
		assertEquals("reroll only up to floor 5", p[0].last(Frame.ERROR).s("message"));
		assertNull(p[1].last(Frame.REROLL_VOTE));
	}

	@Test
	void openVoteEndsWhenSomeoneGoesPastFloorFive() throws Exception {
		FakeLink[] p = running();
		send(p[0], Frame.PROGRESS, "depth", 5, "turns", 400);
		send(p[0], Frame.VOTE_REROLL);
		send(p[0], Frame.PROGRESS, "depth", 6, "turns", 450);
		assertEquals("TOO_DEEP", p[1].last(Frame.REROLL_VOTE).s("ended"));
		send(p[1], Frame.VOTE_REROLL);
		assertNotNull(p[1].last(Frame.ERROR));
		assertEquals(11L, db.matchSeed(firstMatchId(p)));
	}

	@Test
	void rerollVoteCanBeDeclinedOrExpire() throws Exception {
		FakeLink[] p = running();
		send(p[0], Frame.VOTE_REROLL);
		send(p[1], Frame.VOTE_REROLL, "accept", false);
		assertEquals("DECLINED", p[0].last(Frame.REROLL_VOTE).s("ended"));
		send(p[1], Frame.VOTE_REROLL);
		now += cfg.rerollVoteMs;
		hub.tick(now);
		assertEquals("EXPIRED", p[0].last(Frame.REROLL_VOTE).s("ended"));
		send(p[0], Frame.VOTE_REROLL);
		assertFalse(p[1].last(Frame.REROLL_VOTE).data.has("ended"));
		assertEquals(11L, db.matchSeed(firstMatchId(p)));
	}

	@Test
	void rerollWithEmptyPoolKeepsTheSeed() throws Exception {
		FakeLink[] p = running();
		db.takeSeed("x");
		db.takeSeed("y");
		send(p[0], Frame.VOTE_REROLL);
		send(p[1], Frame.VOTE_REROLL);
		assertEquals("POOL_EMPTY", p[0].last(Frame.REROLL_VOTE).s("ended"));
		assertEquals(11L, db.matchSeed(firstMatchId(p)));
	}

	// L19: pause, own clocks

	@Test
	void pauseStopsOnlyThePausersClock() throws Exception {
		cfg.rules = new com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd.Rules(15, 600_000, 300_000);
		FakeLink[] p = running();
		now += 100_000;
		send(p[0], Frame.PAUSE);
		assertTrue(p[1].last(Frame.PAUSED).data.get("paused").getAsBoolean());
		assertFalse(p[1].last(Frame.PAUSED).data.get("mine").getAsBoolean());
		// nothing counts while paused
		send(p[0], Frame.PROGRESS, "depth", 2, "turns", 10);
		assertEquals("paused", p[0].last(Frame.ERROR).s("message"));
		now += 40_000;
		send(p[0], Frame.UNPAUSE);
		assertEquals(20_000, p[0].last(Frame.PAUSED).l("leftMs", -1));
		// match time 440 s: b clears at own 440 s, a's own clock is only at 400 s
		now += 300_000;
		send(p[1], Frame.CLEARED, "depth", 15, "turns", 900, "bosses", 3);
		assertNull(p[1].last(Frame.RESULT), "a may still clear earlier on its own clock");
		now += 20_000;
		// a clears at own 420 s, before b's 440 s
		send(p[0], Frame.CLEARED, "depth", 15, "turns", 950, "bosses", 3);
		assertEquals("WIN", p[0].last(Frame.RESULT).s("outcome"));
		assertEquals("LOSS", p[1].last(Frame.RESULT).s("outcome"));
	}

	@Test
	void clearStandsOnceTheOtherClockPassesIt() throws Exception {
		FakeLink[] p = running();
		send(p[0], Frame.PAUSE);
		now += 10_000;
		send(p[0], Frame.UNPAUSE);
		now += 50_000;
		send(p[1], Frame.CLEARED, "depth", 15, "turns", 900, "bosses", 3);
		assertNull(p[1].last(Frame.RESULT));
		now += 10_000;
		hub.tick(now);
		assertEquals("WIN", p[1].last(Frame.RESULT).s("outcome"));
	}

	@Test
	void pauseBudgetRunsOut() throws Exception {
		cfg.rules = new com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd.Rules(15, 600_000, 300_000);
		FakeLink[] p = running();
		send(p[0], Frame.PAUSE);
		now += 60_000;
		hub.tick(now);
		Frame ended = p[0].last(Frame.PAUSED);
		assertFalse(ended.data.get("paused").getAsBoolean(), "the budget ends the pause");
		assertEquals(0, ended.l("leftMs", -1));
		send(p[0], Frame.PAUSE);
		assertEquals("no pause left", p[0].last(Frame.ERROR).s("message"));
		now += 100_000;
		send(p[0], Frame.CLEARED, "depth", 15, "turns", 900, "bosses", 3);
		assertEquals("WIN", p[0].last(Frame.RESULT).s("outcome"));
	}

	@Test
	void timeoutWaitsForBothOwnClocks() throws Exception {
		cfg.rules = new com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd.Rules(15, 600_000, 300_000);
		FakeLink[] p = running();
		send(p[0], Frame.PAUSE);
		now += 30_000;
		send(p[0], Frame.UNPAUSE);
		now += 570_000;
		hub.tick(now);
		// b is out of time, a still has 30 s
		assertNull(p[0].last(Frame.RESULT));
		send(p[1], Frame.PROGRESS, "depth", 9, "turns", 900);
		assertEquals("time is up", p[1].last(Frame.ERROR).s("message"));
		send(p[0], Frame.PROGRESS, "depth", 3, "turns", 900);
		assertNull(p[0].last(Frame.ERROR));
		now += 30_000;
		hub.tick(now);
		assertEquals("WIN", p[0].last(Frame.RESULT).s("outcome"));
		assertEquals("TIMEOUT_DEPTH", p[0].last(Frame.RESULT).s("reason"));
	}

	private static int count(FakeLink l, String type) {
		int n = 0;
		for (Frame f : l.got) if (f.type.equals(type)) n++;
		return n;
	}

	private static String firstMatchId(FakeLink[] p) {
		return p[0].last(Frame.MATCH_FOUND).matchId;
	}
}
