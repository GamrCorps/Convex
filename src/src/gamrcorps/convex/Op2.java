package src.gamrcorps.convex;

public abstract class Op2 extends Op {
	public Op2(final String name) {
		super(name);
	}

	@Override
	public void run(final Convex x) {
		Object b = x.pop();
		Object a = x.pop();
		final Object t = calc(x, a, b);
		if (t != null) {
			x.push(t);
		}
	}
	
	protected abstract Object calc(final Convex x, final Object a, final Object b);
}
