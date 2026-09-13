package com.google.common.collect;

import java.util.Collection;

/* JADX INFO: loaded from: classes.dex */
public final class ObjectArrays {
    private ObjectArrays() {
    }

    public static <T> T[] newArray(T[] tArr, int i) {
        return (T[]) Platform.newArray(tArr, i);
    }

    static <T> T[] arraysCopyOf(T[] tArr, int i) {
        T[] tArr2 = (T[]) newArray(tArr, i);
        Platform.unsafeArrayCopy(tArr, 0, tArr2, 0, Math.min(tArr.length, i));
        return tArr2;
    }

    static <T> T[] toArrayImpl(Collection<?> collection, T[] tArr) {
        int size = collection.size();
        if (tArr.length < size) {
            tArr = (T[]) newArray(tArr, size);
        }
        fillArray(collection, tArr);
        if (tArr.length > size) {
            tArr[size] = null;
        }
        return tArr;
    }

    static Object[] toArrayImpl(Collection<?> c) {
        return fillArray(c, new Object[c.size()]);
    }

    private static Object[] fillArray(Iterable<?> elements, Object[] array) {
        int i = 0;
        for (Object element : elements) {
            array[i] = element;
            i++;
        }
        return array;
    }
}
