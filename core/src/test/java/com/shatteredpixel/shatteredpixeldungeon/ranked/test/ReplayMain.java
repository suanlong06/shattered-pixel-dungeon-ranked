package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Replays a command file in this JVM and prints the trace digest. args: cmdFile seed class godMode stuckLimit maxDepth */
public final class ReplayMain {
	public static void main(String[] a) throws Exception {
		String text = new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8);
		Headless.boot();
		TurnRunner r = Sim.play(Long.parseLong(a[1]), HeroClass.valueOf(a[2]),
				new Bot.Replay(CommandLog.fromText(text), new Bot.Scenario(Boolean.parseBoolean(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]))),
				Integer.MAX_VALUE);
		System.out.println("TRACE " + r.traceDigest());
		System.exit(0);
	}
}
