package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.badlogic.gdx.files.FileHandle;
import com.google.gson.JsonObject;
import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.SkinDenylist;
import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.SkinHash;
import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.SkinPack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** A6: the skin pack hash the game sends at HELLO, and the server's checks on it */
class SkinCheckTest {

	@TempDir
	Path tmp;

	Storage db;
	Hub hub;
	Hub.Config cfg;
	final long now = 1_000_000;

	@BeforeEach
	void setUp() throws Exception {
		db = new Storage("jdbc:sqlite::memory:");
		cfg = new Hub.Config();
		cfg.skinRules = SkinHash.ofText("deny.atlas=sprites/statue.png\n");
		hub = new Hub(cfg, db, now);
	}

	@AfterEach
	void tearDown() throws Exception { db.close(); }

	static void write(Path root, String rel, String content) throws Exception {
		Path p = root.resolve(rel);
		Files.createDirectories(p.getParent());
		Files.write(p, content.getBytes(StandardCharsets.UTF_8));
	}

	Path pack(String name) throws Exception {
		Path root = tmp.resolve(name);
		write(root, "sprites/warrior.png", "warrior art");
		write(root, "environment/tiles_sewers.png", "sewer art");
		write(root, "a/b/c/d/deep.png", "four folders down: used");
		write(root, "a/b/c/d/e/deeper.png", "five folders down: not used");
		write(root, "skin.properties", "name=Pack\nauthor=someone\n");
		write(root, "readme.txt", "not art");
		return root;
	}

	@Test
	void toolAndGameAgreeOnThePackHash() throws Exception {
		Path a = pack("pack-a");
		String game = new SkinPack(new FileHandle(a.toFile())).hash();
		assertTrue(SkinHash.wellFormed(game), game);
		assertEquals(game, SkinHashTool.hash(a));
		assertTrue(new SkinPack(new FileHandle(a.toFile())).paths().contains("a/b/c/d/deep.png"));
		assertFalse(new SkinPack(new FileHandle(a.toFile())).paths().contains("a/b/c/d/e/deeper.png"));

		// the folder name, the manifest and files that are not art do not count
		Path b = pack("pack-b");
		write(b, "skin.properties", "name=Renamed\n");
		write(b, "readme.txt", "other");
		write(b, "a/b/c/d/e/deeper.png", "changed, but too deep to be used");
		assertEquals(game, SkinHashTool.hash(b));
		assertEquals(game, new SkinPack(new FileHandle(b.toFile())).hash());

		// one changed picture, a moved one, or an added one is another pack
		write(b, "sprites/warrior.png", "warrior art!");
		assertNotEquals(game, SkinHashTool.hash(b));
		Path c = pack("pack-c");
		Files.move(c.resolve("sprites/warrior.png"), c.resolve("sprites/mage.png"));
		assertNotEquals(game, SkinHashTool.hash(c));
		Path d = pack("pack-d");
		write(d, "sprites/rogue.png", "");
		assertNotEquals(game, SkinHashTool.hash(d));
	}

	@Test
	void serverKnowsTheGamesDenylist() throws Exception {
		String rules = ServerMain.coreSkinRules();
		assertNotNull(rules, "the denylist asset is on the server's classpath");
		String text = new String(Files.readAllBytes(Path.of("..", "core", "src", "main", "assets", SkinDenylist.ASSET)), StandardCharsets.UTF_8);
		assertEquals(SkinHash.ofText(text), rules);
	}

	@Test
	void hashListsSkipCommentsAndRefuseGarbage() throws Exception {
		String h = SkinHash.ofText("x");
		Path f = tmp.resolve("deny.txt");
		Files.write(f, Arrays.asList("# shows mimics", h + "  # pack x", "", "  "), StandardCharsets.UTF_8);
		assertEquals(Collections.singleton(h), ServerMain.hashes(f.toString()));
		Files.write(f, Arrays.asList(h, "not-a-hash"), StandardCharsets.UTF_8);
		assertThrows(IllegalArgumentException.class, () -> ServerMain.hashes(f.toString()));
	}

	HubTest.FakeLink hello(String key, String skin, String rules) throws Exception {
		HubTest.FakeLink l = new HubTest.FakeLink();
		hub.connect(l);
		JsonObject d = new JsonObject();
		d.addProperty("protocol", Frame.PROTOCOL);
		d.addProperty("build", "dev");
		d.addProperty("platform", "desktop");
		d.addProperty("deviceKey", key + "-device-key-000");
		d.addProperty("name", key);
		if (skin != null) d.addProperty("skin", skin);
		if (rules != null) d.addProperty("skinRules", rules);
		hub.receive(l, new Frame(Frame.HELLO, d), now);
		return l;
	}

	static String refusal(HubTest.FakeLink l) {
		if (l.last(Frame.WELCOME) != null) return null;
		return l.last(Frame.ERROR).s("message");
	}

	@Test
	void serverChecksTheSkinAtEveryHello() throws Exception {
		String good = SkinHash.ofText("good pack"), bad = SkinHash.ofText("shows mimics");
		cfg.deniedSkins = new HashSet<>(Collections.singleton(bad));

		// the original art always plays, whatever the client's denylist (it has none in use)
		assertNull(refusal(hello("orig", null, null)));
		assertNull(refusal(hello("orig2", SkinHash.NONE, SkinHash.NONE)));

		assertNull(refusal(hello("good", good, cfg.skinRules)));
		assertEquals("skin pack not allowed in ranked", refusal(hello("bad", bad, cfg.skinRules)));
		assertEquals("skin denylist mismatch", refusal(hello("edited", good, SkinHash.ofText("deny.atlas=\n"))));
		assertEquals("skin denylist mismatch", refusal(hello("nolist", good, null)));
		assertEquals("skin pack unreadable", refusal(hello("broken", "unreadable:my-pack", cfg.skinRules)));
		assertEquals("skin pack unreadable", refusal(hello("upper", good.toUpperCase(), cfg.skinRules)));

		// refused before it is a player: it cannot queue
		HubTest.FakeLink l = hello("bad2", bad, cfg.skinRules);
		hub.receive(l, new Frame(Frame.QUEUE, null), now);
		assertEquals("HELLO first", l.last(Frame.ERROR).s("message"));

		// an allowlist lets in only what it names
		cfg.allowedSkins = new HashSet<>(Collections.singleton(good));
		assertNull(refusal(hello("good3", good, cfg.skinRules)));
		assertEquals("skin pack not allowed in ranked", refusal(hello("other", SkinHash.ofText("other"), cfg.skinRules)));
		assertNull(refusal(hello("orig3", null, null)));

		// a server that does not check the denylist still checks the packs
		cfg.skinRules = null;
		assertNull(refusal(hello("good4", good, "anything")));
		assertEquals("skin pack not allowed in ranked", refusal(hello("bad4", bad, "anything")));
	}
}
