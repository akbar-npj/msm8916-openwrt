package com.android.internal.telephony.uicc;

import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;

/* JADX INFO: loaded from: classes.dex */
public final class CsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "CsimFH";

    public CsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
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
            case IccConstants.EF_FDN /* 28475 */:
            case IccConstants.EF_SMS /* 28476 */:
            case IccConstants.EF_MSISDN /* 28480 */:
            case 28481:
            case IccConstants.EF_CSIM_MDN /* 28484 */:
            case IccConstants.EF_CSIM_EPRL /* 28506 */:
                return "3F007FFF";
            default:
                String path = getCommonIccEFPath(efid);
                if (path == null) {
                    if (this.mUseLocalPb) {
                        return "3F007FFF5F3A";
                    }
                    return "3F007F105F3A";
                }
                return path;
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
