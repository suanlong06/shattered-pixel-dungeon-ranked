package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Group;
import com.watabou.utils.PointF;

/**
 * spd-ranked (C1): an outline around the map tile under the mouse.
 * Only drawn on tiles the player has seen (visited or mapped), so it shows nothing new; purely visual.
 */
public class TileIndicator extends Group {

	private static final int COLOR = 0x88FFFFFF;

	private final ColorBlock[] sides = new ColorBlock[4];

	public TileIndicator(){
		for (int i = 0; i < 4; i++){
			sides[i] = new ColorBlock( 1, 1, COLOR );
			add( sides[i] );
		}
	}

	/** the cell under this world point, or -1 if it is outside the map or not discovered */
	public static int cellAt( Level level, float worldX, float worldY ){
		if (level == null || worldX < 0 || worldY < 0) return -1;
		int x = (int)(worldX / DungeonTilemap.SIZE);
		int y = (int)(worldY / DungeonTilemap.SIZE);
		if (x >= level.width() || y >= level.height()) return -1;
		int cell = x + y * level.width();
		return level.visited[cell] || level.mapped[cell] ? cell : -1;
	}

	@Override
	public void update(){
		super.update();
		int cell = -1;
		if (Features.enabled( Feature.TILE_INDICATOR ) && camera() != null){
			PointF hover = PointerEvent.currentHoverPos();
			PointF p = camera().screenToCamera( (int)hover.x, (int)hover.y );
			cell = cellAt( Dungeon.level, p.x, p.y );
		}
		visible = cell != -1;
		if (!visible) return;

		PointF o = DungeonTilemap.tileToWorld( cell );
		float s = DungeonTilemap.SIZE;
		place( sides[0], o.x, o.y, s, 1 );
		place( sides[1], o.x, o.y + s - 1, s, 1 );
		place( sides[2], o.x, o.y, 1, s );
		place( sides[3], o.x + s - 1, o.y, 1, s );
	}

	private static void place( ColorBlock b, float x, float y, float w, float h ){
		b.x = x;
		b.y = y;
		b.size( w, h );
	}
}
