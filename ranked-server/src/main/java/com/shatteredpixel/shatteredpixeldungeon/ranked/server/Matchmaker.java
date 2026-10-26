package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * spd-ranked (A1): the ranked queue. Players only meet others on the same platform (L3:
 * one ladder per input platform until there is data). The accepted rating gap widens the
 * longer a player waits; a pair is made when the gap fits both players' windows.
 * Pure and ordered: same calls, same pairs.
 */
public final class Matchmaker {

	public static final double BASE_WINDOW = 100;
	public static final double WIDEN_PER_SEC = 10;
	public static final double MAX_WINDOW = 500;

	public static final class Entry {
		public final long playerId;
		public final String platform;
		public final double rating;
		public final long since;

		Entry(long playerId, String platform, double rating, long since) {
			this.playerId = playerId;
			this.platform = platform;
			this.rating = rating;
			this.since = since;
		}

		double window(long now) {
			return Math.min(MAX_WINDOW, BASE_WINDOW + WIDEN_PER_SEC * Math.max(0, now - since) / 1000.0);
		}
	}

	private final Map<Long, Entry> queue = new LinkedHashMap<>(); // insertion order = queue order

	public boolean add(long playerId, String platform, double rating, long now) {
		if (queue.containsKey(playerId)) return false;
		queue.put(playerId, new Entry(playerId, platform, rating, now));
		return true;
	}

	public boolean remove(long playerId) { return queue.remove(playerId) != null; }

	public boolean contains(long playerId) { return queue.containsKey(playerId); }

	public int size() { return queue.size(); }

	/** pairs everyone who can be paired now, oldest waiting first, closest rating as partner */
	public List<Entry[]> pair(long now) {
		List<Entry[]> out = new ArrayList<>();
		List<Entry> waiting = new ArrayList<>(queue.values());
		boolean[] taken = new boolean[waiting.size()];
		for (int i = 0; i < waiting.size(); i++) {
			if (taken[i]) continue;
			Entry a = waiting.get(i);
			int best = -1;
			double bestGap = 0;
			for (int j = i + 1; j < waiting.size(); j++) {
				if (taken[j]) continue;
				Entry b = waiting.get(j);
				if (!a.platform.equals(b.platform)) continue;
				double gap = Math.abs(a.rating - b.rating);
				if (gap > a.window(now) || gap > b.window(now)) continue;
				if (best < 0 || gap < bestGap) {
					best = j;
					bestGap = gap;
				}
			}
			if (best >= 0) {
				taken[i] = taken[best] = true;
				Entry b = waiting.get(best);
				queue.remove(a.playerId);
				queue.remove(b.playerId);
				out.add(new Entry[]{a, b});
			}
		}
		return out;
	}
}
