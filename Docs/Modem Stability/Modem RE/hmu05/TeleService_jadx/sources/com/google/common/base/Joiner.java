package com.google.common.base;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class Joiner {
    private final String separator;

    public static Joiner on(String separator) {
        return new Joiner(separator);
    }

    private Joiner(String separator) {
        this.separator = (String) Preconditions.checkNotNull(separator);
    }

    public <A extends Appendable> A appendTo(A appendable, Iterator<?> parts) throws IOException {
        Preconditions.checkNotNull(appendable);
        if (parts.hasNext()) {
            appendable.append(toString(parts.next()));
            while (parts.hasNext()) {
                appendable.append(this.separator);
                appendable.append(toString(parts.next()));
            }
        }
        return appendable;
    }

    public final StringBuilder appendTo(StringBuilder builder, Iterable<?> parts) {
        return appendTo(builder, parts.iterator());
    }

    public final StringBuilder appendTo(StringBuilder builder, Iterator<?> parts) {
        try {
            appendTo(builder, parts);
            return builder;
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public final StringBuilder appendTo(StringBuilder builder, Object[] parts) {
        return appendTo(builder, Arrays.asList(parts));
    }

    /* JADX INFO: renamed from: com.google.common.base.Joiner$1, reason: invalid class name */
    class AnonymousClass1 extends Joiner {
        final /* synthetic */ Joiner this$0;
        final /* synthetic */ String val$nullText;

        @Override // com.google.common.base.Joiner
        CharSequence toString(Object part) {
            return part == null ? this.val$nullText : this.this$0.toString(part);
        }
    }

    public MapJoiner withKeyValueSeparator(String keyValueSeparator) {
        return new MapJoiner(this, keyValueSeparator, null);
    }

    public static final class MapJoiner {
        private final Joiner joiner;
        private final String keyValueSeparator;

        /* synthetic */ MapJoiner(Joiner x0, String x1, AnonymousClass1 x2) {
            this(x0, x1);
        }

        private MapJoiner(Joiner joiner, String keyValueSeparator) {
            this.joiner = joiner;
            this.keyValueSeparator = (String) Preconditions.checkNotNull(keyValueSeparator);
        }

        public StringBuilder appendTo(StringBuilder builder, Map<?, ?> map) {
            return appendTo(builder, map.entrySet());
        }

        public <A extends Appendable> A appendTo(A appendable, Iterator<? extends Map.Entry<?, ?>> parts) throws IOException {
            Preconditions.checkNotNull(appendable);
            if (parts.hasNext()) {
                Map.Entry<?, ?> entry = parts.next();
                appendable.append(this.joiner.toString(entry.getKey()));
                appendable.append(this.keyValueSeparator);
                appendable.append(this.joiner.toString(entry.getValue()));
                while (parts.hasNext()) {
                    appendable.append(this.joiner.separator);
                    Map.Entry<?, ?> e = parts.next();
                    appendable.append(this.joiner.toString(e.getKey()));
                    appendable.append(this.keyValueSeparator);
                    appendable.append(this.joiner.toString(e.getValue()));
                }
            }
            return appendable;
        }

        public StringBuilder appendTo(StringBuilder builder, Iterable<? extends Map.Entry<?, ?>> entries) {
            return appendTo(builder, entries.iterator());
        }

        public StringBuilder appendTo(StringBuilder builder, Iterator<? extends Map.Entry<?, ?>> entries) {
            try {
                appendTo(builder, entries);
                return builder;
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }

    CharSequence toString(Object part) {
        Preconditions.checkNotNull(part);
        return part instanceof CharSequence ? (CharSequence) part : part.toString();
    }
}
