package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayInput;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;

/**
 * spd-ranked (A5): playback state of a replay being watched: pause, speed, one input at a time.
 * Drives a {@link TurnRunner} one frame at a time; no UI, so it runs headless in tests too.
 * <p>
 * Pausing and speed only change how many frames pass and how long they are (Game.timeScale);
 * the runner applies the same inputs at the same points in the game whatever they are, so
 * watching never changes the run (frames run sprites and callbacks, never gameplay rolls).
 */
public final class ReplayPlayback {

	public static final float[] SPEEDS = { 0.25f, 0.5f, 1f, 2f, 4f, 8f };
	public static final int DEFAULT_SPEED = 2;
	/** scene seconds (scaled by the speed) shown between two inputs, so the viewer can follow */
	public static final float INPUT_DELAY = 0.3f;
	/** actor turns at most per frame, so a long rest does not freeze the screen */
	static final int STEPS_PER_FRAME = 2000;

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
		if (paused && stepTarget < 0) return Result.RUNNING;
		for (int n = 0; n < STEPS_PER_FRAME; n++){
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
					switching = true;
					return Result.SWITCH;
				default:
					end( runner.stop );
					return Result.ENDED;
			}
		}
		return Result.RUNNING;
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

	/** Game.timeScale for the scene: 0 while paused (frozen), else the speed */
	public float timeScale(){
		return paused && stepTarget < 0 ? 0f : SPEEDS[speed];
	}

	public boolean paused(){ return paused && stepTarget < 0; }

	public void togglePause(){
		paused = !paused();
		stepTarget = -1;
	}

	/** plays until the next input has been applied and the hero waits again, then pauses */
	public void stepOne(){
		if (ended != null) return;
		paused = true;
		stepTarget = runner.decisions + 1;
	}

	public float speed(){ return SPEEDS[speed]; }

	public void faster(){ speed = Math.min( SPEEDS.length - 1, speed + 1 ); }

	public void slower(){ speed = Math.max( 0, speed - 1 ); }

	/** inputs applied so far, out of all in the file */
	public int done(){ return input.index(); }

	public int total(){ return input.size(); }
}
