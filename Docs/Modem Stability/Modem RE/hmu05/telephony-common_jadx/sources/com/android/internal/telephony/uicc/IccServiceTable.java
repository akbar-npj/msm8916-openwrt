package com.android.internal.telephony.uicc;

import android.telephony.Rlog;

/* JADX INFO: loaded from: classes.dex */
public abstract class IccServiceTable {
    protected final byte[] mServiceTable;

    protected abstract String getTag();

    protected abstract Object[] getValues();

    protected IccServiceTable(byte[] table) {
        this.mServiceTable = table;
    }

    protected boolean isAvailable(int service) {
        int offset = service / 8;
        if (offset >= this.mServiceTable.length) {
            Rlog.e(getTag(), "isAvailable for service " + (service + 1) + " fails, max service is " + (this.mServiceTable.length * 8));
            return false;
        }
        int bit = service % 8;
        return (this.mServiceTable[offset] & (1 << bit)) != 0;
    }

    public String toString() {
        Object[] values = getValues();
        int numBytes = this.mServiceTable.length;
        StringBuilder builder = new StringBuilder(getTag()).append('[').append(numBytes * 8).append("]={ ");
        boolean addComma = false;
        for (int i = 0; i < numBytes; i++) {
            byte currentByte = this.mServiceTable[i];
            for (int bit = 0; bit < 8; bit++) {
                if (((1 << bit) & currentByte) != 0) {
                    if (addComma) {
                        builder.append(", ");
                    } else {
                        addComma = true;
                    }
                    int ordinal = (i * 8) + bit;
                    if (ordinal < values.length) {
                        builder.append(values[ordinal]);
                    } else {
                        builder.append('#').append(ordinal + 1);
                    }
                }
            }
        }
        return builder.append(" }").toString();
    }
}
