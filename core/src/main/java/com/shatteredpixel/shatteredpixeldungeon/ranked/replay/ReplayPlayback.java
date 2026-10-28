package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayInput;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.AmuletScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.SurfaceScene;
import com.watabou.noosa.Game;

/**
 * spd-ranked (A5): playback state of a replay being watched: pause, speed, one input at a time.
 * Drives a {@link TurnRunner} one frame at a time; no UI, so it runs headless in tests too.
 * <p>
 * Pausing and speed only change how many frames pass and how long they are (Game.timeScale);
 * the runner applies the same inputs at the same points in the game whatever they are, so
 * watching never changes the run (frames run sprites and callbacks, never gameplay rolls).
 * Seeking forward is the same, played fast without the pause between inputs; seeking back is a
 * new playback from the start that seeks forward (the viewer restarts the run).
 */
public final class ReplayPlayback {

	public static final float[] SPEEDS = { 0.25f, 0.5f, 1f, 2f, 4f, 8f };
	public static final int DEFAULT_SPEED = 2;
	/** scene seconds (scaled by the speed) shown between two inputs, so the viewer can follow */
	public static final float INPUT_DELAY = 0.3f;
	/** actor turns at most per frame, so a long rest does not freeze the screen */
	static final int STEPS_PER_FRAME = 2000;
	/** speed while seeking */
	public static final float SEEK_SPEED = 16f;

	/** ended(): the hero left the dungeon with the Amulet (SurfaceScene) */
	public static final String WON_SURFACE = "won: surface";
	/** ended(): the hero picked up or used the Amulet (AmuletScene); the scene's choice is not recorded */
	public static final String WON_AMULET = "won: amulet";

	public enum Result { RUNNING, SWITCH, ENDED }

	public final ReplayFile file;
	public final TurnRunner runner;
	public final ReplayInput input;

	private int speed = DEFAULT_SPEED;
	private boolean paused;
	/** stepping one input: pause again once this many inputs were applied and the hero waits */
	private int stepTarget = -1;
	private boolean switching;
	private String ended;
	/** seeking: play fast until this many inputs were applied (or the floor below changed) and the hero waits, then pause */
	private int seekTarget = -1;
	/** seeking to the next floor: the depth and branch to leave, else null */
	private int[] seekFrom;
	/** a window of the viewer is up: nothing plays (a command answering the top window would go to it) */
	private boolean held;

	public ReplayPlayback( ReplayFile file, TurnRunner runner ){
		this( file, runner, new ReplayInput( file.commands ) );
	}

	/** tests: an input that also applies scenario rules before each command */
	public ReplayPlayback( ReplayFile file, TurnRunner runner, ReplayInput input ){
		this.file = file;
		this.runner = runner;
		this.input = input;
		runner.inputDelay = INPUT_DELAY;
	}

	/** Runs the game for one frame of the host's render loop. */
	public Result frame(){
		if (ended != null) return Result.ENDED;
		if (switching) return Result.SWITCH;
		if (held || paused()) return Result.RUNNING;
		runner.inputDelay = seeking() ? 0f : INPUT_DELAY;
		for (int n = 0; n < STEPS_PER_FRAME; n++){
			if (seeking() && runner.waitsForInput() && seekReached()){
				seekTarget = -1;
				seekFrom = null;
				paused = true;
				return Result.RUNNING;
			}
			if (stepTarget >= 0 && runner.decisions >= stepTarget && runner.waitsForInput()){
				stepTarget = -1;
				paused = true;
				return Result.RUNNING;
			}
			TurnRunner.Step s;
			try {
				s = runner.step( input, Integer.MAX_VALUE );
			} catch (RuntimeException e){
				// an input the game no longer accepts: the replay does not match this game
				end( "command failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()) );
				return Result.ENDED;
			}
			switch (s){
				case CONTINUE:
					continue;
				case FRAME:
					return Result.RUNNING;
				case SWITCH:
					String won = ending( Game.pendingScene() );
					if (won != null){
						// the end of the run: stay on the level it ended on (and keep the scene's callback,
						// which wins the game in the player's profile, from running)
						Game.cancelSceneSwitch();
						end( won );
						return Result.ENDED;
					}
					switching = true;
					return Result.SWITCH;
				default:
					end( runner.stop );
					return Result.ENDED;
			}
		}
		return Result.RUNNING;
	}

	/** the scenes a run ends in, with the ended() reason for them; null for any other scene */
	public static String ending( Class<?> next ){
		if (next == SurfaceScene.class) return WON_SURFACE;
		if (next == AmuletScene.class) return WON_AMULET;
		return null;
	}

	private boolean seekReached(){
		if (seekFrom != null) return Dungeon.depth != seekFrom[0] || Dungeon.branch != seekFrom[1];
		return runner.decisions >= seekTarget;
	}

	/** the level change is done and its GameScene is up */
	public void sceneBuilt(){
		if (!switching) return;
		switching = false;
		runner.sceneBuilt();
	}

	public void end( String reason ){
		if (ended == null) ended = reason == null ? "" : reason;
	}

	/** why the replay stopped, or null while it runs; "input ended" when every command was played */
	public String ended(){ return ended; }

	public boolean finished(){ return "input ended".equals( ended ); }

	public boolean switching(){ return switching; }

	/** Game.timeScale for the scene: 0 while paused or held (frozen), else the speed */
	public float timeScale(){
		if (held || paused()) return 0f;
		return seeking() ? SEEK_SPEED : SPEEDS[speed];
	}

	public boolean paused(){ return paused && stepTarget < 0 && !seeking(); }

	public boolean seeking(){ return seekTarget >= 0 || seekFrom != null; }

	/** pauses or plays; stops a seek or a step where it is */
	public void togglePause(){
		paused = !paused();
		stepTarget = -1;
		seekTarget = -1;
		seekFrom = null;
	}

	/** plays until the next input has been applied and the hero waits again, then pauses */
	public void stepOne(){
		if (ended != null) return;
		seekTarget = -1;
		seekFrom = null;
		paused = true;
		stepTarget = runner.decisions + 1;
	}

	/**
	 * Plays fast until target inputs were applied and the hero waits for the next one, then pauses.
	 * Only forward: a target at or before the inputs already applied pauses at the next wait.
	 */
	public void seekTo( int target ){
		if (ended != null) return;
		stepTarget = -1;
		seekFrom = null;
		seekTarget = Math.max( 0, target );
	}

	/** plays fast until the hero is on another floor and waits for an input, then pauses */
	public void seekNextFloor(){
		if (ended != null) return;
		stepTarget = -1;
		seekTarget = -1;
		seekFrom = new int[]{ Dungeon.depth, Dungeon.branch };
	}

	/** inputs applied so far (where seekTo targets count from) */
	public int applied(){ return runner.decisions; }

	/** while a window of the viewer is up, nothing plays and the scene is frozen */
	public void hold( boolean h ){ held = h; }

	public boolean held(){ return held; }

	public float speed(){ return SPEEDS[speed]; }

	public void faster(){ speed = Math.min( SPEEDS.length - 1, speed + 1 ); }

	public void slower(){ speed = Math.max( 0, speed - 1 ); }

	/** inputs applied so far, out of all in the file */
	public int done(){ return input.index(); }

	public int total(){ return input.size(); }
}
