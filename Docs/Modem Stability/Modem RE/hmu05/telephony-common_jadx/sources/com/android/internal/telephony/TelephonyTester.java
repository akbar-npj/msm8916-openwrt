package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.Rlog;

/* JADX INFO: loaded from: classes.dex */
public class TelephonyTester {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "TelephonyTester";
    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.TelephonyTester.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            TelephonyTester.log("sIntentReceiver.onReceive: action=" + action);
            if (action.equals(TelephonyTester.this.mPhone.getActionDetached())) {
                TelephonyTester.log("simulate detaching");
                TelephonyTester.this.mPhone.getServiceStateTracker().mDetachedRegistrants.notifyRegistrants();
            } else if (action.equals(TelephonyTester.this.mPhone.getActionAttached())) {
                TelephonyTester.log("simulate attaching");
                TelephonyTester.this.mPhone.getServiceStateTracker().mAttachedRegistrants.notifyRegistrants();
            } else {
                TelephonyTester.log("onReceive: unknown action=" + action);
            }
        }
    };
    private PhoneBase mPhone;

    TelephonyTester(PhoneBase phone) {
        this.mPhone = phone;
        if (Build.IS_DEBUGGABLE) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(this.mPhone.getActionDetached());
            log("register for intent action=" + this.mPhone.getActionDetached());
            filter.addAction(this.mPhone.getActionAttached());
            log("register for intent action=" + this.mPhone.getActionAttached());
            phone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone.getHandler());
        }
    }

    void dispose() {
        if (Build.IS_DEBUGGABLE) {
            this.mPhone.getContext().unregisterReceiver(this.mIntentReceiver);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
