package com.google.common.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
class EmptyImmutableSortedSet<E> extends ImmutableSortedSet<E> {
    private static final Object[] EMPTY_ARRAY = new Object[0];

    EmptyImmutableSortedSet(Comparator<? super E> comparator) {
        super(comparator);
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

    @Override // com.google.common.collect.ImmutableSortedSet, com.google.common.collect.ImmutableSet, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<E> iterator() {
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
    public int hashCode() {
        return 0;
    }

    @Override // com.google.common.collect.ImmutableCollection
    public String toString() {
        return "[]";
    }

    @Override // java.util.SortedSet
    public E first() {
        throw new NoSuchElementException();
    }

    @Override // java.util.SortedSet
    public E last() {
        throw new NoSuchElementException();
    }

    @Override // com.google.common.collect.ImmutableSortedSet
    ImmutableSortedSet<E> headSetImpl(E toElement, boolean inclusive) {
        return this;
    }

    @Override // com.google.common.collect.ImmutableSortedSet
    ImmutableSortedSet<E> subSetImpl(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        return this;
    }

    @Override // com.google.common.collect.ImmutableSortedSet
    ImmutableSortedSet<E> tailSetImpl(E fromElement, boolean inclusive) {
        return this;
    }

    @Override // com.google.common.collect.ImmutableSortedSet
    int indexOf(Object target) {
        return -1;
    }
}
