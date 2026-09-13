package com.android.internal.telephony.cat;

/* JADX INFO: compiled from: CommandDetails.java */
/* JADX INFO: loaded from: classes.dex */
class DeviceIdentities extends ValueObject {
    public int destinationId;
    public int sourceId;

    DeviceIdentities() {
    }

    @Override // com.android.internal.telephony.cat.ValueObject
    ComprehensionTlvTag getTag() {
        return ComprehensionTlvTag.DEVICE_IDENTITIES;
    }
}
