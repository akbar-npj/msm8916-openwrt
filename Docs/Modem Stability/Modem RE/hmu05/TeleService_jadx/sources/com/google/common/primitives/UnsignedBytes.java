package com.google.common.primitives;

import java.util.Comparator;

/* JADX INFO: loaded from: classes.dex */
public final class UnsignedBytes {
    private UnsignedBytes() {
    }

    public static int toInt(byte value) {
        return value & 255;
    }

    public static int compare(byte a, byte b) {
        return toInt(a) - toInt(b);
    }

    static Comparator<byte[]> lexicographicalComparatorJavaImpl() {
        return LexicographicalComparatorHolder.PureJavaComparator.INSTANCE;
    }

    static class LexicographicalComparatorHolder {
        static final String UNSAFE_COMPARATOR_NAME = LexicographicalComparatorHolder.class.getName() + "$UnsafeComparator";
        static final Comparator<byte[]> BEST_COMPARATOR = UnsignedBytes.lexicographicalComparatorJavaImpl();

        LexicographicalComparatorHolder() {
        }

        enum PureJavaComparator implements Comparator<byte[]> {
            INSTANCE;

            @Override // java.util.Comparator
            public int compare(byte[] left, byte[] right) {
                int minLength = Math.min(left.length, right.length);
                for (int i = 0; i < minLength; i++) {
                    int result = UnsignedBytes.compare(left[i], right[i]);
                    if (result != 0) {
                        return result;
                    }
                }
                return left.length - right.length;
            }
        }
    }
}
