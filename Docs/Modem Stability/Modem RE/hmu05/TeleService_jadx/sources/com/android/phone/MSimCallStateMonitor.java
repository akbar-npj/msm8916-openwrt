package com.android.phone;

import android.os.SystemProperties;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
class MSimCallStateMonitor extends CallStateMonitor {
    private static final boolean DBG;
    private static final String LOG_TAG = MSimCallStateMonitor.class.getSimpleName();

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    public MSimCallStateMonitor(CallManager callManager) {
        super(callManager);
    }

    @Override // com.android.phone.CallStateMonitor
    protected void registerForNotifications() {
        super.registerForNotifications();
        for (Phone phone : this.callManager.getAllPhones()) {
            if (phone.getPhoneType() == 2) {
                Log.d(LOG_TAG, "register for cdma call waiting " + phone.getSubscription());
                this.callManager.registerForCallWaiting(this, 8, Integer.valueOf(phone.getSubscription()));
                break;
            }
        }
        this.callManager.registerForSubscriptionChange(this, 15, (Object) null);
    }

    @Override // com.android.phone.CallStateMonitor
    public void updateAfterRadioTechnologyChange() {
        if (DBG) {
            Log.d(LOG_TAG, "updateCallNotifierRegistrationsAfterRadioTechnologyChange...");
        }
        this.callManager.unregisterForSubscriptionChange(this);
        super.updateAfterRadioTechnologyChange();
    }
}
