package com.shatteredpixel.shatteredpixeldungeon.ranked.race;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.HeadlessGame;
import com.watabou.noosa.Game;

/**
 * spd-ranked (A5): the player's best run per seed, kept as a ghost in the local folder
 * {@code ghosts/<seed>.txt}. The ghost raced against is the one stored when the run reached
 * its seed; a better run replaces the file but not the ghost of the run in progress.
 * Solo only (Ranked will race the opponent instead). Never touches game state or the RNG.
 */
public final class Ghosts {

	public static final String DIR = "ghosts/";

	private static long loadedSeed = Long.MIN_VALUE;
	private static Ghost racing;

	/** tests may turn file access on; off by default when the game runs headless */
	public static Boolean storage = null;

	private Ghosts(){}

	private static boolean active(){
		if (Features.isRanked() || Features.isPractice()) return false;
		if (!Features.enabled( Feature.GHOST_RACE )) return false;
		if (storage != null) return storage;
		return !(Game.instance instanceof HeadlessGame);
	}

	private static FileHandle file( long seed ){
		return Gdx.files.local( DIR + seed + ".txt" );
	}

	/** the ghost for the current seed, or null */
	public static Ghost racing(){
		if (!active()) return null;
		if (loadedSeed != Dungeon.seed){
			loadedSeed = Dungeon.seed;
			racing = load( Dungeon.seed );
		}
		return racing;
	}

	public static Ghost load( long seed ){
		FileHandle f = file( seed );
		if (!f.exists()) return null;
		try {
			Ghost g = Ghost.fromText( f.readString( "UTF-8" ) );
			return g.seed == seed ? g : null;
		} catch (Exception e){
			return null;
		}
	}

	/** called on each new milestone; stores the run if it beats the stored ghost */
	public static void offer( Splits now ){
		if (!active()) return;
		racing(); //pin the ghost of this run before the file may change
		Ghost stored = load( Dungeon.seed );
		if (stored != null && !Ghost.better( now, stored.splits )) return;
		try {
			file( Dungeon.seed ).writeString( new Ghost( Dungeon.seed, "best", now ).toText(), false, "UTF-8" );
		} catch (Exception e){
			//no space or no permission: racing still works with the old ghost
		}
	}

	/** new run or loaded run: forget the pinned ghost */
	public static void reset(){
		loadedSeed = Long.MIN_VALUE;
		racing = null;
	}
}
