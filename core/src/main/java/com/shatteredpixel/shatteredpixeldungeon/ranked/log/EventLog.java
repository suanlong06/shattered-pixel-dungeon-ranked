package com.shatteredpixel.shatteredpixeldungeon.ranked.log;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (T-log): the one match event log shared by Death Analysis (B1), Training (B6),
 * anti-cheat (A3) and ghost/replay/telemetry (A5); see TASKS 3.3.
 *
 * Format, version {@link #VERSION}, one event per line, fields separated by '|':
 * <pre>v1|seq|time|TYPE|actor|subject|value</pre>
 * Fields are only ever added at the end, so older readers keep working (append-only).
 * The log only records: it never reads the RNG nor changes game state, and is not part of
 * the save or the state digest. In Solo it is a ring of {@link #SOLO_CAPACITY} events;
 * Ranked keeps everything for the server.
 */
public final class EventLog {

	public static final int VERSION = 1;
	public static final int SOLO_CAPACITY = 4096;

	public enum Type {
		/** actor = who was hit, subject = source, value = damage incl. shield */
		DAMAGE,
		/** actor = hero, value = HP regained since the hero's last turn */
		HEAL,
		/** actor = hero, value = target cell */
		MOVE,
		/** actor = hero, subject = item class + ":" + action, value = quantity */
		USE_ITEM,
		/** actor = who died, subject = source */
		DEATH
	}

	public static final class Event {
		public final int seq;
		public final float time;
		public final Type type;
		public final String actor, subject;
		public final int value;

		Event( int seq, float time, Type type, String actor, String subject, int value ){
			this.seq = seq; this.time = time; this.type = type;
			this.actor = actor; this.subject = subject; this.value = value;
		}

		public String line(){
			return "v" + VERSION + "|" + seq + "|" + time + "|" + type + "|" + actor + "|" + subject + "|" + value;
		}

		public static Event parse( String line ){
			String[] f = line.split( "[|]", -1 );
			if (f.length < 7 || !f[0].startsWith( "v" )) throw new IllegalArgumentException( line );
			return new Event( Integer.parseInt( f[1] ), Float.parseFloat( f[2] ), Type.valueOf( f[3] ),
					f[4], f[5], Integer.parseInt( f[6] ) );
		}
	}

	private static final ArrayDeque<Event> events = new ArrayDeque<>();
	private static int capacity = SOLO_CAPACITY;
	private static int seq;
	private static int lastHeroHP = -1;

	private EventLog(){}

	/** new or loaded run; 0 capacity = unlimited (Ranked) */
	public static void reset( int cap ){
		events.clear();
		capacity = cap;
		seq = 0;
		lastHeroHP = -1;
	}

	public static List<Event> events(){
		return new ArrayList<>( events );
	}

	public static List<Event> last( int n ){
		List<Event> all = events();
		return all.subList( Math.max( 0, all.size() - n ), all.size() );
	}

	static void add( Type type, String actor, String subject, int value ){
		events.addLast( new Event( seq++, Actor.now(), type, actor, subject, value ) );
		if (capacity > 0 && events.size() > capacity) events.removeFirst();
	}

	static String name( Object o ){
		if (o == null) return "-";
		Class<?> c = o instanceof Class ? (Class<?>) o : o.getClass();
		String n = c.getSimpleName();
		return n.isEmpty() ? c.getName().substring( c.getName().lastIndexOf( '.' ) + 1 ) : n;
	}

	// hooks, one line each in SPD's own files

	public static void damage( Char target, Object src, int dmg ){
		if (dmg <= 0) return;
		add( Type.DAMAGE, name( target ), name( src ), dmg );
		if (target instanceof Hero) lastHeroHP = target.HP;
	}

	public static void death( Char ch, Object src ){
		add( Type.DEATH, name( ch ), name( src ), 0 );
	}

	public static void move( Char ch, int cell ){
		if (ch instanceof Hero) add( Type.MOVE, name( ch ), "-", cell );
	}

	public static void useItem( Hero hero, Item item, String action ){
		add( Type.USE_ITEM, name( hero ), name( item ) + ":" + action, item.quantity() );
	}

	/** start of each hero turn: logs healing from any source as the HP gained since last time */
	public static void heroTurn( Hero hero ){
		if (lastHeroHP >= 0 && hero.HP > lastHeroHP){
			add( Type.HEAL, name( hero ), "-", hero.HP - lastHeroHP );
		}
		lastHeroHP = hero.HP;
	}
}
