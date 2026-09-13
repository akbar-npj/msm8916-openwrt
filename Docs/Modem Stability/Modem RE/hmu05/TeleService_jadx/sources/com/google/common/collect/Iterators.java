package com.google.common.collect;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/* JADX INFO: loaded from: classes.dex */
public final class Iterators {
    static final UnmodifiableIterator<Object> EMPTY_ITERATOR = new UnmodifiableIterator<Object>() { // from class: com.google.common.collect.Iterators.1
        @Override // java.util.Iterator
        public boolean hasNext() {
            return false;
        }

        @Override // java.util.Iterator
        public Object next() {
            throw new NoSuchElementException();
        }
    };
    private static final Iterator<Object> EMPTY_MODIFIABLE_ITERATOR = new Iterator<Object>() { // from class: com.google.common.collect.Iterators.2
        @Override // java.util.Iterator
        public boolean hasNext() {
            return false;
        }

        @Override // java.util.Iterator
        public Object next() {
            throw new NoSuchElementException();
        }

        @Override // java.util.Iterator
        public void remove() {
            throw new IllegalStateException();
        }
    };

    private Iterators() {
    }

    public static <T> UnmodifiableIterator<T> emptyIterator() {
        return (UnmodifiableIterator<T>) EMPTY_ITERATOR;
    }

    public static boolean contains(Iterator<?> iterator, Object element) {
        if (element == null) {
            while (iterator.hasNext()) {
                if (iterator.next() == null) {
                    return true;
                }
            }
        } else {
            while (iterator.hasNext()) {
                if (element.equals(iterator.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean elementsEqual(Iterator<?> iterator1, Iterator<?> iterator2) {
        while (iterator1.hasNext()) {
            if (!iterator2.hasNext()) {
                return false;
            }
            Object o1 = iterator1.next();
            Object o2 = iterator2.next();
            if (!Objects.equal(o1, o2)) {
                return false;
            }
        }
        return !iterator2.hasNext();
    }

    public static String toString(Iterator<?> iterator) {
        if (!iterator.hasNext()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(iterator.next());
        while (iterator.hasNext()) {
            builder.append(", ").append(iterator.next());
        }
        return builder.append(']').toString();
    }

    public static <T> T getOnlyElement(Iterator<T> iterator) {
        T first = iterator.next();
        if (!iterator.hasNext()) {
            return first;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("expected one element but was: <" + first);
        for (int i = 0; i < 4 && iterator.hasNext(); i++) {
            sb.append(", " + iterator.next());
        }
        if (iterator.hasNext()) {
            sb.append(", ...");
        }
        sb.append('>');
        throw new IllegalArgumentException(sb.toString());
    }

    public static <T> boolean addAll(Collection<T> addTo, Iterator<? extends T> iterator) {
        Preconditions.checkNotNull(addTo);
        boolean wasModified = false;
        while (iterator.hasNext()) {
            wasModified |= addTo.add(iterator.next());
        }
        return wasModified;
    }

    public static <F, T> Iterator<T> transform(final Iterator<F> fromIterator, final Function<? super F, ? extends T> function) {
        Preconditions.checkNotNull(fromIterator);
        Preconditions.checkNotNull(function);
        return new Iterator<T>() { // from class: com.google.common.collect.Iterators.8
            @Override // java.util.Iterator
            public boolean hasNext() {
                return fromIterator.hasNext();
            }

            @Override // java.util.Iterator
            public T next() {
                return (T) function.apply(fromIterator.next());
            }

            @Override // java.util.Iterator
            public void remove() {
                fromIterator.remove();
            }
        };
    }

    public static <T> UnmodifiableIterator<T> forArray(final T... array) {
        Preconditions.checkNotNull(array);
        return new AbstractIndexedListIterator<T>(array.length) { // from class: com.google.common.collect.Iterators.11
            @Override // com.google.common.collect.AbstractIndexedListIterator
            protected T get(int i) {
                return (T) array[i];
            }
        };
    }

    static <T> UnmodifiableIterator<T> forArray(final T[] array, final int offset, int length) {
        Preconditions.checkArgument(length >= 0);
        int end = offset + length;
        Preconditions.checkPositionIndexes(offset, end, array.length);
        return new AbstractIndexedListIterator<T>(length) { // from class: com.google.common.collect.Iterators.12
            @Override // com.google.common.collect.AbstractIndexedListIterator
            protected T get(int i) {
                return (T) array[offset + i];
            }
        };
    }

    public static <T> UnmodifiableIterator<T> singletonIterator(final T value) {
        return new UnmodifiableIterator<T>() { // from class: com.google.common.collect.Iterators.13
            boolean done;

            @Override // java.util.Iterator
            public boolean hasNext() {
                return !this.done;
            }

            @Override // java.util.Iterator
            public T next() {
                if (this.done) {
                    throw new NoSuchElementException();
                }
                this.done = true;
                return (T) value;
            }
        };
    }
}
