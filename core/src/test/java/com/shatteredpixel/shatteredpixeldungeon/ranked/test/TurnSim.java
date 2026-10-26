package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.watabou.utils.PathFinder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spike S2/S5: drives the actor loop on the calling thread, without GameScene or the actor thread.
 * The hero follows a fixed policy (attack adjacent enemy, rest when hurt, else walk to the exit);
 * on reaching the exit the next floor is generated and entered directly.
 */
public final class TurnSim {

	public static final class Result {
		public final List<String> turnDigests = new ArrayList<>();
		public int heroTurns, actorSteps, floorsReached = 1;
		public long genNanos, simNanos;
		public String stop = "turn limit";
	}

	private static final Field ALL, CURRENT, NOW;
	static {
		try {
			ALL = Actor.class.getDeclaredField("all");
			CURRENT = Actor.class.getDeclaredField("current");
			NOW = Actor.class.getDeclaredField("now");
			ALL.setAccessible(true); CURRENT.setAccessible(true); NOW.setAccessible(true);
		} catch (NoSuchFieldException e){
			throw new RuntimeException(e);
		}
	}

	private TurnSim(){}

	/** Same selection rule as Actor.process(): earliest time, ties by higher actPriority. */
	@SuppressWarnings("unchecked")
	static Actor pickNext() throws IllegalAccessException {
		Actor cur = null;
		float earliest = Float.MAX_VALUE;
		synchronized (Actor.class){
			for (Actor a : (Collection<Actor>) ALL.get(null)){
				float t = time(a);
				if (t < earliest || t == earliest && (cur == null || priority(a) > priority(cur))){
					earliest = t;
					cur = a;
				}
			}
		}
		return cur;
	}

	private static final Field PRIO, TIME;
	static {
		try {
			PRIO = Actor.class.getDeclaredField("actPriority");
			TIME = Actor.class.getDeclaredField("time");
			PRIO.setAccessible(true); TIME.setAccessible(true);
		} catch (NoSuchFieldException e){
			throw new RuntimeException(e);
		}
	}
	private static final java.lang.reflect.Method ACT;
	static {
		try {
			ACT = Actor.class.getDeclaredMethod("act");
			ACT.setAccessible(true);
		} catch (NoSuchMethodException e){
			throw new RuntimeException(e);
		}
	}
	static boolean act(Actor a){
		try {
			return (Boolean) ACT.invoke(a);
		} catch (java.lang.reflect.InvocationTargetException e){
			if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
			throw new RuntimeException(e.getCause());
		} catch (IllegalAccessException e){
			throw new RuntimeException(e);
		}
	}
	static int priority(Actor a) throws IllegalAccessException { return PRIO.getInt(a); }
	static float time(Actor a) throws IllegalAccessException { return TIME.getFloat(a); }

	public static Result run(long seed, HeroClass cls, int maxHeroTurns, int maxDepth){
		try {
			return run0(seed, cls, maxHeroTurns, maxDepth);
		} catch (Exception e){
			throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
		}
	}

	private static Result run0(long seed, HeroClass cls, int maxHeroTurns, int maxDepth) throws Exception {
		Result r = new Result();
		resting = false;
		frames = 0;
		Headless.newRun(seed, cls, Headless.Profile.VETERAN);
		long t0 = System.nanoTime();
		Level l = Headless.generate(1);
		r.genNanos += System.nanoTime() - t0;
		enter(l, -1);
		long s0 = System.nanoTime();
		int lastHeroTurn = -1;
		while (r.heroTurns < maxHeroTurns){
			if (!Dungeon.hero.isAlive()){ r.stop = "hero died depth " + Dungeon.depth; break; }
			Actor a = pickNext();
			if (a == null){ r.stop = "no actors"; break; }
			if (r.actorSteps > 200_000){ r.stop = "step limit, stuck on " + a.getClass().getSimpleName() + " t=" + time(a) + " heroAction=" + Dungeon.hero.curAction; break; }
			ensureSprites();
			NOW.setFloat(null, time(a));
			CURRENT.set(null, a);
			if (a == Dungeon.hero && Dungeon.hero.curAction == null && !Dungeon.hero.resting){
				if (r.heroTurns != lastHeroTurn){
					r.turnDigests.add(stateDigest());
					lastHeroTurn = r.heroTurns;
				}
				int exit = exitCell();
				if (Dungeon.hero.pos == exit){
					if (Dungeon.depth >= maxDepth){ r.stop = "reached depth " + maxDepth; break; }
					RngLog.mark("descend " + (Dungeon.depth + 1));
					// like InterlevelScene: leave GameScene before the next floor is generated
					com.watabou.noosa.Game.scene().destroy();
					SCENE.set(new com.watabou.noosa.Scene());
					Dungeon.saveAll();
					long g = System.nanoTime();
					Dungeon.depth++;
					Level next = Dungeon.newLevel();
					r.genNanos += System.nanoTime() - g;
					enter(next, -1);
					r.floorsReached = Dungeon.depth;
					continue;
				}
				HeroAction act = choose(exit);
				RngLog.mark("turn " + r.heroTurns + " " + (act == null ? "Rest" : act.getClass().getSimpleName()) + " " + Dungeon.hero.pos);
				r.heroTurns++;
				if (act == null){ Dungeon.hero.rest(false); r.actorSteps++; continue; }
				Dungeon.hero.curAction = act;
				float before = time(Dungeon.hero);
				step(a, r);
				if (time(Dungeon.hero) == before && Dungeon.hero.curAction == null){
					// action was impossible (no path, blocked): rest instead so time moves on
					Dungeon.hero.rest(false);
				}
				continue;
			}
			step(a, r);
		}
		r.simNanos = System.nanoTime() - s0;
		r.turnDigests.add(stateDigest());
		return r;
	}

	/** GameScene normally creates sprites; without it, attach them ourselves (no GL draws happen). */
	static void ensureSprites(){
		ensureGame();
		if (com.watabou.noosa.Camera.main == null){
			com.watabou.noosa.Camera.main = new com.watabou.noosa.Camera(0, 0, 640, 360, 2);
		}
		if (Dungeon.hero.sprite == null){
			com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite hs = new com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite();
			hs.link(Dungeon.hero);
		}
		for (Char c : Actor.chars()){
			if (c.sprite == null && c instanceof Mob){
				com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite cs = ((Mob) c).sprite();
				cs.link(c);
			}
		}
	}

	public static final float FRAME = 1f / Integer.getInteger("spdr.fps", 30);
	public static final int MAX_FRAMES = 900;
	public static long frames;

	/**
	 * Acts like Actor.process(): wait for a moving sprite first, then act; if the actor did not
	 * spend time (its turn ends in a sprite callback: attacks, zaps, missiles), run scene frames
	 * until it does. Frames are fixed-length, so this is deterministic.
	 */
	static void step(Actor a, Result r) throws IllegalAccessException {
		if (a instanceof Char && ((Char) a).sprite != null){
			for (int f = 0; f < MAX_FRAMES && ((Char) a).sprite.isMoving; f++) frame();
		}
		float before = time(a);
		act(a);
		r.actorSteps++;
		for (int f = 0; f < MAX_FRAMES && time(a) == before && pendingCallback(a); f++) frame();
	}

	private static boolean pendingCallback(Actor a){
		if (a == Dungeon.hero) return Dungeon.hero.curAction != null || !Dungeon.hero.ready;
		return a instanceof Char && ((Char) a).isAlive();
	}

	private static final Field MEMBERS;
	static {
		try {
			MEMBERS = com.watabou.noosa.Group.class.getDeclaredField("members");
			MEMBERS.setAccessible(true);
		} catch (NoSuchFieldException e){
			throw new RuntimeException(e);
		}
	}

	/** One fixed frame of the scene graph (sprites, tweens, effects), skipping GameScene.update's actor thread. */
	@SuppressWarnings("unchecked")
	static void frame() throws IllegalAccessException {
		com.watabou.noosa.Game.elapsed = FRAME;
		com.watabou.noosa.Game.timeTotal += FRAME;
		java.util.List<com.watabou.noosa.Gizmo> ms = (java.util.List<com.watabou.noosa.Gizmo>) MEMBERS.get(com.watabou.noosa.Game.scene());
		for (int i = 0; i < ms.size(); i++){
			com.watabou.noosa.Gizmo g = ms.get(i);
			if (g != null && g.exists && g.active) g.update();
		}
		frames++;
	}

	private static void enter(Level l, int pos){
		Dungeon.level = l;
		Dungeon.switchLevel(l, pos);
		// the bot knows the map (like Magic Mapping), so pathing to the exit always works
		java.util.Arrays.fill(l.mapped, true);
		buildScene();
	}

	/** Builds a real GameScene (UI, sprites) on the headless mock GL, like InterlevelScene would. */
	static void buildScene(){
		ensureGame();
		com.watabou.noosa.Game.width = 1280;
		com.watabou.noosa.Game.height = 720;
		com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene gs = new com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene();
		com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.mode = com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.Mode.DESCEND;
		SCENE.set(gs);
		gs.create();
	}

	static final class SceneSetter extends com.watabou.noosa.Game {
		SceneSetter(){ super(com.watabou.noosa.Scene.class, new HeadlessPlatform()); requestedReset = false; scene = new com.watabou.noosa.Scene(); }
		static void set(com.watabou.noosa.Scene s){ ((SceneSetter) instance).scene = s; }
	}
	static final class SCENE { static void set(com.watabou.noosa.Scene s){ SceneSetter.set(s); } }

	static void ensureGame(){
		if (com.watabou.noosa.Game.instance == null) new SceneSetter();
		if (com.badlogic.gdx.Gdx.gl == null){
			// no-op GL: every call returns 0/false/null, nothing is ever rendered
			com.badlogic.gdx.graphics.GL20 gl = (com.badlogic.gdx.graphics.GL20) java.lang.reflect.Proxy.newProxyInstance(
					TurnSim.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.graphics.GL20.class},
					(proxy, m, args) -> {
						Class<?> rt = m.getReturnType();
						if (rt == int.class) return 0;
						if (rt == boolean.class) return false;
						if (rt == float.class) return 0f;
						if (rt == long.class) return 0L;
						if (rt == String.class) return "";
						return null;
					});
			com.badlogic.gdx.Gdx.gl = com.badlogic.gdx.Gdx.gl20 = gl;
		}
	}

	private static int exitCell(){
		LevelTransition t = Dungeon.level.getTransition(LevelTransition.Type.REGULAR_EXIT);
		return t == null ? Dungeon.hero.pos : t.cell();
	}

	private static boolean resting;

	private static HeroAction choose(int exit){
		Char enemy = null;
		for (int n : PathFinder.NEIGHBOURS8){
			Char c = Actor.findChar(Dungeon.hero.pos + n);
			if (c instanceof Mob && c.alignment == Char.Alignment.ENEMY){
				if (enemy == null || c.id() < enemy.id()) enemy = c;
			}
		}
		if (enemy != null) return new HeroAction.Attack(enemy);
		if (Dungeon.hero.HP < Dungeon.hero.HT * 7 / 10 || (resting && Dungeon.hero.HP < Dungeon.hero.HT)){
			resting = true;
			return null;
		}
		resting = false;
		return new HeroAction.Move(exit);
	}

	/** Cheap gameplay digest: depth, time, hero, every char and heap. */
	static String stateDigest(){
		StringBuilder sb = new StringBuilder();
		sb.append(Dungeon.depth).append('|').append(Actor.now()).append('|');
		List<String> cs = new ArrayList<>();
		for (Char c : Actor.chars()) cs.add(c.getClass().getSimpleName() + "@" + c.pos + ":" + c.HP);
		java.util.Collections.sort(cs);
		sb.append(cs);
		List<String> hs = new ArrayList<>();
		for (com.shatteredpixel.shatteredpixeldungeon.items.Heap h : Dungeon.level.heaps.valueList()) hs.add(h.pos + ":" + h.items.size());
		java.util.Collections.sort(hs);
		sb.append(hs);
		return sb.toString();
	}
}
