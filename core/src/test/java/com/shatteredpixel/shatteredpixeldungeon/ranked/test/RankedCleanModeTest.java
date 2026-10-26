package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Bones;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTransmutation;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ExoticCrystals;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.RankedDraws;
import com.watabou.utils.Bundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T0.7: in Ranked, floors 1..MAX depend only on the seed, not on the player's profile or on
 * play that draws from the decks levelgen uses (S1 findings 1, 5, 6, 7).
 * T0.2: game mode and feature flags.
 */
@Tag("determinism")
class RankedCleanModeTest {

	static final int MAX = 12;
	static final long[] SEEDS = {987654321L, 12345L};

	enum Play { NONE, THIEF_LOOT, CURSED_WAND, WEALTH, TRANSMUTE, EXOTIC_CRYSTALS }

	@AfterEach
	void backToSolo(){
		Features.setMode(GameMode.SOLO);
	}

	static List<String> run(GameMode mode, long seed, HeroClass cls, Headless.Profile profile, Play p){
		Features.setMode(mode);
		Headless.newRun(seed, cls, profile);
		if (p == Play.EXOTIC_CRYSTALS){
			ExoticCrystals t = new ExoticCrystals();
			t.level(3);
			Dungeon.hero.belongings.backpack.items.add(t);
		}
		List<String> out = new ArrayList<>();
		for (int d = 1; d <= MAX; d++){
			Level l = Headless.generate(d);
			out.add(Digest.of(l, LevelGenRun.IGNORE_IDS));
			play(p);
		}
		return out;
	}

	/** the same calls the hooked SPD code makes */
	static void play(Play p){
		switch (p){
			case THIEF_LOOT:
				for (int i = 0; i < 2; i++){
					Thief t = new Thief();
					setLoot(t, Generator.Category.ARTIFACT);
					t.createLoot();
				}
				break;
			case CURSED_WAND:
				RankedDraws.usingDefaults(Generator.Category.ARTIFACT);
				break;
			case WEALTH:
				RankedDraws.random(Generator.Category.ARTIFACT);
				Generator.randomUsingDefaults(Generator.Category.RING);
				break;
			case TRANSMUTE:
				ScrollOfTransmutation.changeItem(new ChaliceOfBlood());
				break;
			default:
		}
	}

	static void setLoot(Mob m, Object loot){
		try {
			java.lang.reflect.Field f = Mob.class.getDeclaredField("loot");
			f.setAccessible(true);
			f.set(m, loot);
		} catch (ReflectiveOperationException e){
			throw new RuntimeException(e);
		}
	}

	@Test
	void rankedFloorsIgnoreProfileAndPlay(){
		for (long seed : SEEDS){
			HeroClass cls = HeroClass.WARRIOR;
			List<String> base = run(GameMode.RANKED, seed, cls, Headless.Profile.VETERAN, Play.NONE);
			List<String> fresh = run(GameMode.RANKED, seed, cls, Headless.Profile.FRESH, Play.NONE);
			assertEquals(base, fresh, LevelGenDeterminismTest.report("ranked fresh profile, seed " + seed, base, fresh));
			for (Play p : Play.values()){
				List<String> other = run(GameMode.RANKED, seed, cls, Headless.Profile.FRESH, p);
				assertEquals(base, other, LevelGenDeterminismTest.report("ranked " + p + ", seed " + seed, base, other));
			}
		}
	}

	/** the check above can fail: in Solo the same play does change later floors */
	@Test
	void soloStillShowsTheDrift(){
		long seed = 987654321L;
		List<String> base = run(GameMode.SOLO, seed, HeroClass.WARRIOR, Headless.Profile.VETERAN, Play.NONE);
		assertNotEquals(base, run(GameMode.SOLO, seed, HeroClass.WARRIOR, Headless.Profile.VETERAN, Play.THIEF_LOOT));
		assertNotEquals(base, run(GameMode.SOLO, seed, HeroClass.WARRIOR, Headless.Profile.VETERAN, Play.EXOTIC_CRYSTALS));
	}

	@Test
	void rankedProfileRules(){
		Headless.boot();
		Features.setMode(GameMode.RANKED);
		for (HeroClass c : HeroClass.values()) assertTrue(c.isUnlocked(), c.name());
		assertNull(Bones.get());
		Headless.newRun(12345L, HeroClass.MAGE, Headless.Profile.FRESH);
		assertEquals(0f, ExoticCrystals.consumableExoticChance(), 0f);
		Generator.Category cat = Generator.Category.TRINKET;
		for (int i = 0; i < cat.classes.length; i++){
			if (cat.classes[i] == ExoticCrystals.class) assertEquals(0f, cat.probs[i]);
		}
	}

	@Test
	void soloKeepsExoticCrystals(){
		Features.setMode(GameMode.SOLO);
		Headless.newRun(12345L, HeroClass.WARRIOR, Headless.Profile.VETERAN);
		Generator.Category cat = Generator.Category.TRINKET;
		for (int i = 0; i < cat.classes.length; i++){
			if (cat.classes[i] == ExoticCrystals.class) assertEquals(1f, cat.defaultProbs[i]);
		}
	}

	@Test
	void modeAndFlagsRoundTrip(){
		Headless.boot();
		Features.setMode(GameMode.RANKED);
		for (Feature f : Feature.values()) assertEquals(f.ranked, Features.enabled(f));
		Features.setSolo(Feature.FOOD_FORECAST, !Feature.FOOD_FORECAST.ranked);
		assertEquals(Feature.FOOD_FORECAST.ranked, Features.enabled(Feature.FOOD_FORECAST), "ranked ignores solo settings");

		Bundle b = new Bundle();
		Features.store(b);
		Features.setMode(GameMode.SOLO);
		Features.restore(b);
		assertEquals(GameMode.RANKED, Features.mode());

		Features.setMode(GameMode.SOLO);
		Features.setSolo(Feature.FOOD_FORECAST, false);
		assertFalse(Features.enabled(Feature.FOOD_FORECAST));
		Features.setSolo(Feature.FOOD_FORECAST, true);
		assertTrue(Features.enabled(Feature.FOOD_FORECAST));

		Features.restore(new Bundle());
		assertEquals(GameMode.SOLO, Features.mode(), "old saves are Solo");
	}
}
