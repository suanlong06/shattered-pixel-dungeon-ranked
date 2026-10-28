package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonObject;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.ranked.duel.MatchEnd;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayCheck;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.watabou.utils.PathFinder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** A3: the server replays real command logs and only accepts reports the replay agrees with. */
class ReplayVerifierTest {

	/** the first of these seeds on which the explorer reaches floor 2 alive */
	static final long[] SEEDS = {12345L, 1L, 2L, 3L, 4L, 5L, 6L, 7L};
	static long SEED;

	/** one honest run: a player who explores by tapping, fights, rests and takes the stairs */
	static List<String> log;
	static Hub.SideLog honest;

	@BeforeAll
	static void play() {
		ReplayVerifier.boot();
		String why = "";
		for (long seed : SEEDS) {
			TurnRunner r = ReplayCheck.start(seed, HeroClass.WARRIOR);
			r.run(new Explorer(3), 1500);
			why += seed + ": depth " + Statistics.deepestFloor + ", " + r.stop + "; ";
			if (Statistics.deepestFloor < 2 || !Dungeon.hero.isAlive()) continue;
			SEED = seed;
			log = new ArrayList<>();
			for (Command c : r.applied) log.add(c.toString());
			honest = new Hub.SideLog("WARRIOR", log, Statistics.deepestFloor, 0, (long) Statistics.duration, false, false);
			return;
		}
		fail("explorer never reached floor 2 alive: " + why);
	}

	static Hub.SideLog claim(Hub.SideLog s, int depth, long turns, boolean dead, List<String> cmds, String cls) {
		return new Hub.SideLog(cls, cmds, depth, s.bossesKilled, turns, dead, s.cleared);
	}

	/** side B: never moved; its truth comes from the replay of an empty log */
	static Hub.SideLog idle() {
		ReplayCheck.Facts f = ReplayVerifier.replay(SEED, HeroClass.MAGE, Collections.emptyList());
		return new Hub.SideLog("MAGE", Collections.emptyList(), f.depth, 0, f.turns, false, false);
	}

	static Hub.Evidence evidence(Hub.SideLog a, Hub.SideLog b, MatchEnd.Reason reason) {
		MatchEnd.Verdict v = new MatchEnd.Verdict(MatchEnd.Outcome.A_WINS, reason);
		return new Hub.Evidence("m1", SEED, v, MatchEnd.Rules.defaults(), a, b);
	}

	@Test
	void honestLogsAreAccepted() {
		Hub.Check c = ReplayVerifier.check(evidence(honest, idle(), MatchEnd.Reason.TIMEOUT_DEPTH));
		assertEquals(Hub.Result.ACCEPT, c.result, Arrays.toString(c.detail));
	}

	@Test
	void leagueRunsReplayLikeADuelSide() {
		assertEquals(Hub.Result.ACCEPT, ReplayVerifier.checkRun(SEED, 15, honest).result);
		Hub.SideLog deeper = new Hub.SideLog("WARRIOR", log, honest.depth + 1, 0, honest.turns, false, false);
		Hub.Check c = ReplayVerifier.checkRun(SEED, 15, deeper);
		assertEquals(Hub.Result.REJECT, c.result, c.detail[0]);
		assertNotEquals(Hub.Result.ACCEPT, ReplayVerifier.checkRun(SEED + 1000, 15, honest).result,
				"a run on another week's seed must not pass");
	}

	@Test
	void replayIsTheSameEachTime() {
		ReplayCheck.Facts a = ReplayVerifier.replay(SEED, HeroClass.WARRIOR, parse(log));
		ReplayCheck.Facts b = ReplayVerifier.replay(SEED, HeroClass.WARRIOR, parse(log));
		assertEquals(a.traceDigest, b.traceDigest);
		assertEquals(honest.turns, a.turns);
	}

	@Test
	void claimsTheReplayDoesNotShowAreRejected() {
		Hub.SideLog b = idle();
		Hub.SideLog[] lies = {
				claim(honest, honest.depth + 1, honest.turns, false, log, "WARRIOR"),          // deeper than played
				claim(honest, honest.depth, honest.turns - 5, false, log, "WARRIOR"),          // fewer turns
				claim(honest, honest.depth, honest.turns, false, log.subList(0, 10), "WARRIOR"), // log cut before the stairs
				new Hub.SideLog("WARRIOR", log, honest.depth, 1, honest.turns, false, false),  // a boss never fought
		};
		for (Hub.SideLog lie : lies) {
			Hub.Check c = ReplayVerifier.check(evidence(lie, b, MatchEnd.Reason.TIMEOUT_DEPTH));
			assertEquals(Hub.Result.REJECT, c.result, lie.depth + " " + lie.turns + " " + lie.commands.size() + " " + lie.bossesKilled + " " + Arrays.toString(c.detail));
		}
		Hub.Check garbage = ReplayVerifier.check(evidence(claim(honest, honest.depth, honest.turns, false,
				Arrays.asList("CELL 1", "TELEPORT 5"), "WARRIOR"), b, MatchEnd.Reason.TIMEOUT_DEPTH));
		assertEquals(Hub.Result.REJECT, garbage.result);
	}

	@Test
	void whatReplayCannotFollowIsUnverifiableNotRejected() {
		// a command the runner cannot apply (the real client may have used a window choice T0.8 does not record)
		List<String> cmds = new ArrayList<>(log);
		cmds.add("ITEM 99 DROP");
		Hub.Check c = ReplayVerifier.check(evidence(claim(honest, honest.depth, honest.turns, false, cmds, "WARRIOR"),
				idle(), MatchEnd.Reason.TIMEOUT_DEPTH));
		assertEquals(Hub.Result.UNVERIFIABLE, c.result, Arrays.toString(c.detail));
	}

	@Test
	void forfeitNeedsNoReplay() throws Exception {
		Hub.Check[] got = new Hub.Check[1];
		try (ReplayVerifier v = new ReplayVerifier()) {
			v.verify(evidence(claim(honest, 99, 0, false, log, "nonsense"), idle(), MatchEnd.Reason.FORFEIT), c -> got[0] = c);
		}
		assertEquals(Hub.Result.ACCEPT, got[0].result);
	}

	@Test
	void hubWritesRatingOnlyAfterReplayAgrees() throws Exception {
		assertEquals("WIN", playMatch(honest.turns).s("outcome"));
		Frame lied = playMatch(honest.turns - 5);
		assertEquals("VOID", lied.s("outcome"));
		assertEquals("REJECTED", lied.s("reason"));
	}

	/** a whole match through the hub: A plays the honest log, B idles, time runs out; returns A's RESULT */
	private Frame playMatch(long reportedTurns) throws Exception {
		Storage db = new Storage("jdbc:sqlite::memory:");
		db.addSeeds(Collections.singletonList(SEED));
		Hub.Config cfg = new Hub.Config();
		cfg.rules = new MatchEnd.Rules(15, 60_000, 300_000);
		CountDownLatch answered = new CountDownLatch(1);
		ReplayVerifier rv = new ReplayVerifier();
		cfg.verifier = (e, done) -> rv.verify(e, c -> { done.accept(c); answered.countDown(); });
		long now = 1_000_000;
		Hub hub = new Hub(cfg, db, now);
		HubTest.FakeLink a = hello(hub, "alice", now), b = hello(hub, "bob", now);
		hub.receive(a, frame(Frame.QUEUE), now);
		hub.receive(b, frame(Frame.QUEUE), now);
		hub.tick(now);
		hub.receive(a, frame(Frame.READY), now);
		hub.receive(b, frame(Frame.READY), now);
		now += cfg.countdownMs;
		hub.tick(now);

		Hub.SideLog idle = idle();
		JsonObject la = new JsonObject();
		la.addProperty("from", 0);
		la.addProperty("cls", "WARRIOR");
		// sent in two parts, the second overlapping the first as after a RESUME
		la.addProperty("cmds", String.join("\n", log.subList(0, log.size() / 2)));
		hub.receive(a, new Frame(Frame.LOG, la), now);
		la.addProperty("from", log.size() / 4);
		la.addProperty("cmds", String.join("\n", log.subList(log.size() / 4, log.size())));
		hub.receive(a, new Frame(Frame.LOG, la), now);
		hub.receive(a, frame(Frame.PROGRESS, "depth", honest.depth, "turns", reportedTurns), now);
		JsonObject lb = new JsonObject();
		lb.addProperty("from", 0);
		lb.addProperty("cls", "MAGE");
		lb.addProperty("cmds", "");
		hub.receive(b, new Frame(Frame.LOG, lb), now);
		hub.receive(b, frame(Frame.PROGRESS, "depth", idle.depth, "turns", idle.turns), now);

		now += 60_000;
		hub.tick(now);
		assertNull(a.last(Frame.RESULT), "no result before the replay answered");
		assertTrue(answered.await(120, TimeUnit.SECONDS));
		rv.close();
		String[] stored = db.replayResult(a.last(Frame.MATCH_FOUND).matchId, 0);
		assertNotNull(stored);
		db.close();
		return a.last(Frame.RESULT);
	}

	private static HubTest.FakeLink hello(Hub hub, String name, long now) throws Exception {
		HubTest.FakeLink l = new HubTest.FakeLink();
		hub.connect(l);
		JsonObject d = new JsonObject();
		d.addProperty("protocol", Frame.PROTOCOL);
		d.addProperty("build", "dev");
		d.addProperty("platform", "desktop");
		d.addProperty("deviceKey", name + "-device-key-000");
		d.addProperty("name", name);
		hub.receive(l, new Frame(Frame.HELLO, d), now);
		return l;
	}

	private static Frame frame(String type, Object... kv) {
		JsonObject d = new JsonObject();
		for (int i = 0; i < kv.length; i += 2) d.addProperty((String) kv[i], (Number) kv[i + 1]);
		return new Frame(type, d);
	}

	private static List<Command> parse(List<String> lines) {
		List<Command> out = new ArrayList<>();
		for (String l : lines) out.add(Command.parse(l));
		return out;
	}

	/**
	 * Plays like a person tapping the screen, from what the hero knows: attack an adjacent enemy,
	 * rest when hurt, take the stairs once seen, else walk to the nearest known cell next to the
	 * unknown. Reads game state only, never changes it, so its commands are an honest log.
	 */
	static final class Explorer implements TurnRunner.Input {
		final int maxDepth;
		int lastPos = -1, still;

		Explorer(int maxDepth) { this.maxDepth = maxDepth; }

		@Override
		public Command next(TurnRunner r) {
			Hero hero = Dungeon.hero;
			Level level = Dungeon.level;
			still = hero.pos == lastPos ? still + 1 : 0;
			lastPos = hero.pos;
			if (still > 30) return null;
			for (int n : PathFinder.NEIGHBOURS8) {
				Char c = Actor.findChar(hero.pos + n);
				if (c instanceof Mob && c.alignment == Char.Alignment.ENEMY) return Command.cell(c.pos);
			}
			if (hero.HP < hero.HT * 7 / 10) return Command.rest(false);
			LevelTransition exit = level.getTransition(LevelTransition.Type.REGULAR_EXIT);
			if (exit != null && (level.visited[exit.cell()] || level.mapped[exit.cell()])) {
				if (Dungeon.depth >= maxDepth) return null;
				return Command.cell(exit.cell());
			}
			int target = frontier(hero.pos, level);
			return target < 0 ? null : Command.cell(target);
		}

		private static boolean known(Level l, int c) { return l.visited[c] || l.mapped[c]; }

		/** nearest known passable cell with an unknown neighbour, by walking distance */
		private static int frontier(int from, Level l) {
			int[] dist = new int[l.length()];
			Arrays.fill(dist, -1);
			ArrayDeque<Integer> q = new ArrayDeque<>();
			dist[from] = 0;
			q.add(from);
			while (!q.isEmpty()) {
				int c = q.poll();
				if (c != from) {
					for (int n : PathFinder.NEIGHBOURS8) {
						int x = c + n;
						if (x >= 0 && x < l.length() && !known(l, x)) return c;
					}
				}
				for (int n : PathFinder.NEIGHBOURS8) {
					int x = c + n;
					if (x < 0 || x >= l.length() || dist[x] >= 0 || !l.passable[x] || !known(l, x)) continue;
					dist[x] = dist[c] + 1;
					q.add(x);
				}
			}
			return -1;
		}
	}
}
