package com.google.common.collect;

import java.lang.reflect.Array;

/* JADX INFO: loaded from: classes.dex */
class Platform {
    static <T> T[] clone(T[] tArr) {
        return (T[]) ((Object[]) tArr.clone());
    }

    static void unsafeArrayCopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    static <T> T[] newArray(T[] tArr, int i) {
        return (T[]) ((Object[]) Array.newInstance(tArr.getClass().getComponentType(), i));
    }

    static MapMaker tryWeakKeys(MapMaker mapMaker) {
        return mapMaker.weakKeys();
    }

    private Platform() {
    }
}
