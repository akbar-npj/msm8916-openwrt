package com.google.common.base;

/* JADX INFO: loaded from: classes.dex */
public final class Ascii {
    private Ascii() {
    }

    public static String toLowerCase(String string) {
        int length = string.length();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(toLowerCase(string.charAt(i)));
        }
        return builder.toString();
    }

    public static char toLowerCase(char c) {
        return isUpperCase(c) ? (char) (c ^ ' ') : c;
    }

    public static boolean isUpperCase(char c) {
        return c >= 'A' && c <= 'Z';
    }
}
