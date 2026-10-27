package com.shatteredpixel.shatteredpixeldungeon.ranked.skins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * spd-ranked (A6): which parts of the game's art a skin pack may not replace in Ranked.
 * Data, not code: read from assets/skins/ranked_denylist.properties.
 * <pre>
 * deny.atlas=path,path           whole files
 * deny.rects.&lt;path&gt;=x,y,w,h;...  rectangles inside a file
 * </pre>
 */
public final class SkinDenylist {

	public static final String ASSET = "skins/ranked_denylist.properties";

	private final TreeSet<String> atlases = new TreeSet<>();
	private final TreeMap<String, List<int[]>> rects = new TreeMap<>();

	private SkinDenylist(){}

	/** throws IllegalArgumentException on any malformed line, so a broken file is never half applied */
	public static SkinDenylist parse( String text ){
		SkinDenylist d = new SkinDenylist();
		for (String raw : text.replace( "\r", "" ).split( "\n" )){
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith( "#" )) continue;
			int eq = line.indexOf( '=' );
			if (eq < 0) throw new IllegalArgumentException( "denylist: " + line );
			String key = line.substring( 0, eq ).trim(), val = line.substring( eq + 1 ).trim();
			if (key.equals( "deny.atlas" )){
				for (String p : val.split( "," )) if (!p.trim().isEmpty()) d.atlases.add( p.trim() );
			} else if (key.startsWith( "deny.rects." )){
				List<int[]> list = new ArrayList<>();
				for (String r : val.split( ";" )){
					String[] n = r.trim().split( "," );
					if (n.length != 4) throw new IllegalArgumentException( "denylist rect: " + r );
					int[] rect = new int[4];
					for (int i = 0; i < 4; i++) rect[i] = Integer.parseInt( n[i].trim() );
					if (rect[0] < 0 || rect[1] < 0 || rect[2] <= 0 || rect[3] <= 0) throw new IllegalArgumentException( "denylist rect: " + r );
					list.add( rect );
				}
				d.rects.put( key.substring( "deny.rects.".length() ), list );
			} else {
				throw new IllegalArgumentException( "denylist key: " + key );
			}
		}
		return d;
	}

	public boolean deniesWhole( String path ){
		return atlases.contains( path );
	}

	/** rectangles {x, y, w, h} of this file that keep the original art */
	public List<int[]> rects( String path ){
		List<int[]> r = rects.get( path );
		return r == null ? Collections.<int[]>emptyList() : r;
	}

	/** number of denied files and rectangles */
	public int size(){
		int n = atlases.size();
		for (Map.Entry<String, List<int[]>> e : rects.entrySet()) n += e.getValue().size();
		return n;
	}
}
