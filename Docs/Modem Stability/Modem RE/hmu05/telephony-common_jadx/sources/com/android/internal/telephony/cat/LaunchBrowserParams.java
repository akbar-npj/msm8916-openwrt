package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* JADX INFO: compiled from: CommandParams.java */
/* JADX INFO: loaded from: classes.dex */
class LaunchBrowserParams extends CommandParams {
    TextMessage mConfirmMsg;
    LaunchBrowserMode mMode;
    String mUrl;

    LaunchBrowserParams(CommandDetails cmdDet, TextMessage confirmMsg, String url, LaunchBrowserMode mode) {
        super(cmdDet);
        this.mConfirmMsg = confirmMsg;
        this.mMode = mode;
        this.mUrl = url;
    }

    @Override // com.android.internal.telephony.cat.CommandParams
    boolean setIcon(Bitmap icon) {
        if (icon == null || this.mConfirmMsg == null) {
            return false;
        }
        this.mConfirmMsg.icon = icon;
        return true;
    }
}
