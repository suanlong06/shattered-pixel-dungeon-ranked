package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonObject;

/**
 * spd-ranked (A1): one protocol message, {@code {type, seq, matchId, data}}.
 * On the wire it is a 4-byte big-endian length followed by UTF-8 JSON (see FrameCodec).
 */
public final class Frame {

	/** bump on any incompatible change; HELLO with another version is refused */
	public static final int PROTOCOL = 4; // 2: LOG (A3), 3: TELEMETRY (A5), 4: VOTE_REROLL, PAUSE (L6, L19)

	// client -> server
	public static final String HELLO = "HELLO";
	public static final String QUEUE = "QUEUE";
	public static final String LEAVE_QUEUE = "LEAVE_QUEUE";
	public static final String READY = "READY";
	public static final String PROGRESS = "PROGRESS";
	public static final String DIED = "DIED";
	public static final String CLEARED = "CLEARED";
	public static final String RESIGN = "RESIGN";
	/** A3: next part of the command log, {@code {from, cmds, cls}}; see Hub */
	public static final String LOG = "LOG";
	public static final String TELEMETRY = "TELEMETRY";
	public static final String RESUME = "RESUME";
	/** L6: vote for a new seed, {@code {accept}} (default true; false declines the opponent's vote) */
	public static final String VOTE_REROLL = "VOTE_REROLL";
	/** L19: stop this player's own clock, up to the pause budget */
	public static final String PAUSE = "PAUSE";
	public static final String UNPAUSE = "UNPAUSE";
	public static final String PING = "PING";

	// server -> client
	public static final String WELCOME = "WELCOME";
	public static final String QUEUED = "QUEUED";
	public static final String MATCH_FOUND = "MATCH_FOUND";
	public static final String START = "START";
	public static final String OPPONENT = "OPPONENT";
	public static final String RESUMED = "RESUMED";
	public static final String RESULT = "RESULT";
	/** {@code {mine, theirs, voteMs, ended?}}; a passed vote is a new START instead */
	public static final String REROLL_VOTE = "REROLL_VOTE";
	/** {@code {mine, paused, leftMs}} */
	public static final String PAUSED = "PAUSED";
	public static final String PONG = "PONG";
	public static final String ERROR = "ERROR";

	public final String type;
	public final long seq;
	public final String matchId;
	public final JsonObject data;

	public Frame(String type, long seq, String matchId, JsonObject data) {
		this.type = type;
		this.seq = seq;
		this.matchId = matchId;
		this.data = data == null ? new JsonObject() : data;
	}

	public Frame(String type, JsonObject data) {
		this(type, 0, null, data);
	}

	public int i(String key, int def) { return data.has(key) ? data.get(key).getAsInt() : def; }
	public long l(String key, long def) { return data.has(key) ? data.get(key).getAsLong() : def; }
	public String s(String key) { return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : null; }

	@Override
	public String toString() { return type + " " + data; }
}
