package com.android.internal.telephony.uicc;

import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

/* JADX INFO: loaded from: classes.dex */
public final class RuimFileHandler extends IccFileHandler {
    static final String LOG_TAG = "RuimFH";

    public RuimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    public void loadEFImgTransparent(int fileid, int highOffset, int lowOffset, int length, Message onLoaded) {
        Message response = obtainMessage(10, fileid, 0, onLoaded);
        this.mCi.iccIOForApp(192, fileid, getEFPath(20256), 0, 0, 10, null, null, this.mAid, response);
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected String getEFPath(int efid) {
        switch (efid) {
            case 20256:
            case IccConstants.EF_CSIM_MSPL /* 20257 */:
                return "3F007F105F3C";
            case IccConstants.EF_CSIM_IMSIM /* 28450 */:
            case IccConstants.EF_CSIM_CDMAHOME /* 28456 */:
            case IccConstants.EF_CSIM_PRL /* 28464 */:
            case IccConstants.EF_RUIM_ID /* 28465 */:
            case IccConstants.EF_CST /* 28466 */:
            case 28474:
            case IccConstants.EF_SMS /* 28476 */:
            case 28481:
            case IccConstants.EF_CSIM_MDN /* 28484 */:
            case IccConstants.EF_CSIM_EPRL /* 28506 */:
                return "3F007F25";
            default:
                return getCommonIccEFPath(efid);
        }
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, "[RuimFileHandler] " + msg);
    }

    @Override // com.android.internal.telephony.uicc.IccFileHandler
    protected void loge(String msg) {
        Rlog.e(LOG_TAG, "[RuimFileHandler] " + msg);
    }
}
