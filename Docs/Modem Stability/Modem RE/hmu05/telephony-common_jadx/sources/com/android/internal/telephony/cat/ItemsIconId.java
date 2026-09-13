package com.android.internal.telephony.cat;

/* JADX INFO: compiled from: CommandDetails.java */
/* JADX INFO: loaded from: classes.dex */
class ItemsIconId extends ValueObject {
    int[] recordNumbers;
    boolean selfExplanatory;

    ItemsIconId() {
    }

    @Override // com.android.internal.telephony.cat.ValueObject
    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ITEM_ICON_ID_LIST;
    }
}
