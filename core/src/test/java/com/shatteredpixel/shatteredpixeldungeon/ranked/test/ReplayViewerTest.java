package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrimTrap;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.CleanProfile;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.replay.ReplayFile;
import com.shatteredpixel.shatteredpixeldungeon.ranked.replay.ReplayPlayback;
import com.shatteredpixel.shatteredpixeldungeon.ranked.replay.ReplayViewer;
import com.shatteredpixel.shatteredpixeldungeon.ranked.replay.Replays;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayCheck;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayInput;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.noosa.Game;
import com.watabou.utils.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.Player;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.button;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.click;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.farCell;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.freeNeighbour;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.option;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.tap;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.top;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A5 replay viewer: watching a replay the way the game scene drives it (ReplayPlayback, the
 * game's frames of varying length, pause, speed changes, single steps, level changes done by the
 * host) gives exactly the game that was recorded and that the server's replay check sees.
 */
class ReplayViewerTest {

	static final long SEED = ChoiceReplayTest.SEED;

	@AfterEach
	void tearDown(){
		CommandLog.stop();
		Features.setViewingReplay( false );
		Features.setMode( GameMode.SOLO );
		TurnRunner.frameSeconds = 1 / 30f;
	}

	/**
	 * Watches with a viewer that keeps pressing pause, slower, faster and step at random frames,
	 * on a render loop whose frames last anywhere from 1/240 s to the 0.2 s cap, scaled by the speed.
	 * The runner is a viewer runner: the "host" does level changes when it asks (as InterlevelScene).
	 */
	static ReplayPlayback watch( ReplayPlayback pb, long viewerSeed ){
		java.util.Random viewer = new java.util.Random( viewerSeed );
		pb.runner.hostScenes = true;
		TurnRunner.beginViewing();
		try {
			for (int f = 0; f < 2_000_000; f++){
				switch (viewer.nextInt( 60 )){
					case 0: pb.togglePause(); break;
					case 1: pb.faster(); break;
					case 2: pb.slower(); break;
					case 3: pb.stepOne(); break;
					default: break;
				}
				ReplayPlayback.Result res = pb.frame();
				if (res == ReplayPlayback.Result.ENDED) return pb;
				if (res == ReplayPlayback.Result.SWITCH){
					assertTrue( pb.runner.transitionHeadless(), pb.runner.stop );
					pb.sceneBuilt();
					continue;
				}
				float dt = 1 / 240f + viewer.nextFloat() * (0.2f - 1 / 240f);
				pb.runner.frame( dt * pb.timeScale() );
			}
		} finally {
			TurnRunner.endViewing();
		}
		throw new AssertionError( "replay did not end: " + pb.done() + "/" + pb.total() );
	}

	/** a live Ranked game from ChoiceReplayTest's scripts, then watched as the viewer does */
	private static void liveThenWatch( HeroClass cls, Runnable setup, Player player, long viewerSeed ){
		ChoiceReplayTest.Played p = ChoiceReplayTest.playAndReplay( cls, setup, player );
		ChoiceReplayTest.assertSameGame( p );

		Random.resetGenerators();
		TurnRunner r = ReplayCheck.start( SEED, cls );
		if (setup != null) setup.run();
		ReplayFile file = new ReplayFile( Game.versionCode, SEED, cls, GameMode.RANKED, "",
				CommandLog.fromText( CommandLog.toText( p.log ) ) );
		ReplayPlayback pb = watch( new ReplayPlayback( file, r ), viewerSeed );

		assertTrue( pb.finished(), pb.ended() );
		assertNull( pb.input.unsupported );
		assertEquals( p.log.size(), r.decisions );
		assertEquals( p.live.stateSequenceDigest(), r.stateSequenceDigest(), "watching differs from the live game\nlog:\n" + CommandLog.toText( p.log ) );
		assertEquals( p.replay.stateDigest, r.stateSequenceDigest(), "watching differs from the server's replay" );
	}

	@Test
	void windowsCancelsAndFallingWatchLikeTheLiveGame(){
		// item selector, confirm window, upgrade window (BACK, BTN, SELECT), then a jump into a chasm (FALL)
		com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll[] sou = new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll[1];
		com.shatteredpixel.shatteredpixeldungeon.items.Item[] weapon = new com.shatteredpixel.shatteredpixeldungeon.items.Item[1];
		int[] pit = new int[1];
		Runnable setup = () -> {
			sou[0] = new com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade();
			sou[0].collect();
			weapon[0] = Dungeon.hero.belongings.weapon();
			pit[0] = freeNeighbour();
			Level.set( pit[0], Terrain.CHASM );
			GameScene.updateMap( pit[0] );
		};
		Player pl = new Player()
				.step( r -> ChoiceReplayTest.useItem( sou[0], com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll.AC_READ ) )
				.step( r -> ChoiceReplayTest.back( r ) )
				.step( r -> click( r, option( top(), 1 ) ) )
				.step( r -> click( r, ChoiceReplayTest.slot( top(), weapon[0] ) ) )
				.step( r -> click( r, button( top(), Messages.get( com.shatteredpixel.shatteredpixeldungeon.windows.WndUpgrade.class, "upgrade" ) ) ) )
				.step( r -> tap( pit[0] ) )
				.step( r -> click( r, option( top(), 0 ) ) )
				.step( r -> { assertEquals( 2, Dungeon.depth ); tap( freeNeighbour() ); } );
		liveThenWatch( HeroClass.WARRIOR, setup, pl, 1 );
		assertEquals( 2, Dungeon.depth );
		assertEquals( 1, Dungeon.hero.belongings.weapon().level(), "the watched game upgraded the weapon too" );
	}

	@Test
	void walkCancelledMidwayWatchesAtTheSameTurn(){
		int[] dest = new int[1];
		Player pl = new Player();
		pl.step( r -> {
			dest[0] = farCell( 8 );
			tap( dest[0] );
			pl.midAt = 1;
			pl.mid = () -> GameScene.cancel();
		} ).step( r -> tap( freeNeighbour() ) );
		liveThenWatch( HeroClass.ROGUE, ChoiceReplayTest.NO_MOBS, pl, 2 );
	}

	@Test
	void deathWithAnAnkhAndResurrectionWatches(){
		int[] trap = new int[1];
		Runnable setup = () -> {
			new Ankh().collect();
			Dungeon.hero.HP = 1;
			trap[0] = freeNeighbour();
			Dungeon.level.setTrap( new GrimTrap().reveal(), trap[0] );
			Level.set( trap[0], Terrain.TRAP );
			GameScene.updateMap( trap[0] );
		};
		Player pl = new Player()
				.step( r -> tap( trap[0] ) )
				.step( r -> click( r, button( top(), Messages.get( WndResurrect.class, "confirm" ) ) ) )
				.step( r -> tap( freeNeighbour() ) );
		liveThenWatch( HeroClass.CLERIC, setup, pl, 3 );
		assertTrue( Dungeon.hero.isAlive() );
	}

	@Test
	void longBotRunWithStairsWatchesTheSameWhateverTheViewerDoes(){
		Headless.boot();
		// god mode: a death would leave remains (Solo) that the next run on the seed finds
		TurnRunner a = Sim.play( SEED, HeroClass.HUNTRESS, new Bot( new Bot.Scenario( true, 0, 4 ) ), 400 );
		assertTrue( a.decisions > 100, "bot made too few inputs: " + a.stop );
		assertTrue( Dungeon.depth >= 2, "the run should take the stairs: " + a.stop );
		List<Command> log = new ArrayList<>( a.applied );

		for (long viewerSeed = 10; viewerSeed < 13; viewerSeed++){
			Bot.Scenario sc = new Bot.Scenario( true, 0, 4 );
			TurnRunner r = Sim.start( SEED, HeroClass.HUNTRESS );
			ReplayFile file = new ReplayFile( Game.versionCode, SEED, HeroClass.HUNTRESS, GameMode.SOLO, a.traceDigest(), log );
			ReplayPlayback pb = watch( new ReplayPlayback( file, r, new ReplayInput( log ){
				@Override
				protected void beforeCommand(){ sc.before(); }
			} ), viewerSeed );
			assertTrue( pb.finished(), pb.ended() );
			assertEquals( a.trace, r.trace, "viewer " + viewerSeed );
			assertEquals( file.digest, r.traceDigest() );
		}
	}

	@Test
	void pauseFreezesAndStepPlaysOneInput(){
		Headless.boot();
		TurnRunner a = Sim.play( SEED, HeroClass.WARRIOR, new Bot( new Bot.Scenario( true, 0, 2 ) ), 30 );
		List<Command> log = new ArrayList<>( a.applied );
		TurnRunner r = Sim.start( SEED, HeroClass.WARRIOR );
		r.hostScenes = true;
		Bot.Scenario sc = new Bot.Scenario( true, 0, 2 );
		ReplayPlayback pb = new ReplayPlayback( new ReplayFile( 1, SEED, HeroClass.WARRIOR, GameMode.SOLO, "", log ), r,
				new ReplayInput( log ){ @Override protected void beforeCommand(){ sc.before(); } } );
		TurnRunner.beginViewing();
		try {
			pb.togglePause();
			assertTrue( pb.paused() );
			assertEquals( 0f, pb.timeScale() );
			for (int i = 0; i < 100; i++){ pb.frame(); r.frame( 0 ); }
			assertEquals( 0, r.decisions, "nothing happens while paused" );

			for (int n = 1; n <= 3; n++){
				pb.stepOne();
				assertFalse( pb.paused() );
				for (int i = 0; i < 100_000 && !pb.paused(); i++){
					assertEquals( ReplayPlayback.Result.RUNNING, pb.frame() );
					r.frame( 1 / 60f * pb.timeScale() );
				}
				assertTrue( pb.paused() );
				assertEquals( n, r.decisions, "one input per step" );
				assertTrue( r.waitsForInput(), "stops where the hero waits for the next input" );
			}
			pb.togglePause();
			pb.faster();
			assertEquals( ReplayPlayback.SPEEDS[ReplayPlayback.DEFAULT_SPEED + 1], pb.timeScale() );
		} finally {
			TurnRunner.endViewing();
		}
	}

	@Test
	void replayFileKeepsChallengesAndLabelAndReadsOldFiles(){
		List<Command> cmds = new ArrayList<>();
		cmds.add( Command.cell( 5 ) );
		cmds.add( Command.cancel( false ).at( 2 ) );
		ReplayFile f = new ReplayFile( 922, 7L, HeroClass.MAGE, GameMode.RANKED, "", cmds, 3, "vs Bob\n- won" );
		ReplayFile back = ReplayFile.fromText( f.toText() );
		assertEquals( 3, back.challenges );
		assertEquals( "vs Bob - won", back.label );
		assertEquals( cmds, back.commands );
		assertEquals( f.toText(), back.toText() );

		ReplayFile old = ReplayFile.fromText( "spdr-replay v1\nversion=1\nseed=7\nclass=MAGE\nmode=RANKED\ndigest=\n---\nCELL 5\n" );
		assertEquals( 0, old.challenges );
		assertEquals( "", old.label );
		assertFalse( old.toText().contains( "challenges=" ), "files without them stay as they were" );
	}

	@Test
	void savedReplaysAreListedNewestFirstAndBrokenOnesShown(){
		Headless.boot();
		Gdx.files.local( Replays.DIR ).deleteDirectory();
		try {
			List<Command> cmds = new ArrayList<>();
			cmds.add( Command.rest( false ) );
			String first = Replays.save( new ReplayFile( 1, 1L, HeroClass.WARRIOR, GameMode.RANKED, "", cmds, 0, "first" ), 1_000_000L );
			String second = Replays.save( new ReplayFile( 1, 2L, HeroClass.ROGUE, GameMode.RANKED, "", cmds, 0, "second" ), 1_000_000L );
			assertTrue( first != null && second != null && !first.equals( second ), first + " " + second );
			Gdx.files.local( Replays.DIR + first ).file().setLastModified( 1_000_000L );
			Gdx.files.local( Replays.DIR + second ).file().setLastModified( 2_000_000L );
			Gdx.files.local( Replays.DIR + "junk.txt" ).writeString( "hello", false );
			Gdx.files.local( Replays.DIR + "junk.txt" ).file().setLastModified( 500_000L );

			List<Replays.Entry> list = Replays.list();
			assertEquals( 3, list.size() );
			assertEquals( "second", list.get( 0 ).replay.label );
			assertEquals( 2L, list.get( 0 ).replay.seed );
			assertEquals( "first", list.get( 1 ).replay.label );
			assertNull( list.get( 2 ).replay );
			assertTrue( list.get( 2 ).error != null );

			assertFalse( Replays.delete( "../" + first ) );
			assertTrue( Replays.delete( first ) );
			assertEquals( 2, Replays.list().size() );
		} finally {
			Gdx.files.local( Replays.DIR ).deleteDirectory();
		}
	}

	@Test
	void watchingLeavesTheProfileAloneAndUsesTheReplaysChallenges(){
		Features.setMode( GameMode.SOLO );
		assertFalse( Features.leavesProfileAlone() );
		assertTrue( CleanProfile.bonesAllowed() );
		Features.setViewingReplay( true );
		assertTrue( Features.leavesProfileAlone(), "no badges, rankings, journal or ghosts" );
		assertFalse( CleanProfile.bonesAllowed(), "no remains read or left" );
		Features.setViewingReplay( false );
		assertEquals( 5, ReplayViewer.challenges( 5 ), "not watching: the player's settings" );
		assertFalse( ReplayViewer.active() );
	}
}
