package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.test.LevelGenRun.Perturb;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Spike S1 (T0.6): same seed must give the same floors 1..15, in this JVM and in fresh JVMs,
 * and gameplay between floors must not change later floors (DECISIONS F14/F19).
 *
 * Tagged "determinism": these fail on unmodified SPD v4.0.2 (see docs/spikes/S1-levelgen.md)
 * and run with `./gradlew :core:determinismTest`. They join the default suite once T0.8 lands.
 */
@Tag("determinism")
class LevelGenDeterminismTest {

	static final int MAX = 15;
	static final long[] SEEDS = {12345L, 987654321L, 4242424242L, 7L, 31337L};
	static final HeroClass[] CLASSES = {HeroClass.WARRIOR, HeroClass.CLERIC};

	static List<String> childJvm(long seed, HeroClass cls, Perturb p, String... jvmArgs) throws Exception {
		List<String> cmd = new ArrayList<>();
		cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		Collections.addAll(cmd, jvmArgs);
		cmd.add("-cp");
		cmd.add(System.getProperty("java.class.path"));
		cmd.add(LevelGenRun.class.getName());
		cmd.add(Long.toString(seed));
		cmd.add(cls.name());
		cmd.add(Integer.toString(MAX));
		cmd.add(p.name());
		Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		List<String> out = new ArrayList<>();
		StringBuilder log = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))){
			String line;
			while ((line = r.readLine()) != null){
				if (line.startsWith("DIGEST ")) out.add(line.substring(7));
				else log.append(line).append('\n');
			}
		}
		assertEquals(0, proc.waitFor(), log.toString());
		return out;
	}

	static String report(String title, List<String> a, List<String> b){
		StringBuilder sb = new StringBuilder(title).append('\n');
		for (int i = 0; i < Math.max(a.size(), b.size()); i++){
			String x = i < a.size() ? a.get(i) : "-", y = i < b.size() ? b.get(i) : "-";
			sb.append(String.format("  depth %2d  %s  %s  %s%n", i+1, x, y, x.equals(y) ? "" : "DIFF"));
		}
		return sb.toString();
	}

	static List<String> run(long seed, HeroClass cls, Perturb p){
		return LevelGenRun.run(seed, cls, MAX, p, LevelGenRun.IGNORE_IDS);
	}

	@Test
	void sameSeedSameFloorsInOneJvm(){
		for (long seed : SEEDS) for (HeroClass cls : CLASSES){
			List<String> a = run(seed, cls, Perturb.NONE), b = run(seed, cls, Perturb.NONE);
			assertEquals(a, b, report("same JVM, seed " + seed + " " + cls, a, b));
		}
	}

	@Test
	void sameSeedSameFloorsAcrossJvms() throws Exception {
		for (long seed : SEEDS) for (HeroClass cls : CLASSES){
			List<String> here = run(seed, cls, Perturb.NONE);
			List<String> other = childJvm(seed, cls, Perturb.NONE);
			assertEquals(here, other, report("child JVM, seed " + seed + " " + cls, here, other));
			// constant identity hash codes: exposes any iteration over identity-hashed sets/maps
			List<String> hash = childJvm(seed, cls, Perturb.NONE, "-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=2");
			assertEquals(here, hash, report("child JVM -XX:hashCode=2, seed " + seed + " " + cls, here, hash));
		}
	}

	/** F14: mob loot (non-Thief) and grass drops do not touch decks shared with levelgen. */
	@Test
	void mobLootAndGrassDoNotChangeLaterFloors(){
		for (long seed : SEEDS){
			List<String> base = run(seed, HeroClass.WARRIOR, Perturb.NONE);
			for (Perturb p : new Perturb[]{Perturb.MOB_LOOT, Perturb.GRASS}){
				List<String> o = run(seed, HeroClass.WARRIOR, p);
				assertEquals(base, o, report(p + ", seed " + seed, base, o));
			}
		}
	}

	/**
	 * F19 + new finding: Thief loot, Ring of Wealth, Transmutation draw the ARTIFACT deck that
	 * levelgen also uses, and holding Exotic Crystals changes levelgen. Characterises the current
	 * behaviour; T0.7 must turn these into "no change" for Ranked.
	 */
	@Test
	void sharedDeckDrawsDoChangeLaterFloors(){
		for (Perturb p : new Perturb[]{Perturb.THIEF_LOOT, Perturb.WEALTH, Perturb.TRANSMUTE, Perturb.EXOTIC_CRYSTALS}){
			boolean changed = false;
			for (long seed : SEEDS){
				changed |= !run(seed, HeroClass.WARRIOR, Perturb.NONE).equals(run(seed, HeroClass.WARRIOR, p));
			}
			assertNotEquals(false, changed, p + " was expected to change at least one later floor");
		}
	}
}
