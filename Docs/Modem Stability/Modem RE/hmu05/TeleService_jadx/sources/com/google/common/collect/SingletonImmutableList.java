package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.NoSuchElementException;

/* JADX INFO: loaded from: classes.dex */
final class SingletonImmutableList<E> extends ImmutableList<E> {
    final transient E element;

    SingletonImmutableList(E e) {
        this.element = (E) Preconditions.checkNotNull(e);
    }

    @Override // java.util.List
    public E get(int index) {
        Preconditions.checkElementIndex(index, 1);
        return this.element;
    }

    @Override // java.util.List
    public int indexOf(Object object) {
        return this.element.equals(object) ? 0 : -1;
    }

    @Override // com.google.common.collect.ImmutableList, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<E> iterator() {
        return Iterators.singletonIterator(this.element);
    }

    @Override // java.util.List
    public int lastIndexOf(Object object) {
        return this.element.equals(object) ? 0 : -1;
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public UnmodifiableListIterator<E> listIterator(final int start) {
        Preconditions.checkPositionIndex(start, 1);
        return new UnmodifiableListIterator<E>() { // from class: com.google.common.collect.SingletonImmutableList.1
            boolean hasNext;

            {
                this.hasNext = start == 0;
            }

            @Override // java.util.Iterator, java.util.ListIterator
            public boolean hasNext() {
                return this.hasNext;
            }

            @Override // java.util.ListIterator
            public boolean hasPrevious() {
                return !this.hasNext;
            }

            @Override // java.util.Iterator, java.util.ListIterator
            public E next() {
                if (!this.hasNext) {
                    throw new NoSuchElementException();
                }
                this.hasNext = false;
                return SingletonImmutableList.this.element;
            }

            @Override // java.util.ListIterator
            public int nextIndex() {
                return this.hasNext ? 0 : 1;
            }

            @Override // java.util.ListIterator
            public E previous() {
                if (this.hasNext) {
                    throw new NoSuchElementException();
                }
                this.hasNext = true;
                return SingletonImmutableList.this.element;
            }

            @Override // java.util.ListIterator
            public int previousIndex() {
                return this.hasNext ? -1 : 0;
            }
        };
    }

    @Override // java.util.Collection, java.util.List
    public int size() {
        return 1;
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public ImmutableList<E> subList(int fromIndex, int toIndex) {
        Preconditions.checkPositionIndexes(fromIndex, toIndex, 1);
        return fromIndex == toIndex ? ImmutableList.of() : this;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object object) {
        return this.element.equals(object);
    }

    @Override // com.google.common.collect.ImmutableList, java.util.Collection, java.util.List
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof List)) {
            return false;
        }
        List<?> that = (List) object;
        return that.size() == 1 && this.element.equals(that.get(0));
    }

    @Override // com.google.common.collect.ImmutableList, java.util.Collection, java.util.List
    public int hashCode() {
        return this.element.hashCode() + 31;
    }

    @Override // com.google.common.collect.ImmutableCollection
    public String toString() {
        String elementToString = this.element.toString();
        return new StringBuilder(elementToString.length() + 2).append('[').append(elementToString).append(']').toString();
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean isEmpty() {
        return false;
    }

    @Override // com.google.common.collect.ImmutableCollection
    boolean isPartialView() {
        return false;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public Object[] toArray() {
        return new Object[]{this.element};
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v0 */
    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public <T> T[] toArray(T[] tArr) {
        if (tArr.length == 0) {
            tArr = (T[]) ObjectArrays.newArray(tArr, 1);
        } else if (tArr.length > 1) {
            tArr[1] = null;
        }
        tArr[0] = this.element;
        return tArr;
    }
}
