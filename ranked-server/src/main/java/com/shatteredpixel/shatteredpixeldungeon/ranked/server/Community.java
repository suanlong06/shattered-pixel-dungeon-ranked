package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * spd-ranked (C8): weekly league, player profiles and lobby chat. Spec: docs/C8-community.md.
 * Runs inside {@link Hub}'s lock: the hub hands it every frame it does not know itself.
 *
 * Weekly League: every week (Monday 00:00 UTC) has one seed from the A4 pool, the same for
 * everyone. A player gets the seed with LEAGUE_START, which spends their one attempt of the
 * week, plays it solo, then sends the command log (LEAGUE_LOG parts, like LOG in a duel) and
 * a LEAGUE_SUBMIT claim. The claim is replayed like a duel side (A3) before it reaches the
 * board, which ranks final boss cleared first, then deepest floor, bosses, fewer turns.
 *
 * Chat: one lobby room. A player in a duel that has not ended can neither send nor receive,
 * so chat cannot carry information between opponents or from spectators.
 */
final class Community {

	/** replays one solo run; answers a Check whose detail[0] describes it. May answer later, from any thread */
	interface RunVerifier {
		void verify(long seed, int finalBossDepth, Hub.SideLog run, Consumer<Hub.Check> done);
	}

	static final class Config {
		RunVerifier verifier = (seed, depth, run, done) -> done.accept(new Hub.Check(Hub.Result.ACCEPT, null, null));
		/** what an UNVERIFIABLE run does: stays off the board (default) or counts */
		boolean acceptUnverifiable = false;
		/** a run started near the end of a week may still be submitted this long after it */
		long submitGraceMs = 3600_000L;
		int boardSize = 50;
		int profileRecent = 10;
		int chatMaxChars = 200;
		long chatGapMs = 1_500;
		int chatPerMinute = 10;
		int chatHistory = 50;
	}

	static final long DAY = 86_400_000L, WEEK = 7 * DAY;
	/** 1970-01-05, the first Monday after the epoch */
	static final long FIRST_MONDAY = 4 * DAY;

	static long week(long now) { return Math.floorDiv(now - FIRST_MONDAY, WEEK); }
	static long weekStart(long week) { return FIRST_MONDAY + week * WEEK; }

	/** what the hub shares with this class */
	interface Clients {
		Iterable<Hub.Client> all();
		Hub.Client byPlayer(long player);
		boolean inDuel(Hub.Client c);
	}

	private static final class RunLog {
		final long week;
		String cls;
		final List<String> cmds = new ArrayList<>();

		RunLog(long week) { this.week = week; }
	}

	private static final class Talker {
		long last = Long.MIN_VALUE / 2;
		final Deque<Long> minute = new ArrayDeque<>();
	}

	private final Config cfg;
	private final CommunityStore db;
	private final Clients clients;
	private final Object lock;
	private final int finalBossDepth;
	private final int season;
	private final int maxLogCommands;
	private final Map<Hub.Link, RunLog> logs = new HashMap<>();
	private final Map<Long, Talker> talkers = new HashMap<>();
	private final Deque<JsonObject> history = new ArrayDeque<>();

	Community(Config cfg, Storage storage, Clients clients, Object lock, int finalBossDepth, int season, int maxLogCommands) throws SQLException {
		this.cfg = cfg;
		this.db = new CommunityStore(storage);
		this.clients = clients;
		this.lock = lock;
		this.finalBossDepth = finalBossDepth;
		this.season = season;
		this.maxLogCommands = maxLogCommands;
		// a restart mid-verification: the claim and log are stored, so verify them again
		for (long[] e : db.verifying()) {
			Long seed = db.weekSeed(e[0], 0);
			Hub.SideLog run = db.entryLog(e[0], e[1]);
			if (seed != null && run != null) verify(e[0], e[1], seed, run);
		}
	}

	/** false if the frame is not a community frame */
	boolean receive(Hub.Client c, Frame f, long now) throws SQLException {
		switch (f.type) {
			case Frame.LEAGUE_INFO: info(c, now, false); return true;
			case Frame.LEAGUE_START: start(c, now); return true;
			case Frame.LEAGUE_LOG: log(c, f, now); return true;
			case Frame.LEAGUE_SUBMIT: submit(c, f, now); return true;
			case Frame.LEAGUE_BOARD: board(c, f, now); return true;
			case Frame.PROFILE: profile(c, f); return true;
			case Frame.CHAT: chat(c, f, now); return true;
			case Frame.CHAT_HISTORY: chatHistory(c); return true;
			case Frame.CHAT_REPORT: chatReport(c, f, now); return true;
			default: return false;
		}
	}

	void disconnect(Hub.Link link) {
		logs.remove(link);
	}

	// ---- league

	private void info(Hub.Client c, long now, boolean withSeed) throws SQLException {
		long w = week(now);
		JsonObject d = new JsonObject();
		d.addProperty("week", w);
		d.addProperty("startsAt", weekStart(w));
		d.addProperty("endsAt", weekStart(w + 1));
		d.addProperty("finalBossDepth", finalBossDepth);
		String state = db.entryState(w, c.player);
		d.addProperty("state", state == null ? "NONE" : state);
		d.addProperty("rank", db.rank(w, c.player));
		d.addProperty("entrants", db.entrants(w));
		if (withSeed) {
			Long seed = db.weekSeed(w, now);
			if (seed == null) {
				error(c, "seed pool empty");
				return;
			}
			d.addProperty("seed", seed);
		}
		c.link.send(new Frame(Frame.LEAGUE, d));
	}

	private void start(Hub.Client c, long now) throws SQLException {
		if (clients.inDuel(c)) {
			error(c, "already in a match");
			return;
		}
		long w = week(now);
		String state = db.entryState(w, c.player);
		if (state != null && !state.equals("STARTED")) {
			error(c, "league attempt already used this week");
			return;
		}
		// the seed is taken before the attempt is spent, so an empty pool costs nothing
		if (db.weekSeed(w, now) == null) {
			error(c, "seed pool empty");
			return;
		}
		// asking again while STARTED (a lost connection) gives the same seed back, still one attempt
		db.startEntry(w, c.player, now);
		logs.put(c.link, new RunLog(w));
		info(c, now, true);
	}

	private void log(Hub.Client c, Frame f, long now) throws SQLException {
		long w = f.l("week", -1);
		if (!"STARTED".equals(db.entryState(w, c.player))) {
			error(c, "no league run to log");
			return;
		}
		RunLog r = logs.get(c.link);
		if (r == null || r.week != w) {
			r = new RunLog(w);
			logs.put(c.link, r);
		}
		int from = f.i("from", -1);
		String cls = f.s("cls"), text = f.s("cmds");
		if (from < 0 || text == null) {
			error(c, "LEAGUE_LOG needs from and cmds");
			return;
		}
		if (r.cls == null) {
			if (from != 0 || cls == null) {
				error(c, "first LEAGUE_LOG needs from 0 and cls");
				return;
			}
			r.cls = cls;
		} else if (cls != null && !cls.equals(r.cls)) {
			error(c, "hero class changed");
			return;
		}
		if (from > r.cmds.size()) {
			JsonObject d = new JsonObject();
			d.addProperty("message", "log gap");
			d.addProperty("expected", r.cmds.size());
			c.link.send(new Frame(Frame.ERROR, d));
			return;
		}
		int i = from;
		for (String line : text.split("\n")) {
			line = line.trim();
			if (line.isEmpty()) continue;
			if (i++ < r.cmds.size()) continue;
			if (r.cmds.size() >= maxLogCommands) {
				error(c, "log too long");
				return;
			}
			r.cmds.add(line);
		}
	}

	private void submit(Hub.Client c, Frame f, long now) throws SQLException {
		long w = f.l("week", -1);
		if (!"STARTED".equals(db.entryState(w, c.player))) {
			error(c, "no league run to submit");
			return;
		}
		if (now > weekStart(w + 1) + cfg.submitGraceMs) {
			error(c, "week is over");
			return;
		}
		RunLog r = logs.get(c.link);
		if (r == null || r.week != w || r.cls == null) {
			error(c, "send the run with LEAGUE_LOG first");
			return;
		}
		int depth = f.i("depth", -1), bosses = f.i("bosses", 0);
		long turns = f.l("turns", -1);
		boolean dead = f.b("dead"), cleared = f.b("cleared");
		if (depth < 1 || depth > finalBossDepth || turns < 0 || bosses < 0 || bosses > finalBossDepth / 5
				|| (cleared && (dead || depth != finalBossDepth))) {
			error(c, "bad league claim");
			return;
		}
		Hub.SideLog run = new Hub.SideLog(r.cls, new ArrayList<>(r.cmds), depth, bosses, turns, dead, cleared);
		if (!db.submitEntry(w, c.player, run, now)) {
			error(c, "no league run to submit");
			return;
		}
		logs.remove(c.link);
		verify(w, c.player, db.weekSeed(w, now), run);
	}

	private void verify(long week, long player, long seed, Hub.SideLog run) {
		cfg.verifier.verify(seed, finalBossDepth, run, check -> {
			synchronized (lock) {
				try {
					verified(week, player, check);
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	private void verified(long week, long player, Hub.Check check) throws SQLException {
		String state = check.result == Hub.Result.ACCEPT || (check.result == Hub.Result.UNVERIFIABLE && cfg.acceptUnverifiable)
				? "ACCEPTED" : check.result.name();
		db.judgeEntry(week, player, state, check.detail[0]);
		Hub.Client c = clients.byPlayer(player);
		if (c == null) return;
		JsonObject d = new JsonObject();
		d.addProperty("week", week);
		d.addProperty("state", state);
		d.addProperty("rank", db.rank(week, player));
		d.addProperty("entrants", db.entrants(week));
		c.link.send(new Frame(Frame.LEAGUE_RESULT, d));
	}

	private void board(Hub.Client c, Frame f, long now) throws SQLException {
		long w = f.l("week", week(now));
		JsonObject d = new JsonObject();
		d.addProperty("week", w);
		d.add("entries", db.board(w, cfg.boardSize));
		d.addProperty("myRank", db.rank(w, c.player));
		d.addProperty("entrants", db.entrants(w));
		c.link.send(new Frame(Frame.LEAGUE_BOARD, d));
	}

	// ---- profile

	private void profile(Hub.Client c, Frame f) throws SQLException {
		long id = f.l("playerId", -1);
		String name = f.s("name");
		if (id < 0 && name != null) id = db.playerByName(name);
		if (id < 0 && name == null) id = c.player;
		JsonObject p = id < 0 ? null : db.profile(id, season, cfg.profileRecent);
		if (p == null) {
			error(c, "no such player");
			return;
		}
		c.link.send(new Frame(Frame.PROFILE, p));
	}

	// ---- chat

	private void chat(Hub.Client c, Frame f, long now) throws SQLException {
		if (clients.inDuel(c)) {
			error(c, "chat is closed during a match");
			return;
		}
		if (db.mutedUntil(c.player) > now) {
			error(c, "muted");
			return;
		}
		String text = clean(f.s("text"));
		if (text.isEmpty() || text.codePointCount(0, text.length()) > cfg.chatMaxChars) {
			error(c, "message must be 1 to " + cfg.chatMaxChars + " characters");
			return;
		}
		Talker t = talkers.computeIfAbsent(c.player, k -> new Talker());
		while (!t.minute.isEmpty() && now - t.minute.peekFirst() >= 60_000) t.minute.pollFirst();
		if (now - t.last < cfg.chatGapMs || t.minute.size() >= cfg.chatPerMinute) {
			error(c, "slow down");
			return;
		}
		t.last = now;
		t.minute.addLast(now);
		JsonObject d = new JsonObject();
		d.addProperty("playerId", c.player);
		d.addProperty("name", c.name);
		d.addProperty("text", text);
		d.addProperty("at", now);
		history.addLast(d);
		while (history.size() > cfg.chatHistory) history.pollFirst();
		for (Hub.Client o : clients.all()) {
			if (o.player >= 0 && !clients.inDuel(o)) o.link.send(new Frame(Frame.CHAT_MSG, d));
		}
	}

	/** control characters out, runs of whitespace to one space */
	static String clean(String s) {
		if (s == null) return "";
		return s.replaceAll("[\\p{Cntrl}\\p{Cf}&&[^\\u200d]]", " ").replaceAll("\\s+", " ").trim();
	}

	private void chatHistory(Hub.Client c) {
		if (clients.inDuel(c)) {
			error(c, "chat is closed during a match");
			return;
		}
		JsonArray a = new JsonArray();
		for (JsonObject m : history) a.add(m);
		JsonObject d = new JsonObject();
		d.add("messages", a);
		c.link.send(new Frame(Frame.CHAT_HISTORY, d));
	}

	private void chatReport(Hub.Client c, Frame f, long now) throws SQLException {
		long target = f.l("playerId", -1);
		if (target < 0 || target == c.player) {
			error(c, "bad report");
			return;
		}
		// the reported text is taken from history, so a report cannot put words in someone's mouth
		String quoted = null;
		long at = f.l("at", -1);
		for (JsonObject m : history) {
			if (m.get("playerId").getAsLong() == target && m.get("at").getAsLong() == at) quoted = m.get("text").getAsString();
		}
		db.report(c.player, target, quoted, now);
		c.link.send(new Frame(Frame.CHAT_REPORTED, null));
	}

	// owner tools and tests
	void mute(long player, long until, String reason) throws SQLException { db.mute(player, until, reason); }
	int reportsAgainst(long player) throws SQLException { return db.reportsAgainst(player); }

	private static void error(Hub.Client c, String msg) {
		JsonObject d = new JsonObject();
		d.addProperty("message", msg);
		c.link.send(new Frame(Frame.ERROR, d));
	}
}
