package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayCheck;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.ExitButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndEnergizeItem;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.Played;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.Player;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.SEED;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.assertSameGame;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.click;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.freeNeighbour;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.playAndReplay;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.slot;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.tap;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.types;
import static com.shatteredpixel.shatteredpixeldungeon.ranked.test.ChoiceReplayTest.ui;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alchemy (AlchemyScene) in a Ranked run: the player's inputs there are recorded through the
 * scene's own buttons (input slots, item selector, craft, cancel, energize, exit) and the replay
 * crafts the same items with the same RNG. Played live, then replayed from the log, as
 * {@link ChoiceReplayTest} does.
 */
class AlchemyReplayTest {

	@AfterEach
	void tearDown(){
		CommandLog.stop();
		com.shatteredpixel.shatteredpixeldungeon.ranked.Features.setMode( com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode.SOLO );
	}

	// ---------- the alchemy scene's own buttons ----------

	static AlchemyScene scene(){
		assertInstanceOf( AlchemyScene.class, Game.scene(), "not in alchemy" );
		return (AlchemyScene) Game.scene();
	}

	static Object field( Class<?> c, Object o, String name ){
		try {
			Field f = c.getDeclaredField( name );
			f.setAccessible( true );
			return f.get( o );
		} catch (ReflectiveOperationException e){
			throw new RuntimeException( e );
		}
	}

	/** the item slot (a button) of input i */
	static Button inputSlot( int i ){
		Object in = ((Object[]) field( AlchemyScene.class, null, "inputs" ))[i];
		return (Button) field( in.getClass(), in, "slot" );
	}

	static Button craftButton( int i ){
		Object cb = ((Object[]) field( AlchemyScene.class, null, "combines" ))[i];
		return (Button) field( cb.getClass(), cb, "button" );
	}

	static <T> T member( Group g, Class<T> c ){
		for (Gizmo m : g.membersCopy()){
			if (c.isInstance( m )) return c.cast( m );
		}
		throw new AssertionError( "no " + c.getSimpleName() + " in " + g.getClass().getSimpleName() );
	}

	/** the top window, whoever opened it (the alchemy scene's are all opened by the player) */
	static Window uiTop(){
		Window w = null;
		for (Gizmo g : Game.scene().membersCopy()) if (g instanceof Window && g.exists) w = (Window) g;
		assertNotNull( w, "no window open" );
		return w;
	}

	/** tap an input slot, then pick the item in the bag it opens */
	static void putIn( TurnRunner r, int input, Item item ){
		click( r, inputSlot( input ) );
		pick( r, item );
	}

	/** picks the item in the open bag window, switching to the bag holding it as its tab does */
	static void pick( TurnRunner r, Item item ){
		WndBag w = (WndBag) uiTop();
		com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag bag = null;
		for (com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag b : Dungeon.hero.belongings.getBags()) if (b.items.contains( item )) bag = b;
		boolean shown = false;
		for (Button b : CommandLog.buttons( w )){
			if (b instanceof com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot && ((com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot) b).item() == item) shown = true;
		}
		if (!shown && bag != null){
			// the bag's tab, as WndBag does it
			com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag fb = bag;
			WndBag old = w;
			WndBag[] nw = new WndBag[1];
			ui( () -> { old.hide(); nw[0] = new WndBag( fb, old.getSelector() ); Game.scene().addToFront( nw[0] ); } );
			w = nw[0];
		}
		click( r, slot( w, item ) );
	}

	static void exit( TurnRunner r ){
		click( r, member( Game.scene(), ExitButton.class ) );
	}

	static int potions(){
		int n = 0;
		for (Item i : Dungeon.hero.belongings) if (i instanceof Potion) n += i.quantity();
		return n;
	}

	static int seeds(){
		int n = 0;
		for (Item i : Dungeon.hero.belongings) if (i instanceof Sungrass.Seed) n += i.quantity();
		return n;
	}

	static final int[] POT = new int[1];

	/** an alchemy pot next to the hero, no mobs, and seeds to brew with */
	static final Runnable SETUP = () -> {
		ChoiceReplayTest.NO_MOBS.run();
		POT[0] = freeNeighbour();
		Level.set( POT[0], Terrain.ALCHEMY );
		GameScene.updateMap( POT[0] );
		new Sungrass.Seed().quantity( 4 ).collect();
		new ScrollOfIdentify().collect();
	};

	static Sungrass.Seed seed(){
		return Dungeon.hero.belongings.getItem( Sungrass.Seed.class );
	}

	// ---------- tests ----------

	@Test
	void brewingAPotionReplays(){
		Player pl = new Player()
				.step( r -> tap( POT[0] ) )                                     // the hero uses the pot
				.step( r -> putIn( r, 0, seed() ) )
				.step( r -> putIn( r, 1, seed() ) )
				.step( r -> putIn( r, 2, seed() ) )
				.step( r -> {
					assertTrue( scene().combineEnabled( 0 ) );
					click( r, craftButton( 0 ) );                               // a random potion
				} )
				.step( r -> exit( r ) )
				.step( r -> { assertInstanceOf( GameScene.class, Game.scene() ); tap( freeNeighbour() ); } );
		Played p = playAndReplay( HeroClass.WARRIOR, SETUP, pl );
		assertEquals( 1, potions(), "the replay brewed it too" );
		assertEquals( 1, seeds() );
		assertEquals( Arrays.asList( Command.Type.CELL, Command.Type.ALCH, Command.Type.ALCH, Command.Type.ALCH, Command.Type.ALCH,
				Command.Type.ALCH, Command.Type.CELL ), types( p.log ), CommandLog.toText( p.log ) );
		assertEquals( Command.alchemy( "COMBINE", 0 ), p.log.get( 4 ) );
		assertEquals( Command.alchemy( "EXIT", 0 ), p.log.get( 5 ) );
		assertSameGame( p );
	}

	@Test
	void takingOutCancellingRepeatingAndEnergizingReplay(){
		Player pl = new Player()
				.step( r -> tap( POT[0] ) )
				.step( r -> putIn( r, 0, seed() ) )
				.step( r -> {
					// tapping a full input gives the item back (and opens the bag again: UI only)
					click( r, inputSlot( 0 ) );
					ui( () -> uiTop().hide() );
				} )
				.step( r -> putIn( r, 0, seed() ) )
				.step( r -> putIn( r, 1, seed() ) )
				.step( r -> {
					long[] rng = Random.getStackState();
					click( r, (Button) field( AlchemyScene.class, scene(), "cancel" ) );
					assertArrayEquals( rng, Random.getStackState(), "the alchemy UI draws nothing" );
					assertEquals( 4, seeds(), "cancel gave them back" );
				} )
				.step( r -> {
					// energize the scroll: the "+" by the energy, the bag, then the energize window
					int before = Dungeon.energy;
					ui( WndEnergizeItem::openItemSelector );
					pick( r, Dungeon.hero.belongings.getItem( ScrollOfIdentify.class ) );
					Window w = uiTop();
					assertInstanceOf( WndEnergizeItem.class, w );
					for (Button b : CommandLog.buttons( w )) if (b instanceof RedButton){ click( r, b ); break; }
					assertTrue( Dungeon.energy > before );
					for (Gizmo g : Game.scene().membersCopy()) if (g instanceof Window) ui( ((Window) g)::hide );
				} )
				.step( r -> putIn( r, 0, seed() ) )
				.step( r -> Game.scene().onPause() )                             // the app pauses: energy was made, so the seed goes back
				.step( r -> { assertEquals( 4, seeds() ); putIn( r, 0, seed() ); } )
				.step( r -> exit( r ) )                                          // the seed goes back
				.step( r -> tap( freeNeighbour() ) );
		Played p = playAndReplay( HeroClass.MAGE, SETUP, pl );
		assertTrue( CommandLog.toText( p.log ).contains( "ALCH OUT 0" ), CommandLog.toText( p.log ) );
		assertTrue( CommandLog.toText( p.log ).contains( "ALCH CLEAR" ), CommandLog.toText( p.log ) );
		assertTrue( CommandLog.toText( p.log ).contains( "ENERGIZE" ), CommandLog.toText( p.log ) );
		assertTrue( CommandLog.toText( p.log ).contains( "ALCH PAUSE" ), CommandLog.toText( p.log ) );
		assertSameGame( p );
		assertEquals( 4, seeds() );
		assertNull( Dungeon.hero.belongings.getItem( ScrollOfIdentify.class ), "energized in the replay too" );
	}

	@Test
	void sceneResetsFromTheGuideOrTheDeviceReplay(){
		Player pl = new Player()
				.step( r -> tap( POT[0] ) )
				.step( r -> putIn( r, 0, seed() ) )
				.step( r -> ui( ShatteredPixelDungeon::seamlessResetScene ) )   // alchemy guide beside the scene
				.step( r -> {
					assertEquals( 4, seeds(), "a reset gives the inputs back" );
					putIn( r, 0, seed() );
				} )
				.step( r -> Game.resetScene() )                                  // rotation: not an input event
				.step( r -> putIn( r, 0, seed() ) )
				.step( r -> putIn( r, 1, seed() ) )
				.step( r -> putIn( r, 2, seed() ) )
				.step( r -> click( r, craftButton( 0 ) ) )
				.step( r -> exit( r ) )
				.step( r -> tap( freeNeighbour() ) );
		Played p = playAndReplay( HeroClass.ROGUE, SETUP, pl );
		assertEquals( 2, CommandLog.toText( p.log ).split( "ALCH RESET" ).length - 1, CommandLog.toText( p.log ) );
		assertSameGame( p );
		assertEquals( 1, potions() );
	}

	@Test
	void clientClosedInAlchemyReplaysFromItsSave() throws Exception {
		Headless.boot();
		CommandLog.start();
		TurnRunner first = ReplayCheck.start( SEED, HeroClass.HUNTRESS );
		SETUP.run();
		first.live = true;
		Player pl = new Player()
				.step( r -> tap( POT[0] ) )
				.step( r -> { putIn( r, 0, seed() ); putIn( r, 1, seed() ); putIn( r, 2, seed() ); } )
				.step( r -> click( r, craftButton( 0 ) ) )                    // saves
				.step( r -> putIn( r, 0, seed() ) );                            // after the last save: the process dies here
		first.run( pl, Integer.MAX_VALUE );
		assertEquals( 4, pl.next );
		assertEquals( 0, seeds(), "the last seed is in the pot" );
		CommandLog.stop();
		// a killed process neither gives the inputs back nor saves
		AlchemyScene.abandonInputs();
		TurnRunner.dropScene();

		// restart: the save was made in alchemy, so loading it comes back to the level without what was in the pot
		Random.resetGenerators();
		CommandLog.start();
		TurnRunner resumed = TurnRunner.resumeSavedGame();
		List<Command> restored = CommandLog.recorded();
		assertEquals( Command.alchemy( "LOAD", 0 ), restored.get( restored.size() - 1 ), CommandLog.toText( restored ) );
		assertEquals( 1, potions() );
		assertEquals( 1, seeds(), "as saved: before the last seed went in the pot" );
		resumed.live = true;
		resumed.run( new Player().step( r -> tap( freeNeighbour() ) ).step( r -> tap( freeNeighbour() ) ), Integer.MAX_VALUE );
		List<Command> log = new ArrayList<>( CommandLog.stop() );

		Random.resetGenerators();
		ReplayCheck.Facts f = ReplayCheck.replay( SEED, HeroClass.HUNTRESS, log, SETUP );
		assertNull( f.unsupported, f + "\n" + CommandLog.toText( log ) );
		assertEquals( log.size(), f.commandsApplied );
		assertEquals( 1, potions() );
		assertEquals( 1, seeds() );
		assertEquals( resumed.trace.get( resumed.trace.size() - 1 ), TurnRunner.stateDigest() );
	}

	@Test
	void forgedAlchemyInputsAreRefused(){
		Headless.boot();
		// craft with nothing in the pot, and alchemy outside of it
		ReplayCheck.Facts f = ReplayCheck.replay( SEED, HeroClass.WARRIOR, Arrays.asList( Command.alchemy( "COMBINE", 0 ) ) );
		assertNotNull( f.unsupported, f.toString() );
		assertTrue( f.unsupported.contains( "not in alchemy" ), f.unsupported );
	}

	@Test
	void alchemyCommandsSurviveTextAndRejectGarbage(){
		List<Command> all = Arrays.asList( Command.alchemy( "IN", 7 ), Command.alchemy( "OUT", 2 ), Command.alchemy( "CLEAR", 0 ),
				Command.alchemy( "COMBINE", 1 ), Command.alchemy( "EXIT", 0 ), Command.alchemy( "RESET", 0 ), Command.alchemy( "PAUSE", 0 ),
				Command.alchemy( "LOAD", 0 ), Command.energize( 3, true ), Command.energize( 4, false ),
				Command.button( 0, 1, 2, "windows.WndOptions", "windows.WndOptions$1" ) );
		assertEquals( all, CommandLog.fromText( CommandLog.toText( all ) ) );
		for (String bad : new String[]{ "ALCH", "ALCH IN", "ALCH BREW 1", "ALCH EXIT 3", "ALCH IN -1", "ENERGIZE 3", "ENERGIZE 3 some", "BTN 0 1 2 a b c" }){
			try {
				Command.parse( bad );
				throw new AssertionError( "parsed " + bad );
			} catch (IllegalArgumentException expected){
			}
		}
	}
}
