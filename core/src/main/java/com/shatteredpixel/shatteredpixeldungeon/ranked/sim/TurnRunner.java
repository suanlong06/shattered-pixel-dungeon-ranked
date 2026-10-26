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
		/** The next command, or null to stop. Called each time the hero waits for input. */
		Command next( TurnRunner runner );
	}

	/** Seconds per frame; changing it must never change gameplay (checked by tests). */
	public static float frameSeconds = 1 / 30f;
	public static final int MAX_FRAMES = 900;
	public static final int MAX_STEPS_PER_DECISION = 100_000;

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
		Mob.clearHeldAllies();
		Dungeon.loadGame( GamesInProgress.curSlot );
		if (Dungeon.depth == -1) {
			Dungeon.depth = com.shatteredpixel.shatteredpixeldungeon.Statistics.deepestFloor;
			Dungeon.switchLevel( Dungeon.loadLevel( GamesInProgress.curSlot ), -1 );
		} else {
			Level level = Dungeon.loadLevel( GamesInProgress.curSlot );
			Dungeon.switchLevel( level, Dungeon.hero.pos );
		}
		TurnRunner r = new TurnRunner();
		r.buildScene( InterlevelScene.Mode.CONTINUE );
		return r;
	}

	public Hero hero(){ return Dungeon.hero; }

	/** Runs until the input returns null, the hero dies, or maxDecisions inputs were applied. */
	public void run( Input input, int maxDecisions ){
		boolean awaiting = false;
		int stepsSinceDecision = 0;
		CommandLog.suspend( true );
		try {
			while (true){
				if (!Dungeon.hero.isAlive()){ stop = "hero died depth " + Dungeon.depth; return; }
				if (HeadlessGame.get().consumeSceneSwitch()){
					if (!transition()) return;
					awaiting = false;
					continue;
				}
				if (awaiting){
					trace.add( stateDigest() );
					if (decisions >= maxDecisions){ stop = "decision limit"; return; }
					Command c = input.next( this );
					// the input may instead have started a level transition (scenario scripts in tests)
					if (HeadlessGame.get().switchRequested()) continue;
					if (c == null){ stop = "input ended"; return; }
					float before = Dungeon.hero.cooldown();
					c.apply();
					applied.add( c );
					decisions++;
					stepsSinceDecision = 0;
					// an input that did nothing leaves the hero waiting, as in the UI
					awaiting = Dungeon.hero.ready && Dungeon.hero.curAction == null && !Dungeon.hero.resting
							&& Dungeon.hero.cooldown() == before && !HeadlessGame.get().switchRequested();
					continue;
				}
				if (++stepsSinceDecision > MAX_STEPS_PER_DECISION){ stop = "stuck: no input requested"; return; }
				Actor a = Actor.nextToAct();
				if (a == null){ stop = "no actors"; return; }
				if (a instanceof Char && ((Char) a).sprite != null){
					for (int f = 0; f < MAX_FRAMES && ((Char) a).sprite.isMoving; f++) frame();
				}
				boolean doNext = Actor.actOnce( a );
				actorSteps++;
				if (!doNext){
					for (int f = 0; f < MAX_FRAMES && Actor.waitingOn( a ) && !heroWaits() && !HeadlessGame.get().switchRequested(); f++){
						frame();
					}
					awaiting = heroWaits();
				}
			}
		} finally {
			CommandLog.suspend( false );
		}
	}

	private static boolean heroWaits(){
		return Dungeon.hero.ready && Dungeon.hero.curAction == null && !Dungeon.hero.resting;
	}

	/** One fixed frame of the scene graph (sprites, tweens, particles); never the actor thread. */
	public void frame(){
		Game.elapsed = frameSeconds;
		Game.timeTotal += frameSeconds;
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

	/** InterlevelScene.descend()/ascend() for regular stairs. Other transitions are not supported yet. */
	private boolean transition(){
		InterlevelScene.Mode mode = InterlevelScene.mode;
		LevelTransition t = InterlevelScene.curTransition;
		if ((mode != InterlevelScene.Mode.DESCEND && mode != InterlevelScene.Mode.ASCEND) || t == null){
			stop = "unsupported scene switch: " + mode;
			return false;
		}
		leaveScene();
		try {
			if (t.destBranch != Dungeon.branch && Dungeon.depth >= 16 && Dungeon.depth <= 20) {
				// as InterlevelScene: allies are not held when entering the city quest area
			} else {
				Mob.holdAllies( Dungeon.level );
			}
			Dungeon.saveAll();
			Level level;
			Dungeon.depth = t.destDepth;
			Dungeon.branch = t.destBranch;
			if (Dungeon.levelHasBeenGenerated( Dungeon.depth, Dungeon.branch )) {
				level = Dungeon.loadLevel( GamesInProgress.curSlot );
			} else {
				level = Dungeon.newLevel();
			}
			LevelTransition dest = level.getTransition( t.destType );
			InterlevelScene.curTransition = null;
			Dungeon.switchLevel( level, dest.cell() );
		} catch (IOException e){
			throw new RuntimeException( e );
		}
		buildScene( mode );
		return true;
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
		try {
			MessageDigest md = MessageDigest.getInstance( "SHA-256" );
			for (String s : trace){
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
