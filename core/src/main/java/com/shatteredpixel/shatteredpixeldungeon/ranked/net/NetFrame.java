package com.shatteredpixel.shatteredpixeldungeon.ranked.net;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * spd-ranked (A1 client): one protocol message, {@code {type, seq, matchId, data}}, written as
 * {@code [4 byte BE length][UTF-8 JSON]}. Mirror of the server's Frame/FrameCodec (docs/A1-ranked-server.md);
 * the server module tests that both sides read each other's bytes.
 */
public final class NetFrame {

	public static final int PROTOCOL = 4; // 2: LOG (A3), 3: TELEMETRY (A5), 4: VOTE_REROLL, PAUSE (L6, L19)
	public static final int MAX_FRAME = 64 * 1024;

	// client -> server
	public static final String HELLO = "HELLO";
	public static final String QUEUE = "QUEUE";
	public static final String LEAVE_QUEUE = "LEAVE_QUEUE";
	public static final String READY = "READY";
	public static final String PROGRESS = "PROGRESS";
	public static final String DIED = "DIED";
	public static final String CLEARED = "CLEARED";
	public static final String RESIGN = "RESIGN";
	/** A3: next part of the command log, {from, cls, cmds} */
	public static final String LOG = "LOG";
	public static final String TELEMETRY = "TELEMETRY";
	public static final String RESUME = "RESUME";
	/** L6: vote for a new seed, {@code {accept}}; false declines the opponent's vote */
	public static final String VOTE_REROLL = "VOTE_REROLL";
	/** L19: stop this player's own clock, up to the pause budget */
	public static final String PAUSE = "PAUSE";
	public static final String UNPAUSE = "UNPAUSE";
	public static final String PING = "PING";

	// server -> client
	public static final String WELCOME = "WELCOME";
	public static final String QUEUED = "QUEUED";
	public static final String MATCH_FOUND = "MATCH_FOUND";
	public static final String START = "START";
	public static final String OPPONENT = "OPPONENT";
	public static final String RESUMED = "RESUMED";
	public static final String RESULT = "RESULT";
	/** {@code {mine, theirs, voteMs, ended?}}; a vote that passes is a new START */
	public static final String REROLL_VOTE = "REROLL_VOTE";
	/** {@code {mine, paused, leftMs}} */
	public static final String PAUSED = "PAUSED";
	public static final String PONG = "PONG";
	public static final String ERROR = "ERROR";

	public final String type;
	public final long seq;
	public final String matchId;
	public final JSONObject data;

	public NetFrame( String type, long seq, String matchId, JSONObject data ){
		this.type = type;
		this.seq = seq;
		this.matchId = matchId;
		this.data = data == null ? new JSONObject() : data;
	}

	public NetFrame( String type, JSONObject data ){
		this( type, 0, null, data );
	}

	public int i( String key, int def ){ return data.optInt( key, def ); }
	public long l( String key, long def ){ return data.optLong( key, def ); }
	public double d( String key, double def ){ return data.optDouble( key, def ); }
	public boolean b( String key ){ return data.optBoolean( key, false ); }
	public String s( String key ){ return data.isNull( key ) ? null : data.optString( key, null ); }

	public static JSONObject obj( Object... kv ){
		JSONObject o = new JSONObject();
		try {
			for (int i = 0; i + 1 < kv.length; i += 2) o.put( (String)kv[i], kv[i+1] );
		} catch (JSONException e){
			throw new IllegalArgumentException( e );
		}
		return o;
	}

	public byte[] encode(){
		try {
			JSONObject o = new JSONObject();
			o.put( "type", type );
			o.put( "seq", seq );
			if (matchId != null) o.put( "matchId", matchId );
			o.put( "data", data );
			return o.toString().getBytes( StandardCharsets.UTF_8 );
		} catch (JSONException e){
			throw new IllegalStateException( e );
		}
	}

	public static NetFrame decode( byte[] body ) throws IOException {
		try {
			JSONObject o = new JSONObject( new String( body, StandardCharsets.UTF_8 ) );
			JSONObject data = o.optJSONObject( "data" );
			String matchId = o.isNull( "matchId" ) ? null : o.optString( "matchId", null );
			return new NetFrame( o.getString( "type" ), o.optLong( "seq", 0 ), matchId, data );
		} catch (JSONException e){
			throw new IOException( "malformed frame", e );
		}
	}

	public static void write( OutputStream out, NetFrame f ) throws IOException {
		byte[] body = f.encode();
		if (body.length > MAX_FRAME) throw new IOException( "frame too large: " + body.length );
		DataOutputStream d = new DataOutputStream( out );
		d.writeInt( body.length );
		d.write( body );
		d.flush();
	}

	/** @return the next frame, or null at a clean end of stream */
	public static NetFrame read( InputStream in ) throws IOException {
		DataInputStream d = new DataInputStream( in );
		int len;
		try {
			len = d.readInt();
		} catch (EOFException e){
			return null;
		}
		if (len < 0 || len > MAX_FRAME) throw new IOException( "bad frame length: " + len );
		byte[] body = new byte[len];
		d.readFully( body );
		return decode( body );
	}

	@Override
	public String toString(){ return type + " " + data; }
}
