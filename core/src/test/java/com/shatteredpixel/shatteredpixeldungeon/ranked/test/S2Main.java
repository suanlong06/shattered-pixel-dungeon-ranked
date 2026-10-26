package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Spike S2: args seed class turns maxDepth seedUnseeded(true|false) outFile. -Dspdr.fps=N changes the frame step. */
public final class S2Main {
	public static void main(String[] args) throws Exception {
		long seed = Long.parseLong(args[0]);
		HeroClass cls = HeroClass.valueOf(args[1]);
		int turns = Integer.parseInt(args[2]);
		int depth = Integer.parseInt(args[3]);
		TurnRunner.frameSeconds = 1f / Integer.getInteger("spdr.fps", 30);
		Headless.boot();
		RngLog.install(Boolean.parseBoolean(args[4]));
		RngLog.enabled = true;
		long t0 = System.nanoTime();
		TurnRunner r = Sim.play(seed, cls, new Bot(new Bot.Scenario(false, 0, depth)), turns);
		long t = System.nanoTime() - t0;
		RngLog.enabled = false;
		Files.write(Paths.get(args[5]), RngLog.lines, StandardCharsets.UTF_8);
		Files.write(Paths.get(args[5] + ".state"), r.trace, StandardCharsets.UTF_8);
		System.out.println("turns=" + r.decisions + " steps=" + r.actorSteps + " frames=" + r.frames + " stop=" + r.stop
				+ " rng=" + RngLog.lines.size() + " ms=" + t / 1000000 + " trace=" + r.traceDigest());
		System.exit(0);
	}
}
