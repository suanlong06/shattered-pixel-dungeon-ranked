package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.PlateArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Greatsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.TurnRunner;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Test bot that reaches and beats the bosses of floors 5, 10 and 15 with nothing but player
 * commands, so the whole run (bosses included) can be replayed as the ranked server does
 * (ReplayCheck). Its only help is {@link #KIT}: experience and gear given at the start of the
 * run, which the replay gives too. Each new floor it reads Magic Mapping; it eats when hungry,
 * drinks healing when low, attacks the adjacent enemy, walks to the nearest visible enemy, picks
 * up keys, follows the boss scripts ({@link #bossGoal}), otherwise walks to the exit, and explores
 * the edge of the known map when nothing else moves it. It reads game fields only.
 */
public class BossBot implements TurnRunner.Input {

	/** strong gear, a map for every floor, food and healing; applied once, before any command */
	public static final Runnable KIT = () -> {
		Hero h = Dungeon.hero;
		h.STR = 20;
		h.lvl = 20;
		h.updateHT( true );
		MeleeWeapon w = new Greatsword();
		w.upgrade( 25 );
		w.identify();
		h.belongings.weapon = w;
		Armor a = new PlateArmor();
		a.upgrade( 25 );
		a.identify();
		h.belongings.armor = a;
		a.activate( h );
		new ScrollOfMagicMapping().quantity( 20 ).identify().collect();
		new Food().quantity( 20 ).collect();
		new PotionOfHealing().quantity( 40 ).identify().collect();
	};

	public final List<Command> issued = new ArrayList<>();
	public List<String> debug;
	/** stops (returns null) on arriving at this floor */
	public final int stopDepth;
	private int mappedDepth = -1;
	private int lastPos = -1, still;
	private Command last;

	public BossBot( int stopDepth ){ this.stopDepth = stopDepth; }

	@Override
	public Command next( TurnRunner r ){
		Command c = decide();
		if (c != null) issued.add( c );
		if (debug != null) debug.add( c + "@" + Dungeon.hero.pos );
		return c;
	}

	/**
	 * per target (a mob's id, or -1-cell): {closest distance reached, taps since, blocked until
	 * input n}. A target the hero stops getting closer to (a mob across water, a cell an enemy
	 * keeps interrupting the walk to) is skipped for a while, so the bot does something else.
	 */
	private final java.util.Map<Integer, int[]> progress = new java.util.HashMap<>();
	private int inputs, floorInputs;
	static final int MISSES = 2, COOLDOWN = 100, FLOOR_BUDGET = 3000;

	private Command decide(){
		Hero hero = Dungeon.hero;
		if (!hero.isAlive() || Dungeon.depth >= stopDepth) return null;
		inputs++;
		floorInputs++;
		if (hero.pos == lastPos) still++; else still = 0;
		lastPos = hero.pos;
		if (still > 300 || floorInputs > FLOOR_BUDGET) return null;

		// a new floor, or a boss script rebuilt the map and the hero forgot it
		if (Dungeon.depth != mappedDepth || noneMapped()){
			if (Dungeon.depth != mappedDepth){
				progress.clear();
				floorInputs = 0;
			}
			mappedDepth = Dungeon.depth;
			Command read = use( ScrollOfMagicMapping.class, "READ" );
			if (read != null) return read;
		}
		Hunger hunger = hero.buff( Hunger.class );
		if (hunger != null && hunger.hunger() >= Hunger.HUNGRY){
			Command eat = use( Food.class, "EAT" );
			if (eat != null) return eat;
		}
		if (hero.HP < hero.HT / 2){
			Command drink = use( PotionOfHealing.class, "DRINK" );
			if (drink != null) return drink;
		}

		Char adjacent = null;
		for (int n : PathFinder.NEIGHBOURS8){
			Char ch = Actor.findChar( hero.pos + n );
			if (hostile( ch ) && (adjacent == null || ch.id() < adjacent.id())) adjacent = ch;
		}
		if (adjacent != null) return Command.cell( adjacent.pos );
		// rooted (webs, roots): a tap to walk takes no time, so let time pass instead
		if (hero.rooted) return Command.search();

		Char seen = null;
		int best = Integer.MAX_VALUE;
		for (Mob m : Dungeon.level.mobs.toArray( new Mob[0] )){
			if (!hostile( m ) || !hero.fieldOfView[m.pos] || (blocked( m.id() ) && !m.properties().contains( Char.Property.BOSS ))) continue;
			int d = Dungeon.level.distance( hero.pos, m.pos );
			if (d < best || (d == best && m.id() < seen.id())){ best = d; seen = m; }
		}
		if (seen != null) return tap( seen.id(), seen.pos );

		// keys (a boss drops the one that opens its floor's exit)
		for (com.shatteredpixel.shatteredpixeldungeon.items.Heap heap : Dungeon.level.heaps.valueList()){
			if (heap.peek() instanceof com.shatteredpixel.shatteredpixeldungeon.items.keys.Key && !failed( heap.pos )) return tap( heap.pos );
		}

		int goal = bossGoal();
		if (goal >= 0 && !failed( goal )) return tap( goal );

		// a locked door the hero holds the key to (Tengu's cell)
		if (com.shatteredpixel.shatteredpixeldungeon.journal.Notes.keyCount( new com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey( Dungeon.depth ) ) > 0){
			for (int i = 0; i < Dungeon.level.length(); i++){
				if (Dungeon.level.map[i] == com.shatteredpixel.shatteredpixeldungeon.levels.Terrain.LOCKED_DOOR && !failed( i )) return tap( i );
			}
		}

		LevelTransition exit = Dungeon.level.getTransition( LevelTransition.Type.REGULAR_EXIT );
		if (exit != null && !failed( exit.cell() )) return tap( exit.cell() );

		int explore = nearestUnvisited();
		if (explore >= 0) return tap( explore );
		// everything given up for now: let time pass (enemies move, cooldowns end)
		return Command.search();
	}

	/**
	 * where a boss floor's script wants the hero, as a player who knows the fight goes there:
	 * into Tengu's cell to start the fight, back up the hallway after Tengu's first phase, and to
	 * the pylon that supercharges DM-300 (it may be out of sight)
	 */
	private static int bossGoal(){
		// a boss fighting the hero is the goal even out of sight
		for (Mob m : Dungeon.level.mobs.toArray( new Mob[0] )){
			if (hostile( m ) && m.properties().contains( Char.Property.BOSS )) return m.pos;
		}
		if (Dungeon.level instanceof com.shatteredpixel.shatteredpixeldungeon.levels.CavesBossLevel){
			for (Mob m : Dungeon.level.mobs.toArray( new Mob[0] )){
				if (m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Pylon && m.alignment == Char.Alignment.ENEMY) return m.pos;
			}
			// the fight starts when the hero comes near a pylon: walk next to the first one
			if (!Dungeon.level.locked){
				for (Mob m : Dungeon.level.mobs.toArray( new Mob[0] )){
					if (!(m instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Pylon)) continue;
					int best = -1;
					for (int n : PathFinder.NEIGHBOURS8){
						int c = m.pos + n;
						if (Dungeon.level.passable[c] && (best < 0 || Dungeon.level.distance( Dungeon.hero.pos, c ) < Dungeon.level.distance( Dungeon.hero.pos, best ))) best = c;
					}
					if (best >= 0) return best;
				}
			}
			return -1;
		}
		if (Dungeon.level instanceof com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel){
			switch (((com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel) Dungeon.level).state()){
				case START: return 10 + 27 * Dungeon.level.width();
				case FIGHT_PAUSE: return 10 + 8 * Dungeon.level.width();
				default: return -1;
			}
		}
		return -1;
	}

	private boolean blocked( int key ){
		int[] p = progress.get( key );
		return p != null && p[2] > inputs;
	}

	private boolean failed( int cell ){
		return blocked( -1 - cell );
	}

	private Command tap( int cell ){
		return tap( -1 - cell, cell );
	}

	private Command tap( int key, int cell ){
		int d = Dungeon.level.distance( Dungeon.hero.pos, cell );
		int[] p = progress.computeIfAbsent( key, k -> new int[]{ Integer.MAX_VALUE, 0, 0 } );
		if (d < p[0]){
			p[0] = d;
			p[1] = 0;
		} else if (++p[1] >= MISSES){
			p[0] = Integer.MAX_VALUE;
			p[1] = 0;
			p[2] = inputs + COOLDOWN;
		}
		return Command.cell( cell );
	}

	/**
	 * an enemy worth hitting. DM-300 is skipped while a pylon supercharges it (it takes no
	 * damage then; the active pylon is an enemy to hit instead). Reads fields only: game methods
	 * such as isInvulnerable can change state, and the bot must not.
	 */
	static boolean hostile( Char ch ){
		if (!(ch instanceof Mob) || ch.alignment != Char.Alignment.ENEMY || !ch.isAlive() || ch.invisible > 0) return false;
		return !(ch instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300 && ((com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300) ch).supercharged);
	}

	private static Command use( Class<? extends Item> cls, String action ){
		Item it = Dungeon.hero.belongings.getItem( cls );
		if (it == null) return null;
		int i = Command.indexOf( Dungeon.hero, it );
		return i < 0 ? null : Command.item( i, action );
	}

	private static boolean noneMapped(){
		for (boolean m : Dungeon.level.mapped) if (m) return false;
		return true;
	}

	/** nearest known cell, by walking over known cells, that borders a cell the hero does not know yet */
	private int nearestUnvisited(){
		int len = Dungeon.level.length();
		boolean[] known = new boolean[len];
		boolean[] pass = new boolean[len];
		for (int i = 0; i < len; i++){
			known[i] = Dungeon.level.visited[i] || Dungeon.level.mapped[i];
			pass[i] = known[i] && Dungeon.level.passable[i];
		}
		PathFinder.buildDistanceMap( Dungeon.hero.pos, pass );
		int best = -1, bd = Integer.MAX_VALUE;
		for (int i = 0; i < len; i++){
			int d = PathFinder.distance[i];
			if (!pass[i] || d <= 0 || d >= bd || failed( i )) continue;
			for (int n : PathFinder.NEIGHBOURS8){
				int c = i + n;
				if (c >= 0 && c < len && !known[c]){ bd = d; best = i; break; }
			}
		}
		return best;
	}

}
