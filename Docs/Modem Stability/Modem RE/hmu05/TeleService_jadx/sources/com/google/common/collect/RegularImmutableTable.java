package com.google.common.collect;

import com.google.common.base.Function;
import java.util.Map;
import java.util.Set;

/* JADX INFO: loaded from: classes.dex */
abstract class RegularImmutableTable<R, C, V> extends ImmutableTable<R, C, V> {
    private static final Function<Table.Cell<Object, Object, Object>, Object> GET_VALUE_FUNCTION = new Function<Table.Cell<Object, Object, Object>, Object>() { // from class: com.google.common.collect.RegularImmutableTable.1
        @Override // com.google.common.base.Function
        public Object apply(Table.Cell<Object, Object, Object> from) {
            return from.getValue();
        }
    };
    private final ImmutableSet<Table.Cell<R, C, V>> cellSet;

    @Override // com.google.common.collect.ImmutableTable, com.google.common.collect.Table
    public final ImmutableSet<Table.Cell<R, C, V>> cellSet() {
        return this.cellSet;
    }

    static final class SparseImmutableTable<R, C, V> extends RegularImmutableTable<R, C, V> {
        private final ImmutableMap<R, Map<C, V>> rowMap;

        @Override // com.google.common.collect.RegularImmutableTable, com.google.common.collect.ImmutableTable, com.google.common.collect.Table
        public /* bridge */ /* synthetic */ Set cellSet() {
            return super.cellSet();
        }

        @Override // com.google.common.collect.ImmutableTable
        public ImmutableMap<R, Map<C, V>> rowMap() {
            return this.rowMap;
        }
    }

    static final class DenseImmutableTable<R, C, V> extends RegularImmutableTable<R, C, V> {
        private final ImmutableBiMap<C, Integer> columnKeyToIndex;
        private final ImmutableBiMap<R, Integer> rowKeyToIndex;
        private volatile transient ImmutableMap<R, Map<C, V>> rowMap;
        private final V[][] values;

        @Override // com.google.common.collect.RegularImmutableTable, com.google.common.collect.ImmutableTable, com.google.common.collect.Table
        public /* bridge */ /* synthetic */ Set cellSet() {
            return super.cellSet();
        }

        private ImmutableMap<R, Map<C, V>> makeRowMap() {
            ImmutableMap.Builder<R, Map<C, V>> rowMapBuilder = ImmutableMap.builder();
            for (int r = 0; r < this.values.length; r++) {
                V[] row = this.values[r];
                ImmutableMap.Builder<C, V> columnMapBuilder = ImmutableMap.builder();
                for (int c = 0; c < row.length; c++) {
                    V value = row[c];
                    if (value != null) {
                        columnMapBuilder.put(this.columnKeyToIndex.inverse().get(Integer.valueOf(c)), value);
                    }
                }
                rowMapBuilder.put(this.rowKeyToIndex.inverse().get(Integer.valueOf(r)), columnMapBuilder.build());
            }
            return rowMapBuilder.build();
        }

        @Override // com.google.common.collect.ImmutableTable
        public ImmutableMap<R, Map<C, V>> rowMap() {
            ImmutableMap<R, Map<C, V>> result = this.rowMap;
            if (result == null) {
                ImmutableMap<R, Map<C, V>> result2 = makeRowMap();
                this.rowMap = result2;
                return result2;
            }
            return result;
        }
    }
}
