package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;

/**
 * spd-ranked (B3): which known-type icon to show on an item lying on the floor or in a shop.
 * v4.0.2 already shows this icon in the inventory and shop windows ({@code ItemSlot});
 * this applies the same rule to heaps, so it never reveals anything the player does not know.
 */
public final class KnownItems {

	private KnownItems(){}

	/** same condition as ItemSlot.updateText(); -1 = no icon */
	public static int icon( Item item ){
		if (item == null || item.icon == -1) return -1;
		if (item.isIdentified() || (item instanceof Ring && ((Ring) item).isKnown())) return item.icon;
		return -1;
	}

	/** only open heaps and shop items show their top item; chests, tombs and remains hide it */
	public static int icon( Heap heap ){
		if (heap == null || heap.isEmpty()) return -1;
		if (heap.type != Heap.Type.HEAP && heap.type != Heap.Type.FOR_SALE) return -1;
		return icon( heap.peek() );
	}
}
