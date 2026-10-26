package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.levels.SewerBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.log.EventLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.practice.BossPractice;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.ContextualKnowledge;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.DeathAnalysis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B1, B2 and B6: logic only, run headless. */
class TrainingTest {

	@AfterEach
	void backToSolo(){
		Features.setMode(GameMode.SOLO);
	}

	private Hero start(){
		Headless.newRun(12345L, HeroClass.WARRIOR);
		Headless.generate(1);
		Dungeon.hero.pos = Dungeon.level.entrance();
		return Dungeon.hero;
	}

	// B1

	@Test
	void deathAnalysisSumsRecentDamageAndKnownEscapes(){
		Hero hero = start();
		EventLog.reset(EventLog.SOLO_CAPACITY);
		Rat rat = new Rat();
		Gnoll gnoll = new Gnoll();
		EventLog.damage(hero, rat, 2);
		EventLog.damage(hero, gnoll, 4);
		EventLog.damage(hero, rat, 3);
		EventLog.damage(rat, hero, 5); // not damage to the hero
		EventLog.death(hero, gnoll);

		PotionOfHealing heal = new PotionOfHealing();
		heal.identify();
		heal.collect(hero.belongings.backpack);
		new PotionOfHaste().collect(hero.belongings.backpack); // unknown unless identified
		boolean hasteKnown = new PotionOfHaste().isIdentified();

		DeathAnalysis d = DeathAnalysis.of(hero);
		assertEquals("Gnoll", d.killer);
		assertEquals(Integer.valueOf(5), d.damage.get("Rat"));
		assertEquals(Integer.valueOf(4), d.damage.get("Gnoll"));
		assertEquals("Rat", d.damage.keySet().iterator().next(), "biggest source first");
		assertTrue(d.unusedEscapes.containsKey("PotionOfHealing"));
		assertEquals(hasteKnown, d.unusedEscapes.containsKey("PotionOfHaste"));

		List<String> lines = d.lines();
		assertEquals("Killed by Gnoll", lines.get(0));
		assertEquals("Damage in the last 10 turns: Rat 5, Gnoll 4", lines.get(1));
	}

	@Test
	void deathAnalysisIsReadOnly(){
		Hero hero = start();
		String before = Digest.of(Dungeon.level, Collections.emptySet());
		DeathAnalysis.of(hero).lines();
		assertEquals(before, Digest.of(Dungeon.level, Collections.emptySet()));
	}

	// B2

	@Test
	void contextualKnowledgeNotesOncePerRunAndOnlyWhatIsDiscovered(){
		start();
		Rat rat = new Rat();
		boolean known = Bestiary.isSeen(Rat.class);
		String first = ContextualKnowledge.note(rat);
		assertEquals(known, first.contains("max HP"));
		assertNull(ContextualKnowledge.note(new Rat()), "one note per kind per run");

		Bestiary.setSeen(Rat.class);
		ContextualKnowledge.reset();
		assertTrue(ContextualKnowledge.note(rat).startsWith("Rat: max HP " + rat.HT));
	}

	@Test
	void newFlagsAreOffInRanked(){
		Features.setMode(GameMode.RANKED);
		assertFalse(Features.enabled(Feature.DEATH_ANALYSIS));
		assertFalse(Features.enabled(Feature.CONTEXTUAL_KNOWLEDGE));
	}

	// B6

	private String practice(long seed){
		Headless.newRun(seed, HeroClass.MAGE);
		BossPractice.setUp(BossPractice.Boss.GOO);
		return Digest.of(Dungeon.level, Collections.emptySet())
				+ "|" + Dungeon.hero.lvl + "|" + Dungeon.hero.HT + "|" + Dungeon.hero.STR
				+ "|" + Dungeon.hero.belongings.weapon().level() + "|" + Dungeon.hero.pos;
	}

	@Test
	void bossPracticeStartsOnTheBossFloorWithTheKit(){
		practice(777L);
		assertTrue(Features.isPractice());
		assertFalse(Features.isRanked());
		assertEquals(5, Dungeon.depth);
		assertTrue(Dungeon.level instanceof SewerBossLevel);
		Hero hero = Dungeon.hero;
		assertEquals(BossPractice.Boss.GOO.heroLevel, hero.lvl);
		assertEquals(hero.HT, hero.HP);
		assertEquals(BossPractice.Boss.GOO.upgrades, hero.belongings.weapon().level());
		assertTrue(hero.belongings.getAllItems(PotionOfHealing.class).size() > 0);
	}

	@Test
	void bossPracticeUsesItsOwnSlotAndPendingBoss(){
		assertTrue(BossPractice.SLOT > GamesInProgress.MAX_SLOTS, "never one of the normal game slots");
		assertNull(BossPractice.startPending(), "a normal run has no pending boss");
	}

	@Test
	void bossPracticeRestartIsIdentical(){
		assertEquals(practice(777L), practice(777L));
	}

	@Test
	void bossPracticeLeavesBadgesAndJournalAlone(){
		Headless.newRun(777L, HeroClass.WARRIOR);
		BossPractice.setUp(BossPractice.Boss.GOO);
		boolean badge = com.shatteredpixel.shatteredpixeldungeon.Badges.allUnlocked()
				.contains(com.shatteredpixel.shatteredpixeldungeon.Badges.Badge.BOSS_SLAIN_1);
		boolean seen = Bestiary.isSeen(Gnoll.class);
		int count = Bestiary.encounterCount(Gnoll.class);
		com.shatteredpixel.shatteredpixeldungeon.Badges.unlock(com.shatteredpixel.shatteredpixeldungeon.Badges.Badge.BOSS_SLAIN_1);
		Bestiary.setSeen(Gnoll.class);
		Bestiary.countEncounter(Gnoll.class);
		assertEquals(badge, com.shatteredpixel.shatteredpixeldungeon.Badges.isUnlocked(com.shatteredpixel.shatteredpixeldungeon.Badges.Badge.BOSS_SLAIN_1));
		assertEquals(seen, Bestiary.isSeen(Gnoll.class));
		assertEquals(count, Bestiary.encounterCount(Gnoll.class));
	}

	@Test
	void bossPracticeHuntressBowScalesWithKitLevel(){
		Headless.newRun(777L, HeroClass.HUNTRESS);
		BossPractice.setUp(BossPractice.Boss.TENGU);
		assertEquals(BossPractice.Boss.TENGU.heroLevel / 5,
				Dungeon.hero.belongings.getItem(com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow.class).level());
	}
}
