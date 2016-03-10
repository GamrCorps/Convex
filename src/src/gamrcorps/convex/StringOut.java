package src.gamrcorps.convex;

public class StringOut extends Out {
	private final StringBuilder sb = new StringBuilder();
	
	@Override
	public void print(final Object o) {
		sb.append(o);
	}

	@Override
	public String toString() {
		return sb.toString();
	}
}
