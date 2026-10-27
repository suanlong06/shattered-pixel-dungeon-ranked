package com.shatteredpixel.shatteredpixeldungeon.ranked.replay;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;

import java.util.ArrayList;
import java.util.List;

/**
 * spd-ranked (A5): a replay is everything needed to play a run again on the deterministic core
 * (T0.8): seed, class, mode, game version and the recorded commands. Watching it means feeding
 * the commands back through TurnRunner; the same file is what the server re-simulates (A3).
 *
 * Text form:
 * <pre>
 * spdr-replay v1
 * version=&lt;game version code&gt;
 * seed=&lt;seed&gt;
 * class=&lt;HeroClass&gt;
 * mode=&lt;GameMode&gt;
 * digest=&lt;final trace digest, may be empty&gt;
 * ---
 * &lt;one command per line, CommandLog format&gt;
 * </pre>
 * Unknown header keys are ignored so later versions can add some.
 */
public final class ReplayFile {

	public static final int VERSION = 1;
	private static final String MAGIC = "spdr-replay v";

	public final int gameVersion;
	public final long seed;
	public final HeroClass heroClass;
	public final GameMode mode;
	public final String digest;
	public final List<Command> commands;

	public ReplayFile( int gameVersion, long seed, HeroClass heroClass, GameMode mode, String digest, List<Command> commands ){
		this.gameVersion = gameVersion;
		this.seed = seed;
		this.heroClass = heroClass;
		this.mode = mode;
		this.digest = digest == null ? "" : digest;
		this.commands = new ArrayList<>( commands );
	}

	public String toText(){
		return MAGIC + VERSION + "\n"
				+ "version=" + gameVersion + "\n"
				+ "seed=" + seed + "\n"
				+ "class=" + heroClass.name() + "\n"
				+ "mode=" + mode.name() + "\n"
				+ "digest=" + digest + "\n"
				+ "---\n"
				+ CommandLog.toText( commands );
	}

	public static ReplayFile fromText( String text ){
		text = text.replace( "\r", "" );
		if (!text.startsWith( MAGIC )) throw new IllegalArgumentException( "not a replay" );
		int split = text.indexOf( "\n---\n" );
		if (split < 0) throw new IllegalArgumentException( "replay without commands section" );
		int version = -1;
		long seed = 0;
		HeroClass cls = null;
		GameMode mode = GameMode.SOLO;
		String digest = "";
		for (String line : text.substring( 0, split ).split( "\n" )){
			int eq = line.indexOf( '=' );
			if (eq < 0) continue;
			String k = line.substring( 0, eq ), v = line.substring( eq + 1 );
			switch (k){
				case "version": version = Integer.parseInt( v ); break;
				case "seed":    seed = Long.parseLong( v ); break;
				case "class":   cls = HeroClass.valueOf( v ); break;
				case "mode":    mode = GameMode.valueOf( v ); break;
				case "digest":  digest = v; break;
				default: break;
			}
		}
		if (cls == null) throw new IllegalArgumentException( "replay without class" );
		return new ReplayFile( version, seed, cls, mode, digest, CommandLog.fromText( text.substring( split + 5 ) ) );
	}
}
