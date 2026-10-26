package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * spd-ranked (A1): SQLite storage. Players are keyed by device key (L2 default: device key
 * plus display name). Ratings are per season and platform (L3). Seeds come from the A4 pool
 * file and are used once. Schema version lives in {@code PRAGMA user_version}.
 */
public final class Storage implements AutoCloseable {

	public static final int SCHEMA = 1;

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

	/** loads pool seeds; seeds already known keep their used state */
	public int addSeeds(List<Long> seeds) throws SQLException {
		int added = 0;
		try (PreparedStatement p = db.prepareStatement("INSERT OR IGNORE INTO seed (seed) VALUES (?)")) {
			for (long s : seeds) {
				p.setLong(1, s);
				added += p.executeUpdate();
			}
		}
		return added;
	}

	/** takes the next unused seed for a match, or null if the pool is empty */
	public Long takeSeed(String matchId) throws SQLException {
		try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT seed FROM seed WHERE used_by IS NULL ORDER BY rowid LIMIT 1")) {
			if (!r.next()) return null;
			long seed = r.getLong(1);
			try (PreparedStatement u = db.prepareStatement("UPDATE seed SET used_by = ? WHERE seed = ?")) {
				u.setString(1, matchId);
				u.setLong(2, seed);
				u.executeUpdate();
			}
			return seed;
		}
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
