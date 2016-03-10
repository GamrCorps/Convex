package src.gamrcorps.convex;

import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class Block extends Op {
    private final List<Op> l;
    private final List<Integer> o;

    private static List<Object> parseString(final CStream cs, final StringBuilder sb) {
        final List<Object> l = new ArrayList<Object>();
        try {
            while (true) {
                char c = cs.get();
                sb.append(c);
                if (c == '"') {
                    return l;
                }
                if (c == '\\') {
                    final char c1 = cs.get();
                    if (c1 == '"' || c1 == '\\') {
                        sb.append(c1);
                        c = c1;
                    } else {
                        cs.put(c1);
                    }
                }
                l.add(c);
            }
        } catch (NoSuchElementException e) {
            throw new RuntimeException("Unfinished string");
        }
    }

    private static Number parseNumber(final CStream cs, final StringBuilder s2) {
        final StringBuilder sb = new StringBuilder();
        try {
            while (true) {
                final char c = cs.get();
                if (c >= '0' && c <= '9' || c == '.') {
                    sb.append(c);
                } else if (c == '-') {
                    if (sb.length() == 0) {
                        sb.append(c);
                    } else {
                        cs.put(c);
                        break;
                    }
                } else {
                    cs.put(c);
                    break;
                }
            }
        } catch (NoSuchElementException e) {
            // ignore
        }
        final String s = sb.toString();
        s2.append(s.substring(1));
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            // ignore
        }
        try {
            return new BigInteger(s);
        } catch (Exception e) {
            // ignore
        }
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            // ignore
        }
        throw new RuntimeException("Invalid number: " + s);
    }

    public static Block parse(final Reader r, final boolean close) {
        return parse(new CStream(r), close);
    }

    protected Block(final List<Op> l, final List<Integer> o, final String repr) {
        super(repr);
        if (l.size() != o.size()) {
            throw new IllegalArgumentException("Size mismatch");
        }
        this.l = l;
        this.o = o;
    }

    public static Block parse(final CStream cs, final boolean close) {
        final List<Op> l = new ArrayList<Op>();
        final List<Integer> o = new ArrayList<Integer>();
        final StringBuilder sb = new StringBuilder();
        int off = cs.getOffset();
        if (close) {
            sb.append('{');
            off--;
        }
        while (true) {
            final char c;
            try {
                c = cs.get();
            } catch (NoSuchElementException e) {
                if (close) {
                    throw new RuntimeException("Unfinished block");
                }
                return new Block(l, o, sb.toString());
            }
            sb.append(c);
            switch (c) {
                case ' ':
                case '\t':
                case '\n':
                    break;
                case '}':
                    if (close) {
                        return new Block(l, o, sb.toString());
                    }
                    throw new RuntimeException("Unexpected }");
                case 'e':
                    char c1;
                    try {
                        c1 = cs.peek();
                    } catch (NoSuchElementException e) {
                        throw new RuntimeException("Unfinished operator: e");
                    }
                    if (c1 == '#') {
                        // line comment
                        try {
                            do {
                                // eat characters
                                c1 = cs.get();
                                sb.append(c1);
                            } while (c1 != '\n');
                        } catch (NoSuchElementException e) {
                            // eof reached, stop here
                        }
                        break;
                    }
                    // fall through
                default:
                    o.add(cs.getOffset() - off - 1);
                    l.add(parseOp(cs, sb, c));
            }
        }
    }

    private static Op parseOp(final CStream cs, final StringBuilder sb, final char c) {
        char c1;
        Block b;
        String s;
        Op op;
        if (c >= '0' && c <= '9') {
            cs.put(c);
            return Ops.push(parseNumber(cs, sb));
        }
        if (c >= 'A' && c <= 'Z') {
            return Ops.pushVar(c);
        }
        switch (c) {
            case '"':
                return Ops.push(parseString(cs, sb));
            case '®':
                return Ops.push(new Regex(parseString(cs, sb)));
            case '-':
                try {
                    c1 = cs.peek();
                    if (c1 >= '0' && c1 <= '9' || c1 == '.') {
                        cs.put(c);
                        return Ops.push(parseNumber(cs, sb));
                    }
                } catch (NoSuchElementException e) {
                    // ignore
                }
                return Ops.get('-');
            case '\'':
                c1 = cs.get();
                sb.append(c1);
                return Ops.push(c1);
            case '{':
                b = parse(cs, true);
                sb.append(b.toString().substring(1));
                return Ops.push(b);
            case '}':
                throw new RuntimeException("Expected operator but found }");
            case 'e':
                c1 = cs.get();
                sb.append(c1);
                if (c1 >= '0' && c1 <= '9' || c1 == '-' || c1 == '.') {
                    cs.put(c1);
                    return Ops.e10(parseNumber(cs, sb));
                }
                s = new String(new char[]{c, c1});
                op = Ops.get(s);
                if (op == null) {
                    throw new RuntimeException(s + " not handled");
                }
                return op;
            case 'm':
                try {
                    c1 = cs.get();
                } catch (NoSuchElementException e) {
                    throw new RuntimeException("Unfinished operator: m");
                }
                if (c1 >= '0' && c1 <= '9' || c1 == '-' || c1 == '.') {
                    cs.put(c1);
                    return Ops.get('-');
                }
                sb.append(c1);
                s = new String(new char[]{c, c1});
                op = Ops.get(s);
                if (op == null) {
                    throw new RuntimeException(s + " not handled");
                }
                return op;
            case 'f':
                try {
                    c1 = cs.get();
                } catch (NoSuchElementException e) {
                    throw new RuntimeException("Unfinished operator: f");
                }
                sb.append(c1);
                if (c1 >= 'A' && c1 <= 'Z') {
                    return Ops.forLoop(c1);
                }
                if (c1 == '{') {
                    b = parse(cs, true);
                    sb.append(b.toString().substring(1));
                    return Ops.quickMap2(b);
                }
                op = parseOp(cs, sb, c1);
                if (op instanceof Op2) {
                    return Ops.quickMap2(op);
                }
                throw new RuntimeException("Unhandled operator after f: " + op);
            case ':':
                try {
                    c1 = cs.get();
                } catch (NoSuchElementException e) {
                    throw new RuntimeException("Unfinished operator: :");
                }
                sb.append(c1);
                if (c1 >= 'A' && c1 <= 'Z') {
                    return Ops.setVar(c1);
                }
                op = parseOp(cs, sb, c1);
                if (op instanceof Op1) {
                    return Ops.quickMap(op);
                }
                if (op instanceof Op2) {
                    return Ops.quickFold(op);
                }
                throw new RuntimeException("Unhandled operator after ':': " + op);
            case '×':
                return Ops.quickFold(Ops.get("*"));
            case '†':
                return Ops.quickFold(Ops.get("+"));
            case '.':
                c1 = cs.get();
                sb.append(c1);
                if (c1 >= '0' && c1 <= '9') {
                    cs.put(c1);
                    cs.put(c);
                    return Ops.push(parseNumber(cs, sb));
                }
                if (c1 == '{') {
                    b = parse(cs, true);
                    sb.append(b.toString().substring(1));
                    return Ops.vector(b);
                }
                op = parseOp(cs, sb, c1);
                if (op instanceof Op2) {
                    return Ops.vector(op);
                }
                throw new RuntimeException("Unhandled operator after '.': " + op);
            default:
                op = Ops.get(c);
                if (op == null) {
                    throw new RuntimeException(c + " not handled");
                }
                return op;
        }
    }

    @Override
    public void run(final Convex x) {
        for (int i = 0; i < l.size(); ++i) {
            try {
                l.get(i).run(x);
            } catch (RuntimeException e) {
                x.errln();
                int off = o.get(i);
                final int a = name.lastIndexOf('\n', off) + 1;
                int b = name.indexOf('\n', off);
                if (b < 0) {
                    b = name.length();
                }
                x.errln(name.substring(a, b));
                off -= a;
                for (int j = 0; j < off; ++j) {
                    x.err(name.charAt(j + a) == '\t' ? '\t' : ' ');
                }
                x.errln('^');
                throw e;
            }
        }
    }

    public List<Op> getOps() {
        return l;
    }
}
