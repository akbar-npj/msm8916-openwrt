package com.google.common.base;

/* JADX INFO: loaded from: classes.dex */
public abstract class Equivalence<T> {
    protected abstract boolean doEquivalent(T t, T t2);

    protected abstract int doHash(T t);

    protected Equivalence() {
    }

    public final boolean equivalent(T a, T b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return doEquivalent(a, b);
    }

    public final int hash(T t) {
        if (t == null) {
            return 0;
        }
        return doHash(t);
    }
}
