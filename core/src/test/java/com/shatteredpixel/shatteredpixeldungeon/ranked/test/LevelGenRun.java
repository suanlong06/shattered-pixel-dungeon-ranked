package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTransmutation;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.ExoticCrystals;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.RatSkull;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spike S1: generate floors 1..N for a seed and digest each one, optionally applying a
 * gameplay-like "perturbation" between floors. Also runnable as a main() in a child JVM.
 */
public final class LevelGenRun {

	/**
	 * "id": actor ids count every actor ever created, not part of level content.
	 * "set_id": MissileWeapon.setID comes from SecureRandom (stacking token, never seeded).
	 */
	public static final Set<String> IGNORE_IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("id", "set_id")));

	public enum Perturb {
		NONE,
		/** every mob on the floor except Thieves rolls its loot (Mob.createLoot) */
		MOB_LOOT,
		/** a Thief rolls its loot: RING or ARTIFACT, and the ARTIFACT deck is shared with levelgen */
		THIEF_LOOT,
		/** what trampling grass draws: SEED deck + default STONE */
		GRASS,
		/** Ring of Wealth equipment drop: ARTIFACT deck + default RING */
		WEALTH,
		/** Scroll of Transmutation on an artifact and a trinket */
		TRANSMUTE,
		/** hero carries the Exotic Crystals trinket from the start */
		EXOTIC_CRYSTALS
	}

	private LevelGenRun(){}

	public static List<String> run(long seed, HeroClass cls, int maxDepth, Perturb p, Set<String> ignored){
		return run(seed, cls, Headless.Profile.VETERAN, maxDepth, p, ignored);
	}

	public static List<String> run(long seed, HeroClass cls, Headless.Profile profile, int maxDepth, Perturb p, Set<String> ignored){
		Headless.newRun(seed, cls, profile);
		if (p == Perturb.EXOTIC_CRYSTALS){
			ExoticCrystals t = new ExoticCrystals();
			t.level(3);
			Dungeon.hero.belongings.backpack.items.add(t);
		}
		List<String> out = new ArrayList<>();
		String dumpDir = System.getProperty("spdr.dump");
		for (int d = 1; d <= maxDepth; d++){
			Level l = Headless.generate(d);
			out.add(Digest.of(l, ignored));
			if (dumpDir != null){
				try {
					java.nio.file.Path f = java.nio.file.Paths.get(dumpDir, seed + "_" + cls + "_" + p + "_" + d + ".json");
					java.nio.file.Files.createDirectories(f.getParent());
					java.nio.file.Files.write(f, Digest.canonicalJson(l, ignored).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				} catch (java.io.IOException e){
					throw new RuntimeException(e);
				}
			}
			perturb(p, l);
		}
		return out;
	}

	private static void perturb(Perturb p, Level l){
		switch (p){
			case MOB_LOOT:
				for (Mob m : l.mobs.toArray(new Mob[0])) if (!(m instanceof Thief)) m.createLoot();
				break;
			case THIEF_LOOT:
				for (int i = 0; i < 2; i++){
					Thief t = new Thief();
					setLoot(t, Generator.Category.ARTIFACT); // Thief picks RING or ARTIFACT at random
					t.createLoot();
				}
				break;
			case GRASS:
				for (int i = 0; i < 20; i++) Generator.random(Generator.Category.SEED);
				for (int i = 0; i < 3; i++) Generator.randomUsingDefaults(Generator.Category.STONE);
				break;
			case WEALTH:
				Generator.random(Generator.Category.ARTIFACT);
				Generator.randomUsingDefaults(Generator.Category.RING);
				break;
			case TRANSMUTE:
				ScrollOfTransmutation.changeItem(new ChaliceOfBlood());
				ScrollOfTransmutation.changeItem(new RatSkull());
				break;
			default:
		}
	}

	private static void setLoot(Mob m, Object loot){
		try {
			java.lang.reflect.Field f = Mob.class.getDeclaredField("loot");
			f.setAccessible(true);
			f.set(m, loot);
		} catch (ReflectiveOperationException e){
			throw new RuntimeException(e);
		}
	}

	/** args: seed class maxDepth perturb [raw] */
	public static void main(String[] args){
		long seed = Long.parseLong(args[0]);
		HeroClass cls = HeroClass.valueOf(args[1]);
		int max = Integer.parseInt(args[2]);
		Perturb p = Perturb.valueOf(args[3]);
		Set<String> ignored = args.length > 4 && args[4].equals("raw") ? Collections.<String>emptySet() : IGNORE_IDS;
		for (String s : run(seed, cls, max, p, ignored)) System.out.println("DIGEST " + s);
		System.exit(0);
	}
}
