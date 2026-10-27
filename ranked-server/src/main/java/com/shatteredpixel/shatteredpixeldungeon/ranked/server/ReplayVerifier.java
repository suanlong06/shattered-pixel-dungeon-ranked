package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.HeadlessGame;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.ReplayCheck;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import com.watabou.utils.PlatformSupport;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * spd-ranked (A3): {@link Hub.Verifier} that replays each side's command log on the server
 * (T0.8 deterministic core, headless) and compares the result with that side's last report.
 * Spec: docs/A3-replay-verifier.md.
 *
 * For each side, replay must give exactly the reported deepest floor, bosses killed, turns,
 * death and final-boss clear, and must use every command. A difference is REJECT, unless the
 * replay hit something T0.8 cannot follow yet (window choices, falls, teleports between floors,
 * a command that fails), which is UNVERIFIABLE: the hub voids those by default.
 * Forfeit and disconnect verdicts are server facts and are accepted without replay.
 *
 * The game is static state, so replays run one at a time on one thread; the hub is not held
 * while they run.
 */
public final class ReplayVerifier implements Hub.Verifier, AutoCloseable {

	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "replay-verifier");
		t.setDaemon(true);
		return t;
	});

	@Override
	public void verify(Hub.Evidence e, Consumer<Hub.Check> done) {
		if (!e.needsReplay()) {
			done.accept(new Hub.Check(Hub.Result.ACCEPT, null, null));
			return;
		}
		worker.execute(() -> {
			Hub.Check c;
			try {
				c = check(e);
			} catch (Throwable t) {
				c = new Hub.Check(Hub.Result.UNVERIFIABLE, "verifier error: " + t, "verifier error: " + t);
			}
			done.accept(c);
		});
	}

	/** runs on the calling thread */
	public static Hub.Check check(Hub.Evidence e) {
		Hub.Result result = Hub.Result.ACCEPT;
		String[] detail = new String[2];
		for (int i = 0; i < 2; i++) {
			Hub.SideLog s = e.sides[i];
			Hub.Result r;
			HeroClass cls = heroClass(s.heroClass);
			List<Command> cmds = cls == null ? null : parse(s.commands);
			if (cls == null || cmds == null) {
				r = Hub.Result.REJECT;
				detail[i] = cls == null ? "bad hero class " + s.heroClass : "bad command in log";
			} else {
				ReplayCheck.Facts f = replay(e.seed, cls, cmds);
				String diff = compare(s, f, cmds.size(), e.rules.finalBossDepth);
				r = diff == null ? Hub.Result.ACCEPT : f.unsupported != null ? Hub.Result.UNVERIFIABLE : Hub.Result.REJECT;
				detail[i] = (diff == null ? "ok" : diff) + "; replay " + f;
			}
			result = worse(result, r);
		}
		return new Hub.Check(result, detail[0], detail[1]);
	}

	/** null if the replay agrees with the report, else the first difference */
	static String compare(Hub.SideLog s, ReplayCheck.Facts f, int commands, int finalBossDepth) {
		if (f.commandsApplied != commands) return "replay used " + f.commandsApplied + " of " + commands + " commands";
		if (f.died != s.dead) return "reported " + (s.dead ? "dead" : "alive") + ", replay " + (f.died ? "dead" : "alive");
		if (f.cleared(finalBossDepth) != s.cleared) return "reported clear " + s.cleared + ", replay " + f.cleared(finalBossDepth);
		if (f.depth != s.depth) return "reported depth " + s.depth + ", replay " + f.depth;
		if (f.bossesKilled(finalBossDepth) != s.bossesKilled) return "reported bosses " + s.bossesKilled + ", replay " + f.bossesKilled(finalBossDepth);
		if (f.turns != s.turns) return "reported turns " + s.turns + ", replay " + f.turns;
		return null;
	}

	/** REJECT over UNVERIFIABLE over ACCEPT */
	private static Hub.Result worse(Hub.Result a, Hub.Result b) {
		if (a == Hub.Result.REJECT || b == Hub.Result.REJECT) return Hub.Result.REJECT;
		if (a == Hub.Result.UNVERIFIABLE || b == Hub.Result.UNVERIFIABLE) return Hub.Result.UNVERIFIABLE;
		return Hub.Result.ACCEPT;
	}

	private static HeroClass heroClass(String name) {
		if (name == null) return null;
		try {
			return HeroClass.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static List<Command> parse(List<String> lines) {
		List<Command> out = new ArrayList<>(lines.size());
		try {
			for (String l : lines) out.add(Command.parse(l));
		} catch (RuntimeException e) {
			return null;
		}
		return out;
	}

	public static synchronized ReplayCheck.Facts replay(long seed, HeroClass cls, List<Command> cmds) {
		boot();
		return ReplayCheck.replay(seed, cls, cmds);
	}

	private static boolean booted;

	/** libGDX headless backend, no GL; empty prefs and save directories so no profile leaks in */
	static synchronized void boot() {
		if (booted) return;
		try {
			// a fresh profile each start: nothing from earlier runs (badges, journal) may reach a replay
			File home = new File(System.getProperty("user.home")).getCanonicalFile();
			File base = new File(home, ".spdr-server");
			base.mkdirs();
			File prefs = Files.createTempDirectory(base.toPath(), "prefs").toFile();
			prefs.deleteOnExit();
			HeadlessApplicationConfiguration cfg = new HeadlessApplicationConfiguration();
			// headless prefs are resolved against user.home
			cfg.preferencesDirectory = ".spdr-server" + File.separator + prefs.getName() + File.separator;
			cfg.updatesPerSecond = -1;
			new HeadlessApplication(new ApplicationAdapter() {}, cfg);
			File saves = Files.createTempDirectory("spdr-replay").toFile();
			FileUtils.setDefaultFileProperties(com.badlogic.gdx.Files.FileType.Absolute, saves.getAbsolutePath() + File.separator);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		HeadlessGame.install(new NoTextPlatform());
		Game.version = "4.0.2-server";
		Game.versionCode = 922;
		booted = true;
	}

	@Override
	public void close() {
		worker.shutdownNow();
	}

	/** text is never drawn on the server */
	static final class NoTextPlatform extends PlatformSupport {
		@Override public void updateDisplaySize() {}
		@Override public void updateSystemUI() {}
		@Override public boolean connectedToUnmeteredNetwork() { return false; }
		@Override public boolean supportsVibration() { return false; }
		@Override public void setupFontGenerators(int pageSize, boolean systemFont) {}
		@Override protected FreeTypeFontGenerator getGeneratorForString(String input) { return null; }
		@Override public String[] splitforTextBlock(String text, boolean multiline) {
			return multiline ? text.split("(?<=\n)|(?=\n)| ") : text.split(" ");
		}
	}
}
