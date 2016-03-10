package src.gamrcorps.convex;

public abstract class Out {
	public abstract void print(Object o);
	
	public void println() {
		print('\n');
	}
	
	public void println(final Object o) {
		print(o);
		print('\n');
	}
}
