package com.shatteredpixel.shatteredpixeldungeon.ranked.sim;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.Toolbar;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;

import java.util.List;

/**
 * spd-ranked (T0.8): one player input. These are the inputs the game itself receives from its UI,
 * so a recorded game replays through exactly the same code paths:
 *  CELL c        - a tap on cell c (move, attack, interact, or the target of a pending item/spell)
 *  REST full     - the wait button (full = hold to rest until disturbed)
 *  SEARCH        - the search button
 *  ITEM i action - an item action from a window or quickslot; i = index in hero.belongings iteration order
 *  MOVE c        - hero.handle(c) without the cell selector: movement keys, attack and loot indicators
 *  PICKUP        - the wait-or-pickup key (Toolbar.waitOrPickup)
 *  ACTION        - the action indicator (ActionIndicator.action.doAction)
 *  RESUME        - the resume indicator (hero.resume)
 *  TALENT name   - spend a talent point
 *  CANCEL [sel]  - GameScene.cancel(), or with sel GameScene.cancelCellSelector(): stops the hero's
 *                  action or rest, or cancels a pending cell selection
 *  BTN k i n cls b - click kind k (0 left, 1 right, 2 middle, 3 long) on button i of the n buttons of
 *                  class b (depth first in the tree) in the top window, whose class is cls (CHOICE in a
 *                  window: options, shop, talents...). Counting only buttons of one class keeps the
 *                  address if a window ever adds other buttons by device or settings.
 *                  The older form without b counts every button of the window.
 *  BACK cls      - back / tap outside on the top window
 *  SELECT i      - item i (belongings index) picked in the top item selector window
 *  ALCH op [n]   - an input in the alchemy scene: IN i (belongings item i into the first empty input),
 *                  OUT s (input s back), CLEAR, COMBINE s (craft output s), EXIT (back to the level),
 *                  RESET (the scene is rebuilt: guide, rotation), PAUSE (the app paused with energy just
 *                  made), LOAD (the client was closed in alchemy and came back from its last save)
 *  ENERGIZE i all|one - turn item i (or one of its stack) into alchemical energy
 *  NOTE text     - the client saw something replay cannot follow; the replay is then not trusted
 * A command given while the hero was acting (not waiting for input) is prefixed "@k ", where k is
 * the number of hero turns since the previous command: replay applies it right before turn k+1.
 * Windows only exist in replay if the game itself opened them; see {@link CommandLog}.
 */
public final class Command {

	public enum Type { CELL, REST, SEARCH, ITEM, MOVE, PICKUP, ACTION, RESUME, TALENT, CANCEL, BTN, BACK, SELECT, ALCH, ENERGIZE, NOTE }

	/** ALCH operations, with whether they take a number */
	private static final java.util.Map<String, Boolean> ALCH_OPS = new java.util.LinkedHashMap<>();
	static {
		for (String op : new String[]{ "IN", "OUT", "COMBINE" }) ALCH_OPS.put( op, true );
		for (String op : new String[]{ "CLEAR", "EXIT", "RESET", "PAUSE", "LOAD" }) ALCH_OPS.put( op, false );
	}
	/** ALCH operations that do not come from the scene itself: they may come with any window open */
	private static final java.util.Set<String> ALCH_ANY_WINDOW = new java.util.LinkedHashSet<>( java.util.Arrays.asList( "EXIT", "RESET", "PAUSE", "LOAD" ) );

	/** commands that answer a window, or need no particular game state */
	private static final java.util.EnumSet<Type> WINDOW_OK = java.util.EnumSet.of( Type.BTN, Type.BACK, Type.SELECT, Type.TALENT, Type.CANCEL, Type.NOTE );

	public final Type type;
	public final int arg;
	/** ITEM: the action; BTN: "kind index count class"; BACK: class; TALENT: name; CANCEL: "sel" or null; NOTE: text */
	public final String action;
	/** hero turns since the previous command when the hero was acting, else -1 */
	public final int at;

	private Command( Type type, int arg, String action, int at ){
		this.type = type;
		this.arg = arg;
		this.action = action;
		this.at = at;
	}

	public static Command cell( int cell ){ return new Command(Type.CELL, cell, null, -1); }
	public static Command rest( boolean full ){ return new Command(Type.REST, full ? 1 : 0, null, -1); }
	public static Command search(){ return new Command(Type.SEARCH, 0, null, -1); }
	public static Command item( int index, String action ){ return new Command(Type.ITEM, index, action, -1); }
	public static Command move( int cell ){ return new Command(Type.MOVE, cell, null, -1); }
	public static Command pickup(){ return new Command(Type.PICKUP, 0, null, -1); }
	public static Command action(){ return new Command(Type.ACTION, 0, null, -1); }
	public static Command resume(){ return new Command(Type.RESUME, 0, null, -1); }
	public static Command talent( Talent t ){ return new Command(Type.TALENT, 0, t.name(), -1); }
	public static Command cancel( boolean selectorOnly ){ return new Command(Type.CANCEL, 0, selectorOnly ? "sel" : null, -1); }
	public static Command button( int kind, int index, int count, String windowClass ){
		return new Command(Type.BTN, index, kind + " " + index + " " + count + " " + windowClass, -1);
	}
	public static Command button( int kind, int index, int count, String windowClass, String buttonClass ){
		return new Command(Type.BTN, index, kind + " " + index + " " + count + " " + windowClass + " " + buttonClass, -1);
	}
	public static Command alchemy( String op, int n ){
		Boolean numbered = ALCH_OPS.get( op );
		if (numbered == null || n < 0) throw new IllegalArgumentException("bad alchemy input: " + op + " " + n);
		return new Command(Type.ALCH, numbered ? n : 0, op, -1);
	}
	public static Command energize( int index, boolean all ){ return new Command(Type.ENERGIZE, index, all ? "all" : "one", -1); }
	public static Command back( String windowClass ){ return new Command(Type.BACK, 0, windowClass, -1); }
	public static Command select( int index ){ return new Command(Type.SELECT, index, null, -1); }
	public static Command note( String text ){ return new Command(Type.NOTE, 0, text.replace( '\n', ' ' ).trim(), -1); }

	/** the same command, given k hero turns after the previous one while the hero was acting */
	public Command at( int k ){ return new Command(type, arg, action, k); }

	/** true if the command may be given while a window is open (all others mean a window was closed unseen) */
	public boolean answersWindow(){
		return WINDOW_OK.contains( type ) || (type == Type.ALCH && ALCH_ANY_WINDOW.contains( action ));
	}

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
			case ITEM: {
				Item item = itemAt( hero, arg );
				if (item == null) throw new IllegalStateException("no item at index " + arg + " for " + this);
				item.execute( hero, action );
				break;
			}
			case MOVE:
				if (hero.handle( arg )) hero.next();
				break;
			case PICKUP:
				Toolbar.waitOrPickup();
				break;
			case ACTION:
				if (ActionIndicator.action == null) throw new IllegalStateException("no action indicator for " + this);
				ActionIndicator.action.doAction();
				break;
			case RESUME:
				hero.resume();
				break;
			case TALENT: {
				Talent t = Talent.valueOf( action );
				if (hero.talentPointsAvailable( talentTier( hero, t ) ) <= 0) throw new IllegalStateException("no talent point for " + this);
				hero.upgradeTalent( t );
				break;
			}
			case CANCEL:
				if (action == null) GameScene.doCancel(); else GameScene.doCancelCellSelector();
				break;
			case BTN: {
				String[] p = action.split( " " );
				Window w = topWindow( p[3] );
				List<Button> buttons = p.length > 4 ? CommandLog.buttons( w, p[4] ) : CommandLog.buttons( w );
				if (buttons.size() != Integer.parseInt( p[2] )) throw new IllegalStateException(buttons.size() + " buttons, not " + p[2] + ", for " + this);
				buttons.get( arg ).replayClick( Integer.parseInt( p[0] ) );
				break;
			}
			case BACK:
				topWindow( action ).onBackPressed();
				break;
			case SELECT: {
				Window w = topWindow( null );
				if (!(w instanceof WndBag) || ((WndBag) w).getSelector() == null) throw new IllegalStateException("no item selector for " + this);
				Item item = itemAt( hero, arg );
				if (item == null) throw new IllegalStateException("no item at index " + arg + " for " + this);
				// as WndBag's item slot does
				WndBag.ItemSelector selector = ((WndBag) w).getSelector();
				if (selector.hideAfterSelecting()) w.hide();
				selector.onSelect( item );
				break;
			}
			case ALCH:
				applyAlchemy( hero );
				break;
			case ENERGIZE: {
				Item item = itemAt( hero, arg );
				// as WndEnergizeItem's selector allows
				if (item == null || item.energyVal() <= 0) throw new IllegalStateException("nothing to energize for " + this);
				if (action.equals( "all" )) com.shatteredpixel.shatteredpixeldungeon.windows.WndEnergizeItem.energizeAll( item );
				else com.shatteredpixel.shatteredpixeldungeon.windows.WndEnergizeItem.energizeOne( item );
				break;
			}
			case NOTE:
				break;
		}
	}

	/** as the alchemy scene's buttons do; the checks are the ones its UI makes before them */
	private void applyAlchemy( Hero hero ){
		if (!(com.watabou.noosa.Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene)){
			throw new IllegalStateException("not in alchemy for " + this);
		}
		com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene scene = (com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene) com.watabou.noosa.Game.scene();
		switch (action){
			case "IN": {
				Item item = itemAt( hero, arg );
				if (item == null || !com.shatteredpixel.shatteredpixeldungeon.items.Recipe.usableInRecipe( item )
						|| (hero.belongings.lostInventory() && !item.keptThroughLostInventory())){
					throw new IllegalStateException("no usable item at index " + arg + " for " + this);
				}
				if (scene.addInput( item ) == null) throw new IllegalStateException("no empty input for " + this);
				break;
			}
			case "OUT":
				if (arg > 2) throw new IllegalStateException("no input " + arg + " for " + this);
				scene.removeInput( arg );
				break;
			case "CLEAR":
				scene.clearSlots();
				break;
			case "COMBINE":
				if (!scene.combineEnabled( arg )) throw new IllegalStateException("cannot craft output " + arg + " for " + this);
				scene.combine( arg );
				break;
			case "EXIT":
				com.watabou.noosa.Game.switchScene( GameScene.class );
				break;
			case "RESET":
				com.watabou.noosa.Game.switchScene( com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class );
				break;
			case "PAUSE":
				scene.onPause();
				break;
			case "LOAD":
				// what the client lost when it closed: the scene and its inputs; it went on from its last save
				com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.abandonInputs();
				ActionIndicator.clearAction();
				com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.mode = com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.Mode.CONTINUE;
				com.watabou.noosa.Game.switchScene( com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.class );
				break;
		}
	}

	private static Window topWindow( String expectedClass ){
		Window w = CommandLog.topWindow();
		if (w == null) throw new IllegalStateException("no window open");
		if (expectedClass != null && !expectedClass.equals( CommandLog.windowName( w ) )){
			throw new IllegalStateException("top window is " + CommandLog.windowName( w ) + ", not " + expectedClass);
		}
		return w;
	}

	private static int talentTier( Hero hero, Talent t ){
		for (int i = 0; i < hero.talents.size(); i++){
			if (hero.talents.get( i ).containsKey( t )) return i + 1;
		}
		throw new IllegalStateException("hero has no talent " + t);
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
		String s;
		switch (type){
			case CELL:   s = "CELL " + arg; break;
			case REST:   s = "REST " + arg; break;
			case ITEM:   s = "ITEM " + arg + " " + action; break;
			case MOVE:   s = "MOVE " + arg; break;
			case SELECT: s = "SELECT " + arg; break;
			case CANCEL: s = action == null ? "CANCEL" : "CANCEL " + action; break;
			case ALCH:   s = ALCH_OPS.get( action ) ? "ALCH " + action + " " + arg : "ALCH " + action; break;
			case ENERGIZE: s = "ENERGIZE " + arg + " " + action; break;
			case TALENT: case BTN: case BACK: case NOTE:
				s = type + " " + action; break;
			default:     s = type.name(); break;
		}
		return at >= 0 ? "@" + at + " " + s : s;
	}

	public static Command parse( String s ){
		s = s.trim();
		int at = -1;
		if (s.startsWith( "@" )){
			int sp = s.indexOf( ' ' );
			at = Integer.parseInt( s.substring( 1, sp ) );
			if (at < 0) throw new IllegalArgumentException("bad turn count: " + s);
			s = s.substring( sp + 1 ).trim();
		}
		String[] p = s.split(" ");
		Type t = Type.valueOf(p[0]);
		String rest = p.length > 1 ? s.substring( p[0].length() + 1 ) : "";
		Command c;
		switch (t){
			case CELL:   c = cell(Integer.parseInt(p[1])); break;
			case REST:   c = rest(Integer.parseInt(p[1]) != 0); break;
			case SEARCH: c = search(); break;
			case ITEM:   c = item(Integer.parseInt(p[1]), p[2]); break;
			case MOVE:   c = move(Integer.parseInt(p[1])); break;
			case PICKUP: c = pickup(); break;
			case ACTION: c = action(); break;
			case RESUME: c = resume(); break;
			case TALENT: c = talent(Talent.valueOf(p[1])); break;
			case CANCEL:
				if (p.length > 1 && !p[1].equals("sel")) throw new IllegalArgumentException("bad cancel: " + s);
				c = cancel(p.length > 1); break;
			case BTN: {
				if (p.length != 5 && p.length != 6) throw new IllegalArgumentException("bad button: " + s);
				int kind = Integer.parseInt(p[1]), index = Integer.parseInt(p[2]), count = Integer.parseInt(p[3]);
				if (kind < 0 || kind > 3 || index < 0 || index >= count) throw new IllegalArgumentException("bad button: " + s);
				c = p.length == 6 ? button(kind, index, count, p[4], p[5]) : button(kind, index, count, p[4]); break;
			}
			case BACK:
				if (p.length != 2) throw new IllegalArgumentException("bad back: " + s);
				c = back(p[1]); break;
			case SELECT: c = select(Integer.parseInt(p[1])); break;
			case ALCH: {
				Boolean numbered = p.length > 1 ? ALCH_OPS.get( p[1] ) : null;
				if (numbered == null || p.length != (numbered ? 3 : 2)) throw new IllegalArgumentException("bad alchemy input: " + s);
				c = alchemy(p[1], numbered ? Integer.parseInt(p[2]) : 0); break;
			}
			case ENERGIZE:
				if (p.length != 3 || !(p[2].equals("all") || p[2].equals("one"))) throw new IllegalArgumentException("bad energize: " + s);
				c = energize(Integer.parseInt(p[1]), p[2].equals("all")); break;
			default:     c = note(rest); break;
		}
		return at >= 0 ? c.at( at ) : c;
	}

	@Override
	public boolean equals( Object o ){
		return o instanceof Command && o.toString().equals(toString());
	}

	@Override
	public int hashCode(){ return toString().hashCode(); }
}
