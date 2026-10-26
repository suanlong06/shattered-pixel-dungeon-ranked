package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;

/**
 * spd-ranked (B3): draws the known-type icon at the corner of each visible heap.
 * Added to GameScene right above the heap sprites; purely visual.
 */
public class KnownItemMarkers extends Group {

	@Override
	public void update(){
		super.update();
		for (Gizmo g : members) if (g != null) g.visible = false;
		if (Dungeon.level == null || !Features.enabled( Feature.KNOWN_ITEMS )) return;

		int used = 0;
		for (Heap heap : Dungeon.level.heaps.valueList()){
			if (heap.sprite == null || !heap.sprite.visible || !heap.seen) continue;
			int icon = KnownItems.icon( heap );
			if (icon == -1) continue;

			Image img;
			if (used < members.size()){
				img = (Image) members.get( used );
			} else {
				img = new Image( Assets.Sprites.ITEM_ICONS );
				add( img );
			}
			used++;
			img.frame( ItemSpriteSheet.Icons.film.get( icon ) );
			img.x = heap.sprite.x + heap.sprite.width() - img.width();
			img.y = heap.sprite.y;
			img.visible = true;
		}
	}
}
