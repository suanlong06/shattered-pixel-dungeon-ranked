package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayCheck;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T0.8 / A3 with bosses: {@link BossBot} plays a Ranked run from floor 1 through Goo, Tengu and
 * DM-300 to floor 16 with player commands only, then the server's replay (ReplayCheck) plays
 * the same commands: it must follow every one, see the same three boss kills, and reach the same
 * game, turn for turn.
 */
class BossBotTest {

	@org.junit.jupiter.api.AfterEach
	void tearDown(){
		// ReplayCheck starts Ranked runs; later tests in this JVM expect Solo
		com.shatteredpixel.shatteredpixeldungeon.ranked.Features.setMode( com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode.SOLO );
	}

	static final class Run {
		final List<Command> cmds;
		final String trace;
		final int depth;
		final int[] bossScores;
		final long turns;
		Run( List<Command> cmds, TurnRunner r ){
			this.cmds = cmds;
			this.trace = r.traceDigest();
			this.depth = Statistics.deepestFloor;
			this.bossScores = Statistics.bossScores.clone();
			this.turns = (long) Statistics.duration;
		}
	}

	static Run live( long seed, HeroClass cls ){
		Headless.boot();
		TurnRunner r = ReplayCheck.start( seed, cls );
		BossBot.KIT.run();
		BossBot bot = new BossBot( 16 );
		bot.debug = new ArrayList<>();
		r.run( bot, 20_000 );
		int n = bot.debug.size();
		String why = cls + " seed " + seed + ": depth " + Dungeon.depth + ", " + r.stop + ", last inputs (command@hero cell) "
				+ bot.debug.subList( Math.max( 0, n - 40 ), n );
		assertEquals( "input ended", r.stop, why );
		assertEquals( 16, Dungeon.depth, why );
		return new Run( new ArrayList<>( bot.issued ), r );
	}

	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> runs(){
		java.util.List<org.junit.jupiter.params.provider.Arguments> out = new ArrayList<>();
		for (long seed : GoldenDigestTest.LEVEL_SEEDS) for (HeroClass cls : HeroClass.values()) out.add( org.junit.jupiter.params.provider.Arguments.of( seed, cls ) );
		return out.stream();
	}

	@org.junit.jupiter.params.ParameterizedTest( name = "{0} {1}" )
	@org.junit.jupiter.params.provider.MethodSource( "runs" )
	void botBeatsTheThreeBossesAndTheReplayAgrees( long seed, HeroClass cls ){
		long t0 = System.nanoTime();
		Run run = live( seed, cls );
		long t1 = System.nanoTime();
		assertEquals( 16, run.depth, "the bot reached floor 16" );
		assertTrue( Dungeon.hero.isAlive() );

		ReplayCheck.Facts f = ReplayCheck.replay( seed, cls, run.cmds, BossBot.KIT );
		long t2 = System.nanoTime();
		System.out.println( "boss run " + seed + " " + cls + ": " + run.cmds.size() + " commands, " + run.turns + " turns, live "
				+ (t1 - t0) / 1_000_000 + " ms, replay " + (t2 - t1) / 1_000_000 + " ms, boss scores " + Arrays.toString( run.bossScores ) );
		assertNull( f.unsupported, f.toString() );
		assertEquals( run.cmds.size(), f.commandsApplied, f.toString() );
		assertEquals( "input ended", f.stop );
		assertEquals( 16, f.depth );
		assertTrue( f.bosses[0] && f.bosses[1] && f.bosses[2], f.toString() );
		assertTrue( f.cleared( 15 ) );
		assertEquals( 3, f.bossesKilled( 15 ) );
		assertEquals( run.turns, f.turns );
		assertEquals( run.trace, f.traceDigest, "the replay played the same game" );
		assertTrue( Arrays.equals( run.bossScores, Statistics.bossScores ), Arrays.toString( Statistics.bossScores ) );
	}
}
