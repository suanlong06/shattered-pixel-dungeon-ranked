/*
 * spd-ranked: deterministic core (T0.8)
 */

package com.watabou.utils;

/**
 * spd-ranked: a java.util.Random whose full state can be read and restored.
 * Uses exactly the same LCG and the same derived methods as java.util.Random, so for a given
 * seed it produces the same sequence (checked by StatefulRandomTest). State = 48-bit seed plus
 * the cached second gaussian, so a save/load in the middle of a run continues the same stream.
 */
public class StatefulRandom extends java.util.Random {

	private static final long MULTIPLIER = 0x5DEECE66DL;
	private static final long ADDEND = 0xBL;
	private static final long MASK = (1L << 48) - 1;

	private long state;
	private boolean haveNextNextGaussian;
	private double nextNextGaussian;

	public StatefulRandom( long seed ){
		super(0);
		setSeed(seed);
	}

	@Override
	public synchronized void setSeed( long seed ){
		state = (seed ^ MULTIPLIER) & MASK;
		haveNextNextGaussian = false;
	}

	@Override
	protected synchronized int next( int bits ){
		state = (state * MULTIPLIER + ADDEND) & MASK;
		return (int)(state >>> (48 - bits));
	}

	// same algorithm as java.util.Random.nextGaussian (which also uses StrictMath)
	@Override
	public synchronized double nextGaussian(){
		if (haveNextNextGaussian) {
			haveNextNextGaussian = false;
			return nextNextGaussian;
		}
		double v1, v2, s;
		do {
			v1 = 2 * nextDouble() - 1;
			v2 = 2 * nextDouble() - 1;
			s = v1 * v1 + v2 * v2;
		} while (s >= 1 || s == 0);
		double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s)/s);
		nextNextGaussian = v2 * multiplier;
		haveNextNextGaussian = true;
		return v1 * multiplier;
	}

	public synchronized long[] getState(){
		return new long[]{ state, haveNextNextGaussian ? 1 : 0, Double.doubleToRawLongBits(nextNextGaussian) };
	}

	public synchronized void setState( long[] s ){
		state = s[0] & MASK;
		haveNextNextGaussian = s[1] != 0;
		nextNextGaussian = Double.longBitsToDouble(s[2]);
	}
}
