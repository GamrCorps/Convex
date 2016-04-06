package src.gamrcorps.convex;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static src.gamrcorps.convex.Conv.*;

public class Ops {
    private static final Op[] TABLE = new Op[256];
    private static final Map<String, Op> MAP = new HashMap<String, Op>();

    private static void add(final Op op) {
        final String s = op.toString();
        if (s.length() != 1) {
            if (MAP.put(s, op) != null) {
                throw new RuntimeException("Duplicate operator: " + s);
            }
            return;
        }
        final int x = s.charAt(0);
        if (x > 255) {
            throw new IllegalArgumentException(s);
        }
        if (TABLE[x] != null) {
            throw new RuntimeException("Duplicate operator: " + s);
        }
        TABLE[x] = op;
    }

    public static Op get(final char c) {
        if (c > 255) {
            throw new IllegalArgumentException(String.valueOf(c));
        }
        return TABLE[c];
    }

    public static Op get(final String s) {
        return s.length() == 1 ? get(s.charAt(0)) : MAP.get(s);
    }

    public static Op push(final Object o) {
        return new Op(repr(o)) {
            @Override
            public void run(final Convex x) {
                x.push(o);
            }
        };
    }

    public static Op pushVar(final char c) {
        return new Op(String.valueOf(c)) {
            @Override
            public void run(final Convex x) {
                final Object o = x.getVar(c);
                if (isBlock(o)) {
                    toBlock(o).run(x);
                } else {
                    x.push(o);
                }
            }
        };
    }

    public static Op setVar(final char c) {
        return new Op(":" + c) {
            @Override
            public void run(final Convex x) {
                x.setVar(c, x.peek());
            }
        };
    }

    public static Op quickMap(final Op op) {
        return new Op1(":" + op) {
            @Override
            public Object calc(final Convex x, final Object a) {
                if (!isList(a)) {
                    throw fail(a);
                }
                final List<?> al = toList(a);
                x.mark();
                for (Object o : al) {
                    x.push(o);
                    op.run(x);
                }
                x.popMark();
                return null;
            }
        };
    }

    public static Op quickFold(final Op op) {
        return new Op1(":" + op) {
            @Override
            public Object calc(final Convex x, final Object a) {
                if (!isList(a)) {
                    throw fail(a);
                }
                final List<?> al = toList(a);
                final int n = al.size();
                x.push(al.get(0));
                for (int i = 1; i < n; ++i) {
                    x.push(al.get(i));
                    op.run(x);
                }
                return null;
            }
        };
    }

    public static Op quickMap2(final Op op) {
        return new Op2("f" + op) {
            @Override
            public Object calc(final Convex x, final Object a, final Object b) {
                if (isList(a)) {
                    final List<?> al = toList(a);
                    x.mark();
                    for (Object o : al) {
                        x.push(o);
                        x.push(b);
                        op.run(x);
                    }
                    x.popMark();
                    return null;
                }
                if (isList(b)) {
                    final List<?> bl = toList(b);
                    x.mark();
                    for (Object o : bl) {
                        x.push(a);
                        x.push(o);
                        op.run(x);
                    }
                    x.popMark();
                    return null;
                }
                throw fail(a, b);
            }
        };
    }

    public static Op forLoop(final char c) {
        return new Op2("f" + c) {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isBlock(b)) {
                    final Block bb = toBlock(b);
                    if (isNumber(a)) {
                        final long al = toLong(a);
                        for (long i = 0; i < al; ++i) {
                            x.setVar(c, i);
                            bb.run(x);
                        }
                        return null;
                    }
                    if (isList(a)) {
                        final List<?> al = toList(a);
                        for (Object i : al) {
                            x.setVar(c, i);
                            bb.run(x);
                        }
                        return null;
                    }
                } else if (isBlock(a)) {
                    return calc(x, b, a);
                }
                throw fail(a, b);
            }
        };
    }

    public static Op vector(final Op op) {
        return new Op2("." + op) {
            @Override
            public Object calc(final Convex x, final Object a, final Object b) {
                if (!bothList(a, b)) {
                    throw fail(a, b);
                }
                final List<?> al = toList(a);
                final int an = al.size();
                final List<?> bl = toList(b);
                final int bn = bl.size();
                final int n = Math.max(an, bn);
                x.mark();
                for (int i = 0; i < n; ++i) {
                    if (i < an) {
                        x.push(al.get(i));
                        if (i < bn) {
                            x.push(bl.get(i));
                            op.run(x);
                        }
                    } else {
                        x.push(bl.get(i));
                    }
                }
                x.popMark();
                return null;
            }
        };
    }

    public static Op e10(final Number exp) {
        return new Op1("e" + exp) {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                if (isDouble(exp)) {
                    return toDouble(a) * Math.pow(10, exp.doubleValue());
                }
                if (!isLong(exp)) {
                    throw fail(a);
                }
                final long bl = exp.longValue();
                if (isDouble(a) || bl < 0) {
                    return Double.parseDouble(a + "e" + bl);
                }
                return adjustInt(toBigint(a).multiply(BigInteger.TEN.pow((int) bl)));
            }
        };
    }

    protected static Object adjustInt(final BigInteger x) {
        return x.bitLength() < 64 ? x.longValue() : x;
    }

    protected static final int compareLists(final List<?> a, final List<?> b) {
        final int an = a.size();
        final int bn = b.size();
        final int n = Math.min(an, bn);
        for (int i = 0; i < n; ++i) {
            final int x = compare(a.get(i), b.get(i));
            if (x != 0) {
                return x;
            }
        }
        return an - bn;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected static final int compare(final Object a, final Object b) {
        if (bothList(a, b)) {
            return compareLists(toList(a), toList(b));
        }
        if (a.getClass() == b.getClass()) {
            return ((Comparable) a).compareTo(b);
        }
        if (bothNumber(a, b)) {
            if (anyDouble(a, b)) {
                return Double.compare(toDouble(a), toDouble(b));
            }
            return toBigint(a).compareTo(toBigint(b));
        }
        if (anyChar(a, b) && anyNumber(a, b)) {
            return ((Long) toLong(a)).compareTo(toLong(b));
        }
        throw new IllegalArgumentException("Can't compare " + a.getClass().getSimpleName()
                + " with " + b.getClass().getSimpleName());
    }

    protected static final Comparator<Object> COMP = new Comparator<Object>() {
        @Override
        public int compare(final Object o1, final Object o2) {
            return Ops.compare(o1, o2);
        }
    };

    protected static final Comparator<Object[]> COMP2 = new Comparator<Object[]>() {
        @Override
        public int compare(final Object[] o1, final Object[] o2) {
            return Ops.compare(o1[1], o2[1]);
        }
    };

    private static List<Integer> preproc(final List<?> l) {
        final List<Integer> p = new ArrayList<Integer>();
        final int subl = l.size();
        p.add(-1);
        for (int i = 1, n = 0; i < subl; i++, n++) {
            if (l.get(i).equals(l.get(n))) {
                p.add(p.get(n));
            } else {
                p.add(n);
                do {
                    n = p.get(n);
                } while (n >= 0 && !l.get(i).equals(l.get(n)));
            }
        }
        return p;
    }

    protected static int find(final List<?> s, final List<?> sub) {
        final List<Integer> p = preproc(sub);
        final int sl = s.size();
        final int max = sub.size() - 1;

        for (int i = 0, m = 0; i < sl; i++, m++) {
            while (m >= 0 && !sub.get(m).equals(s.get(i))) {
                m = p.get(m);
            }
            if (m == max) {
                return i - m;
            }
        }
        return -1;
    }

    protected static List<?> split(final List<?> s, final List<?> sub, final boolean empty) {
        final List<Integer> p = preproc(sub);
        final int sl = s.size();
        final int max = sub.size() - 1;

        final List<Object> l = new ArrayList<Object>();
        int x = 0;
        for (int i = 0, m = 0; i < sl; i++, m++) {
            while (m >= 0 && !sub.get(m).equals(s.get(i))) {
                m = p.get(m);
            }
            if (m == max) {
                final List<?> t = s.subList(x, i - m);
                if (empty || !t.isEmpty()) {
                    l.add(new ArrayList<Object>(t));
                }
                x = i + 1;
                m = -1;
            }
        }
        final List<?> t = s.subList(x, s.size());
        if (empty || !t.isEmpty()) {
            l.add(new ArrayList<Object>(t));
        }
        return l;
    }

    protected static List<Object> pair(final Object x, final Object y) {
        final List<Object> l = new ArrayList<Object>(2);
        l.add(x);
        l.add(y);
        return l;
    }

    protected static int adjustIndexMod(final Object x, final int n) {
        if (!(x instanceof Long)) {
            throw new IllegalArgumentException(x.getClass().getSimpleName() + " can't be an index");
        }
        int y = (int) ((Long) x % n);
        if (y < 0) {
            y += n;
        }
        return y;
    }

    protected static int adjustIndex(final Object x, final int n) {
        if (!(x instanceof Long)) {
            throw new IllegalArgumentException(x.getClass().getSimpleName() + " can't be an index");
        }
        int y = ((Long) x).intValue();
        if (y < 0) {
            y += n;
            if (y < 0) {
                y = 0;
            }
        } else if (y > n) {
            y = n;
        }
        return y;
    }

    static {
        add(new Op2("+") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (a instanceof Quaternion || b instanceof Quaternion) {
                    if (a instanceof Quaternion && b instanceof Quaternion) {
                        return ((Quaternion) a).add(((Quaternion) b));
                    } else if (a instanceof Quaternion) {
                        return ((Quaternion) a).add(new Quaternion(toDouble(b)));
                    } else {
                        return ((Quaternion) b).add(new Quaternion(toDouble(a)));
                    }
                }
                if (anyList(a, b) || bothChar(a, b)) {
                    final List<Object> l = toNewList(a);
                    l.addAll(toList(b));
                    return l;
                }
                if (bothNumber(a, b)) {
                    if (anyDouble(a, b)) {
                        return toDouble(a) + toDouble(b);
                    }
                    if (bothLong(a, b)) {
                        final long al = toLong(a);
                        final long bl = toLong(b);
                        final long r = al + bl;
                        if ((al ^ bl) < 0 || (al ^ r) >= 0) {
                            return r;
                        }
                    }
                    return adjustInt(toBigint(a).add(toBigint(b)));
                }
                if (anyChar(a, b) && anyNumber(a, b)) {
                    return (char) (toLong(a) + toLong(b));
                }
                throw fail(a, b);
            }
        });

        add(new Op2("-") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (a instanceof Quaternion || b instanceof Quaternion) {
                    if (a instanceof Quaternion && b instanceof Quaternion) {
                        return ((Quaternion) a).subtract(((Quaternion) b));
                    } else if (a instanceof Quaternion) {
                        return ((Quaternion) a).subtract(new Quaternion(toDouble(b)));
                    } else {
                        return new Quaternion(toDouble(a)).subtract(((Quaternion) b));
                    }
                }
                if (bothChar(a, b)) {
                    return toLong(a) - toLong(b);
                }
                if (bothNumber(a, b)) {
                    if (anyDouble(a, b)) {
                        return toDouble(a) - toDouble(b);
                    }
                    if (bothLong(a, b)) {
                        final long al = toLong(a);
                        final long bl = toLong(b);
                        final long r = al - bl;
                        if ((al ^ bl) >= 0 || (al ^ r) >= 0) {
                            return r;
                        }
                    }
                    return adjustInt(toBigint(a).subtract(toBigint(b)));
                }
                if (isChar(a) && isNumber(b)) {
                    return (char) (toLong(a) - toLong(b));
                }
                if (anyList(a, b)) {
                    if (anyBlock(a, b)) {
                        throw fail(a, b); // maybe do something different with blocks later
                    }
                    final List<Object> l = toNewList(a);
                    l.removeAll(toList(b));
                    return l;
                }
                throw fail(a, b);
            }
        });

        add(new Op2("*") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (a instanceof Quaternion || b instanceof Quaternion) {
                    if (a instanceof Quaternion && b instanceof Quaternion) {
                        return ((Quaternion) a).multiply(((Quaternion) b));
                    } else if (a instanceof Quaternion) {
                        return ((Quaternion) a).multiply(toDouble(b));
                    } else {
                        return ((Quaternion) b).multiply(toDouble(a));
                    }
                }
                if (isNumber(a) && (isList(b) || isChar(b) || isBlock(b))) {
                    return calc(x, b, a);
                }
                if ((isBlock(a) || isChar(a)) && isList(b)) {
                    return calc(x, b, a);
                }
                if ((isList(a) || isChar(a)) && isNumber(b)) {
                    final List<?> al = toList(a);
                    final int n = toInt(b);
                    final int m = al.size();
                    final int t = n * m;
                    if (t == 0) {
                        return new ArrayList<Object>(0);
                    }
                    final Object[] a1 = al.toArray();
                    final Object[] a2 = new Object[t];
                    System.arraycopy(a1, 0, a2, 0, m);
                    int i = m;
                    while (i * 2 <= t) {
                        System.arraycopy(a2, 0, a2, i, i);
                        i *= 2;
                    }
                    if (i < t) {
                        System.arraycopy(a2, 0, a2, i, t - i);
                    }
                    return new ArrayList<Object>(Arrays.asList(a2));
                }
                if (bothNumber(a, b)) {
                    if (anyDouble(a, b)) {
                        return toDouble(a) * toDouble(b);
                    }
                    if (bothLong(a, b)) {
                        final long al = toLong(a);
                        final long bl = toLong(b);
                        if (al == 0 || bl == 0) {
                            return 0l;
                        }
                        final long r = al * bl;
                        if (r / al == bl && (al ^ bl ^ r) >= 0) {
                            return r;
                        }
                    }
                    return adjustInt(toBigint(a).multiply(toBigint(b)));
                }
                if (isBlock(a) && isNumber(b)) {
                    final Block ab = toBlock(a);
                    final int n = toInt(b);
                    for (int i = 0; i < n; ++i) {
                        ab.run(x);
                    }
                    return null;
                }
                if (isList(a)) {
                    final List<?> al = toList(a);
                    final int n = al.size();
                    if (isBlock(b)) {
                        final Block bb = toBlock(b);
                        x.push(al.get(0));
                        for (int i = 1; i < n; ++i) {
                            x.push(al.get(i));
                            bb.run(x);
                        }
                        return null;
                    }
                    if (isList(b) || isChar(b)) {
                        final List<?> bl = toList(b);
                        final List<Object> l = new ArrayList<Object>(n + (n == 0 ? 0 : (n - 1) * bl.size()));
                        for (int i = 0; i < n; ++i) {
                            if (i > 0) {
                                l.addAll(bl);
                            }
                            final Object o = al.get(i);
                            if (isList(o)) {
                                l.addAll(toList(o));
                            } else {
                                l.add(o);
                            }
                        }
                        return l;
                    }
                }
                throw fail(a, b);
            }
        });

        add(new Op2("/") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (a instanceof Quaternion || b instanceof Quaternion) {
                    if (a instanceof Quaternion && b instanceof Quaternion) {
                        return ((Quaternion) a).divide(((Quaternion) b));
                    } else if (a instanceof Quaternion) {
                        return ((Quaternion) a).divide(toDouble(b));
                    } else {
                        return new Quaternion(toDouble(a)).divide(((Quaternion) b));
                    }
                }
                if (isBlock(a)) {
                    if (isBlock(b)) {
                        throw fail(a, b);
                    }
                    return calc(x, b, a);
                }
                if (isNumber(a)) {
                    if (isList(b)) {
                        return calc(x, b, a);
                    }
                    if (isNumber(b)) {
                        if (anyDouble(a, b)) {
                            return toDouble(a) / toDouble(b);
                        }
                        if (bothLong(a, b)) {
                            final long al = toLong(a);
                            final long bl = toLong(b);
                            if (al != Long.MIN_VALUE || bl != -1) {
                                return al / bl;
                            }
                        }
                        return adjustInt(toBigint(a).divide(toBigint(b)));
                    }
                    if (isBlock(b)) {
                        final Block bb = toBlock(b);
                        final long al = toLong(a);
                        for (long i = 0; i < al; ++i) {
                            x.push(i);
                            bb.run(x);
                        }
                        return null;
                    }
                }
                if (isList(a)) {
                    final List<?> al = toList(a);
                    if (isBlock(b)) {
                        final Block bb = toBlock(b);
                        for (Object o : al) {
                            x.push(o);
                            bb.run(x);
                        }
                        return null;
                    }
                    if (isList(b) || isChar(b)) {
                        return split(al, toList(b), true);
                    }
                    if (isNumber(b)) {
                        final int n = toInt(b);
                        if (n <= 0) {
                            throw new RuntimeException("Invalid size for splitting");
                        }
                        final int m = al.size();
                        final List<Object> l = new ArrayList<Object>((m + n - 1) / n);
                        for (int i = 0; i < m; i += n) {
                            l.add(new ArrayList<Object>(al.subList(i, Math.min(i + n, m))));
                        }
                        return l;
                    }
                }
                throw fail(a, b);
            }
        });

        add(new Op2("%") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isBlock(a)) {
                    if (isBlock(b)) {
                        throw fail(a, b);
                    }
                    return calc(x, b, a);
                }
                if (isNumber(a)) {
                    if (isList(b)) {
                        return calc(x, b, a);
                    }
                    if (isNumber(b)) {
                        if (anyDouble(a, b)) {
                            return toDouble(a) % toDouble(b);
                        }
                        if (bothLong(a, b)) {
                            return toLong(a) % toLong(b);
                        }
                        return adjustInt(toBigint(a).mod(toBigint(b)));
                    }
                    if (isBlock(b)) {
                        final Block bb = toBlock(b);
                        final long al = toLong(a);
                        x.mark();
                        for (long i = 0; i < al; ++i) {
                            x.push(i);
                            bb.run(x);
                        }
                        x.popMark();
                        return null;
                    }
                }
                if (isList(a)) {
                    final List<?> al = toList(a);
                    if (isNumber(b)) {
                        final int n = al.size();
                        int bi = toInt(b);
                        boolean rev = false;
                        if (bi < 0) {
                            rev = true;
                            bi = -bi;
                        }
                        final List<Object> l = new ArrayList<Object>(n / bi + 1);
                        for (int i = 0; i < n; i += bi) {
                            l.add(al.get(rev ? n - 1 - i : i));
                        }
                        return l;
                    }
                    if (isBlock(b)) {
                        final Block bb = toBlock(b);
                        x.mark();
                        for (Object o : al) {
                            x.push(o);
                            bb.run(x);
                        }
                        x.popMark();
                        return null;
                    }
                    if (isList(b) || isChar(b)) {
                        return split(al, toList(b), false);
                    }
                }
                throw fail(a, b);
            }
        });

        add(new Op1("_") {
            @Override
            public void run(final Convex x) {
                x.push(x.peek());
            }

            @Override
            protected Object calc(final Convex x, final Object a) {
                // dummy, just needed to extend Op1
                return null;
            }
        });

        add(new Op1(";") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                return null;
            }
        });

        add(new Op2("\\") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                x.push(b);
                return a;
            }
        });

        add(new Op("@") {
            @Override
            public void run(final Convex x) {
                final Object a = x.pop();
                final Object b = x.pop();
                final Object c = x.pop();
                x.push(b);
                x.push(a);
                x.push(c);
            }
        });

        add(new Op1("i") {
            @Override
            public Object calc(final Convex x, final Object a) {
                if (isBigint(a)) {
                    return adjustInt(toBigint(a));
                }
                if (isString(a)) {
                    final String s = listToStr(a);
                    return s.length() < 19 ? Long.parseLong(s) : adjustInt(new BigInteger(s));
                }
                if (isDouble(a)) {
                    final double ad = toDouble(a);
                    final double max = 9.223372e18;
                    if (ad > max || ad < -max) {
                        return adjustInt(BigDecimal.valueOf(ad).toBigInteger());
                    }
                }
                return toLong(a);
            }
        });

        add(new Op1("d") {
            @Override
            public Object calc(final Convex x, final Object a) {
                return toDouble(a);
            }
        });

        add(new Op1("c") {
            @Override
            public Object calc(final Convex x, final Object a) {
                if (a instanceof Quaternion) {
                    return ((Quaternion) a).conjugate();
                }
                return toChar(a);
            }
        });

        add(new Op1("a") {
            @Override
            public Object calc(final Convex x, final Object a) {
                final List<Object> l = new ArrayList<Object>(1);
                l.add(a);
                return l;
            }
        });

        add(new Op1("s") {
            @Override
            public Object calc(final Convex x, final Object a) {
                return strToList(toStr(a));
            }
        });

        add(new Op2("=") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (a.getClass() == b.getClass()) {
                    return boolVal(a.equals(b));
                }
                if (!isList(a) && isList(b)) {
                    return calc(x, b, a);
                }
                if (isList(a)) {
                    final List<?> al = toList(a);
                    if (isNumber(b)) {
                        final int bi = adjustIndexMod(b, al.size());
                        return al.get(bi);
                    }
                    if (isBlock(b)) {
                        final Block bb = toBlock(b);
                        for (int i = 0; i < al.size(); ++i) {
                            x.push(al.get(i));
                            bb.run(x);
                            if (toBool(x.pop())) {
                                return al.get(i);
                            }
                        }
                        return null;
                    }
                    throw fail(a, b);
                }
                if (bothNumber(a, b)) {
                    if (anyDouble(a, b)) {
                        return boolVal(toDouble(a) == toDouble(b));
                    }
                    return boolVal(toBigint(a).equals(toBigint(b)));
                }
                if (anyChar(a, b) && anyNumber(a, b)) {
                    return boolVal(toLong(a) == toLong(b));
                }
                throw fail(a, b);
            }
        });

        add(new Op2("<") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isNumber(a) && isList(b)) {
                    return calc(x, b, a);
                }
                if (isList(a) && isNumber(b)) {
                    final List<?> al = toList(a);
                    final int bi = adjustIndex(b, al.size());
                    return new ArrayList<Object>(al.subList(0, bi));
                }
                return boolVal(compare(a, b) < 0);
            }
        });

        add(new Op2(">") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isNumber(a) && isList(b)) {
                    return calc(x, b, a);
                }
                if (isList(a) && isNumber(b)) {
                    final List<?> al = toList(a);
                    final int n = al.size();
                    final int bi = adjustIndex(b, n);
                    return new ArrayList<Object>(al.subList(bi, n));
                }
                return boolVal(compare(a, b) > 0);
            }
        });

        add(new Op1("g") {
            private final Pattern charsetPattern = Pattern.compile(";\\s*CHARSET\\s*=\\s*(\\S+)");
            private final char[] buf = new char[4096];

            @Override
            protected Object calc(final Convex x, final Object a) {
                if (isBlock(a)) {
                    final Block ab = toBlock(a);
                    do {
                        ab.run(x);
                    } while (toBool(x.pop()));
                    return null;
                }
                if (isNumber(a)) {
                    if (isDouble(a)) {
                        return (long) Math.signum(toDouble(a));
                    }
                    if (isLong(a)) {
                        return (long) Long.signum(toLong(a));
                    }
                    return (long) toBigint(a).signum();
                }
                if (!isString(a)) {
                    throw fail(a);
                }
                String as = listToStr(a);
                if (!as.contains("://")) {
                    as = "http://" + as;
                }
                try {
                    final URLConnection conn = new URL(as).openConnection();
                    final String ctype = conn.getContentType();
                    final String charset;
                    if (ctype == null) {
                        charset = "UTF-8";
                    } else {
                        final Matcher m = charsetPattern.matcher(ctype.toUpperCase());
                        charset = m.find() ? m.group(1) : "UTF-8";
                    }
                    final Reader r = new InputStreamReader(conn.getInputStream(), charset);
                    final StringBuilder sb = new StringBuilder();
                    while (true) {
                        final int n = r.read(buf);
                        if (n < 0) {
                            break;
                        }
                        sb.append(buf, 0, n);
                    }
                    r.close();
                    return strToList(sb.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        add(new Op1("h") {
            @Override
            public Object calc(final Convex x, final Object a) {
                if (!isBlock(a)) {
                    throw fail(a);
                }
                final Block ab = toBlock(a);
                do {
                    ab.run(x);
                } while (toBool(x.peek()));
                return null;
            }
        });

        add(new Op2("w") {
            @Override
            public Object calc(final Convex x, final Object a, final Object b) {
                if (!bothBlock(a, b)) {
                    throw fail(a, b);
                }
                final Block ab = toBlock(a);
                final Block bb = toBlock(b);
                while (true) {
                    ab.run(x);
                    if (!toBool(x.pop())) {
                        break;
                    }
                    bb.run(x);
                }
                return null;
            }
        });

        add(new Op3("?") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b, final Object c) {
                final Object o = toBool(a) ? b : c;
                if (isBlock(o)) {
                    toBlock(o).run(x);
                    return null;
                }
                return o;
            }
        });

        add(new Op2("#") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (bothNumber(a, b)) {
                    if (anyDouble(a, b) || toLong(b) < 0) {
                        return Math.pow(toDouble(a), toDouble(b));
                    }
                    return adjustInt(toBigint(a).pow(toInt(b)));
                }
                if (isList(a)) {
                    final List<?> al = toList(a);
                    if (isBlock(b)) {
                        final Block bb = toBlock(b);
                        for (int i = 0; i < al.size(); ++i) {
                            x.push(al.get(i));
                            bb.run(x);
                            if (toBool(x.pop())) {
                                return (long) i;
                            }
                        }
                        return -1l;
                    }
                    final int pos = isList(b) ? find(al, toList(b)) : al.indexOf(b);
                    return (long) pos;
                }
                if (isList(b)) {
                    if (isBlock(a)) {
                        return calc(x, b, a);
                    }
                    return (long) toList(b).indexOf(a);
                }
                throw fail(a, b);
            }
        });

        add(new Op1(",") {
            @Override
            public Object calc(final Convex x, final Object a) {
                if (isNumber(a)) {
                    final int n = toInt(a);
                    final List<Object> l = new ArrayList<Object>(n);
                    for (long i = 0; i < n; ++i) {
                        l.add(i);
                    }
                    return l;
                }
                if (isChar(a)) {
                    final char ac = toChar(a);
                    final List<Object> l = new ArrayList<Object>(ac);
                    for (char i = 0; i < ac; ++i) {
                        l.add(i);
                    }
                    return l;
                }
                if (isList(a)) {
                    return (long) toList(a).size();
                }
                if (isBlock(a)) {
                    final Block ab = toBlock(a);
                    final Object b = x.pop();
                    if (isList(b)) {
                        final List<?> bl = toList(b);
                        final List<Object> l = new ArrayList<Object>();
                        for (Object o : bl) {
                            x.push(o);
                            ab.run(x);
                            if (toBool(x.pop())) {
                                l.add(o);
                            }
                        }
                        return l;
                    }
                    if (isNumber(b)) {
                        final long bl = toLong(b);
                        final List<Object> l = new ArrayList<Object>();
                        for (long i = 0; i < bl; ++i) {
                            x.push(i);
                            ab.run(x);
                            if (toBool(x.pop())) {
                                l.add(i);
                            }
                        }
                        return l;
                    }
                    throw fail(b, a);
                }
                throw fail(a);
            }
        });

        add(new Op1("!") {
            @Override
            public Object calc(final Convex x, final Object a) {
                return boolVal(!toBool(a));
            }
        });

        add(new Op1("(") {
            @Override
            public Object calc(final Convex x, final Object a) {
                if (isNumber(a)) {
                    if (isDouble(a)) {
                        return toDouble(a) - 1;
                    }
                    if (isLong(a)) {
                        if (!a.equals(Long.MIN_VALUE)) {
                            return toLong(a) - 1;
                        }
                    }
                    return adjustInt(toBigint(a).subtract(BigInteger.ONE));
                }
                if (isChar(a)) {
                    return (char) (toChar(a) - 1);
                }
                if (isList(a)) {
                    final List<Object> l = toNewList(a);
                    final Object o = l.remove(0);
                    x.push(l);
                    return o;
                }
                throw fail(a);
            }
        });

        add(new Op1(")") {
            @Override
            public Object calc(final Convex x, final Object a) {
                if (isNumber(a)) {
                    if (isDouble(a)) {
                        return toDouble(a) + 1;
                    }
                    if (isLong(a)) {
                        if (!a.equals(Long.MAX_VALUE)) {
                            return toLong(a) + 1;
                        }
                    }
                    return adjustInt(toBigint(a).add(BigInteger.ONE));
                }
                if (isChar(a)) {
                    return (char) (toChar(a) + 1);
                }
                if (isList(a)) {
                    final List<Object> l = toNewList(a);
                    final Object o = l.remove(l.size() - 1);
                    x.push(l);
                    return o;
                }
                throw fail(a);
            }
        });

        add(new Op1("$") {
            @SuppressWarnings({"rawtypes", "unchecked"})
            @Override
            public Object calc(final Convex x, final Object a) {
                if (isNumber(a)) {
                    return x.get(toInt(a));
                }
                if (isList(a)) {
                    final List l = toNewList(a);
                    Collections.sort(l, COMP);
                    return l;
                }
                if (isBlock(a)) {
                    final Object b = x.pop();
                    if (!isList(b)) {
                        throw fail(b, a);
                    }
                    final Block ab = toBlock(a);
                    final List<?> bl = toList(b);
                    final List<Object[]> l = new ArrayList<Object[]>(bl.size());
                    for (Object i : bl) {
                        x.push(i);
                        ab.run(x);
                        final Object j = x.pop();
                        l.add(new Object[]{i, j});
                    }
                    Collections.sort(l, COMP2);
                    final List<Object> r = new ArrayList<Object>(l.size());
                    for (Object[] t : l) {
                        r.add(t[0]);
                    }
                    return r;
                }
                throw fail(a);
            }
        });

        add(new Op2("&") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (bothNumber(a, b)) {
                    if (anyDouble(a, b)) {
                        throw fail(a, b);
                    }
                    if (bothLong(a, b)) {
                        return toLong(a) & toLong(b);
                    }
                    return adjustInt(toBigint(a).and(toBigint(b)));
                }
                if (isBlock(a)) {
                    throw fail(a, b);
                }
                if (isBlock(b)) {
                    if (toBool(a)) {
                        toBlock(b).run(x);
                    }
                    return null;
                }
                if (anyList(a, b)) {
                    final Set<Object> s = new LinkedHashSet<Object>(toList(a));
                    s.retainAll(toList(b));
                    return new ArrayList<Object>(s);
                }
                if (anyChar(a, b) && anyLong(a, b)) {
                    return (char) (toLong(a) & toLong(b));
                }
                throw fail(a, b);
            }
        });

        add(new Op2("|") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (bothNumber(a, b)) {
                    if (anyDouble(a, b)) {
                        throw fail(a, b);
                    }
                    if (bothLong(a, b)) {
                        return toLong(a) | toLong(b);
                    }
                    return adjustInt(toBigint(a).or(toBigint(b)));
                }
                if (isBlock(a)) {
                    throw fail(a, b);
                }
                if (isBlock(b)) {
                    if (!toBool(a)) {
                        toBlock(b).run(x);
                    }
                    return null;
                }
                if (anyList(a, b)) {
                    final Set<Object> s = new LinkedHashSet<Object>(toList(a));
                    s.addAll(toList(b));
                    return new ArrayList<Object>(s);
                }
                if (anyChar(a, b) && anyLong(a, b)) {
                    return (char) (toLong(a) | toLong(b));
                }
                throw fail(a, b);
            }
        });

        add(new Op2("^") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (bothNumber(a, b)) {
                    if (anyDouble(a, b)) {
                        throw fail(a, b);
                    }
                    if (bothLong(a, b)) {
                        return toLong(a) ^ toLong(b);
                    }
                    return adjustInt(toBigint(a).xor(toBigint(b)));
                }
                if (anyList(a, b)) {
                    if (anyBlock(a, b)) {
                        throw fail(a, b); // maybe do something different with blocks later
                    }
                    final Set<Object> s = new LinkedHashSet<Object>(toList(a));
                    s.removeAll(toList(b));
                    final Set<Object> s2 = new LinkedHashSet<Object>(toList(b));
                    s2.removeAll(toList(a));
                    s.addAll(s2);
                    return new ArrayList<Object>(s);
                }
                if (bothChar(a, b)) {
                    return toLong(a) ^ toLong(b);
                }
                if (anyChar(a, b) && anyLong(a, b)) {
                    return (char) (toLong(a) ^ toLong(b));
                }
                throw fail(a, b);
            }
        });

        add(new Op("r") {
            @Override
            public void run(final Convex x) {
                x.push(strToList(x.readNext()));
            }
        });

        add(new Op("l") {
            @Override
            public void run(final Convex x) {
                x.push(strToList(x.readLine()));
            }
        });

        add(new Op("q") {
            @Override
            public void run(final Convex x) {
                x.push(strToList(x.readAll()));
            }
        });

        add(new Op1("~") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (isNumber(a)) {
                    if (isDouble(a)) {
                        throw fail(a);
                    }
                    if (isLong(a)) {
                        return ~toLong(a);
                    }
                    return adjustInt(toBigint(a).not());
                }
                if (isBlock(a)) {
                    toBlock(a).run(x);
                    return null;
                }
                if (isString(a) || isChar(a)) {
                    Block.parse(new StringReader(toStr(a)), false).run(x);
                    return null;
                }
                if (isList(a)) {
                    for (Object o : toList(a)) {
                        x.push(o);
                    }
                    return null;
                }
                throw fail(a);
            }
        });

        add(new Op3("t") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b, final Object c) {
                if (isNumber(a) && isList(b)) {
                    return calc(x, b, a, c);
                }
                if (isList(a) && isNumber(b)) {
                    final List<Object> al = toNewList(a);
                    final int bi = adjustIndexMod(b, al.size());
                    al.set(bi, c);
                    return al;
                }
                throw fail(a, b, c);
            }
        });

        add(new Op2("b") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isNumber(a) && isList(b)) {
                    return calc(x, b, a);
                }
                if (!isNumber(b) || anyDouble(a, b)) {
                    throw fail(a, b);
                }
                if (isNumber(a)) {
                    final List<Object> l = new ArrayList<Object>();
                    if (anyBigint(a, b)) {
                        BigInteger ab = toBigint(a).abs();
                        final BigInteger bb = toBigint(b).abs();
                        while (!ab.equals(BigInteger.ZERO)) {
                            final BigInteger[] d = ab.divideAndRemainder(bb);
                            l.add(adjustInt(d[1]));
                            ab = d[0];
                        }
                    } else {
                        long al = Math.abs(toLong(a));
                        final long bl = Math.abs(toLong(b));
                        if (bl == 1) {
                            l.addAll(Collections.nCopies((int) al, 1l));
                        } else {
                            while (al != 0) {
                                l.add(al % bl);
                                al /= bl;
                            }
                        }
                    }
                    if (l.isEmpty()) {
                        l.add(0l);
                    }
                    Collections.reverse(l);
                    return l;
                }
                if (isList(a)) {
                    final BigInteger bb = toBigint(b).abs();
                    if (isString(a)) {
                        return adjustInt(new BigInteger(toStr(a), bb.intValue()));
                    }
                    final List<?> al = toList(a);
                    BigInteger t = BigInteger.ZERO;
                    for (Object o : al) {
                        t = t.multiply(bb).add(toBigint(o));
                    }
                    return adjustInt(t);
                }
                throw fail(a, b);
            }
        });

        add(new Op("[") {
            @Override
            public void run(final Convex x) {
                x.mark();
            }
        });

        add(new Op("]") {
            @Override
            public void run(final Convex x) {
                x.popMark();
            }
        });

        add(new Op1("`") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                return strToList(repr(a));
            }
        });

        add(new Op1("p") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                x.println(repr(a));
                return null;
            }
        });

        add(new Op1("o") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                x.print(toStr(a));
                return null;
            }
        });

        add(new Op("ed") {
            @Override
            public void run(final Convex x) {
                x.show();
            }
        });

        add(new Op2("e<") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                return compare(a, b) < 0 ? a : b;
            }
        });

        add(new Op2("e>") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                return compare(a, b) > 0 ? a : b;
            }
        });

        add(new Op1("eu") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (isChar(a)) {
                    return Character.toUpperCase(toChar(a));
                }
                if (isString(a)) {
                    return strToList(listToStr(a).toUpperCase());
                }
                throw fail(a);
            }
        });

        add(new Op1("el") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (isChar(a)) {
                    return Character.toLowerCase(toChar(a));
                }
                if (isString(a)) {
                    return strToList(listToStr(a).toLowerCase());
                }
                throw fail(a);
            }
        });

        add(new Op2("e&") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                final Object o = toBool(a) ? b : a;
                if (isBlock(o)) {
                    toBlock(o).run(x);
                    return null;
                }
                return o;
            }
        });

        add(new Op2("e|") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                final Object o = toBool(a) ? a : b;
                if (isBlock(o)) {
                    toBlock(o).run(x);
                    return null;
                }
                return o;
            }
        });

        add(new Op2("m<") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isNumber(b)) {
                    long bl = toLong(b);
                    if (bl < 0) {
                        return ((Op2) get("m>")).calc(x, a, -bl);
                    }
                    if (isNumber(a)) {
                        if (isDouble(a)) {
                            return toDouble(a) * (1 << bl);
                        }
                        return adjustInt(toBigint(a).shiftLeft((int) bl));
                    }
                    if (isList(a)) {
                        final List<?> al = toList(a);
                        final int n = al.size();
                        if (n < 2) {
                            return al;
                        }
                        final int k = (int) (bl % n);
                        if (k == 0) {
                            return al;
                        }
                        final List<Object> l = new ArrayList<Object>(n);
                        l.addAll(al.subList(k, n));
                        l.addAll(al.subList(0, k));
                        return l;
                    }
                }
                if (isNumber(a) && isList(b)) {
                    return calc(x, b, a);
                }
                throw fail(a);
            }
        });

        add(new Op2("m>") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isNumber(b)) {
                    long bl = toLong(b);
                    if (bl < 0) {
                        return ((Op2) get("m<")).calc(x, a, -bl);
                    }
                    if (isNumber(a)) {
                        if (isDouble(a)) {
                            return toDouble(a) / (1 << bl);
                        }
                        return adjustInt(toBigint(a).shiftRight((int) bl));
                    }
                    if (isList(a)) {
                        final List<?> al = toList(a);
                        final int n = al.size();
                        if (n < 2) {
                            return al;
                        }
                        final int k = (int) (bl % n);
                        if (k == 0) {
                            return al;
                        }
                        final List<Object> l = new ArrayList<Object>(n);
                        l.addAll(al.subList(n - k, n));
                        l.addAll(al.subList(0, n - k));
                        return l;
                    }
                }
                if (isNumber(a) && isList(b)) {
                    return calc(x, b, a);
                }
                throw fail(a);
            }
        });

        add(new Op1("mr") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                final Random r = x.getRandom();
                if (isNumber(a)) {
                    if (isDouble(a)) {
                        return r.nextDouble() * toDouble(a);
                    }
                    if (isLong(a)) {
                        final long al = toLong(a);
                        if (al <= 0) {
                            throw new IllegalArgumentException("Parameter must be positive");
                        }
                        if (al <= Integer.MAX_VALUE) {
                            return (long) r.nextInt((int) al);
                        }
                        long bits, val;
                        do {
                            bits = r.nextLong() & Long.MAX_VALUE;
                            val = bits % al;
                        } while (bits - val + (al - 1) < 0l);
                        return val;
                    }
                    final BigInteger ab = toBigint(a);
                    if (ab.signum() <= 0) {
                        throw new IllegalArgumentException("Parameter must be positive");
                    }
                    final int n = ab.bitLength();
                    BigInteger bits, val;
                    do {
                        bits = new BigInteger(n, r);
                        val = bits.mod(ab);
                    } while (bits.subtract(val).add(ab).subtract(BigInteger.ONE).bitLength() <= n);
                    return val;
                }
                if (isList(a)) {
                    final List<Object> l = toNewList(a);
                    Collections.shuffle(l, r);
                    return l;
                }
                throw fail(a);
            }
        });

        add(new Op1("mR") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isList(a)) {
                    throw fail(a);
                }
                final List<?> al = toList(a);
                if (al.isEmpty()) {
                    throw new IllegalArgumentException("Empty array");
                }
                return al.get(x.getRandom().nextInt(al.size()));
            }
        });

        add(new Op1("z") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (a instanceof Quaternion) {
                    return ((Quaternion) a).magnitude();
                }
                if (isNumber(a)) {
                    if (isDouble(a)) {
                        return Math.abs(toDouble(a));
                    }
                    if (isBigint(a)) {
                        return toBigint(a).abs();
                    }
                    final long al = toLong(a);
                    return al == Long.MIN_VALUE ? BigInteger.valueOf(al).abs() : Math.abs(al);
                }
                if (isList(a)) {
                    final List<List<Object>> l = new ArrayList<List<Object>>();
                    for (Object o : toList(a)) {
                        final List<?> l2 = toList(o);
                        for (int j = 0; j < l2.size(); ++j) {
                            final List<Object> lj;
                            if (j == l.size()) {
                                l.add(lj = new ArrayList<Object>());
                            } else {
                                lj = l.get(j);
                            }
                            lj.add(l2.get(j));
                        }
                    }
                    return l;
                }
                throw fail(a);
            }
        });

        add(new Op2("md") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (!bothNumber(a, b)) {
                    throw fail(a, b);
                }
                if (anyDouble(a, b)) {
                    final double ad = toDouble(a);
                    final double bd = toDouble(b);
                    final double r = ad % bd;
                    x.push(Math.round((ad - r) / bd));
                    return ad % bd;
                }
                if (bothLong(a, b)) {
                    final long al = toLong(a);
                    final long bl = toLong(b);
                    if (al != Long.MIN_VALUE || bl != -1) {
                        x.push(al / bl);
                        return al % bl;
                    }
                }
                final BigInteger[] dr = toBigint(a).divideAndRemainder(toBigint(b));
                x.push(adjustInt(dr[0]));
                return adjustInt(dr[1]);
            }
        });

        add(new Op("j") {
            @Override
            public void run(final Convex x) {
                Memo m = x.getMemo();
                if (m != null) {
                    m.calc(x);
                    return;
                }
                final int n;
                final Block bl;
                final Object a = x.pop();
                if (isLong(a)) {
                    n = toInt(a);
                    bl = toBlock(x.pop());
                } else if (isBlock(a)) {
                    n = 1;
                    bl = toBlock(a);
                } else {
                    throw fail(a);
                }
                final Object l = x.pop();
                m = new Memo(l, bl, n);
                x.setMemo(m);
                m.calc(x);
                x.setMemo(null);
            }
        });

        add(new Op("ea") {
            @Override
            public void run(final Convex x) {
                final List<Object> l = new ArrayList<Object>();
                for (String s : x.getArgs()) {
                    l.add(strToList(s));
                }
                x.push(l);
            }
        });

        add(new Op2("m*") {
            private List<?> list;
            private List<Object> all;
            private Object[] comb;
            private int n;
            private int size;

            private void add(final int k) {
                if (k == size) {
                    all.add(new ArrayList<Object>(Arrays.asList(comb)));
                } else {
                    for (int i = 0; i < n; ++i) {
                        comb[k] = list.get(i);
                        add(k + 1);
                    }
                }
            }

            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (bothList(a, b)) {
                    final List<?> al = toList(a);
                    final List<?> bl = toList(b);
                    final List<Object> l = new ArrayList<Object>(al.size() * bl.size());
                    for (Object i : al) {
                        for (Object j : bl) {
                            l.add(pair(i, j));
                        }
                    }
                    return l;
                }
                if (isLong(a) && isList(b)) {
                    return calc(x, b, a);
                }
                if (isList(a) && isLong(b)) {
                    list = toList(a);
                    n = list.size();
                    size = toInt(b);
                    comb = new Object[size];
                    all = new ArrayList<Object>();
                    add(0);
                    list = null;
                    comb = null;
                    final List<Object> l = all;
                    all = null;
                    return l;
                }
                if (bothLong(a, b)) {
                    final int ai = toInt(a);
                    final List<Object> l = new ArrayList<Object>(ai);
                    for (long i = 0; i < ai; ++i) {
                        l.add(i);
                    }
                    return calc(x, l, b);
                }
                throw fail(a, b);
            }
        });

        add(new Op1("mp") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isLong(a)) {
                    throw fail(a);
                }
                final long al = toLong(a);
                if (al == 2 || al == 3) {
                    return 1l;
                }
                if (al < 5 || al % 2 == 0) {
                    return 0l;
                }
                for (int i = 3; i * i <= al; i += 2) {
                    if (al % i == 0) {
                        return 0l;
                    }
                }
                return 1l;
            }
        });

        add(new Op1("mf") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isLong(a)) {
                    throw fail(a);
                }
                long al = toLong(a);
                final List<Object> l = new ArrayList<Object>();
                if (al < 4) {
                    l.add(al);
                    return l;
                }
                while (al % 2 == 0) {
                    l.add(2l);
                    al >>= 1;
                }
                for (long i = 3; i * i <= al; i += 2) {
                    while (al % i == 0) {
                        l.add(i);
                        al /= i;
                    }
                }
                if (al > 1) {
                    l.add(al);
                }
                return l;
            }
        });

        add(new Op1("mF") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isLong(a)) {
                    throw fail(a);
                }
                long al = toLong(a);
                final List<Object> l = new ArrayList<Object>();
                if (al < 4) {
                    l.add(pair(al, 1l));
                    return l;
                }
                long n = 0;
                while (al % 2 == 0) {
                    n++;
                    al >>= 1;
                }
                if (n > 0) {
                    l.add(pair(2l, n));
                }
                for (long i = 3; i * i <= al; i += 2) {
                    n = 0;
                    while (al % i == 0) {
                        n++;
                        al /= i;
                    }
                    if (n > 0) {
                        l.add(pair(i, n));
                    }
                }
                if (al > 1) {
                    l.add(pair(al, 1l));
                }
                return l;
            }
        });

        add(new Op("es") {
            @Override
            public void run(final Convex x) {
                x.push(System.currentTimeMillis());
            }
        });

        add(new Op("et") {
            @Override
            public void run(final Convex x) {
                final Calendar c = Calendar.getInstance();
                final List<Object> l = new ArrayList<Object>();
                l.add((long) c.get(Calendar.YEAR));
                l.add((long) (c.get(Calendar.MONTH) + 1));
                l.add((long) c.get(Calendar.DAY_OF_MONTH));
                l.add((long) c.get(Calendar.HOUR_OF_DAY));
                l.add((long) c.get(Calendar.MINUTE));
                l.add((long) c.get(Calendar.SECOND));
                l.add((long) c.get(Calendar.MILLISECOND));
                l.add((long) (c.get(Calendar.DAY_OF_WEEK) - 1));
                l.add((long) (c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)));
                x.push(l);
            }
        });

        add(new Op1("mo") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                if (isDouble(a)) {
                    return Math.round(toDouble(a));
                }
                return a;
            }
        });

        add(new Op2("mO") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (!isNumber(a) || !isLong(b)) {
                    throw fail(a, b);
                }
                final long bl = toLong(b);
                final double p = Math.pow(10, bl);
                final double d = Math.round(toDouble(a) * p) / p;
                if (isDouble(a) && bl > 0) {
                    return d;
                }
                return Math.round(d);
            }
        });

        add(new Op3("er") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b, final Object c) {
                if (!isList(a)) {
                    throw fail(a, b, c);
                }
                final List<?> al = toList(a);
                final List<?> bl = toList(b);
                final List<?> cl = toList(c);
                final int n = cl.size();
                final List<Object> l = new ArrayList<Object>(al.size());
                for (Object o : al) {
                    final int t = bl.indexOf(o);
                    if (t < 0) {
                        l.add(o);
                    } else if (t < n) {
                        l.add(cl.get(t));
                    } else {
                        l.add(cl.get(n - 1));
                    }
                }
                return l;
            }
        });

        add(new Op1("ms") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.sin(toDouble(a));
            }
        });

        add(new Op1("mc") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.cos(toDouble(a));
            }
        });

        add(new Op1("mt") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.tan(toDouble(a));
            }
        });

        add(new Op1("mS") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.asin(toDouble(a));
            }
        });

        add(new Op1("mC") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.acos(toDouble(a));
            }
        });

        add(new Op1("mT") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.atan(toDouble(a));
            }
        });

        add(new Op2("ma") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (!bothNumber(a, b)) {
                    throw fail(a, b);
                }
                return Math.atan2(toDouble(a), toDouble(b));
            }
        });

        add(new Op2("mh") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (!bothNumber(a, b)) {
                    throw fail(a, b);
                }
                return Math.hypot(toDouble(a), toDouble(b));
            }
        });

        add(new Op1("mq") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.sqrt(toDouble(a));
            }
        });

        add(new Op1("mQ") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                final Object a1 = isDouble(a) ? ((Op1) get('i')).calc(x, a) : a;
                if (isLong(a1)) {
                    final long al = toLong(a1);
                    if (al < 0) {
                        throw new ArithmeticException("Square root of negative number");
                    }
                    long t = (long) Math.sqrt(al);
                    if (t < 1 << 26) {
                        return t;
                    }
                    while (true) {
                        final long t1 = (t + al / t) / 2;
                        if (t1 >= t && t1 <= t + 1) {
                            break;
                        }
                        t = t1;
                    }
                    return t;

                }
                final BigInteger ab = toBigint(a1);
                if (ab.signum() == -1) {
                    throw new ArithmeticException("Square root of negative number");
                }
                final int l = ab.bitLength();
                final long tl = (long) Math.sqrt(ab.shiftRight(l / 2 * 2 - 60).longValue());
                BigInteger t = BigInteger.valueOf(tl).shiftLeft(l / 2 - 30);
                while (true) {
                    final BigInteger t1 = t.add(ab.divide(t)).shiftRight(1);
                    final BigInteger d = t1.subtract(t);
                    if (d.signum() == 0 || d.equals(BigInteger.ONE)) {
                        break;
                    }
                    t = t1;
                }
                return t;
            }
        });

        add(new Op1("me") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.exp(toDouble(a));
            }
        });

        add(new Op1("ml") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return Math.log(toDouble(a));
            }
        });

        add(new Op2("mL") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (!bothNumber(a, b)) {
                    throw fail(a, b);
                }
                final double ad = toDouble(a);
                final double bd = toDouble(b);
                return bd == 10 ? Math.log10(ad) : (Math.log(ad) / Math.log(bd));
            }
        });

        add(new Op1("m[") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return (long) Math.floor(toDouble(a));
            }
        });

        add(new Op1("m]") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return (long) Math.ceil(toDouble(a));
            }
        });

        add(new Op2("e%") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isString(a)) {
                    final String fmt = toStr(a);
                    final Object[] ba = toList(b).toArray();
                    for (int i = 0; i < ba.length; ++i) {
                        if (isString(ba[i])) {
                            ba[i] = toStr(ba[i]);
                        }
                    }
                    return strToList(String.format(fmt, ba));
                }
                if (isString(b)) {
                    return calc(x, b, a);
                }
                throw fail(a, b);
            }
        });

        add(new Op2("e*") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isNumber(a) && isList(b)) {
                    return calc(x, b, a);
                }
                if (isList(a) && isNumber(b)) {
                    final List<?> al = toList(a);
                    final int n = toInt(b);
                    final int m = al.size();
                    final long t = n * (long) m;
                    if (t == 0) {
                        return new ArrayList<Object>(0);
                    }
                    if (t > Integer.MAX_VALUE) {
                        throw new RuntimeException("Size limit exceeded");
                    }
                    if (n == 1) {
                        return al;
                    }
                    final Object[] a2 = new Object[(int) t];
                    for (int i = 0; i < m; ++i) {
                        Arrays.fill(a2, i * n, (i + 1) * n, al.get(i));
                    }
                    return new ArrayList<Object>(Arrays.asList(a2));
                }
                throw fail(a, b);
            }
        });

        add(new Op3("e\\") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b, final Object c) {
                if (isList(a)) {
                    if (!bothNumber(b, c)) {
                        throw fail(a, b, c);
                    }
                    final List<Object> al = toNewList(a);
                    final int n = al.size();
                    final int bi = adjustIndexMod(b, n);
                    final int ci = adjustIndexMod(c, n);
                    Collections.swap(al, bi, ci);
                    return al;
                }
                if (isList(b)) {
                    return calc(x, b, a, c);
                }
                if (isList(c)) {
                    return calc(x, c, a, b);
                }
                throw fail(a, b, c);
            }
        });

        add(new Op2("e=") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isList(a)) {
                    final List<?> al = toList(a);
                    int n = 0;
                    for (Object o : al) {
                        if (o.equals(b)) {
                            n++;
                        }
                    }
                    return (long) n;
                }
                if (isList(b)) {
                    return calc(x, b, a);
                }
                throw fail(a, b);
            }
        });

        add(new Op1("e!") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (isList(a)) {
                    List<Object> al = toNewList(a);
                    Collections.sort(al, COMP);
                    final List<Object> l = new ArrayList<Object>();
                    l.add(al);
                    final int n = al.size();
                    int k;
                    while (true) {
                        al = new ArrayList<Object>(al);
                        for (k = n - 2; k >= 0; --k) {
                            if (compare(al.get(k), al.get(k + 1)) < 0) {
                                break;
                            }
                        }
                        if (k < 0) {
                            break;
                        }
                        int i = n - 1;
                        final Object o = al.get(k);
                        while (compare(o, al.get(i)) >= 0) {
                            --i;
                        }
                        Collections.swap(al, i, k);
                        ++k;
                        i = n - 1;
                        while (k < i) {
                            Collections.swap(al, i, k);
                            ++k;
                            --i;
                        }
                        l.add(al);
                    }
                    return l;
                }
                if (isNumber(a)) {
                    final int n = toInt(a);
                    final List<Object> l = new ArrayList<Object>(n);
                    for (long i = 0; i < n; ++i) {
                        l.add(i);
                    }
                    return calc(x, l);
                }
                throw fail(a);
            }
        });

        add(new Op1("m!") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (isList(a)) {
                    final List<?> al = toList(a);
                    final int n = al.size();
                    final List<?> p = toList(((Op1) get("e!")).calc(x, (long) n));
                    for (Object o : p) {
                        @SuppressWarnings("unchecked")
                        final List<Object> ol = (List<Object>) toList(o);
                        for (int i = 0; i < n; ++i) {
                            final long j = (Long) ol.get(i);
                            ol.set(i, al.get((int) j));
                        }
                    }
                    return p;
                }
                if (isNumber(a)) {
                    final int n = toInt(a);
                    if (n <= 20) {
                        long t = 1;
                        for (int i = 2; i <= n; ++i) {
                            t *= i;
                        }
                        return t;
                    }
                    BigInteger t = BigInteger.ONE;
                    for (int i = 2; i <= n; ++i) {
                        t = t.multiply(BigInteger.valueOf(i));
                    }
                    return t;
                }
                throw fail(a);
            }
        });

        add(new Op1("e_") {
            private void flatten(final List<Object> l, final List<?> al) {
                for (Object o : al) {
                    if (isList(o)) {
                        flatten(l, toList(o));
                    } else {
                        l.add(o);
                    }
                }
            }

            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isList(a)) {
                    throw fail(a);
                }
                final List<Object> l = new ArrayList<Object>();
                flatten(l, toList(a));
                return l;
            }
        });

        add(new Op1("e`") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isList(a)) {
                    throw fail(a);
                }
                final List<?> al = toList(a);
                if (al.isEmpty()) {
                    return al;
                }
                final List<Object> l = new ArrayList<Object>();
                Object o = al.get(0);
                long n = 1;
                for (int i = 1; i < al.size(); ++i) {
                    final Object o1 = al.get(i);
                    if (o.equals(o1)) {
                        n++;
                    } else {
                        l.add(pair(n, o));
                        o = o1;
                        n = 1;
                    }
                }
                l.add(pair(n, o));
                return l;
            }
        });

        add(new Op1("e~") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isList(a)) {
                    throw fail(a);
                }
                final List<?> al = toList(a);
                if (al.isEmpty()) {
                    return al;
                }
                final List<Object> l = new ArrayList<Object>();
                for (Object o : al) {
                    if (!isList(o)) {
                        throw new RuntimeException("Expected an array of pairs");
                    }
                    final List<?> ol = toList(o);
                    l.addAll(Collections.nCopies(toInt(ol.get(0)), ol.get(1)));
                }
                return l;
            }
        });

        add(new Op2("ew") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b) {
                if (isList(b) && isLong(a)) {
                    return calc(x, b, a);
                }
                if (isList(a) && isLong(b)) {
                    final List<?> al = toList(a);
                    final int n = al.size();
                    final int bi = toInt(b);
                    if (bi <= 0) {
                        throw new RuntimeException("Invalid slice size");
                    }
                    if (bi > n) {
                        final List<Object> z = new ArrayList<Object>();
                        z.add(new ArrayList<Object>(al.subList(0,al.size())));
                        return z;
                    }
                    final List<Object> l = new ArrayList<Object>(n - bi + 1);
                    for (int i = 0; i < n - bi + 1; ++i) {
                        l.add(new ArrayList<Object>(al.subList(i, i + bi)));
                    }
                    return l;
                }
                throw fail(a, b);
            }
        });

        add(new Op3("e[") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b, final Object c) {
                if (!isList(a) || !isLong(b)) {
                    throw fail(a, b, c);
                }
                final List<?> al = toList(a);
                final int n = al.size();
                final int bi = toInt(b);
                if (bi < 0) {
                    throw new RuntimeException("Invalid array size");
                }
                if (bi <= n) {
                    return al;
                }
                final List<Object> l = new ArrayList<Object>(bi);
                for (int i = 0; i < bi - n; ++i) {
                    l.add(c);
                }
                l.addAll(al);
                return l;
            }
        });

        add(new Op3("e]") {
            @Override
            protected Object calc(final Convex x, final Object a, final Object b, final Object c) {
                if (!isList(a) || !isLong(b)) {
                    throw fail(a, b, c);
                }
                final List<?> al = toList(a);
                final int n = al.size();
                final int bi = toInt(b);
                if (bi < 0) {
                    throw new RuntimeException("Invalid array size");
                }
                if (bi <= n) {
                    return al;
                }
                final List<Object> l = new ArrayList<Object>(bi);
                l.addAll(al);
                for (int i = 0; i < bi - n; ++i) {
                    l.add(c);
                }
                return l;
            }
        });

        add(new Op1("ee") {
            @Override
            protected Object calc(final Convex x, final Object a) {
                if (!isList(a)) {
                    throw fail(a);
                }
                final List<?> al = toList(a);
                final List<Object> l = new ArrayList<Object>(al.size());
                for (int i = 0; i < al.size(); i++) {
                    l.add(pair((long) i, al.get(i)));
                }
                return l;
            }
        });

        //TODO: MY OPS

        add(new Op1("¡") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                final long d = toLong(a);
                long result = 1;
                for (long i = 1; i <= d; i++) {
                    result = result * i;
                }
                return result;
            }
        });

        add(new Op1("½") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return simplify(toDouble(a)/2.0);
            }
        });

        add(new Op1("²") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                if (isLong(a))
                    return adjustInt(toBigint(a).pow(2));
                if (isDouble(a))
                    return Math.pow(toDouble(a), 2);
                return toInt(a) * toInt(a);
            }
        });

        add(new Op1("³") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return simplify(Math.pow(toDouble(a), 3));
            }
        });

        add(new Op1("±") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (a instanceof Quaternion) {
                    return ((Quaternion) a).multiply(-1);
                }
                if (!isNumber(a)) {
                    throw fail(a);
                }
                if (isDouble(a)) {
                    return -1.0 * toDouble(a);
                } else if (isLong(a)) {
                    return -1 * toLong(a);
                }
                throw fail(a);
            }
        });

        add(new Op1("°") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return simplify(Math.toDegrees(toDouble(a)));
            }
        });

        add(new Op1("þ") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a) || !isString(a)) {
                    throw fail(a);
                }
                if (isNumber(a)) {
                    final double z = toDouble(a);
                    return isLong(a) ? (long) Math.toRadians(z) : Math.toRadians(z);
                } else if (isString(a)) {
                    return new Regex(toStr(a));
                }
                throw fail(a);
            }
        });

        add(new Op1("¹") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (a instanceof Quaternion) {
                    return new Quaternion(1).divide(((Quaternion) a));
                }
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return simplify(1.0/toDouble(a));
            }
        });

        add(new Op1("¬") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                return simplify(Math.floor(toDouble(a)));
            }
        });

        add(new Op1("¯") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a)) {
                    throw fail(a);
                }
                final double z = toDouble(a);
                return simplify(Math.ceil(toDouble(a)));
            }
        });

        add(new Op2("¶") {
            @Override
            protected Object calc(Convex x, Object a, Object b) {
                return pair(a, b);
            }
        });

        add(new Op2("ø") {
            @Override
            protected Object calc(Convex x, Object a, Object b) {
                if (bothString(a, b)) {
                    if (a instanceof Regex) {
                        Matcher matcher = Pattern.compile(toStr(a)).matcher(toStr(b));
                        return strToList(matcher.find() ? matcher.group(0) : "");
                    } else if (b instanceof Regex) {
                        Matcher matcher = Pattern.compile(toStr(b)).matcher(toStr(a));
                        return strToList(matcher.find() ? matcher.group(0) : "");
                    } else {
                        Matcher matcher = Pattern.compile(toStr(a)).matcher(toStr(b));
                        return strToList(matcher.find() ? matcher.group(0) : "");
                    }
                }
                throw fail(a, b);
            }
        });

        add(new Op2("ð") {
            @Override
            protected Object calc(Convex x, Object a, Object b) {
                if (bothString(a, b)) {
                    if (a instanceof Regex) {
                        Matcher matcher = Pattern.compile(toStr(a)).matcher(toStr(b));
                        int count = 0;
                        while (matcher.find())
                            count++;
                        return count;
                    } else if (b instanceof Regex) {
                        Matcher matcher = Pattern.compile(toStr(b)).matcher(toStr(a));
                        int count = 0;
                        while (matcher.find())
                            count++;
                        return count;
                    } else {
                        Matcher matcher = Pattern.compile(toStr(a)).matcher(toStr(b));
                        int count = 0;
                        while (matcher.find())
                            count++;
                        return count;
                    }
                }
                throw fail(a, b);
            }
        });

        add(new Op2("ô") {
            @Override
            protected Object calc(Convex x, Object a, Object b) {
                if (bothString(a, b)) {
                    if (a instanceof Regex) {
                        Matcher matcher = Pattern.compile(toStr(a)).matcher(toStr(b));
                        List<Object> allMatches = new ArrayList<Object>();
                        while (matcher.find()) {
                            allMatches.add(strToList(matcher.group()));
                        }
                        return allMatches;
                    } else if (b instanceof Regex) {
                        Matcher matcher = Pattern.compile(toStr(b)).matcher(toStr(a));
                        List<Object> allMatches = new ArrayList<Object>();
                        while (matcher.find()) {
                            allMatches.add(strToList(matcher.group()));
                        }
                        return allMatches;
                    } else {
                        Matcher matcher = Pattern.compile(toStr(a)).matcher(toStr(b));
                        List<Object> allMatches = new ArrayList<Object>();
                        while (matcher.find()) {
                            allMatches.add(strToList(matcher.group()));
                        }
                        return allMatches;
                    }
                }
                throw fail(a, b);
            }
        });

        add(new Op2("õ") {
            @Override
            protected Object calc(Convex x, Object a, Object b) {
                if (bothString(a, b)) {
                    if (a instanceof Regex) {
                        Matcher matcher = Pattern.compile(toStr(a)).matcher(toStr(b));
                        List<Object> allMatches = new ArrayList<Object>();
                        while (matcher.find()) {
                            allMatches.add(matcher.start());
                        }
                        return allMatches;
                    } else if (b instanceof Regex) {
                        Matcher matcher = Pattern.compile(toStr(b)).matcher(toStr(a));
                        List<Object> allMatches = new ArrayList<Object>();
                        while (matcher.find()) {
                            allMatches.add(matcher.start());
                        }
                        return allMatches;
                    } else {
                        Matcher matcher = Pattern.compile(toStr(a)).matcher(toStr(b));
                        List<Object> allMatches = new ArrayList<Object>();
                        while (matcher.find()) {
                            allMatches.add(matcher.start());
                        }
                        return allMatches;
                    }
                }
                throw fail(a, b);
            }
        });

        add(new Op3("ò") {
            @Override
            protected Object calc(Convex x, Object a, Object b, Object c) {
                if (bothString(a, b) && isString(c)) {
                    if (a instanceof Regex) {
                        return strToList(Pattern.compile(toStr(a)).matcher(toStr(b)).replaceAll(toStr(c)));
                    } else if (b instanceof Regex) {
                        return strToList(Pattern.compile(toStr(b)).matcher(toStr(a)).replaceAll(toStr(c)));
                    } else {
                        return strToList(Pattern.compile(toStr(a)).matcher(toStr(b)).replaceAll(toStr(c)));
                    }
                }
                throw fail(a, b, c);
            }
        });

        add(new Op3("ó") {
            @Override
            protected Object calc(Convex x, Object a, Object b, Object c) {
                if (bothString(a, b) && isString(c)) {
                    if (a instanceof Regex) {
                        return strToList(Pattern.compile(toStr(a)).matcher(toStr(b)).replaceFirst(toStr(c)));
                    } else if (b instanceof Regex) {
                        return strToList(Pattern.compile(toStr(b)).matcher(toStr(a)).replaceFirst(toStr(c)));
                    } else {
                        return strToList(Pattern.compile(toStr(a)).matcher(toStr(b)).replaceFirst(toStr(c)));
                    }
                }
                throw fail(a, b, c);
            }
        });

        add(new Op2("ö") {
            @Override
            protected Object calc(Convex x, Object a, Object b) {
                if (bothString(a, b)) {
                    if (a instanceof Regex) {
                        String[] s = toStr(b).split(toStr(a));
                        List<Object> z = new ArrayList<Object>();
                        for (String str : s) {
                            z.add(strToList(str));
                        }
                        return z;
                    } else if (b instanceof Regex) {
                        String[] s = toStr(a).split(toStr(b));
                        List<Object> z = new ArrayList<Object>();
                        for (String str : s) {
                            z.add(strToList(str));
                        }
                        return z;
                    } else {
                        String[] s = toStr(a).split(toStr(b));
                        List<Object> z = new ArrayList<Object>();
                        for (String str : s) {
                            z.add(strToList(str));
                        }
                        return z;
                    }
                }
                throw fail(a, b);
            }
        });

        add(new Op1("µ") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (!isNumber(a)) throw fail(a);
                long candidate, count;
                for (candidate = 2, count = 0; count < toLong(a); ++candidate) {
                    if (isPrime(candidate)) {
                        ++count;
                    }
                }
                return candidate - 1;
            }
        });

        add(new Op2("Ð") {
            @Override
            protected Object calc(Convex x, Object a, Object b) {
                BigInteger b1 = BigInteger.valueOf(toLong(a));
                BigInteger b2 = BigInteger.valueOf(toLong(b));
                BigInteger gcd = b1.gcd(b2);
                return adjustInt(gcd);
            }
        });

        add(new Op1("¤") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (isString(a)) {
                    return Quaternion.stringToQuaternion(toStr(a));
                } else if (isList(a)) {
                    List<?> z = toList(a);
                    if (bothNumber(z.get(0), z.get(1)) && bothNumber(z.get(2), z.get(3))) {
                        return new Quaternion(toDouble(z.get(0)), toDouble(z.get(1)), toDouble(z.get(2)), toDouble(z.get(3)));
                    }
                }
                throw fail(a);
            }
        });

        add(new Op1("n") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (a instanceof Quaternion) {
                    return ((Quaternion) a).norm();
                }
                throw fail(a);
            }
        });

        add(new Op1("´") {
            @Override
            protected Object calc(Convex x, Object a) {
                if (isNumber(a)) {
                    final int n = toInt(a);
                    final List<Object> l = new ArrayList<Object>(n);
                    for (long i = 1; i <= n; ++i) {
                        l.add(i);
                    }
                    return l;
                }
                throw fail(a);
            }
        });

        add(new Op("¢") {
            @Override
            public void run(Convex x) {
                x.push((Math.sqrt(5)+1.0)/2.0);
            }
        });
    }

    private static boolean isPrime(long n) {
        for (long i = 2; i < n; ++i) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }

    public static void main(final String... args) {
        System.out.println("Defined simple operators:");
        for (int i = 0; i < TABLE.length; ++i) {
            if (TABLE[i] != null) {
                System.out.print((char) i);
            }
        }
        System.out.println();
        final List<String> l = new ArrayList<String>(MAP.keySet());
        Collections.sort(l);
        for (String s : l) {
            System.out.print(s + ' ');
        }
        System.out.println();
    }
}