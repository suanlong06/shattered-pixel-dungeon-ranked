package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

/**
 * spd-ranked (A1 client): turns the run's state into the reports the server wants
 * (PROGRESS / DIED / CLEARED), each only when something changed. Pure: the caller reads
 * the game (RankedSession) and sends what this returns, so it is unit tested without a game.
 * <p>
 * Bosses: boss {@code i} sits on depth {@code 5*(i+1)}. It counts as killed once the hero got
 * deeper than its floor, or when {@code Statistics.bossScores[i]} rises above the lowest value
 * seen: the score only goes up when the boss dies (hits taken lower it), which also covers the
 * final boss, where there is no deeper floor to reach.
 */
public final class ProgressTracker {

	public enum Report { NONE, PROGRESS, DIED, CLEARED }

	public static final int BOSS_FLOOR_STEP = 5;

	private final int finalBossDepth;
	private final int[] lowestScore = new int[5];
	private final boolean[] killed = new boolean[5];
	private boolean started;
	private boolean over;
	private int depth;
	private int bosses;
	private long turns;

	public ProgressTracker( int finalBossDepth ){
		this.finalBossDepth = finalBossDepth;
	}

	public int depth(){ return depth; }
	public int bosses(){ return bosses; }
	public long turns(){ return turns; }
	public boolean over(){ return over; }

	/**
	 * @param deepest   deepest floor reached so far
	 * @param turns     game turns so far
	 * @param bossScores Statistics.bossScores
	 * @param alive     whether the hero is alive
	 * @return the report to send now, if any
	 */
	public Report update( int deepest, long turns, int[] bossScores, boolean alive ){
		if (over) return Report.NONE;
		int n = Math.min( bossScores.length, killed.length );
		if (!started){
			for (int i = 0; i < n; i++) lowestScore[i] = bossScores[i];
		}
		int newBosses = 0;
		for (int i = 0; i < n; i++){
			if (!killed[i]){
				if (bossScores[i] < lowestScore[i]) lowestScore[i] = bossScores[i];
				if (deepest > BOSS_FLOOR_STEP * (i + 1) || (started && bossScores[i] > lowestScore[i])) killed[i] = true;
			}
			if (killed[i]) newBosses++;
		}
		// the server refuses anything past the final boss floor or going backwards
		int newDepth = Math.max( depth, Math.min( deepest, finalBossDepth ) );
		long newTurns = Math.max( this.turns, turns );
		boolean changed = !started || newDepth != depth || newBosses != bosses;
		started = true;
		depth = newDepth;
		bosses = newBosses;
		this.turns = newTurns;

		if (!alive){
			over = true;
			return Report.DIED;
		}
		int finalBoss = finalBossDepth / BOSS_FLOOR_STEP - 1;
		if (finalBoss >= 0 && finalBoss < n && killed[finalBoss] && depth >= finalBossDepth){
			over = true;
			return Report.CLEARED;
		}
		return changed ? Report.PROGRESS : Report.NONE;
	}
}
