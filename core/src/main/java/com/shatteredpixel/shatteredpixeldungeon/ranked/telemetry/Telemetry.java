package com.shatteredpixel.shatteredpixeldungeon.ranked.telemetry;

import com.shatteredpixel.shatteredpixeldungeon.ranked.log.EventLog;
import com.shatteredpixel.shatteredpixeldungeon.ranked.race.Splits;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * spd-ranked (A5): a run summary built from the shared event log and the splits.
 * In Ranked the summary of each side is sent to the server once, before its last report
 * ({@link #upload}); it is kept there to tune features and train models, never to judge matches.
 * Pure: reads the log, never the game RNG or state.
 *
 * Text form is {@code key=value} lines with stable, sorted keys, so summaries can be diffed
 * and aggregated by a script.
 */
public final class Telemetry {

	public final EnumMap<EventLog.Type, Integer> counts = new EnumMap<>( EventLog.Type.class );
	public final TreeMap<String, Integer> damageTaken = new TreeMap<>();
	public final TreeMap<String, Integer> damageDealt = new TreeMap<>();
	public final TreeMap<String, Integer> itemsUsed = new TreeMap<>();
	public int healed;
	/** turns spent on each depth, from arriving there to arriving on the next */
	public final TreeMap<Integer, Float> turnsOnDepth = new TreeMap<>();

	public static Telemetry of( List<EventLog.Event> events, Splits splits, String hero ){
		Telemetry t = new Telemetry();
		for (EventLog.Type ty : EventLog.Type.values()) t.counts.put( ty, 0 );
		for (EventLog.Event e : events){
			t.counts.put( e.type, t.counts.get( e.type ) + 1 );
			switch (e.type){
				case DAMAGE:
					if (e.actor.equals( hero )) t.damageTaken.merge( e.subject, e.value, Integer::sum );
					else if (e.subject.equals( hero )) t.damageDealt.merge( e.actor, e.value, Integer::sum );
					break;
				case HEAL:
					t.healed += e.value;
					break;
				case USE_ITEM:
					t.itemsUsed.merge( e.subject, 1, Integer::sum );
					break;
				default:
					break;
			}
		}
		Integer prev = null;
		for (Map.Entry<Integer, Float> a : splits.arrived().entrySet()){
			if (prev != null) t.turnsOnDepth.put( prev, a.getValue() - splits.arrivedAt( prev ) );
			prev = a.getKey();
		}
		return t;
	}

	/** the server refuses bodies above 16 KB; per-source maps are cut to their largest entries first */
	public static final int MAX_CHARS = 15 * 1024;
	private static final int MAX_PER_MAP = 40;

	/** the text sent to the server at the end of a Ranked run, from the live log and splits */
	public static String upload( String heroClass, String end, int depth, long turns ){
		Telemetry t = of( EventLog.events(), Splits.current(), "Hero" );
		trim( t.damageTaken );
		trim( t.damageDealt );
		trim( t.itemsUsed );
		Map<String, String> f = t.fields();
		f.put( "game.version", "" + com.watabou.noosa.Game.versionCode );
		f.put( "hero.class", heroClass == null ? "-" : heroClass );
		f.put( "run.end", end );
		f.put( "run.depth", "" + depth );
		f.put( "run.turns", "" + turns );
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : f.entrySet()){
			String line = e.getKey().replaceAll( "[^A-Za-z0-9_.:-]", "_" ) + "=" + e.getValue() + "\n";
			if (sb.length() + line.length() > MAX_CHARS) break;
			sb.append( line );
		}
		return sb.toString();
	}

	/** keeps the largest entries (ties by name), so the summary stays small */
	static void trim( TreeMap<String, Integer> m ){
		if (m.size() <= MAX_PER_MAP) return;
		java.util.List<Map.Entry<String, Integer>> es = new java.util.ArrayList<>( m.entrySet() );
		es.sort( (a, b) -> b.getValue().equals( a.getValue() ) ? a.getKey().compareTo( b.getKey() ) : b.getValue() - a.getValue() );
		m.clear();
		for (Map.Entry<String, Integer> e : es.subList( 0, MAX_PER_MAP )) m.put( e.getKey(), e.getValue() );
	}

	public Map<String, String> fields(){
		Map<String, String> f = new LinkedHashMap<>();
		f.put( "telemetry", "v1" );
		for (Map.Entry<EventLog.Type, Integer> e : counts.entrySet()) f.put( "count." + e.getKey().name().toLowerCase(), "" + e.getValue() );
		f.put( "healed", "" + healed );
		for (Map.Entry<String, Integer> e : damageTaken.entrySet()) f.put( "taken." + e.getKey(), "" + e.getValue() );
		for (Map.Entry<String, Integer> e : damageDealt.entrySet()) f.put( "dealt." + e.getKey(), "" + e.getValue() );
		for (Map.Entry<String, Integer> e : itemsUsed.entrySet()) f.put( "used." + e.getKey(), "" + e.getValue() );
		for (Map.Entry<Integer, Float> e : turnsOnDepth.entrySet()) f.put( "turns.d" + e.getKey(), "" + e.getValue() );
		return f;
	}

	public String toText(){
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : fields().entrySet()) sb.append( e.getKey() ).append( '=' ).append( e.getValue() ).append( '\n' );
		return sb.toString();
	}
}
