package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** C8: weekly league, profiles and lobby chat, driven through the hub with a fake clock. */
class CommunityTest {

	Storage db;
	Hub hub;
	Hub.Config cfg;
	/** Wednesday of some week */
	long now = Community.weekStart(2900) + 2 * Community.DAY;
	/** league runs the fake verifier has not answered yet */
	final List<Consumer<Hub.Check>> pending = new ArrayList<>();
	final List<Hub.SideLog> runs = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		db = new Storage("jdbc:sqlite::memory:");
		db.addSeeds(Arrays.asList(11L, 22L, 33L, 44L));
		cfg = new Hub.Config();
		cfg.community.verifier = (seed, depth, run, done) -> {
			runs.add(run);
			pending.add(done);
		};
		hub = new Hub(cfg, db, now);
	}

	@AfterEach
	void tearDown() throws Exception { db.close(); }

	HubTest.FakeLink hello(String name) throws Exception {
		HubTest.FakeLink l = new HubTest.FakeLink();
		hub.connect(l);
		hub.receive(l, frame(Frame.HELLO, "protocol", Frame.PROTOCOL, "build", "dev", "platform", "desktop",
				"deviceKey", name + "-device-key-000", "name", name), now);
		assertNotNull(l.last(Frame.WELCOME));
		return l;
	}

	static Frame frame(String type, Object... kv) {
		JsonObject d = new JsonObject();
		for (int i = 0; i < kv.length; i += 2) {
			Object v = kv[i + 1];
			if (v instanceof Number) d.addProperty((String) kv[i], (Number) v);
			else if (v instanceof Boolean) d.addProperty((String) kv[i], (Boolean) v);
			else d.addProperty((String) kv[i], (String) v);
		}
		return new Frame(type, d);
	}

	void send(HubTest.FakeLink l, String type, Object... kv) throws Exception {
		hub.receive(l, frame(type, kv), now);
	}

	String lastError(HubTest.FakeLink l) {
		Frame e = l.last(Frame.ERROR);
		return e == null ? null : e.s("message");
	}

	/** starts and submits a league run with the given claim */
	long run(HubTest.FakeLink l, int depth, long turns, boolean cleared) throws Exception {
		send(l, Frame.LEAGUE_START);
		Frame info = l.last(Frame.LEAGUE);
		assertNotNull(info, l.got.toString());
		long week = info.l("week", -1);
		send(l, Frame.LEAGUE_LOG, "week", week, "from", 0, "cls", "WARRIOR", "cmds", "W 1\nW 2\n");
		send(l, Frame.LEAGUE_SUBMIT, "week", week, "depth", depth, "turns", turns, "bosses", cleared ? 3 : depth / 5, "cleared", cleared);
		return week;
	}

	void answer(int i, Hub.Result r) {
		pending.get(i).accept(new Hub.Check(r, r.name().toLowerCase(), null));
	}

	// ---- league

	@Test
	void weeksStartOnMondayUtc() {
		// 2026-10-26 was a Monday
		long monday = java.time.LocalDate.of(2026, 10, 26).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
		long w = Community.week(monday);
		assertEquals(monday, Community.weekStart(w));
		assertEquals(w - 1, Community.week(monday - 1));
		assertEquals(w, Community.week(monday + Community.WEEK - 1));
	}

	@Test
	void everyoneGetsTheSameWeeklySeedAndItLeavesTheDuelPool() throws Exception {
		HubTest.FakeLink a = hello("alice"), b = hello("bob");
		send(a, Frame.LEAGUE_INFO);
		assertFalse(a.last(Frame.LEAGUE).data.has("seed"), "the seed only comes with the attempt");
		assertEquals("NONE", a.last(Frame.LEAGUE).s("state"));
		send(a, Frame.LEAGUE_START);
		send(b, Frame.LEAGUE_START);
		long seed = a.last(Frame.LEAGUE).l("seed", -1);
		assertEquals(11L, seed);
		assertEquals(seed, b.last(Frame.LEAGUE).l("seed", -2));
		assertEquals("league-W" + Community.week(now), db.seedUsedBy(11L));
		assertEquals(3, db.seedsLeft());

		// next week, a new seed
		now += Community.WEEK;
		send(a, Frame.LEAGUE_START);
		assertEquals(22L, a.last(Frame.LEAGUE).l("seed", -1));
	}

	@Test
	void oneAttemptPerWeek() throws Exception {
		HubTest.FakeLink a = hello("alice");
		send(a, Frame.LEAGUE_START);
		// asking again before submitting (lost connection) is the same attempt
		send(a, Frame.LEAGUE_START);
		assertEquals("STARTED", a.last(Frame.LEAGUE).s("state"));
		assertNull(lastError(a));
		run(a, 4, 900, false);
		assertEquals(1, pending.size());
		a.got.clear();
		send(a, Frame.LEAGUE_START);
		assertEquals("league attempt already used this week", lastError(a));
	}

	@Test
	void boardRanksClearThenDepthThenTurnsAndOnlyAfterReplay() throws Exception {
		HubTest.FakeLink a = hello("alice"), b = hello("bob"), c = hello("carol"), d = hello("dave");
		long week = run(a, 7, 3000, false);
		run(b, 15, 20000, true);
		run(c, 7, 2500, false);
		run(d, 9, 100, false);
		send(a, Frame.LEAGUE_BOARD);
		assertEquals(0, a.last(Frame.LEAGUE_BOARD).data.getAsJsonArray("entries").size(), "nothing before replay");

		answer(0, Hub.Result.ACCEPT);
		assertEquals("ACCEPTED", a.last(Frame.LEAGUE_RESULT).s("state"));
		answer(1, Hub.Result.ACCEPT);
		answer(2, Hub.Result.ACCEPT);
		answer(3, Hub.Result.REJECT);
		assertEquals("REJECT", d.last(Frame.LEAGUE_RESULT).s("state"));

		send(a, Frame.LEAGUE_BOARD, "week", week);
		Frame board = a.last(Frame.LEAGUE_BOARD);
		JsonArray e = board.data.getAsJsonArray("entries");
		assertEquals(3, e.size());
		assertEquals("bob", e.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("carol", e.get(1).getAsJsonObject().get("name").getAsString(), "same depth: fewer turns first");
		assertEquals("alice", e.get(2).getAsJsonObject().get("name").getAsString());
		assertEquals(3, board.i("myRank", -1));
		assertEquals(3, board.i("entrants", -1));
		assertFalse(e.get(0).getAsJsonObject().has("deviceKey"));
	}

	@Test
	void unverifiableRunsStayOffTheBoardUnlessConfigured() throws Exception {
		HubTest.FakeLink a = hello("alice");
		long week = run(a, 3, 300, false);
		answer(0, Hub.Result.UNVERIFIABLE);
		assertEquals("UNVERIFIABLE", a.last(Frame.LEAGUE_RESULT).s("state"));
		send(a, Frame.LEAGUE_BOARD, "week", week);
		assertEquals(0, a.last(Frame.LEAGUE_BOARD).i("entrants", -1));
	}

	@Test
	void badClaimsAndLateSubmissionsAreRefused() throws Exception {
		HubTest.FakeLink a = hello("alice");
		send(a, Frame.LEAGUE_START);
		long week = a.last(Frame.LEAGUE).l("week", -1);
		send(a, Frame.LEAGUE_SUBMIT, "week", week, "depth", 3, "turns", 100);
		assertEquals("send the run with LEAGUE_LOG first", lastError(a));
		send(a, Frame.LEAGUE_LOG, "week", week, "from", 5, "cls", "WARRIOR", "cmds", "W 1\n");
		assertEquals("first LEAGUE_LOG needs from 0 and cls", lastError(a));
		send(a, Frame.LEAGUE_LOG, "week", week, "from", 0, "cls", "WARRIOR", "cmds", "W 1\n");
		send(a, Frame.LEAGUE_LOG, "week", week, "from", 3, "cmds", "W 1\n");
		assertEquals(1, a.last(Frame.ERROR).i("expected", -1));
		send(a, Frame.LEAGUE_SUBMIT, "week", week, "depth", 12, "turns", 100, "cleared", true);
		assertEquals("bad league claim", lastError(a));
		send(a, Frame.LEAGUE_SUBMIT, "week", week, "depth", 16, "turns", 100);
		assertEquals("bad league claim", lastError(a));

		now = Community.weekStart(week + 1) + cfg.community.submitGraceMs + 1;
		send(a, Frame.LEAGUE_SUBMIT, "week", week, "depth", 3, "turns", 100);
		assertEquals("week is over", lastError(a));
		assertTrue(pending.isEmpty());
	}

	@Test
	void logResendSkipsWhatTheServerHasAndReachesTheVerifier() throws Exception {
		HubTest.FakeLink a = hello("alice");
		send(a, Frame.LEAGUE_START);
		long week = a.last(Frame.LEAGUE).l("week", -1);
		send(a, Frame.LEAGUE_LOG, "week", week, "from", 0, "cls", "MAGE", "cmds", "W 1\nW 2\n");
		send(a, Frame.LEAGUE_LOG, "week", week, "from", 1, "cmds", "W 2\nW 3\n");
		send(a, Frame.LEAGUE_SUBMIT, "week", week, "depth", 2, "turns", 30, "dead", true);
		Hub.SideLog r = runs.get(0);
		assertEquals(Arrays.asList("W 1", "W 2", "W 3"), r.commands);
		assertEquals("MAGE", r.heroClass);
		assertTrue(r.dead);
	}

	@Test
	void cannotStartALeagueRunDuringADuel() throws Exception {
		HubTest.FakeLink a = hello("alice"), b = hello("bob");
		send(a, Frame.QUEUE);
		send(b, Frame.QUEUE);
		hub.tick(now);
		send(a, Frame.LEAGUE_START);
		assertEquals("already in a match", lastError(a));
	}

	@Test
	void restartVerifiesHalfDoneRunsAgain() throws Exception {
		HubTest.FakeLink a = hello("alice");
		long week = run(a, 4, 400, false);
		// the server stops before the verifier answers
		pending.clear();
		hub = new Hub(cfg, db, now);
		assertEquals(1, pending.size());
		answer(0, Hub.Result.ACCEPT);
		HubTest.FakeLink a2 = hello("alice");
		send(a2, Frame.LEAGUE_BOARD, "week", week);
		assertEquals(1, a2.last(Frame.LEAGUE_BOARD).i("myRank", -1));
	}

	// ---- profile

	@Test
	void profileShowsRecordRecentMatchesAndLeague() throws Exception {
		HubTest.FakeLink a = hello("alice"), b = hello("bob");
		send(a, Frame.QUEUE);
		send(b, Frame.QUEUE);
		hub.tick(now);
		send(a, Frame.READY);
		send(b, Frame.READY);
		now += cfg.countdownMs;
		hub.tick(now);
		send(a, Frame.CLEARED, "depth", 15, "turns", 5000, "bosses", 3);
		assertEquals("WIN", a.last(Frame.RESULT).s("outcome"));
		long week = run(b, 5, 900, false);
		answer(0, Hub.Result.ACCEPT);

		send(a, Frame.PROFILE, "name", "bob");
		Frame p = a.last(Frame.PROFILE);
		assertNotNull(p, a.got.toString());
		assertEquals("bob", p.s("name"));
		assertEquals(0, p.i("wins", -1));
		assertEquals(1, p.i("losses", -1));
		JsonObject m = p.data.getAsJsonArray("recent").get(0).getAsJsonObject();
		assertEquals("LOSS", m.get("result").getAsString());
		assertEquals("alice", m.get("opponent").getAsString());
		JsonObject l = p.data.getAsJsonArray("league").get(0).getAsJsonObject();
		assertEquals(week, l.get("week").getAsLong());
		assertEquals(1, l.get("rank").getAsInt());
		assertTrue(p.data.getAsJsonArray("ratings").get(0).getAsJsonObject().get("rating").getAsDouble() < 1500);
		assertFalse(p.data.toString().contains("device-key"), "the device key is the login and stays private");

		send(a, Frame.PROFILE);
		assertEquals("alice", a.last(Frame.PROFILE).s("name"));
		assertEquals(1, a.last(Frame.PROFILE).i("wins", -1));
		send(a, Frame.PROFILE, "name", "nobody");
		assertEquals("no such player", lastError(a));
	}

	// ---- chat

	@Test
	void chatReachesTheLobbyButNotPlayersInADuel() throws Exception {
		HubTest.FakeLink a = hello("alice"), b = hello("bob"), c = hello("carol"), d = hello("dave");
		send(c, Frame.QUEUE);
		send(d, Frame.QUEUE);
		hub.tick(now);
		send(a, Frame.CHAT, "text", "  hi\u0007  there\n ");
		Frame got = b.last(Frame.CHAT_MSG);
		assertEquals("hi there", got.s("text"));
		assertEquals("alice", got.s("name"));
		assertNotNull(a.last(Frame.CHAT_MSG), "the sender sees their own message");
		assertNull(c.last(Frame.CHAT_MSG));
		send(c, Frame.CHAT, "text", "the seed has a shop on 2");
		assertEquals("chat is closed during a match", lastError(c));
		assertNull(a.last(Frame.ERROR));

		HubTest.FakeLink late = hello("erin");
		send(late, Frame.CHAT_HISTORY);
		assertEquals(1, late.last(Frame.CHAT_HISTORY).data.getAsJsonArray("messages").size());
	}

	@Test
	void chatIsRateLimitedAndLengthChecked() throws Exception {
		HubTest.FakeLink a = hello("alice");
		send(a, Frame.CHAT, "text", "   ");
		assertTrue(lastError(a).startsWith("message must be"));
		StringBuilder big = new StringBuilder();
		for (int i = 0; i <= cfg.community.chatMaxChars; i++) big.append('x');
		send(a, Frame.CHAT, "text", big.toString());
		assertTrue(lastError(a).startsWith("message must be"));

		send(a, Frame.CHAT, "text", "one");
		a.got.clear();
		send(a, Frame.CHAT, "text", "two");
		assertEquals("slow down", lastError(a));
		int sent = 1;
		for (int i = 0; i < 20; i++) {
			now += cfg.community.chatGapMs;
			a.got.clear();
			send(a, Frame.CHAT, "text", "m" + i);
			if (a.last(Frame.CHAT_MSG) != null) sent++;
		}
		// 20 tries 1.5 s apart span 30 s: the per-minute cap holds
		assertEquals(cfg.community.chatPerMinute, sent);
	}

	@Test
	void mutedPlayersCannotTalkAndReportsQuoteTheServerCopy() throws Exception {
		HubTest.FakeLink a = hello("alice"), b = hello("bob");
		send(a, Frame.CHAT, "text", "rude words");
		Frame m = b.last(Frame.CHAT_MSG);
		send(b, Frame.CHAT_REPORT, "playerId", m.l("playerId", -1), "at", m.l("at", -1));
		assertNotNull(b.last(Frame.CHAT_REPORTED));
		long alice = m.l("playerId", -1);
		CommunityStore store = new CommunityStore(db);
		assertEquals(1, store.reportsAgainst(alice));
		send(b, Frame.CHAT_REPORT, "playerId", -1);
		assertEquals("bad report", lastError(b));

		store.mute(alice, now + 60_000, "test");
		now += 10_000;
		send(a, Frame.CHAT, "text", "again");
		assertEquals("muted", lastError(a));
		now += 60_000;
		a.got.clear();
		send(a, Frame.CHAT, "text", "sorry");
		assertNotNull(a.last(Frame.CHAT_MSG));
	}

	@Test
	void communityFramesNeedHello() throws Exception {
		HubTest.FakeLink l = new HubTest.FakeLink();
		hub.connect(l);
		send(l, Frame.CHAT, "text", "hi");
		assertEquals("HELLO first", lastError(l));
	}
}
