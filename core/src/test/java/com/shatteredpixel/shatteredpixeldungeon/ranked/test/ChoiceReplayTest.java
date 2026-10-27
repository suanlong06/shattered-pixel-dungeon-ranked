package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfPassage;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrimTrap;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayCheck;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUpgrade;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T0.8: inputs given in windows (CHOICE), talents, cancels while the hero acts, and level changes
 * other than stairs (falling, Return, resurrection) are recorded through the client's own code
 * paths and replay to the same game. Each test plays "live" (TurnRunner.live: inputs go through
 * the UI hooks inside an input context, as pointer and key events do, and CommandLog records them),
 * then replays the recorded log from the seed as the server does, and compares the traces.
 */
class ChoiceReplayTest {

	static final long SEED = 12345;

	@AfterEach
	void tearDown(){
		CommandLog.stop();
		// Ranked changes level generation; other tests expect Solo
		com.shatteredpixel.shatteredpixeldungeon.ranked.Features.setMode( com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode.SOLO );
	}

	// ---------- the player ----------

	/** Live input: one step per time the game waits; a mid-action step runs right before hero turn k+1. */
	static final class Player implements TurnRunner.Input {
		final List<Consumer<TurnRunner>> steps = new ArrayList<>();
		int next;
		int midAt = -1;
		Runnable mid;

		Player step( Consumer<TurnRunner> s ){ steps.add( s ); return this; }

		@Override
		public Command next( TurnRunner r ){
			if (next >= steps.size()) return null;
			steps.get( next++ ).accept( r );
			return Command.note( "live" );
		}

		@Override
		public Command beforeHeroActs( TurnRunner r, int heroActs ){
			if (mid == null || heroActs != midAt) return null;
			Runnable m = mid;
			mid = null;
			CommandLog.asInput( m );
			return Command.note( "live" );
		}
	}

	static void ui( Runnable r ){ CommandLog.asInput( r ); }

	/** the player reads the window first; windows may ignore clicks that come too fast (Chasm) */
	static void pause( TurnRunner r ){
		for (float t = 0; t < TurnRunner.UI_PAUSE; t += TurnRunner.frameSeconds) r.frame();
	}

	static void click( TurnRunner r, Button b ){
		pause( r );
		try {
			Method m = Button.class.getDeclaredMethod( "click", int.class );
			m.setAccessible( true );
			ui( () -> {
				try {
					m.invoke( b, 0 );
				} catch (ReflectiveOperationException e){
					throw new RuntimeException( e );
				}
			} );
		} catch (NoSuchMethodException e){
			throw new RuntimeException( e );
		}
	}

	/** back key or a tap outside, as Window does */
	static void back( TurnRunner r ){
		pause( r );
		Window w = top();
		ui( () -> CommandLog.back( w, w::onBackPressed ) );
	}

	static Window top(){
		Window w = CommandLog.topWindow();
		assertNotNull( w, "no window open" );
		return w;
	}

	static Button button( Window w, String textPart ){
		for (Button b : CommandLog.buttons( w )){
			if (b instanceof RedButton && ((RedButton) b).text().contains( textPart )) return b;
		}
		throw new AssertionError( "no button '" + textPart + "' in " + CommandLog.windowName( w ) );
	}

	static Button option( Window w, int index ){
		List<Button> bs = new ArrayList<>();
		for (Button b : CommandLog.buttons( w )) if (b instanceof RedButton) bs.add( b );
		return bs.get( index );
	}

	/** an item slot of the open item selector */
	static Button slot( Window w, Item item ){
		for (Button b : CommandLog.buttons( w )){
			if (b instanceof com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot
					&& ((com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot) b).item() == item) return b;
		}
		throw new AssertionError( "no slot for " + item );
	}

	static void useItem( Item item, String action ){
		ui( () -> CommandLog.item( item, action, () -> item.execute( Dungeon.hero, action ) ) );
	}

	static void tap( int cell ){
		ui( () -> GameScene.handleCell( cell ) );
	}

	/** no mobs on the floor and the map known: nothing interrupts a walk, and long walks find a path */
	static final Runnable NO_MOBS = () -> {
		Arrays.fill( Dungeon.level.mapped, true );
		for (com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob m : Dungeon.level.mobs.toArray( new com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob[0] )){
			m.destroy();
			if (m.sprite != null) m.sprite.killAndErase();
		}
	};

	/** a passable, free neighbour of the hero, lowest index first */
	static int freeNeighbour(){
		for (int o : PathFinder.NEIGHBOURS8){
			int c = Dungeon.hero.pos + o;
			if (Dungeon.level.passable[c] && !Dungeon.level.solid[c] && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar( c ) == null
					&& Dungeon.level.traps.get( c ) == null && !Dungeon.level.pit[c]) return c;
		}
		throw new AssertionError( "no free neighbour" );
	}

	/** a reachable cell some distance away (by path), for a walk of several turns */
	static int farCell( int minSteps ){
		PathFinder.buildDistanceMap( Dungeon.hero.pos, com.watabou.utils.BArray.or( Dungeon.level.passable, Dungeon.level.avoid, null ) );
		for (int c = 0; c < Dungeon.level.length(); c++){
			if (PathFinder.distance[c] >= minSteps && PathFinder.distance[c] < minSteps + 3 && Dungeon.level.passable[c]
					&& Dungeon.level.traps.get( c ) == null) return c;
		}
		throw new AssertionError( "no far cell" );
	}

	// ---------- live play, then replay ----------

	static final class Played {
		TurnRunner live;
		List<Command> log;
		ReplayCheck.Facts replay;
	}

	static Played playAndReplay( HeroClass cls, Runnable setup, Player player ){
		Headless.boot();
		Played p = new Played();
		CommandLog.start();
		p.live = ReplayCheck.start( SEED, cls );
		if (setup != null) setup.run();
		p.live.live = true;
		p.live.run( player, Integer.MAX_VALUE );
		p.log = new ArrayList<>( CommandLog.stop() );
		assertEquals( player.steps.size(), player.next, "the script did not finish: " + p.live.stop );

		// the server's side: a fresh process state, the log as text
		Random.resetGenerators();
		p.replay = ReplayCheck.replay( SEED, cls, CommandLog.fromText( CommandLog.toText( p.log ) ), setup );
		return p;
	}

	static void assertSameGame( Played p ){
		assertNull( p.replay.unsupported, p.replay + "\nlog:\n" + CommandLog.toText( p.log ) );
		assertEquals( p.log.size(), p.replay.commandsApplied, p.replay.toString() );
		assertEquals( p.live.stateSequenceDigest(), p.replay.stateDigest, "replay differs from the live game\nlog:\n" + CommandLog.toText( p.log ) );
	}

	// ---------- tests ----------

	@Test
	void upgradeScrollWithCancelConfirmAndUpgradeWindowReplays(){
		Item[] weapon = new Item[1];
		Scroll[] sou = new Scroll[1];
		Runnable setup = () -> {
			sou[0] = new ScrollOfUpgrade();
			sou[0].collect();
			weapon[0] = Dungeon.hero.belongings.weapon();
		};
		Player pl = new Player()
				.step( r -> useItem( sou[0], Scroll.AC_READ ) )                 // unknown: identifies, picks an item
				.step( r -> { assertInstanceOf( WndBag.class, top() ); back( r ); } ) // cancel: "are you sure?"
				.step( r -> { assertInstanceOf( WndOptions.class, top() ); click( r, option( top(), 1 ) ); } ) // no, pick again
				.step( r -> click( r, slot( top(), weapon[0] ) ) )             // the weapon: WndUpgrade
				.step( r -> { assertInstanceOf( WndUpgrade.class, top() ); click( r, button( top(), "" + Messages.get( WndUpgrade.class, "upgrade" ) ) ); } )
				.step( r -> tap( freeNeighbour() ) );
		Played p = playAndReplay( HeroClass.WARRIOR, setup, pl );
		assertEquals( 1, weapon[0].level(), "the live game upgraded the weapon" );
		List<Command.Type> types = new ArrayList<>();
		for (Command c : p.log) types.add( c.type );
		assertEquals( Arrays.asList( Command.Type.ITEM, Command.Type.BACK, Command.Type.BTN, Command.Type.SELECT, Command.Type.BTN, Command.Type.CELL ), types,
				CommandLog.toText( p.log ) );
		assertSameGame( p );
		assertEquals( 1, Dungeon.hero.belongings.weapon().level(), "the replay upgraded it too" );
	}

	@Test
	void talentPointsReplay(){
		Runnable setup = () -> Dungeon.hero.earnExp( Dungeon.hero.maxExp(), getClass() );
		Talent t = Talent.HEARTY_MEAL;
		Player pl = new Player()
				.step( r -> ui( () -> CommandLog.input( Command.talent( t ) ) ) ) // TalentButton.upgradeTalent
				.step( r -> tap( freeNeighbour() ) );
		Played p = playAndReplay( HeroClass.WARRIOR, setup, pl );
		assertEquals( 1, Dungeon.hero.pointsInTalent( t ) );
		assertEquals( Command.talent( t ), p.log.get( 0 ) );
		assertSameGame( p );
	}

	@Test
	void cancellingAWalkMidwayReplaysAtTheSameTurn(){
		int[] dest = new int[1];
		Player pl = new Player();
		pl.step( r -> {
			dest[0] = farCell( 8 );
			tap( dest[0] );
			// the player taps again while the hero walks: GameScene.cancel() stops the walk
			pl.midAt = 1;
			pl.mid = () -> GameScene.cancel();
		} ).step( r -> {
			assertTrue( Dungeon.hero.pos != dest[0], "the walk was stopped" );
			tap( freeNeighbour() );
		} );
		Played p = playAndReplay( HeroClass.ROGUE, NO_MOBS, pl );
		assertEquals( Command.cancel( false ).at( 1 ), p.log.get( 1 ), CommandLog.toText( p.log ) );
		assertSameGame( p );
	}

	@Test
	void jumpingIntoAChasmReplays(){
		int[] pit = new int[1];
		Runnable setup = () -> {
			pit[0] = freeNeighbour();
			Level.set( pit[0], Terrain.CHASM );
			GameScene.updateMap( pit[0] );
		};
		Player pl = new Player()
				.step( r -> tap( pit[0] ) )                                    // asks "jump?"
				.step( r -> click( r, option( top(), 0 ) ) )                   // yes: the hero falls to depth 2
				.step( r -> { assertEquals( 2, Dungeon.depth ); tap( freeNeighbour() ); } );
		Played p = playAndReplay( HeroClass.MAGE, setup, pl );
		assertSameGame( p );
		assertEquals( 2, p.replay.depth );
	}

	@Test
	void returnToTheRegionStartReplays(){
		Scroll[] passage = new Scroll[1];
		Runnable setup = () -> {
			NO_MOBS.run();
			passage[0] = new ScrollOfPassage();
			passage[0].collect();
		};
		int[] posBefore = new int[1], posAfter = new int[1];
		Player pl = new Player()
				.step( r -> tap( farCell( 6 ) ) )
				.step( r -> {
					posBefore[0] = Dungeon.hero.pos;
					useItem( passage[0], Scroll.AC_READ );                      // RETURN to the start of the region (a new spot)
				} )
				.step( r -> {
					posAfter[0] = Dungeon.hero.pos;
					assertTrue( posAfter[0] != posBefore[0] );
					assertFalse( Dungeon.hero.belongings.contains( passage[0] ) );
					tap( freeNeighbour() );
				} );
		Played p = playAndReplay( HeroClass.HUNTRESS, setup, pl );
		assertSameGame( p );
	}

	@Test
	void windowsOpenedFromTheUiAreNotRecorded(){
		Player pl = new Player()
				.step( r -> ui( () -> GameScene.show( new com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage( "info" ) ) ) )
				.step( r -> {
					assertNull( CommandLog.topWindow(), "not a window of the game" );
					Window w = null;
					for (com.watabou.noosa.Gizmo g : com.watabou.noosa.Game.scene().membersCopy()) if (g instanceof Window) w = (Window) g;
					assertNotNull( w );
					Window fw = w;
					pause( r );
					ui( () -> CommandLog.back( fw, fw::onBackPressed ) );
					assertNull( fw.parent, "closed" );
				} )
				.step( r -> tap( freeNeighbour() ) );
		Played p = playAndReplay( HeroClass.WARRIOR, null, pl );
		assertEquals( Arrays.asList( Command.Type.CELL ), types( p.log ), CommandLog.toText( p.log ) );
		assertSameGame( p );
	}

	static List<Command.Type> types( List<Command> log ){
		List<Command.Type> t = new ArrayList<>();
		for (Command c : log) t.add( c.type );
		return t;
	}

	@Test
	void dyingWithAnAnkhAndResurrectingReplays(){
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
				.step( r -> tap( trap[0] ) )                                   // the trap kills the hero
				.step( r -> {
					assertFalse( Dungeon.hero.isAlive() );
					assertInstanceOf( WndResurrect.class, top() );
					click( r, button( top(), Messages.get( WndResurrect.class, "confirm" ) ) );
				} )
				.step( r -> { assertTrue( Dungeon.hero.isAlive() ); tap( freeNeighbour() ); } );
		Played p = playAndReplay( HeroClass.CLERIC, setup, pl );
		assertSameGame( p );
		assertFalse( p.replay.died );
		assertTrue( Dungeon.hero.isAlive() );
	}

	@Test
	void logSavedWithTheGameComesBackOnRejoin() throws Exception {
		Headless.boot();
		CommandLog.start();
		TurnRunner first = ReplayCheck.start( SEED, HeroClass.WARRIOR );
		NO_MOBS.run();
		first.live = true;
		Player pl = new Player()
				.step( r -> tap( freeNeighbour() ) )
				.step( r -> tap( freeNeighbour() ) )
				.step( r -> tap( farCell( 8 ) ) );
		// one turn into the walk the app is closed: GameScene.onPause saves, then the process dies
		boolean[] saved = new boolean[1];
		first.run( new TurnRunner.Input(){
			@Override public Command next( TurnRunner r ){ return saved[0] ? null : pl.next( r ); }
			@Override public Command beforeHeroActs( TurnRunner r, int acts ){
				if (!saved[0] && pl.next == 3 && acts == 1){
					assertNotNull( Dungeon.hero.curAction, "still walking" );
					try { Dungeon.saveAll(); } catch (Exception e){ throw new RuntimeException( e ); }
					saved[0] = true;
				}
				return null;
			}
		}, Integer.MAX_VALUE );
		assertTrue( saved[0] );
		List<String> traceBefore = new ArrayList<>( first.trace.subList( 0, 3 ) );
		CommandLog.stop();

		// restart: recording starts again and loading the save brings its log back
		Random.resetGenerators();
		CommandLog.start();
		TurnRunner resumed = TurnRunner.resumeSavedGame();
		List<Command> restored = CommandLog.recorded();
		assertEquals( 5, restored.size(), CommandLog.toText( restored ) );
		assertEquals( Command.cancel( false ).at( 1 ), restored.get( 3 ) );
		assertEquals( Command.Type.NOTE, restored.get( 4 ).type );
		resumed.live = true;
		resumed.run( new Player().step( r -> tap( freeNeighbour() ) ).step( r -> tap( freeNeighbour() ) ), Integer.MAX_VALUE );
		List<Command> log = new ArrayList<>( CommandLog.stop() );
		assertEquals( 7, log.size() );

		// the server replays the whole log: the load dropped the walk, replay stops it at the same turn
		Random.resetGenerators();
		TurnRunner replay = ReplayCheck.start( SEED, HeroClass.WARRIOR );
		NO_MOBS.run();
		replay.run( replayOf( log ), Integer.MAX_VALUE );
		List<String> expected = new ArrayList<>( traceBefore );
		expected.add( resumed.trace.get( 0 ) ); // the NOTE is answered while the hero waits there
		expected.addAll( resumed.trace );
		assertEquals( expected, replay.trace );

		// and the verifier is told the save was made mid-action (it decides on a mismatch only)
		Random.resetGenerators();
		ReplayCheck.Facts f = ReplayCheck.replay( SEED, HeroClass.WARRIOR, log, NO_MOBS );
		assertTrue( f.unsupported != null && f.unsupported.contains( "save" ), f.toString() );
	}

	static TurnRunner.Input replayOf( List<Command> log ){
		return new TurnRunner.Input(){
			int i;
			@Override public Command next( TurnRunner rr ){ return i < log.size() ? log.get( i++ ) : null; }
			@Override public Command beforeHeroActs( TurnRunner rr, int acts ){
				return i < log.size() && log.get( i ).at >= 0 && log.get( i ).at <= acts ? log.get( i++ ) : null;
			}
		};
	}

	@Test
	void windowCommandWithoutItsWindowIsUnsupported(){
		Headless.boot();
		ReplayCheck.Facts f = ReplayCheck.replay( SEED, HeroClass.WARRIOR, Arrays.asList( Command.button( 0, 0, 2, "windows.WndOptions" ) ) );
		assertNotNull( f.unsupported, f.toString() );
		assertTrue( f.unsupported.contains( "no window" ), f.unsupported );
	}

	@Test
	void everyCommandSurvivesTextAndRejectsGarbage(){
		List<Command> all = Arrays.asList( Command.cell( 5 ), Command.rest( true ), Command.search(), Command.item( 3, "READ" ),
				Command.move( 40 ), Command.pickup(), Command.action(), Command.resume(), Command.talent( Talent.HEARTY_MEAL ),
				Command.cancel( false ), Command.cancel( true ).at( 4 ), Command.button( 3, 1, 4, "windows.WndTradeItem$2" ),
				Command.back( "windows.WndBag" ), Command.select( 7 ), Command.note( "scene reset with windows.WndBag open" ).at( 0 ) );
		assertEquals( all, CommandLog.fromText( CommandLog.toText( all ) ) );
		for (String bad : new String[]{ "BTN 0 5 2 x", "BTN 9 0 1 x", "@-1 CELL 3", "CANCEL now", "TALENT NOPE", "BACK", "JUMP 3" }){
			try {
				Command.parse( bad );
				throw new AssertionError( "parsed " + bad );
			} catch (IllegalArgumentException expected){
			}
		}
	}

	static final class Messages {
		static String get( Class<?> c, String key ){
			return com.shatteredpixel.shatteredpixeldungeon.messages.Messages.get( c, key );
		}
	}
}
