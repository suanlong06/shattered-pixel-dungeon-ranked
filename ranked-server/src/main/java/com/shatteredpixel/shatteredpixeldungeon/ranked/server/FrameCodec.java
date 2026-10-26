package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** spd-ranked (A1): length-prefixed JSON frames, {@code [4 byte BE length][UTF-8 JSON]}. */
public final class FrameCodec {

	/** a frame is small; anything larger is a broken or hostile peer */
	public static final int MAX_FRAME = 64 * 1024;

	private FrameCodec() {}

	public static byte[] encode(Frame f) {
		JsonObject o = new JsonObject();
		o.addProperty("type", f.type);
		o.addProperty("seq", f.seq);
		if (f.matchId != null) o.addProperty("matchId", f.matchId);
		o.add("data", f.data);
		return o.toString().getBytes(StandardCharsets.UTF_8);
	}

	public static Frame decode(byte[] body) {
		JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
		String type = o.get("type").getAsString();
		long seq = o.has("seq") ? o.get("seq").getAsLong() : 0;
		String matchId = o.has("matchId") && !o.get("matchId").isJsonNull() ? o.get("matchId").getAsString() : null;
		JsonObject data = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : new JsonObject();
		return new Frame(type, seq, matchId, data);
	}

	public static void write(OutputStream out, Frame f) throws IOException {
		byte[] body = encode(f);
		if (body.length > MAX_FRAME) throw new IOException("frame too large: " + body.length);
		DataOutputStream d = new DataOutputStream(out);
		d.writeInt(body.length);
		d.write(body);
		d.flush();
	}

	/** @return the next frame, or null at a clean end of stream */
	public static Frame read(InputStream in) throws IOException {
		DataInputStream d = new DataInputStream(in);
		int len;
		try {
			len = d.readInt();
		} catch (EOFException e) {
			return null;
		}
		if (len < 0 || len > MAX_FRAME) throw new IOException("bad frame length: " + len);
		byte[] body = new byte[len];
		d.readFully(body);
		try {
			return decode(body);
		} catch (RuntimeException e) {
			throw new IOException("malformed frame", e);
		}
	}
}
