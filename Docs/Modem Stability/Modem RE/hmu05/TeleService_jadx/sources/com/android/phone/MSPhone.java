package com.android.phone;

import android.os.SystemProperties;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.codeaurora.telephony.msim.MSimPhoneFactory;

/* JADX INFO: loaded from: classes.dex */
public class MSPhone {
    private static final boolean DBG;
    public boolean mIsSimPinEnabled;
    public Phone mPhone;
    public PhoneConstants.State mLastPhoneState = PhoneConstants.State.IDLE;
    public CdmaPhoneCallState mCdmaPhoneCallState = null;
    public OtaUtils.CdmaOtaProvisionData mCdmaOtaProvisionData = null;
    public OtaUtils.CdmaOtaConfigData mCdmaOtaConfigData = null;
    public OtaUtils.CdmaOtaScreenState mCdmaOtaScreenState = null;
    public OtaUtils.CdmaOtaInCallScreenUiState mCdmaOtaInCallScreenUiState = null;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    MSPhone(int subscription) {
        this.mPhone = MSimPhoneFactory.getPhone(subscription);
        if (this.mPhone.getPhoneType() == 2) {
        }
        if (this.mPhone.getPhoneType() == 2) {
            initializeCdmaVariables();
        }
    }

    public void initializeCdmaVariables() {
        if (this.mPhone.getPhoneType() == 2) {
            this.mCdmaPhoneCallState = new CdmaPhoneCallState();
            this.mCdmaPhoneCallState.CdmaPhoneCallStateInit();
            if (this.mCdmaOtaProvisionData == null) {
                this.mCdmaOtaProvisionData = new OtaUtils.CdmaOtaProvisionData();
            }
            if (this.mCdmaOtaConfigData == null) {
                this.mCdmaOtaConfigData = new OtaUtils.CdmaOtaConfigData();
            }
            if (this.mCdmaOtaScreenState == null) {
                this.mCdmaOtaScreenState = new OtaUtils.CdmaOtaScreenState();
            }
            if (this.mCdmaOtaInCallScreenUiState == null) {
                this.mCdmaOtaInCallScreenUiState = new OtaUtils.CdmaOtaInCallScreenUiState();
            }
        }
    }
}
