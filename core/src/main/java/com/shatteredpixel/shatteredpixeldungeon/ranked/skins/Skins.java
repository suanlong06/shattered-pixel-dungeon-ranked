package com.shatteredpixel.shatteredpixeldungeon.ranked.skins;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.watabou.gltextures.TextureCache;
import com.watabou.utils.GameSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A6): the active skin pack and the Ranked denylist.
 *
 * {@link #install()} hooks TextureCache so every texture loaded by path goes through
 * {@link #load(String)}. Skins only change pictures: they never touch game state or the RNG.
 * In Ranked the denylist applies: denied files load the original, denied rectangles are copied
 * back from the original art. If the denylist cannot be read, Ranked uses no skin at all (fails closed).
 */
public final class Skins {

	public static final String DIR = "skins/";
	private static final String SETTING = "spdr_skin";

	private static SkinPack active;
	private static SkinDenylist denylist;
	private static Boolean lastRanked;

	private Skins(){}

	/** once at startup, after Gdx is up */
	public static void install(){
		try {
			setDenylist( Gdx.files.internal( SkinDenylist.ASSET ).readString( "UTF-8" ) );
		} catch (Exception e){
			denylist = null;
		}
		String id = GameSettings.getString( SETTING, "" );
		active = id.isEmpty() ? null : find( id );
		lastRanked = Features.isRanked();
		TextureCache.override = Skins::load;
	}

	/** the denylist text to use; null or malformed = unreadable, so Ranked shows no skin */
	public static void setDenylist( String text ){
		try {
			denylist = text == null ? null : SkinDenylist.parse( text );
		} catch (IllegalArgumentException e){
			denylist = null;
		}
	}

	public static SkinDenylist denylist(){
		return denylist;
	}

	public static List<SkinPack> installed(){
		List<SkinPack> out = new ArrayList<>();
		FileHandle dir = Gdx.files.local( DIR );
		if (!dir.exists() || !dir.isDirectory()) return out;
		for (FileHandle f : dir.list()){
			if (f.isDirectory()) out.add( new SkinPack( f ) );
		}
		out.sort( (a, b) -> a.id.compareTo( b.id ) );
		return out;
	}

	public static SkinPack find( String id ){
		for (SkinPack p : installed()) if (p.id.equals( id )) return p;
		return null;
	}

	public static SkinPack active(){
		return active;
	}

	/** null = the game's own art; textures of the old and new pack are reloaded the next time they are used */
	public static void select( SkinPack pack ){
		SkinPack old = active;
		active = pack;
		GameSettings.put( SETTING, pack == null ? "" : pack.id );
		forget( old );
		forget( pack );
	}

	/** call when the game mode may have changed (new or loaded run), so the Ranked rules apply */
	public static void modeChanged(){
		boolean ranked = Features.isRanked();
		if (lastRanked != null && lastRanked == ranked) return;
		lastRanked = ranked;
		forget( active );
	}

	private static void forget( SkinPack pack ){
		if (pack == null) return;
		for (String path : pack.paths()) TextureCache.remove( path );
	}

	/** what is drawn for an asset path; null = the original */
	public static Pixmap load( String path ){
		SkinPack pack = active;
		if (pack == null) return null;
		boolean ranked = Features.isRanked();
		SkinDenylist deny = denylist;
		if (ranked && (deny == null || deny.deniesWhole( path ))) return null;
		FileHandle f = pack.file( path );
		if (f == null) return null;
		Pixmap skin;
		try {
			skin = new Pixmap( f );
		} catch (Exception e){
			return null;
		}
		if (ranked && !deny.rects( path ).isEmpty()){
			Pixmap orig = new Pixmap( Gdx.files.internal( path ) );
			restore( skin, orig, deny.rects( path ) );
			orig.dispose();
		}
		return skin;
	}

	/** copies the original pixels back into each rectangle (blending off, so transparency is copied too) */
	static void restore( Pixmap skin, Pixmap orig, List<int[]> rects ){
		skin.setBlending( Pixmap.Blending.None );
		for (int[] r : rects){
			skin.drawPixmap( orig, r[0], r[1], r[2], r[3], r[0], r[1], r[2], r[3] );
		}
		skin.setBlending( Pixmap.Blending.SourceOver );
	}
}
