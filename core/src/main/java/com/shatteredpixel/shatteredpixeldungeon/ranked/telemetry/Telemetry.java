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
 * Local only: nothing here sends data anywhere (whether and what to upload is not decided yet).
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
