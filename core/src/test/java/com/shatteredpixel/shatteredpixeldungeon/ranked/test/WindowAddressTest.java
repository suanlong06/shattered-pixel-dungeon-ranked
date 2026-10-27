package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayCheck;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.IconButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndClericSpells;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.noosa.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A window the game opens must look the same to the replay, whatever the client's device: the
 * same buttons at the same addresses (BTN), and the same windows at all (no window opened by a
 * device counter).
 */
class WindowAddressTest {

	@AfterEach
	void tearDown(){
		CommandLog.stop();
		Game.width = 1280;
		Game.height = 720;
		SPDSettings.interfaceSize( 0 );
		Messages.setup( Languages.ENGLISH );
		Features.setMode( GameMode.SOLO );
	}

	/** every button as its address: class and index among the buttons of that class */
	static List<String> addresses( Window w ){
		List<String> out = new ArrayList<>();
		Map<String, Integer> n = new HashMap<>();
		for (Button b : CommandLog.buttons( w )){
			String c = CommandLog.buttonName( b );
			int i = n.merge( c, 1, Integer::sum ) - 1;
			out.add( c + "#" + i );
		}
		return out;
	}

	static class Options extends WndOptions {
		int chosen = -1;
		Options(){ super( "title", "a message long enough to wrap on a narrow screen, in any language at all", "one", "two", "three" ); }
		@Override protected boolean hasInfo( int index ){ return index == 1; }
		@Override protected void onSelect( int index ){ chosen = index; }
	}

	@Test
	void gameWindowsHaveTheSameButtonsOnEveryDevice(){
		Headless.boot();
		ReplayCheck.start( ChoiceReplayTest.SEED, HeroClass.CLERIC );
		HolyTome tome = Dungeon.hero.belongings.getItem( HolyTome.class );
		List<Supplier<Window>> windows = new ArrayList<>();
		windows.add( Options::new );
		windows.add( () -> new WndResurrect( new Ankh() ) );
		windows.add( () -> new WndClericSpells( tome, Dungeon.hero, false ) );
		windows.add( () -> new WndClericSpells( tome, Dungeon.hero, true ) );

		// device settings: phone portrait, phone landscape, desktop UI, another language
		Runnable[] devices = {
				() -> { Game.width = 720; Game.height = 1280; SPDSettings.interfaceSize( 0 ); Messages.setup( Languages.ENGLISH ); },
				() -> { Game.width = 1280; Game.height = 720; SPDSettings.interfaceSize( 0 ); Messages.setup( Languages.ENGLISH ); },
				() -> { Game.width = 1920; Game.height = 1080; SPDSettings.interfaceSize( 2 ); Messages.setup( Languages.ENGLISH ); },
				() -> { Game.width = 720; Game.height = 1280; SPDSettings.interfaceSize( 0 ); Messages.setup( Languages.VIETNAMESE ); },
		};
		for (Supplier<Window> make : windows){
			List<String> first = null;
			for (Runnable device : devices){
				device.run();
				Window w = make.get();
				List<String> a = addresses( w );
				w.destroy();
				if (first == null) first = a;
				else assertEquals( first, a, "buttons of " + CommandLog.windowName( w ) + " depend on the device" );
			}
			assertFalse( first.isEmpty() );
		}
	}

	@Test
	void aButtonIsFoundByItsClassEvenIfOthersAreAdded(){
		Headless.boot();
		TurnRunner r = ReplayCheck.start( ChoiceReplayTest.SEED, HeroClass.WARRIOR );
		CommandLog.start();
		Options w = new Options(); // made by the game
		GameScene.show( w );
		ChoiceReplayTest.click( r, ChoiceReplayTest.option( w, 1 ) );
		assertEquals( 1, w.chosen );
		Command c = CommandLog.recorded().get( 0 );
		assertEquals( Command.Type.BTN, c.type );
		assertTrue( c.action.endsWith( "$1" ), c.toString() );
		CommandLog.stop();

		// the same window with one more button of another class before the options
		CommandLog.start();
		Options again = new Options();
		IconButton extra = new IconButton( Icons.INFO.get() );
		again.addToBack( extra );
		GameScene.show( again );
		assertEquals( extra, CommandLog.buttons( again ).get( 0 ) );
		CommandLog.runAsGameplay( c::apply );
		assertEquals( 1, again.chosen, "the class address still finds option two" );
	}

	@Test
	void noGameWindowOpensByADeviceCounterInRanked(){
		Headless.boot();
		SPDSettings.supportNagged( false );
		SPDSettings.vaultInjureWarns( 3 );
		Features.setMode( GameMode.SOLO );
		assertFalse( SPDSettings.supportNagged() );
		assertEquals( 3, SPDSettings.vaultInjureWarns() );
		Features.setMode( GameMode.RANKED );
		// the support prompt on Goo's key never shows, the vault warning shows once in every run
		assertTrue( SPDSettings.supportNagged() );
		assertEquals( 0, SPDSettings.vaultInjureWarns() );
	}
}
