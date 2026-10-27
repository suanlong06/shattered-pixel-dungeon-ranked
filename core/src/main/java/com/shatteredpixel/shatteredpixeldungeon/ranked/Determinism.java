package com.shatteredpixel.shatteredpixeldungeon.ranked;

import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;

/**
 * spd-ranked (T0.8): run-level state that must survive save/load for a run to continue
 * exactly as if it had never been interrupted: the gameplay RNG stack and the id counters
 * that used to be random. Hooked from Dungeon.saveGame / Dungeon.loadGame.
 */
public final class Determinism {

	private static final String RNG_STATE   = "spdr_rng_state";
	private static final String NEXT_SET_ID = "spdr_next_set_id";

	private Determinism(){}

	public static void store( Bundle bundle ){
		bundle.put( RNG_STATE, Random.getStackState() );
		bundle.put( NEXT_SET_ID, MissileWeapon.nextSetID );
		com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog.store( bundle );
	}

	/** Saves from before T0.8 have no state: they keep the generator they were given. */
	public static void restore( Bundle bundle ){
		if (bundle.contains( RNG_STATE )){
			Random.setStackState( bundle.getLongArray( RNG_STATE ) );
		}
		if (bundle.contains( NEXT_SET_ID )){
			MissileWeapon.nextSetID = bundle.getLong( NEXT_SET_ID );
		}
		com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog.restore( bundle );
	}
}
