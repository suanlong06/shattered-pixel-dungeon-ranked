package com.shatteredpixel.shatteredpixeldungeon.ranked.sim;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * spd-ranked (T0.8): runs the game turn by turn on the calling thread, with a fixed frame step,
 * feeding it player commands. Same actor order as Actor.process(); when an actor's turn ends in
 * a sprite callback (attacks, zaps, missiles), scene frames run until that callback calls next(),
 * exactly as the actor thread would wait. Level transitions are done as InterlevelScene does them.
 * Requires a booted libGDX (headless backend) and HeadlessGame.install(...).
 */
public final class TurnRunner {

	public interface Input {
		/** The next command, or null to stop. Called each time the hero waits for input (or lies dead with a WndResurrect pending). */
		Command next( TurnRunner runner );

		/**
		 * A command the player gave while the hero was acting (Command.at), due now: the hero has
		 * taken heroActs turns since the previous command and is about to act again. Null if none.
		 */
		default Command beforeHeroActs( TurnRunner runner, int heroActs ){ return null; }
	}

	/** Seconds per frame; changing it must never change gameplay (checked by tests). */
	public static float frameSeconds = 1 / 30f;
	public static final int MAX_FRAMES = 900;
	public static final int MAX_STEPS_PER_DECISION = 100_000;

	/**
	 * Tests only: play as the client does, recording into {@link CommandLog}. The input then gives
	 * its inputs itself, through the UI code paths and inside {@link CommandLog#asInput}, and returns
	 * any non-null command to go on (it is not applied). Replay is the default (false).
	 */
	public boolean live = false;
	/** seconds the player takes before answering a window; windows may ignore faster clicks (Chasm) */
	public static final float UI_PAUSE = 0.5f;

	public int decisions, actorSteps;
	public long frames;
	public String stop = "";
	/** state digest each time the hero waits for input, see {@link #stateDigest()} */
	public final List<String> trace = new ArrayList<>();
	public final List<Command> applied = new ArrayList<>();

	/** Starts a new run at depth 1 like InterlevelScene does for a new game (Dungeon.init already done by the caller). */
	public static TurnRunner startNewGame(){
		leaveScene();
		Level level = Dungeon.newLevel();
		Dungeon.switchLevel( level, -1 );
		TurnRunner r = new TurnRunner();
		r.buildScene( InterlevelScene.Mode.DESCEND );
		return r;
	}

	/** Continues the saved game in GamesInProgress.curSlot, as InterlevelScene.restore() does (RESUME). */
	public static TurnRunner resumeSavedGame() throws IOException {
		leaveScene();
		InterlevelScene.mode = InterlevelScene.Mode.CONTINUE;
		InterlevelScene.load();
		TurnRunner r = new TurnRunner();
		r.buildScene( InterlevelScene.Mode.CONTINUE );
		return r;
	}

	public Hero hero(){ return Dungeon.hero; }

	/** What {@link #step} needs next. */
	public enum Step {
		/** call step() again */
		CONTINUE,
		/** run one scene frame (sprites, tweens, posted callbacks), then call step() again */
		FRAME,
		/** a level change was requested; only with {@link #hostScenes}: call {@link #sceneBuilt()} once the new GameScene is up */
		SWITCH,
		/** the run is over for this runner, see {@link #stop} */
		STOP
	}

	/**
	 * The game's own InterlevelScene does level changes and its render loop runs the frames and posted
	 * callbacks (the in-game replay viewer). False for the headless runner, which does both itself.
	 */
	public boolean hostScenes = false;
	/** with {@link #hostScenes}: seconds the scene keeps running before each input, so a viewer can follow it */
	public float inputDelay = 0f;

	// where step() stopped: run() is a loop over step(), the viewer calls it from GameScene.update()
	private enum Phase { READY, SPRITE, CALLBACK, UI_PAUSE }
	private Phase phase = Phase.READY;
	private boolean awaiting = false;
	private int stepsSinceDecision = 0;
	/** the actor whose sprite or callback is waited for */
	private Actor waitingFor;
	/** frames waited (frame units of frameSeconds, so a variable frame rate counts the same scene time) */
	private float waited;
	/** UI_PAUSE: the command to apply after the pause, and whether it came from Input.next */
	private Command pending;
	private boolean pendingFromNext;
	private float pendingCooldown;
	/** with hostScenes: the scene ran this long since the hero started waiting for this input */
	private boolean inputReady;
	private boolean delayStarted;
	private int delayFrames;
	private float delayTime;

	/** Runs until the input returns null, the hero dies, or maxDecisions inputs were applied. */
	public void run( Input input, int maxDecisions ){
		awaiting = false;
		stepsSinceDecision = 0;
		if (!live) CommandLog.suspend( true );
		try {
			while (true){
				Step s = step( input, maxDecisions );
				if (s == Step.FRAME) frame();
				else if (s != Step.CONTINUE) return;
			}
		} finally {
			if (!live) CommandLog.suspend( false );
		}
	}

	/** Replay in the real game (viewer): nothing is recorded and every window is the game's, until {@link #endViewing()}. */
	public static void beginViewing(){
		CommandLog.suspend( true );
	}

	public static void endViewing(){
		CommandLog.suspend( false );
	}

	/** A runner for the viewer: the game's scenes and render loop do level changes and frames. */
	public static TurnRunner forViewer(){
		TurnRunner r = new TurnRunner();
		r.hostScenes = true;
		return r;
	}

	/** true when the next step() hands the hero an input (the hero waits, or lies dead with WndResurrect up) */
	public boolean waitsForInput(){
		return phase == Phase.READY && (awaiting || (Dungeon.hero != null && !Dungeon.hero.isAlive()));
	}

	/** with hostScenes: the GameScene the level change led to is up, carry on there */
	public void sceneBuilt(){
		phase = Phase.READY;
		awaiting = false;
		resetInputDelay();
	}

	/**
	 * One piece of the run: an actor's turn, an input, or one frame of waiting. Same order of
	 * events as the actor thread; frames only run sprites and callbacks, never gameplay rolls.
	 */
	public Step step( Input input, int maxDecisions ){
		switch (phase){
			case SPRITE:
				waited += Game.elapsed / frameSeconds;
				if (waited < MAX_FRAMES && ((Char) waitingFor).sprite.isMoving) return Step.FRAME;
				phase = Phase.READY;
				return act( waitingFor );
			case CALLBACK:
				waited += Game.elapsed / frameSeconds;
				return afterAct();
			case UI_PAUSE:
				waited += Game.elapsed;
				if (waited < UI_PAUSE) return Step.FRAME;
				phase = Phase.READY;
				finishApply();
				return Step.CONTINUE;
			default:
				break;
		}

		if (hostScenes){
			if (Game.switchingScene()) return Step.SWITCH;
		} else if (HeadlessGame.get().consumeSceneSwitch()){
			if (!transition()) return Step.STOP;
			awaiting = false;
			return Step.CONTINUE;
		}
		// dead with an unblessed ankh: the game waits for the player's choice in WndResurrect
		boolean dead = !Dungeon.hero.isAlive();
		if (dead && WndResurrect.instance == null){ stop = "hero died depth " + Dungeon.depth; return Step.STOP; }
		if (awaiting || dead){
			if (hostScenes){
				// the render loop keeps running while the game waits for the player: posted callbacks run
				// there, and the viewer gets a moment to see what happened
				if (!inputReady){
					if (delayStarted){
						delayFrames++;
						delayTime += Game.elapsed;
					}
					delayStarted = true;
					if (delayFrames < 2 || delayTime < inputDelay) return Step.FRAME;
					inputReady = true;
				}
			} else {
				runPostedCallbacks();
			}
			if (switchRequested()) return Step.CONTINUE;
			trace.add( stateDigest() );
			if (decisions >= maxDecisions){ stop = "decision limit"; return Step.STOP; }
			Command c = input.next( this );
			// the input may instead have started a level transition (scenario scripts in tests)
			if (switchRequested()) return Step.CONTINUE;
			if (c == null){ stop = "input ended"; return Step.STOP; }
			return beginApply( c, true );
		}
		if (++stepsSinceDecision > MAX_STEPS_PER_DECISION){ stop = "stuck: no input requested"; return Step.STOP; }
		Actor a = Actor.nextToAct();
		if (a == null){ stop = "no actors"; return Step.STOP; }
		if (a == Dungeon.hero){
			// an input given while the hero was acting goes in right before its next turn;
			// then look again, it may have changed who acts next
			Command c = input.beforeHeroActs( this, CommandLog.heroActs() );
			if (c != null) return beginApply( c, false );
		}
		if (a instanceof Char && ((Char) a).sprite != null && ((Char) a).sprite.isMoving){
			phase = Phase.SPRITE;
			waitingFor = a;
			waited = 0;
			return Step.FRAME;
		}
		return act( a );
	}

	private Step act( Actor a ){
		boolean doNext = Actor.actOnce( a );
		actorSteps++;
		if (doNext) return Step.CONTINUE;
		waitingFor = a;
		waited = 0;
		return afterAct();
	}

	/** an actor that did not call next() yet: frames run until its sprite callback does */
	private Step afterAct(){
		if (waited < MAX_FRAMES && Actor.waitingOn( waitingFor ) && !heroWaits() && !switchRequested()){
			phase = Phase.CALLBACK;
			return Step.FRAME;
		}
		phase = Phase.READY;
		awaiting = heroWaits();
		return Step.CONTINUE;
	}

	private boolean switchRequested(){
		return hostScenes ? Game.switchingScene() : HeadlessGame.get().switchRequested();
	}

	private Step beginApply( Command c, boolean fromNext ){
		pending = c;
		pendingFromNext = fromNext;
		pendingCooldown = Dungeon.hero.cooldown();
		if (!live && (c.type == Command.Type.BTN || c.type == Command.Type.BACK || c.type == Command.Type.SELECT)){
			// the scene keeps running while the player reads the window
			phase = Phase.UI_PAUSE;
			waited = 0;
			return Step.FRAME;
		}
		finishApply();
		return Step.CONTINUE;
	}

	private void finishApply(){
		Command c = pending;
		pending = null;
		if (live){
			decisions++;
		} else {
			c.apply();
			applied.add( c );
			decisions++;
			CommandLog.resetHeroActs();
		}
		stepsSinceDecision = 0;
		resetInputDelay();
		if (pendingFromNext){
			// an input that did nothing leaves the hero waiting, as in the UI
			awaiting = heroWaits() && Dungeon.hero.cooldown() == pendingCooldown && !switchRequested();
		}
	}

	private void resetInputDelay(){
		inputReady = delayStarted = false;
		delayFrames = 0;
		delayTime = 0;
	}

	/** callbacks posted with Game.runOnRenderThread, until none are left (as frames would while waiting) */
	private static void runPostedCallbacks(){
		for (int i = 0; i < 100 && HeadlessGame.get().runPostedCallbacks(); i++);
	}

	private static boolean heroWaits(){
		return Dungeon.hero.isAlive() && Dungeon.hero.ready && Dungeon.hero.curAction == null && !Dungeon.hero.resting;
	}

	/** One fixed frame of the scene graph (sprites, tweens, particles); never the actor thread. */
	public void frame(){
		frame( frameSeconds );
	}

	/** a frame of any length, as a render loop with a varying frame rate or Game.timeScale gives */
	public void frame( float seconds ){
		HeadlessGame.get().runPostedCallbacks();
		Game.elapsed = seconds;
		Game.timeTotal += seconds;
		Game.scene().updateMembers();
		frames++;
	}

	/** Leaves any GameScene, as switching to InterlevelScene does; levels must be built without one. */
	public static void leaveScene(){
		if (Game.instance instanceof HeadlessGame && Game.scene() instanceof GameScene){
			Game.scene().destroy();
			HeadlessGame.get().setScene( new Scene() );
		}
	}

	/**
	 * A level change, done as InterlevelScene's loading thread does it (InterlevelScene.load):
	 * stairs, falling, Return (beacons, passage, Fadeleaf, warp), resurrection, reset.
	 * Other scenes (alchemy, the surface, the amulet) are not supported yet.
	 */
	private boolean transition(){
		String unsupported = unsupportedSwitch( HeadlessGame.get().requestedScene() );
		if (unsupported != null){
			stop = unsupported;
			return false;
		}
		InterlevelScene.Mode mode = InterlevelScene.mode;
		leaveScene();
		try {
			InterlevelScene.load();
		} catch (IOException e){
			throw new RuntimeException( e );
		}
		buildScene( mode );
		return true;
	}

	/** null if a switch to scene next is a level change replay can follow, else why not */
	public static String unsupportedSwitch( Class<?> next ){
		InterlevelScene.Mode mode = InterlevelScene.mode;
		if (next != InterlevelScene.class){
			return "unsupported scene switch: " + (next == null ? "none" : next.getSimpleName());
		}
		if (mode == null || mode == InterlevelScene.Mode.CONTINUE || mode == InterlevelScene.Mode.NONE
				|| ((mode == InterlevelScene.Mode.DESCEND || mode == InterlevelScene.Mode.ASCEND) && InterlevelScene.curTransition == null)){
			return "unsupported scene switch: " + mode;
		}
		return null;
	}

	/** Tests: what the game's InterlevelScene does for a viewer runner (hostScenes) after Step.SWITCH. */
	public boolean transitionHeadless(){
		HeadlessGame.get().consumeSceneSwitch();
		return transition();
	}

	private void buildScene( InterlevelScene.Mode mode ){
		InterlevelScene.mode = mode;
		GameScene gs = new GameScene();
		HeadlessGame.get().setScene( gs );
		gs.create();
		HeadlessGame.get().consumeSceneSwitch();
	}

	/** Gameplay state when the hero waits for input: time, hero, every char, every heap. */
	public static String stateDigest(){
		Hero h = Dungeon.hero;
		StringBuilder sb = new StringBuilder();
		// times are relative to now: saving rebases all actor times (Actor.fixTime), absolute time is not game state
		sb.append(Dungeon.depth).append(',').append(Dungeon.branch).append('|')
				.append(h.pos).append(',').append(h.HP).append('/').append(h.HT).append(",lvl").append(h.lvl).append(",exp").append(h.exp)
				.append(",gold").append(Dungeon.gold).append(",t").append(h.cooldown()).append('|');
		List<String> items = new ArrayList<>();
		h.belongings.forEach( i -> items.add( i.getClass().getSimpleName() + "x" + i.quantity() + "+" + i.level() ) );
		sb.append(items).append('|');
		// choices, only once made (a run without any keeps its old digest)
		StringBuilder ch = new StringBuilder();
		if (h.subClass != null && h.subClass != com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass.NONE) ch.append(h.subClass).append(',');
		if (h.armorAbility != null) ch.append(h.armorAbility.getClass().getSimpleName()).append(',');
		for (java.util.LinkedHashMap<com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent, Integer> tier : h.talents){
			for (java.util.Map.Entry<com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent, Integer> e : tier.entrySet()){
				if (e.getValue() > 0) ch.append(e.getKey()).append('=').append(e.getValue()).append(',');
			}
		}
		if (ch.length() > 0) sb.append(ch).append('|');
		List<String> cs = new ArrayList<>();
		for (Char c : Actor.chars()) if (c != h) cs.add( c.getClass().getSimpleName() + "@" + c.pos + ":" + c.HP + "t" + c.cooldown() );
		Collections.sort( cs );
		sb.append(cs).append('|');
		List<String> hs = new ArrayList<>();
		for (Heap heap : Dungeon.level.heaps.valueList()) hs.add( heap.pos + ":" + heap.items.size() );
		Collections.sort( hs );
		sb.append(hs);
		return sb.toString();
	}

	/** SHA-256 over the whole trace, 16 hex chars. */
	public String traceDigest(){
		return digest( trace );
	}

	/**
	 * The same over the states the game went through: repeats in a row are dropped, so inputs that
	 * changed nothing (a UI-only window in the client, a NOTE in replay) do not count.
	 */
	public String stateSequenceDigest(){
		List<String> states = new ArrayList<>();
		for (String s : trace) if (states.isEmpty() || !states.get( states.size() - 1 ).equals( s )) states.add( s );
		return digest( states );
	}

	private static String digest( List<String> lines ){
		try {
			MessageDigest md = MessageDigest.getInstance( "SHA-256" );
			for (String s : lines){
				md.update( s.getBytes( StandardCharsets.UTF_8 ) );
				md.update( (byte) '\n' );
			}
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest()) sb.append( String.format( "%02x", b ) );
			return sb.substring( 0, 16 );
		} catch (NoSuchAlgorithmException e){
			throw new RuntimeException( e );
		}
	}
}
