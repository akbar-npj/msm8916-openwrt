package com.google.common.collect;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public final class Lists {
    private Lists() {
    }

    public static <E> ArrayList<E> newArrayList() {
        return new ArrayList<>();
    }

    static int computeArrayListCapacity(int arraySize) {
        Preconditions.checkArgument(arraySize >= 0);
        return Ints.saturatedCast(5 + ((long) arraySize) + ((long) (arraySize / 10)));
    }

    public static <E> ArrayList<E> newArrayList(Iterable<? extends E> elements) {
        Preconditions.checkNotNull(elements);
        return elements instanceof Collection ? new ArrayList<>(Collections2.cast(elements)) : newArrayList(elements.iterator());
    }

    public static <E> ArrayList<E> newArrayList(Iterator<? extends E> elements) {
        Preconditions.checkNotNull(elements);
        ArrayList<E> list = newArrayList();
        while (elements.hasNext()) {
            list.add(elements.next());
        }
        return list;
    }

    public static <E> ArrayList<E> newArrayListWithCapacity(int initialArraySize) {
        Preconditions.checkArgument(initialArraySize >= 0);
        return new ArrayList<>(initialArraySize);
    }

    static int hashCodeImpl(List<?> list) {
        int hashCode = 1;
        Iterator<?> it = list.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            hashCode = (hashCode * 31) + (o == null ? 0 : o.hashCode());
        }
        return hashCode;
    }

    static boolean equalsImpl(List<?> list, Object object) {
        if (object == Preconditions.checkNotNull(list)) {
            return true;
        }
        if (!(object instanceof List)) {
            return false;
        }
        List<?> o = (List) object;
        return list.size() == o.size() && Iterators.elementsEqual(list.iterator(), o.iterator());
    }
}
