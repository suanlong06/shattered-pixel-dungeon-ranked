package com.shatteredpixel.shatteredpixeldungeon.ranked.race;

import java.util.Map;

/**
 * spd-ranked (A5): a ghost is the splits of an earlier run on the same seed (the player's best,
 * or the opponent's from a finished duel). The race compares milestone by milestone, so it works
 * across the whole run and not only at the end (TASKS A5, A2).
 * Pure data: never reads the game RNG or state.
 */
public final class Ghost {

	public final long seed;
	public final String label;
	public final Splits splits;

	public Ghost( long seed, String label, Splits splits ){
		this.seed = seed;
		this.label = label;
		this.splits = splits.copy();
	}

	/** a comparison at the latest milestone both runs reached; delta &lt; 0 = ahead of the ghost */
	public static final class Delta {
		public final int depth;
		public final boolean boss;
		public final float delta;

		Delta( int depth, boolean boss, float delta ){
			this.depth = depth; this.boss = boss; this.delta = delta;
		}

		public boolean ahead(){
			return delta < 0;
		}
	}

	/** null when no milestone is shared yet */
	public Delta compare( Splits now ){
		Delta best = null;
		for (Map.Entry<Integer, Float> e : now.arrived().entrySet()){
			Float g = splits.arrivedAt( e.getKey() );
			if (g != null) best = new Delta( e.getKey(), false, e.getValue() - g );
			Float nb = now.bossAt( e.getKey() ), gb = splits.bossAt( e.getKey() );
			if (nb != null && gb != null) best = new Delta( e.getKey(), true, nb - gb );
		}
		return best;
	}

	/**
	 * Which of two runs on the same seed is the better ghost: the one that killed the deeper
	 * boss, then got deeper, then got there sooner.
	 */
	public static boolean better( Splits a, Splits b ){
		int ba = a.lastBoss(), bb = b.lastBoss();
		if (ba != bb) return ba > bb;
		if (a.deepest() != b.deepest()) return a.deepest() > b.deepest();
		if (a.deepest() == 0) return false;
		Float ta = ba > 0 ? a.bossAt( ba ) : a.arrivedAt( a.deepest() );
		Float tb = bb > 0 ? b.bossAt( bb ) : b.arrivedAt( b.deepest() );
		return ta < tb;
	}

	public String toText(){
		return "spdr-ghost v1\nseed=" + seed + "\nlabel=" + label.replace( '\n', ' ' ) + "\n" + splits.toText();
	}

	public static Ghost fromText( String text ){
		String[] lines = text.split( "\n" );
		if (lines.length < 3 || !lines[0].startsWith( "spdr-ghost v" )) throw new IllegalArgumentException( "not a ghost" );
		long seed = Long.parseLong( lines[1].substring( "seed=".length() ) );
		String label = lines[2].substring( "label=".length() );
		StringBuilder rest = new StringBuilder();
		for (int i = 3; i < lines.length; i++) rest.append( lines[i] ).append( '\n' );
		return new Ghost( seed, label, Splits.fromText( rest.toString() ) );
	}
}
