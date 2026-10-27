package com.shatteredpixel.shatteredpixeldungeon.ranked.sim;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.utils.Bundle;
import com.watabou.utils.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * spd-ranked (T0.8): records player inputs as commands, in the order the game received them.
 * Nothing is recorded unless {@link #start()} was called. The runner suspends recording while it
 * replays, so replayed inputs are not recorded twice.
 * <p>
 * Only the player's own inputs are recorded, not what the game does in answer to them. Inputs come
 * from pointer and key events (Game.update), held keys (CellSelector.update), long presses
 * (Button.update) and render-thread callbacks posted from those: the "input context". Everything
 * else is game code ("gameplay") and replays by itself: the actor thread, sprite callbacks and
 * other frame updates, scene creation, callbacks posted by game code, and whatever an input does
 * once it is recorded ({@link #input}).
 * <p>
 * Windows (and item and cell selectors) made by gameplay are the ones that also exist in replay;
 * clicks and back presses in them are recorded (BTN, BACK, SELECT). Windows the player opened from
 * the UI (hero info, journal, examine) do not exist in replay; inputs there are recorded only where
 * they reach the game (TALENT, ITEM).
 */
public final class CommandLog {

	private static List<Command> recording = null;
	private static boolean suspended = false;
	/** depth of gameplay code running on the render thread */
	private static int scope = 0;
	/** depth of input context (event processing) on the render thread */
	private static int inputDepth = 0;
	private static Thread renderThread;
	/** hero turns since the last command (recorded, or applied in replay) */
	private static final java.util.concurrent.atomic.AtomicInteger heroActs = new java.util.concurrent.atomic.AtomicInteger();

	/** windows, item selectors and cell listeners made by gameplay; never iterated */
	private static final Set<Object> gameplay = Collections.synchronizedSet( Collections.newSetFromMap( new WeakHashMap<>() ) );

	private CommandLog(){}

	/** call on the render thread */
	public static synchronized void start(){
		recording = new ArrayList<>();
		renderThread = Thread.currentThread();
		heroActs.set( 0 );
		scope = 0;
		inputDepth = 0;
		inAlchemy = false;
		alchemySwitch = null;
		Game.renderCallbackHook = CommandLog::wrapCallback;
		Game.inputHook = CommandLog::asInput;
		Game.switchHook = CommandLog::sceneRequested;
	}

	public static synchronized List<Command> stop(){
		List<Command> r = recording;
		recording = null;
		if (!suspended) Game.renderCallbackHook = null;
		Game.inputHook = null;
		Game.switchHook = null;
		return r;
	}

	/** what was recorded since start(), read-only; empty when not recording */
	public static synchronized List<Command> recorded(){
		return recording == null ? Collections.<Command>emptyList() : Collections.unmodifiableList( new ArrayList<>( recording ) );
	}

	public static boolean recording(){ return recording != null && !suspended; }

	/** replay mode: nothing is recorded and all windows and selectors are gameplay */
	static void suspend( boolean s ){
		suspended = s;
		if (s) {
			heroActs.set( 0 );
			Game.renderCallbackHook = null;
			Game.inputHook = null;
			Game.switchHook = null;
		} else if (recording != null) {
			Game.renderCallbackHook = CommandLog::wrapCallback;
			Game.inputHook = CommandLog::asInput;
			Game.switchHook = CommandLog::sceneRequested;
		}
	}

	private static boolean active(){ return recording != null || suspended; }

	/** true while game code (not a player input) runs */
	public static boolean inGameplay(){
		return suspended || scope > 0 || inputDepth == 0 || Thread.currentThread() != renderThread;
	}

	/** Runs body as input context: pointer and key events, held keys, long presses. */
	public static void asInput( Runnable body ){
		if (!active() || Thread.currentThread() != renderThread){
			body.run();
			return;
		}
		inputDepth++;
		try {
			body.run();
		} finally {
			inputDepth--;
		}
	}

	/** true if a player input given now must be recorded */
	private static boolean canRecord(){
		return recording() && !inGameplay();
	}

	private static synchronized void add( Command c ){
		int k = heroActs.getAndSet( 0 );
		if (!(Dungeon.hero != null && heroWaits())) c = c.at( k );
		recording.add( c );
	}

	static boolean heroWaits(){
		Hero h = Dungeon.hero;
		return h.ready && h.curAction == null && !h.resting;
	}

	// hooks

	/** Hero.act: one more hero turn */
	public static void heroActed(){
		if (active()) heroActs.incrementAndGet();
	}

	public static int heroActs(){ return heroActs.get(); }
	static void resetHeroActs(){ heroActs.set( 0 ); }

	/** Records c if it is a player input, then applies it as gameplay. */
	public static void input( Command c ){
		if (canRecord()) add( c );
		runAsGameplay( c::apply );
	}

	/** Runs body as gameplay: nothing it does is recorded as an input. */
	public static void runAsGameplay( Runnable body ){
		if (!active()){
			body.run();
			return;
		}
		scope++;
		try {
			body.run();
		} finally {
			scope--;
		}
	}

	private static boolean asGameplay( BooleanSupplier body ){
		if (!active()) return body.getAsBoolean();
		scope++;
		try {
			return body.getAsBoolean();
		} finally {
			scope--;
		}
	}

	/** GameScene.create: what a new scene shows is gameplay (replay builds the same scene) */
	public static void beginScene(){ if (active()) scope++; }
	public static void endScene(){ if (active() && scope > 0) scope--; }

	/** Game.runOnRenderThread: a callback runs in the context it was posted from */
	private static Callback wrapCallback( Callback c ){
		if (inGameplay()) return () -> runAsGameplay( c::call );
		return () -> asInput( c::call );
	}

	/** Window constructor */
	public static void windowCreated( Window w ){
		if (active() && inGameplay()) gameplay.add( w );
	}

	/** GameScene.selectItem */
	public static void selectorCreated( WndBag.ItemSelector s ){
		if (active() && inGameplay() && s != null) gameplay.add( s );
	}

	/** GameScene.selectCell: the default listener, or one set by gameplay, takes gameplay taps */
	public static void cellListener( Object oldListener, Object listener, boolean isDefault ){
		if (!active() || listener == null) return;
		if (isDefault || inGameplay()) gameplay.add( listener );
		if (canRecord() && oldListener != null && oldListener != listener && gameplay.contains( oldListener ) && !isDefault){
			add( Command.note( "cell selection replaced from the UI" ) );
		}
	}

	/** CellSelector.select: a tap goes to the listener; recorded if the game set that listener */
	public static void cell( int cell, Object listener, Runnable select ){
		if (canRecord() && gameplay.contains( listener )){
			add( Command.cell( cell ) );
			runAsGameplay( select );
		} else {
			select.run();
		}
	}

	/** GameScene.cancel / cancelCellSelector, and keys that stop resting */
	public static boolean cancel( boolean selectorOnly, BooleanSupplier body ){
		if (canRecord() && cancels( selectorOnly )){
			add( Command.cancel( selectorOnly ) );
			return asGameplay( body );
		}
		return body.getAsBoolean();
	}

	/** movement keys stop resting: replayed as CANCEL, which does the same while the hero rests */
	public static void stopResting( Runnable body ){
		if (canRecord() && Dungeon.hero.resting){
			add( Command.cancel( false ) );
			runAsGameplay( body );
		} else {
			body.run();
		}
	}

	/** whether a cancel now would change the game (else it only touches UI) */
	private static boolean cancels( boolean selectorOnly ){
		Hero h = Dungeon.hero;
		if (!selectorOnly && h != null && (h.curAction != null || h.resting)) return true;
		Object l = com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.cellListener();
		return l != null && !com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.isDefaultCellListener( l ) && gameplay.contains( l );
	}

	/** a click on a button (Button), recorded if the button is in a window made by gameplay */
	public static boolean click( Button b, int kind, BooleanSupplier body ){
		if (!canRecord()) return body.getAsBoolean();
		Window w = windowOf( b );
		if (w == null || !isGameplay( w )) return body.getAsBoolean();
		if (w instanceof WndBag){
			// item slots are recorded by what they pick (SELECT); the bag tabs only change the view
			Item item = b instanceof com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot
					? ((com.shatteredpixel.shatteredpixeldungeon.ui.InventorySlot) b).item() : null;
			if (item == null || kind == 2 || kind == 3) return body.getAsBoolean();
			int i = Command.indexOf( Dungeon.hero, item );
			add( i >= 0 ? Command.select( i ) : Command.note( "selected an item outside the belongings" ) );
			return asGameplay( body );
		}
		String cls = buttonName( b );
		List<Button> buttons = buttons( w, cls );
		int i = buttons.indexOf( b );
		if (i < 0) return body.getAsBoolean();
		Command c = Command.button( kind, i, buttons.size(), windowName( w ), cls );
		// a long press that is not handled is no click at all
		if (kind == 3){
			synchronized (CommandLog.class){
				int at = recording.size();
				boolean waiting = Dungeon.hero != null && heroWaits();
				int k = heroActs.get();
				boolean handled = asGameplay( body );
				if (handled){
					heroActs.set( 0 );
					recording.add( at, waiting ? c : c.at( k ) );
				}
				return handled;
			}
		}
		add( c );
		return asGameplay( body );
	}

	/** Window back press (key or tap outside) */
	public static void back( Window w, Runnable body ){
		if (canRecord() && isGameplay( w )){
			add( Command.back( windowName( w ) ) );
			runAsGameplay( body );
		} else {
			body.run();
		}
	}

	/** GameScene.saveWindows: a scene reset (rotation, UI settings) drops windows the replay still has */
	public static void sceneReset( Group scene ){
		if (!recording() || scope > 0 || scene == null) return;
		for (Gizmo g : scene.membersCopy()){
			if (g instanceof Window && isGameplay( (Window) g )){
				add( Command.note( "scene reset with " + windowName( (Window) g ) + " open" ) );
				return;
			}
		}
	}

	private static boolean isGameplay( Window w ){
		return gameplay.contains( w ) || (w instanceof WndBag && gameplay.contains( ((WndBag) w).getSelector() ));
	}

	private static Window windowOf( Gizmo g ){
		for (Group p = g.parent; p != null; p = p.parent){
			if (p instanceof Window) return (Window) p;
		}
		return null;
	}

	/** the top window of the scene that the game opened (in replay every window is) */
	public static Window topWindow(){
		Group scene = Game.scene();
		if (scene == null) return null;
		Gizmo[] ms = scene.membersCopy();
		for (int i = ms.length - 1; i >= 0; i--){
			if (ms[i] instanceof Window && ms[i].exists && (suspended || isGameplay( (Window) ms[i] ))) return (Window) ms[i];
		}
		return null;
	}

	/** every button in the window, depth first in member order */
	public static List<Button> buttons( Window w ){
		List<Button> out = new ArrayList<>();
		collect( w, out );
		return out;
	}

	/** the buttons of one class in the window, depth first in member order */
	public static List<Button> buttons( Window w, String buttonClass ){
		List<Button> out = new ArrayList<>();
		for (Button b : buttons( w )) if (buttonName( b ).equals( buttonClass )) out.add( b );
		return out;
	}

	public static String buttonName( Button b ){
		return b.getClass().getName().replace( "com.shatteredpixel.shatteredpixeldungeon.", "" );
	}

	private static void collect( Group g, List<Button> out ){
		for (Gizmo m : g.membersCopy()){
			if (m == null) continue;
			if (m instanceof Button) out.add( (Button) m );
			if (m instanceof Group) collect( (Group) m, out );
		}
	}

	public static String windowName( Window w ){
		return w.getClass().getName().replace( "com.shatteredpixel.shatteredpixeldungeon.", "" );
	}

	// alchemy: AlchemyScene is no window, and the player's inputs there change the game. They are
	// recorded where they take effect (ALCH, ENERGIZE), whichever button, key, radial menu or guide
	// recipe led there; the windows on the way (item selectors, energize) are UI only.

	/** an AlchemyScene is up (true from its create to the destroy that leaves alchemy) */
	private static boolean inAlchemy = false;
	/** the scene a recorded EXIT or RESET switches to, until the switch is made */
	private static Class<?> alchemySwitch = null;

	/** AlchemyScene: an input that changes the game; recorded if the player gave it */
	public static void alchemy( Command c, Runnable body ){
		if (canRecord()){
			add( c );
			runAsGameplay( body );
		} else {
			body.run();
		}
	}

	/** AlchemyScene.addInput: the item is addressed by its index in the belongings */
	public static void alchemyInput( Item item, Runnable body ){
		if (canRecord() && item != null){
			int i = Command.indexOf( Dungeon.hero, item );
			add( i >= 0 ? Command.alchemy( "IN", i ) : Command.note( "alchemy input outside the belongings" ) );
			runAsGameplay( body );
		} else {
			body.run();
		}
	}

	/** WndEnergizeItem.energizeAll / energizeOne */
	public static void energize( Item item, boolean all, Runnable body ){
		if (canRecord() && Dungeon.hero != null){
			int i = Command.indexOf( Dungeon.hero, item );
			add( i >= 0 ? Command.energize( i, all ) : Command.note( "energized an item outside the belongings" ) );
			runAsGameplay( body );
		} else {
			body.run();
		}
	}

	/** AlchemyScene.create */
	public static synchronized void alchemyOpened(){
		if (!recording()) return;
		inAlchemy = true;
		alchemySwitch = null;
	}

	/**
	 * Game.switchScene: leaving alchemy (exit button, back key) and resetting its scene (alchemy
	 * guide, rotation, window size) are inputs, whoever asked; entering it is the game's doing.
	 */
	private static synchronized void sceneRequested( Class<?> next ){
		if (!recording() || scope > 0 || !inAlchemy || alchemySwitch != null) return;
		if (next == com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class){
			add( Command.alchemy( "RESET", 0 ) );
		} else if (next == com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.class){
			add( Command.alchemy( "EXIT", 0 ) );
		} else {
			add( Command.note( "left alchemy for " + (next == null ? "none" : next.getSimpleName()) ) );
		}
		alchemySwitch = next;
	}

	/** AlchemyScene.destroy, before it gives the inputs back and saves */
	public static synchronized void alchemyClosing(){
		if (!recording() || !inAlchemy) return;
		// closed without a switch: the app is closing, and the save it makes has left alchemy
		if (alchemySwitch == null) sceneRequested( com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.class );
		inAlchemy = alchemySwitch == com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene.class;
	}

	/** AlchemyScene.onPause: with energy just made, pausing gives the inputs back and saves */
	public static synchronized void alchemyPaused(){
		if (recording() && inAlchemy) add( Command.alchemy( "PAUSE", 0 ) );
	}

	// old hooks, kept for the item and hero call sites

	public static void recordRest( boolean full ){
		if (canRecord()) add( Command.rest( full ) );
	}

	public static void recordSearch(){
		if (canRecord()) add( Command.search() );
	}

	/** Item action from the UI. Only items the hero holds can be addressed; items nested in others (a staff's wand) are reached through their holder. */
	public static void item( Item item, String action, Runnable execute ){
		if (action == null) return; // as Item.execute(hero) without a default action
		if (canRecord() && Dungeon.hero != null){
			int i = Command.indexOf( Dungeon.hero, item );
			add( i >= 0 ? Command.item( i, action ) : Command.note( "action " + action + " on an item outside the belongings" ) );
			runAsGameplay( execute );
		} else {
			execute.run();
		}
	}

	// save and load (rejoin after the app was closed): the log goes with the save it belongs to

	private static final String LOG = "spdr_command_log";
	private static final String ACTS = "spdr_command_log_acts";
	private static final String BUSY = "spdr_command_log_busy";
	private static final String ALCH = "spdr_command_log_alchemy";

	/** Dungeon.saveGame */
	public static synchronized void store( Bundle bundle ){
		if (!recording()) return;
		bundle.put( LOG, toText( recording ) );
		bundle.put( ACTS, heroActs.get() );
		// what loading drops: the hero's current action and resting (a level change has neither)
		bundle.put( BUSY, Dungeon.hero != null && (Dungeon.hero.curAction != null || Dungeon.hero.resting) );
		// saved in alchemy (not on the way out): loading it comes back to the level, without the inputs
		bundle.put( ALCH, inAlchemy );
	}

	/**
	 * Dungeon.loadGame: a recording started for this run continues the saved log. A save made while
	 * the hero was acting drops that action on load; replay stops it at the same turn.
	 */
	public static synchronized void restore( Bundle bundle ){
		if (!recording() || !bundle.contains( LOG )) return;
		recording = new ArrayList<>( fromText( bundle.getString( LOG ) ) );
		heroActs.set( 0 );
		if (bundle.getBoolean( BUSY )){
			recording.add( Command.cancel( false ).at( bundle.getInt( ACTS ) ) );
			recording.add( Command.note( "resumed from a save made while the hero was acting" ) );
		}
		if (bundle.getBoolean( ALCH )) recording.add( Command.alchemy( "LOAD", 0 ) );
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
