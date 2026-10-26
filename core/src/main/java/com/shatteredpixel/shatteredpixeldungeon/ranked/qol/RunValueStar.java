package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemSlot;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.ui.Component;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * spd-ranked (B4): the ★ hint in the bottom-left corner of an item slot.
 * The pixel font has no star glyph, so it is drawn as a yellow '*'.
 */
public class RunValueStar extends BitmapText {

	private static final Map<ItemSlot, WeakReference<RunValueStar>> STARS = new WeakHashMap<>();

	private RunValueStar(){
		super( "*", PixelScene.pixelFont );
		hardlight( ItemSlot.MASTERED );
	}

	/** hook in ItemSlot.updateText(): adds, shows or hides the star of this slot */
	public static void update( ItemSlot slot, Item item ){
		//weak both ways: the slot owns its star, the map must not keep either alive
		WeakReference<RunValueStar> ref = STARS.get( slot );
		RunValueStar star = ref == null ? null : ref.get();
		boolean show = item != null && Features.enabled( Feature.SMART_INVENTORY ) && RunValue.starred( item );
		if (!show){
			if (star != null) star.visible = false;
			return;
		}
		if (star == null){
			star = (RunValueStar) slot.add( new RunValueStar() );
			STARS.put( slot, new WeakReference<>( star ) );
		}
		star.visible = true;
	}

	@Override
	public void update(){
		super.update();
		if (parent instanceof Component){
			Component c = (Component) parent;
			x = c.left() + 1;
			y = c.bottom() - baseLine() - 2;
			PixelScene.align( this );
		}
	}
}
