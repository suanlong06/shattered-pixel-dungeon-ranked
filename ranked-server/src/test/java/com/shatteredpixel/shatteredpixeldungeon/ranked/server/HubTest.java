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
		for (int i = 0; i < kv.length; i += 2) d.addProperty((String) kv[i], (Number) kv[i + 1]);
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
		cfg.verifier = (id, v) -> false;
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
	}

	private static String firstMatchId(FakeLink[] p) {
		return p[0].last(Frame.MATCH_FOUND).matchId;
	}
}
