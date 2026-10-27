package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.Random;
import com.watabou.utils.StatefulRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T0.8: the deterministic core. Same seed + same commands = same game, on any frame rate, JVM or save/load. */
class DeterministicCoreTest {

	@AfterEach
	void resetFrameRate(){
		TurnRunner.frameSeconds = 1 / 30f;
	}

	// ---------- RNG ----------

	@Test
	void statefulRandomMatchesJavaUtilRandom(){
		for (long seed : new long[]{0, 1, 42, -1, Long.MIN_VALUE, 0x5DEECE66DL}){
			java.util.Random a = new java.util.Random(seed);
			StatefulRandom b = new StatefulRandom(seed);
			for (int i = 0; i < 2000; i++){
				switch (i % 6){
					case 0: assertEquals(a.nextInt(), b.nextInt()); break;
					case 1: assertEquals(a.nextInt(1 + i), b.nextInt(1 + i)); break;
					case 2: assertEquals(a.nextLong(), b.nextLong()); break;
					case 3: assertEquals(Float.floatToRawIntBits(a.nextFloat()), Float.floatToRawIntBits(b.nextFloat())); break;
					case 4: assertEquals(Double.doubleToRawLongBits(a.nextDouble()), Double.doubleToRawLongBits(b.nextDouble())); break;
					default: assertEquals(Double.doubleToRawLongBits(a.nextGaussian()), Double.doubleToRawLongBits(b.nextGaussian()));
				}
			}
		}
	}

	@Test
	void statefulRandomStateRoundTripIncludingCachedGaussian(){
		StatefulRandom a = new StatefulRandom(12345);
		for (int i = 0; i < 37; i++) a.nextInt();
		a.nextGaussian(); // leaves the second gaussian cached
		long[] st = a.getState();
		StatefulRandom b = new StatefulRandom(999);
		b.setState(st);
		for (int i = 0; i < 500; i++){
			assertEquals(Double.doubleToRawLongBits(a.nextGaussian()), Double.doubleToRawLongBits(b.nextGaussian()));
			assertEquals(a.nextLong(), b.nextLong());
		}
	}

	@Test
	void generatorStackStateRoundTrip(){
		Random.resetGenerators(77);
		Random.Int(); Random.Float();
		Random.pushGenerator(5);
		Random.Long();
		long[] st = Random.getStackState();
		int[] expect = new int[50];
		for (int i = 0; i < 25; i++) expect[i] = Random.Int();
		Random.popGenerator();
		for (int i = 25; i < 50; i++) expect[i] = Random.Int();

		Random.resetGenerators(1);
		Random.setStackState(st);
		int[] got = new int[50];
		for (int i = 0; i < 25; i++) got[i] = Random.Int();
		Random.popGenerator();
		for (int i = 25; i < 50; i++) got[i] = Random.Int();
		assertArrayEquals(expect, got);
		Random.resetGenerators();
	}

	// ---------- record / replay ----------

	static final long SEED = 12345L;

	static TurnRunner botRun(long seed, HeroClass cls, boolean god, int stuck, int depth, int inputs){
		Headless.boot();
		return Sim.play(seed, cls, new Bot(new Bot.Scenario(god, stuck, depth)), inputs);
	}

	static TurnRunner replay(long seed, HeroClass cls, List<Command> cmds, boolean god, int stuck, int depth){
		Headless.boot();
		return Sim.play(seed, cls, new Bot.Replay(cmds, new Bot.Scenario(god, stuck, depth)), Integer.MAX_VALUE);
	}

	@Test
	void replayOfRecordedCommandsGivesTheSameGame(){
		TurnRunner a = botRun(SEED, HeroClass.WARRIOR, false, 0, 6, 400);
		assertTrue(a.decisions > 50, "bot made too few inputs: " + a.stop);
		TurnRunner b = replay(SEED, HeroClass.WARRIOR, a.applied, false, 0, 6);
		assertEquals(a.trace, b.trace);
	}

	@Test
	void frameRateDoesNotChangeGameplay(){
		TurnRunner a = botRun(SEED, HeroClass.MAGE, true, 200, 6, 1500);
		TurnRunner.frameSeconds = 1 / 60f;
		TurnRunner b = replay(SEED, HeroClass.MAGE, a.applied, true, 200, 6);
		assertTrue(b.frames != a.frames, "frame count should differ, else the test proves nothing");
		assertEquals(a.traceDigest(), b.traceDigest());
		assertEquals(a.trace, b.trace);
	}

	@Test
	void sameGameInChildJvmWithDegenerateIdentityHashes() throws Exception {
		TurnRunner a = botRun(SEED, HeroClass.HUNTRESS, true, 200, 6, 1500);
		Path cmds = Files.createTempFile("spdr-cmds", ".txt");
		Files.write(cmds, CommandLog.toText(a.applied).getBytes(StandardCharsets.UTF_8));
		String child = childReplay(cmds, SEED, HeroClass.HUNTRESS, "-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=2");
		assertEquals(a.traceDigest(), child);
	}

	static String childReplay(Path cmds, long seed, HeroClass cls, String... jvmArgs) throws Exception {
		List<String> cmd = new ArrayList<>();
		cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		Collections.addAll(cmd, jvmArgs);
		Collections.addAll(cmd, "-cp", System.getProperty("java.class.path"), ReplayMain.class.getName(),
				cmds.toString(), Long.toString(seed), cls.name(), "true", "200", "6");
		Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		String trace = null;
		StringBuilder log = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))){
			String line;
			while ((line = r.readLine()) != null){
				if (line.startsWith("TRACE ")) trace = line.substring(6).trim();
				else log.append(line).append('\n');
			}
		}
		assertEquals(0, proc.waitFor(), log.toString());
		return trace;
	}

	// ---------- save / load (RESUME) ----------

	@Test
	void saveAndResumeMidRunContinuesTheSameGame() throws Exception {
		// god mode, no floor skipping: the bot walks floors 1..4 and stops at the exit of 4
		TurnRunner full = botRun(SEED, HeroClass.ROGUE, true, 0, 4, 2000);
		List<Command> all = full.applied;
		int cut = all.size() / 2;
		assertTrue(cut > 20, full.stop);

		Headless.boot();
		TurnRunner first = Sim.play(SEED, HeroClass.ROGUE, new Bot.Replay(all.subList(0, cut), new Bot.Scenario(true, 0, 4)), cut);
		// the hero waits for input here; save as the game does when it is closed
		Dungeon.saveAll();
		int depthAtSave = Dungeon.depth;

		// "restart the app": fresh RNG stack, then RESUME from the save
		Random.resetGenerators();
		TurnRunner resumed = TurnRunner.resumeSavedGame();
		assertEquals(depthAtSave, Dungeon.depth);
		resumed.run(new Bot.Replay(new ArrayList<>(all.subList(cut, all.size())), new Bot.Scenario(true, 0, 4)), Integer.MAX_VALUE);

		// the resumed game reports the state it was saved in first, then continues
		assertEquals(first.trace.get(first.trace.size() - 1), resumed.trace.get(0));
		List<String> joined = new ArrayList<>(first.trace);
		joined.addAll(resumed.trace.subList(1, resumed.trace.size()));
		assertEquals(full.trace, joined);
	}

	// ---------- recording from the UI paths ----------

	@Test
	void uiInputsAreRecordedAsCommands(){
		Headless.boot();
		TurnRunner r = Sim.start(SEED, HeroClass.WARRIOR);
		r.run(new Bot(new Bot.Scenario(false, 0, 1)), 1); // play until the hero first waits for input
		CommandLog.start();
		int target = Dungeon.hero.pos + 1;
		// as pointer and key events do, one input each time the hero waits; game code is never recorded
		List<Runnable> inputs = Arrays.asList(() -> Dungeon.hero.rest(false), () -> Dungeon.hero.search(true), () -> GameScene.handleCell(target));
		r.live = true;
		r.run(new TurnRunner.Input(){
			int i;
			@Override public Command next(TurnRunner t){
				if (i >= inputs.size()) return null;
				CommandLog.asInput(inputs.get(i++));
				return Command.search();
			}
		}, Integer.MAX_VALUE);
		List<Command> got = CommandLog.stop();
		assertEquals(Arrays.asList(Command.rest(false), Command.search(), Command.cell(target)), got);
		assertEquals(got, CommandLog.fromText(CommandLog.toText(got)));
		assertFalse(CommandLog.recording());
	}
}
