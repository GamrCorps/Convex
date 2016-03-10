package src.gamrcorps.convex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Type checking and conversion utilities
 * 
 * @author aditsu
 */
public class Conv {
	public static boolean isList(final Object o) {
		return o instanceof ArrayList;
	}
	
	public static boolean anyList(final Object a, final Object b) {
		return isList(a) || isList(b);
	}
	
	public static boolean bothList(final Object a, final Object b) {
		return isList(a) && isList(b);
	}
	
	public static List<?> toList(final Object o) {
		if (isList(o)) {
			return (List<?>) o; 
		}
		final List<Object> l = new ArrayList<Object>();
		l.add(o);
		return l;
	}
	
	public static List<Object> toNewList(final Object o) {
		if (isList(o)) {
			return new ArrayList<Object>((List<?>) o); 
		}
		final List<Object> l = new ArrayList<Object>();
		l.add(o);
		return l;
	}
	
	public static boolean isNumber(final Object o) {
		return o instanceof Number;
	}
	
	public static boolean anyNumber(final Object a, final Object b) {
		return isNumber(a) || isNumber(b);
	}
	
	public static boolean bothNumber(final Object a, final Object b) {
		return isNumber(a) && isNumber(b);
	}
	
	public static Number toNumber(final Object o) {
		return (Number) o;
	}
	
	public static boolean isLong(final Object o) {
		return o instanceof Long;
	}
	
	public static boolean anyLong(final Object a, final Object b) {
		return isLong(a) || isLong(b);
	}
	
	public static boolean bothLong(final Object a, final Object b) {
		return isLong(a) && isLong(b);
	}
	
	private static int error(final Object o, final String type) {
		throw new RuntimeException("Can't convert " + o.getClass().getSimpleName() + " to " + type);
	}
	
	public static long toLong(final Object o) {
		return isLong(o) ? (Long) o : isNumber(o) ? ((Number) o).longValue() : isString(o) ? Long.parseLong(listToStr(o))
				: isChar(o) ? (long) (Character) o : error(o, "long");
	}
	
	public static int toInt(final Object o) {
		return (int) toLong(o);
	}
	
	public static boolean isDouble(final Object o) {
		return o instanceof Double;
	}
	
	public static boolean anyDouble(final Object a, final Object b) {
		return isDouble(a) || isDouble(b);
	}
	
	public static boolean bothDouble(final Object a, final Object b) {
		return isDouble(a) && isDouble(b);
	}
	
	public static double toDouble(final Object o) {
		return isNumber(o) ? ((Number) o).doubleValue() : isString(o) ? Double.parseDouble(listToStr(o))
				: isChar(o) ? (double) (Character) o : error(o, "double");
	}
	
	public static boolean isBigint(final Object o) {
		return o instanceof BigInteger;
	}
	
	public static boolean anyBigint(final Object a, final Object b) {
		return isBigint(a) || isBigint(b);
	}
	
	public static boolean bothBigint(final Object a, final Object b) {
		return isBigint(a) && isBigint(b);
	}
	
	public static BigInteger toBigint(final Object o) {
		return isBigint(o) ? (BigInteger) o : isString(o) ? new BigInteger(listToStr(o)) : BigInteger.valueOf(toLong(o));
	}
	
	public static boolean isChar(final Object o) {
		return o instanceof Character;
	}
	
	public static boolean anyChar(final Object a, final Object b) {
		return isChar(a) || isChar(b);
	}
	
	public static boolean bothChar(final Object a, final Object b) {
		return isChar(a) && isChar(b);
	}
	
	public static char toChar(final Object o) {
		return isChar(o) ? (Character) o : isString(o) ? (Character) toList(o).get(0) : (char) toLong(o);
	}
	
	public static boolean isString(final Object o) {
		if (!isList(o)) {
			return false;
		}
		for (Object x : toList(o)) {
			if (!isChar(x)) {
				return false;
			}
		}
		return true;
	}
	
	public static String listToStr(final Object o) {
		final StringBuilder sb = new StringBuilder();
		for (Object x : toList(o)) {
			sb.append(x);
		}
		return sb.toString();
	}
	
	public static String toStr(final Object o) {
		if (isList(o)) {
			final StringBuilder sb = new StringBuilder();
			for (Object x : toList(o)) {
				sb.append(toStr(x));
			}
			return sb.toString();
		}
		return o.toString();
	}
	
	public static String repr(final Object o) {
		if (isDouble(o)) {
			final double d = toDouble(o);
			if (d == Double.POSITIVE_INFINITY) {
				return "1d0/";
			}
			if (d == Double.NEGATIVE_INFINITY) {
				return "-1d0/";
			}
			if (d != d) {
				return "0d0/";
			}
			return Double.toString(d).toLowerCase();
		}
		if (isNumber(o) || isBlock(o)) {
			return o.toString();
		}
		if (isChar(o)) {
			return new String(new char[]{'\'', toChar(o)});
		}
		if (isString(o)) {
			final String s = listToStr(o);
			final int n = s.length();
			final StringBuilder sb = new StringBuilder(n * 4 / 3 + 2);
			sb.append('"');
			for (int i = 0; i < n; ++i) {
				final char c = s.charAt(i);
				if (c == '"') {
					sb.append("\\\"");
				}
				else if (c == '\\') {
					if (i == n - 1) {
						sb.append("\\\\");
					}
					else {
						final char c1 = s.charAt(i + 1);
						if (c1 == '"' || c1 == '\\') {
							sb.append("\\\\");
						}
						else {
							sb.append('\\');
						}
					}
				}
				else {
					sb.append(c);
				}
			}
			return sb.append('"').toString();
		}
		if (isList(o)) {
			final StringBuilder sb = new StringBuilder("[");
			for (Object x : toList(o)) {
				sb.append(repr(x)).append(' ');
			}
			sb.setCharAt(sb.length() - 1, ']');
			return sb.toString();
		}
        if (isRegex(o)) {
            return o.toString();
        }
		throw new RuntimeException(o.getClass().getSimpleName() + " not handled");
	}
	
	public static List<Object> strToList(final String s) {
		final int n = s.length();
		final List<Object> l = new ArrayList<Object>(n);
		for (int i = 0; i < n; ++i) {
			l.add(s.charAt(i));
		}
		return l;
	}
	
	public static boolean isBlock(final Object o) {
		return o instanceof Block;
	}
	
	public static boolean anyBlock(final Object a, final Object b) {
		return isBlock(a) || isBlock(b);
	}
	
	public static boolean bothBlock(final Object a, final Object b) {
		return isBlock(a) && isBlock(b);
	}
	
	public static Block toBlock(final Object o) {
		return (Block) o;
	}
	
	public static boolean toBool(final Object o) {
		if (isBigint(o)) {
			return !toBigint(o).equals(BigInteger.ZERO);
		}
		if (isNumber(o)) {
			return toDouble(o) != 0;
		}
		if (isList(o)) {
			return !toList(o).isEmpty();
		}
		if (isChar(o)) {
			return toChar(o) != 0;
		}
		throw new RuntimeException(o.getClass().getSimpleName());
	}
	
	public static long boolVal(final boolean b) {
		return b ? 1l : 0l;
	}

	public static boolean isRegex(final Object o) {
        return o instanceof Regex;
    }
}
