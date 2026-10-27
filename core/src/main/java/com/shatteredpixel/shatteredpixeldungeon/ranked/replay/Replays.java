package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * spd-ranked (A5): replay files in the local folder {@code replays/}. Ranked matches store theirs
 * when the result comes; any {@code .txt} replay put there by hand (from a friend, from the server)
 * shows up in the list too.
 */
public final class Replays {

	public static final String DIR = "replays/";
	/** the list shows at most this many, newest first */
	public static final int MAX_LISTED = 50;

	private Replays(){}

	public static final class Entry {
		public final String name;
		public final long modified;
		/** null if the file could not be read */
		public final ReplayFile replay;
		public final String error;

		Entry( String name, long modified, ReplayFile replay, String error ){
			this.name = name;
			this.modified = modified;
			this.replay = replay;
			this.error = error;
		}
	}

	/** Stores the replay under a new name; returns the name, or null if it could not be written. */
	public static String save( ReplayFile replay, long time ){
		SimpleDateFormat f = new SimpleDateFormat( "yyyyMMdd-HHmmss", Locale.ROOT );
		f.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
		String base = f.format( new Date( time ) ) + "-" + replay.heroClass.name().toLowerCase( Locale.ROOT );
		try {
			FileHandle h = Gdx.files.local( DIR + base + ".txt" );
			for (int i = 2; h.exists(); i++) h = Gdx.files.local( DIR + base + "-" + i + ".txt" );
			h.writeString( replay.toText(), false, "UTF-8" );
			return h.name();
		} catch (Exception e){
			//no space or no permission: the match result stands, only the replay is lost
			return null;
		}
	}

	/** every .txt file in the folder, newest first; unreadable ones are listed with their error */
	public static List<Entry> list(){
		List<Entry> out = new ArrayList<>();
		FileHandle dir = Gdx.files.local( DIR );
		if (!dir.isDirectory()) return out;
		List<FileHandle> files = new ArrayList<>();
		for (FileHandle h : dir.list( ".txt" )) files.add( h );
		files.sort( (a, b) -> {
			int c = Long.compare( b.lastModified(), a.lastModified() );
			return c != 0 ? c : b.name().compareTo( a.name() );
		} );
		for (FileHandle h : files){
			if (out.size() >= MAX_LISTED) break;
			try {
				out.add( new Entry( h.name(), h.lastModified(), ReplayFile.fromText( h.readString( "UTF-8" ) ), null ) );
			} catch (Exception e){
				out.add( new Entry( h.name(), h.lastModified(), null, e.getMessage() == null ? e.toString() : e.getMessage() ) );
			}
		}
		return out;
	}

	public static boolean delete( String name ){
		if (name.contains( "/" ) || name.contains( "\\" ) || name.contains( ".." )) return false;
		return Gdx.files.local( DIR + name ).delete();
	}
}
