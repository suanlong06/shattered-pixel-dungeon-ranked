package com.shatteredpixel.shatteredpixeldungeon.ranked;

/**
 * spd-ranked (T0.2): an optional helper feature (ROADMAP group B).
 * In Solo the player turns it on or off; in Ranked it takes {@link #ranked}, the same for both players.
 * Features that only show information must never change RNG or simulation results.
 */
public enum Feature {
	KNOWN_ITEMS          ( true, false ), // B3
	FOOD_FORECAST        ( true, false ), // B5
	SMART_INVENTORY      ( true, false ), // B4
	DEATH_ANALYSIS       ( true, false ), // B1
	CONTEXTUAL_KNOWLEDGE ( true, false ), // B2
	GHOST_RACE           ( true, false ), // A5, Solo: race your best run on the same seed
	//display/camera only, safe in Ranked (docs/C-research.md)
	ITEM_NOTE_MARKERS    ( true, true ),  // C2
	CAMERA_KEYS          ( true, true ),  // C1
	TILE_INDICATOR       ( true, true ),  // C1
	HOTBAR_2             ( true, true ),  // C1, slots 7-12 in a second row; quickslots are input only
	HP_TICKS             ( true, true );  // C6, marks on enemy HP bars every few HP

	/** value in Solo until the player changes it */
	public final boolean soloDefault;
	/** fixed value in Ranked; the ranked values are not decided yet, so everything is off */
	public final boolean ranked;

	Feature( boolean soloDefault, boolean ranked ){
		this.soloDefault = soloDefault;
		this.ranked = ranked;
	}

	String settingsKey(){
		return "spdr_feature_" + name().toLowerCase();
	}
}
