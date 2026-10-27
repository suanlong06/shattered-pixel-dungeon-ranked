package com.shatteredpixel.shatteredpixeldungeon.ranked;

import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ExoticCrystals;

/**
 * spd-ranked (T0.7): in Ranked the run must not depend on the player's profile or on what
 * the player is carrying, so both players get the same floors from the same seed
 * (DECISIONS F17, F19; docs/spikes/S1-levelgen.md).
 *
 * Every method is called from a one-line hook in an SPD file and returns SPD's own answer in Solo.
 * <ul>
 *   <li>all 6 classes unlocked ({@code HeroClass.isUnlocked})</li>
 *   <li>no Bones: nothing read from or written to the local remains file ({@code Bones})</li>
 *   <li>a "veteran" profile: intro off, every journal page found and read, so no guide or lore
 *       pages are placed and levelgen never reads the profile ({@code SPDSettings.intro},
 *       {@code Document.isPageFound/isPageRead})</li>
 *   <li>windows the game opens by a device counter, which the server's replay must open too: the
 *       support prompt on picking up Goo's key never shows ({@code SPDSettings.supportNagged}), the
 *       vault injury warning shows once every run ({@code SPDSettings.vaultInjureWarns})</li>
 *   <li>Exotic Crystals is out of the trinket deck and has no effect: it changes the potions and
 *       scrolls of every later floor ({@code ExoticCrystals}, {@link #applyGeneratorRules})</li>
 * </ul>
 * Catalog needs nothing: in v4.0.2 Trinity (Cleric) reads the per-run
 * {@code Statistics.itemTypesDiscovered}, and no levelgen or gameplay code reads Catalog.
 * Draws from the ARTIFACT deck during play are in {@link RankedDraws}.
 */
public final class CleanProfile {

	private CleanProfile(){}

	public static boolean classUnlocked( boolean solo ){
		return Features.isRanked() || solo;
	}

	public static boolean bonesAllowed(){
		return !Features.isRanked();
	}

	public static boolean intro( boolean solo ){
		return !Features.isRanked() && solo;
	}

	public static boolean pageKnown( boolean solo ){
		return Features.isRanked() || solo;
	}

	public static boolean supportNagged( boolean solo ){
		return Features.isRanked() || solo;
	}

	public static int vaultInjureWarns( int solo ){
		return Features.isRanked() ? 0 : solo;
	}

	public static float exoticChance( float solo ){
		return Features.isRanked() ? 0f : solo;
	}

	/** call after the Generator decks are reset or restored */
	public static void applyGeneratorRules(){
		Generator.Category cat = Generator.Category.TRINKET;
		for (int i = 0; i < cat.classes.length; i++){
			if (cat.classes[i] == ExoticCrystals.class){
				cat.defaultProbs[i] = Features.isRanked() ? 0 : 1;
				if (Features.isRanked()) cat.probs[i] = 0;
			}
		}
	}
}
