package com.shatteredpixel.shatteredpixeldungeon.ranked.sim;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (T0.8): records player inputs as commands, in the order the game received them.
 * Hooks (marked spd-ranked) in CellSelector.select, Hero.rest, Hero.search and Item.execute call
 * the record methods; nothing is recorded unless {@link #start()} was called.
 * The runner disables recording while it replays, so replayed inputs are not recorded twice.
 */
public final class CommandLog {

	private static List<Command> recording = null;
	private static boolean suspended = false;

	private CommandLog(){}

	public static void start(){ recording = new ArrayList<>(); }
	public static List<Command> stop(){ List<Command> r = recording; recording = null; return r; }
	public static boolean recording(){ return recording != null && !suspended; }

	static void suspend( boolean s ){ suspended = s; }

	public static void recordCell( int cell ){
		if (recording()) recording.add(Command.cell(cell));
	}

	public static void recordRest( boolean full ){
		if (recording()) recording.add(Command.rest(full));
	}

	public static void recordSearch(){
		if (recording()) recording.add(Command.search());
	}

	/** Only items the hero holds can be addressed; items nested in others (a staff's wand) are reached through their holder. */
	public static void recordItem( Item item, String action ){
		if (!recording() || Dungeon.hero == null || action == null) return;
		int i = Command.indexOf(Dungeon.hero, item);
		if (i >= 0) recording.add(Command.item(i, action));
	}

	public static String toText( List<Command> cmds ){
		StringBuilder sb = new StringBuilder();
		for (Command c : cmds) sb.append(c).append('\n');
		return sb.toString();
	}

	public static List<Command> fromText( String text ){
		List<Command> out = new ArrayList<>();
		for (String line : text.split("\n")){
			if (!line.trim().isEmpty()) out.add(Command.parse(line));
		}
		return out;
	}
}
