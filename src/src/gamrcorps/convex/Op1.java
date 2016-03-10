package src.gamrcorps.convex;

public abstract class Op1 extends Op {
	public Op1(final String name) {
		super(name);
	}

	@Override
	public void run(final Convex x) {
		Object a = x.pop();
		final Object t = calc(x, a);
		if (t != null) {
			x.push(t);
		}
	}
	
	protected abstract Object calc(final Convex x, final Object a);
}
