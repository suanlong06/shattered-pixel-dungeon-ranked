package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemSlot;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.ui.Component;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * spd-ranked (C2): a small 'n' on items the player wrote a custom note for,
 * in item slots (inventory, shop windows) and on floor heaps.
 * Uses the same lookup as Item.info(), so it only shows what the player wrote; purely visual.
 */
public class ItemNoteMarkers extends Group {

	public static final int COLOR = 0x66CCFF;

	/** true if this item, or its type, has a custom note */
	public static boolean hasNote( Item item ){
		if (item == null) return false;
		return Notes.findCustomRecord( item.customNoteID ) != null
				|| Notes.findCustomRecord( item.getClass() ) != null;
	}

	private static boolean show( Item item ){
		return Features.enabled( Feature.ITEM_NOTE_MARKERS ) && hasNote( item );
	}

	//floor heaps: one marker per visible heap, in the top-left corner

	@Override
	public void update(){
		super.update();
		for (Gizmo g : members) if (g != null) g.visible = false;
		if (Dungeon.level == null || !Features.enabled( Feature.ITEM_NOTE_MARKERS )) return;

		int used = 0;
		for (Heap heap : Dungeon.level.heaps.valueList()){
			if (heap.sprite == null || !heap.sprite.visible || !heap.seen) continue;
			//closed containers hide their item
			if (heap.type != Heap.Type.HEAP && heap.type != Heap.Type.FOR_SALE) continue;
			if (!hasNote( heap.peek() )) continue;

			BitmapText txt;
			if (used < members.size()){
				txt = (BitmapText) members.get( used );
			} else {
				txt = marker();
				add( txt );
			}
			used++;
			txt.x = heap.sprite.x;
			txt.y = heap.sprite.y;
			txt.visible = true;
		}
	}

	private static BitmapText marker(){
		BitmapText t = new BitmapText( "n", PixelScene.pixelFont );
		t.hardlight( COLOR );
		return t;
	}

	//item slots: same pattern as RunValueStar, placed bottom-left after the star

	private static final Map<ItemSlot, WeakReference<SlotMarker>> SLOTS = new WeakHashMap<>();

	/** hook in ItemSlot.item(): adds, shows or hides the marker of this slot */
	public static void update( ItemSlot slot, Item item ){
		WeakReference<SlotMarker> ref = SLOTS.get( slot );
		SlotMarker m = ref == null ? null : ref.get();
		if (!show( item )){
			if (m != null) m.visible = false;
			return;
		}
		if (m == null){
			m = (SlotMarker) slot.add( new SlotMarker() );
			SLOTS.put( slot, new WeakReference<>( m ) );
		}
		m.visible = true;
	}

	private static class SlotMarker extends BitmapText {

		SlotMarker(){
			super( "n", PixelScene.pixelFont );
			hardlight( COLOR );
		}

		@Override
		public void update(){
			super.update();
			if (parent instanceof Component){
				Component c = (Component) parent;
				x = c.left() + 6;
				y = c.bottom() - baseLine() - 2;
				PixelScene.align( this );
			}
		}
	}
}
