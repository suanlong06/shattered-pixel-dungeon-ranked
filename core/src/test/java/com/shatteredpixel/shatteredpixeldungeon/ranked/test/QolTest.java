package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Shadows;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.WellFed;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Longsword;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.log.EventLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.FoodForecast;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.KnownItems;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.RunValue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B3, B5, B4 and T-log: logic only, run headless. */
class QolTest {

	Hero hero;

	@BeforeEach
	void run(){
		Headless.newRun(12345L, HeroClass.WARRIOR);
		Headless.generate(1);
		hero = Dungeon.hero;
		hero.pos = Dungeon.level.entrance();
	}

	@AfterEach
	void backToSolo(){
		Features.setMode(GameMode.SOLO);
	}

	// B5

	@Test
	void foodForecastFollowsHungerRules(){
		FoodForecast f = FoodForecast.of(hero);
		assertEquals(1f, f.rate);
		assertEquals((int)Hunger.HUNGRY - (int)f.level, f.toHungry);
		assertEquals((int)Hunger.STARVING - (int)f.level, f.toStarving);

		int n = f.foodItems;
		float e = f.foodEnergy;
		new Food().collect(hero.belongings.backpack);
		FoodForecast g = FoodForecast.of(hero);
		assertEquals(n + 1, g.foodItems);
		assertEquals(e + Hunger.HUNGRY, g.foodEnergy);

		Buff.affect(hero, Shadows.class);
		assertEquals(1f / 1.5f, FoodForecast.of(hero).rate, 1e-6);
		Buff.detach(hero, Shadows.class);

		Buff.affect(hero, WellFed.class);
		FoodForecast p = FoodForecast.of(hero);
		assertEquals(0f, p.rate);
		assertEquals(-1, p.toHungry);
		assertTrue(p.summary().startsWith("paused"));
	}

	@Test
	void foodForecastIsReadOnly(){
		Hunger h = hero.buff(Hunger.class);
		int before = h.hunger();
		String rng = Digest.of(Dungeon.level, java.util.Collections.emptySet());
		FoodForecast.of(hero).summary();
		assertEquals(before, h.hunger());
		assertEquals(rng, Digest.of(Dungeon.level, java.util.Collections.emptySet()));
	}

	// B3

	@Test
	void knownItemIconOnlyForKnownTypes(){
		PotionOfHealing p = new PotionOfHealing();
		boolean known = p.isKnown();
		assertEquals(known ? p.icon : -1, KnownItems.icon(p));
		p.identify();
		assertEquals(p.icon, KnownItems.icon(p));

		Heap open = new Heap();
		open.drop(p);
		assertEquals(p.icon, KnownItems.icon(open));
		open.type = Heap.Type.CHEST;
		assertEquals(-1, KnownItems.icon(open));
		assertEquals(-1, KnownItems.icon(new Longsword()));
	}

	// B4

	@Test
	void runValueOnlyJudgesWhatIsKnown(){
		ScrollOfUpgrade s = new ScrollOfUpgrade();
		if (!s.isIdentified()) assertEquals(0, RunValue.score(s));
		s.identify();
		assertEquals(3, RunValue.score(s));
		assertTrue(RunValue.starred(s));

		Longsword w = new Longsword();
		w.level(2);
		assertEquals(0, RunValue.score(w), "unknown level is not judged");
		w.identify();
		assertEquals(2, RunValue.score(w));
		w.cursed = true;
		assertEquals(0, RunValue.score(w));
	}

	@Test
	void flagsAreOffInRanked(){
		Features.setMode(GameMode.RANKED);
		for (Feature f : new Feature[]{Feature.KNOWN_ITEMS, Feature.FOOD_FORECAST, Feature.SMART_INVENTORY}){
			assertFalse(Features.enabled(f));
		}
	}

	// T-log

	@Test
	void eventLogRecordsDamageHealUseAndRoundTrips(){
		EventLog.reset(EventLog.SOLO_CAPACITY);
		Rat rat = new Rat();
		rat.pos = hero.pos;
		rat.damage(3, this);
		EventLog.heroTurn(hero);
		hero.HP -= 5;
		EventLog.heroTurn(hero);
		hero.HP += 2;
		EventLog.heroTurn(hero);
		EventLog.useItem(hero, new Food(), "NOTHING"); // Item.execute needs a GameScene

		List<EventLog.Event> ev = EventLog.events();
		assertEquals(EventLog.Type.DAMAGE, ev.get(0).type);
		assertEquals("Rat", ev.get(0).actor);
		assertEquals("QolTest", ev.get(0).subject);
		assertEquals(3, ev.get(0).value);
		assertEquals(EventLog.Type.HEAL, ev.get(1).type);
		assertEquals(2, ev.get(1).value);
		assertEquals(EventLog.Type.USE_ITEM, ev.get(2).type);
		assertEquals("Food:NOTHING", ev.get(2).subject);

		for (EventLog.Event e : ev){
			assertEquals(e.line(), EventLog.Event.parse(e.line()).line());
			assertTrue(e.line().startsWith("v" + EventLog.VERSION + "|"));
		}
	}

	@Test
	void eventLogIsBoundedInSoloAndUnboundedInRanked(){
		EventLog.reset(10);
		for (int i = 0; i < 25; i++) EventLog.move(hero, i);
		assertEquals(10, EventLog.events().size());
		assertEquals(24, EventLog.events().get(9).value);

		Features.setMode(GameMode.RANKED);
		for (int i = 0; i < EventLog.SOLO_CAPACITY + 5; i++) EventLog.move(hero, i);
		assertEquals(EventLog.SOLO_CAPACITY + 5, EventLog.events().size());
	}
}
