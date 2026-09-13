package com.android.internal.telephony.cat;

/* JADX INFO: compiled from: CommandParams.java */
/* JADX INFO: loaded from: classes.dex */
class SetEventListParams extends CommandParams {
    int[] mEventInfo;

    SetEventListParams(CommandDetails cmdDet, int[] eventInfo) {
        super(cmdDet);
        this.mEventInfo = eventInfo;
    }
}
