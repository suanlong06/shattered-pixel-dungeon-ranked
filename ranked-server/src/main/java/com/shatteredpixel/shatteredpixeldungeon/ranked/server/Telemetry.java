package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import java.util.regex.Pattern;

/**
 * A5: checks a client's telemetry body (core ranked/telemetry/Telemetry, "key=value" lines).
 * The body is data from the client: it is only stored, never trusted for match results.
 */
public final class Telemetry {

	private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.:-]{1,96}");
	private static final int MAX_VALUE = 256;

	private Telemetry() {}

	/** the body with blank lines dropped, or null if too long, not v1, or a line is malformed */
	public static String clean(String body, int maxChars) {
		if (body == null || body.length() > maxChars) return null;
		StringBuilder out = new StringBuilder();
		boolean first = true;
		for (String raw : body.split("\n")) {
			String line = raw.trim();
			if (line.isEmpty()) continue;
			int eq = line.indexOf('=');
			if (eq <= 0) return null;
			String k = line.substring(0, eq), v = line.substring(eq + 1);
			if (!KEY.matcher(k).matches() || v.length() > MAX_VALUE || v.indexOf('\r') >= 0) return null;
			if (first && !(k.equals("telemetry") && v.startsWith("v"))) return null;
			first = false;
			out.append(line).append('\n');
		}
		return first ? null : out.toString();
	}
}
