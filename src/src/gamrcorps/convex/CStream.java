package src.gamrcorps.convex;

import java.io.IOException;
import java.io.Reader;
import java.util.NoSuchElementException;

public class CStream {
	private final Reader r;
	private char[] buf = new char[10];
	private int k = 0;
	private int o = 0;
	
	public CStream(final Reader r) {
		this.r = r;
	}
	
	public char get() {
		if (k > 0) {
			o++;
			return buf[--k];
		}
		try {
			final int x = r.read();
			if (x < 0) {
				throw new NoSuchElementException();
			}
			o++;
			return (char) x;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void put(final char c) {
		buf[k++] = c;
		o--;
	}
	
	public char peek() {
		final char c = get();
		put(c);
		return c;
	}
	
	public int getOffset() {
		return o;
	}
}
