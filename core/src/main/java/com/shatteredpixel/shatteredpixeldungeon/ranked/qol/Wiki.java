package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;

import java.util.Locale;

/**
 * spd-ranked (C4): the journal's catalog and bestiary as a wiki that grows with the player.
 *
 * A page only opens once its entry is seen (SPD's own {@code Catalog.isSeen} / {@code Bestiary.isSeen});
 * an open page gets a tip written for this fork after SPD's description, from the ranked bundle
 * ({@code ranked.qol.wiki.tip.<class>}). Entries without a tip show SPD's text only.
 *
 * In a Ranked match the profile would give the two players different pages, so the journal shows
 * a standard catalog instead: every page open, the same for both. Only the journal window reads this;
 * no gameplay code does, so it cannot change the simulation.
 */
public final class Wiki {

	private static final String TIP_KEY = "ranked.qol.wiki.tip.";

	private Wiki(){}

	/** hook in WndJournal: is this catalog/bestiary entry open? {@code profile} is SPD's answer */
	public static boolean seen( boolean profile ){
		return standardCatalog() || profile;
	}

	/** true while a Ranked match is on screen with the wiki on */
	public static boolean standardCatalog(){
		return Features.isRanked() && Features.enabled( Feature.WIKI )
				&& ShatteredPixelDungeon.scene() instanceof GameScene;
	}

	/** hook in WndJournal: the entry's text, with the wiki tip after it when the page is open */
	public static String withTip( String desc, Class<?> entry, boolean seen ){
		if (!seen || !Features.enabled( Feature.WIKI )) return desc;
		String tip = tip( entry );
		if (tip == null) return desc;
		return desc + "\n\n_" + Messages.get( Wiki.class, "tip_label" ) + "_ " + tip;
	}

	/** the tip for exactly this class (not its parents: a Fetid Rat is not a Rat), or null */
	public static String tip( Class<?> entry ){
		if (entry == null) return null;
		String t = Messages.get( (Class) null, key( entry ) );
		return Messages.NO_TEXT_FOUND.equals( t ) ? null : t;
	}

	public static String key( Class<?> entry ){
		return TIP_KEY + entry.getSimpleName().toLowerCase( Locale.ENGLISH );
	}
}
