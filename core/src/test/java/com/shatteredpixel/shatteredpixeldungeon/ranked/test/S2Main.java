package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Spike S2: args seed class turns maxDepth seedUnseeded(true|false) outFile */
public final class S2Main {
	public static void main(String[] args) throws Exception {
		long seed = Long.parseLong(args[0]);
		HeroClass cls = HeroClass.valueOf(args[1]);
		int turns = Integer.parseInt(args[2]);
		int depth = Integer.parseInt(args[3]);
		Headless.boot();
		RngLog.install(Boolean.parseBoolean(args[4]));
		RngLog.enabled = true;
		TurnSim.Result r = TurnSim.run(seed, cls, turns, depth);
		RngLog.enabled = false;
		Files.write(Paths.get(args[5]), RngLog.lines, StandardCharsets.UTF_8);
		Files.write(Paths.get(args[5] + ".state"), r.turnDigests, StandardCharsets.UTF_8);
		System.out.println("turns=" + r.heroTurns + " steps=" + r.actorSteps + " floor=" + r.floorsReached + " stop=" + r.stop
				+ " rng=" + RngLog.lines.size() + " genMs=" + r.genNanos / 1000000 + " simMs=" + r.simNanos / 1000000);
		System.exit(0);
	}
}
