package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;

/** Spike S5: bot walks floors 1..maxDepth headless and reports timing. args: seed class maxTurns maxDepth stuckLimit */
public final class S5Main {
	public static void main(String[] args){
		long seed = Long.parseLong(args[0]);
		HeroClass cls = HeroClass.valueOf(args[1]);
		TurnSim.godMode = true;
		TurnSim.stuckLimit = Integer.parseInt(args[4]);
		Headless.boot();
		String log = System.getProperty("spdr.rnglog");
		if (log != null){ RngLog.install(false); RngLog.enabled = true; }
		long t0 = System.nanoTime();
		TurnSim.Result r = TurnSim.run(seed, cls, Integer.parseInt(args[2]), Integer.parseInt(args[3]));
		long total = System.nanoTime() - t0;
		if (log != null){
			RngLog.enabled = false;
			try { java.nio.file.Files.write(java.nio.file.Paths.get(log), RngLog.lines); } catch (java.io.IOException e){ throw new RuntimeException(e); }
		}
		for (String f : r.floors) System.out.println("FLOOR " + f);
		System.out.printf("RESULT seed=%d class=%s floor=%d stop=%s heroTurns=%d actorSteps=%d frames=%d genMs=%d simMs=%d totalMs=%d usPerHeroTurn=%.1f usPerActorStep=%.1f%n",
				seed, cls, r.floorsReached, r.stop, r.heroTurns, r.actorSteps, TurnSim.frames, r.genNanos / 1_000_000, r.simNanos / 1_000_000,
				total / 1_000_000, r.simNanos / 1000.0 / Math.max(1, r.heroTurns), r.simNanos / 1000.0 / Math.max(1, r.actorSteps));
		System.out.println("TRACE " + Digest.sha256(String.join("\n", r.turnDigests)).substring(0, 16) + " turns=" + r.turnDigests.size());
		System.exit(0);
	}
}
