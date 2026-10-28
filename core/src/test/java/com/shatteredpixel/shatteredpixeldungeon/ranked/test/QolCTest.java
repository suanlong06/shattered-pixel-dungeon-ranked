package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.QuickSlot;
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
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.Hotbar2;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.HpTicks;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.ItemNoteMarkers;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.TileIndicator;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.utils.Bundle;
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
		assertTrue(Features.enabled(Feature.HOTBAR_2));
		assertTrue(Features.enabled(Feature.HP_TICKS));
	}

	@Test
	void notesAndQuickslotsAreNotInTheRankedDigest(){
		Features.setMode(GameMode.RANKED);
		String before = TurnRunner.stateDigest();

		ScrollOfUpgrade s = new ScrollOfUpgrade();
		s.collect();
		String held = TurnRunner.stateDigest();

		Notes.CustomRecord type = new Notes.CustomRecord(ScrollOfUpgrade.class, "t", "b");
		Notes.add(type);
		Notes.CustomRecord one = new Notes.CustomRecord(s, "t", "b");
		Notes.add(one);
		s.customNoteID = one.ID();
		Dungeon.quickslot.setSlot(Hotbar2.FIRST + 3, s);
		assertEquals(held, TurnRunner.stateDigest());

		Notes.remove(type);
		Notes.remove(one);
		Dungeon.quickslot.clearItem(s);
		s.detachAll(Dungeon.hero.belongings.backpack);
		assertEquals(before, TurnRunner.stateDigest());
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
	void secondHotbarSlotsAreRealQuickslots(){
		assertEquals(12, QuickSlot.SIZE);
		ScrollOfUpgrade s = new ScrollOfUpgrade();
		QuickSlot q = new QuickSlot();
		q.setSlot(11, s);
		assertEquals(11, q.getSlot(s));
		q.setSlot(7, s); //an item is only ever in one slot
		assertNull(q.getItem(11));

		//placeholders in the second row survive a save
		q.setSlot(9, s.virtual());
		Bundle b = new Bundle();
		q.storePlaceholders(b);
		QuickSlot r = new QuickSlot();
		r.restorePlaceholders(b);
		assertTrue(r.isPlaceholder(9));

		//a save from before the second row (6 placements) still loads
		Bundle old = new Bundle();
		java.util.ArrayList<com.shatteredpixel.shatteredpixeldungeon.items.Item> ph = new java.util.ArrayList<>();
		ph.add(s.virtual());
		old.put("placeholders", ph);
		old.put("placements", new boolean[]{false, false, true, false, false, false});
		QuickSlot o = new QuickSlot();
		o.restorePlaceholders(old);
		assertTrue(o.isPlaceholder(2));
	}

	@Test
	void secondHotbarKeysAndPlaces(){
		assertEquals(SPDAction.QUICKSLOT_7, new QuickSlotButton(6).keyAction());
		assertEquals(SPDAction.QUICKSLOT_12, new QuickSlotButton(11).keyAction());
		QuickSlotButton.reset();

		//slot 7 on the side of slot 1, then leftwards; mirrored when the toolbar is flipped
		assertEquals(200 - 22, Hotbar2.x(0, 200, 6, 22, false));
		assertEquals(200 - 6 * 22, Hotbar2.x(0, 200, 11, 22, false));
		assertEquals(0, Hotbar2.x(0, 200, 6, 22, true));
		assertEquals(5 * 22, Hotbar2.x(0, 200, 11, 22, true));

		assertTrue(Hotbar2.shown());
		assertEquals(Hotbar2.ROW, Hotbar2.extraHeight());
		Features.setSolo(Feature.HOTBAR_2, false);
		try {
			assertFalse(Hotbar2.shown());
			assertEquals(0, Hotbar2.extraHeight());
		} finally {
			Features.setSolo(Feature.HOTBAR_2, true);
		}
	}

	// C6

	@Test
	void hpTickStepDependsOnlyOnMaxHP(){
		assertEquals(5, HpTicks.step(6));
		assertEquals(5, HpTicks.step(30));
		assertEquals(10, HpTicks.step(31));
		assertEquals(25, HpTicks.step(120));
		assertEquals(50, HpTicks.step(300));
		assertEquals(100, HpTicks.step(1000));

		assertEquals(5, HpTicks.count(30, 30));  //marks at 5..25
		assertEquals(1, HpTicks.count(6, 6));
		assertEquals(0, HpTicks.count(5, 5));
		assertEquals(6, HpTicks.count(30, 33));  //shield over max HP widens the bar
		assertEquals(0, HpTicks.count(30, 0));
	}

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
