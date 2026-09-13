package com.android.services.telephony.common;

/* JADX INFO: loaded from: classes.dex */
public class AudioMode {
    public static int EARPIECE = 1;
    public static int BLUETOOTH = 2;
    public static int WIRED_HEADSET = 4;
    public static int SPEAKER = 8;
    public static int WIRED_OR_EARPIECE = EARPIECE | WIRED_HEADSET;
    public static int ALL_MODES = ((EARPIECE | BLUETOOTH) | WIRED_HEADSET) | SPEAKER;

    public static String toString(int mode) {
        if (((ALL_MODES ^ (-1)) & mode) != 0) {
            return "UNKNOWN";
        }
        StringBuffer buffer = new StringBuffer();
        if ((EARPIECE & mode) == EARPIECE) {
            listAppend(buffer, "EARPIECE");
        }
        if ((BLUETOOTH & mode) == BLUETOOTH) {
            listAppend(buffer, "BLUETOOTH");
        }
        if ((WIRED_HEADSET & mode) == WIRED_HEADSET) {
            listAppend(buffer, "WIRED_HEADSET");
        }
        if ((SPEAKER & mode) == SPEAKER) {
            listAppend(buffer, "SPEAKER");
        }
        return buffer.toString();
    }

    private static void listAppend(StringBuffer buffer, String str) {
        if (buffer.length() > 0) {
            buffer.append(", ");
        }
        buffer.append(str);
    }
}
