package com.google.common.base;

/* JADX INFO: loaded from: classes.dex */
final class Platform {
    private static final ThreadLocal<char[]> DEST_TL = new ThreadLocal<char[]>() { // from class: com.google.common.base.Platform.1
        /* JADX INFO: Access modifiers changed from: protected */
        @Override // java.lang.ThreadLocal
        public char[] initialValue() {
            return new char[1024];
        }
    };

    private Platform() {
    }

    static long systemNanoTime() {
        return System.nanoTime();
    }
}
