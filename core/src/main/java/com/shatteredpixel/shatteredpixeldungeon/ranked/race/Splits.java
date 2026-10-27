package com.shatteredpixel.shatteredpixeldungeon.ranked.race;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

import java.util.Map;
import java.util.TreeMap;

/**
 * spd-ranked (A5): the milestones of one run, in game time (Statistics.duration, which unlike
 * Actor.now() is not rebased on level change or save): when the hero first
 * arrived on each depth and when each boss died, by the depth it died on.
 * Fed by EventLog (which resets it), so it only records and never touches game state.
 * Kept apart from the solo event ring so long runs never lose their early splits.
 *
 * Text form, one milestone per line: {@code d<depth>=<time>} or {@code b<depth>=<time>}.
 */
public final class Splits {

	private static final Splits CURRENT = new Splits();

	/** the run being played now */
	public static Splits current(){
		return CURRENT;
	}

	private final TreeMap<Integer, Float> arrived = new TreeMap<>();
	private final TreeMap<Integer, Float> bosses = new TreeMap<>();
	private int lastDepth = 0;

	public void clear(){
		arrived.clear();
		bosses.clear();
		lastDepth = 0;
	}

	/** only the first arrival counts: going back up and down again is not a new split; true if new */
	public boolean arrive( int depth, float time ){
		lastDepth = depth;
		if (arrived.containsKey( depth )) return false;
		arrived.put( depth, time );
		return true;
	}

	/** true if this was a new boss split */
	public boolean death( Char ch, float time ){
		if (ch != null && ch.properties().contains( Char.Property.BOSS ) && !bosses.containsKey( lastDepth )){
			bosses.put( lastDepth, time );
			return true;
		}
		return false;
	}

	public void boss( int depth, float time ){
		if (!bosses.containsKey( depth )) bosses.put( depth, time );
	}

	/** one line for the hero window: how this run stands against a ghost, or null */
	public static String raceLine( Ghost g, Splits now ){
		if (g == null) return null;
		Ghost.Delta d = g.compare( now );
		if (d == null) return null;
		int turns = Math.round( Math.abs( d.delta ) );
		String key = turns == 0 ? "even" : d.ahead() ? "ahead" : "behind";
		return com.shatteredpixel.shatteredpixeldungeon.messages.Messages.get( Ghost.class, d.boss ? key + "_boss" : key, turns, d.depth );
	}

	public Map<Integer, Float> arrived(){
		return new TreeMap<>( arrived );
	}

	public Map<Integer, Float> bosses(){
		return new TreeMap<>( bosses );
	}

	public Float arrivedAt( int depth ){
		return arrived.get( depth );
	}

	public Float bossAt( int depth ){
		return bosses.get( depth );
	}

	/** depth of the deepest boss killed, 0 if none */
	public int lastBoss(){
		return bosses.isEmpty() ? 0 : bosses.lastKey();
	}

	public int deepest(){
		return arrived.isEmpty() ? 0 : arrived.lastKey();
	}

	public Splits copy(){
		Splits s = new Splits();
		s.arrived.putAll( arrived );
		s.bosses.putAll( bosses );
		s.lastDepth = lastDepth;
		return s;
	}

	public String toText(){
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Float> e : arrived.entrySet()) sb.append( 'd' ).append( e.getKey() ).append( '=' ).append( e.getValue() ).append( '\n' );
		for (Map.Entry<Integer, Float> e : bosses.entrySet()) sb.append( 'b' ).append( e.getKey() ).append( '=' ).append( e.getValue() ).append( '\n' );
		return sb.toString();
	}

	/** unknown line kinds are skipped, so later versions can add milestones */
	public static Splits fromText( String text ){
		Splits s = new Splits();
		for (String line : text.split( "\n" )){
			line = line.trim();
			int eq = line.indexOf( '=' );
			if (line.length() < 3 || eq < 2) continue;
			int depth = Integer.parseInt( line.substring( 1, eq ) );
			float time = Float.parseFloat( line.substring( eq + 1 ) );
			if (line.charAt( 0 ) == 'd') s.arrive( depth, time );
			else if (line.charAt( 0 ) == 'b') s.boss( depth, time );
		}
		return s;
	}

	@Override
	public boolean equals( Object o ){
		return o instanceof Splits && ((Splits) o).arrived.equals( arrived ) && ((Splits) o).bosses.equals( bosses );
	}

	@Override
	public int hashCode(){
		return arrived.hashCode() * 31 + bosses.hashCode();
	}
}
