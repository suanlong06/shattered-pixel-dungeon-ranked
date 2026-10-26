package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Canonical (key-sorted) JSON of a Level's save bundle, and its SHA-256. */
public final class Digest {

	private Digest(){}

	/**
	 * Level fields stored from unordered collections (HashSet of mobs, SparseArray of heaps...).
	 * Their order is sorted before hashing so the digest compares content; the order itself is
	 * checked separately (see {@link #canonicalJson(Level, Set, boolean)}).
	 */
	public static final Set<String> UNORDERED = new HashSet<>(Arrays.asList("mobs", "heaps", "blobs", "plants", "traps"));

	public static String canonicalJson(Level level, Set<String> ignoredKeys){
		return canonicalJson(level, ignoredKeys, true);
	}

	public static String canonicalJson(Level level, Set<String> ignoredKeys, boolean sortUnordered){
		Bundle b = new Bundle();
		b.put("level", level);
		Object json = new JSONObject(b.toString());
		StringBuilder sb = new StringBuilder();
		write(json, ignoredKeys, sortUnordered, sb);
		return sb.toString();
	}

	public static String of(Level level, Set<String> ignoredKeys){
		return sha256(canonicalJson(level, ignoredKeys, true));
	}

	public static String ofRawOrder(Level level, Set<String> ignoredKeys){
		return sha256(canonicalJson(level, ignoredKeys, false));
	}

	public static String sha256(String s){
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i]));
			return sb.toString();
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	private static void write(Object o, Set<String> ignored, boolean sort, StringBuilder sb){
		if (o instanceof JSONObject){
			JSONObject j = (JSONObject) o;
			List<String> keys = new ArrayList<>();
			for (Iterator<String> it = j.keys(); it.hasNext();) keys.add(it.next());
			Collections.sort(keys);
			sb.append('{');
			boolean first = true;
			for (String k : keys){
				if (ignored.contains(k)) continue;
				if (!first) sb.append(',');
				first = false;
				sb.append(JSONObject.quote(k)).append(':');
				Object v = j.get(k);
				if (sort && v instanceof JSONArray && UNORDERED.contains(k)){
					List<String> parts = new ArrayList<>();
					JSONArray a = (JSONArray) v;
					for (int i = 0; i < a.length(); i++){
						StringBuilder e = new StringBuilder();
						write(a.get(i), ignored, sort, e);
						parts.add(e.toString());
					}
					Collections.sort(parts);
					sb.append('[').append(String.join(",", parts)).append(']');
				} else {
					write(v, ignored, sort, sb);
				}
			}
			sb.append('}');
		} else if (o instanceof JSONArray){
			JSONArray a = (JSONArray) o;
			sb.append('[');
			for (int i = 0; i < a.length(); i++){
				if (i > 0) sb.append(',');
				write(a.get(i), ignored, sort, sb);
			}
			sb.append(']');
		} else if (o instanceof String){
			sb.append(JSONObject.quote((String) o));
		} else {
			sb.append(String.valueOf(o));
		}
	}
}
