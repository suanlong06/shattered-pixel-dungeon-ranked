package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.journal.Document;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;

import java.io.File;
import java.nio.file.Files;

/** Boots just enough of libGDX (headless backend, no GL) to run dungeon logic. */
public final class Headless {

	private static boolean started;

	private Headless(){}

	public static synchronized void boot(){
		if (started) return;
		try {
			File prefs = Files.createTempDirectory("spdr-prefs").toFile();
			HeadlessApplicationConfiguration cfg = new HeadlessApplicationConfiguration();
			cfg.preferencesDirectory = prefs.getAbsolutePath() + File.separator;
			cfg.updatesPerSecond = -1; // no render loop
			new HeadlessApplication(new ApplicationAdapter(){}, cfg);
			// fresh, empty save/profile directory: no badges, no bones, no catalog
			File saves = Files.createTempDirectory("spdr-saves").toFile();
			FileUtils.setDefaultFileProperties(com.badlogic.gdx.Files.FileType.Absolute, saves.getAbsolutePath() + File.separator);
		} catch (Exception e){
			throw new RuntimeException(e);
		}
		Game.version = "4.0.2-test";
		Game.versionCode = 922;
		started = true;
	}

	/** Player-profile state that SPD reads during level generation (DECISIONS F17). */
	public enum Profile {
		/** first launch: intro on, no guidebook pages */
		FRESH,
		/** intro done, every adventurer's guide page found and read */
		VETERAN
	}

	public static void applyProfile(Profile p){
		SPDSettings.intro(p == Profile.FRESH);
		for (String page : Document.ADVENTURERS_GUIDE.pageNames()){
			if (p == Profile.FRESH){
				Document.ADVENTURERS_GUIDE.deletePage(page);
			} else {
				Document.ADVENTURERS_GUIDE.findPage(page);
				Document.ADVENTURERS_GUIDE.readPage(page);
			}
		}
	}

	public static void newRun(long seed, HeroClass cls){
		newRun(seed, cls, Profile.FRESH);
	}

	/** Starts a fresh run exactly like InterlevelScene.descend() does for a new game. */
	public static void newRun(long seed, HeroClass cls, Profile profile){
		boot();
		applyProfile(profile);
		GamesInProgress.selectedClass = cls;
		GamesInProgress.curSlot = 1;
		Dungeon.daily = Dungeon.dailyReplay = false;
		Dungeon.customSeedText = "";
		Dungeon.seed = seed;
		Dungeon.hero = null;
		Dungeon.init();
	}

	public static Level generate(int depth){
		Dungeon.depth = depth;
		Dungeon.branch = 0;
		Dungeon.level = Dungeon.newLevel();
		return Dungeon.level;
	}
}
