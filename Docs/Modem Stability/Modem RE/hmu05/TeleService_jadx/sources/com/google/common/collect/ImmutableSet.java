package com.google.common.collect;

import com.google.common.base.Preconditions;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
public abstract class ImmutableSet<E> extends ImmutableCollection<E> implements Set<E> {
    @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
    public abstract UnmodifiableIterator<E> iterator();

    public static <E> ImmutableSet<E> of() {
        return EmptyImmutableSet.INSTANCE;
    }

    public static <E> ImmutableSet<E> of(E element) {
        return new SingletonImmutableSet(element);
    }

    private static <E> ImmutableSet<E> construct(Object... elements) {
        int tableSize = chooseTableSize(elements.length);
        Object[] table = new Object[tableSize];
        int mask = tableSize - 1;
        ArrayList<Object> uniqueElementsList = null;
        int hashCode = 0;
        for (int i = 0; i < elements.length; i++) {
            Object element = elements[i];
            int hash = element.hashCode();
            int j = Hashing.smear(hash);
            while (true) {
                int index = j & mask;
                Object value = table[index];
                if (value == null) {
                    if (uniqueElementsList != null) {
                        uniqueElementsList.add(element);
                    }
                    table[index] = element;
                    hashCode += hash;
                    break;
                }
                if (!value.equals(element)) {
                    j++;
                } else {
                    if (uniqueElementsList != null) {
                        break;
                    }
                    uniqueElementsList = new ArrayList<>(elements.length);
                    for (int k = 0; k < i; k++) {
                        Object previous = elements[k];
                        uniqueElementsList.add(previous);
                    }
                    break;
                }
            }
        }
        Object[] uniqueElements = uniqueElementsList == null ? elements : uniqueElementsList.toArray();
        if (uniqueElements.length == 1) {
            return new SingletonImmutableSet(uniqueElements[0], hashCode);
        }
        if (tableSize > chooseTableSize(uniqueElements.length) * 2) {
            return construct(uniqueElements);
        }
        return new RegularImmutableSet(uniqueElements, hashCode, table, mask);
    }

    static int chooseTableSize(int setSize) {
        if (setSize < 536870912) {
            return Integer.highestOneBit(setSize) << 2;
        }
        Preconditions.checkArgument(setSize < 1073741824, "collection too large");
        return 1073741824;
    }

    public static <E> ImmutableSet<E> copyOf(E[] elements) {
        switch (elements.length) {
            case 0:
                return of();
            case 1:
                return of((Object) elements[0]);
            default:
                return construct((Object[]) elements.clone());
        }
    }

    ImmutableSet() {
    }

    boolean isHashCodeFast() {
        return false;
    }

    @Override // java.util.Collection, java.util.Set
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if ((object instanceof ImmutableSet) && isHashCodeFast() && ((ImmutableSet) object).isHashCodeFast() && hashCode() != object.hashCode()) {
            return false;
        }
        return Sets.equalsImpl(this, object);
    }

    @Override // java.util.Collection, java.util.Set
    public int hashCode() {
        return Sets.hashCodeImpl(this);
    }

    static abstract class ArrayImmutableSet<E> extends ImmutableSet<E> {
        final transient Object[] elements;

        ArrayImmutableSet(Object[] elements) {
            this.elements = elements;
        }

        @Override // java.util.Collection, java.util.Set
        public int size() {
            return this.elements.length;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean isEmpty() {
            return false;
        }

        @Override // com.google.common.collect.ImmutableSet, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
        public UnmodifiableIterator<E> iterator() {
            return Iterators.forArray(this.elements);
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public Object[] toArray() {
            Object[] array = new Object[size()];
            System.arraycopy(this.elements, 0, array, 0, size());
            return array;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public <T> T[] toArray(T[] tArr) {
            int size = size();
            if (tArr.length < size) {
                tArr = (T[]) ObjectArrays.newArray(tArr, size);
            } else if (tArr.length > size) {
                tArr[size] = null;
            }
            System.arraycopy(this.elements, 0, tArr, 0, size);
            return tArr;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean containsAll(Collection<?> targets) {
            if (targets == this) {
                return true;
            }
            if (!(targets instanceof ArrayImmutableSet)) {
                return super.containsAll(targets);
            }
            if (targets.size() > size()) {
                return false;
            }
            Object[] arr$ = ((ArrayImmutableSet) targets).elements;
            for (Object target : arr$) {
                if (!contains(target)) {
                    return false;
                }
            }
            return true;
        }

        @Override // com.google.common.collect.ImmutableCollection
        boolean isPartialView() {
            return false;
        }

        @Override // com.google.common.collect.ImmutableCollection
        ImmutableList<E> createAsList() {
            return new ImmutableAsList(this.elements, this);
        }
    }

    static abstract class TransformedImmutableSet<D, E> extends ImmutableSet<E> {
        final int hashCode;
        final D[] source;

        abstract E transform(D d);

        TransformedImmutableSet(D[] source, int hashCode) {
            this.source = source;
            this.hashCode = hashCode;
        }

        @Override // java.util.Collection, java.util.Set
        public int size() {
            return this.source.length;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean isEmpty() {
            return false;
        }

        @Override // com.google.common.collect.ImmutableSet, com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
        public UnmodifiableIterator<E> iterator() {
            return new AbstractIndexedListIterator<E>(this.source.length) { // from class: com.google.common.collect.ImmutableSet.TransformedImmutableSet.1
                @Override // com.google.common.collect.AbstractIndexedListIterator
                protected E get(int i) {
                    return (E) TransformedImmutableSet.this.transform(TransformedImmutableSet.this.source[i]);
                }
            };
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public Object[] toArray() {
            return toArray(new Object[size()]);
        }

        /* JADX WARN: Multi-variable type inference failed */
        /* JADX WARN: Type inference failed for: r1v0 */
        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public <T> T[] toArray(T[] tArr) {
            int size = size();
            if (tArr.length < size) {
                tArr = (T[]) ObjectArrays.newArray(tArr, size);
            } else if (tArr.length > size) {
                tArr[size] = null;
            }
            ?? r1 = tArr;
            for (int i = 0; i < this.source.length; i++) {
                r1[i] = transform(this.source[i]);
            }
            return tArr;
        }

        @Override // com.google.common.collect.ImmutableSet, java.util.Collection, java.util.Set
        public final int hashCode() {
            return this.hashCode;
        }

        @Override // com.google.common.collect.ImmutableSet
        boolean isHashCodeFast() {
            return true;
        }
    }

    private static class SerializedForm implements Serializable {
        private static final long serialVersionUID = 0;
        final Object[] elements;

        SerializedForm(Object[] elements) {
            this.elements = elements;
        }

        Object readResolve() {
            return ImmutableSet.copyOf(this.elements);
        }
    }

    @Override // com.google.common.collect.ImmutableCollection
    Object writeReplace() {
        return new SerializedForm(toArray());
    }

    public static class Builder<E> extends ImmutableCollection.Builder<E> {
        final ArrayList<E> contents = Lists.newArrayList();

        @Override // com.google.common.collect.ImmutableCollection.Builder
        public Builder<E> add(E e) {
            this.contents.add((E) Preconditions.checkNotNull(e));
            return this;
        }

        @Override // com.google.common.collect.ImmutableCollection.Builder
        public Builder<E> add(E... elements) {
            this.contents.ensureCapacity(this.contents.size() + elements.length);
            super.add((Object[]) elements);
            return this;
        }
    }
}
