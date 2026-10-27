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

	/** one pool line: the seed and its lowest and highest class score */
	public static final class Entry {
		public final long seed;
		public final int min, max;

		public Entry( long seed, int min, int max ){
			this.seed = seed;
			this.min = min;
			this.max = max;
		}
	}

	/** the header a pool built now gets, without the leading "# " */
	public static String currentHeader(){
		return VERSION + " depth " + SeedScanner.DEPTH + " min " + SeedScorer.MIN + " cap " + SeedScorer.CAP;
	}

	public static String write( List<SeedScorer.Result> pool ){
		StringBuilder s = new StringBuilder();
		s.append( "# " ).append( currentHeader() ).append( '\n' );
		for (SeedScorer.Result r : pool){
			s.append( r.seed ).append( ' ' ).append( r.min ).append( ' ' ).append( r.max ).append( '\n' );
		}
		return s.toString();
	}

	/** the seeds of a pool file, in file order */
	public static List<Long> read( String text ){
		List<Long> out = new ArrayList<>();
		for (Entry e : entries( text )) out.add( e.seed );
		return out;
	}

	/** the lines of a pool file, in file order; a line with only a seed gets scores -1 */
	public static List<Entry> entries( String text ){
		List<Entry> out = new ArrayList<>();
		for (String line : text.split( "\n" )){
			line = line.trim();
			if (line.isEmpty() || line.startsWith( "#" )) continue;
			String[] f = line.split( "\\s+" );
			if (f.length != 1 && f.length != 3) throw new IllegalArgumentException( "bad pool line: " + line );
			out.add( f.length == 3
					? new Entry( Long.parseLong( f[0] ), Integer.parseInt( f[1] ), Integer.parseInt( f[2] ) )
					: new Entry( Long.parseLong( f[0] ), -1, -1 ) );
		}
		return out;
	}

	/** the first comment line without "# " (the thresholds the pool was built with), or null */
	public static String header( String text ){
		for (String line : text.split( "\n" )){
			line = line.trim();
			if (line.isEmpty()) continue;
			return line.startsWith( "#" ) ? line.substring( 1 ).trim() : null;
		}
		return null;
	}
}
