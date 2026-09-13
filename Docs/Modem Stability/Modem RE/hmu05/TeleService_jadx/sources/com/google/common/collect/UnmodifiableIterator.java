package com.google.common.collect;

import java.util.Iterator;

/* JADX INFO: loaded from: classes.dex */
public abstract class UnmodifiableIterator<E> implements Iterator<E> {
    protected UnmodifiableIterator() {
    }

    @Override // java.util.Iterator
    public final void remove() {
        throw new UnsupportedOperationException();
    }
}
