package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FetidRat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Goo;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.ExplorationMemory;
import com.shatteredpixel.shatteredpixeldungeon.ranked.qol.Wiki;
import com.watabou.utils.Bundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C4 wiki and C5 exploration memory (docs/C4-C5-wiki-memory.md): logic only, run headless. */
class WikiMemoryTest {

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
	void bothAreOnAndEqualInRanked(){
		Features.setMode(GameMode.RANKED);
		assertTrue(Features.enabled(Feature.WIKI));
		assertTrue(Features.enabled(Feature.EXPLORATION_MEMORY));
	}

	// C4

	@Test
	void tipsBelongToExactlyOneClass(){
		assertNotNull(Wiki.tip(Rat.class));
		assertNotNull(Wiki.tip(Goo.class));
		//a subclass does not inherit its parent's tip
		assertNull(Wiki.tip(FetidRat.class));
		assertNull(Wiki.tip(null));
	}

	@Test
	void tipOnlyOnOpenPages(){
		String desc = "desc";
		assertEquals(desc, Wiki.withTip(desc, Rat.class, false));
		String open = Wiki.withTip(desc, Rat.class, true);
		assertTrue(open.startsWith(desc + "\n\n"));
		assertTrue(open.endsWith(Wiki.tip(Rat.class)));
		assertEquals(desc, Wiki.withTip(desc, FetidRat.class, true));

		Features.setSolo(Feature.WIKI, false);
		try {
			assertEquals(desc, Wiki.withTip(desc, Rat.class, true));
		} finally {
			Features.setSolo(Feature.WIKI, true);
		}
	}

	@Test
	void standardCatalogOnlyDuringARankedMatch(){
		//Solo: the profile decides
		assertFalse(Wiki.seen(false));
		assertTrue(Wiki.seen(true));
		//Ranked outside the game scene (menus, tests): still the profile
		Features.setMode(GameMode.RANKED);
		assertFalse(Wiki.standardCatalog());
		assertFalse(Wiki.seen(false));
	}

	/** every tip key, in both languages, names a class the journal shows, so no tip is dead text */
	@Test
	void everyTipNamesAJournalEntry() throws Exception {
		Set<String> entries = new HashSet<>();
		for (Catalog c : Catalog.values()) for (Class<?> cls : c.items()) entries.add(simple(cls));
		for (Bestiary b : Bestiary.values()) for (Class<?> cls : b.entities()) entries.add(simple(cls));

		Set<String> en = tipKeys("messages/ranked/ranked.properties");
		Set<String> vi = tipKeys("messages/ranked/ranked_vi.properties");
		assertFalse(en.isEmpty());
		for (String k : en) assertTrue(entries.contains(k), "no journal entry for tip " + k);
		assertEquals(en, vi, "the English and Vietnamese tips cover different entries");
	}

	private static String simple(Class<?> c){
		return c.getSimpleName().toLowerCase(Locale.ENGLISH);
	}

	private static Set<String> tipKeys(String path) throws Exception {
		Properties p = new Properties();
		InputStream in = com.badlogic.gdx.Gdx.files.internal(path).read();
		try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)){
			p.load(r);
		}
		String prefix = Wiki.key(Rat.class).replace("rat", "");
		Set<String> keys = new HashSet<>();
		for (String k : p.stringPropertyNames()){
			if (k.startsWith(prefix)) keys.add(k.substring(prefix.length()));
		}
		return keys;
	}

	// C5

	/** a 5x5 patch: door at the centre, discovered cells around it except one */
	@Test
	void doorToUnknownIsCountedOnlyWhenDiscovered(){
		Level l = Dungeon.level;
		Arrays.fill(l.visited, false);
		Arrays.fill(l.mapped, false);
		int door = l.entrance();
		int w = l.width();
		l.map[door] = Terrain.DOOR;

		assertFalse(ExplorationMemory.opensOnUnknown(l, door));
		l.visited[door] = true;
		assertTrue(ExplorationMemory.opensOnUnknown(l, door));
		l.visited[door - 1] = l.visited[door + 1] = l.visited[door - w] = l.visited[door + w] = true;
		assertFalse(ExplorationMemory.opensOnUnknown(l, door));

		//a secret door is wall until found
		l.visited[door + w] = false;
		l.map[door] = Terrain.SECRET_DOOR;
		assertFalse(ExplorationMemory.opensOnUnknown(l, door));
	}

	@Test
	void scanCountsOnlyWhatWasSeen(){
		Level l = Dungeon.level;
		Arrays.fill(l.visited, false);
		Arrays.fill(l.mapped, false);
		l.heaps.clear();
		int w = l.width();
		int c = l.entrance();

		l.map[c] = Terrain.LOCKED_DOOR;
		l.mapped[c] = true;
		Heap seen = l.drop(new Gold(5), c + 2 * w);
		seen.seen = true;
		Heap unseen = l.drop(new Gold(5), c - 2 * w);
		unseen.seen = false;

		ExplorationMemory.FloorNotes n = ExplorationMemory.scan(l, 1);
		assertEquals(1, n.lockedDoors);
		assertFalse(n.ironKey);
		assertEquals(1, n.items);
		assertEquals(0, n.containers);

		Notes.add(new IronKey(1));
		assertTrue(ExplorationMemory.scan(l, 1).ironKey);
		Notes.remove(new IronKey(1));

		String line = ExplorationMemory.describe(n);
		assertNotNull(line);
		assertTrue(line.contains("1"));
		assertNull(ExplorationMemory.describe(new ExplorationMemory.FloorNotes()));
	}

	@Test
	void scanDoesNotChangeTheLevel(){
		Level l = Dungeon.level;
		Set<String> none = Collections.emptySet();
		String before = Digest.of(l, none);
		ExplorationMemory.scan(l, 1);
		ExplorationMemory.update();
		assertEquals(before, Digest.of(l, none));
	}

	@Test
	void notesSurviveASave() throws Exception {
		ExplorationMemory.reset();
		Level l = Dungeon.level;
		Arrays.fill(l.visited, true);
		ExplorationMemory.update();
		ExplorationMemory.FloorNotes before = ExplorationMemory.notes(Dungeon.depth);
		assertNotNull(before);

		Bundle b = new Bundle();
		Features.store(b);
		ExplorationMemory.reset();
		assertNull(ExplorationMemory.notes(Dungeon.depth));
		Features.restore(Bundle.read(new java.io.ByteArrayInputStream(b.toString().getBytes(StandardCharsets.UTF_8))));

		ExplorationMemory.FloorNotes after = ExplorationMemory.notes(Dungeon.depth);
		assertNotNull(after);
		assertEquals(before.openDoors, after.openDoors);
		assertEquals(before.items, after.items);
		assertEquals(before.containers, after.containers);
	}
}
