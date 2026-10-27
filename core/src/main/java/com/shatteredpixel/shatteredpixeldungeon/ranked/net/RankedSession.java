package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.watabou.noosa.Game;
import com.watabou.utils.DeviceCompat;
import com.watabou.utils.GameSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * spd-ranked (A1 client): the game side of a ranked Duel. Owns the {@link RankedClient},
 * moves its events onto the render thread, starts the run on START and reports progress.
 * <p>
 * {@link #update()} runs every frame while the queue window (WndRankedQueue) or the ranked
 * game scene (RankedHud) is up; it is the only place that reads or changes game state.
 */
public final class RankedSession {

	/** ranked runs live in their own save slot, after Boss Practice's */
	public static final int SLOT = GamesInProgress.MAX_SLOTS + 2;

	/** reports wait this long after START: the server only takes them once its countdown ends */
	static final long COUNTDOWN_GRACE_MS = 6_000;

	private static final String KEY_SERVER = "ranked_server";
	private static final String KEY_DEVICE = "ranked_device_key";
	private static final String KEY_NAME = "ranked_name";
	public static final String DEFAULT_SERVER = "127.0.0.1:7420";

	private static RankedSession current;

	private final ConcurrentLinkedQueue<Runnable> events = new ConcurrentLinkedQueue<>();
	private final RankedClient client;

	private RankedClient.State state = RankedClient.State.OFFLINE;
	private String status = "";
	private String error;
	private String opponent;
	private int opponentRating;
	private int finalBossDepth = 15;

	// opponent ghost (OPPONENT frames)
	private int oppDepth = 1, oppBosses;
	private boolean oppDead, oppConnected = true;

	private ProgressTracker tracker;
	private long startedAt = -1;
	/** commands of this run already handed to the client (A3) */
	private int logged;
	private String heroClass;
	private String result;

	private RankedSession( RankedClient.Config cfg ){
		client = new RankedClient( cfg, new RankedClient.Listener(){
			@Override public void onFrame( NetFrame f ){ events.add( () -> frame( f ) ); }
			@Override public void onState( RankedClient.State s ){ events.add( () -> state( s ) ); }
			@Override public void onError( String message ){ events.add( () -> error = message ); }
		} );
	}

	public static RankedSession current(){ return current; }

	/** connects and joins the queue once the server welcomes us */
	public static RankedSession queue(){
		if (current != null) current.client.close();
		current = new RankedSession( config() );
		current.client.start();
		return current;
	}

	/** leaves the queue or abandons a match that has not started */
	public void cancel(){
		if (state == RankedClient.State.QUEUED) client.leaveQueue();
		client.close();
		if (current == this) current = null;
	}

	public void accept(){ client.ready(); }
	public void resign(){ client.resign(); }

	public RankedClient.State state(){ return state; }
	public String error(){ return error; }
	public String opponent(){ return opponent; }
	public int opponentRating(){ return opponentRating; }
	public int rating(){ return client.rating(); }
	public String result(){ return result; }
	public int opponentDepth(){ return oppDepth; }
	public int opponentBosses(){ return oppBosses; }
	public boolean opponentDead(){ return oppDead; }
	public boolean opponentConnected(){ return oppConnected; }
	/** true while a ranked run is being played or waiting for its RESULT */
	public boolean inRun(){ return startedAt >= 0 && result == null; }

	/** render thread, every frame */
	public void update(){
		Runnable r;
		while ((r = events.poll()) != null) r.run();
		if (inRun() && Features.isRanked() && Dungeon.hero != null
				&& System.currentTimeMillis() - startedAt >= COUNTDOWN_GRACE_MS && heroWaits()){
			report();
		}
	}

	/**
	 * A3: report only where the server's replay stops too, when the hero waits for input
	 * (or has died), so turns and floor match the replay of the log sent with the report
	 */
	private static boolean heroWaits(){
		return !Dungeon.hero.isAlive()
				|| (Dungeon.hero.ready && Dungeon.hero.curAction == null && !Dungeon.hero.resting);
	}

	private void state( RankedClient.State s ){
		state = s;
		if (s == RankedClient.State.IDLE) client.queue();
	}

	private void frame( NetFrame f ){
		switch (f.type){
			case NetFrame.MATCH_FOUND:
				opponent = f.s( "opponent" );
				opponentRating = (int)Math.round( f.d( "opponentRating", 0 ) );
				finalBossDepth = f.i( "finalBossDepth", finalBossDepth );
				break;
			case NetFrame.START:
				startRun( f.l( "seed", 0 ) );
				break;
			case NetFrame.OPPONENT:
				oppDepth = f.i( "depth", oppDepth );
				oppBosses = f.i( "bosses", oppBosses );
				oppDead = f.b( "dead" );
				oppConnected = f.b( "connected" );
				break;
			case NetFrame.RESULT:
				String outcome = f.s( "outcome" );
				CommandLog.stop();
				result = Messages.get( this, "result_" + (outcome == null ? "void" : outcome.toLowerCase( java.util.Locale.ROOT )) );
				if (f.data.has( "ratingAfter" )){
					result += " " + Messages.get( this, "rating_change",
							(int)Math.round( f.d( "ratingBefore", 0 ) ), (int)Math.round( f.d( "ratingAfter", 0 ) ) );
				}
				break;
			default:
				break;
		}
	}

	/** the seed arrives with START (L18); the run starts like Boss Practice, but in Ranked mode */
	private void startRun( long seed ){
		startedAt = System.currentTimeMillis();
		tracker = new ProgressTracker( finalBossDepth );
		Dungeon.deleteGame( SLOT, true );
		GamesInProgress.curSlot = SLOT;
		Dungeon.hero = null;
		Dungeon.daily = Dungeon.dailyReplay = false;
		Dungeon.initSeed();
		// initSeed sets Solo; Dungeon.init() keeps the mode we set here (as SeedScanner relies on)
		Features.setMode( GameMode.RANKED );
		Dungeon.seed = seed;
		Dungeon.customSeedText = "";
		ActionIndicator.clearAction();
		InterlevelScene.mode = InterlevelScene.Mode.DESCEND;
		// A3: every input of the run is recorded; the first LOG names the hero class
		heroClass = GamesInProgress.selectedClass.name();
		logged = 0;
		CommandLog.start();
		sendLog();
		Game.switchScene( InterlevelScene.class );
	}

	private void sendLog(){
		List<Command> rec = CommandLog.recorded();
		List<String> cmds = new ArrayList<>();
		for (int i = logged; i < rec.size(); i++) cmds.add( rec.get( i ).toString() );
		logged = rec.size();
		client.log( heroClass, cmds );
	}

	private void report(){
		int depth = Dungeon.depth;
		long turns = (long)Statistics.duration;
		ProgressTracker.Report rep = tracker.update( Math.max( depth, Statistics.deepestFloor ), turns,
				Statistics.bossScores, Dungeon.hero.isAlive() );
		if (rep != ProgressTracker.Report.NONE) sendLog();
		switch (rep){
			case PROGRESS: client.progress( tracker.depth(), tracker.turns(), tracker.bosses() ); break;
			case DIED:     client.died( tracker.depth(), tracker.turns(), tracker.bosses() ); break;
			case CLEARED:  client.cleared( tracker.depth(), tracker.turns(), tracker.bosses() ); break;
			default: break;
		}
	}

	// settings

	static RankedClient.Config config(){
		RankedClient.Config cfg = new RankedClient.Config();
		String server = GameSettings.getString( KEY_SERVER, DEFAULT_SERVER );
		int colon = server.lastIndexOf( ':' );
		cfg.host = colon > 0 ? server.substring( 0, colon ) : server;
		try {
			cfg.port = colon > 0 ? Integer.parseInt( server.substring( colon + 1 ) ) : cfg.port;
		} catch (NumberFormatException ignored){
		}
		// plain TCP is for a server on this machine or LAN only; anything else needs TLS
		cfg.tls = !(cfg.host.equals( "127.0.0.1" ) || cfg.host.equals( "localhost" ) || cfg.host.startsWith( "192.168." ) || cfg.host.startsWith( "10." ));
		cfg.build = buildFingerprint();
		cfg.platform = DeviceCompat.isDesktop() ? "desktop" : "android";
		cfg.deviceKey = deviceKey();
		cfg.name = GameSettings.getString( KEY_NAME, "Player-" + cfg.deviceKey.substring( 0, 6 ), 24 );
		return cfg;
	}

	/**
	 * Must equal the server's -Dspdr.build. First pass: version name and code; replay checks
	 * (A3) need the exact bytecode, so a release build should bake a class hash in here.
	 */
	public static String buildFingerprint(){
		return Game.version + "+" + Game.versionCode;
	}

	/** L2: the account is this device key plus a name; generated once and kept in settings */
	static String deviceKey(){
		String key = GameSettings.getString( KEY_DEVICE, "" );
		if (key.length() < 16){
			key = UUID.randomUUID().toString().replace( "-", "" );
			GameSettings.put( KEY_DEVICE, key );
		}
		return key;
	}
}
