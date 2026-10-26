package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;

/** Spike S5: god-mode bot walks floors 1..maxDepth headless and reports timing. args: seed class maxInputs maxDepth stuckLimit */
public final class S5Main {
	public static void main(String[] args){
		long seed = Long.parseLong(args[0]);
		HeroClass cls = HeroClass.valueOf(args[1]);
		TurnRunner.frameSeconds = 1f / Integer.getInteger("spdr.fps", 30);
		Headless.boot();
		String log = System.getProperty("spdr.rnglog");
		if (log != null){ RngLog.install(false); RngLog.enabled = true; }
		Bot.Scenario sc = new Bot.Scenario(true, Integer.parseInt(args[4]), Integer.parseInt(args[3]));
		long t0 = System.nanoTime();
		TurnRunner r = Sim.play(seed, cls, new Bot(sc), Integer.parseInt(args[2]));
		long total = System.nanoTime() - t0;
		if (log != null){
			RngLog.enabled = false;
			try { java.nio.file.Files.write(java.nio.file.Paths.get(log), RngLog.lines); } catch (java.io.IOException e){ throw new RuntimeException(e); }
		}
		System.out.printf("RESULT seed=%d class=%s floor=%d stop=%s inputs=%d actorSteps=%d frames=%d skipped=%d totalMs=%d usPerInput=%.1f usPerActorStep=%.1f%n",
				seed, cls, Dungeon.depth, r.stop, r.decisions, r.actorSteps, r.frames, sc.skipped,
				total / 1_000_000, total / 1000.0 / Math.max(1, r.decisions), total / 1000.0 / Math.max(1, r.actorSteps));
		System.out.println("TRACE " + r.traceDigest() + " inputs=" + r.trace.size());
		System.exit(0);
	}
}
