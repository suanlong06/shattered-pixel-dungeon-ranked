package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.shatteredpixel.shatteredpixeldungeon.ranked.seed.SeedPoolFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A1): SQLite storage. Players are keyed by device key (L2 default: device key
 * plus display name). Ratings are per season and platform (L3). Seeds come from the A4 pool
 * file and are used once:
 * a seed is marked with the match that took it in the same transaction that creates the
 * match, so a seed can never back two matches, even across a crash. Schema version lives in {@code PRAGMA user_version}.
 */
public final class Storage implements AutoCloseable {

	public static final int SCHEMA = 4;

	private final Connection db;

	/** @param url e.g. {@code jdbc:sqlite:ranked.db}, or {@code jdbc:sqlite::memory:} in tests */
	public Storage(String url) throws SQLException {
		db = DriverManager.getConnection(url);
		try (Statement s = db.createStatement()) {
			s.execute("PRAGMA foreign_keys = ON");
			if (!url.contains(":memory:")) s.execute("PRAGMA journal_mode = WAL");
			int version;
			try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
				version = r.next() ? r.getInt(1) : 0;
			}
			if (version > SCHEMA) throw new SQLException("database schema " + version + " is newer than server " + SCHEMA);
			if (version < 1) {
				s.execute("CREATE TABLE player (id INTEGER PRIMARY KEY, device_key TEXT NOT NULL UNIQUE, name TEXT NOT NULL, created INTEGER NOT NULL)");
				s.execute("CREATE TABLE rating (player_id INTEGER NOT NULL REFERENCES player(id), season INTEGER NOT NULL, platform TEXT NOT NULL,"
						+ " rating REAL NOT NULL, rd REAL NOT NULL, vol REAL NOT NULL, games INTEGER NOT NULL, PRIMARY KEY (player_id, season, platform))");
				s.execute("CREATE TABLE seed (seed INTEGER PRIMARY KEY, used_by TEXT)");
				s.execute("CREATE TABLE match (id TEXT PRIMARY KEY, season INTEGER NOT NULL, platform TEXT NOT NULL, a INTEGER NOT NULL, b INTEGER NOT NULL,"
						+ " seed INTEGER NOT NULL, state TEXT NOT NULL, outcome TEXT, reason TEXT, created INTEGER NOT NULL, ended INTEGER)");
				s.execute("CREATE TABLE strike (player_id INTEGER NOT NULL REFERENCES player(id), at INTEGER NOT NULL, reason TEXT NOT NULL)");
				s.execute("PRAGMA user_version = 1");
			}
			if (version < 2) {
				// A3: each side's command log and what replaying it showed, kept for disputes
				s.execute("CREATE TABLE replay (match_id TEXT NOT NULL REFERENCES match(id), side INTEGER NOT NULL, hero_class TEXT,"
						+ " commands TEXT NOT NULL, result TEXT, detail TEXT, PRIMARY KEY (match_id, side))");
				s.execute("PRAGMA user_version = 2");
			}
			if (version < 3) {
				// A4: keep each seed's scores and the pool header it came from (stale thresholds
				// can be found and retired), and when it was loaded and used
				s.execute("ALTER TABLE seed ADD COLUMN min_score INTEGER");
				s.execute("ALTER TABLE seed ADD COLUMN max_score INTEGER");
				s.execute("ALTER TABLE seed ADD COLUMN pool TEXT");
				s.execute("ALTER TABLE seed ADD COLUMN added INTEGER");
				s.execute("ALTER TABLE seed ADD COLUMN used_at INTEGER");
				s.execute("ALTER TABLE seed ADD COLUMN retired INTEGER NOT NULL DEFAULT 0");
				// hand-out order: the seed is the rowid, so rowid order would be numeric, not file order
				s.execute("ALTER TABLE seed ADD COLUMN pos INTEGER");
				s.execute("UPDATE seed SET pos = (SELECT COUNT(*) FROM seed o WHERE o.seed <= seed.seed)");
				s.execute("PRAGMA user_version = 3");
			}
			if (version < 4) {
				// A5: each side's run summary (key=value lines), for tuning features and training models
				s.execute("CREATE TABLE telemetry (match_id TEXT NOT NULL REFERENCES match(id), side INTEGER NOT NULL,"
						+ " received INTEGER NOT NULL, body TEXT NOT NULL, PRIMARY KEY (match_id, side))");
				s.execute("PRAGMA user_version = 4");
			}
		}
	}

	public long player(String deviceKey, String name, long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT id FROM player WHERE device_key = ?")) {
			p.setString(1, deviceKey);
			try (ResultSet r = p.executeQuery()) {
				if (r.next()) {
					long id = r.getLong(1);
					try (PreparedStatement u = db.prepareStatement("UPDATE player SET name = ? WHERE id = ?")) {
						u.setString(1, name);
						u.setLong(2, id);
						u.executeUpdate();
					}
					return id;
				}
			}
		}
		try (PreparedStatement p = db.prepareStatement("INSERT INTO player (device_key, name, created) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
			p.setString(1, deviceKey);
			p.setString(2, name);
			p.setLong(3, now);
			p.executeUpdate();
			try (ResultSet r = p.getGeneratedKeys()) {
				r.next();
				return r.getLong(1);
			}
		}
	}

	public Glicko2.Rating rating(long player, int season, String platform) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT rating, rd, vol FROM rating WHERE player_id = ? AND season = ? AND platform = ?")) {
			p.setLong(1, player);
			p.setInt(2, season);
			p.setString(3, platform);
			try (ResultSet r = p.executeQuery()) {
				return r.next() ? new Glicko2.Rating(r.getDouble(1), r.getDouble(2), r.getDouble(3)) : Glicko2.Rating.initial();
			}
		}
	}

	public void setRating(long player, int season, String platform, Glicko2.Rating g) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("INSERT INTO rating VALUES (?, ?, ?, ?, ?, ?, 1)"
				+ " ON CONFLICT (player_id, season, platform) DO UPDATE SET rating = excluded.rating, rd = excluded.rd, vol = excluded.vol, games = games + 1")) {
			p.setLong(1, player);
			p.setInt(2, season);
			p.setString(3, platform);
			p.setDouble(4, g.rating);
			p.setDouble(5, g.rd);
			p.setDouble(6, g.vol);
			p.executeUpdate();
		}
	}

	/** loads bare seeds (tests); known seeds keep their used state */
	public int addSeeds(List<Long> seeds) throws SQLException {
		List<SeedPoolFile.Entry> e = new ArrayList<>();
		for (long s : seeds) e.add(new SeedPoolFile.Entry(s, -1, -1));
		return addSeeds(e, null, 0);
	}

	/**
	 * loads a pool file's seeds in file order, in one transaction. Seeds already known keep
	 * their used state, scores and pool, so reloading the same file at every start is safe.
	 */
	public int addSeeds(List<SeedPoolFile.Entry> seeds, String pool, long now) throws SQLException {
		return inTransaction(() -> {
			int added = 0;
			try (PreparedStatement p = db.prepareStatement("INSERT OR IGNORE INTO seed (seed, min_score, max_score, pool, added, pos)"
					+ " VALUES (?, ?, ?, ?, ?, (SELECT COALESCE(MAX(pos), 0) + 1 FROM seed))")) {
				for (SeedPoolFile.Entry e : seeds) {
					p.setLong(1, e.seed);
					if (e.min < 0) p.setNull(2, java.sql.Types.INTEGER); else p.setInt(2, e.min);
					if (e.max < 0) p.setNull(3, java.sql.Types.INTEGER); else p.setInt(3, e.max);
					p.setString(4, pool);
					p.setLong(5, now);
					added += p.executeUpdate();
				}
			}
			return added;
		});
	}

	/** unused seeds that matches can still take */
	public int seedsLeft() throws SQLException {
		try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM seed WHERE used_by IS NULL AND retired = 0")) {
			r.next();
			return r.getInt(1);
		}
	}

	/**
	 * stops handing out unused seeds whose pool header differs from {@code keep} (built with
	 * old thresholds); used seeds stay as the record of their matches. Returns how many.
	 */
	public int retireOtherPools(String keep) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("UPDATE seed SET retired = 1 WHERE used_by IS NULL AND retired = 0 AND (pool IS NULL OR pool <> ?)")) {
			p.setString(1, keep);
			return p.executeUpdate();
		}
	}

	/** the match that used a seed, or null if it is unused or unknown */
	public String seedUsedBy(long seed) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT used_by FROM seed WHERE seed = ?")) {
			p.setLong(1, seed);
			try (ResultSet r = p.executeQuery()) {
				return r.next() ? r.getString(1) : null;
			}
		}
	}

	/** takes the next unused seed (file order) for a match, or null if the pool is empty */
	public Long takeSeed(String matchId) throws SQLException {
		return takeSeed(matchId, 0);
	}

	private Long takeSeed(String matchId, long now) throws SQLException {
		// one statement, so two takers can never get the same seed
		try (PreparedStatement u = db.prepareStatement("UPDATE seed SET used_by = ?, used_at = ?"
				+ " WHERE seed = (SELECT seed FROM seed WHERE used_by IS NULL AND retired = 0 ORDER BY pos LIMIT 1) RETURNING seed")) {
			u.setString(1, matchId);
			u.setLong(2, now);
			try (ResultSet r = u.executeQuery()) {
				return r.next() ? r.getLong(1) : null;
			}
		}
	}

	/**
	 * takes a seed and creates the match with it in one transaction: either both happen or
	 * neither does. Returns the seed, or null (and no match) if the pool is empty.
	 */
	public Long startMatch(String id, int season, String platform, long a, long b, long now) throws SQLException {
		return inTransaction(() -> {
			Long seed = takeSeed(id, now);
			if (seed != null) createMatch(id, season, platform, a, b, seed, now);
			return seed;
		});
	}

	public void createMatch(String id, int season, String platform, long a, long b, long seed, long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("INSERT INTO match (id, season, platform, a, b, seed, state, created) VALUES (?, ?, ?, ?, ?, ?, 'PREPARING', ?)")) {
			p.setString(1, id);
			p.setInt(2, season);
			p.setString(3, platform);
			p.setLong(4, a);
			p.setLong(5, b);
			p.setLong(6, seed);
			p.setLong(7, now);
			p.executeUpdate();
		}
	}

	private interface Work<T> {
		T run() throws SQLException;
	}

	private synchronized <T> T inTransaction(Work<T> w) throws SQLException {
		db.setAutoCommit(false);
		try {
			T out = w.run();
			db.commit();
			return out;
		} catch (SQLException | RuntimeException e) {
			db.rollback();
			throw e;
		} finally {
			db.setAutoCommit(true);
		}
	}

	public void endMatch(String id, String state, String outcome, String reason, long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("UPDATE match SET state = ?, outcome = ?, reason = ?, ended = ? WHERE id = ?")) {
			p.setString(1, state);
			p.setString(2, outcome);
			p.setString(3, reason);
			p.setLong(4, now);
			p.setString(5, id);
			p.executeUpdate();
		}
	}

	public void saveReplay(String matchId, int side, String heroClass, String commands, String result, String detail) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("INSERT OR REPLACE INTO replay (match_id, side, hero_class, commands, result, detail) VALUES (?, ?, ?, ?, ?, ?)")) {
			p.setString(1, matchId);
			p.setInt(2, side);
			p.setString(3, heroClass);
			p.setString(4, commands);
			p.setString(5, result);
			p.setString(6, detail);
			p.executeUpdate();
		}
	}

	/** {result, detail} of a stored replay, or null */
	public void saveTelemetry(String matchId, int side, String body, long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("INSERT OR REPLACE INTO telemetry (match_id, side, received, body) VALUES (?, ?, ?, ?)")) {
			p.setString(1, matchId);
			p.setInt(2, side);
			p.setLong(3, now);
			p.setString(4, body);
			p.executeUpdate();
		}
	}

	/** null if that side sent none */
	public String telemetry(String matchId, int side) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT body FROM telemetry WHERE match_id = ? AND side = ?")) {
			p.setString(1, matchId);
			p.setInt(2, side);
			try (ResultSet r = p.executeQuery()) {
				return r.next() ? r.getString(1) : null;
			}
		}
	}

	public String[] replayResult(String matchId, int side) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT result, detail FROM replay WHERE match_id = ? AND side = ?")) {
			p.setString(1, matchId);
			p.setInt(2, side);
			try (ResultSet r = p.executeQuery()) {
				return r.next() ? new String[]{r.getString(1), r.getString(2)} : null;
			}
		}
	}

	public String matchState(String id) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT state FROM match WHERE id = ?")) {
			p.setString(1, id);
			try (ResultSet r = p.executeQuery()) {
				return r.next() ? r.getString(1) : null;
			}
		}
	}

	/** server restart mid-match voids the match (T0.5: kept from the old design) */
	public int voidUnfinished(long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("UPDATE match SET state = 'VOIDED', reason = 'SERVER_RESTART', ended = ? WHERE ended IS NULL")) {
			p.setLong(1, now);
			return p.executeUpdate();
		}
	}

	public void strike(long player, long now, String reason) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("INSERT INTO strike VALUES (?, ?, ?)")) {
			p.setLong(1, player);
			p.setLong(2, now);
			p.setString(3, reason);
			p.executeUpdate();
		}
	}

	public int strikesSince(long player, long since) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT COUNT(*) FROM strike WHERE player_id = ? AND at >= ?")) {
			p.setLong(1, player);
			p.setLong(2, since);
			try (ResultSet r = p.executeQuery()) {
				r.next();
				return r.getInt(1);
			}
		}
	}

	@Override
	public void close() throws SQLException { db.close(); }
}
