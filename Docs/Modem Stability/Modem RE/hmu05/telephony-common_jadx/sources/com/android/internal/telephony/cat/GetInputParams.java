package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* JADX INFO: compiled from: CommandParams.java */
/* JADX INFO: loaded from: classes.dex */
class GetInputParams extends CommandParams {
    Input mInput;

    GetInputParams(CommandDetails cmdDet, Input input) {
        super(cmdDet);
        this.mInput = null;
        this.mInput = input;
    }

    @Override // com.android.internal.telephony.cat.CommandParams
    boolean setIcon(Bitmap icon) {
        if (icon != null && this.mInput != null) {
            this.mInput.icon = icon;
            return true;
        }
        return true;
    }
}
