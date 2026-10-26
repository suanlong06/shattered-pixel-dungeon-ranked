package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.watabou.noosa.Game;
import com.watabou.utils.PathFinder;

import java.util.Arrays;
import java.util.List;

/**
 * Test bot: produces the same commands a player could give. Attack the adjacent enemy with the
 * lowest id, rest when below 70% HP (until full), otherwise tap the exit (walks there, then descends).
 * Scenario rules outside player input are in {@link Scenario} and are applied identically on replay.
 */
public class Bot implements TurnRunner.Input {

	/** Test scenario rules, deterministic functions of game state, run before every input. */
	public static final class Scenario {
		/** hero cannot die (timing runs) */
		final boolean godMode;
		/** after this many inputs on one floor, move to the next floor as if the stairs were taken (0 = never) */
		final int stuckLimit;
		final int maxDepth;
		int floorDepth = -1, floorInputs;
		public int skipped;

		public Scenario(boolean godMode, int stuckLimit, int maxDepth){
			this.godMode = godMode; this.stuckLimit = stuckLimit; this.maxDepth = maxDepth;
		}

		/** @return true if it started a level transition instead of asking for input */
		boolean before(){
			// the bot knows the map, so tapping the exit always finds a path
			Arrays.fill(Dungeon.level.mapped, true);
			if (godMode){
				Dungeon.hero.HT = 1_000_000;
				Dungeon.hero.HP = Dungeon.hero.HT;
			}
			if (Dungeon.depth != floorDepth){ floorDepth = Dungeon.depth; floorInputs = 0; }
			if (stuckLimit > 0 && floorInputs >= stuckLimit && Dungeon.depth < maxDepth){
				LevelTransition exit = Dungeon.level.getTransition(LevelTransition.Type.REGULAR_EXIT);
				if (exit != null){
					skipped++;
					InterlevelScene.curTransition = exit;
					InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
					Game.switchScene(InterlevelScene.class);
					return true;
				}
			}
			floorInputs++;
			return false;
		}
	}

	public final Scenario scenario;
	private boolean resting;

	public Bot(Scenario scenario){ this.scenario = scenario; }

	@Override
	public Command next(TurnRunner r){
		if (scenario.before()) return null;
		// stuck on the last floor (e.g. a boss the bot cannot beat): stop
		if (scenario.stuckLimit > 0 && Dungeon.depth >= scenario.maxDepth && scenario.floorInputs > scenario.stuckLimit) return null;
		Hero hero = Dungeon.hero;
		Char enemy = null;
		for (int n : PathFinder.NEIGHBOURS8){
			Char c = Actor.findChar(hero.pos + n);
			if (c instanceof Mob && c.alignment == Char.Alignment.ENEMY && (enemy == null || c.id() < enemy.id())) enemy = c;
		}
		if (enemy != null) return Command.cell(enemy.pos);
		if (hero.HP < hero.HT * 7 / 10 || (resting && hero.HP < hero.HT)){
			resting = true;
			return Command.rest(false);
		}
		resting = false;
		LevelTransition exit = Dungeon.level.getTransition(LevelTransition.Type.REGULAR_EXIT);
		if (exit == null) return null;
		if (hero.pos == exit.cell() && Dungeon.depth >= scenario.maxDepth) return null;
		return Command.cell(exit.cell());
	}

	/** Replays recorded commands under the same scenario rules. */
	public static final class Replay implements TurnRunner.Input {
		private final List<Command> cmds;
		private final Scenario scenario;
		private int i;
		public Replay(List<Command> cmds, Scenario scenario){ this.cmds = cmds; this.scenario = scenario; }
		@Override
		public Command next(TurnRunner r){
			if (scenario.before()) return null;
			return i < cmds.size() ? cmds.get(i++) : null;
		}
	}
}
