package com.shatteredpixel.shatteredpixeldungeon.ranked.skins;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * spd-ranked (A6): what the ranked server is told about skins. A pack is named by the SHA-256
 * of its art (each PNG's path and bytes, paths sorted), so renaming the folder or editing
 * skin.properties keeps the hash, and any changed pixel changes it. The denylist is named by
 * the SHA-256 of its text, so the server can tell a client whose denylist was edited.
 * Plain Java (no libGDX) so the server and its hash tool compute the same thing.
 */
public final class SkinHash {

	/** no skin: the game's own art */
	public static final String NONE = "";

	public interface Source {
		byte[] read( String path ) throws IOException;
	}

	private SkinHash(){}

	/** asset paths a pack may replace; anything that could leave the pack folder is refused */
	public static boolean usable( String path ){
		return path != null && path.endsWith( ".png" ) && !path.contains( ".." ) && !path.startsWith( "/" )
				&& !path.contains( "\\" ) && !path.contains( ":" );
	}

	/** the pack hash over these paths (unusable ones are skipped) */
	public static String of( List<String> paths, Source src ) throws IOException {
		List<String> sorted = new ArrayList<>();
		for (String p : paths) if (usable( p )) sorted.add( p );
		Collections.sort( sorted );
		MessageDigest md = sha256();
		for (String p : sorted){
			byte[] name = p.getBytes( StandardCharsets.UTF_8 );
			byte[] data = src.read( p );
			md.update( name );
			md.update( (byte) 0 );
			for (int i = 7; i >= 0; i--) md.update( (byte) (((long) data.length) >>> (8 * i)) );
			md.update( data );
		}
		return hex( md.digest() );
	}

	public static String ofText( String text ){
		return hex( sha256().digest( text.getBytes( StandardCharsets.UTF_8 ) ) );
	}

	/** 64 lowercase hex digits */
	public static boolean wellFormed( String hash ){
		return hash != null && hash.matches( "[0-9a-f]{64}" );
	}

	private static MessageDigest sha256(){
		try {
			return MessageDigest.getInstance( "SHA-256" );
		} catch (NoSuchAlgorithmException e){
			throw new IllegalStateException( e );
		}
	}

	private static String hex( byte[] b ){
		StringBuilder s = new StringBuilder( b.length * 2 );
		for (byte x : b) s.append( Character.forDigit( (x >> 4) & 15, 16 ) ).append( Character.forDigit( x & 15, 16 ) );
		return s.toString();
	}
}
