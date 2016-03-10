package src.gamrcorps.convex;

import java.io.PrintStream;

public class PrintStreamOut extends Out {
	private final PrintStream ps;
	
	public PrintStreamOut(final PrintStream ps) {
		this.ps = ps;
	}

	@Override
	public void print(final Object o) {
		ps.print(o);
	}

	@Override
	public void println() {
		ps.println();
	}

	@Override
	public void println(final Object o) {
		ps.println(o);
	}
}
