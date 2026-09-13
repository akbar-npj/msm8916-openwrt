package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.Comparator;

/* JADX INFO: loaded from: classes.dex */
final class ImmutableSortedAsList<E> extends ImmutableList<E> implements SortedIterable<E> {
    private final transient ImmutableList<E> backingList;
    private final transient ImmutableSortedSet<E> backingSet;

    ImmutableSortedAsList(ImmutableSortedSet<E> backingSet, ImmutableList<E> backingList) {
        this.backingSet = backingSet;
        this.backingList = backingList;
    }

    @Override // com.google.common.collect.SortedIterable
    public Comparator<? super E> comparator() {
        return this.backingSet.comparator();
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object target) {
        return this.backingSet.indexOf(target) >= 0;
    }

    @Override // java.util.List
    public int indexOf(Object target) {
        return this.backingSet.indexOf(target);
    }

    @Override // java.util.List
    public int lastIndexOf(Object target) {
        return this.backingSet.indexOf(target);
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public ImmutableList<E> subList(int fromIndex, int toIndex) {
        Preconditions.checkPositionIndexes(fromIndex, toIndex, size());
        return fromIndex == toIndex ? ImmutableList.of() : new RegularImmutableSortedSet(this.backingList.subList(fromIndex, toIndex), this.backingSet.comparator()).asList();
    }

    @Override // com.google.common.collect.ImmutableList, com.google.common.collect.ImmutableCollection
    Object writeReplace() {
        return new ImmutableAsList.SerializedForm(this.backingSet);
    }

    @Override // com.google.common.collect.ImmutableList, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<E> iterator() {
        return this.backingList.iterator();
    }

    @Override // java.util.List
    public E get(int index) {
        return this.backingList.get(index);
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public UnmodifiableListIterator<E> listIterator() {
        return this.backingList.listIterator();
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public UnmodifiableListIterator<E> listIterator(int index) {
        return this.backingList.listIterator(index);
    }

    @Override // java.util.Collection, java.util.List
    public int size() {
        return this.backingList.size();
    }

    @Override // com.google.common.collect.ImmutableList, java.util.Collection, java.util.List
    public boolean equals(Object obj) {
        return this.backingList.equals(obj);
    }

    @Override // com.google.common.collect.ImmutableList, java.util.Collection, java.util.List
    public int hashCode() {
        return this.backingList.hashCode();
    }

    @Override // com.google.common.collect.ImmutableCollection
    boolean isPartialView() {
        return this.backingList.isPartialView();
    }
}
