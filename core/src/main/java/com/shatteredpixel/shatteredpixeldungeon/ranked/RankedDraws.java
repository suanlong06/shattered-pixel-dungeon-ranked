package com.shatteredpixel.shatteredpixeldungeon.ranked;

import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

/**
 * spd-ranked (T0.7): item rolls made during play that would draw from the ARTIFACT deck, which
 * levelgen also draws from. In Solo they go to Generator unchanged. In Ranked they pick an artifact
 * that is still in the deck, with the gameplay RNG, without taking it out of the deck, so later
 * floors get the same artifacts whatever happens in play (S1 findings 5 to 7).
 *
 * Callers: Thief loot ({@code Mob.createLoot}), Cursed Wand transmogrify, Ring of Wealth,
 * Scroll of Transmutation. Trade-off: in Ranked an artifact found in play can appear again later.
 */
public final class RankedDraws {

	private RankedDraws(){}

	/** {@link Generator#randomUsingDefaults(Generator.Category)} */
	public static Item usingDefaults( Generator.Category cat ){
		if (Features.isRanked() && cat == Generator.Category.ARTIFACT) return artifactOrRing();
		return Generator.randomUsingDefaults( cat );
	}

	/** {@link Generator#random(Generator.Category)} */
	public static Item random( Generator.Category cat ){
		if (Features.isRanked() && cat == Generator.Category.ARTIFACT) return artifactOrRing();
		return Generator.random( cat );
	}

	/** {@link Generator#randomArtifact()}: null when no artifact is left */
	public static Artifact artifact(){
		if (!Features.isRanked()) return Generator.randomArtifact();
		Generator.Category cat = Generator.Category.ARTIFACT;
		int i = Random.chances( cat.probs );
		if (i == -1) return null;
		return (Artifact) ((Item) Reflection.newInstance( cat.classes[i] )).random();
	}

	private static Item artifactOrRing(){
		Item a = artifact();
		//same fallback as Generator.random(ARTIFACT)
		return a != null ? a : Generator.randomUsingDefaults( Generator.Category.RING );
	}
}
