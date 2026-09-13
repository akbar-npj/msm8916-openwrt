package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
final class RegularImmutableSortedSet<E> extends ImmutableSortedSet<E> {
    private final transient ImmutableList<E> elements;

    RegularImmutableSortedSet(ImmutableList<E> elements, Comparator<? super E> comparator) {
        super(comparator);
        this.elements = elements;
        Preconditions.checkArgument(!elements.isEmpty());
    }

    @Override // com.google.common.collect.ImmutableSortedSet, com.google.common.collect.ImmutableSet, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<E> iterator() {
        return this.elements.iterator();
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean isEmpty() {
        return false;
    }

    @Override // java.util.Collection, java.util.Set
    public int size() {
        return this.elements.size();
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object o) {
        if (o == null) {
            return false;
        }
        try {
            return binarySearch(o) >= 0;
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean containsAll(Collection<?> targets) {
        if (!SortedIterables.hasSameComparator(comparator(), targets) || targets.size() <= 1) {
            return super.containsAll(targets);
        }
        Iterator<E> thisIterator = iterator();
        Iterator<?> thatIterator = targets.iterator();
        Object target = thatIterator.next();
        while (thisIterator.hasNext()) {
            try {
                int cmp = unsafeCompare(thisIterator.next(), target);
                if (cmp == 0) {
                    if (!thatIterator.hasNext()) {
                        return true;
                    }
                    target = thatIterator.next();
                } else if (cmp > 0) {
                    return false;
                }
            } catch (ClassCastException e) {
                return false;
            } catch (NullPointerException e2) {
                return false;
            }
        }
        return false;
    }

    private int binarySearch(Object key) {
        return Collections.binarySearch(this.elements, key, this.comparator);
    }

    @Override // com.google.common.collect.ImmutableCollection
    boolean isPartialView() {
        return this.elements.isPartialView();
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public Object[] toArray() {
        return this.elements.toArray();
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public <T> T[] toArray(T[] tArr) {
        return (T[]) this.elements.toArray(tArr);
    }

    @Override // com.google.common.collect.ImmutableSet, java.util.Collection, java.util.Set
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof Set)) {
            return false;
        }
        Set<?> that = (Set) object;
        if (size() != that.size()) {
            return false;
        }
        if (SortedIterables.hasSameComparator(this.comparator, that)) {
            Iterator<E> it = that.iterator();
            try {
                Iterator<E> iterator = iterator();
                while (iterator.hasNext()) {
                    Object element = iterator.next();
                    Object otherElement = it.next();
                    if (otherElement == null || unsafeCompare(element, otherElement) != 0) {
                        return false;
                    }
                }
                return true;
            } catch (ClassCastException e) {
                return false;
            } catch (NoSuchElementException e2) {
                return false;
            }
        }
        return containsAll(that);
    }

    @Override // java.util.SortedSet
    public E first() {
        return this.elements.get(0);
    }

    @Override // java.util.SortedSet
    public E last() {
        return this.elements.get(size() - 1);
    }

    @Override // com.google.common.collect.ImmutableSortedSet
    ImmutableSortedSet<E> headSetImpl(E toElement, boolean inclusive) {
        int index;
        if (inclusive) {
            index = SortedLists.binarySearch(this.elements, Preconditions.checkNotNull(toElement), comparator(), SortedLists.KeyPresentBehavior.FIRST_AFTER, SortedLists.KeyAbsentBehavior.NEXT_HIGHER);
        } else {
            index = SortedLists.binarySearch(this.elements, Preconditions.checkNotNull(toElement), comparator(), SortedLists.KeyPresentBehavior.FIRST_PRESENT, SortedLists.KeyAbsentBehavior.NEXT_HIGHER);
        }
        return createSubset(0, index);
    }

    @Override // com.google.common.collect.ImmutableSortedSet
    ImmutableSortedSet<E> subSetImpl(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        return tailSetImpl(fromElement, fromInclusive).headSetImpl(toElement, toInclusive);
    }

    @Override // com.google.common.collect.ImmutableSortedSet
    ImmutableSortedSet<E> tailSetImpl(E fromElement, boolean inclusive) {
        int index;
        if (inclusive) {
            index = SortedLists.binarySearch(this.elements, Preconditions.checkNotNull(fromElement), comparator(), SortedLists.KeyPresentBehavior.FIRST_PRESENT, SortedLists.KeyAbsentBehavior.NEXT_HIGHER);
        } else {
            index = SortedLists.binarySearch(this.elements, Preconditions.checkNotNull(fromElement), comparator(), SortedLists.KeyPresentBehavior.FIRST_AFTER, SortedLists.KeyAbsentBehavior.NEXT_HIGHER);
        }
        return createSubset(index, size());
    }

    private ImmutableSortedSet<E> createSubset(int newFromIndex, int newToIndex) {
        if (newFromIndex != 0 || newToIndex != size()) {
            if (newFromIndex < newToIndex) {
                return new RegularImmutableSortedSet<>(this.elements.subList(newFromIndex, newToIndex), this.comparator);
            }
            return emptySet(this.comparator);
        }
        return this;
    }

    @Override // com.google.common.collect.ImmutableSortedSet
    int indexOf(Object target) {
        if (target == null) {
            return -1;
        }
        try {
            int position = SortedLists.binarySearch(this.elements, target, comparator(), SortedLists.KeyPresentBehavior.ANY_PRESENT, SortedLists.KeyAbsentBehavior.INVERTED_INSERTION_INDEX);
            if (position < 0 || !this.elements.get(position).equals(target)) {
                position = -1;
            }
            return position;
        } catch (ClassCastException e) {
            return -1;
        }
    }

    @Override // com.google.common.collect.ImmutableCollection
    ImmutableList<E> createAsList() {
        return new ImmutableSortedAsList(this, this.elements);
    }
}
