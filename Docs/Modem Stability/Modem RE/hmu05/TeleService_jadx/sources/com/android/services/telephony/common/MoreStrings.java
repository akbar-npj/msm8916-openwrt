package com.android.services.telephony.common;

/* JADX INFO: loaded from: classes.dex */
public class MoreStrings {
    public static String toSafeString(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '-' || c == '@' || c == '.') {
                builder.append(c);
            } else {
                builder.append('x');
            }
        }
        return builder.toString();
    }
}
