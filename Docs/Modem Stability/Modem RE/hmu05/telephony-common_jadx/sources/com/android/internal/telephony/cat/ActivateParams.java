package com.android.internal.telephony.cat;

/* JADX INFO: compiled from: CommandParams.java */
/* JADX INFO: loaded from: classes.dex */
class ActivateParams extends CommandParams {
    int mActivateTarget;

    ActivateParams(CommandDetails cmdDet, int target) {
        super(cmdDet);
        this.mActivateTarget = target;
    }
}
