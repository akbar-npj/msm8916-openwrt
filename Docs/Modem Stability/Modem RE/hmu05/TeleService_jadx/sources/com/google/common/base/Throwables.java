package com.google.common.base;

/* JADX INFO: loaded from: classes.dex */
public final class Throwables {
    private Throwables() {
    }

    /* JADX INFO: Thrown type has an unknown type hierarchy: X extends java.lang.Throwable */
    public static <X extends Throwable> void propagateIfInstanceOf(Throwable throwable, Class<X> declaredType) throws Throwable {
        if (throwable != null && declaredType.isInstance(throwable)) {
            throw declaredType.cast(throwable);
        }
    }
}
