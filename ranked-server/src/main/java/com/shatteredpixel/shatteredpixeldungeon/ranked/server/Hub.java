package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonObject;
import com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * spd-ranked (A1): the server's game logic, independent of sockets. Every call takes the
 * current server time, so tests drive it with a fake clock. Calls are serialised.
 *
 * Match lifecycle (T0.5 2.1): QUEUED -> PREPARING (seed taken, waiting for READY) ->
 * COUNTDOWN (START sent with t0) -> RUNNING -> VERIFYING -> FINISHED or VOIDED.
 * The server owns time and the verdict: clients only report progress, and a report is
 * a claim until replay verification (A3) accepts it through {@link Verifier}.
 *
 * Command log (A3, docs/A3-replay-verifier.md): each client streams its commands in LOG frames,
 * {@code {from, cmds, cls}}: {@code from} is the index of the first command in {@code cmds}
 * (one command per line), {@code cls} the hero class (required with from = 0). A part that
 * starts past the end of the log is refused with {@code expected}; commands already received
 * are skipped, so a client can resend after RESUME (RESUMED carries {@code logCount}).
 * Every PROGRESS, DIED and CLEARED must follow the LOG part that brings the log up to it: the
 * verifier replays the log to its end and compares the result with the side's last report.
 *
 * Not built on purpose (rules the user has not settled): mid-match seed reroll vote and
 * the shared 180 s pause budget. Both would be new frame types plus a Match field.
 */
public final class Hub {

	public enum State { PREPARING, COUNTDOWN, RUNNING, VERIFYING, FINISHED, VOIDED }

	/** one connection; the transport decides how frames get there */
	public interface Link {
		void send(Frame f);
	}

	/** A3 hook: decides whether a verdict stands before ratings change. May answer later, from any thread. */
	public interface Verifier {
		void verify(Evidence e, Consumer<Check> done);
	}

	public enum Result {
		/** replay agrees with every claim the verdict uses */
		ACCEPT,
		/** replay contradicts a claim: the match is voided */
		REJECT,
		/** the log uses something replay cannot follow yet (T0.8 limits); see Config.acceptUnverifiable */
		UNVERIFIABLE
	}

	public static final class Check {
		public final Result result;
		/** per side: null if not replayed, else what replay found (stored with the log) */
		public final String[] detail;

		public Check(Result result, String detailA, String detailB) {
			this.result = result;
			this.detail = new String[]{detailA, detailB};
		}
	}

	/** everything a verifier needs; copies, so the hub does not wait on it */
	public static final class Evidence {
		public final String matchId;
		public final long seed;
		public final MatchEnd.Verdict verdict;
		public final MatchEnd.Rules rules;
		public final SideLog[] sides;

		Evidence(String matchId, long seed, MatchEnd.Verdict verdict, MatchEnd.Rules rules, SideLog a, SideLog b) {
			this.matchId = matchId;
			this.seed = seed;
			this.verdict = verdict;
			this.rules = rules;
			this.sides = new SideLog[]{a, b};
		}

		/** forfeit and disconnect are facts the server saw itself; every other verdict rests on reports */
		public boolean needsReplay() {
			return needsReplay(verdict);
		}

		static boolean needsReplay(MatchEnd.Verdict v) {
			return v.reason != MatchEnd.Reason.FORFEIT && v.reason != MatchEnd.Reason.DISCONNECT;
		}
	}

	/** one side's log and last report */
	public static final class SideLog {
		public final String heroClass;
		public final List<String> commands;
		public final int depth, bossesKilled;
		public final long turns;
		public final boolean dead, cleared;

		public SideLog(String heroClass, List<String> commands, int depth, int bossesKilled, long turns, boolean dead, boolean cleared) {
			this.heroClass = heroClass;
			this.commands = commands;
			this.depth = depth;
			this.bossesKilled = bossesKilled;
			this.turns = turns;
			this.dead = dead;
			this.cleared = cleared;
		}

		SideLog(Side s) {
			this(s.heroClass, new ArrayList<>(s.log), s.p.depth, s.p.bossesKilled, s.p.turns, s.p.deathAt >= 0, s.p.clearedAt >= 0);
		}
	}

	public static final class Config {
		public String build = "dev";
		public int season = 1;
		public long countdownMs = 5_000;
		public long readyTimeoutMs = 60_000;
		public int strikeLimit = 3;
		public long strikeWindowMs = 24L * 3600_000L;
		public MatchEnd.Rules rules = MatchEnd.Rules.defaults();
		public Verifier verifier = (e, done) -> done.accept(new Check(Result.ACCEPT, null, null));
		/** what an UNVERIFIABLE check does: void the match (default, ratings unchanged) or count the verdict */
		public boolean acceptUnverifiable = false;
		/** longest command log per side */
		public int maxLogCommands = 500_000;
	}

	static final class Side {
		final long player;
		final String name;
		final double rating;
		final String token;
		Link link;
		boolean ready;
		final MatchEnd.Player p = new MatchEnd.Player();
		String heroClass;
		final List<String> log = new ArrayList<>();

		Side(long player, String name, double rating, String token) {
			this.player = player;
			this.name = name;
			this.rating = rating;
			this.token = token;
		}
	}

	static final class Match {
		final String id;
		final String platform;
		final long seed;
		final long created;
		final Side[] sides;
		State state = State.PREPARING;
		long t0;
		MatchEnd.Verdict pending;

		Match(String id, String platform, long seed, long created, Side a, Side b) {
			this.id = id;
			this.platform = platform;
			this.seed = seed;
			this.created = created;
			this.sides = new Side[]{a, b};
		}

		/** match time in ms; 0 until t0 */
		long clock(long now) { return state == State.RUNNING || state == State.COUNTDOWN ? Math.max(0, now - t0) : 0; }
	}

	static final class Client {
		final Link link;
		long player = -1;
		String name;
		String platform;
		Match match;

		Client(Link link) { this.link = link; }
	}

	private final Config cfg;
	private final Storage db;
	private final Matchmaker queue = new Matchmaker();
	private final Map<Link, Client> clients = new HashMap<>();
	private final Map<Long, Client> byPlayer = new HashMap<>();
	private final Map<String, Match> matches = new LinkedHashMap<>();
	private final Map<String, Match> byToken = new HashMap<>();
	private final SecureRandom tokens = new SecureRandom();
	private long nextMatch = 1;
	/** latest server time seen, for verifier answers that arrive between calls */
	private long lastNow;

	public Hub(Config cfg, Storage db, long now) throws SQLException {
		this.cfg = cfg;
		this.db = db;
		db.voidUnfinished(now);
	}

	public synchronized void connect(Link link) {
		clients.put(link, new Client(link));
	}

	public synchronized void disconnect(Link link, long now) throws SQLException {
		lastNow = Math.max(lastNow, now);
		Client c = clients.remove(link);
		if (c == null || c.player < 0) return;
		if (byPlayer.get(c.player) == c) byPlayer.remove(c.player);
		queue.remove(c.player);
		Match m = c.match;
		if (m == null) return;
		Side s = side(m, c.player);
		if (s.link != link) return;
		s.link = null;
		if (m.state == State.PREPARING) {
			voidMatch(m, now, "LEFT_BEFORE_START", s.player);
		} else if (m.state == State.COUNTDOWN || m.state == State.RUNNING) {
			s.p.disconnectedSince = m.clock(now);
			opponent(m, s);
			judge(m, now);
		}
	}

	public synchronized void receive(Link link, Frame f, long now) throws SQLException {
		lastNow = Math.max(lastNow, now);
		Client c = clients.get(link);
		if (c == null) return;
		if (f.type.equals(Frame.PING)) {
			link.send(new Frame(Frame.PONG, f.seq, null, null));
			return;
		}
		if (f.type.equals(Frame.HELLO)) {
			hello(c, f, now);
			return;
		}
		if (c.player < 0) {
			error(link, "HELLO first");
			return;
		}
		switch (f.type) {
			case Frame.QUEUE: queue(c, now); break;
			case Frame.LEAVE_QUEUE: queue.remove(c.player); break;
			case Frame.RESUME: resume(c, f); break;
			case Frame.READY: ready(c, now); break;
			case Frame.PROGRESS: case Frame.DIED: case Frame.CLEARED: case Frame.RESIGN: report(c, f, now); break;
			case Frame.LOG: log(c, f); break;
			default: error(link, "unknown type " + f.type);
		}
	}

	/** pairs the queue, starts and times out matches; call about once a second */
	public synchronized void tick(long now) throws SQLException {
		lastNow = Math.max(lastNow, now);
		for (Matchmaker.Entry[] pair : queue.pair(now)) {
			start(pair[0], pair[1], now);
		}
		for (Match m : new ArrayList<>(matches.values())) {
			if (m.state == State.PREPARING && now - m.created > cfg.readyTimeoutMs) {
				for (Side s : m.sides) if (!s.ready) db.strike(s.player, now, "NOT_READY");
				voidMatch(m, now, "READY_TIMEOUT", -1);
				continue;
			}
			if (m.state == State.COUNTDOWN && now >= m.t0) m.state = State.RUNNING;
			judge(m, now);
		}
	}

	private void hello(Client c, Frame f, long now) throws SQLException {
		if (f.i("protocol", -1) != Frame.PROTOCOL) {
			error(c.link, "protocol " + Frame.PROTOCOL + " required");
			return;
		}
		// replay only holds on the same bytecode (T0.5 4.4), so the build must match exactly
		if (!cfg.build.equals(f.s("build"))) {
			error(c.link, "build mismatch");
			return;
		}
		String platform = f.s("platform"), key = f.s("deviceKey"), name = f.s("name");
		if (!"android".equals(platform) && !"desktop".equals(platform)) {
			error(c.link, "platform must be android or desktop");
			return;
		}
		if (key == null || key.length() < 16 || key.length() > 128 || name == null || name.isEmpty() || name.length() > 24) {
			error(c.link, "bad deviceKey or name");
			return;
		}
		c.player = db.player(key, name, now);
		c.name = name;
		c.platform = platform;
		Client old = byPlayer.put(c.player, c);
		if (old != null && old != c) queue.remove(c.player);
		JsonObject d = new JsonObject();
		d.addProperty("playerId", c.player);
		d.addProperty("season", cfg.season);
		d.addProperty("rating", db.rating(c.player, cfg.season, platform).rating);
		c.link.send(new Frame(Frame.WELCOME, d));
	}

	private void queue(Client c, long now) throws SQLException {
		if (c.match != null && (c.match.state != State.FINISHED && c.match.state != State.VOIDED)) {
			error(c.link, "already in a match");
			return;
		}
		int strikes = db.strikesSince(c.player, now - cfg.strikeWindowMs);
		if (strikes >= cfg.strikeLimit) {
			error(c.link, "queue locked: " + strikes + " strikes");
			return;
		}
		c.match = null;
		queue.add(c.player, c.platform, db.rating(c.player, cfg.season, c.platform).rating, now);
		c.link.send(new Frame(Frame.QUEUED, null));
	}

	private void start(Matchmaker.Entry a, Matchmaker.Entry b, long now) throws SQLException {
		Client ca = byPlayer.get(a.playerId), cb = byPlayer.get(b.playerId);
		String id = "m" + now + "-" + (nextMatch++);
		Long seed = db.startMatch(id, cfg.season, a.platform, a.playerId, b.playerId, now);
		if (seed == null) {
			System.err.println("seed pool empty: match not started");
			error(ca.link, "seed pool empty");
			error(cb.link, "seed pool empty");
			return;
		}
		Match m = new Match(id, a.platform, seed, now,
				new Side(a.playerId, ca.name, a.rating, token()), new Side(b.playerId, cb.name, b.rating, token()));
		matches.put(id, m);
		for (int i = 0; i < 2; i++) {
			Side s = m.sides[i], o = m.sides[1 - i];
			Client c = byPlayer.get(s.player);
			s.link = c.link;
			c.match = m;
			byToken.put(s.token, m);
			JsonObject d = new JsonObject();
			d.addProperty("opponent", o.name);
			d.addProperty("opponentRating", o.rating);
			d.addProperty("resumeToken", s.token);
			d.addProperty("finalBossDepth", cfg.rules.finalBossDepth);
			d.addProperty("timeLimitMs", cfg.rules.timeLimitMs);
			s.link.send(new Frame(Frame.MATCH_FOUND, 0, id, d));
		}
	}

	private void ready(Client c, long now) {
		Match m = c.match;
		if (m == null || m.state != State.PREPARING) {
			error(c.link, "no match waiting for READY");
			return;
		}
		side(m, c.player).ready = true;
		if (m.sides[0].ready && m.sides[1].ready) {
			m.state = State.COUNTDOWN;
			m.t0 = now + cfg.countdownMs;
			// the seed stays secret until both players committed to the match (L18)
			for (Side s : m.sides) s.link.send(new Frame(Frame.START, 0, m.id, startData(m)));
		}
	}

	private JsonObject startData(Match m) {
		JsonObject d = new JsonObject();
		d.addProperty("seed", m.seed);
		d.addProperty("t0", m.t0);
		return d;
	}

	private void resume(Client c, Frame f) {
		String token = f.s("resumeToken");
		Match m = token == null ? null : byToken.get(token);
		if (m == null || (m.state != State.COUNTDOWN && m.state != State.RUNNING)) {
			error(c.link, "nothing to resume");
			return;
		}
		Side s = m.sides[0].token.equals(token) ? m.sides[0] : m.sides[1];
		if (s.player != c.player) {
			error(c.link, "token belongs to another player");
			return;
		}
		s.link = c.link;
		s.p.disconnectedSince = -1;
		c.match = m;
		JsonObject d = startData(m);
		d.addProperty("depth", s.p.depth);
		d.addProperty("turns", s.p.turns);
		d.addProperty("logCount", s.log.size());
		c.link.send(new Frame(Frame.RESUMED, 0, m.id, d));
		opponent(m, s);
		opponent(m, other(m, s));
	}

	private void log(Client c, Frame f) {
		Match m = c.match;
		// the first part (hero class, no commands yet) comes with START, during the countdown
		if (m == null || (m.state != State.RUNNING && m.state != State.COUNTDOWN) || (f.matchId != null && !f.matchId.equals(m.id))) {
			error(c.link, "no running match");
			return;
		}
		Side s = side(m, c.player);
		if (s.p.deathAt >= 0 || s.p.clearedAt >= 0) {
			error(c.link, "log is closed");
			return;
		}
		int from = f.i("from", -1);
		String cls = f.s("cls"), text = f.s("cmds");
		if (from < 0 || text == null) {
			error(c.link, "LOG needs from and cmds");
			return;
		}
		if (s.heroClass == null) {
			if (from != 0 || cls == null) {
				error(c.link, "first LOG needs from 0 and cls");
				return;
			}
			s.heroClass = cls;
		} else if (cls != null && !cls.equals(s.heroClass)) {
			error(c.link, "hero class changed");
			return;
		}
		if (from > s.log.size()) {
			JsonObject d = new JsonObject();
			d.addProperty("message", "log gap");
			d.addProperty("expected", s.log.size());
			c.link.send(new Frame(Frame.ERROR, 0, m.id, d));
			return;
		}
		int i = from;
		for (String line : text.split("\n")) {
			line = line.trim();
			if (line.isEmpty()) continue;
			// commands already received (a resend) are skipped
			if (i++ < s.log.size()) continue;
			if (s.log.size() >= cfg.maxLogCommands) {
				error(c.link, "log too long");
				return;
			}
			s.log.add(line);
		}
	}

	private void report(Client c, Frame f, long now) throws SQLException {
		Match m = c.match;
		if (m == null || m.state != State.RUNNING || (f.matchId != null && !f.matchId.equals(m.id))) {
			error(c.link, "no running match");
			return;
		}
		Side s = side(m, c.player);
		long t = m.clock(now);
		if (f.type.equals(Frame.RESIGN)) {
			s.p.forfeited = true;
		} else if (s.p.deathAt < 0 && s.p.clearedAt < 0) {
			int depth = f.i("depth", s.p.depth);
			long turns = f.l("turns", s.p.turns);
			int bosses = f.i("bosses", s.p.bossesKilled);
			// progress only moves forward; a lower number is a client bug or a lie
			if (depth < s.p.depth || turns < s.p.turns || bosses < s.p.bossesKilled || depth > cfg.rules.finalBossDepth) {
				error(c.link, "bad progress");
				return;
			}
			if (f.type.equals(Frame.CLEARED) && depth < cfg.rules.finalBossDepth) {
				error(c.link, "final boss is on depth " + cfg.rules.finalBossDepth);
				return;
			}
			s.p.depth = depth;
			s.p.turns = turns;
			s.p.bossesKilled = bosses;
			if (f.type.equals(Frame.DIED)) s.p.deathAt = t;
			if (f.type.equals(Frame.CLEARED)) s.p.clearedAt = t;
			opponent(m, s);
		}
		judge(m, now);
	}

	/** ghost data: only floor and boss milestones (DECISIONS 7.3), never map or inventory */
	private void opponent(Match m, Side s) {
		Side o = other(m, s);
		if (o.link == null) return;
		JsonObject d = new JsonObject();
		d.addProperty("depth", s.p.depth);
		d.addProperty("bosses", s.p.bossesKilled);
		d.addProperty("dead", s.p.deathAt >= 0);
		d.addProperty("connected", s.link != null);
		o.link.send(new Frame(Frame.OPPONENT, 0, m.id, d));
	}

	private void judge(Match m, long now) throws SQLException {
		if (m.state != State.RUNNING && m.state != State.COUNTDOWN) return;
		MatchEnd.Verdict v = MatchEnd.judge(m.sides[0].p, m.sides[1].p, m.clock(now), cfg.rules);
		if (!v.over()) return;
		// both players gone is not a contest (T0.5: both disconnected = void)
		if (v.reason == MatchEnd.Reason.DISCONNECT && v.outcome == MatchEnd.Outcome.DRAW) {
			voidMatch(m, now, "BOTH_DISCONNECTED", -1);
			return;
		}
		m.state = State.VERIFYING;
		m.pending = v;
		Evidence e = new Evidence(m.id, m.seed, v, cfg.rules, new SideLog(m.sides[0]), new SideLog(m.sides[1]));
		cfg.verifier.verify(e, check -> verified(m, check));
	}

	/** the verifier's answer, right away (inside judge) or later from the verifier's thread */
	private synchronized void verified(Match m, Check check) {
		if (m.state != State.VERIFYING || matches.get(m.id) != m) return;
		try {
			if (Evidence.needsReplay(m.pending)) {
				for (int i = 0; i < 2; i++) {
					Side s = m.sides[i];
					db.saveReplay(m.id, i, s.heroClass, String.join("\n", s.log), check.result.name(), check.detail[i]);
				}
			}
			if (check.result == Result.ACCEPT || (check.result == Result.UNVERIFIABLE && cfg.acceptUnverifiable)) {
				finish(m, m.pending, lastNow);
			} else {
				voidMatch(m, lastNow, check.result == Result.REJECT ? "REJECTED" : "UNVERIFIABLE", -1);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	private void finish(Match m, MatchEnd.Verdict v, long now) throws SQLException {
		m.state = State.FINISHED;
		db.endMatch(m.id, m.state.name(), v.outcome.name(), v.reason.name(), now);
		double scoreA = v.outcome == MatchEnd.Outcome.A_WINS ? 1 : v.outcome == MatchEnd.Outcome.B_WINS ? 0 : 0.5;
		Glicko2.Rating ra = db.rating(m.sides[0].player, cfg.season, m.platform);
		Glicko2.Rating rb = db.rating(m.sides[1].player, cfg.season, m.platform);
		Glicko2.Rating na = Glicko2.update(ra, rb, scoreA), nb = Glicko2.update(rb, ra, 1 - scoreA);
		db.setRating(m.sides[0].player, cfg.season, m.platform, na);
		db.setRating(m.sides[1].player, cfg.season, m.platform, nb);
		for (int i = 0; i < 2; i++) {
			double score = i == 0 ? scoreA : 1 - scoreA;
			// leaving a match on purpose or for good counts toward the queue lock
			if (score == 0 && (v.reason == MatchEnd.Reason.FORFEIT || v.reason == MatchEnd.Reason.DISCONNECT)) {
				db.strike(m.sides[i].player, now, v.reason.name());
			}
			result(m, m.sides[i], score == 1 ? "WIN" : score == 0 ? "LOSS" : "DRAW", v.reason.name(),
					i == 0 ? ra : rb, i == 0 ? na : nb);
		}
		close(m);
	}

	private void voidMatch(Match m, long now, String reason, long strikePlayer) throws SQLException {
		m.state = State.VOIDED;
		db.endMatch(m.id, m.state.name(), "VOID", reason, now);
		if (strikePlayer >= 0) db.strike(strikePlayer, now, reason);
		for (Side s : m.sides) result(m, s, "VOID", reason, null, null);
		close(m);
	}

	private static void result(Match m, Side s, String outcome, String reason, Glicko2.Rating before, Glicko2.Rating after) {
		if (s.link == null) return;
		JsonObject d = new JsonObject();
		d.addProperty("outcome", outcome);
		d.addProperty("reason", reason);
		if (before != null) {
			d.addProperty("ratingBefore", before.rating);
			d.addProperty("ratingAfter", after.rating);
		}
		s.link.send(new Frame(Frame.RESULT, 0, m.id, d));
	}

	private void close(Match m) {
		matches.remove(m.id);
		for (Side s : m.sides) byToken.remove(s.token);
	}

	private static Side side(Match m, long player) {
		return m.sides[0].player == player ? m.sides[0] : m.sides[1];
	}

	private static Side other(Match m, Side s) {
		return m.sides[0] == s ? m.sides[1] : m.sides[0];
	}

	private String token() {
		byte[] b = new byte[18];
		tokens.nextBytes(b);
		StringBuilder s = new StringBuilder();
		for (byte x : b) s.append(String.format("%02x", x));
		return s.toString();
	}

	private static void error(Link link, String msg) {
		JsonObject d = new JsonObject();
		d.addProperty("message", msg);
		link.send(new Frame(Frame.ERROR, d));
	}

	// monitoring and tests
	public synchronized int queued() { return queue.size(); }
	public synchronized int activeMatches() { return matches.size(); }
}
