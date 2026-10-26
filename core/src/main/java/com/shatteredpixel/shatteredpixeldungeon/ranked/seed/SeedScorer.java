package com.shatteredpixel.shatteredpixeldungeon.ranked.seed;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * spd-ranked (A4): how much the items on floors 1 to 5 help a hero survive and move fast (L18 defaults).
 *
 * Every class is scored on its own and a seed is judged on its weakest class, so a pool seed is
 * fair whichever class each player picks. A cap on the strongest class keeps out seeds where
 * one lucky find decides the duel. The scorer sees the true items (it runs on the server, not
 * for the player), including curses: a cursed item counts as nothing.
 *
 * Upgrade scrolls and strength potions are left out on purpose: levelgen gives the same number
 * of them on every seed, so they do not tell seeds apart.
 */
public final class SeedScorer {

	/** a pool seed scores at least this for every class; edit here to tune (30 sample seeds on floors 1-5 scored 11 to 44, see SeedPoolTest output) */
	public static final int MIN = 18;
	/** and at most this for any class */
	public static final int CAP = 36;

	/** item class simple name -> points per item, for items scored by name; edit here to tune */
	public static final Map<String, Integer> TABLE = new LinkedHashMap<>();
	static {
		//survival
		TABLE.put( "PotionOfHealing",        3 );
		TABLE.put( "PotionOfExperience",     3 );
		TABLE.put( "Ankh",                   4 );
		TABLE.put( "PotionOfShielding",      2 );
		TABLE.put( "StoneOfEnchantment",     2 );
		TABLE.put( "ScrollOfRemoveCurse",    1 );
		TABLE.put( "ScrollOfMirrorImage",    1 );
		TABLE.put( "ScrollOfTerror",         1 );
		TABLE.put( "ScrollOfLullaby",        1 );
		TABLE.put( "PotionOfInvisibility",   1 );
		TABLE.put( "StoneOfBlink",           1 );
		TABLE.put( "StoneOfShock",           1 );
		TABLE.put( "StoneOfAugmentation",    1 );
		TABLE.put( "Honeypot",               1 );
		//speed
		TABLE.put( "ScrollOfMagicMapping",   2 );
		TABLE.put( "ScrollOfTeleportation",  2 );
		TABLE.put( "PotionOfHaste",          1 );
		TABLE.put( "PotionOfMindVision",     1 );
		TABLE.put( "StoneOfClairvoyance",    1 );
		TABLE.put( "StoneOfDetectMagic",     1 );
	}

	/** base points for an uncursed piece of gear, before the class weight */
	public static final int WAND = 3, RING = 3, ARTIFACT = 4, MISSILE = 1;

	private SeedScorer(){}

	public static int score( Item item, HeroClass cls ){
		Integer t = TABLE.get( item.getClass().getSimpleName() );
		if (t != null) return t * item.quantity();
		if (item.cursed) return 0;

		if (item instanceof Wand)          return weigh( WAND + item.level(), cls == HeroClass.MAGE );
		if (item instanceof Ring)          return weigh( RING + item.level(), cls == HeroClass.ROGUE );
		if (item instanceof Artifact)      return weigh( ARTIFACT, cls == HeroClass.ROGUE || cls == HeroClass.CLERIC );
		if (item instanceof MissileWeapon) return weigh( MISSILE * item.quantity(), cls == HeroClass.HUNTRESS );
		if (item instanceof MeleeWeapon){
			//a weapon only helps over the starting one: higher tier, upgraded or enchanted
			MeleeWeapon w = (MeleeWeapon) item;
			int p = atLeast0( w.tier - 2 ) + 2 * item.level() + (w.enchantment != null ? 2 : 0);
			return weigh( p, cls == HeroClass.WARRIOR || cls == HeroClass.DUELIST );
		}
		if (item instanceof Armor){
			Armor a = (Armor) item;
			int p = atLeast0( a.tier - 1 ) + 2 * item.level() + (a.glyph != null ? 2 : 0);
			return weigh( p, cls == HeroClass.WARRIOR );
		}
		return 0;
	}

	private static int atLeast0( int v ){
		return v < 0 ? 0 : v;
	}

	/** the class that uses this kind of item best gets half again */
	private static int weigh( int p, boolean favoured ){
		return favoured ? p + p / 2 : p;
	}

	public static int score( List<List<Item>> floors, HeroClass cls ){
		int s = 0;
		for (List<Item> f : floors){
			for (Item i : f) s += score( i, cls );
		}
		return s;
	}

	public static final class Result {
		public final long seed;
		public final Map<HeroClass, Integer> perClass = new EnumMap<>( HeroClass.class );
		public final int min, max;

		Result( long seed, List<List<Item>> floors ){
			this.seed = seed;
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
			for (HeroClass c : HeroClass.values()){
				int s = score( floors, c );
				perClass.put( c, s );
				if (s < lo) lo = s;
				if (s > hi) hi = s;
			}
			min = lo;
			max = hi;
		}

		public boolean inPool(){
			return min >= MIN && max <= CAP;
		}

		@Override
		public String toString(){
			return seed + " min " + min + " max " + max + " " + perClass + (inPool() ? " IN" : " out");
		}
	}

	public static Result of( long seed, List<List<Item>> floors ){
		return new Result( seed, floors );
	}

	/** scans and scores one seed */
	public static Result of( long seed ){
		return new Result( seed, SeedScanner.scan( seed, SeedScanner.DEPTH ) );
	}

	/** the candidates that pass, in the order given; the caller picks the candidates and keeps them secret */
	public static List<Result> pool( long[] candidates ){
		List<Result> out = new ArrayList<>();
		for (long s : candidates){
			Result r = of( s );
			if (r.inPool()) out.add( r );
		}
		return out;
	}
}
