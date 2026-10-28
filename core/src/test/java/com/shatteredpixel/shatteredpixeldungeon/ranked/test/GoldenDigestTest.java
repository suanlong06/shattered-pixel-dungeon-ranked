package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * T0.8 golden digests: fixed seeds must give exactly these floors and these games, on every
 * platform (CI Linux, Windows, ART/aarch64) and after every upstream merge. A change here means
 * gameplay changed: check why before regenerating with
 *   ./gradlew :core:test --tests '*GoldenDigestTest' -Dspdr.golden.update=core/src/test/resources/ranked/golden-digests.txt
 * (the property is passed through to the test JVM by core/build.gradle).
 */
class GoldenDigestTest {

	static final long[] LEVEL_SEEDS = {12345L, 987654321L};
	static final HeroClass[] GAME_CLASSES = {HeroClass.WARRIOR, HeroClass.CLERIC};

	static List<String> compute(){
		List<String> out = new ArrayList<>();
		for (long seed : LEVEL_SEEDS){
			List<String> d = LevelGenRun.run(seed, HeroClass.WARRIOR, 15, LevelGenRun.Perturb.NONE, LevelGenRun.IGNORE_IDS);
			for (int i = 0; i < d.size(); i++) out.add("level " + seed + " WARRIOR " + (i + 1) + " " + d.get(i));
		}
		for (HeroClass cls : GAME_CLASSES){
			Headless.boot();
			TurnRunner r = Sim.play(12345L, cls, new Bot(new Bot.Scenario(true, 200, 8)), 2500);
			out.add("game 12345 " + cls + " inputs=" + r.decisions + " steps=" + r.actorSteps + " " + r.traceDigest());
		}
		// floors 1 to 16 through Goo, Tengu and DM-300 (BossBot, no scenario rules)
		BossBotTest.Run boss;
		try {
			boss = BossBotTest.live( 12345L, HeroClass.WARRIOR );
		} finally {
			com.shatteredpixel.shatteredpixeldungeon.ranked.Features.setMode( com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode.SOLO );
		}
		out.add( "bosses 12345 WARRIOR inputs=" + boss.cmds.size() + " turns=" + boss.turns + " " + boss.trace );
		return out;
	}

	@Test
	void goldenDigestsMatch() throws IOException {
		List<String> got = compute();
		String update = System.getProperty("spdr.golden.update");
		if (update != null && !update.isEmpty()){
			Files.write(Paths.get(update), (String.join("\n", got) + "\n").getBytes(StandardCharsets.UTF_8));
			return;
		}
		String expected;
		try (InputStream in = GoldenDigestTest.class.getResourceAsStream("/ranked/golden-digests.txt")){
			assertNotNull(in, "golden file missing; generate it (see class comment)");
			expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r", "").trim();
		}
		assertEquals(expected, String.join("\n", got));
	}
}
