package com.shatteredpixel.shatteredpixeldungeon.ranked.qol;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Feature;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.log.EventLog;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * spd-ranked (B1): what happened just before the hero died, built only from the event log
 * and what the hero carried. Facts only (L12 default): no verdict on how the player played.
 *
 * Unused escape items are only listed when the player knew what they were (identified),
 * so the report never reveals an unknown potion or scroll.
 */
public final class DeathAnalysis {

	/** how far back, in game time, damage is summed */
	public static final float WINDOW = 10f;
	/** how many last log events are shown */
	public static final int LAST = 6;

	/** item class simple names counted as a way out; edit here to tune */
	public static final List<String> ESCAPES = Arrays.asList(
			"PotionOfHealing", "PotionOfInvisibility", "PotionOfHaste", "PotionOfShielding",
			"ScrollOfTeleportation", "ScrollOfMirrorImage", "ScrollOfTerror", "ScrollOfLullaby",
			"StoneOfBlink", "StoneOfFear", "StoneOfDeepSleep", "StoneOfShock" );

	private static final String HERO = "Hero";

	/** source -> damage taken by the hero in the last {@link #WINDOW} turns, biggest first */
	public final Map<String, Integer> damage = new LinkedHashMap<>();
	/** source of the hero's death, "-" if the log has none */
	public final String killer;
	public final List<EventLog.Event> last;
	/** known escape items still carried: item class -> quantity */
	public final Map<String, Integer> unusedEscapes = new LinkedHashMap<>();

	DeathAnalysis( List<EventLog.Event> events, Hero hero ){
		String k = "-";
		float end = events.isEmpty() ? 0 : events.get( events.size() - 1 ).time;
		Map<String, Integer> sum = new LinkedHashMap<>();
		for (EventLog.Event e : events){
			if (e.type == EventLog.Type.DEATH && HERO.equals( e.actor )) k = e.subject;
			if (e.type == EventLog.Type.DAMAGE && HERO.equals( e.actor ) && e.time >= end - WINDOW){
				Integer v = sum.get( e.subject );
				sum.put( e.subject, (v == null ? 0 : v) + e.value );
			}
		}
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>( sum.entrySet() );
		//stable sort: ties keep log order
		sorted.sort( (a, b) -> b.getValue() - a.getValue() );
		for (Map.Entry<String, Integer> e : sorted) damage.put( e.getKey(), e.getValue() );
		killer = k;
		last = new ArrayList<>( events.subList( Math.max( 0, events.size() - LAST ), events.size() ) );

		if (hero != null){
			for (Item i : hero.belongings){
				String n = i.getClass().getSimpleName();
				if (i.isIdentified() && ESCAPES.contains( n )){
					Integer v = unusedEscapes.get( n );
					unusedEscapes.put( n, (v == null ? 0 : v) + i.quantity() );
				}
			}
		}
	}

	public static DeathAnalysis of( Hero hero ){
		return new DeathAnalysis( EventLog.events(), hero );
	}

	public List<String> lines(){
		List<String> out = new ArrayList<>();
		out.add( "Death analysis: killed by " + killer );
		out.add( "Damage in the last " + (int)WINDOW + " turns: " + join( damage, " " ) );
		for (EventLog.Event e : last){
			out.add( "  t" + (int)e.time + " " + e.type + " " + e.actor + " " + e.subject + " " + e.value );
		}
		out.add( "Known escape items carried: " + join( unusedEscapes, " x" ) );
		return out;
	}

	private static String join( Map<String, Integer> m, String sep ){
		if (m.isEmpty()) return "none";
		StringBuilder s = new StringBuilder();
		for (Map.Entry<String, Integer> e : m.entrySet()){
			if (s.length() > 0) s.append( ", " );
			s.append( e.getKey() ).append( sep ).append( e.getValue() );
		}
		return s.toString();
	}

	/** hook on the game over screen; only writes to the game log */
	public static void report( Hero hero ){
		if (!Features.enabled( Feature.DEATH_ANALYSIS )) return;
		for (String l : of( hero ).lines()) GLog.i( l );
	}
}
