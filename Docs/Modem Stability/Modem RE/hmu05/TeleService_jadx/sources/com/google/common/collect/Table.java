package com.google.common.collect;

import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public interface Table<R, C, V> {

    public interface Cell<R, C, V> {
        V getValue();
    }

    Set<Cell<R, C, V>> cellSet();
}
