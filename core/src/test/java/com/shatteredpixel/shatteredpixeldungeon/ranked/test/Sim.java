package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;

/** Test helper: new run (veteran profile) + runner + input. */
public final class Sim {
	private Sim(){}

	public static TurnRunner start(long seed, HeroClass cls){
		Headless.newRun(seed, cls, Headless.Profile.VETERAN);
		return TurnRunner.startNewGame();
	}

	public static TurnRunner play(long seed, HeroClass cls, TurnRunner.Input in, int maxDecisions){
		TurnRunner r = start(seed, cls);
		r.run(in, maxDecisions);
		return r;
	}
}
