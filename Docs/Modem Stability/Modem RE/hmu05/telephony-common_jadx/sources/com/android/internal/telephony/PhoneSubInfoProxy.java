package com.android.internal.telephony;

import android.os.ServiceManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes.dex */
public class PhoneSubInfoProxy extends IPhoneSubInfo.Stub {
    private PhoneSubInfo mPhoneSubInfo;

    /* JADX WARN: Multi-variable type inference failed */
    public PhoneSubInfoProxy(PhoneSubInfo phoneSubInfo) {
        this.mPhoneSubInfo = phoneSubInfo;
        if (ServiceManager.getService("iphonesubinfo") == null) {
            ServiceManager.addService("iphonesubinfo", this);
        }
    }

    public void setmPhoneSubInfo(PhoneSubInfo phoneSubInfo) {
        this.mPhoneSubInfo = phoneSubInfo;
    }

    public String getDeviceId() {
        return this.mPhoneSubInfo.getDeviceId();
    }

    public String getDeviceSvn() {
        return this.mPhoneSubInfo.getDeviceSvn();
    }

    public String getSubscriberId() {
        return this.mPhoneSubInfo.getSubscriberId();
    }

    public String getGroupIdLevel1() {
        return this.mPhoneSubInfo.getGroupIdLevel1();
    }

    public String getIccSerialNumber() {
        return this.mPhoneSubInfo.getIccSerialNumber();
    }

    public String getLine1Number() {
        return this.mPhoneSubInfo.getLine1Number();
    }

    public String getLine1AlphaTag() {
        return this.mPhoneSubInfo.getLine1AlphaTag();
    }

    public String getMsisdn() {
        return this.mPhoneSubInfo.getMsisdn();
    }

    public String getVoiceMailNumber() {
        return this.mPhoneSubInfo.getVoiceMailNumber();
    }

    public String getCompleteVoiceMailNumber() {
        return this.mPhoneSubInfo.getCompleteVoiceMailNumber();
    }

    public String getVoiceMailAlphaTag() {
        return this.mPhoneSubInfo.getVoiceMailAlphaTag();
    }

    public String getIsimImpi() {
        return this.mPhoneSubInfo.getIsimImpi();
    }

    public String getIsimDomain() {
        return this.mPhoneSubInfo.getIsimDomain();
    }

    public String[] getIsimImpu() {
        return this.mPhoneSubInfo.getIsimImpu();
    }

    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mPhoneSubInfo.dump(fd, pw, args);
    }
}
