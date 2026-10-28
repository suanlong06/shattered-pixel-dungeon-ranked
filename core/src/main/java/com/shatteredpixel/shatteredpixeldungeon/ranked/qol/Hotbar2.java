package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.QuickSlot;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.watabou.noosa.ui.Component;

/**
 * spd-ranked (C1): quickslots 7 to 12, in a second row above the toolbar.
 * The slots are part of {@link QuickSlot} (SIZE = 12), so every game rule that keeps an item's
 * quickslot (placeholders, transmutation, bags...) covers them too. Quickslots are UI only:
 * using one records the same ITEM command as the first row, and the ranked state digest
 * does not include them.
 * The Toolbar keeps the buttons; this class only decides whether they show and where.
 */
public final class Hotbar2 {

	/** index of the first slot of the second row */
	public static final int FIRST = 6;
	/** height of the row, the same as a toolbar quickslot */
	public static final int ROW = 24;

	private Hotbar2(){}

	public static boolean shown(){
		return QuickSlot.SIZE > FIRST && Dungeon.hero != null && Features.enabled( Feature.HOTBAR_2 );
	}

	/** how far the toolbar's top moves up for the second row */
	public static float extraHeight(){
		return shown() ? ROW : 0;
	}

	/**
	 * x of slot i (FIRST or more) in a row of slots of this width: slot 7 sits on the side of
	 * slot 1, at the right edge, or at the left edge when the toolbar is flipped.
	 */
	public static float x( float left, float right, int i, float slotWidth, boolean flipped ){
		int k = i - FIRST;
		return flipped ? left + k * slotWidth : right - (k + 1) * slotWidth;
	}

	/** Toolbar.layout(): places the second row, or moves it off screen when hidden */
	public static void place( Component toolbar, Component[] tools ){
		boolean on = shown();
		boolean flipped = SPDSettings.flipToolbar();
		for (int i = FIRST; i < tools.length; i++){
			Component t = tools[i];
			if (on){
				t.setPos( x( toolbar.left(), toolbar.right(), i, t.width(), flipped ), toolbar.top() );
				PixelScene.align( t );
			} else {
				t.setPos( t.left(), PixelScene.uiCamera.height );
			}
		}
	}
}
