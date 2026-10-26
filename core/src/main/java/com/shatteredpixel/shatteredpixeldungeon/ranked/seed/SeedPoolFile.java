package com.shatteredpixel.shatteredpixeldungeon.ranked.seed;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A4): the seed pool as plain text, one seed per line: {@code seed min max}.
 * Lines starting with '#' are comments; the first one records the scorer thresholds used,
 * so a pool built with old thresholds can be spotted. The server keeps this file secret.
 */
public final class SeedPoolFile {

	public static final String VERSION = "spdr-seedpool 1";

	private SeedPoolFile(){}

	public static String write( List<SeedScorer.Result> pool ){
		StringBuilder s = new StringBuilder();
		s.append( "# " ).append( VERSION ).append( " depth " ).append( SeedScanner.DEPTH )
				.append( " min " ).append( SeedScorer.MIN ).append( " cap " ).append( SeedScorer.CAP ).append( '\n' );
		for (SeedScorer.Result r : pool){
			s.append( r.seed ).append( ' ' ).append( r.min ).append( ' ' ).append( r.max ).append( '\n' );
		}
		return s.toString();
	}

	/** the seeds of a pool file, in file order */
	public static List<Long> read( String text ){
		List<Long> out = new ArrayList<>();
		for (String line : text.split( "\n" )){
			line = line.trim();
			if (line.isEmpty() || line.startsWith( "#" )) continue;
			out.add( Long.parseLong( line.split( " " )[0] ) );
		}
		return out;
	}
}
