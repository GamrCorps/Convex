package src.gamrcorps.convex;

import java.io.Reader;
import java.util.NoSuchElementException;

public class CStreamIn implements In {
    private final CStream cs;
    private boolean eof;

    public CStreamIn(final Reader r) {
        this(new CStream(r));
    }

    public CStreamIn(final CStream cs) {
        this.cs = cs;
    }

    @Override
    public String readNext() {
        if (eof) {
            throw new RuntimeException("EOF");
        }
        final StringBuilder sb = new StringBuilder();
        boolean b = false;
        while (true) {
            final char c;
            try {
                c = cs.get();
            } catch (NoSuchElementException e) {
                if (b) {
                    return sb.toString();
                }
                eof = true;
                return "";
            }
            if (" 	\r\n".indexOf(c) >= 0) {
                if (b) {
                    cs.put(c);
                    return sb.toString();
                }
            } else {
                b = true;
                sb.append(c);
            }
        }
    }

    @Override
    public String readLine() {
        if (eof) {
            throw new RuntimeException("EOF");
        }
        final StringBuilder sb = new StringBuilder();
        while (true) {
            final char c;
            try {
                c = cs.get();
            } catch (NoSuchElementException e) {
                if (sb.length() == 0) {
                    eof = true;
                    return "\n";
                }
                return sb.toString();
            }
            if (c == '\n') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
    }

    @Override
    public String readAll() {
        if (eof) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        try {
            while (true) {
                sb.append(cs.get());
            }
        } catch (NoSuchElementException e) {
            eof = true;
            return sb.toString();
        }
    }
}
