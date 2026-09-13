package com.android.internal.telephony.cat;

/* JADX INFO: compiled from: CommandDetails.java */
/* JADX INFO: loaded from: classes.dex */
class ActivateDescriptor extends ValueObject {
    public int target;

    ActivateDescriptor() {
    }

    @Override // com.android.internal.telephony.cat.ValueObject
    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.ACTIVATE_DESCRIPTOR;
    }
}
