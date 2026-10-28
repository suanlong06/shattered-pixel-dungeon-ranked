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
		// later tests build levels without a game scene (SeedScanner)
		if (Gdx.app != null) TurnRunner.leaveScene();
		Features.setViewingReplay( false );
		Features.setMode( GameMode.SOLO );
		TurnRunner.frameSeconds = 1 / 30f;
	}

	/**
	 * Watches with a viewer that keeps pressing pause, slower, faster, step and seek at random frames,
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
					case 4: pb.seekTo( pb.applied() + viewer.nextInt( 30 ) ); break;
					case 5: if (viewer.nextInt( 4 ) == 0) pb.seekNextFloor(); break;
					default: break;
				}
				ReplayPlayback.Result res = pb.frame();
				if (res == ReplayPlayback.Result.ENDED) return pb;
				if (res == ReplayPlayback.Result.SWITCH){
					// the viewer follows the game there (InterlevelScene, AlchemyScene) or stops
					assertNull( ReplayViewer.unfollowable( Game.pendingScene() ) );
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
	static void liveThenWatch( HeroClass cls, Runnable setup, Player player, long viewerSeed ){
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
	void brewingInTheAlchemySceneWatches(){
		Player pl = new Player()
				.step( r -> tap( AlchemyReplayTest.POT[0] ) )
				.step( r -> AlchemyReplayTest.putIn( r, 0, AlchemyReplayTest.seed() ) )
				.step( r -> AlchemyReplayTest.putIn( r, 1, AlchemyReplayTest.seed() ) )
				.step( r -> AlchemyReplayTest.putIn( r, 2, AlchemyReplayTest.seed() ) )
				.step( r -> click( r, AlchemyReplayTest.craftButton( 0 ) ) )
				.step( r -> com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon.seamlessResetScene() ) // alchemy guide: RESET
				.step( r -> AlchemyReplayTest.exit( r ) )
				.step( r -> tap( freeNeighbour() ) );
		liveThenWatch( HeroClass.WARRIOR, AlchemyReplayTest.SETUP, pl, 4 );
		assertEquals( 1, AlchemyReplayTest.potions(), "the watched game brewed it too" );
		assertTrue( Game.scene() instanceof GameScene );
	}

	@Test
	void theSurfaceAndAClientClosedInAlchemyAreNotFollowed(){
		Headless.boot();
		assertNull( ReplayViewer.unfollowable( com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class ) );
		assertTrue( ReplayViewer.unfollowable( com.shatteredpixel.shatteredpixeldungeon.scenes.SurfaceScene.class ) != null );
		assertTrue( ReplayViewer.unfollowable( com.shatteredpixel.shatteredpixeldungeon.scenes.TitleScene.class ) != null );
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

	/** plays frames of a playback until it pauses or ends, the host following level changes */
	static void untilPaused( ReplayPlayback pb, float dt ){
		for (int i = 0; i < 2_000_000 && !pb.paused(); i++){
			ReplayPlayback.Result res = pb.frame();
			if (res == ReplayPlayback.Result.ENDED) return;
			if (res == ReplayPlayback.Result.SWITCH){
				assertTrue( pb.runner.transitionHeadless(), pb.runner.stop );
				pb.sceneBuilt();
				continue;
			}
			pb.runner.frame( dt * pb.timeScale() );
		}
		assertTrue( pb.paused(), "never paused" );
	}

	/**
	 * Seeking back is the run again from the start, seeking forward to the input: it pauses where the
	 * hero waits after exactly that many inputs, in the state the recorded run had there, and playing
	 * on from there gives the whole recorded run. Floors too: it pauses on the next floor.
	 */
	@Test
	void seekingPausesAtTheInputInTheRecordedState(){
		Headless.boot();
		TurnRunner a = Sim.play( SEED, HeroClass.HUNTRESS, new Bot( new Bot.Scenario( true, 0, 4 ) ), 400 );
		assertTrue( Dungeon.depth >= 2, "the run should take the stairs: " + a.stop );
		List<Command> log = new ArrayList<>( a.applied );
		int floorAt = -1;
		for (int i = 1; i < a.trace.size(); i++){
			if (!a.trace.get( i ).startsWith( "1," )){ floorAt = i; break; }
		}
		assertTrue( floorAt > 0 );

		int[] targets = { 0, 1, 37, a.decisions / 2, a.decisions };
		for (int t : targets){
			Bot.Scenario sc = new Bot.Scenario( true, 0, 4 );
			TurnRunner r = Sim.start( SEED, HeroClass.HUNTRESS );
			ReplayFile file = new ReplayFile( Game.versionCode, SEED, HeroClass.HUNTRESS, GameMode.SOLO, a.traceDigest(), log );
			ReplayPlayback pb = new ReplayPlayback( file, r, new ReplayInput( log ){
				@Override
				protected void beforeCommand(){ sc.before(); }
			} );
			r.hostScenes = true;
			TurnRunner.beginViewing();
			try {
				pb.seekTo( t );
				assertTrue( pb.seeking() );
				assertFalse( pb.paused() );
				assertEquals( ReplayPlayback.SEEK_SPEED, pb.timeScale() );
				untilPaused( pb, 1 / 30f );
				assertNull( pb.ended() );
				assertEquals( t, r.decisions, "paused after the inputs sought to" );
				assertTrue( r.waitsForInput() );
				assertFalse( pb.seeking() );
				assertEquals( a.trace.get( t ), TurnRunner.stateDigest(), "the recorded run's state at input " + t );

				if (t == targets[1]){
					// then to the next floor
					pb.seekNextFloor();
					untilPaused( pb, 1 / 30f );
					assertEquals( floorAt, r.decisions, "paused at the first input on the next floor" );
					assertEquals( a.trace.get( floorAt ), TurnRunner.stateDigest() );
				}

				pb.togglePause();
				ReplayPlayback.Result res;
				do {
					res = pb.frame();
					if (res == ReplayPlayback.Result.SWITCH){
						assertTrue( pb.runner.transitionHeadless(), pb.runner.stop );
						pb.sceneBuilt();
					} else if (res == ReplayPlayback.Result.RUNNING){
						r.frame( 1 / 30f * pb.timeScale() );
					}
				} while (res != ReplayPlayback.Result.ENDED);
			} finally {
				TurnRunner.endViewing();
			}
			assertTrue( pb.finished(), pb.ended() );
			assertEquals( a.trace, r.trace, "seek to " + t );
		}
	}

	@Test
	void aWindowOfTheViewerHoldsThePlayback(){
		Headless.boot();
		TurnRunner a = Sim.play( SEED, HeroClass.WARRIOR, new Bot( new Bot.Scenario( true, 0, 2 ) ), 20 );
		List<Command> log = new ArrayList<>( a.applied );
		TurnRunner r = Sim.start( SEED, HeroClass.WARRIOR );
		r.hostScenes = true;
		Bot.Scenario sc = new Bot.Scenario( true, 0, 2 );
		ReplayPlayback pb = new ReplayPlayback( new ReplayFile( 1, SEED, HeroClass.WARRIOR, GameMode.SOLO, "", log ), r,
				new ReplayInput( log ){ @Override protected void beforeCommand(){ sc.before(); } } );
		TurnRunner.beginViewing();
		try {
			pb.hold( true );
			assertEquals( 0f, pb.timeScale() );
			pb.seekTo( 10 );
			for (int i = 0; i < 200; i++){ assertEquals( ReplayPlayback.Result.RUNNING, pb.frame() ); r.frame( 1 / 30f ); }
			assertEquals( 0, r.decisions, "nothing plays while held, not even a seek" );
			pb.hold( false );
			untilPaused( pb, 1 / 30f );
			assertEquals( 10, r.decisions );
			assertEquals( a.trace.get( 10 ), TurnRunner.stateDigest() );
		} finally {
			TurnRunner.endViewing();
		}
	}

	/** the inventory window and every item's description read the run without drawing from its RNG */
	@Test
	void theInventoryWindowLeavesTheRunAlone() throws Exception {
		Headless.boot();
		Sim.play( SEED, HeroClass.MAGE, new Bot( new Bot.Scenario( true, 0, 4 ) ), 150 );
		long[] rng = Random.getStackState();
		String state = TurnRunner.stateDigest();
		Class<?> c = Class.forName( "com.shatteredpixel.shatteredpixeldungeon.ranked.replay.WndReplayBag" );
		java.lang.reflect.Constructor<?> k = c.getDeclaredConstructor( ReplayViewer.class );
		k.setAccessible( true );
		Object w = k.newInstance( (ReplayViewer) null );
		java.lang.reflect.Method items = c.getDeclaredMethod( "items" );
		items.setAccessible( true );
		@SuppressWarnings("unchecked")
		List<com.shatteredpixel.shatteredpixeldungeon.items.Item> all = (List<com.shatteredpixel.shatteredpixeldungeon.items.Item>) items.invoke( null );
		assertTrue( all.size() >= 3, "the hero carries things: " + all );
		for (com.shatteredpixel.shatteredpixeldungeon.items.Item i : all){
			new com.shatteredpixel.shatteredpixeldungeon.windows.WndTitledMessage( new com.shatteredpixel.shatteredpixeldungeon.windows.IconTitle( i ), i.info() );
		}
		assertTrue( w != null );
		assertTrue( java.util.Arrays.equals( rng, Random.getStackState() ), "no gameplay roll" );
		assertEquals( state, TurnRunner.stateDigest() );
	}

	/** a replay of the given commands, watched from the start of a run on SEED after setup */
	static ReplayPlayback watchCommands( HeroClass cls, Runnable setup, java.util.function.Supplier<List<Command>> cmds ){
		Random.resetGenerators();
		TurnRunner r = ReplayCheck.start( SEED, cls );
		setup.run();
		ReplayFile file = new ReplayFile( Game.versionCode, SEED, cls, GameMode.SOLO, "", cmds.get() );
		return watch( new ReplayPlayback( file, r ), 7 );
	}

	@Test
	void leavingWithTheAmuletEndsTheReplayOnTheLevelAsAWin(){
		Headless.boot();
		ReplayPlayback pb = watchCommands( HeroClass.WARRIOR, () -> {
			ChoiceReplayTest.NO_MOBS.run();
			new com.shatteredpixel.shatteredpixeldungeon.items.Amulet().collect();
		}, () -> {
			List<Command> c = new ArrayList<>();
			c.add( Command.cell( Dungeon.level.entrance() ) );
			c.add( Command.rest( false ) );
			return c;
		} );
		assertEquals( ReplayPlayback.WON_SURFACE, pb.ended() );
		assertFalse( Game.switchingScene(), "the viewer stays on the level, the surface scene (which wins the game) is not shown" );
		assertTrue( Game.scene() instanceof GameScene );
		assertEquals( 1, pb.done() );
	}

	@Test
	void pickingUpTheAmuletEndsTheReplayAsAWin(){
		Headless.boot();
		int[] at = new int[1];
		ReplayPlayback pb = watchCommands( HeroClass.ROGUE, () -> {
			ChoiceReplayTest.NO_MOBS.run();
			at[0] = freeNeighbour();
			Dungeon.level.drop( new com.shatteredpixel.shatteredpixeldungeon.items.Amulet(), at[0] );
		}, () -> {
			List<Command> c = new ArrayList<>();
			c.add( Command.cell( at[0] ) );
			c.add( Command.rest( false ) );
			c.add( Command.rest( false ) );
			return c;
		} );
		assertEquals( ReplayPlayback.WON_AMULET, pb.ended() );
		assertFalse( Game.switchingScene() );
		assertTrue( Game.scene() instanceof GameScene );
		assertEquals( 1, pb.done() );
		assertEquals( 2, pb.total() - pb.done(), "the inputs after it, which the viewer says it cannot show" );
		assertTrue( Dungeon.hero.belongings.getItem( com.shatteredpixel.shatteredpixeldungeon.items.Amulet.class ) != null );
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
