package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.CameraKeys;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.ItemNoteMarkers;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.TileIndicator;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.utils.PointF;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C1 and C2 (docs/C-research.md): logic only, run headless. */
class QolCTest {

	@BeforeEach
	void run(){
		Headless.newRun(12345L, HeroClass.WARRIOR);
		Headless.generate(1);
	}

	@AfterEach
	void backToSolo(){
		Features.setMode(GameMode.SOLO);
	}

	@Test
	void displayOnlyFeaturesAreOnInRanked(){
		Features.setMode(GameMode.RANKED);
		assertTrue(Features.enabled(Feature.ITEM_NOTE_MARKERS));
		assertTrue(Features.enabled(Feature.CAMERA_KEYS));
		assertTrue(Features.enabled(Feature.TILE_INDICATOR));
	}

	// C2

	@Test
	void noteMarkerFollowsTypeAndItemNotes(){
		PotionOfHealing p = new PotionOfHealing();
		ScrollOfUpgrade s = new ScrollOfUpgrade();
		assertFalse(ItemNoteMarkers.hasNote(p));
		assertFalse(ItemNoteMarkers.hasNote(null));

		Notes.CustomRecord type = new Notes.CustomRecord(PotionOfHealing.class, "t", "b");
		Notes.add(type);
		assertTrue(ItemNoteMarkers.hasNote(p));
		assertFalse(ItemNoteMarkers.hasNote(s));

		Notes.CustomRecord one = new Notes.CustomRecord(s, "t", "b");
		Notes.add(one);
		s.customNoteID = one.ID();
		assertTrue(ItemNoteMarkers.hasNote(s));
		assertFalse(ItemNoteMarkers.hasNote(new ScrollOfUpgrade()));

		Notes.remove(type);
		Notes.remove(one);
		assertFalse(ItemNoteMarkers.hasNote(p));
	}

	// C1

	@Test
	void tileIndicatorOnlyOnDiscoveredCells(){
		Level l = Dungeon.level;
		int cell = l.entrance();
		float wx = (cell % l.width()) * DungeonTilemap.SIZE + 1;
		float wy = (cell / l.width()) * DungeonTilemap.SIZE + 1;

		l.visited[cell] = false;
		l.mapped[cell] = false;
		assertEquals(-1, TileIndicator.cellAt(l, wx, wy));
		l.mapped[cell] = true;
		assertEquals(cell, TileIndicator.cellAt(l, wx, wy));

		assertEquals(-1, TileIndicator.cellAt(l, -1, wy));
		assertEquals(-1, TileIndicator.cellAt(l, l.width() * DungeonTilemap.SIZE, wy));
		assertEquals(-1, TileIndicator.cellAt(null, wx, wy));
	}

	@Test
	void cameraPanOffsets(){
		float step = CameraKeys.PAN_TILES * DungeonTilemap.SIZE;
		PointF up = CameraKeys.offset(SPDAction.CAMERA_UP);
		assertEquals(0, up.x);
		assertEquals(-step, up.y);
		assertEquals(step, CameraKeys.offset(SPDAction.CAMERA_RIGHT).x);
		assertNull(CameraKeys.offset(SPDAction.ZOOM_IN));
	}
}
