package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* JADX INFO: compiled from: CommandParams.java */
/* JADX INFO: loaded from: classes.dex */
class CallSetupParams extends CommandParams {
    TextMessage mCallMsg;
    TextMessage mConfirmMsg;

    CallSetupParams(CommandDetails cmdDet, TextMessage confirmMsg, TextMessage callMsg) {
        super(cmdDet);
        this.mConfirmMsg = confirmMsg;
        this.mCallMsg = callMsg;
    }

    @Override // com.android.internal.telephony.cat.CommandParams
    boolean setIcon(Bitmap icon) {
        if (icon == null) {
            return false;
        }
        if (this.mConfirmMsg != null && this.mConfirmMsg.icon == null) {
            this.mConfirmMsg.icon = icon;
            return true;
        }
        if (this.mCallMsg == null || this.mCallMsg.icon != null) {
            return false;
        }
        this.mCallMsg.icon = icon;
        return true;
    }
}
