package com.google.common.collect;

import java.util.Collection;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
final class EmptyImmutableSet extends ImmutableSet<Object> {
    private static final long serialVersionUID = 0;
    static final EmptyImmutableSet INSTANCE = new EmptyImmutableSet();
    private static final Object[] EMPTY_ARRAY = new Object[0];

    private EmptyImmutableSet() {
    }

    @Override // java.util.Collection, java.util.Set
    public int size() {
        return 0;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean isEmpty() {
        return true;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object target) {
        return false;
    }

    @Override // com.google.common.collect.ImmutableSet, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<Object> iterator() {
        return Iterators.emptyIterator();
    }

    @Override // com.google.common.collect.ImmutableCollection
    boolean isPartialView() {
        return false;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public Object[] toArray() {
        return EMPTY_ARRAY;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public <T> T[] toArray(T[] a) {
        if (a.length > 0) {
            a[0] = null;
        }
        return a;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean containsAll(Collection<?> targets) {
        return targets.isEmpty();
    }

    @Override // com.google.common.collect.ImmutableSet, java.util.Collection, java.util.Set
    public boolean equals(Object object) {
        if (!(object instanceof Set)) {
            return false;
        }
        Set<?> that = (Set) object;
        return that.isEmpty();
    }

    @Override // com.google.common.collect.ImmutableSet, java.util.Collection, java.util.Set
    public final int hashCode() {
        return 0;
    }

    @Override // com.google.common.collect.ImmutableSet
    boolean isHashCodeFast() {
        return true;
    }

    @Override // com.google.common.collect.ImmutableCollection
    public String toString() {
        return "[]";
    }

    Object readResolve() {
        return INSTANCE;
    }
}
