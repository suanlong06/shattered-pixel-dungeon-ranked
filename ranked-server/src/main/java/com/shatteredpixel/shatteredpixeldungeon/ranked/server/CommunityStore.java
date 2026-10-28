package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * spd-ranked (C8): SQL for the community features (weekly league, profiles, chat reports).
 * Shares {@link Storage}'s connection; its tables are created with IF NOT EXISTS and do not
 * move Storage's schema version, so the ranked core schema can change without touching these.
 */
final class CommunityStore {

	private final Storage storage;
	private final Connection db;

	CommunityStore(Storage storage) throws SQLException {
		this.storage = storage;
		this.db = storage.connection();
		try (Statement s = db.createStatement()) {
			// one pool seed per week, shared by every entrant
			s.execute("CREATE TABLE IF NOT EXISTS league_week (week INTEGER PRIMARY KEY, seed INTEGER NOT NULL, created INTEGER NOT NULL)");
			// one attempt per player and week; state STARTED -> VERIFYING -> ACCEPTED / REJECTED / UNVERIFIABLE
			s.execute("CREATE TABLE IF NOT EXISTS league_entry (week INTEGER NOT NULL, player_id INTEGER NOT NULL REFERENCES player(id),"
					+ " started INTEGER NOT NULL, state TEXT NOT NULL, hero_class TEXT, depth INTEGER, bosses INTEGER, turns INTEGER,"
					+ " dead INTEGER, cleared INTEGER, submitted INTEGER, commands TEXT, detail TEXT, PRIMARY KEY (week, player_id))");
			s.execute("CREATE TABLE IF NOT EXISTS chat_report (reporter INTEGER NOT NULL, target INTEGER NOT NULL, text TEXT, at INTEGER NOT NULL)");
			s.execute("CREATE TABLE IF NOT EXISTS chat_mute (player_id INTEGER PRIMARY KEY, until INTEGER NOT NULL, reason TEXT)");
		}
	}

	// ---- league

	/** the week's seed, taken from the pool the first time anyone asks; null if the pool is empty */
	Long weekSeed(long week, long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT seed FROM league_week WHERE week = ?")) {
			p.setLong(1, week);
			try (ResultSet r = p.executeQuery()) {
				if (r.next()) return r.getLong(1);
			}
		}
		return storage.inTransaction(() -> {
			Long seed = storage.takeSeed("league-W" + week, now);
			if (seed == null) return null;
			try (PreparedStatement p = db.prepareStatement("INSERT INTO league_week VALUES (?, ?, ?)")) {
				p.setLong(1, week);
				p.setLong(2, seed);
				p.setLong(3, now);
				p.executeUpdate();
			}
			return seed;
		});
	}

	/** the entry's state, or null if the player has not started this week */
	String entryState(long week, long player) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT state FROM league_entry WHERE week = ? AND player_id = ?")) {
			p.setLong(1, week);
			p.setLong(2, player);
			try (ResultSet r = p.executeQuery()) {
				return r.next() ? r.getString(1) : null;
			}
		}
	}

	void startEntry(long week, long player, long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("INSERT OR IGNORE INTO league_entry (week, player_id, started, state) VALUES (?, ?, ?, 'STARTED')")) {
			p.setLong(1, week);
			p.setLong(2, player);
			p.setLong(3, now);
			p.executeUpdate();
		}
	}

	/** moves a STARTED entry to VERIFYING with its claim and log; false if it was not STARTED */
	boolean submitEntry(long week, long player, Hub.SideLog s, long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("UPDATE league_entry SET state = 'VERIFYING', hero_class = ?, depth = ?, bosses = ?, turns = ?,"
				+ " dead = ?, cleared = ?, submitted = ?, commands = ? WHERE week = ? AND player_id = ? AND state = 'STARTED'")) {
			p.setString(1, s.heroClass);
			p.setInt(2, s.depth);
			p.setInt(3, s.bossesKilled);
			p.setLong(4, s.turns);
			p.setInt(5, s.dead ? 1 : 0);
			p.setInt(6, s.cleared ? 1 : 0);
			p.setLong(7, now);
			p.setString(8, String.join("\n", s.commands));
			p.setLong(9, week);
			p.setLong(10, player);
			return p.executeUpdate() == 1;
		}
	}

	void judgeEntry(long week, long player, String state, String detail) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("UPDATE league_entry SET state = ?, detail = ? WHERE week = ? AND player_id = ? AND state = 'VERIFYING'")) {
			p.setString(1, state);
			p.setString(2, detail);
			p.setLong(3, week);
			p.setLong(4, player);
			p.executeUpdate();
		}
	}

	/** entries a restart left half-verified, as {week, player} */
	java.util.List<long[]> verifying() throws SQLException {
		java.util.List<long[]> out = new java.util.ArrayList<>();
		try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT week, player_id FROM league_entry WHERE state = 'VERIFYING'")) {
			while (r.next()) out.add(new long[]{r.getLong(1), r.getLong(2)});
		}
		return out;
	}

	/** the stored claim of an entry, for re-verifying after a restart */
	Hub.SideLog entryLog(long week, long player) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT hero_class, commands, depth, bosses, turns, dead, cleared FROM league_entry WHERE week = ? AND player_id = ?")) {
			p.setLong(1, week);
			p.setLong(2, player);
			try (ResultSet r = p.executeQuery()) {
				if (!r.next()) return null;
				String cmds = r.getString(2);
				java.util.List<String> lines = new java.util.ArrayList<>();
				if (cmds != null) for (String l : cmds.split("\n")) if (!l.isEmpty()) lines.add(l);
				return new Hub.SideLog(r.getString(1), lines, r.getInt(3), r.getInt(4), r.getLong(5), r.getInt(6) == 1, r.getInt(7) == 1);
			}
		}
	}

	/** ranking order: final boss cleared, then deepest floor, more bosses, fewer turns, earlier submission */
	private static final String ORDER = " ORDER BY e.cleared DESC, e.depth DESC, e.bosses DESC, e.turns ASC, e.submitted ASC";

	/** accepted entries of a week, best first */
	JsonArray board(long week, int limit) throws SQLException {
		JsonArray out = new JsonArray();
		try (PreparedStatement p = db.prepareStatement("SELECT e.player_id, pl.name, e.hero_class, e.cleared, e.depth, e.bosses, e.turns"
				+ " FROM league_entry e JOIN player pl ON pl.id = e.player_id WHERE e.week = ? AND e.state = 'ACCEPTED'" + ORDER + " LIMIT ?")) {
			p.setLong(1, week);
			p.setInt(2, limit);
			try (ResultSet r = p.executeQuery()) {
				int rank = 0;
				while (r.next()) {
					JsonObject o = entryJson(r);
					o.addProperty("rank", ++rank);
					out.add(o);
				}
			}
		}
		return out;
	}

	/** a player's 1-based rank among accepted entries of a week, or 0 if they have none */
	int rank(long week, long player) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT e.player_id FROM league_entry e WHERE e.week = ? AND e.state = 'ACCEPTED'" + ORDER)) {
			p.setLong(1, week);
			try (ResultSet r = p.executeQuery()) {
				int rank = 0;
				while (r.next()) {
					rank++;
					if (r.getLong(1) == player) return rank;
				}
			}
		}
		return 0;
	}

	int entrants(long week) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT COUNT(*) FROM league_entry WHERE week = ? AND state = 'ACCEPTED'")) {
			p.setLong(1, week);
			try (ResultSet r = p.executeQuery()) {
				r.next();
				return r.getInt(1);
			}
		}
	}

	private static JsonObject entryJson(ResultSet r) throws SQLException {
		JsonObject o = new JsonObject();
		o.addProperty("playerId", r.getLong(1));
		o.addProperty("name", r.getString(2));
		o.addProperty("cls", r.getString(3));
		o.addProperty("cleared", r.getInt(4) == 1);
		o.addProperty("depth", r.getInt(5));
		o.addProperty("bosses", r.getInt(6));
		o.addProperty("turns", r.getLong(7));
		return o;
	}

	// ---- profile

	/** public profile; never the device key. null if the player does not exist */
	JsonObject profile(long player, int season, int recent) throws SQLException {
		JsonObject o = new JsonObject();
		try (PreparedStatement p = db.prepareStatement("SELECT name, created FROM player WHERE id = ?")) {
			p.setLong(1, player);
			try (ResultSet r = p.executeQuery()) {
				if (!r.next()) return null;
				o.addProperty("playerId", player);
				o.addProperty("name", r.getString(1));
				o.addProperty("created", r.getLong(2));
			}
		}
		JsonArray ratings = new JsonArray();
		try (PreparedStatement p = db.prepareStatement("SELECT season, platform, rating, rd, games FROM rating WHERE player_id = ? ORDER BY season DESC, platform")) {
			p.setLong(1, player);
			try (ResultSet r = p.executeQuery()) {
				while (r.next()) {
					JsonObject x = new JsonObject();
					x.addProperty("season", r.getInt(1));
					x.addProperty("platform", r.getString(2));
					x.addProperty("rating", r.getDouble(3));
					x.addProperty("rd", r.getDouble(4));
					x.addProperty("games", r.getInt(5));
					ratings.add(x);
				}
			}
		}
		o.add("ratings", ratings);

		// this season's record, from the player's side of each finished match
		int wins = 0, losses = 0, draws = 0;
		try (PreparedStatement p = db.prepareStatement("SELECT a, outcome FROM match WHERE (a = ? OR b = ?) AND season = ? AND state = 'FINISHED'")) {
			p.setLong(1, player);
			p.setLong(2, player);
			p.setInt(3, season);
			try (ResultSet r = p.executeQuery()) {
				while (r.next()) {
					String res = mine(r.getLong(1) == player, r.getString(2));
					if (res.equals("WIN")) wins++; else if (res.equals("LOSS")) losses++; else draws++;
				}
			}
		}
		o.addProperty("season", season);
		o.addProperty("wins", wins);
		o.addProperty("losses", losses);
		o.addProperty("draws", draws);

		JsonArray matches = new JsonArray();
		try (PreparedStatement p = db.prepareStatement("SELECT m.id, m.a, m.outcome, m.reason, m.ended, m.platform, pa.name, pb.name FROM match m"
				+ " JOIN player pa ON pa.id = m.a JOIN player pb ON pb.id = m.b"
				+ " WHERE (m.a = ? OR m.b = ?) AND m.state = 'FINISHED' ORDER BY m.ended DESC LIMIT ?")) {
			p.setLong(1, player);
			p.setLong(2, player);
			p.setInt(3, recent);
			try (ResultSet r = p.executeQuery()) {
				while (r.next()) {
					boolean isA = r.getLong(2) == player;
					JsonObject x = new JsonObject();
					x.addProperty("matchId", r.getString(1));
					x.addProperty("result", mine(isA, r.getString(3)));
					x.addProperty("reason", r.getString(4));
					x.addProperty("ended", r.getLong(5));
					x.addProperty("platform", r.getString(6));
					x.addProperty("opponent", isA ? r.getString(8) : r.getString(7));
					matches.add(x);
				}
			}
		}
		o.add("recent", matches);

		JsonArray league = new JsonArray();
		try (PreparedStatement p = db.prepareStatement("SELECT week FROM league_entry WHERE player_id = ? AND state = 'ACCEPTED' ORDER BY week DESC LIMIT ?")) {
			p.setLong(1, player);
			p.setInt(2, recent);
			try (ResultSet r = p.executeQuery()) {
				java.util.List<Long> weeks = new java.util.ArrayList<>();
				while (r.next()) weeks.add(r.getLong(1));
				for (long w : weeks) {
					JsonObject x = new JsonObject();
					x.addProperty("week", w);
					x.addProperty("rank", rank(w, player));
					x.addProperty("of", entrants(w));
					league.add(x);
				}
			}
		}
		o.add("league", league);
		return o;
	}

	private static String mine(boolean isA, String outcome) {
		if ("DRAW".equals(outcome)) return "DRAW";
		boolean aWon = "A_WINS".equals(outcome);
		return aWon == isA ? "WIN" : "LOSS";
	}

	/** player id for an exact display name (latest registered wins a tie), or -1 */
	long playerByName(String name) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT id FROM player WHERE name = ? ORDER BY id DESC LIMIT 1")) {
			p.setString(1, name);
			try (ResultSet r = p.executeQuery()) {
				return r.next() ? r.getLong(1) : -1;
			}
		}
	}

	// ---- chat

	void report(long reporter, long target, String text, long now) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("INSERT INTO chat_report VALUES (?, ?, ?, ?)")) {
			p.setLong(1, reporter);
			p.setLong(2, target);
			p.setString(3, text);
			p.setLong(4, now);
			p.executeUpdate();
		}
	}

	/** for the server owner: silence a player until a time */
	void mute(long player, long until, String reason) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("INSERT OR REPLACE INTO chat_mute VALUES (?, ?, ?)")) {
			p.setLong(1, player);
			p.setLong(2, until);
			p.setString(3, reason);
			p.executeUpdate();
		}
	}

	long mutedUntil(long player) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT until FROM chat_mute WHERE player_id = ?")) {
			p.setLong(1, player);
			try (ResultSet r = p.executeQuery()) {
				return r.next() ? r.getLong(1) : 0;
			}
		}
	}

	int reportsAgainst(long target) throws SQLException {
		try (PreparedStatement p = db.prepareStatement("SELECT COUNT(*) FROM chat_report WHERE target = ?")) {
			p.setLong(1, target);
			try (ResultSet r = p.executeQuery()) {
				r.next();
				return r.getInt(1);
			}
		}
	}
}
