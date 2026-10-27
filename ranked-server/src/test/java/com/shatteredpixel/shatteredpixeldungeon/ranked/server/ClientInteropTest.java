package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shatteredpixel.shatteredpixeldungeon.ranked.net.NetFrame;
import com.shatteredpixel.shatteredpixeldungeon.ranked.net.RankedClient;
import com.shatteredpixel.shatteredpixeldungeon.ranked.net.RankedClient.State;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A1 client against the real server over a local socket: the game's RankedClient, not test frames. */
class ClientInteropTest {

	/** records everything a client reports, for the test thread to wait on */
	static final class Probe implements RankedClient.Listener {
		final LinkedBlockingQueue<NetFrame> frames = new LinkedBlockingQueue<>();
		final LinkedBlockingQueue<State> states = new LinkedBlockingQueue<>();
		final LinkedBlockingQueue<String> errors = new LinkedBlockingQueue<>();
		@Override public void onFrame(NetFrame f) { frames.add(f); }
		@Override public void onState(State s) { states.add(s); }
		@Override public void onError(String m) { errors.add(m); }

		NetFrame await(String type) throws InterruptedException {
			while (true) {
				NetFrame f = frames.poll(10, TimeUnit.SECONDS);
				assertNotNull(f, "timed out waiting for " + type);
				if (f.type.equals(type)) return f;
			}
		}

		NetFrame awaitOpponent(boolean connected, int depth) throws InterruptedException {
			while (true) {
				NetFrame f = await(NetFrame.OPPONENT);
				if (f.b("connected") == connected && f.i("depth", -1) == depth) return f;
			}
		}

		void awaitState(State want) throws InterruptedException {
			while (true) {
				State s = states.poll(10, TimeUnit.SECONDS);
				assertNotNull(s, "timed out waiting for state " + want);
				if (s == want) return;
			}
		}
	}

	static RankedClient client(int port, String build, String name, Probe p) {
		RankedClient.Config c = new RankedClient.Config();
		c.port = port;
		c.build = build;
		c.platform = "desktop";
		c.deviceKey = name + "-device-key-000";
		c.name = name;
		c.firstBackoffMs = 100;
		return new RankedClient(c, p);
	}

	@Test
	void fullMatchWithResume() throws Exception {
		try (Storage db = new Storage("jdbc:sqlite::memory:")) {
			db.addSeeds(Arrays.asList(42L));
			Hub.Config cfg = new Hub.Config();
			cfg.countdownMs = 0;
			try (Server server = new Server(new Hub(cfg, db, System.currentTimeMillis()), new ServerSocket(0))) {
				server.start();
				Probe pa = new Probe(), pb = new Probe();
				RankedClient a = client(server.port(), "dev", "alice", pa), b = client(server.port(), "dev", "bob", pb);
				try {
					a.start();
					b.start();
					pa.awaitState(State.IDLE);
					pb.awaitState(State.IDLE);
					a.queue();
					b.queue();
					assertEquals("bob", pa.await(NetFrame.MATCH_FOUND).s("opponent"));
					pb.await(NetFrame.MATCH_FOUND);
					a.ready();
					b.ready();
					assertEquals(42L, pa.await(NetFrame.START).l("seed", -1));
					pb.await(NetFrame.START);
					assertEquals(State.STARTED, a.state());
					Thread.sleep(1500); // the ticker moves the match to RUNNING

					a.progress(3, 100, 0);
					pb.awaitOpponent(true, 3);

					// network failure: the client resumes on its own and sends its newest report again
					a.dropLink();
					pb.awaitOpponent(false, 3);
					a.progress(4, 150, 0); // made while offline
					pa.awaitState(State.RECONNECTING);
					NetFrame resumed = pa.await(NetFrame.RESUMED);
					assertEquals(3, resumed.i("depth", -1), "server state before the queued report");
					pb.awaitOpponent(true, 4);

					a.cleared(15, 900, 3);
					NetFrame ra = pa.await(NetFrame.RESULT), rb = pb.await(NetFrame.RESULT);
					assertEquals("WIN", ra.s("outcome"));
					assertEquals("LOSS", rb.s("outcome"));
					assertTrue(ra.d("ratingAfter", 0) > ra.d("ratingBefore", 0));
					pa.awaitState(State.FINISHED);
					assertTrue(pa.errors.isEmpty(), pa.errors.toString());
				} finally {
					a.close();
					b.close();
				}
			}
		}
	}

	@Test
	void buildMismatchIsReported() throws Exception {
		try (Storage db = new Storage("jdbc:sqlite::memory:")) {
			try (Server server = new Server(new Hub(new Hub.Config(), db, System.currentTimeMillis()), new ServerSocket(0))) {
				server.start();
				Probe p = new Probe();
				RankedClient c = client(server.port(), "other-build", "carol", p);
				try {
					c.start();
					assertEquals("build mismatch", p.errors.poll(10, TimeUnit.SECONDS));
					assertFalse(c.state() == State.IDLE);
				} finally {
					c.close();
				}
			}
		}
	}
}
