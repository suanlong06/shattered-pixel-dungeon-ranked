package com.shatteredpixel.shatteredpixeldungeon.ranked.sim;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import com.watabou.utils.PlatformSupport;

import java.lang.reflect.Proxy;

/**
 * spd-ranked (T0.8): the Game instance used when the game runs without a window (tests,
 * replay server). The libGDX application itself (headless backend) is started by the host.
 * Rendering calls go to a GL that does nothing; scenes, sprites and tweens still update.
 */
public final class HeadlessGame extends Game {

	private HeadlessGame( PlatformSupport platform ){
		super( Scene.class, platform );
		requestedReset = false;
		scene = new Scene();
	}

	/** Idempotent. The platform only needs to provide (no) fonts; text is never drawn. */
	public static void install( PlatformSupport platform ){
		if (!(Game.instance instanceof HeadlessGame)) new HeadlessGame( platform );
		if (Gdx.gl == null){
			GL20 gl = (GL20) Proxy.newProxyInstance( HeadlessGame.class.getClassLoader(), new Class<?>[]{ GL20.class },
					(proxy, m, args) -> {
						Class<?> rt = m.getReturnType();
						if (rt == int.class) return 0;
						if (rt == boolean.class) return false;
						if (rt == float.class) return 0f;
						if (rt == long.class) return 0L;
						if (rt == String.class) return "";
						return null;
					});
			Gdx.gl = Gdx.gl20 = gl;
		}
		Game.width = 1280;
		Game.height = 720;
		if (Camera.main == null) Camera.main = new Camera( 0, 0, Game.width, Game.height, 2 );
	}

	static HeadlessGame get(){
		return (HeadlessGame) Game.instance;
	}

	void setScene( Scene s ){
		scene = s;
	}

	boolean switchRequested(){
		return requestedReset;
	}

	/** the scene a pending switch goes to */
	Class<? extends Scene> requestedScene(){
		return sceneClass;
	}

	private java.lang.reflect.Method executeRunnables;

	/**
	 * Runs the callbacks posted with Game.runOnRenderThread, as the render loop does once per frame.
	 * The headless backend has no loop of its own here (updatesPerSecond -1). Returns true if any ran.
	 */
	boolean runPostedCallbacks(){
		try {
			if (executeRunnables == null) executeRunnables = Gdx.app.getClass().getMethod( "executeRunnables" );
			return (Boolean) executeRunnables.invoke( Gdx.app );
		} catch (ReflectiveOperationException e){
			throw new IllegalStateException( "headless backend without executeRunnables", e );
		}
	}

	/** True once if a scene switch was requested since the last call (e.g. a level transition). */
	boolean consumeSceneSwitch(){
		boolean r = requestedReset;
		requestedReset = false;
		return r;
	}
}
