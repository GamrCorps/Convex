package src.gamrcorps.convex;

public class Quaternion {
    public double a, b, c, d;

    public Quaternion(double a) {
        this(a, 0, 0, 0);
    }

    public Quaternion(double a, double b) {
        this(a, b, 0, 0);
    }

    public Quaternion(double a, double b, double c) {
        this(a, b, c, 0);
    }

    public Quaternion(double a, double b, double c, double d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public Quaternion add(double a, double b, double c, double d) {
        return add(new Quaternion(a, b, c, d));
    }

    public Quaternion add(Quaternion q) {
        return new Quaternion(a + q.a, b + q.b, c + q.c, d + q.d);
    }

    public Quaternion subtract(double a, double b, double c, double d) {
        return subtract(new Quaternion(a, b, c, d));
    }

    public Quaternion subtract(Quaternion q) {
        return new Quaternion(a - q.a, b - q.b, c - q.c, d - q.d);
    }

    public Quaternion multiply(double scalar) {
        return new Quaternion(scalar * a, scalar * b, scalar * c, scalar * d);
    }

    public Quaternion multiply(double a, double b, double c, double d) {
        return multiply(new Quaternion(a, b, c, d));
    }

    public Quaternion multiply(Quaternion q) {
        return new Quaternion(
                a * q.a - b * q.b - c * q.c - d * q.d,
                b * q.a + a * q.b + c * q.d - d * q.c,
                a * q.c - b * q.d + c * q.a + d * q.b,
                a * q.d + b * q.c - c * q.b + d * q.a
        );
    }

    public static double norm(Quaternion q) {
        return Math.pow(q.a, 2) + Math.pow(q.b, 2) + Math.pow(q.c, 2) + Math.pow(q.d, 2);
    }

    public double norm() {
        return Math.pow(a, 2) + Math.pow(b, 2) + Math.pow(c, 2) + Math.pow(d, 2);
    }

    public static Quaternion conjugate(Quaternion q) {
        return new Quaternion(q.a, -q.b, -q.c, -q.d);
    }

    public Quaternion conjugate() {
        return new Quaternion(a, -b, -c, -d);
    }

    public static double magnitude(double a, double b, double c, double d) {
        return magnitude(new Quaternion(a, b, c, d));
    }

    public static double magnitude(Quaternion q) {
        return Math.sqrt(Math.pow(q.a, 2) + Math.pow(q.b, 2) + Math.pow(q.c, 2) + Math.pow(q.d, 2));
    }

    public double magnitude() {
        return Math.sqrt(Math.pow(a, 2) + Math.pow(b, 2) + Math.pow(c, 2) + Math.pow(d, 2));
    }

    public Quaternion divide(double scalar) {
        return new Quaternion(a / scalar, b / scalar, c / scalar, d / scalar);
    }

    public Quaternion divide(double a, double b, double c, double d) {
        return divide(new Quaternion(a, b, c, d));
    }

    public Quaternion divide(Quaternion q) {
        return multiply(conjugate(q)).divide(q.norm());
    }

    public static Quaternion stringToQuaternion (String input) {
        String output = input.replaceAll("\\b[ijk]", "1$0");
        output = output.replaceAll("^(?!.*\\d([+-]|$))", "0+");
        output = output.replaceAll("^(?!.*i)", "+0i+");
        output = output.replaceAll("^(?!.*j)", "0j+");
        output = output.replaceAll("^(?!.*k)", "0k+");
        double i = new Double (output.replaceAll("(?![+-]?\\d\\.?\\d*i).",""));
        double j = new Double (output.replaceAll("(?![+-]?\\d\\.?\\d*j).",""));
        double k = new Double (output.replaceAll("(?![+-]?\\d\\.?\\d*k).",""));
        double s = new Double (output.replaceAll("(?!\\W?\\d+\\b).",""));
        return new Quaternion(s, i, j, k);
    }

    @Override
    public String toString() {
        return "" + Conv.simplify(a) + (b < 0 ? "" : "+") + Conv.simplify(b) + "i" + (c < 0 ? "" : "+") + Conv.simplify(c) + "j" + (d < 0 ? "" : "+") + Conv.simplify(d) + "k";
    }
}