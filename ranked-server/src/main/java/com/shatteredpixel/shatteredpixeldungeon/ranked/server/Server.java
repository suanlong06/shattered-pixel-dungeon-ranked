package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * spd-ranked (A1): socket transport for {@link Hub}. One thread per connection, which is
 * plenty for a small ladder on a phone. TLS is used when the server socket comes from an
 * SSLServerSocketFactory (see ServerMain); tests use a plain socket.
 * Keepalive (T0.5): a client sends PING every 5 s; 3 missed (15 s silent) closes the link.
 */
public final class Server implements AutoCloseable {

	public static final int SILENCE_MS = 15_000;

	private final Hub hub;
	private final ServerSocket socket;
	private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "hub-tick");
		t.setDaemon(true);
		return t;
	});
	private volatile boolean open = true;

	public Server(Hub hub, ServerSocket socket) {
		this.hub = hub;
		this.socket = socket;
	}

	public int port() { return socket.getLocalPort(); }

	public void start() {
		ticker.scheduleAtFixedRate(() -> {
			try {
				hub.tick(System.currentTimeMillis());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, 0, 1, TimeUnit.SECONDS);
		Thread accept = new Thread(this::acceptLoop, "accept");
		accept.setDaemon(true);
		accept.start();
	}

	private void acceptLoop() {
		while (open) {
			try {
				Socket s = socket.accept();
				Thread t = new Thread(() -> serve(s), "conn-" + s.getPort());
				t.setDaemon(true);
				t.start();
			} catch (IOException e) {
				if (open) e.printStackTrace();
			}
		}
	}

	private void serve(Socket s) {
		OutputStream out;
		InputStream in;
		try {
			s.setSoTimeout(SILENCE_MS);
			s.setTcpNoDelay(true);
			out = new BufferedOutputStream(s.getOutputStream());
			in = new BufferedInputStream(s.getInputStream());
		} catch (IOException e) {
			closeQuietly(s);
			return;
		}
		Hub.Link link = f -> {
			synchronized (out) {
				try {
					FrameCodec.write(out, f);
				} catch (IOException e) {
					closeQuietly(s);
				}
			}
		};
		hub.connect(link);
		try {
			Frame f;
			while ((f = FrameCodec.read(in)) != null) {
				hub.receive(link, f, System.currentTimeMillis());
			}
		} catch (SocketTimeoutException e) {
			// keepalive missed
		} catch (Exception e) {
			// broken peer or storage error: drop the link, the hub handles the rest
		} finally {
			closeQuietly(s);
			try {
				hub.disconnect(link, System.currentTimeMillis());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void closeQuietly(Socket s) {
		try {
			s.close();
		} catch (IOException ignored) {
		}
	}

	@Override
	public void close() throws IOException {
		open = false;
		ticker.shutdownNow();
		socket.close();
	}
}
