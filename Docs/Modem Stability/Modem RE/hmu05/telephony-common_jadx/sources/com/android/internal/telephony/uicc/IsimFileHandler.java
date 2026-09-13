package com.android.internal.telephony.uicc;

import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

/* JADX INFO: loaded from: classes.dex */
public final class IsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "IsimFH";

    public IsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected String getEFPath(int efid) {
        switch (efid) {
            case IccConstants.EF_IMPI /* 28418 */:
            case IccConstants.EF_DOMAIN /* 28419 */:
            case IccConstants.EF_IMPU /* 28420 */:
                return "3F007FFF";
            default:
                return getCommonIccEFPath(efid);
        }
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }
}
