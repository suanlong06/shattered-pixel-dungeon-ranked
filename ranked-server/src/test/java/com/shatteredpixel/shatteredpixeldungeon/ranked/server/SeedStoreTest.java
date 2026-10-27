package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.shatteredpixel.shatteredpixeldungeon.ranked.seed.SeedPoolFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A4 on the server: the pool file goes into SQLite and each seed backs at most one match */
class SeedStoreTest {

	static final String POOL = "# " + SeedPoolFile.currentHeader() + "\n5 20 30\n3 19 25\n\n9 18 36\n";

	@Test
	void poolFileLinesAndHeader() {
		List<SeedPoolFile.Entry> e = SeedPoolFile.entries(POOL);
		assertEquals(3, e.size());
		assertEquals(5, e.get(0).seed);
		assertEquals(19, e.get(1).min);
		assertEquals(36, e.get(2).max);
		assertEquals(SeedPoolFile.currentHeader(), SeedPoolFile.header(POOL));
		assertNull(SeedPoolFile.header("7\n"));
		assertEquals(-1, SeedPoolFile.entries("7\n").get(0).min);
		assertThrows(IllegalArgumentException.class, () -> SeedPoolFile.entries("1 2\n"));
	}

	@Test
	void seedsGoOutInFileOrderOncePerMatch() throws Exception {
		try (Storage db = new Storage("jdbc:sqlite::memory:")) {
			assertEquals(3, db.addSeeds(SeedPoolFile.entries(POOL), SeedPoolFile.header(POOL), 1));
			assertEquals(3, db.seedsLeft());
			assertEquals(Long.valueOf(5), db.startMatch("m1", 1, "desktop", 1, 2, 10));
			assertEquals(Long.valueOf(3), db.startMatch("m2", 1, "desktop", 1, 2, 11));
			assertEquals("m1", db.seedUsedBy(5));
			assertEquals("m2", db.seedUsedBy(3));
			assertNull(db.seedUsedBy(9));
			assertEquals(1, db.seedsLeft());
			assertEquals("PREPARING", db.matchState("m1"));
		}
	}

	@Test
	void failedMatchInsertGivesTheSeedBack() throws Exception {
		try (Storage db = new Storage("jdbc:sqlite::memory:")) {
			db.addSeeds(SeedPoolFile.entries(POOL), null, 1);
			db.startMatch("dup", 1, "desktop", 1, 2, 10);
			// same match id: the match insert fails, so the seed take must roll back too
			assertThrows(SQLException.class, () -> db.startMatch("dup", 1, "desktop", 1, 2, 11));
			assertNull(db.seedUsedBy(3));
			assertEquals(2, db.seedsLeft());
			assertEquals(Long.valueOf(3), db.startMatch("m2", 1, "desktop", 1, 2, 12));
		}
	}

	@Test
	void emptyPoolStartsNoMatch() throws Exception {
		try (Storage db = new Storage("jdbc:sqlite::memory:")) {
			assertNull(db.startMatch("m1", 1, "desktop", 1, 2, 10));
			assertNull(db.matchState("m1"));
		}
	}

	@Test
	void usedStateSurvivesRestartAndReload(@TempDir Path dir) throws Exception {
		String url = "jdbc:sqlite:" + dir.resolve("ranked.db");
		try (Storage db = new Storage(url)) {
			db.addSeeds(SeedPoolFile.entries(POOL), SeedPoolFile.header(POOL), 1);
			db.startMatch("m1", 1, "desktop", 1, 2, 10);
		}
		try (Storage db = new Storage(url)) {
			// the server reloads the same file at every start
			assertEquals(0, db.addSeeds(SeedPoolFile.entries(POOL), SeedPoolFile.header(POOL), 2));
			assertEquals("m1", db.seedUsedBy(5));
			assertEquals(2, db.seedsLeft());
			assertEquals(Long.valueOf(3), db.startMatch("m2", 1, "desktop", 1, 2, 20));
		}
	}

	@Test
	void stalePoolsCanBeRetired() throws Exception {
		try (Storage db = new Storage("jdbc:sqlite::memory:")) {
			db.addSeeds(SeedPoolFile.entries("1 1 1\n2 1 1\n"), "spdr-seedpool 1 depth 5 min 10 cap 30", 1);
			db.startMatch("old", 1, "desktop", 1, 2, 5);
			db.addSeeds(SeedPoolFile.entries(POOL), SeedPoolFile.header(POOL), 2);
			assertEquals(1, db.retireOtherPools(SeedPoolFile.currentHeader()));
			assertEquals(3, db.seedsLeft());
			assertEquals("old", db.seedUsedBy(1));
			assertEquals(Long.valueOf(5), db.startMatch("m1", 1, "desktop", 1, 2, 10));
		}
	}

	@Test
	void schemaTwoDatabaseKeepsItsSeeds(@TempDir Path dir) throws Exception {
		String url = "jdbc:sqlite:" + dir.resolve("old.db");
		try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
			s.execute("CREATE TABLE player (id INTEGER PRIMARY KEY, device_key TEXT NOT NULL UNIQUE, name TEXT NOT NULL, created INTEGER NOT NULL)");
			s.execute("CREATE TABLE rating (player_id INTEGER NOT NULL, season INTEGER NOT NULL, platform TEXT NOT NULL,"
					+ " rating REAL NOT NULL, rd REAL NOT NULL, vol REAL NOT NULL, games INTEGER NOT NULL, PRIMARY KEY (player_id, season, platform))");
			s.execute("CREATE TABLE seed (seed INTEGER PRIMARY KEY, used_by TEXT)");
			s.execute("CREATE TABLE match (id TEXT PRIMARY KEY, season INTEGER NOT NULL, platform TEXT NOT NULL, a INTEGER NOT NULL, b INTEGER NOT NULL,"
					+ " seed INTEGER NOT NULL, state TEXT NOT NULL, outcome TEXT, reason TEXT, created INTEGER NOT NULL, ended INTEGER)");
			s.execute("CREATE TABLE strike (player_id INTEGER NOT NULL, at INTEGER NOT NULL, reason TEXT NOT NULL)");
			s.execute("CREATE TABLE replay (match_id TEXT NOT NULL, side INTEGER NOT NULL, hero_class TEXT,"
					+ " commands TEXT NOT NULL, result TEXT, detail TEXT, PRIMARY KEY (match_id, side))");
			s.execute("INSERT INTO seed VALUES (100, 'old-match'), (200, NULL)");
			s.execute("PRAGMA user_version = 2");
		}
		try (Storage db = new Storage(url)) {
			assertEquals("old-match", db.seedUsedBy(100));
			assertEquals(1, db.seedsLeft());
			assertEquals(Long.valueOf(200), db.takeSeed("m"));
		}
	}
}
