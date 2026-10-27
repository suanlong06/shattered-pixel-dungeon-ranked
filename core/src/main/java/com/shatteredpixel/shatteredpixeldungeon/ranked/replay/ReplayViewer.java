package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.net.RankedSession;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.TitleScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.input.GameAction;
import com.watabou.input.KeyBindings;
import com.watabou.input.KeyEvent;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Game;
import com.watabou.noosa.PointerArea;
import com.watabou.noosa.Scene;
import com.watabou.utils.Signal;

/**
 * spd-ranked (A5): watches a replay in the real game scene. The run starts in its own save slot
 * like the recorded run started (seed, class, mode, challenges); then, instead of the actor
 * thread, {@link #update} steps the game from GameScene.update() with a {@link ReplayPlayback},
 * which feeds the recorded commands in exactly as the server's replay check does. Level changes
 * go through the game's own InterlevelScene. The player's taps and keys do not reach the game
 * while watching (only the replay controls), and nothing of the replay is kept: no badges,
 * rankings, journal, bones or ghosts, and the save slot is deleted on exit.
 */
public final class ReplayViewer {

	/** after Boss Practice's and Ranked's slots */
	public static final int SLOT = GamesInProgress.MAX_SLOTS + 3;

	private static ReplayViewer current;

	private final ReplayPlayback playback;
	private final int prevSlot;
	private final HeroClass prevClass;
	private Scene lastScene;
	private ReplayControls controls;
	private boolean exiting;

	private ReplayViewer( ReplayPlayback playback ){
		this.playback = playback;
		prevSlot = GamesInProgress.curSlot;
		prevClass = GamesInProgress.selectedClass;
	}

	/** true from start() until the viewer has left the game scene */
	public static boolean active(){
		return current != null;
	}

	public static ReplayPlayback playback(){
		return current == null ? null : current.playback;
	}

	/** null if the replay can be watched here, else why not (shown to the player) */
	public static String cannotWatch( ReplayFile f ){
		if (current != null) return Messages.get( ReplayViewer.class, "busy" );
		if (f.mode == GameMode.PRACTICE) return Messages.get( ReplayViewer.class, "no_practice" );
		RankedSession s = RankedSession.current();
		if (s != null && s.inRun()) return Messages.get( ReplayViewer.class, "in_match" );
		return null;
	}

	/** Starts watching (from a menu scene); returns false with nothing changed if cannotWatch(f). */
	public static boolean start( ReplayFile f ){
		if (cannotWatch( f ) != null) return false;
		current = new ReplayViewer( new ReplayPlayback( f, TurnRunner.forViewer() ) );

		// the run starts as RankedSession.startRun starts a ranked run
		Dungeon.deleteGame( SLOT, true );
		GamesInProgress.curSlot = SLOT;
		GamesInProgress.selectedClass = f.heroClass;
		Dungeon.hero = null;
		WndResurrect.instance = null;
		Dungeon.daily = Dungeon.dailyReplay = false;
		Dungeon.initSeed();
		Features.setMode( f.mode );
		Features.setViewingReplay( true );
		Dungeon.seed = f.seed;
		Dungeon.customSeedText = "";
		ActionIndicator.clearAction();
		InterlevelScene.mode = InterlevelScene.Mode.DESCEND;

		TurnRunner.beginViewing();
		installGate();
		Game.switchScene( InterlevelScene.class );
		return true;
	}

	// hooks

	/** Dungeon.init: the replay's challenges, not the player's current settings */
	public static int challenges( int fromSettings ){
		return current != null ? current.playback.file.challenges : fromSettings;
	}

	/** GameScene.update, instead of starting or waking the actor thread */
	public static boolean drivesActors(){
		return current != null;
	}

	/** GameScene.update, every frame while watching */
	public static void update( GameScene scene ){
		if (current != null) current.frame( scene );
	}

	private void frame( GameScene scene ){
		// leaving: the switch to the title screen happens next frame, the run must not request another
		if (exiting) return;
		if (scene != lastScene){
			lastScene = scene;
			playback.sceneBuilt();
			controls = new ReplayControls( this );
			scene.add( controls );
		}
		if (playback.frame() == ReplayPlayback.Result.SWITCH && Game.switchingScene()){
			String unsupported = TurnRunner.unsupportedSwitch( Game.pendingScene() );
			if (unsupported != null){
				// the game went somewhere replay cannot follow (alchemy, the amulet...)
				playback.end( unsupported );
				exit( Messages.get( ReplayViewer.class, "unsupported_scene" ) );
			}
		}
		Game.timeScale = playback.ended() != null && !playback.paused() ? 1f : playback.timeScale();
		controls.refresh();
	}

	// controls

	void togglePause(){ playback.togglePause(); }
	void stepOne(){ playback.stepOne(); }
	void faster(){ playback.faster(); }
	void slower(){ playback.slower(); }
	ReplayPlayback state(){ return playback; }

	/** leaves the replay for the title screen, optionally with a message there */
	public static void exit( String message ){
		if (current == null || current.exiting) return;
		current.exiting = true;
		ShatteredPixelDungeon.switchNoFade( TitleScene.class, new Game.SceneChangeCallback(){
			@Override
			public void beforeCreate(){
				cleanup();
			}

			@Override
			public void afterCreate(){
				if (message != null) Game.scene().addToFront( new WndMessage( message ) );
			}
		} );
	}

	/** the game scene is gone: give the player back their game and settings */
	private static void cleanup(){
		ReplayViewer v = current;
		if (v == null) return;
		removeGate();
		TurnRunner.endViewing();
		Features.setViewingReplay( false );
		Features.setMode( GameMode.SOLO );
		Dungeon.hero = null;
		Dungeon.deleteGame( SLOT, true );
		GamesInProgress.curSlot = v.prevSlot;
		GamesInProgress.selectedClass = v.prevClass;
		current = null;
	}

	// input gate: while the game scene is up, presses only reach the replay controls

	private static final Signal.Listener<PointerEvent> POINTER_GATE = new Signal.Listener<PointerEvent>(){
		@Override
		public boolean onSignal( PointerEvent e ){
			// moves, releases and hovers pass: nothing clicks without a press it saw
			if (current == null || e == null || e.type != PointerEvent.Type.DOWN) return false;
			if (!(Game.scene() instanceof GameScene)) return false;
			ReplayControls c = current.controls;
			return c == null || !c.hits( e );
		}
	};

	private static final Signal.Listener<KeyEvent> KEY_GATE = new Signal.Listener<KeyEvent>(){
		@Override
		public boolean onSignal( KeyEvent e ){
			if (current == null || !(Game.scene() instanceof GameScene)) return false;
			if (e.pressed && KeyBindings.getActionForKey( e ) == GameAction.BACK) exit( null );
			return true;
		}
	};

	private static void installGate(){
		Game.inputHook = body -> {
			raiseGate();
			body.run();
		};
	}

	/** listeners added last are asked first: put the controls, then the gate, in front of anything newer */
	private static void raiseGate(){
		ReplayViewer v = current;
		if (v != null && v.controls != null && v.lastScene == Game.scene()){
			for (PointerArea a : v.controls.areas()){
				PointerEvent.removePointerListener( a );
				PointerEvent.addPointerListener( a );
			}
		}
		PointerEvent.removePointerListener( POINTER_GATE );
		PointerEvent.addPointerListener( POINTER_GATE );
		KeyEvent.removeKeyListener( KEY_GATE );
		KeyEvent.addKeyListener( KEY_GATE );
	}

	private static void removeGate(){
		Game.inputHook = null;
		PointerEvent.removePointerListener( POINTER_GATE );
		KeyEvent.removeKeyListener( KEY_GATE );
	}
}
