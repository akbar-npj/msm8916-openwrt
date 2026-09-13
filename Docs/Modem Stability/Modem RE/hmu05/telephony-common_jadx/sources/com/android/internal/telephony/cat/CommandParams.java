package com.android.internal.telephony.cat;

import android.graphics.Bitmap;

/* JADX INFO: loaded from: classes.dex */
class CommandParams {
    CommandDetails mCmdDet;
    boolean mLoadIconFailed = false;

    CommandParams(CommandDetails cmdDet) {
        this.mCmdDet = cmdDet;
    }

    AppInterface.CommandType getCommandType() {
        return AppInterface.CommandType.fromInt(this.mCmdDet.typeOfCommand);
    }

    boolean setIcon(Bitmap icon) {
        return true;
    }

    public String toString() {
        return this.mCmdDet.toString();
    }
}
