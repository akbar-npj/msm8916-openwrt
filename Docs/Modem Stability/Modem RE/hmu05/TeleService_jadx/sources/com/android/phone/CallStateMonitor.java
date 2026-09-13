package com.android.phone;

import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
class CallStateMonitor extends Handler {
    private static final boolean DBG;
    private static final String LOG_TAG = CallStateMonitor.class.getSimpleName();
    protected CallManager callManager;
    private ArrayList<Handler> registeredHandlers = new ArrayList<>();

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    public CallStateMonitor(CallManager callManager) {
        this.callManager = callManager;
        registerForNotifications();
    }

    protected void registerForNotifications() {
        this.callManager.registerForNewRingingConnection(this, 2, (Object) null);
        this.callManager.registerForPreciseCallStateChanged(this, 1, (Object) null);
        this.callManager.registerForDisconnect(this, 3, (Object) null);
        this.callManager.registerForUnknownConnection(this, 4, (Object) null);
        this.callManager.registerForIncomingRing(this, 5, (Object) null);
        this.callManager.registerForCdmaOtaStatusChange(this, 20, (Object) null);
        this.callManager.registerForCallWaiting(this, 8, (Object) null);
        this.callManager.registerForDisplayInfo(this, 6, (Object) null);
        this.callManager.registerForSignalInfo(this, 7, (Object) null);
        this.callManager.registerForInCallVoicePrivacyOn(this, 9, (Object) null);
        this.callManager.registerForInCallVoicePrivacyOff(this, 10, (Object) null);
        this.callManager.registerForRingbackTone(this, 11, (Object) null);
        this.callManager.registerForResendIncallMute(this, 12, (Object) null);
        this.callManager.registerForPostDialCharacter(this, 13, (Object) null);
        this.callManager.registerForSuppServiceNotification(this, 14, (Object) null);
        this.callManager.registerForCallModify(this, 16, (Object) null);
        this.callManager.registerForSuppServiceFailed(this, 17, (Object) null);
    }

    public void addListener(Handler handler) {
        if (handler != null && !this.registeredHandlers.contains(handler)) {
            if (DBG) {
                Log.d(LOG_TAG, "Adding Handler: " + handler);
            }
            this.registeredHandlers.add(handler);
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        if (DBG) {
            Log.d(LOG_TAG, "handleMessage(" + msg.what + ")");
        }
        for (Handler handler : this.registeredHandlers) {
            handler.handleMessage(msg);
        }
    }

    public void updateAfterRadioTechnologyChange() {
        if (DBG) {
            Log.d(LOG_TAG, "updateCallNotifierRegistrationsAfterRadioTechnologyChange...");
        }
        this.callManager.unregisterForNewRingingConnection(this);
        this.callManager.unregisterForPreciseCallStateChanged(this);
        this.callManager.unregisterForDisconnect(this);
        this.callManager.unregisterForUnknownConnection(this);
        this.callManager.unregisterForIncomingRing(this);
        this.callManager.unregisterForCallWaiting(this);
        this.callManager.unregisterForDisplayInfo(this);
        this.callManager.unregisterForSignalInfo(this);
        this.callManager.unregisterForCdmaOtaStatusChange(this);
        this.callManager.unregisterForRingbackTone(this);
        this.callManager.unregisterForResendIncallMute(this);
        this.callManager.unregisterForInCallVoicePrivacyOn(this);
        this.callManager.unregisterForInCallVoicePrivacyOff(this);
        this.callManager.unregisterForPostDialCharacter(this);
        this.callManager.unregisterForSuppServiceNotification(this);
        this.callManager.unregisterForCallModify(this);
        registerForNotifications();
    }
}
