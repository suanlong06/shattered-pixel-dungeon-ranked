package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.log.EventLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.race.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.ranked.race.Splits;
import com.shatteredpixel.shatteredpixeldungeon.ranked.replay.ReplayFile;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.SkinDenylist;
import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.SkinPack;
import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.Skins;
import com.shatteredpixel.shatteredpixeldungeon.ranked.telemetry.Telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A5 (splits, ghost, replay file, telemetry) and A6 (skin packs, Ranked denylist), run headless. */
class RaceReplaySkinTest {

	@AfterEach
	void reset(){
		Features.setMode(GameMode.SOLO);
		if (Gdx.app != null) Skins.select(null);
	}

	// A5

	@Test
	void splitsAndGhostRoundTripAndCompare(){
		Splits mine = new Splits();
		mine.arrive(1, 0f);
		mine.arrive(2, 120f);
		mine.arrive(1, 150f); // going back up is not a new split
		mine.arrive(3, 300f);
		Splits ghostRun = new Splits();
		ghostRun.arrive(1, 0f);
		ghostRun.arrive(2, 100f);
		ghostRun.arrive(3, 350f);
		ghostRun.arrive(4, 500f);

		assertEquals(Float.valueOf(120f), mine.arrivedAt(2));
		assertEquals(mine, Splits.fromText(mine.toText()));

		Ghost g = new Ghost(42L, "best", ghostRun);
		Ghost back = Ghost.fromText(g.toText());
		assertEquals(42L, back.seed);
		assertEquals("best", back.label);
		assertEquals(ghostRun, back.splits);

		Ghost.Delta d = g.compare(mine);
		assertEquals(3, d.depth);
		assertEquals(-50f, d.delta, 0.001f);
		assertTrue(d.ahead());
		assertNull(g.compare(new Splits()));

		assertTrue(Ghost.better(ghostRun, mine), "deeper wins");
		Splits boss = mine.copy();
		boss.boss(3, 400f);
		assertTrue(Ghost.better(boss, ghostRun), "a boss kill beats depth");
		assertFalse(Ghost.better(mine, mine));
	}

	@Test
	void replayFileReplaysTheSameRunWithTheSameSplitsAndTelemetry(){
		Headless.boot();
		Features.setMode(GameMode.SOLO); // a menu start resets the log in Dungeon.initSeed; tests call Dungeon.init directly
		TurnRunner a = Sim.play(DeterministicCoreTest.SEED, HeroClass.WARRIOR,
				new Bot(new Bot.Scenario(true, 200, 3)), 600);
		Splits splitsA = Splits.current().copy();
		String telA = Telemetry.of(EventLog.events(), splitsA, "Hero").toText();
		assertTrue(splitsA.deepest() >= 2, "bot should leave depth 1: " + a.stop);
		assertEquals(Float.valueOf(0f), splitsA.arrivedAt(1));

		ReplayFile file = new ReplayFile(Dungeon.version, DeterministicCoreTest.SEED, HeroClass.WARRIOR,
				GameMode.SOLO, a.traceDigest(), a.applied);
		ReplayFile back = ReplayFile.fromText(file.toText().replace("\n", "\r\n"));
		assertEquals(file.toText(), back.toText());

		Features.setMode(back.mode);
		TurnRunner b = Sim.play(back.seed, back.heroClass,
				new Bot.Replay(back.commands, new Bot.Scenario(true, 200, 3)), Integer.MAX_VALUE);
		assertEquals(back.digest, b.traceDigest());
		assertEquals(splitsA, Splits.current());
		assertEquals(telA, Telemetry.of(EventLog.events(), Splits.current(), "Hero").toText());
		assertTrue(splitsA.arrivedAt(2) > 0f, telA);
		assertTrue(telA.contains("turns.d1=" + splitsA.arrivedAt(2)), telA);
	}

	@Test
	void replayFileRejectsGarbage(){
		assertThrows(IllegalArgumentException.class, () -> ReplayFile.fromText("hello"));
		assertThrows(IllegalArgumentException.class, () -> ReplayFile.fromText("spdr-replay v1\nseed=1\n"));
	}

	// A6

	@Test
	void bundledDenylistParsesAndBrokenOnesAreRefused(){
		Headless.boot();
		SkinDenylist d = SkinDenylist.parse(Gdx.files.internal(SkinDenylist.ASSET).readString("UTF-8"));
		assertEquals(10, d.size());
		assertTrue(d.deniesWhole("sprites/statue.png"));
		assertEquals(4, d.rects("sprites/items.png").size());
		assertTrue(d.rects("sprites/warrior.png").isEmpty());
		assertThrows(IllegalArgumentException.class, () -> SkinDenylist.parse("deny.rects.a.png=1,2,3"));
		assertThrows(IllegalArgumentException.class, () -> SkinDenylist.parse("allow.everything=1"));
	}

	@Test
	void skinsReplaceArtInSoloAndKeepDeniedArtInRanked(){
		Headless.boot();
		Skins.setDenylist(Gdx.files.internal(SkinDenylist.ASSET).readString("UTF-8"));
		FileHandle root = Gdx.files.local(Skins.DIR + "spdr-test-pack");
		root.deleteDirectory();
		try {
			Pixmap mimic = new Pixmap(Gdx.files.internal("sprites/mimic.png"));
			int w = mimic.getWidth(), h = mimic.getHeight();
			int origHidden = mimic.getPixel(5, 5), origOther = mimic.getPixel(w - 3, 5);
			mimic.dispose();
			paint(root.child("sprites/mimic.png"), w, h);
			paint(root.child("sprites/statue.png"), 16, 16);
			root.child(SkinPack.MANIFEST).writeString("name=Test pack\nauthor=me\n", false, "UTF-8");

			SkinPack pack = Skins.find("spdr-test-pack");
			assertNotNull(pack);
			assertEquals("Test pack", pack.name);
			assertNull(pack.file("../sprites/mimic.png"));
			assertEquals(2, pack.paths().size());
			Skins.select(pack);

			Pixmap solo = Skins.load("sprites/mimic.png");
			assertEquals(RED, solo.getPixel(5, 5));
			solo.dispose();
			assertNotNull(Skins.load("sprites/statue.png"));
			assertNull(Skins.load("sprites/warrior.png"), "not in the pack");

			Features.setMode(GameMode.RANKED);
			Pixmap ranked = Skins.load("sprites/mimic.png");
			assertEquals(origHidden, ranked.getPixel(5, 5), "hiding frame keeps the original art");
			assertEquals(RED, ranked.getPixel(w - 3, 5), "other frames are skinned");
			assertTrue(origOther != RED);
			ranked.dispose();
			assertNull(Skins.load("sprites/statue.png"), "denied file");

			Skins.setDenylist(null);
			assertNull(Skins.load("sprites/mimic.png"), "no denylist: Ranked fails closed");
		} finally {
			Skins.setDenylist(Gdx.files.internal(SkinDenylist.ASSET).readString("UTF-8"));
			root.deleteDirectory();
		}
	}

	private static final int RED = 0xFF0000FF;

	private static void paint(FileHandle f, int w, int h){
		Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
		p.setColor(RED);
		p.fill();
		f.parent().mkdirs();
		PixmapIO.writePNG(f, p);
		p.dispose();
	}
}
