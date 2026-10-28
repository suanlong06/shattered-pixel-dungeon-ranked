package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.net.RankedSession;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.TitleScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.input.GameAction;
import com.watabou.input.KeyBindings;
import com.watabou.input.KeyEvent;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.Game;
import com.watabou.noosa.PointerArea;
import com.watabou.noosa.Scene;
import com.watabou.utils.Random;
import com.watabou.utils.Signal;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A5): watches a replay in the real game scene. The run starts in its own save slot
 * like the recorded run started (seed, class, mode, challenges); then, instead of the actor
 * thread, {@link #update} steps the game from GameScene.update() with a {@link ReplayPlayback},
 * which feeds the recorded commands in exactly as the server's replay check does. Level changes
 * go through the game's own InterlevelScene; alchemy inputs are stepped from AlchemyScene.update()
 * the same way. The player's taps and keys do not reach the game
 * while watching (only the replay controls and the viewer's own windows), and nothing of the
 * replay is kept: no badges, rankings, journal, bones or ghosts, and the save slot is deleted on exit.
 * <p>
 * Seeking back starts the run again and seeks forward to the input. The viewer's windows (the
 * hero's inventory, seeking) hold the playback while they are up, and whatever they read cannot
 * change the run: the gameplay RNG is put back as it was when they close.
 */
public final class ReplayViewer {

	/** after Boss Practice's and Ranked's slots */
	public static final int SLOT = GamesInProgress.MAX_SLOTS + 3;

	private static ReplayViewer current;

	private final ReplayPlayback playback;
	private final int prevSlot;
	private final HeroClass prevClass;
	private final int prevLastClass;
	private Scene lastScene;
	private ReplayControls controls;
	private boolean exiting;
	/** the viewer's windows that may still be up, and the gameplay RNG from before the first opened */
	private final List<Window> windows = new ArrayList<>();
	private long[] rngBefore;
	/** real seconds the scene still fades in for (PixelScene's Fader): it runs even while paused, or stays black */
	private float fading;
	/** seeking back: the scene of the run before, which this viewer leaves alone until it is gone */
	private Scene replaced;

	private ReplayViewer( ReplayPlayback playback ){
		this.playback = playback;
		prevSlot = GamesInProgress.curSlot;
		prevClass = GamesInProgress.selectedClass;
		prevLastClass = SPDSettings.lastClass();
	}

	/** the same viewing session on a new playback (seeking back): the player's things to give back stay */
	private ReplayViewer( ReplayPlayback playback, ReplayViewer from ){
		this.playback = playback;
		prevSlot = from.prevSlot;
		prevClass = from.prevClass;
		prevLastClass = from.prevLastClass;
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
		beginRun( f );
		TurnRunner.beginViewing();
		installGate();
		Game.switchScene( InterlevelScene.class );
		return true;
	}

	/** the run starts as RankedSession.startRun starts a ranked run; InterlevelScene (DESCEND) does the rest */
	private static void beginRun( ReplayFile f ){
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

	/** GameScene.update and AlchemyScene.update, every frame while watching */
	public static void update( Scene scene ){
		if (current != null) current.frame( scene );
	}

	/** the scenes the replay runs in; the input gate and the controls only work there */
	static boolean playsIn( Scene s ){
		return s instanceof GameScene || s instanceof AlchemyScene;
	}

	/**
	 * Whether the viewer can follow the game to the scene it asked for, as the server's replay does:
	 * a level change, into alchemy, its reset, and back to the level. (A client closed in alchemy
	 * comes back from its save without the scene's destroy; the real game cannot do that here.)
	 */
	public static String unfollowable( Class<?> next ){
		if (next == AlchemyScene.class) return null;
		if (next == GameScene.class && Game.scene() instanceof AlchemyScene) return null;
		if (Game.scene() instanceof AlchemyScene && next == InterlevelScene.class) return "client closed in alchemy";
		return TurnRunner.unsupportedSwitch( next );
	}

	private void frame( Scene scene ){
		// leaving: the switch to the title screen happens next frame, the run must not request another
		if (exiting || scene == replaced) return;
		if (scene != lastScene){
			lastScene = scene;
			playback.sceneBuilt();
			controls = new ReplayControls( this );
			scene.add( controls );
			fading = 1f;
		}
		if (!windows.isEmpty()) closedWindows();
		if (playback.frame() == ReplayPlayback.Result.SWITCH && Game.switchingScene()){
			String unsupported = unfollowable( Game.pendingScene() );
			if (unsupported != null){
				// the game went somewhere replay cannot follow (a client closed in alchemy...)
				playback.end( unsupported );
				exit( Messages.get( ReplayViewer.class, "unsupported_scene" ) );
			}
		}
		Game.timeScale = playback.ended() != null && !playback.paused() ? 1f : playback.timeScale();
		if (fading > 0){
			// only tweens and animations run meanwhile; the game itself waits for the playback
			fading -= com.badlogic.gdx.Gdx.graphics.getDeltaTime();
			if (Game.timeScale == 0f) Game.timeScale = 1f;
		}
		controls.refresh();
	}

	// controls

	void togglePause(){ playback.togglePause(); }
	void stepOne(){ playback.stepOne(); }
	void faster(){ playback.faster(); }
	void slower(){ playback.slower(); }
	ReplayPlayback state(){ return playback; }

	/** pauses once the hero waits after target inputs; back means the run starts again and plays up to there */
	void seek( int target ){
		target = Math.max( 0, Math.min( target, playback.total() ) );
		if (target > playback.applied() && playback.ended() == null){
			playback.seekTo( target );
		} else {
			restart( target );
		}
	}

	void seekNextFloor(){
		playback.seekNextFloor();
	}

	/** the run again from its start, in the same viewing session, seeking to target inputs */
	private void restart( int target ){
		if (exiting) return;
		closeWindows();
		ReplayPlayback pb = new ReplayPlayback( playback.file, TurnRunner.forViewer() );
		pb.seekTo( target );
		current = new ReplayViewer( pb, this );
		current.replaced = Game.scene();
		beginRun( playback.file );
		Game.switchScene( InterlevelScene.class );
	}

	/** shows one of the viewer's windows (read-only: inventory, item info, seeking); the playback holds meanwhile */
	void show( Window w ){
		if (windows.isEmpty()){
			try {
				rngBefore = Random.getStackState();
			} catch (IllegalStateException e){
				// test harnesses wrap the generators; their RNG is checked by comparing the run instead
				rngBefore = null;
			}
		}
		windows.add( w );
		playback.hold( true );
		Game.scene().addToFront( w );
	}

	private boolean windowUp(){
		for (Window w : windows) if (w.parent != null) return true;
		return false;
	}

	/** the viewer's windows all closed: the playback goes on with the RNG it had */
	private void closedWindows(){
		if (windowUp()) return;
		windows.clear();
		if (rngBefore != null) Random.setStackState( rngBefore );
		rngBefore = null;
		playback.hold( false );
	}

	private void closeWindows(){
		for (Window w : new ArrayList<>( windows )) if (w.parent != null) w.hide();
		closedWindows();
	}

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
		// GameScene.create remembers the hero class as the player's last one
		SPDSettings.lastClass( v.prevLastClass );
		current = null;
	}

	// input gate: while the game scene is up, presses only reach the replay controls

	private static final Signal.Listener<PointerEvent> POINTER_GATE = new Signal.Listener<PointerEvent>(){
		@Override
		public boolean onSignal( PointerEvent e ){
			// moves, releases and hovers pass: nothing clicks without a press it saw
			if (current == null || e == null || e.type != PointerEvent.Type.DOWN) return false;
			if (!playsIn( Game.scene() )) return false;
			ReplayControls c = current.controls;
			// the viewer's windows get every press except on the controls, which do nothing meanwhile
			if (current.windowUp()) return c != null && c.hits( e );
			return c == null || !c.hits( e );
		}
	};

	private static final Signal.Listener<KeyEvent> KEY_GATE = new Signal.Listener<KeyEvent>(){
		@Override
		public boolean onSignal( KeyEvent e ){
			if (current == null || !playsIn( Game.scene() )) return false;
			if (e.pressed && KeyBindings.getActionForKey( e ) == GameAction.BACK){
				// back closes the viewer's top window first
				Window top = null;
				for (Window w : current.windows) if (w.parent != null) top = w;
				if (top != null) top.onBackPressed();
				else exit( null );
			}
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
