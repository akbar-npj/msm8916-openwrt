package com.android.internal.telephony.cat;

/* JADX INFO: loaded from: classes.dex */
public enum TextAlignment {
    LEFT(0),
    CENTER(1),
    RIGHT(2),
    DEFAULT(3);

    private int mValue;

    TextAlignment(int value) {
        this.mValue = value;
    }

    public static TextAlignment fromInt(int value) {
        TextAlignment[] arr$ = values();
        for (TextAlignment e : arr$) {
            if (e.mValue == value) {
                return e;
            }
        }
        return null;
    }
}
