package com.shatteredpixel.shatteredpixeldungeon.ranked.seed;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A4): lists the items lying on floors 1 to N of a seed, as a Ranked run generates them.
 *
 * Floors are generated in order in Ranked mode (clean profile, T0.7), which is what both duel
 * players get: play does not change later floors in Ranked (RankedCleanModeTest), so a sequential
 * scan sees the same items the players will. Only items placed by levelgen are listed (heaps,
 * including chests and remains); mob drops happen during play and are left out.
 *
 * Tooling for building the seed pool (server or offline): it starts a new run, so it must not be
 * called while a game is being played.
 */
public final class SeedScanner {

	/** L18: the pool is judged on floors 1 to 5 */
	public static final int DEPTH = 5;

	private SeedScanner(){}

	/** floor items per depth, index 0 = floor 1; heaps in cell order */
	public static List<List<Item>> scan( long seed, int depth ){
		//floor content does not depend on the class (checked by SeedPoolTest), any class works
		return scan( seed, depth, HeroClass.WARRIOR );
	}

	public static List<List<Item>> scan( long seed, int depth, HeroClass cls ){
		Features.setMode( GameMode.RANKED );
		GamesInProgress.selectedClass = cls;
		Dungeon.daily = Dungeon.dailyReplay = false;
		Dungeon.customSeedText = "";
		Dungeon.seed = seed;
		Dungeon.hero = null;
		Dungeon.init();

		List<List<Item>> floors = new ArrayList<>();
		for (int d = 1; d <= depth; d++){
			Dungeon.depth = d;
			Dungeon.branch = 0;
			Dungeon.level = Dungeon.newLevel();
			floors.add( items( Dungeon.level ) );
		}
		return floors;
	}

	public static List<Item> items( Level level ){
		List<Item> out = new ArrayList<>();
		for (int cell = 0; cell < level.length(); cell++){
			Heap h = level.heaps.get( cell );
			if (h != null) out.addAll( h.items );
		}
		return out;
	}
}
