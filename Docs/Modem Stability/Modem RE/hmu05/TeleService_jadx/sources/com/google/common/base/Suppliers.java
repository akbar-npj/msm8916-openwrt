package com.google.common.base;

import java.io.Serializable;

/* JADX INFO: loaded from: classes.dex */
public final class Suppliers {

    static class ExpiringMemoizingSupplier<T> implements Supplier<T>, Serializable {
        private static final long serialVersionUID = 0;
        final Supplier<T> delegate;
        final long durationNanos;
    }

    static class MemoizingSupplier<T> implements Supplier<T>, Serializable {
        private static final long serialVersionUID = 0;
        final Supplier<T> delegate;
    }

    private Suppliers() {
    }
}
