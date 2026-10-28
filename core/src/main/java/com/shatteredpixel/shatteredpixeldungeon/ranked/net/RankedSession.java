package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ranked.Features;
import com.shatteredpixel.shatteredpixeldungeon.ranked.GameMode;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.Command;
import com.shatteredpixel.shatteredpixeldungeon.ranked.sim.CommandLog;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.watabou.noosa.Game;
import com.watabou.utils.DeviceCompat;
import com.watabou.utils.GameSettings;

import org.json.JSONException;
import org.json.JSONObject;

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
	/** the running match, kept so it can be rejoined after the app was closed (JSON) */
	private static final String KEY_RESUME = "ranked_resume";
	/** the server forfeits a side after 5 minutes away */
	static final long RESUME_WINDOW_MS = 5L * 60_000L;
	/** how often the saved match is stamped as still alive */
	private static final long SEEN_EVERY_MS = 10_000;
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
	private boolean rejoining;
	private long seenSaved;

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

	/** true if a ranked match was left running when the app closed, and can still be resumed */
	public static boolean canRejoin(){
		JSONObject saved = saved();
		return saved != null
				&& System.currentTimeMillis() - saved.optLong( "seen", 0 ) < RESUME_WINDOW_MS
				&& GamesInProgress.check( SLOT ) != null;
	}

	/** reconnects to the saved match; the saved run loads once the server answers RESUMED */
	public static RankedSession rejoin(){
		JSONObject saved = saved();
		if (current != null) current.client.close();
		current = new RankedSession( config() );
		current.rejoining = true;
		current.opponent = saved.optString( "opponent", null );
		current.finalBossDepth = saved.optInt( "finalBossDepth", current.finalBossDepth );
		current.client.resume( saved.optString( "matchId" ), saved.optString( "token" ), saved.optLong( "seen", 0 ) );
		return current;
	}

	/** leaves the queue or abandons a match that has not started */
	public void cancel(){
		if (state == RankedClient.State.QUEUED) client.leaveQueue();
		client.close();
		if (current == this) current = null;
	}

	/**
	 * A6: true from connecting to the result. The server checks the skin at HELLO (and again on
	 * every reconnect), so it may not change in between.
	 */
	public static boolean locksSkin(){
		RankedSession s = current;
		return s != null && s.state != RankedClient.State.OFFLINE && s.state != RankedClient.State.FINISHED;
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
		long now = System.currentTimeMillis();
		if (inRun() && Features.isRanked() && Dungeon.hero != null
				&& now - startedAt >= COUNTDOWN_GRACE_MS && heroWaits()){
			report();
		}
		if (inRun() && now - seenSaved >= SEEN_EVERY_MS) save();
	}

	/**
	 * A3: report only where the server's replay stops too, when the hero waits for input
	 * (or has died), so turns and floor match the replay of the log sent with the report
	 */
	private static boolean heroWaits(){
		// lying dead while WndResurrect asks what to keep is not death yet (the replay agrees)
		if (!Dungeon.hero.isAlive()) return WndResurrect.instance == null;
		return Dungeon.hero.ready && Dungeon.hero.curAction == null && !Dungeon.hero.resting;
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
				save();
				break;
			case NetFrame.RESUMED:
				if (rejoining){
					rejoining = false;
					loadRun();
				}
				break;
			case NetFrame.ERROR:
				// the saved match is over or unknown to the server: nothing left to rejoin
				if (rejoining || client.resumeToken() == null) forget();
				break;
			case NetFrame.OPPONENT:
				oppDepth = f.i( "depth", oppDepth );
				oppBosses = f.i( "bosses", oppBosses );
				oppDead = f.b( "dead" );
				oppConnected = f.b( "connected" );
				break;
			case NetFrame.RESULT:
				forget();
				String outcome = f.s( "outcome" );
				saveReplay( CommandLog.stop(), outcome );
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

	/** A5: the finished match goes to the replay list (replays/) */
	private void saveReplay( List<Command> log, String outcome ){
		if (log == null || log.isEmpty() || Dungeon.hero == null || !Features.isRanked()) return;
		if (Game.instance instanceof com.shatteredpixel.shatteredpixeldungeon.ranked.sim.HeadlessGame) return;
		String label = (opponent == null ? "" : Messages.get( this, "replay_vs", opponent ) + " - ")
				+ Messages.get( this, "replay_" + (outcome == null ? "void" : outcome.toLowerCase( java.util.Locale.ROOT )) );
		com.shatteredpixel.shatteredpixeldungeon.ranked.replay.Replays.save( new com.shatteredpixel.shatteredpixeldungeon.ranked.replay.ReplayFile(
				Game.versionCode, Dungeon.seed, Dungeon.hero.heroClass, GameMode.RANKED, "", log, Dungeon.challenges, label ),
				System.currentTimeMillis() );
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

	/** rejoin after a restart: the run continues from its save, the server holds the match state */
	private void loadRun(){
		startedAt = System.currentTimeMillis() - COUNTDOWN_GRACE_MS;
		tracker = new ProgressTracker( finalBossDepth );
		save();
		// A3: the log is saved with the game (CommandLog.store) and comes back when the save loads;
		// the next LOG sends it again from the start and the server keeps what it lacks
		heroClass = null;
		logged = 0;
		CommandLog.start();
		GamesInProgress.curSlot = SLOT;
		Dungeon.hero = null;
		Dungeon.daily = Dungeon.dailyReplay = false;
		ActionIndicator.clearAction();
		InterlevelScene.mode = InterlevelScene.Mode.CONTINUE;
		Game.switchScene( InterlevelScene.class );
	}

	private void save(){
		seenSaved = System.currentTimeMillis();
		if (client.resumeToken() == null) return;
		JSONObject o = new JSONObject();
		try {
			o.put( "matchId", client.matchId() );
			o.put( "token", client.resumeToken() );
			o.put( "opponent", opponent );
			o.put( "finalBossDepth", finalBossDepth );
			o.put( "seen", seenSaved );
		} catch (JSONException e){
			return;
		}
		GameSettings.put( KEY_RESUME, o.toString() );
	}

	private static void forget(){
		GameSettings.put( KEY_RESUME, "" );
	}

	private static JSONObject saved(){
		String s = GameSettings.getString( KEY_RESUME, "" );
		if (s.isEmpty()) return null;
		try {
			JSONObject o = new JSONObject( s );
			return o.has( "token" ) && o.has( "matchId" ) ? o : null;
		} catch (JSONException e){
			return null;
		}
	}

	private void sendLog(){
		if (heroClass == null && Dungeon.hero != null) heroClass = Dungeon.hero.heroClass.name();
		// A3: the save must hold every command the server has, or a rejoin after a crash would play
		// on from an older state than the server's log; in Ranked a save does not change the game
		if (Dungeon.hero != null && Dungeon.level != null && Game.scene() instanceof GameScene){
			try {
				Dungeon.saveAll();
			} catch (java.io.IOException e){
				ShatteredPixelDungeon.reportException( e );
			}
		}
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
				Statistics.bossScores, Dungeon.hero.isAlive() || WndResurrect.instance != null );
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
		cfg.skin = com.shatteredpixel.shatteredpixeldungeon.ranked.skins.Skins.rankedSkin();
		cfg.skinRules = com.shatteredpixel.shatteredpixeldungeon.ranked.skins.Skins.denylistHash();
		cfg.deviceKey = deviceKey();
		cfg.name = GameSettings.getString( KEY_NAME, "Player-" + cfg.deviceKey.substring( 0, 6 ), 24 );
		return cfg;
	}

	/**
	 * Must equal the server's build. Replay checks (A3) need the exact bytecode, so the build
	 * writes a SHA-256 of core's compiled classes into {@link #FINGERPRINT_RESOURCE}; the server
	 * reads the same resource from its core jar. Version name and code are only a fallback for
	 * builds that skipped that step (an IDE run without Gradle).
	 */
	public static String buildFingerprint(){
		if (fingerprint == null){
			String fp = null;
			try (java.io.InputStream in = RankedSession.class.getResourceAsStream( FINGERPRINT_RESOURCE )){
				if (in != null){
					java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
					byte[] buf = new byte[256];
					int n;
					while ((n = in.read( buf )) > 0) b.write( buf, 0, n );
					fp = b.toString( "UTF-8" ).trim();
				}
			} catch (java.io.IOException ignored){
			}
			fingerprint = fp == null || fp.isEmpty() ? Game.version + "+" + Game.versionCode : fp;
		}
		return fingerprint;
	}

	public static final String FINGERPRINT_RESOURCE = "/ranked/build-fingerprint.txt";
	private static String fingerprint;

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
