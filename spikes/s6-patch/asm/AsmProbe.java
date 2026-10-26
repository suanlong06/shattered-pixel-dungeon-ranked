import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Spike S6: would a build-time bytecode pass replace the mechanical source edits?
 * Rules (the same ones the source scripts apply):
 *  1. classes in visual packages, or whose superclass chain is a visual type: calls to
 *     com/watabou/utils/Random -> com/watabou/utils/fx/Random
 *  2. other classes: NEW java/util/HashSet|HashMap -> LinkedHashSet|LinkedHashMap
 *  3. Collections.shuffle(List) -> Random.shuffle(List)
 * Usage: AsmProbe <classesDir> <outDir>; prints what it rewrote and writes transformed classes.
 */
public class AsmProbe {

	static final String[] VISUAL_PKGS = {"/effects/", "/sprites/", "/ui/", "/scenes/", "/tiles/", "/windows/", "com/watabou/noosa/"};
	static final String[] VISUAL_SUPERS = {"com/watabou/noosa/particles/PixelParticle", "com/watabou/noosa/particles/Emitter",
			"com/watabou/noosa/particles/Emitter$Factory", "com/watabou/noosa/Gizmo"};

	static Map<String, String> supers = new HashMap<>();
	static int randomRemaps, newRemaps, shuffleRemaps, classesChanged;

	public static void main(String[] a) throws IOException {
		Path in = Paths.get(a[0]), out = Paths.get(a[1]);
		List<Path> files;
		try (Stream<Path> s = Files.walk(in)) { files = s.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList()); }
		for (Path p : files){
			ClassReader cr = new ClassReader(Files.readAllBytes(p));
			supers.put(cr.getClassName(), cr.getSuperName());
		}
		long t0 = System.nanoTime();
		for (Path p : files){
			byte[] b = Files.readAllBytes(p);
			ClassReader cr = new ClassReader(b);
			String name = cr.getClassName();
			boolean visual = isVisual(name);
			int before = randomRemaps + newRemaps + shuffleRemaps;
			ClassWriter cw = new ClassWriter(0);
			cr.accept(new ClassVisitor(Opcodes.ASM9, cw){
				@Override
				public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex){
					return new MethodVisitor(Opcodes.ASM9, super.visitMethod(acc, n, d, sig, ex)){
						@Override
						public void visitTypeInsn(int op, String type){
							if (!visual && op == Opcodes.NEW && (type.equals("java/util/HashSet") || type.equals("java/util/HashMap"))){
								type = type.replace("java/util/Hash", "java/util/LinkedHash"); newRemaps++;
							}
							super.visitTypeInsn(op, type);
						}
						@Override
						public void visitMethodInsn(int op, String owner, String n, String d, boolean itf){
							if (op == Opcodes.INVOKESPECIAL && n.equals("<init>") && !visual
									&& (owner.equals("java/util/HashSet") || owner.equals("java/util/HashMap"))){
								owner = owner.replace("java/util/Hash", "java/util/LinkedHash");
							} else if (visual && owner.equals("com/watabou/utils/Random")){
								owner = "com/watabou/utils/fx/Random"; randomRemaps++;
							} else if (!visual && owner.equals("java/util/Collections") && n.equals("shuffle") && d.equals("(Ljava/util/List;)V")){
								owner = "com/watabou/utils/Random"; shuffleRemaps++;
							}
							super.visitMethodInsn(op, owner, n, d, itf);
						}
					};
				}
			}, 0);
			Path o = out.resolve(in.relativize(p));
			Files.createDirectories(o.getParent());
			Files.write(o, cw.toByteArray());
			if (randomRemaps + newRemaps + shuffleRemaps != before) classesChanged++;
		}
		System.out.printf("classes=%d changed=%d randomCalls->fx=%d newHash->Linked=%d shuffle=%d time=%dms%n",
				files.size(), classesChanged, randomRemaps, newRemaps, shuffleRemaps, (System.nanoTime() - t0) / 1_000_000);
	}

	static boolean isVisual(String name){
		for (String p : VISUAL_PKGS) if (name.contains(p)) return true;
		for (String s = supers.get(name); s != null; s = supers.get(s)){
			for (String v : VISUAL_SUPERS) if (s.equals(v)) return true;
		}
		return false;
	}
}
