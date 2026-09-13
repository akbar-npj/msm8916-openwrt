package com.google.common.collect;

import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public abstract class ImmutableTable<R, C, V> implements Table<R, C, V> {
    @Override // com.google.common.collect.Table
    public abstract ImmutableSet<Table.Cell<R, C, V>> cellSet();

    public abstract ImmutableMap<R, Map<C, V>> rowMap();

    ImmutableTable() {
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof Table) {
            Table<?, ?, ?> that = (Table) obj;
            return cellSet().equals(that.cellSet());
        }
        return false;
    }

    public int hashCode() {
        return cellSet().hashCode();
    }

    public String toString() {
        return rowMap().toString();
    }
}
