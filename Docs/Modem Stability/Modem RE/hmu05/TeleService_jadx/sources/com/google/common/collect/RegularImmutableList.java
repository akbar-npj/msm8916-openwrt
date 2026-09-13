package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
class RegularImmutableList<E> extends ImmutableList<E> {
    private final transient Object[] array;
    private final transient int offset;
    private final transient int size;

    RegularImmutableList(Object[] array, int offset, int size) {
        this.offset = offset;
        this.size = size;
        this.array = array;
    }

    RegularImmutableList(Object[] array) {
        this(array, 0, array.length);
    }

    @Override // java.util.Collection, java.util.List
    public int size() {
        return this.size;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean isEmpty() {
        return false;
    }

    @Override // com.google.common.collect.ImmutableCollection
    boolean isPartialView() {
        return (this.offset == 0 && this.size == this.array.length) ? false : true;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public boolean contains(Object target) {
        return indexOf(target) != -1;
    }

    @Override // com.google.common.collect.ImmutableList, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public UnmodifiableIterator<E> iterator() {
        return Iterators.forArray(this.array, this.offset, this.size);
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public Object[] toArray() {
        Object[] newArray = new Object[size()];
        System.arraycopy(this.array, this.offset, newArray, 0, this.size);
        return newArray;
    }

    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
    public <T> T[] toArray(T[] tArr) {
        if (tArr.length < this.size) {
            tArr = (T[]) ObjectArrays.newArray(tArr, this.size);
        } else if (tArr.length > this.size) {
            tArr[this.size] = null;
        }
        System.arraycopy(this.array, this.offset, tArr, 0, this.size);
        return tArr;
    }

    @Override // java.util.List
    public E get(int i) {
        Preconditions.checkElementIndex(i, this.size);
        return (E) this.array[this.offset + i];
    }

    @Override // java.util.List
    public int indexOf(Object target) {
        if (target != null) {
            for (int i = this.offset; i < this.offset + this.size; i++) {
                if (this.array[i].equals(target)) {
                    return i - this.offset;
                }
            }
        }
        return -1;
    }

    @Override // java.util.List
    public int lastIndexOf(Object target) {
        if (target != null) {
            for (int i = (this.offset + this.size) - 1; i >= this.offset; i--) {
                if (this.array[i].equals(target)) {
                    return i - this.offset;
                }
            }
        }
        return -1;
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public ImmutableList<E> subList(int fromIndex, int toIndex) {
        Preconditions.checkPositionIndexes(fromIndex, toIndex, this.size);
        return fromIndex == toIndex ? ImmutableList.of() : new RegularImmutableList(this.array, this.offset + fromIndex, toIndex - fromIndex);
    }

    @Override // com.google.common.collect.ImmutableList, java.util.List
    public UnmodifiableListIterator<E> listIterator(int start) {
        return new AbstractIndexedListIterator<E>(this.size, start) { // from class: com.google.common.collect.RegularImmutableList.1
            @Override // com.google.common.collect.AbstractIndexedListIterator
            protected E get(int i) {
                return (E) RegularImmutableList.this.array[RegularImmutableList.this.offset + i];
            }
        };
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
        if (size() != that.size()) {
            return false;
        }
        int index = this.offset;
        if (object instanceof RegularImmutableList) {
            RegularImmutableList<?> other = (RegularImmutableList) object;
            int i = other.offset;
            while (i < other.offset + other.size) {
                int index2 = index + 1;
                if (!this.array[index].equals(other.array[i])) {
                    return false;
                }
                i++;
                index = index2;
            }
            return true;
        }
        for (Object element : that) {
            int index3 = index + 1;
            if (!this.array[index].equals(element)) {
                return false;
            }
            index = index3;
        }
        return true;
    }

    @Override // com.google.common.collect.ImmutableList, java.util.Collection, java.util.List
    public int hashCode() {
        int hashCode = 1;
        for (int i = this.offset; i < this.offset + this.size; i++) {
            hashCode = (hashCode * 31) + this.array[i].hashCode();
        }
        return hashCode;
    }

    @Override // com.google.common.collect.ImmutableCollection
    public String toString() {
        StringBuilder sb = Collections2.newStringBuilderForCollection(size()).append('[').append(this.array[this.offset]);
        for (int i = this.offset + 1; i < this.offset + this.size; i++) {
            sb.append(", ").append(this.array[i]);
        }
        return sb.append(']').toString();
    }
}
