package src.gamrcorps.convex;

import java.util.ArrayList;
import java.util.List;

public class StringCompressor {
    public static String characterMap =
            "\0ABCDEFGHIJKLMNOPQRSTUVWXYZ[],\" " +
            "abcdefghijklmnopqrstuvwxyz!.(){}" +
            "0123456789#$%'*+-/<=>@\\+^\n|~:;?";

    public static String compress (String str) {
        char[] raw = str.toCharArray();
        List<String> binaryStrings = new ArrayList<String>();
        int blockSize = 0;
        for (char c : raw) {
            if (characterMap.indexOf(c) > blockSize) {
                blockSize = characterMap.indexOf(c);
            }
        }
        blockSize = 5 + (int) Math.floor(blockSize / 32.0);
        for (char c : raw) {
            binaryStrings.add(Integer.toBinaryString((int)Math.pow(2, blockSize) + characterMap.indexOf(c)).substring(1));
        }
        binaryStrings.add(0, Integer.toBinaryString(blockSize));
        String concat = "";
        for (String s : binaryStrings) {
            concat+=s;
        }
        int m = concat.length();
        final List<String> l = new ArrayList<String>();
        for (int i = 0; i < m; i += 8) {
            l.add(concat.substring(i, Math.min(i + 8, m)));
        }
        if (l.get(l.size()-1).length() != 8) l.set(l.size()-1,(l.get(l.size()-1)+"00000000").substring(0,8));
        String result = "";
        for (String s : l) {
            result += (char) Integer.parseInt(s, 2);
        }
        return result;
    }

    public static String decompress (String str) {
        String binaryRep = "";
        for (char c : str.toCharArray()) {
            binaryRep += Integer.toBinaryString(256 + c).substring(1);
        }
        int blockSize = Integer.parseInt(binaryRep.substring(0,3),2);
        binaryRep = binaryRep.substring(3);
        List<String> blocks = new ArrayList<String>();
        int m = binaryRep.length();
        for (int i = 0; i < m; i += blockSize) {
            if (binaryRep.substring(i, Math.min(i + blockSize, m)).contains("1"))
            blocks.add(binaryRep.substring(i, Math.min(i + blockSize, m)));
        }
        String result = "";
        for (String s: blocks) {
            result += characterMap.charAt(Integer.parseInt(s, 2));
        }
        return result;
    }

    public static void main (String[] args) {
        String s;
        System.out.println(s=compress("HELLO, WORLD"));
        System.out.println(decompress(s));
    }
}
