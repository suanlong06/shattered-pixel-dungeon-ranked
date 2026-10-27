package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shatteredpixel.shatteredpixeldungeon.ranked.net.ProgressTracker.Report;
import org.junit.jupiter.api.Test;

class ProgressTrackerTest {

	@Test
	void reportsOnlyChanges(){
		ProgressTracker t = new ProgressTracker( 15 );
		int[] scores = new int[5];
		assertEquals( Report.PROGRESS, t.update( 1, 0, scores, true ), "first report");
		assertEquals( Report.NONE, t.update( 1, 50, scores, true ) );
		assertEquals( Report.PROGRESS, t.update( 2, 80, scores, true ) );
		assertEquals( 2, t.depth() );
		assertEquals( 80, t.turns() );
	}

	@Test
	void bossByDepthAndByScore(){
		ProgressTracker t = new ProgressTracker( 15 );
		int[] scores = new int[5];
		t.update( 5, 10, scores, true );
		scores[0] -= 300; // hits taken in the Goo fight
		assertEquals( Report.NONE, t.update( 5, 20, scores, true ) );
		scores[0] += 1000; // Goo dies
		assertEquals( Report.PROGRESS, t.update( 5, 30, scores, true ) );
		assertEquals( 1, t.bosses() );
		assertEquals( Report.PROGRESS, t.update( 11, 40, scores, true ) );
		assertEquals( 2, t.bosses(), "Tengu counted by getting past floor 10" );
	}

	@Test
	void clearedOnFinalBossOnly(){
		ProgressTracker t = new ProgressTracker( 15 );
		int[] scores = new int[5];
		t.update( 15, 100, scores, true );
		assertEquals( 2, t.bosses() );
		scores[2] += 3000;
		assertEquals( Report.CLEARED, t.update( 15, 120, scores, true ) );
		assertEquals( 3, t.bosses() );
		assertEquals( Report.NONE, t.update( 15, 130, scores, true ), "nothing after the end" );
	}

	@Test
	void deathEndsAndNeverGoesBackwards(){
		ProgressTracker t = new ProgressTracker( 15 );
		int[] scores = new int[5];
		t.update( 7, 100, scores, true );
		assertEquals( Report.DIED, t.update( 6, 90, scores, false ) );
		assertEquals( 7, t.depth() );
		assertEquals( 100, t.turns() );
		assertEquals( Report.NONE, t.update( 8, 200, scores, false ) );
	}

	@Test
	void depthCappedAtFinalBoss(){
		ProgressTracker t = new ProgressTracker( 15 );
		t.update( 17, 0, new int[5], true );
		assertEquals( 15, t.depth() );
	}
}
