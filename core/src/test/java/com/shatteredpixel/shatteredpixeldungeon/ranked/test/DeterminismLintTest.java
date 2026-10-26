package com.shatteredpixel.shatteredpixeldungeon.ranked.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T0.8 lint, on the compiled game classes (core + SPD-classes), so it also sees code added by
 * an upstream merge. Gameplay code must not use:
 *   MATH     Math.sin/cos/tan/asin/acos/atan/atan2/exp/log/log10/pow/cbrt/hypot/sinh/cosh/tanh/expm1/log1p/random
 *            (differ between JVM and ART, spike S3) - use StrictMath
 *   HASH     new HashSet / new HashMap (iteration order depends on identity hashes) - use Linked*
 *   SHUFFLE  Collections.shuffle(list) (own unseeded RNG) - use Random.shuffle(list)
 * Nowhere may code create its own RNG:
 *   RNG      new java.util.Random / SecureRandom / ThreadLocalRandom, outside the two Random classes
 * Visual code (effects, sprites, ui, scenes, tiles, windows, noosa, visual nested classes and *fx methods)
 * must not draw from the gameplay RNG:
 *   FXRNG    com.watabou.utils.Random from visual code - use com.watabou.utils.fx.Random
 * Exceptions live in /ranked/lint-allow.txt, one "rule class#method  reason" per line.
 */
class DeterminismLintTest {

	static final Set<String> MATH = new HashSet<>(Arrays.asList("sin", "cos", "tan", "asin", "acos", "atan", "atan2",
			"exp", "log", "log10", "pow", "cbrt", "hypot", "sinh", "cosh", "tanh", "expm1", "log1p", "random"));
	static final String[] VISUAL_PKGS = {"/effects/", "/sprites/", "/ui/", "/scenes/", "/tiles/", "/windows/",
			"com/watabou/noosa/", "com/watabou/glwrap/", "com/watabou/gltextures/", "com/watabou/glscripts/", "com/watabou/input/",
			"com/watabou/utils/fx/", "com/watabou/utils/Bitmap", "/ranked/sim/HeadlessGame"};
	/** windows apply the player's choice to the game: their code is gameplay, even though they are Gizmos */
	static final String WINDOW = "com/shatteredpixel/shatteredpixeldungeon/ui/Window";
	static final String[] VISUAL_SUPERS = {"com/watabou/noosa/Gizmo", "com/watabou/noosa/particles/Emitter$Factory"};
	static final Set<String> RNG_OWNERS = new HashSet<>(Arrays.asList(
			"com/watabou/utils/Random", "com/watabou/utils/fx/Random", "com/watabou/utils/StatefulRandom"));

	final Map<String, String> supers = new HashMap<>();
	final Map<String, byte[]> classes = new HashMap<>();

	@Test
	void gameplayCodeUsesOnlyDeterministicPrimitives() throws IOException {
		for (String e : System.getProperty("java.class.path").split(File.pathSeparator)){
			String n = e.replace('\\', '/').replaceAll("/+", "/");
			if (n.endsWith("core/build/classes/java/main")) loadDir(Path.of(e));
			else if (n.matches(".*/SPD-classes(-[0-9.]+)?\\.jar") || n.endsWith("SPD-classes/build/classes/java/main")) loadJar(e);
		}
		assertTrue(classes.size() > 2000, "game classes not found on the test classpath: " + classes.size() + " " + System.getProperty("java.class.path"));

		Set<String> allow = allowList();
		Set<String> used = new HashSet<>();
		List<String> found = scan(allow, used);
		Set<String> stale = new TreeSet<>(allow);
		stale.removeAll(used);
		assertTrue(found.isEmpty(), found.size() + " violations:\n" + found.stream().sorted().collect(Collectors.joining("\n")));
		assertTrue(stale.isEmpty(), "allow-list entries that no longer match anything (remove them):\n" + String.join("\n", stale));
	}

	List<String> scan(Set<String> allow, Set<String> used){
		List<String> found = new ArrayList<>();
		for (Map.Entry<String, byte[]> c : classes.entrySet()){
			String owner = c.getKey();
			boolean visualClass = isVisual(owner);
			new ClassReader(c.getValue()).accept(new ClassVisitor(Opcodes.ASM9){
				@Override
				public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex){
					boolean visual = visualClass || name.matches(".*([Ff]x|VFX).*");
					String where = owner + "#" + name;
					return new MethodVisitor(Opcodes.ASM9){
						void flag(String rule, String what){
							String key = rule + " " + where;
							if (allow.contains(key)) used.add(key);
							else found.add(key + "  (" + what + ")");
						}
						@Override
						public void visitTypeInsn(int op, String type){
							if (op != Opcodes.NEW) return;
							if (!visual && (type.equals("java/util/HashSet") || type.equals("java/util/HashMap"))) flag("HASH", "new " + type);
							if ((type.equals("java/util/Random") || type.equals("java/security/SecureRandom")) && !RNG_OWNERS.contains(owner)) flag("RNG", "new " + type);
						}
						@Override
						public void visitMethodInsn(int op, String o, String n, String d, boolean itf){
							if (!visual && o.equals("java/lang/Math") && MATH.contains(n)) flag("MATH", "Math." + n);
							if (!visual && o.equals("java/util/Collections") && n.equals("shuffle") && d.equals("(Ljava/util/List;)V")) flag("SHUFFLE", "Collections.shuffle(list)");
							if (o.equals("java/util/concurrent/ThreadLocalRandom")) flag("RNG", "ThreadLocalRandom");
							if (visual && o.equals("com/watabou/utils/Random")) flag("FXRNG", "Random." + n);
						}
					};
				}
			}, 0);
		}
		return found;
	}

	/** The lint must actually catch things: a fixture class with every forbidden pattern. */
	@Test
	void lintFlagsForbiddenPatterns() throws IOException {
		try (InputStream in = LintFixture.class.getResourceAsStream("DeterminismLintTest$LintFixture.class")){
			add(in.readAllBytes());
		}
		List<String> found = scan(new HashSet<>(), new HashSet<>());
		String all = String.join("\n", found);
		for (String rule : new String[]{"MATH", "HASH", "SHUFFLE", "RNG"}){
			assertTrue(found.stream().anyMatch(f -> f.startsWith(rule + " ")), rule + " not flagged:\n" + all);
		}
	}

	@SuppressWarnings("unused")
	static final class LintFixture {
		double bad(java.util.List<Object> l){
			java.util.Collections.shuffle(l);
			new java.util.HashMap<String, String>();
			return Math.sin(1) + new java.util.Random().nextInt();
		}
	}

	boolean isVisual(String name){
		for (String p : VISUAL_PKGS) if (name.contains(p)) return true;
		for (String s = supers.get(name); s != null; s = supers.get(s)){
			if (s.equals(WINDOW)) return false;
			for (String v : VISUAL_SUPERS) if (s.equals(v)) return true;
		}
		// nested classes of a visual class are visual too
		int i = name.lastIndexOf('$');
		return i > 0 && classes.containsKey(name.substring(0, i)) && isVisual(name.substring(0, i));
	}

	static Set<String> allowList() throws IOException {
		Set<String> out = new HashSet<>();
		try (InputStream in = DeterminismLintTest.class.getResourceAsStream("/ranked/lint-allow.txt")){
			if (in == null) return out;
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")){
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] p = line.split("\\s+");
				out.add(p[0] + " " + p[1]);
			}
		}
		return out;
	}

	void loadDir(Path dir) throws IOException {
		try (Stream<Path> s = Files.walk(dir)){
			for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class"))::iterator) add(Files.readAllBytes(p));
		}
	}

	void loadJar(String jar) throws IOException {
		try (JarFile jf = new JarFile(jar)){
			Enumeration<JarEntry> en = jf.entries();
			while (en.hasMoreElements()){
				JarEntry e = en.nextElement();
				if (e.getName().endsWith(".class")) try (InputStream in = jf.getInputStream(e)){ add(in.readAllBytes()); }
			}
		}
	}

	void add(byte[] b){
		ClassReader cr = new ClassReader(b);
		classes.put(cr.getClassName(), b);
		supers.put(cr.getClassName(), cr.getSuperName());
	}
}
