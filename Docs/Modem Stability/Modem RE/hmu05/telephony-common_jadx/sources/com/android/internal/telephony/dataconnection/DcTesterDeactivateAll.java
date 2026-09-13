package com.android.internal.telephony.dataconnection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.telephony.Rlog;
import com.android.internal.telephony.PhoneBase;

/* JADX INFO: loaded from: classes.dex */
public class DcTesterDeactivateAll {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "DcTesterDeacativeAll";
    public static String sActionDcTesterDeactivateAll = "com.android.internal.telephony.dataconnection.action_deactivate_all";
    private DcController mDcc;
    private PhoneBase mPhone;
    protected BroadcastReceiver sIntentReceiver = new BroadcastReceiver() { // from class: com.android.internal.telephony.dataconnection.DcTesterDeactivateAll.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            DcTesterDeactivateAll.log("sIntentReceiver.onReceive: action=" + action);
            if (action.equals(DcTesterDeactivateAll.sActionDcTesterDeactivateAll) || action.equals(DcTesterDeactivateAll.this.mPhone.getActionDetached())) {
                DcTesterDeactivateAll.log("Send DEACTIVATE to all Dcc's");
                if (DcTesterDeactivateAll.this.mDcc != null) {
                    for (DataConnection dc : DcTesterDeactivateAll.this.mDcc.mDcListAll) {
                        dc.tearDownNow();
                    }
                    return;
                }
                DcTesterDeactivateAll.log("onReceive: mDcc is null, ignoring");
                return;
            }
            DcTesterDeactivateAll.log("onReceive: unknown action=" + action);
        }
    };

    DcTesterDeactivateAll(PhoneBase phone, DcController dcc, Handler handler) {
        this.mPhone = phone;
        this.mDcc = dcc;
        if (Build.IS_DEBUGGABLE) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(sActionDcTesterDeactivateAll);
            log("register for intent action=" + sActionDcTesterDeactivateAll);
            filter.addAction(this.mPhone.getActionDetached());
            log("register for intent action=" + this.mPhone.getActionDetached());
            phone.getContext().registerReceiver(this.sIntentReceiver, filter, null, handler);
        }
    }

    void dispose() {
        if (Build.IS_DEBUGGABLE) {
            this.mPhone.getContext().unregisterReceiver(this.sIntentReceiver);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
