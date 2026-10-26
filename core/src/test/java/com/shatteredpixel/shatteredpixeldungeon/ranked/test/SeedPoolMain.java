package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.ranked.seed.SeedPoolFile;
import com.shatteredpixel.shatteredpixeldungeon.ranked.seed.SeedScorer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A4 batch tool: scans candidate seeds and writes the pool file.
 * Args: first candidate, count, step, output file. Candidates are first + i*step;
 * the caller picks them (and keeps the output secret).
 */
public final class SeedPoolMain {

	public static void main(String[] args) throws Exception {
		long first = Long.parseLong(args[0]);
		int count = Integer.parseInt(args[1]);
		long step = Long.parseLong(args[2]);
		Headless.boot();
		long[] candidates = new long[count];
		for (int i = 0; i < count; i++) candidates[i] = first + i * step;
		String text = SeedPoolFile.write(SeedScorer.pool(candidates));
		Files.write(Paths.get(args[3]), text.getBytes(StandardCharsets.UTF_8));
		System.out.println(SeedPoolFile.read(text).size() + " of " + count + " seeds in pool -> " + args[3]);
		System.exit(0);
	}
}
