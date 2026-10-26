package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Longsword;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.seed.SeedScanner;
import com.shatteredpixel.shatteredpixeldungeon.ranked.seed.SeedScorer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A4: seed scanner and scorer, run headless. */
class SeedPoolTest {

	private static final long[] SEEDS = {12345L, 777L, 2026L};

	@AfterEach
	void backToSolo(){
		Features.setMode(GameMode.SOLO);
	}

	/** what a floor holds, in a form two scans can compare */
	static List<String> describe(List<List<Item>> floors){
		List<String> out = new ArrayList<>();
		for (List<Item> f : floors){
			StringBuilder s = new StringBuilder();
			for (Item i : f){
				s.append(i.getClass().getSimpleName()).append('+').append(i.level())
						.append('x').append(i.quantity()).append(i.cursed ? "c" : "").append(' ');
			}
			out.add(s.toString());
		}
		return out;
	}

	@Test
	void scanIsRepeatableAndClassDoesNotChangeFloors(){
		Headless.boot();
		for (long seed : SEEDS){
			List<String> base = describe(SeedScanner.scan(seed, SeedScanner.DEPTH));
			assertEquals(base, describe(SeedScanner.scan(seed, SeedScanner.DEPTH)), "seed " + seed);
			for (HeroClass c : HeroClass.values()){
				assertEquals(base, describe(SeedScanner.scan(seed, SeedScanner.DEPTH, c)), c + ", seed " + seed);
			}
			assertTrue(base.get(0).length() > 0, "floor 1 has items");
		}
	}

	@Test
	void scoringRules(){
		Headless.boot();
		PotionOfHealing heal = new PotionOfHealing();
		heal.quantity(2);
		assertEquals(6, SeedScorer.score(heal, HeroClass.WARRIOR));

		WandOfMagicMissile wand = new WandOfMagicMissile();
		wand.cursed = false;
		assertEquals(SeedScorer.WAND + wand.level(), SeedScorer.score(wand, HeroClass.WARRIOR));
		assertTrue(SeedScorer.score(wand, HeroClass.MAGE) > SeedScorer.score(wand, HeroClass.WARRIOR), "mage favours wands");
		wand.cursed = true;
		assertEquals(0, SeedScorer.score(wand, HeroClass.MAGE), "cursed gear counts as nothing");

		Longsword sword = new Longsword(); // tier 4
		sword.cursed = false;
		sword.enchantment = null;
		assertEquals(2, SeedScorer.score(sword, HeroClass.MAGE));
		assertEquals(3, SeedScorer.score(sword, HeroClass.DUELIST));
	}

	/** prints the score spread so MIN and CAP can be tuned; checks the pool is neither empty nor everything */
	@Test
	void poolFiltersSomeSeeds(){
		Headless.boot();
		long[] candidates = new long[30];
		for (int i = 0; i < candidates.length; i++) candidates[i] = 1_000_003L * (i + 1);
		int in = 0;
		for (long s : candidates){
			SeedScorer.Result r = SeedScorer.of(s);
			System.out.println("A4 " + r);
			assertTrue(r.min <= r.max);
			if (r.inPool()) in++;
		}
		assertEquals(in, SeedScorer.pool(candidates).size());
		assertTrue(in > 0 && in < candidates.length, in + " of " + candidates.length + " in pool");
	}
}
