package com.shatteredpixel.shatteredpixeldungeon.ranked.sim;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;

import java.util.List;

/**
 * spd-ranked (A3): replays one player's Ranked run from its seed and command log and reports
 * the facts a Duel verdict depends on (docs/A3-replay-verifier.md). Needs a booted libGDX
 * (headless backend) and {@link HeadlessGame#install}; one replay at a time, the game is static.
 *
 * What T0.8 cannot replay yet is reported in {@link Facts#unsupported} instead of being guessed:
 * a switch to a scene other than a level (alchemy), a command that cannot be applied, a window
 * still open when a command that needs none comes (the client closed it in a way that was not
 * recorded), a NOTE the client logged, or any other runner stop than running out of commands or
 * the hero's death.
 */
public final class ReplayCheck {

	public static final class Facts {
		/** Statistics.deepestFloor */
		public int depth;
		/** bosses killed, in order: index i is the boss of depth 5*(i+1) */
		public final boolean[] bosses = new boolean[5];
		/** whole game turns, (long) Statistics.duration */
		public long turns;
		public boolean died;
		public int commandsApplied;
		/** null if the replay used only what T0.8 supports, else why it may differ from the real game */
		public String unsupported;
		public String stop = "";
		public String traceDigest = "";
		/** {@link TurnRunner#stateSequenceDigest()} */
		public String stateDigest = "";

		public int bossesKilled(int finalBossDepth){
			int n = 0;
			for (int i = 0; i < finalBossDepth / 5 && i < bosses.length; i++) if (bosses[i]) n++;
			return n;
		}

		public boolean cleared(int finalBossDepth){
			int i = finalBossDepth / 5 - 1;
			return i >= 0 && i < bosses.length && bosses[i];
		}

		@Override
		public String toString(){
			StringBuilder b = new StringBuilder();
			for (boolean x : bosses) b.append(x ? '1' : '0');
			return "depth " + depth + " bosses " + b + " turns " + turns + (died ? " dead" : "")
					+ " cmds " + commandsApplied + " stop '" + stop + "'" + (unsupported != null ? " unsupported '" + unsupported + "'" : "");
		}
	}

	private ReplayCheck(){}

	/** Starts the run as a Ranked client does (clean profile, slot 1) and applies every command. */
	public static Facts replay( long seed, HeroClass cls, List<Command> cmds ){
		return replay( seed, cls, cmds, null );
	}

	/** Tests: setup changes the new run the same way the recorded run was changed, before any command. */
	public static Facts replay( long seed, HeroClass cls, List<Command> cmds, Runnable setup ){
		TurnRunner r = start( seed, cls );
		if (setup != null) setup.run();
		Facts f = new Facts();
		// same rule as the client's ProgressTracker: penalties only lower a boss score, so a
		// score above its lowest so far is a kill; so is being deeper than that boss's floor
		int[] lowest = Statistics.bossScores.clone();
		Runnable bossCheck = () -> {
			for (int b = 0; b < f.bosses.length; b++){
				lowest[b] = Math.min( lowest[b], Statistics.bossScores[b] );
				if (Statistics.bossScores[b] > lowest[b] || Statistics.deepestFloor > 5 * (b + 1)) f.bosses[b] = true;
			}
		};
		try {
			r.run( new TurnRunner.Input(){
				int i;
				@Override
				public Command next( TurnRunner runner ){
					bossCheck.run();
					return i < cmds.size() ? take() : null;
				}

				@Override
				public Command beforeHeroActs( TurnRunner runner, int heroActs ){
					if (i >= cmds.size() || cmds.get( i ).at < 0 || cmds.get( i ).at > heroActs) return null;
					bossCheck.run();
					return take();
				}

				private Command take(){
					Command c = cmds.get( i );
					if (f.unsupported == null){
						if (c.type == Command.Type.NOTE){
							f.unsupported = "client: " + c.action;
						} else if (!c.answersWindow() && CommandLog.topWindow() != null){
							f.unsupported = "window " + CommandLog.windowName( CommandLog.topWindow() ) + " open before command " + i;
						}
					}
					i++;
					return c;
				}
			}, Integer.MAX_VALUE );
		} catch (RuntimeException e){
			if (f.unsupported == null) f.unsupported = "command failed: " + e;
		}
		bossCheck.run();
		// anything but "out of commands" or death means the runner could not follow the game
		if (f.unsupported == null && !r.stop.equals( "input ended" ) && !r.stop.startsWith( "hero died" )) f.unsupported = r.stop;
		// lying dead with WndResurrect up is not death yet; the client does not report it either
		f.died = !Dungeon.hero.isAlive() && WndResurrect.instance == null;
		f.depth = Statistics.deepestFloor;
		f.turns = (long) Statistics.duration;
		f.commandsApplied = r.decisions;
		f.stop = r.stop;
		f.traceDigest = r.traceDigest();
		f.stateDigest = r.stateSequenceDigest();
		return f;
	}

	/** A new Ranked run at depth 1, as the client starts it; the hero has not acted yet. */
	public static TurnRunner start( long seed, HeroClass cls ){
		TurnRunner.leaveScene();
		Features.setMode( GameMode.RANKED );
		GamesInProgress.selectedClass = cls;
		GamesInProgress.curSlot = 1;
		Dungeon.daily = Dungeon.dailyReplay = false;
		Dungeon.customSeedText = "";
		Dungeon.seed = seed;
		Dungeon.hero = null;
		WndResurrect.instance = null;
		Dungeon.init();
		return TurnRunner.startNewGame();
	}
}
