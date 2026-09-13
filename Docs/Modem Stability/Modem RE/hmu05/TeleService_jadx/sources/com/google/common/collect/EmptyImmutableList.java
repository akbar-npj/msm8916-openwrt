package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

/* JADX INFO: loaded from: classes.dex */
final class EmptyImmutableList extends ImmutableList<Object> {
    private static final long serialVersionUID = 0;
    static final EmptyImmutableList INSTANCE = new EmptyImmutableList();
    static final UnmodifiableListIterator<Object> ITERATOR = new UnmodifiableListIterator<Object>() { // from class: com.google.common.collect.EmptyImmutableList.1
        @Override // java.util.Iterator, java.util.ListIterator
        public boolean hasNext() {
            return false;
        }

        @Override // java.util.ListIterator
        public boolean hasPrevious() {
            return false;
        }

        @Override // java.util.Iterator, java.util.ListIterator
        public Object next() {
            throw new NoSuchElementException();
        }

        @Override // java.util.ListIterator
        public int nextIndex() {
            return 0;
        }

        @Override // java.util.ListIterator
        public Object previous() {
            throw new NoSuchElementException();
        }

        @Override // java.util.ListIterator
        public int previousIndex() {
            return -1;
        }
    };
    private static final Object[] EMPTY_ARRAY = new Object[0];

    private EmptyImmutableList() {
    }

    @Override // java.util.Collection, java.util.List
    public int size() {
        return 0;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean isEmpty() {
        return true;
    }

    @Override // com.google.common.collect.ImmutableCollection
    boolean isPartialView() {
        return false;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object target) {
        return false;
    }

    @Override // com.google.common.collect.ImmutableList, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<Object> iterator() {
        return Iterators.emptyIterator();
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

    @Override // java.util.List
    public Object get(int index) {
        Preconditions.checkElementIndex(index, 0);
        throw new AssertionError("unreachable");
    }

    @Override // java.util.List
    public int indexOf(Object target) {
        return -1;
    }

    @Override // java.util.List
    public int lastIndexOf(Object target) {
        return -1;
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public ImmutableList<Object> subList(int fromIndex, int toIndex) {
        Preconditions.checkPositionIndexes(fromIndex, toIndex, 0);
        return this;
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public UnmodifiableListIterator<Object> listIterator() {
        return ITERATOR;
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public UnmodifiableListIterator<Object> listIterator(int start) {
        Preconditions.checkPositionIndex(start, 0);
        return ITERATOR;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean containsAll(Collection<?> targets) {
        return targets.isEmpty();
    }

    @Override // com.google.common.collect.ImmutableList, java.util.Collection, java.util.List
    public boolean equals(Object object) {
        if (!(object instanceof List)) {
            return false;
        }
        List<?> that = (List) object;
        return that.isEmpty();
    }

    @Override // com.google.common.collect.ImmutableList, java.util.Collection, java.util.List
    public int hashCode() {
        return 1;
    }

    @Override // com.google.common.collect.ImmutableCollection
    public String toString() {
        return "[]";
    }

    Object readResolve() {
        return INSTANCE;
    }
}
