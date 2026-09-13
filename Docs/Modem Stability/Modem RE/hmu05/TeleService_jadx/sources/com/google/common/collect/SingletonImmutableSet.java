package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
final class SingletonImmutableSet<E> extends ImmutableSet<E> {
    private transient int cachedHashCode;
    final transient E element;

    SingletonImmutableSet(E e) {
        this.element = (E) Preconditions.checkNotNull(e);
    }

    SingletonImmutableSet(E element, int hashCode) {
        this.element = element;
        this.cachedHashCode = hashCode;
    }

    @Override // java.util.Collection, java.util.Set
    public int size() {
        return 1;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean isEmpty() {
        return false;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object target) {
        return this.element.equals(target);
    }

    @Override // com.google.common.collect.ImmutableSet, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<E> iterator() {
        return Iterators.singletonIterator(this.element);
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

    @Override // com.google.common.collect.ImmutableSet, java.util.Collection, java.util.Set
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof Set)) {
            return false;
        }
        Set<?> that = (Set) object;
        return that.size() == 1 && this.element.equals(that.iterator().next());
    }

    @Override // com.google.common.collect.ImmutableSet, java.util.Collection, java.util.Set
    public final int hashCode() {
        int code = this.cachedHashCode;
        if (code == 0) {
            int code2 = this.element.hashCode();
            this.cachedHashCode = code2;
            return code2;
        }
        return code;
    }

    @Override // com.google.common.collect.ImmutableSet
    boolean isHashCodeFast() {
        return this.cachedHashCode != 0;
    }

    @Override // com.google.common.collect.ImmutableCollection
    public String toString() {
        String elementToString = this.element.toString();
        return new StringBuilder(elementToString.length() + 2).append('[').append(elementToString).append(']').toString();
    }
}
