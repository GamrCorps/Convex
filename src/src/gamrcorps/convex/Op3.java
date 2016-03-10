package src.gamrcorps.convex;

public abstract class Op3 extends Op {
	public Op3(final String name) {
		super(name);
	}

	@Override
	public void run(final Convex x) {
		Object c = x.pop();
		Object b = x.pop();
		Object a = x.pop();
		final Object t = calc(x, a, b, c);
		if (t != null) {
			x.push(t);
		}
	}
	
	protected abstract Object calc(final Convex x, final Object a, final Object b, final Object c);
}
