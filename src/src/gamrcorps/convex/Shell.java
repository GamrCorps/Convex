package src.gamrcorps.convex;

import java.io.StringReader;

public class Shell {
	public static void main(final String... args) {
		final Convex x = new Convex();
		x.setArgs(args);
		while (true) {
			System.out.print("> ");
			final String s = x.readLine();
			final Block b = Block.parse(new StringReader(s), false);
			x.runCode(b);
			x.clear();
			System.out.println();
		}
	}
}
