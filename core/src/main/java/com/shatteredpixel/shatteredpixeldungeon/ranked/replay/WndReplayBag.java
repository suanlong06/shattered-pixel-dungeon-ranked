package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.IconTitle;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTitledMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A5): the watched hero's inventory, read-only: equipped items first, then the backpack
 * with what its bags hold. Tapping an item shows its description. Not the game's WndBag, whose
 * buttons use items and set quickslots, and whose remembered bag tab gameplay windows open on.
 */
class WndReplayBag extends Window {

	static final int COLS = 6;
	static final int SLOT = 18;
	static final int GAP = 1;
	static final int WIDTH = COLS * (SLOT + GAP) - GAP;

	WndReplayBag( ReplayViewer viewer ){
		super();
		RenderedTextBlock title = PixelScene.renderTextBlock( Messages.get( ReplayViewer.class, "bag_title" ), 9 );
		title.hardlight( TITLE_COLOR );
		title.maxWidth( WIDTH );
		title.setPos( (WIDTH - title.width()) / 2f, 0 );
		add( title );

		RenderedTextBlock desc = PixelScene.renderTextBlock( Messages.get( ReplayViewer.class, "bag_desc", Dungeon.depth, Dungeon.gold ), 6 );
		desc.maxWidth( WIDTH );
		desc.setPos( 0, title.bottom() + 2 );
		add( desc );

		float top = desc.bottom() + 3;
		int n = 0;
		for (final Item item : items()){
			InventorySlot slot = new InventorySlot( item ){
				@Override
				protected void onClick(){
					viewer.show( new WndTitledMessage( new IconTitle( item ), item.info() ) );
				}
			};
			slot.setRect( (n % COLS) * (SLOT + GAP), top + (n / COLS) * (SLOT + GAP), SLOT, SLOT );
			add( slot );
			n++;
		}
		int rows = Math.max( 1, (n + COLS - 1) / COLS );
		resize( WIDTH, (int) (top + rows * (SLOT + GAP) - GAP) );
	}

	/** what the hero carries, as Belongings iterates it (equipped, then the backpack and its bags) */
	static List<Item> items(){
		List<Item> out = new ArrayList<>();
		if (Dungeon.hero == null) return out;
		for (Item i : Dungeon.hero.belongings){
			if (i.name() != null) out.add( i );
		}
		return out;
	}
}
