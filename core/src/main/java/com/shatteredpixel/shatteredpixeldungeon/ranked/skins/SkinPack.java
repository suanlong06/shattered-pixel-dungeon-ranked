package com.shatteredpixel.shatteredpixeldungeon.ranked.skins;

import com.badlogic.gdx.files.FileHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * spd-ranked (A6): a skin pack is a folder whose PNG files replace the game's assets with the
 * same relative path (for example {@code sprites/warrior.png}), plus an optional
 * {@code skin.properties} with {@code name=} and {@code author=}. Packs live in the local
 * folder {@code skins/<id>/}. Anything that is not a PNG is ignored.
 */
public final class SkinPack {

	public static final String MANIFEST = "skin.properties";
	private static final int MAX_DEPTH = 4;

	public final String id;
	public final String name;
	public final String author;
	private final FileHandle root;

	public SkinPack( FileHandle root ){
		this.root = root;
		this.id = root.name();
		Properties p = new Properties();
		FileHandle m = root.child( MANIFEST );
		if (m.exists()){
			try (java.io.Reader r = m.reader( "UTF-8" )){
				p.load( r );
			} catch (Exception e){
				//unreadable manifest: fall back to the folder name
			}
		}
		this.name = p.getProperty( "name", id );
		this.author = p.getProperty( "author", "" );
	}

	/** the pack's replacement for an asset path, or null; paths that could leave the folder are refused */
	public FileHandle file( String path ){
		if (path == null || !path.endsWith( ".png" ) || path.contains( ".." ) || path.startsWith( "/" )
				|| path.contains( "\\" ) || path.contains( ":" )) return null;
		FileHandle f = root.child( path );
		return f.exists() && !f.isDirectory() ? f : null;
	}

	/** asset paths this pack replaces, sorted */
	public List<String> paths(){
		List<String> out = new ArrayList<>();
		collect( root, "", 0, out );
		java.util.Collections.sort( out );
		return out;
	}

	private static void collect( FileHandle dir, String prefix, int depth, List<String> out ){
		if (depth > MAX_DEPTH) return;
		for (FileHandle f : dir.list()){
			if (f.isDirectory()) collect( f, prefix + f.name() + "/", depth + 1, out );
			else if (f.name().endsWith( ".png" )) out.add( prefix + f.name() );
		}
	}
}
