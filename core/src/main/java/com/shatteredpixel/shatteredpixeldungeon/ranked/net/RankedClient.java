package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

/**
 * spd-ranked (A1 client): one connection to the ranked server, from HELLO to RESULT.
 * <p>
 * One daemon thread connects and reads frames, a second pings every 5 s. Frames and state
 * changes reach the {@link Listener} on the network thread; the game side (RankedSession)
 * hands them to the render thread. Nothing here touches game state.
 * <p>
 * Connection loss after START: the client retries (1 s, 2 s, 4 s ... 10 s) inside the
 * server's 5 minute window, then sends HELLO + RESUME with the match token. The newest
 * report is kept and sent again after RESUMED: progress only moves forward on the server,
 * so an equal report is harmless. Loss before START cannot be resumed (the server voids).
 */
public final class RankedClient {

	public enum State {
		OFFLINE,      // not connected, or gave up
		CONNECTING,   // socket opening, HELLO sent
		IDLE,         // WELCOME received
		QUEUED,
		MATCH_FOUND,  // waiting for both READY
		STARTED,      // START or RESUMED received: the run is on
		RECONNECTING, // lost the link mid-match, retrying inside the resume window
		FINISHED      // RESULT received
	}

	public interface Listener {
		void onFrame( NetFrame f );
		void onState( State s );
		void onError( String message );
	}

	public static final class Config {
		public String host = "127.0.0.1";
		public int port = 7420;
		public boolean tls = false;
		public String build;
		public String platform;
		public String deviceKey;
		public String name;
		/** A6: Skins.rankedSkin() and Skins.denylistHash(), checked by the server */
		public String skin = "";
		public String skinRules = "";
		public long pingMs = 5_000;
		/** the server counts 15 s of silence as a dead link; so do we */
		public int readTimeoutMs = 15_000;
		public int connectTimeoutMs = 10_000;
		/** a little under the server's 5 minutes, so the last try still lands inside it */
		public long resumeWindowMs = 5L * 60_000L - 5_000L;
		public long firstBackoffMs = 1_000;
		public long maxBackoffMs = 10_000;
	}

	private final Config cfg;
	private final Listener listener;
	private final Object writeLock = new Object();

	private volatile State state = State.OFFLINE;
	private volatile boolean closed;
	private volatile Socket socket;
	private OutputStream out;
	private Thread thread;
	private long seq;

	private volatile String matchId;
	private volatile String resumeToken;
	private volatile NetFrame lastReport;
	/** A3: this match's command log, as sent; the server replays it before rating */
	private final List<String> log = new ArrayList<>();
	private volatile String logClass;
	private volatile int rating = -1;
	private volatile long playerId = -1;
	private volatile long resumeLostAt = -1;

	public RankedClient( Config cfg, Listener listener ){
		this.cfg = cfg;
		this.listener = listener;
	}

	public State state(){ return state; }
	public String matchId(){ return matchId; }
	public int rating(){ return rating; }
	public long playerId(){ return playerId; }

	/** starts the network thread; HELLO goes out as soon as the socket is up */
	public synchronized void start(){
		if (thread != null) return;
		closed = false;
		setState( State.CONNECTING );
		thread = new Thread( this::run, "ranked-net" );
		thread.setDaemon( true );
		thread.start();
	}

	/**
	 * Rejoins a match after the app was closed: connects, then HELLO + RESUME with the saved
	 * token. {@code lostAt} is when the link went away, so the 5 minute window is not restarted.
	 */
	public synchronized void resume( String matchId, String resumeToken, long lostAt ){
		if (thread != null) return;
		this.matchId = matchId;
		this.resumeToken = resumeToken;
		this.resumeLostAt = lostAt;
		closed = false;
		setState( State.RECONNECTING );
		thread = new Thread( this::run, "ranked-net" );
		thread.setDaemon( true );
		thread.start();
	}

	public String resumeToken(){ return resumeToken; }

	public void queue(){ send( new NetFrame( NetFrame.QUEUE, null ) ); }

	public void leaveQueue(){
		send( new NetFrame( NetFrame.LEAVE_QUEUE, null ) );
		if (state == State.QUEUED) setState( State.IDLE );
	}

	public void ready(){ send( match( NetFrame.READY, null ) ); }
	public void resign(){ report( match( NetFrame.RESIGN, null ) ); }

	/**
	 * A3: appends commands to the match log and sends them. Call before the report they lead
	 * up to (the server compares its replay of the whole log with the last report).
	 */
	public void log( String heroClass, List<String> cmds ){
		synchronized (log){
			int from = log.size();
			logClass = heroClass;
			log.addAll( cmds );
			if (state != State.RECONNECTING) sendLog( from );
		}
	}

	/** LOG frames from index from to the end, split to stay well under the frame limit; at least one */
	private void sendLog( int from ){
		synchronized (log){
			int i = Math.max( 0, Math.min( from, log.size() ) );
			do {
				int start = i;
				StringBuilder sb = new StringBuilder();
				while (i < log.size() && sb.length() < NetFrame.MAX_FRAME / 2){
					sb.append( log.get( i++ ) ).append( '\n' );
				}
				send( match( NetFrame.LOG, NetFrame.obj( "from", start, "cls", logClass, "cmds", sb.toString() ) ) );
			} while (i < log.size());
		}
	}

	/** A5: the run summary, best effort (dropped while reconnecting); send before the last report */
	public void telemetry( String body ){
		if (state != State.RECONNECTING) send( match( NetFrame.TELEMETRY, NetFrame.obj( "body", body ) ) );
	}

	public void progress( int depth, long turns, int bosses ){ report( progressFrame( NetFrame.PROGRESS, depth, turns, bosses ) ); }
	public void died( int depth, long turns, int bosses ){ report( progressFrame( NetFrame.DIED, depth, turns, bosses ) ); }
	public void cleared( int depth, long turns, int bosses ){ report( progressFrame( NetFrame.CLEARED, depth, turns, bosses ) ); }

	/** stops for good: no reconnect, no resume */
	public void close(){
		closed = true;
		closeQuietly( socket );
		Thread t;
		synchronized (this){
			t = thread;
			thread = null;
		}
		if (t != null) t.interrupt();
		setState( State.OFFLINE );
	}

	/** drops the socket as if the network failed; the client then tries to resume. For tests and debugging. */
	public void dropLink(){
		closeQuietly( socket );
	}

	private NetFrame progressFrame( String type, int depth, long turns, int bosses ){
		return match( type, NetFrame.obj( "depth", depth, "turns", turns, "bosses", bosses ) );
	}

	private NetFrame match( String type, JSONObject data ){
		return new NetFrame( type, 0, matchId, data );
	}

	private void report( NetFrame f ){
		lastReport = f;
		// while reconnecting, the report waits for RESUMED
		if (state != State.RECONNECTING) send( f );
	}

	private void send( NetFrame f ){
		synchronized (writeLock){
			if (out == null) return;
			try {
				NetFrame.write( out, new NetFrame( f.type, ++seq, f.matchId, f.data ) );
			} catch (IOException e){
				closeQuietly( socket );
			}
		}
	}

	private void run(){
		long lostAt = resumeLostAt;
		long backoff = cfg.firstBackoffMs;
		while (!closed){
			try {
				session();
			} catch (IOException e){
				// decided below
			} finally {
				synchronized (writeLock){ out = null; }
				closeQuietly( socket );
				socket = null;
			}
			if (closed) break;
			boolean inMatch = resumeToken != null && (state == State.STARTED || state == State.RECONNECTING);
			if (!inMatch){
				if (state != State.FINISHED) setState( State.OFFLINE );
				break;
			}
			long now = System.currentTimeMillis();
			if (lostAt < 0 || state == State.STARTED){
				// first loss, or resumed and lost again: a fresh window, as on the server
				lostAt = now;
				backoff = cfg.firstBackoffMs;
				setState( State.RECONNECTING );
			}
			if (now - lostAt > cfg.resumeWindowMs){
				resumeToken = null;
				listener.onError( "connection lost for too long" );
				setState( State.OFFLINE );
				break;
			}
			try {
				Thread.sleep( backoff );
			} catch (InterruptedException e){
				break;
			}
			backoff = Math.min( backoff * 2, cfg.maxBackoffMs );
		}
		synchronized (this){
			if (thread == Thread.currentThread()) thread = null;
		}
	}

	private void session() throws IOException {
		Socket s = cfg.tls ? SSLSocketFactory.getDefault().createSocket() : new Socket();
		socket = s;
		if (closed) throw new IOException( "closed" );
		s.connect( new InetSocketAddress( cfg.host, cfg.port ), cfg.connectTimeoutMs );
		s.setSoTimeout( cfg.readTimeoutMs );
		s.setTcpNoDelay( true );
		InputStream in = new BufferedInputStream( s.getInputStream() );
		synchronized (writeLock){ out = new BufferedOutputStream( s.getOutputStream() ); }
		send( new NetFrame( NetFrame.HELLO, NetFrame.obj(
				"protocol", NetFrame.PROTOCOL, "build", cfg.build, "platform", cfg.platform,
				"deviceKey", cfg.deviceKey, "name", cfg.name, "skin", cfg.skin, "skinRules", cfg.skinRules ) ) );
		Thread pinger = new Thread( () -> {
			try {
				while (socket == s && !s.isClosed()){
					Thread.sleep( cfg.pingMs );
					send( new NetFrame( NetFrame.PING, null ) );
				}
			} catch (InterruptedException ignored){
			}
		}, "ranked-ping" );
		pinger.setDaemon( true );
		pinger.start();
		try {
			NetFrame f;
			while ((f = NetFrame.read( in )) != null){
				handle( f );
				if (state == State.FINISHED) return;
			}
		} finally {
			pinger.interrupt();
		}
	}

	private void handle( NetFrame f ){
		switch (f.type){
			case NetFrame.PONG:
				return;
			case NetFrame.WELCOME:
				playerId = f.l( "playerId", -1 );
				rating = (int)Math.round( f.d( "rating", -1 ) );
				if (state == State.RECONNECTING && resumeToken != null){
					send( new NetFrame( NetFrame.RESUME, NetFrame.obj( "resumeToken", resumeToken ) ) );
				} else {
					setState( State.IDLE );
				}
				break;
			case NetFrame.QUEUED:
				setState( State.QUEUED );
				break;
			case NetFrame.MATCH_FOUND:
				matchId = f.matchId;
				resumeToken = f.s( "resumeToken" );
				lastReport = null;
				synchronized (log){ log.clear(); }
				setState( State.MATCH_FOUND );
				break;
			case NetFrame.START:
				setState( State.STARTED );
				break;
			case NetFrame.RESUMED:
				setState( State.STARTED );
				NetFrame r = lastReport;
				// the log first (from what the server has), then the report it leads to
				if (logClass != null) sendLog( f.i( "logCount", 0 ) );
				if (r != null) send( r );
				break;
			case NetFrame.RESULT:
				resumeToken = null;
				setState( State.FINISHED );
				break;
			case NetFrame.ERROR:
				listener.onError( f.s( "message" ) );
				if (state == State.RECONNECTING){
					// the match is over or the token is gone: nothing left to resume
					resumeToken = null;
					setState( State.OFFLINE );
					closeQuietly( socket );
				}
				break;
			default:
				break;
		}
		listener.onFrame( f );
	}

	private void setState( State s ){
		if (state == s) return;
		state = s;
		listener.onState( s );
	}

	private static void closeQuietly( Socket s ){
		if (s == null) return;
		try {
			s.close();
		} catch (IOException ignored){
		}
	}
}
