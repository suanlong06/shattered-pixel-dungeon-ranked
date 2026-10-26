package com.shatteredpixel.shatteredpixeldungeon.ranked;

import com.watabou.utils.Bundle;
import com.watabou.utils.GameSettings;

import java.util.EnumSet;

/**
 * spd-ranked (T0.2): the game mode of the current run and its feature flags.
 *
 * The mode is set before {@code Dungeon.init()} (Solo: hooked in {@code Dungeon.initSeed()};
 * Ranked: by the match code) and saved with the run, so a loaded run keeps its mode.
 * A Ranked run also saves its feature set, so replays and both players see the same flags
 * even if the defaults change in a later version.
 */
public final class Features {

	private static final String MODE     = "spdr_mode";
	private static final String FEATURES = "spdr_features";

	private static GameMode mode = GameMode.SOLO;
	private static EnumSet<Feature> rankedSet = defaultRankedSet();

	private Features(){}

	public static GameMode mode(){
		return mode;
	}

	public static boolean isRanked(){
		return mode == GameMode.RANKED;
	}

	/** starts a new run in this mode; Ranked takes the current default ranked feature set */
	public static void setMode( GameMode m ){
		mode = m;
		rankedSet = defaultRankedSet();
	}

	public static boolean enabled( Feature f ){
		if (isRanked()) return rankedSet.contains( f );
		return GameSettings.getBoolean( f.settingsKey(), f.soloDefault );
	}

	/** the player's Solo choice; has no effect in Ranked */
	public static void setSolo( Feature f, boolean on ){
		GameSettings.put( f.settingsKey(), on );
	}

	public static void store( Bundle bundle ){
		bundle.put( MODE, mode.name() );
		if (isRanked()){
			String[] names = new String[rankedSet.size()];
			int i = 0;
			for (Feature f : rankedSet) names[i++] = f.name();
			bundle.put( FEATURES, names );
		}
	}

	/** saves without a mode are Solo */
	public static void restore( Bundle bundle ){
		mode = bundle.contains( MODE ) ? GameMode.valueOf( bundle.getString( MODE ) ) : GameMode.SOLO;
		rankedSet = defaultRankedSet();
		if (isRanked() && bundle.contains( FEATURES )){
			rankedSet.clear();
			for (String n : bundle.getStringArray( FEATURES )){
				try {
					rankedSet.add( Feature.valueOf( n ) );
				} catch (IllegalArgumentException e){
					//feature removed in this version: ignore
				}
			}
		}
	}

	private static EnumSet<Feature> defaultRankedSet(){
		EnumSet<Feature> s = EnumSet.noneOf( Feature.class );
		for (Feature f : Feature.values()) if (f.ranked) s.add( f );
		return s;
	}
}
