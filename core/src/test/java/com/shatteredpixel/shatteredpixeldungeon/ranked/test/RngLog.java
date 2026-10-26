package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.watabou.utils.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Spike S2: records every draw made through com.watabou.utils.Random, with the nearest
 * caller outside the RNG classes. Installed through Random.factory.
 */
public final class RngLog {

	public static final List<String> lines = new ArrayList<>();
	public static boolean enabled = false;
	/** when true, unseeded generators get a fixed seed (counter), to look past the unseeded base RNG */
	public static boolean seedUnseeded = false;
	private static long unseededCounter;
	private static int genCounter;

	private RngLog(){}

	public static void install(boolean seedUnseeded){
		RngLog.seedUnseeded = seedUnseeded;
		unseededCounter = 0x5EED0000L;
		genCounter = 0;
		lines.clear();
		Random.factory = seed -> {
			int id = genCounter++;
			if (seed == null){
				return RngLog.seedUnseeded ? new Logging(id, "u", unseededCounter++) : new Logging(id, "U");
			}
			return new Logging(id, "s", seed);
		};
		Random.resetGenerators();
	}

	public static void uninstall(){
		Random.factory = null;
		enabled = false;
		Random.resetGenerators();
	}

	public static void mark(String s){
		if (enabled) lines.add("# " + s);
	}

	static String caller(){
		StackTraceElement[] st = new Throwable().getStackTrace();
		for (StackTraceElement e : st){
			String c = e.getClassName();
			if (c.startsWith("java.") || c.equals("com.watabou.utils.Random") || c.equals("com.watabou.utils.StatefulRandom") || c.startsWith(RngLog.class.getName())) continue;
			return c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
		}
		return "?";
	}

	static final class Logging extends com.watabou.utils.StatefulRandom {
		final String tag;

		Logging(int id, String kind){ super(new java.util.Random().nextLong()); tag = kind + id; }
		Logging(int id, String kind, long seed){ super(seed); tag = kind + id; }

		@Override
		protected int next(int bits){
			int v = super.next(bits);
			if (enabled) lines.add(tag + " " + caller() + " " + v);
			return v;
		}
	}
}
