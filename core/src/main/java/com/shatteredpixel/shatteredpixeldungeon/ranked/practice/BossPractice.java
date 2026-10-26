package com.shatteredpixel.shatteredpixeldungeon.ranked.practice;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;

/**
 * spd-ranked (B6): Boss Practice, a solo mode outside Ranked (DECISIONS L17 defaults):
 * the hero gets a fixed kit for the chosen boss and starts straight on its floor.
 * The kit is the same for every class (on top of the class's starting gear) and is
 * rebuilt from scratch each time, nothing carries over between bosses.
 *
 * Restarting an encounter (L13 default: from its start) is a new run with the same seed,
 * class and boss: hero, kit and floor come out identical, so the RNG is never saved or rewound.
 * Practice runs never reach the rankings ({@code Rankings.submit}) and the event log works
 * as in Solo, so the fight can be read back afterwards (B1).
 */
public final class BossPractice {

	/** kit per boss: hero level, extra strength, +N on starting weapon and armor, healing potions */
	public enum Boss {
		GOO   (  5,  5, 1, 1, 2 ),
		TENGU ( 10, 10, 2, 3, 3 ),
		DM300 ( 15, 14, 3, 5, 3 ),
		KING  ( 20, 18, 4, 7, 4 ),
		YOG   ( 25, 22, 5, 9, 5 );

		public final int depth, heroLevel, strength, upgrades, healing;

		Boss( int depth, int heroLevel, int strength, int upgrades, int healing ){
			this.depth = depth; this.heroLevel = heroLevel; this.strength = strength;
			this.upgrades = upgrades; this.healing = healing;
		}
	}

	private BossPractice(){}

	/**
	 * Call right after {@code Dungeon.init()} (which sets the mode back to Solo):
	 * marks the run as practice, applies the kit and generates the boss floor.
	 */
	public static Level setUp( Boss boss ){
		Features.setMode( GameMode.PRACTICE );
		applyKit( Dungeon.hero, boss );
		Dungeon.depth = boss.depth;
		Dungeon.branch = 0;
		Dungeon.level = Dungeon.newLevel();
		Dungeon.hero.pos = Dungeon.level.entrance();
		return Dungeon.level;
	}

	static void applyKit( Hero hero, Boss boss ){
		hero.lvl = boss.heroLevel;
		hero.STR += boss.strength;
		hero.updateHT( true );
		hero.HP = hero.HT;
		KindOfWeapon w = hero.belongings.weapon();
		if (w != null){
			w.upgrade( boss.upgrades );
			w.identify();
		}
		Armor a = hero.belongings.armor();
		if (a != null){
			a.upgrade( boss.upgrades );
			a.identify();
		}
		for (int i = 0; i < boss.healing; i++){
			new PotionOfHealing().identify().collect( hero.belongings.backpack );
		}
	}
}
