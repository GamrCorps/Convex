package src.gamrcorps.convex;

public abstract class Op {
	protected final String name;
	
	public Op(final String name) {
		this.name = name;
	}

	public abstract void run(Convex x);

	@Override
	public String toString() {
		return name;
	}
	
	protected RuntimeException fail(final Object a) {
		return new RuntimeException(a.getClass().getSimpleName() + ' ' + name + " not implemented");
	}
	
	protected RuntimeException fail(final Object a, final Object b) {
		return new RuntimeException(a.getClass().getSimpleName() + ' ' + b.getClass().getSimpleName()
				+  ' ' + name + " not implemented");
	}
	
	protected RuntimeException fail(final Object a, final Object b, final Object c) {
		return new RuntimeException(a.getClass().getSimpleName() + ' ' + b.getClass().getSimpleName()
				+ ' ' + c.getClass().getSimpleName() + ' ' + name + " not implemented");
	}
}
