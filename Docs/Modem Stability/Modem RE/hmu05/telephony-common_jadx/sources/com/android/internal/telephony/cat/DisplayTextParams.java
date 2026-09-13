package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* JADX INFO: compiled from: CommandParams.java */
/* JADX INFO: loaded from: classes.dex */
class DisplayTextParams extends CommandParams {
    TextMessage mTextMsg;

    DisplayTextParams(CommandDetails cmdDet, TextMessage textMsg) {
        super(cmdDet);
        this.mTextMsg = textMsg;
    }

    @Override // com.android.internal.telephony.cat.CommandParams
    boolean setIcon(Bitmap icon) {
        if (icon == null || this.mTextMsg == null) {
            return false;
        }
        this.mTextMsg.icon = icon;
        return true;
    }
}
