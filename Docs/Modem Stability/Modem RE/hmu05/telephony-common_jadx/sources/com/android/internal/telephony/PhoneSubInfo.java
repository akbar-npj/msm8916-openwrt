package com.android.internal.telephony;

import android.content.Context;
import android.os.Binder;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import com.android.internal.telephony.uicc.IsimRecords;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes.dex */
public class PhoneSubInfo extends IPhoneSubInfo.Stub {
    private static final String CALL_PRIVILEGED = "android.permission.CALL_PRIVILEGED";
    private static final boolean DBG = true;
    static final String LOG_TAG = "PhoneSubInfo";
    private static final String READ_PHONE_STATE = "android.permission.READ_PHONE_STATE";
    private static final String READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE";
    private static final boolean VDBG = false;
    private Context mContext;
    private Phone mPhone;

    public PhoneSubInfo(Phone phone) {
        this.mPhone = phone;
        this.mContext = phone.getContext();
    }

    public void dispose() {
    }

    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            loge("Error while finalizing:", throwable);
        }
        log("PhoneSubInfo finalized");
    }

    public String getDeviceId() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getDeviceId();
    }

    public String getDeviceSvn() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getDeviceSvn();
    }

    public String getSubscriberId() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getSubscriberId();
    }

    public String getGroupIdLevel1() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getGroupIdLevel1();
    }

    public String getIccSerialNumber() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getIccSerialNumber();
    }

    public String getLine1Number() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getLine1Number();
    }

    public String getLine1AlphaTag() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getLine1AlphaTag();
    }

    public String getMsisdn() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getMsisdn();
    }

    public String getVoiceMailNumber() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        String number = PhoneNumberUtils.extractNetworkPortion(this.mPhone.getVoiceMailNumber());
        return number;
    }

    public String getCompleteVoiceMailNumber() {
        this.mContext.enforceCallingOrSelfPermission(CALL_PRIVILEGED, "Requires CALL_PRIVILEGED");
        String number = this.mPhone.getVoiceMailNumber();
        return number;
    }

    public String getVoiceMailAlphaTag() {
        this.mContext.enforceCallingOrSelfPermission(READ_PHONE_STATE, "Requires READ_PHONE_STATE");
        return this.mPhone.getVoiceMailAlphaTag();
    }

    public String getIsimImpi() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimImpi();
        }
        return null;
    }

    public String getIsimDomain() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimDomain();
        }
        return null;
    }

    public String[] getIsimImpu() {
        this.mContext.enforceCallingOrSelfPermission(READ_PRIVILEGED_PHONE_STATE, "Requires READ_PRIVILEGED_PHONE_STATE");
        IsimRecords isim = this.mPhone.getIsimRecords();
        if (isim != null) {
            return isim.getIsimImpu();
        }
        return null;
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    private void loge(String s, Throwable e) {
        Rlog.e(LOG_TAG, s, e);
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump PhoneSubInfo from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("Phone Subscriber Info:");
        pw.println("  Phone Type = " + this.mPhone.getPhoneName());
        pw.println("  Device ID = " + this.mPhone.getDeviceId());
    }
}
