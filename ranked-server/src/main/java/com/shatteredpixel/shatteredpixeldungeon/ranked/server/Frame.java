package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonObject;

/**
 * spd-ranked (A1): one protocol message, {@code {type, seq, matchId, data}}.
 * On the wire it is a 4-byte big-endian length followed by UTF-8 JSON (see FrameCodec).
 */
public final class Frame {

	/** bump on any incompatible change; HELLO with another version is refused */
	public static final int PROTOCOL = 2; // 2: LOG (A3)

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
	public static final String RESUME = "RESUME";
	public static final String PING = "PING";
	// C8 (docs/C8-community.md); PROFILE and LEAGUE_BOARD are both the request and the answer
	public static final String LEAGUE_INFO = "LEAGUE_INFO";
	public static final String LEAGUE_START = "LEAGUE_START";
	public static final String LEAGUE_LOG = "LEAGUE_LOG";
	public static final String LEAGUE_SUBMIT = "LEAGUE_SUBMIT";
	public static final String LEAGUE_BOARD = "LEAGUE_BOARD";
	public static final String PROFILE = "PROFILE";
	public static final String CHAT = "CHAT";
	public static final String CHAT_HISTORY = "CHAT_HISTORY";
	public static final String CHAT_REPORT = "CHAT_REPORT";

	// server -> client
	public static final String WELCOME = "WELCOME";
	public static final String QUEUED = "QUEUED";
	public static final String MATCH_FOUND = "MATCH_FOUND";
	public static final String START = "START";
	public static final String OPPONENT = "OPPONENT";
	public static final String RESUMED = "RESUMED";
	public static final String RESULT = "RESULT";
	public static final String PONG = "PONG";
	public static final String ERROR = "ERROR";
	// C8
	public static final String LEAGUE = "LEAGUE";
	public static final String LEAGUE_RESULT = "LEAGUE_RESULT";
	public static final String CHAT_MSG = "CHAT_MSG";
	public static final String CHAT_REPORTED = "CHAT_REPORTED";

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
	public boolean b(String key) { return data.has(key) && data.get(key).getAsBoolean(); }
	public String s(String key) { return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : null; }

	@Override
	public String toString() { return type + " " + data; }
}
