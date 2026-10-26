package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * spd-ranked (B2): a one-line note the first time each kind of enemy comes into view in a run.
 * Reuses v4.0.2's Bestiary ("discovered" = the player has defeated or befriended one before),
 * so it only repeats what the journal already shows: an undiscovered enemy just gets
 * "not in your bestiary yet".
 * Read only: no RNG, no game state; the noted set is not saved (a loaded run notes again).
 */
public final class ContextualKnowledge {

	private static final Set<Class<?>> noted = new LinkedHashSet<>();

	private ContextualKnowledge(){}

	/** new or loaded run */
	public static void reset(){
		noted.clear();
	}

	/** the note for this mob, or null if its kind was already noted this run */
	public static String note( Mob m ){
		Class<?> c = m.getClass();
		if (!noted.add( c )) return null;
		String name = c.getSimpleName();
		if (!Bestiary.isSeen( c )) return Messages.get( ContextualKnowledge.class, "unknown", name );
		return Messages.get( ContextualKnowledge.class, "known", name, m.HT, Bestiary.encounterCount( c ) );
	}

	/** hook in Hero.checkVisibleMobs, for each enemy in view */
	public static void seen( Mob m ){
		if (!Features.enabled( Feature.CONTEXTUAL_KNOWLEDGE )) return;
		String n = note( m );
		if (n != null) GLog.i( n );
	}
}
