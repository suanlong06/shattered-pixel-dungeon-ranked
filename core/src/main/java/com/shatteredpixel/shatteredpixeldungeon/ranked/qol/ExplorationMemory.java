package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.utils.Bundle;
import com.watabou.utils.PointF;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * spd-ranked (C5): what the player remembers of each floor, written automatically.
 *
 * Per floor of the main dungeon: doors that lead somewhere not yet discovered, locked doors
 * (and whether the key is in the journal), and items and containers seen but left behind.
 * Shown as one line under the floor in the journal's notes tab, and as a '?' on the map over each
 * door to unexplored space.
 *
 * Only discovered cells ({@code visited || mapped}) and heaps the player has seen ({@code Heap.seen})
 * are read, so nothing is revealed. A secret door reads as wall until found, as on screen.
 * Read only: no RNG, nothing in the level changes. The notes are saved with the run (outside the level
 * bundles, so level digests are unchanged).
 */
public final class ExplorationMemory {

	private static final String BUNDLE = "spdr_memory";

	/** what is noted for one floor */
	public static final class FloorNotes {
		public int openDoors;    //doors with an undiscovered cell next to them
		public int lockedDoors;
		public boolean ironKey;  //an iron key of this floor is in the journal
		public int crystalDoors;
		public boolean crystalKey;
		public int items;        //seen heaps lying on the floor (not for sale)
		public int containers;   //seen chests, tombs, skeletons and remains

		boolean empty(){
			return openDoors == 0 && lockedDoors == 0 && crystalDoors == 0 && items == 0 && containers == 0;
		}

		int[] toArray(){
			return new int[]{ openDoors, lockedDoors, ironKey ? 1 : 0, crystalDoors, crystalKey ? 1 : 0, items, containers };
		}

		static FloorNotes fromArray( int[] a ){
			FloorNotes n = new FloorNotes();
			if (a.length < 7) return n;
			n.openDoors = a[0];
			n.lockedDoors = a[1];
			n.ironKey = a[2] != 0;
			n.crystalDoors = a[3];
			n.crystalKey = a[4] != 0;
			n.items = a[5];
			n.containers = a[6];
			return n;
		}
	}

	private static final TreeMap<Integer, FloorNotes> floors = new TreeMap<>();

	private ExplorationMemory(){}

	public static boolean discovered( Level level, int cell ){
		return level.visited[cell] || level.mapped[cell];
	}

	/** a discovered door with an undiscovered cell beside it */
	public static boolean opensOnUnknown( Level level, int cell ){
		if (!discovered( level, cell )) return false;
		int t = level.map[cell];
		if (t != Terrain.DOOR && t != Terrain.OPEN_DOOR) return false;
		int w = level.width();
		int x = cell % w;
		if (x > 0 && !discovered( level, cell - 1 )) return true;
		if (x < w - 1 && !discovered( level, cell + 1 )) return true;
		if (cell - w >= 0 && !discovered( level, cell - w )) return true;
		return cell + w < level.length() && !discovered( level, cell + w );
	}

	/** the notes for this level, from what the player has discovered */
	public static FloorNotes scan( Level level, int depth ){
		FloorNotes n = new FloorNotes();
		for (int cell = 0; cell < level.length(); cell++){
			if (!discovered( level, cell )) continue;
			int t = level.map[cell];
			if (t == Terrain.LOCKED_DOOR) n.lockedDoors++;
			else if (t == Terrain.CRYSTAL_DOOR) n.crystalDoors++;
			else if (opensOnUnknown( level, cell )) n.openDoors++;
		}
		for (Heap heap : level.heaps.valueList()){
			if (!heap.seen || heap.isEmpty()) continue;
			if (heap.type == Heap.Type.HEAP) n.items++;
			else if (heap.type != Heap.Type.FOR_SALE) n.containers++;
		}
		n.ironKey = n.lockedDoors > 0 && Notes.keyCount( new IronKey( depth ) ) > 0;
		n.crystalKey = n.crystalDoors > 0 && Notes.keyCount( new CrystalKey( depth ) ) > 0;
		return n;
	}

	/** notes the current floor; side floors (quest branches) share depths with the main floors and are skipped */
	public static void update(){
		if (Dungeon.level == null || Dungeon.branch != 0) return;
		floors.put( Dungeon.depth, scan( Dungeon.level, Dungeon.depth ) );
	}

	public static FloorNotes notes( int depth ){
		return floors.get( depth );
	}

	/** hook in WndJournal's notes tab: the line under a floor's header, or null */
	public static String line( int depth ){
		if (!Features.enabled( Feature.EXPLORATION_MEMORY )) return null;
		if (depth == Dungeon.depth) update();
		FloorNotes n = floors.get( depth );
		return n == null ? null : describe( n );
	}

	public static String describe( FloorNotes n ){
		if (n.empty()) return null;
		ArrayList<String> parts = new ArrayList<>();
		if (n.openDoors > 0) parts.add( Messages.get( ExplorationMemory.class, "open_doors", n.openDoors ) );
		if (n.lockedDoors > 0) parts.add( Messages.get( ExplorationMemory.class,
				n.ironKey ? "locked_key" : "locked", n.lockedDoors ) );
		if (n.crystalDoors > 0) parts.add( Messages.get( ExplorationMemory.class,
				n.crystalKey ? "crystal_key" : "crystal", n.crystalDoors ) );
		if (n.items > 0) parts.add( Messages.get( ExplorationMemory.class, "items", n.items ) );
		if (n.containers > 0) parts.add( Messages.get( ExplorationMemory.class, "containers", n.containers ) );
		StringBuilder sb = new StringBuilder();
		for (String p : parts){
			if (sb.length() > 0) sb.append( ", " );
			sb.append( p );
		}
		return sb.toString();
	}

	public static void reset(){
		floors.clear();
	}

	public static void store( Bundle bundle ){
		if (floors.isEmpty()) return;
		Bundle b = new Bundle();
		for (Map.Entry<Integer, FloorNotes> e : floors.entrySet()){
			b.put( Integer.toString( e.getKey() ), e.getValue().toArray() );
		}
		bundle.put( BUNDLE, b );
	}

	public static void restore( Bundle bundle ){
		floors.clear();
		if (!bundle.contains( BUNDLE )) return;
		Bundle b = bundle.getBundle( BUNDLE );
		for (String k : b.getKeys()){
			try {
				floors.put( Integer.parseInt( k ), FloorNotes.fromArray( b.getIntArray( k ) ) );
			} catch (NumberFormatException e){
				//not a floor: ignore
			}
		}
	}

	/** the '?' over doors to unexplored space; rescans when game time passes or the floor changes */
	public static class Overlay extends Group {

		public static final int COLOR = 0xFFDD66;

		private float lastTime = -1;
		private Level lastLevel;

		@Override
		public void update(){
			super.update();
			Level level = Dungeon.level;
			if (level == null || !Features.enabled( Feature.EXPLORATION_MEMORY )){
				for (Gizmo g : members) if (g != null) g.visible = false;
				lastLevel = null;
				return;
			}
			if (level == lastLevel && Actor.now() == lastTime) return;
			lastLevel = level;
			lastTime = Actor.now();
			ExplorationMemory.update();

			int used = 0;
			for (int cell = 0; cell < level.length(); cell++){
				if (!opensOnUnknown( level, cell )) continue;
				BitmapText txt;
				if (used < members.size()){
					txt = (BitmapText) members.get( used );
				} else {
					txt = new BitmapText( "?", PixelScene.pixelFont );
					txt.hardlight( COLOR );
					txt.measure();
					add( txt );
				}
				used++;
				PointF o = DungeonTilemap.tileToWorld( cell );
				txt.x = o.x + (DungeonTilemap.SIZE - txt.width()) / 2f;
				txt.y = o.y + (DungeonTilemap.SIZE - txt.baseLine()) / 2f;
				txt.visible = true;
			}
			for (int i = used; i < members.size(); i++){
				Gizmo g = members.get( i );
				if (g != null) g.visible = false;
			}
		}
	}
}
