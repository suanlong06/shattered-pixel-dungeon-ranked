package com.shatteredpixel.shatteredpixeldungeon.ranked.sim;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;

/**
 * spd-ranked (T0.8): one player input, given while the hero waits for input.
 * These are the inputs the game itself receives from its UI, so a recorded game replays through
 * exactly the same code paths:
 *  CELL c        - a tap on cell c (move, attack, interact, or the target of a pending item/spell)
 *  REST full     - the wait button (full = hold to rest until disturbed)
 *  SEARCH        - the search button
 *  ITEM i action - an item action from a window or quickslot; i = index in hero.belongings iteration order
 */
public final class Command {

	public enum Type { CELL, REST, SEARCH, ITEM }

	public final Type type;
	public final int arg;
	public final String action;

	private Command( Type type, int arg, String action ){
		this.type = type;
		this.arg = arg;
		this.action = action;
	}

	public static Command cell( int cell ){ return new Command(Type.CELL, cell, null); }
	public static Command rest( boolean full ){ return new Command(Type.REST, full ? 1 : 0, null); }
	public static Command search(){ return new Command(Type.SEARCH, 0, null); }
	public static Command item( int index, String action ){ return new Command(Type.ITEM, index, action); }

	/** Applies the input the same way the UI does. */
	public void apply(){
		Hero hero = Dungeon.hero;
		switch (type){
			case CELL:
				GameScene.handleCell( arg );
				break;
			case REST:
				hero.rest( arg != 0 );
				break;
			case SEARCH:
				hero.search( true );
				break;
			case ITEM:
				Item item = itemAt( hero, arg );
				if (item == null) throw new IllegalStateException("no item at index " + arg + " for " + this);
				item.execute( hero, action );
				break;
		}
	}

	public static Item itemAt( Hero hero, int index ){
		int i = 0;
		for (Item it : hero.belongings){
			if (i++ == index) return it;
		}
		return null;
	}

	public static int indexOf( Hero hero, Item item ){
		int i = 0;
		for (Item it : hero.belongings){
			if (it == item) return i;
			i++;
		}
		return -1;
	}

	@Override
	public String toString(){
		switch (type){
			case CELL:   return "CELL " + arg;
			case REST:   return "REST " + arg;
			case SEARCH: return "SEARCH";
			default:     return "ITEM " + arg + " " + action;
		}
	}

	public static Command parse( String s ){
		String[] p = s.trim().split(" ");
		switch (Type.valueOf(p[0])){
			case CELL:   return cell(Integer.parseInt(p[1]));
			case REST:   return rest(Integer.parseInt(p[1]) != 0);
			case SEARCH: return search();
			default:     return item(Integer.parseInt(p[1]), p[2]);
		}
	}

	@Override
	public boolean equals( Object o ){
		return o instanceof Command && o.toString().equals(toString());
	}

	@Override
	public int hashCode(){ return toString().hashCode(); }
}
