package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessSmokeTest {

	@Test
	void generatesFirstFifteenFloorsWithoutGraphics(){
		Headless.newRun(12345L, HeroClass.WARRIOR);
		for (int d = 1; d <= 15; d++){
			Level l = Headless.generate(d);
			assertNotNull(l);
			assertTrue(l.length() > 0);
		}
	}
}
