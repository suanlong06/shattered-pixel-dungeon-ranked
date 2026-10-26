package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.input.GameAction;
import com.watabou.noosa.Camera;
import com.watabou.utils.PointF;

/**
 * spd-ranked (C1): keys that pan the camera or bring it back to the hero.
 * Only moves the camera; never touches the game state.
 */
public final class CameraKeys {

	/** tiles moved per key press */
	public static final int PAN_TILES = 3;

	private CameraKeys(){}

	/** hook in CellSelector's key listener (key release); true if the key was a camera key */
	public static boolean handle( GameAction action, Camera camera ){
		if (!Features.enabled( Feature.CAMERA_KEYS )) return false;

		if (action == SPDAction.CAMERA_CENTER){
			if (Dungeon.hero != null && Dungeon.hero.sprite != null){
				camera.panFollow( Dungeon.hero.sprite, 5f );
			}
			return true;
		}

		PointF d = offset( action );
		if (d == null) return false;
		PointF c = camera.center().offset( camera.scroll );
		camera.panTo( c.offset( d ), 5f );
		return true;
	}

	/** world offset for a pan action, or null if it is not one */
	public static PointF offset( GameAction action ){
		float step = PAN_TILES * DungeonTilemap.SIZE;
		if (action == SPDAction.CAMERA_UP)    return new PointF( 0, -step );
		if (action == SPDAction.CAMERA_DOWN)  return new PointF( 0, step );
		if (action == SPDAction.CAMERA_LEFT)  return new PointF( -step, 0 );
		if (action == SPDAction.CAMERA_RIGHT) return new PointF( step, 0 );
		return null;
	}
}
