package com.google.common.collect;

/* JADX INFO: loaded from: classes.dex */
final class Hashing {
    private Hashing() {
    }

    static int smear(int hashCode) {
        int hashCode2 = hashCode ^ ((hashCode >>> 20) ^ (hashCode >>> 12));
        return ((hashCode2 >>> 7) ^ hashCode2) ^ (hashCode2 >>> 4);
    }
}
