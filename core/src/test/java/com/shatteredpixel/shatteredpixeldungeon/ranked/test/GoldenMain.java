package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

/** Prints the golden digests computed on this machine (for devices without a test runner, e.g. the A53). */
public final class GoldenMain {
	public static void main(String[] a){
		long t0 = System.nanoTime();
		for (String s : GoldenDigestTest.compute()) System.out.println("GOLDEN " + s);
		System.out.println("ms=" + (System.nanoTime() - t0) / 1_000_000);
		System.exit(0);
	}
}
