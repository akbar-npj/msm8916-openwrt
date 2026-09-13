package com.android.internal.telephony.cat;

/* JADX INFO: compiled from: CommandDetails.java */
/* JADX INFO: loaded from: classes.dex */
class IconId extends ValueObject {
    int recordNumber;
    boolean selfExplanatory;

    IconId() {
    }

    @Override // com.android.internal.telephony.cat.ValueObject
    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ICON_ID;
    }
}
