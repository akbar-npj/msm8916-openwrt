package com.google.common.collect;

import java.io.Serializable;
import java.util.Collection;

/* JADX INFO: loaded from: classes.dex */
public abstract class ImmutableCollection<E> implements Collection<E>, Serializable {
    static final ImmutableCollection<Object> EMPTY_IMMUTABLE_COLLECTION = new EmptyImmutableCollection();
    private transient ImmutableList<E> asList;

    abstract boolean isPartialView();

    @Override // java.util.Collection, java.lang.Iterable, java.util.List
    public abstract UnmodifiableIterator<E> iterator();

    ImmutableCollection() {
    }

    @Override // java.util.Collection, java.util.List
    public Object[] toArray() {
        return ObjectArrays.toArrayImpl(this);
    }

    @Override // java.util.Collection, java.util.List
    public <T> T[] toArray(T[] tArr) {
        return (T[]) ObjectArrays.toArrayImpl(this, tArr);
    }

    @Override // java.util.Collection, java.util.List
    public boolean contains(Object object) {
        return object != null && Iterators.contains(iterator(), object);
    }

    @Override // java.util.Collection, java.util.List
    public boolean containsAll(Collection<?> targets) {
        return Collections2.containsAllImpl(this, targets);
    }

    @Override // java.util.Collection, java.util.List
    public boolean isEmpty() {
        return size() == 0;
    }

    public String toString() {
        return Collections2.toStringImpl(this);
    }

    @Override // java.util.Collection
    public final boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Collection
    public final boolean remove(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Collection
    public final boolean addAll(Collection<? extends E> newElements) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Collection
    public final boolean removeAll(Collection<?> oldElements) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Collection
    public final boolean retainAll(Collection<?> elementsToKeep) {
        throw new UnsupportedOperationException();
    }

    @Override // java.util.Collection
    public final void clear() {
        throw new UnsupportedOperationException();
    }

    public ImmutableList<E> asList() {
        ImmutableList<E> list = this.asList;
        if (list != null) {
            return list;
        }
        ImmutableList<E> list2 = createAsList();
        this.asList = list2;
        return list2;
    }

    ImmutableList<E> createAsList() {
        switch (size()) {
            case 0:
                return ImmutableList.of();
            case 1:
                return ImmutableList.of((Object) iterator().next());
            default:
                return new ImmutableAsList(toArray(), this);
        }
    }

    private static class EmptyImmutableCollection extends ImmutableCollection<Object> {
        private static final Object[] EMPTY_ARRAY = new Object[0];

        private EmptyImmutableCollection() {
        }

        @Override // java.util.Collection
        public int size() {
            return 0;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean isEmpty() {
            return true;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean contains(Object object) {
            return false;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
        public UnmodifiableIterator<Object> iterator() {
            return Iterators.EMPTY_ITERATOR;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public Object[] toArray() {
            return EMPTY_ARRAY;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public <T> T[] toArray(T[] array) {
            if (array.length > 0) {
                array[0] = null;
            }
            return array;
        }

        @Override // com.google.common.collect.ImmutableCollection
        ImmutableList<Object> createAsList() {
            return ImmutableList.of();
        }

        @Override // com.google.common.collect.ImmutableCollection
        boolean isPartialView() {
            return false;
        }
    }

    private static class ArrayImmutableCollection<E> extends ImmutableCollection<E> {
        private final E[] elements;

        ArrayImmutableCollection(E[] elements) {
            this.elements = elements;
        }

        @Override // java.util.Collection
        public int size() {
            return this.elements.length;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.util.List
        public boolean isEmpty() {
            return false;
        }

        @Override // com.google.common.collect.ImmutableCollection, java.util.Collection, java.lang.Iterable, java.util.List
        public UnmodifiableIterator<E> iterator() {
            return Iterators.forArray(this.elements);
        }

        @Override // com.google.common.collect.ImmutableCollection
        ImmutableList<E> createAsList() {
            return this.elements.length == 1 ? new SingletonImmutableList(this.elements[0]) : new RegularImmutableList(this.elements);
        }

        @Override // com.google.common.collect.ImmutableCollection
        boolean isPartialView() {
            return false;
        }
    }

    private static class SerializedForm implements Serializable {
        private static final long serialVersionUID = 0;
        final Object[] elements;

        SerializedForm(Object[] elements) {
            this.elements = elements;
        }

        Object readResolve() {
            return this.elements.length == 0 ? ImmutableCollection.EMPTY_IMMUTABLE_COLLECTION : new ArrayImmutableCollection(Platform.clone(this.elements));
        }
    }

    Object writeReplace() {
        return new SerializedForm(toArray());
    }

    public static abstract class Builder<E> {
        public abstract Builder<E> add(E e);

        Builder() {
        }

        public Builder<E> add(E... elements) {
            for (E element : elements) {
                add(element);
            }
            return this;
        }
    }
}
