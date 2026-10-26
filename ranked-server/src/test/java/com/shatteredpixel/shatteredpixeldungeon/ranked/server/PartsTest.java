package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PartsTest {

	@Test
	void codecRoundTripsAndRejectsOversize() throws Exception {
		JsonObject d = new JsonObject();
		d.addProperty("depth", 7);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FrameCodec.write(out, new Frame(Frame.PROGRESS, 3, "m1", d));
		byte[] bytes = out.toByteArray();
		assertEquals(bytes.length - 4, ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff));
		Frame f = FrameCodec.read(new ByteArrayInputStream(bytes));
		assertEquals(Frame.PROGRESS, f.type);
		assertEquals(3, f.seq);
		assertEquals("m1", f.matchId);
		assertEquals(7, f.i("depth", 0));
		assertNull(FrameCodec.read(new ByteArrayInputStream(new byte[0])));

		ByteArrayOutputStream big = new ByteArrayOutputStream();
		new DataOutputStream(big).writeInt(FrameCodec.MAX_FRAME + 1);
		assertThrows(IOException.class, () -> FrameCodec.read(new ByteArrayInputStream(big.toByteArray())));
	}

	@Test
	void glickoMovesTowardResult() {
		Glicko2.Rating a = Glicko2.Rating.initial(), b = Glicko2.Rating.initial();
		Glicko2.Rating wa = Glicko2.update(a, b, 1), lb = Glicko2.update(b, a, 0), da = Glicko2.update(a, b, 0.5);
		assertTrue(wa.rating > 1500);
		assertEquals(1500 - wa.rating, lb.rating - 1500, 1e-6, "equal players move symmetrically");
		assertEquals(1500, da.rating, 1e-6);
		assertTrue(wa.rd < a.rd);
		// beating a much stronger player gains more than beating an equal one
		Glicko2.Rating strong = new Glicko2.Rating(1900, 60, 0.06);
		assertTrue(Glicko2.update(a, strong, 1).rating > wa.rating);
	}

	@Test
	void matchmakerWidensWindowAndKeepsOrder() {
		Matchmaker m = new Matchmaker();
		m.add(1, "desktop", 1500, 0);
		m.add(2, "desktop", 1750, 0);
		m.add(3, "desktop", 1530, 0);
		List<Matchmaker.Entry[]> pairs = m.pair(0);
		assertEquals(1, pairs.size());
		assertEquals(Arrays.asList(1L, 3L), Arrays.asList(pairs.get(0)[0].playerId, pairs.get(0)[1].playerId));

		m.add(4, "desktop", 1500, 0);
		assertTrue(m.pair(10_000).isEmpty(), "gap 250 > window 200 after 10 s");
		assertEquals(1, m.pair(15_000).size(), "window 250 after 15 s");
		assertEquals(0, m.size());
	}

	/** the real socket path: two clients, length-prefixed frames, a full match */
	@Test
	void socketEndToEnd() throws Exception {
		try (Storage db = new Storage("jdbc:sqlite::memory:")) {
			db.addSeeds(Arrays.asList(42L));
			Hub.Config cfg = new Hub.Config();
			cfg.countdownMs = 0;
			try (Server server = new Server(new Hub(cfg, db, System.currentTimeMillis()), new ServerSocket(0))) {
				server.start();
				try (Socket a = new Socket("127.0.0.1", server.port()); Socket b = new Socket("127.0.0.1", server.port())) {
					a.setSoTimeout(10_000);
					b.setSoTimeout(10_000);
					hello(a, "alice");
					hello(b, "bob");
					FrameCodec.write(a.getOutputStream(), new Frame(Frame.QUEUE, null));
					FrameCodec.write(b.getOutputStream(), new Frame(Frame.QUEUE, null));
					await(a.getInputStream(), Frame.MATCH_FOUND);
					await(b.getInputStream(), Frame.MATCH_FOUND);
					FrameCodec.write(a.getOutputStream(), new Frame(Frame.READY, null));
					FrameCodec.write(b.getOutputStream(), new Frame(Frame.READY, null));
					assertEquals(42L, await(a.getInputStream(), Frame.START).l("seed", -1));
					await(b.getInputStream(), Frame.START);
					Thread.sleep(1500); // let the ticker move the match to RUNNING
					FrameCodec.write(a.getOutputStream(), new Frame(Frame.RESIGN, null));
					assertEquals("WIN", await(b.getInputStream(), Frame.RESULT).s("outcome"));
				}
			}
		}
	}

	private static void hello(Socket s, String name) throws IOException {
		JsonObject d = new JsonObject();
		d.addProperty("protocol", Frame.PROTOCOL);
		d.addProperty("build", "dev");
		d.addProperty("platform", "desktop");
		d.addProperty("deviceKey", name + "-device-key-000");
		d.addProperty("name", name);
		OutputStream out = s.getOutputStream();
		FrameCodec.write(out, new Frame(Frame.HELLO, d));
		await(s.getInputStream(), Frame.WELCOME);
	}

	private static Frame await(InputStream in, String type) throws IOException {
		while (true) {
			Frame f = FrameCodec.read(in);
			assertNotNull(f, "connection closed waiting for " + type);
			assertNotEquals(Frame.ERROR, f.type, f.toString());
			if (f.type.equals(type)) return f;
		}
	}
}
