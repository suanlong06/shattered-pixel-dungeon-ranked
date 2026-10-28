package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.shatteredpixel.shatteredpixeldungeon.ranked.seed.SeedPoolFile;
import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.SkinDenylist;
import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.SkinHash;

import java.io.FileInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * spd-ranked (A1): entry point. Settings are system properties so a runbook (A7) can set
 * them without code changes:
 * <pre>
 *   spdr.port      (default 7420)
 *   spdr.db        (default ranked.db)
 *   spdr.build     build fingerprint clients must send (default: the bytecode hash in core's
 *                  ranked/build-fingerprint.txt, the same one the game sends)
 *   spdr.season    (default 1)
 *   spdr.seedpool  A4 pool file to load; known seeds keep their used state
 *   spdr.retireStalePools  true: stop using unused seeds from pools built with other thresholds
 *   spdr.keystore / spdr.keystorePass   PKCS12 keystore; without it the server runs plain TCP
 *   spdr.verify    replay (default): replay command logs before rating (A3); none: accept every verdict
 *   spdr.acceptUnverifiable  true: count verdicts replay cannot follow yet instead of voiding them
 *   spdr.skinDeny  file of skin pack hashes refused in ranked (one per line, # comments; see SkinHashTool)
 *   spdr.skinAllow file of the only skin pack hashes let in; unset: any pack not denied
 *   spdr.skinRules hash of the skin denylist clients must apply (default: core's
 *                  skins/ranked_denylist.properties; none: not checked)
 * </pre>
 */
public final class ServerMain {

	public static void main(String[] args) throws Exception {
		Hub.Config cfg = new Hub.Config();
		cfg.build = System.getProperty("spdr.build", coreFingerprint());
		if (cfg.build == null) throw new IllegalArgumentException("no fingerprint in core; set -Dspdr.build=<fingerprint>");
		cfg.season = Integer.getInteger("spdr.season", 1);
		int port = Integer.getInteger("spdr.port", 7420);
		if (!"none".equals(System.getProperty("spdr.verify", "replay"))) cfg.verifier = new ReplayVerifier();
		cfg.acceptUnverifiable = Boolean.getBoolean("spdr.acceptUnverifiable");

		cfg.skinRules = System.getProperty("spdr.skinRules", coreSkinRules());
		if ("none".equals(cfg.skinRules)) cfg.skinRules = null;
		if (System.getProperty("spdr.skinDeny") != null) cfg.deniedSkins = hashes(System.getProperty("spdr.skinDeny"));
		if (System.getProperty("spdr.skinAllow") != null) cfg.allowedSkins = hashes(System.getProperty("spdr.skinAllow"));
		System.out.println("skins: " + cfg.deniedSkins.size() + " denied, "
				+ (cfg.allowedSkins == null ? "any other allowed" : cfg.allowedSkins.size() + " allowed")
				+ ", denylist " + (cfg.skinRules == null ? "not checked" : cfg.skinRules));

		Storage db = new Storage("jdbc:sqlite:" + System.getProperty("spdr.db", "ranked.db"));
		String pool = System.getProperty("spdr.seedpool");
		if (pool != null) {
			String text = new String(Files.readAllBytes(Paths.get(pool)), StandardCharsets.UTF_8);
			String header = SeedPoolFile.header(text);
			if (!SeedPoolFile.currentHeader().equals(header)) {
				System.out.println("WARNING: seed pool built with '" + header + "', scorer now '" + SeedPoolFile.currentHeader() + "'");
			}
			int added = db.addSeeds(SeedPoolFile.entries(text), header, System.currentTimeMillis());
			System.out.println("seed pool: " + added + " new seeds");
		}
		if (Boolean.getBoolean("spdr.retireStalePools")) {
			System.out.println("seed pool: retired " + db.retireOtherPools(SeedPoolFile.currentHeader()) + " stale seeds");
		}
		int left = db.seedsLeft();
		System.out.println("seed pool: " + left + " unused seeds");
		if (left == 0) System.out.println("WARNING: seed pool empty, no match can start (set -Dspdr.seedpool=<file>)");

		ServerSocket socket;
		String ks = System.getProperty("spdr.keystore");
		if (ks != null) {
			char[] pass = System.getProperty("spdr.keystorePass", "").toCharArray();
			KeyStore store = KeyStore.getInstance("PKCS12");
			try (FileInputStream in = new FileInputStream(ks)) {
				store.load(in, pass);
			}
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(store, pass);
			SSLContext tls = SSLContext.getInstance("TLS");
			tls.init(kmf.getKeyManagers(), null, null);
			socket = tls.getServerSocketFactory().createServerSocket(port);
		} else {
			System.out.println("WARNING: no keystore, plain TCP (local testing only)");
			socket = new ServerSocket(port);
		}

		Server server = new Server(new Hub(cfg, db, System.currentTimeMillis()), socket);
		server.start();
		System.out.println("spd-ranked server on port " + server.port() + ", build " + cfg.build + ", season " + cfg.season);
		Thread.currentThread().join();
	}

	/** the hash of core's skin denylist, as the game computes it (Skins.denylistHash), or null */
	static String coreSkinRules() {
		try (java.io.InputStream in = ServerMain.class.getResourceAsStream("/" + SkinDenylist.ASSET)) {
			if (in == null) return null;
			java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
			return SkinHash.ofText(new String(b.toByteArray(), StandardCharsets.UTF_8));
		} catch (java.io.IOException e) {
			return null;
		}
	}

	/** skin hashes, one per line; blank lines and # comments skipped; a malformed line stops the server */
	static java.util.Set<String> hashes(String file) throws java.io.IOException {
		java.util.Set<String> out = new java.util.HashSet<>();
		for (String raw : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
			String line = raw.replaceAll("#.*", "").trim();
			if (line.isEmpty()) continue;
			if (!SkinHash.wellFormed(line)) throw new IllegalArgumentException(file + ": not a skin hash: " + line);
			out.add(line);
		}
		return out;
	}

	/** the bytecode hash core's build wrote (core/build.gradle, rankedFingerprint), or null */
	static String coreFingerprint() {
		try (java.io.InputStream in = ServerMain.class.getResourceAsStream("/ranked/build-fingerprint.txt")) {
			if (in == null) return null;
			java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[256];
			int n;
			while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
			String fp = new String(b.toByteArray(), StandardCharsets.UTF_8).trim();
			return fp.isEmpty() ? null : fp;
		} catch (java.io.IOException e) {
			return null;
		}
	}
}
