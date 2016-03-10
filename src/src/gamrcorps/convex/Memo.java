package src.gamrcorps.convex;

import static src.gamrcorps.convex.Conv.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Memo {
	private final Map<Object, Object> m = new HashMap<Object, Object>();
	private final Block b;
	private final int n;

	public Memo(final Object init, final Block b, final int n) {
		this.b = b;
		this.n = n;
		set(new ArrayList<Object>(), init, n);
	}

	private void set(final List<Object> l, final Object o, final int n) {
		if (n == 0) {
			if (this.n == 1) {
				m.put(l.get(0), o);
			}
			else {
				m.put(new ArrayList<Object>(l), o);
			}
			return;
		}
		final List<?> ol = toList(o);
		for (int i = 0; i < ol.size(); ++i) {
			l.add((long) i);
			set(l, ol.get(i), n - 1);
			l.remove(l.size() - 1);
		}
	}

	public void calc(final Convex x) {
		Object o;
		List<Object> l = null;
		if (n == 1) {
			o = x.pop();
		}
		else {
			l = new ArrayList<Object>(n);
			for (int i = 0; i < n; ++i) {
				l.add(x.pop());
			}
			Collections.reverse(l);
			o = l;
		}
		final Object t = m.get(o);
		if (t != null) {
			x.push(t);
			return;
		}
		if (n == 1) {
			x.push(o);
		}
		else {
			for (Object a : l) {
				x.push(a);
			}
		}
		b.run(x);
		m.put(o, x.peek());
	}
}
