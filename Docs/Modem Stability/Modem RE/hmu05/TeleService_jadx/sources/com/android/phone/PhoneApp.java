package com.android.phone;

import android.app.Application;
import android.os.UserHandle;
import android.telephony.MSimTelephonyManager;

/* JADX INFO: loaded from: classes.dex */
public class PhoneApp extends Application {
    PhoneGlobals mPhoneGlobals;

    @Override // android.app.Application
    public void onCreate() {
        if (UserHandle.myUserId() == 0) {
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                this.mPhoneGlobals = new MSimPhoneGlobals(this);
            } else {
                this.mPhoneGlobals = new PhoneGlobals(this);
            }
            this.mPhoneGlobals.onCreate();
        }
    }
}
