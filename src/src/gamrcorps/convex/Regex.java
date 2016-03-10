package src.gamrcorps.convex;

import java.util.List;

public class Regex {
    final String regex;

    public Regex(String str) {
        regex = str;
    }

    public Regex(List<Object> str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.size(); i++) {
            sb.append((char) str.get(i));
        }
        regex = sb.toString();
    }

    @Override
    public String toString() {
        return regex;
    }
}
