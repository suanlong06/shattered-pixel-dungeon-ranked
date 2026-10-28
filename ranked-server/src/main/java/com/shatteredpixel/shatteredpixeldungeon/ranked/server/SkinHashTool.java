package com.shatteredpixel.shatteredpixeldungeon.ranked.server;

import com.shatteredpixel.shatteredpixeldungeon.ranked.skins.SkinHash;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * spd-ranked (A6): prints the hash the game sends for a skin pack folder, for the server's
 * spdr.skinDeny / spdr.skinAllow files. Same files as SkinPack.paths: PNGs in the root and up to
 * four folders below it.
 * <pre>java -cp ranked-server.jar ...server.SkinHashTool skins/my-pack [more packs]</pre>
 */
public final class SkinHashTool {

	/** a file four folders below the root is five path elements deep */
	static final int MAX_DEPTH = 5;

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.err.println("usage: SkinHashTool <pack folder>...");
			System.exit(2);
		}
		for (String a : args) System.out.println(hash(Paths.get(a)) + "  " + a);
	}

	public static String hash(Path root) throws IOException {
		List<String> paths = new ArrayList<>();
		try (Stream<Path> s = Files.walk(root, MAX_DEPTH)) {
			s.filter(Files::isRegularFile).forEach(p -> {
				StringBuilder rel = new StringBuilder();
				for (Path part : root.relativize(p)) rel.append(rel.length() == 0 ? "" : "/").append(part);
				if (rel.toString().endsWith(".png")) paths.add(rel.toString());
			});
		}
		return SkinHash.of(paths, p -> Files.readAllBytes(root.resolve(p)));
	}
}
