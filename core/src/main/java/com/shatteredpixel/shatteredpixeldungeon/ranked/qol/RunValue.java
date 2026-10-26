package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * spd-ranked (B4): a fixed rule table (L11 default) saying which carried items are worth keeping.
 * The ★ in the inventory is only a hint: nothing is sorted, dropped or used for the player.
 * v4.0.2 already sorts items into bags by type, so B4 adds the hint rather than a second sorter.
 *
 * Rules only look at what the player knows: an unknown potion or scroll scores 0, and gear
 * with an unknown level or curse is not judged.
 */
public final class RunValue {

	/** score at or above which an item gets the ★ */
	public static final int STAR = 2;

	/** item class simple name -> score (0..3); edit here to tune, without touching the rules below */
	public static final Map<String, Integer> TABLE = new LinkedHashMap<>();
	static {
		TABLE.put( "ScrollOfUpgrade",          3 );
		TABLE.put( "PotionOfStrength",         3 );
		TABLE.put( "ElixirOfMight",            3 );
		TABLE.put( "PotionOfHealing",          2 );
		TABLE.put( "ScrollOfRemoveCurse",      2 );
		TABLE.put( "ScrollOfTeleportation",    2 );
		TABLE.put( "StoneOfEnchantment",       2 );
		TABLE.put( "Ankh",                     3 );
		TABLE.put( "PotionOfExperience",       2 );
		TABLE.put( "ScrollOfMagicMapping",     1 );
		TABLE.put( "PotionOfInvisibility",     1 );
	}

	private RunValue(){}

	public static int score( Item item ){
		if (item == null) return 0;
		Integer t = TABLE.get( item.getClass().getSimpleName() );
		if (t != null) return item.isIdentified() ? t : 0;

		if (item instanceof Weapon || item instanceof Armor){
			if (!item.levelKnown || !item.cursedKnown || item.cursed) return 0;
			int lvl = item.level();
			return lvl >= 3 ? 3 : lvl >= 1 ? 2 : 0;
		}
		return 0;
	}

	public static boolean starred( Item item ){
		return score( item ) >= STAR;
	}
}
