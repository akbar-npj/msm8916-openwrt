package com.android.internal.telephony.cat;

import java.io.ByteArrayOutputStream;

/* JADX INFO: compiled from: ResponseData.java */
/* JADX INFO: loaded from: classes.dex */
class SelectItemResponseData extends ResponseData {
    private int mId;

    public SelectItemResponseData(int id) {
        this.mId = id;
    }

    @Override // com.android.internal.telephony.cat.ResponseData
    public void format(ByteArrayOutputStream buf) {
        int tag = ComprehensionTlvTag.ITEM_ID.value() | 128;
        buf.write(tag);
        buf.write(1);
        buf.write(this.mId);
    }
}
