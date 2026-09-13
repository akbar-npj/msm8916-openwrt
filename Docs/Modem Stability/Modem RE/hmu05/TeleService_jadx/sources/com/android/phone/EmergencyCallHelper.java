package com.android.phone;

import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

/* JADX INFO: loaded from: classes.dex */
public class EmergencyCallHelper extends Handler {
    private PhoneGlobals mApp = PhoneGlobals.getInstance();
    private CallManager mCM = this.mApp.mCM;
    private CallController mCallController;
    private int mNumRetriesSoFar;
    private String mNumber;
    private PowerManager.WakeLock mPartialWakeLock;

    public EmergencyCallHelper(CallController callController) {
        this.mCallController = callController;
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                startSequenceInternal(msg);
                break;
            case 2:
                onServiceStateChanged(msg);
                break;
            case 3:
                onDisconnect(msg);
                break;
            case 4:
                onRetryTimeout();
                break;
            default:
                Log.wtf("EmergencyCallHelper", "handleMessage: unexpected message: " + msg);
                break;
        }
    }

    public void startEmergencyCallFromAirplaneModeSequence(String number) {
        Message msg = obtainMessage(1, number);
        sendMessage(msg);
    }

    private void startSequenceInternal(Message msg) {
        cleanup();
        this.mNumber = (String) msg.obj;
        this.mNumRetriesSoFar = 0;
        PowerManager pm = (PowerManager) this.mApp.getSystemService("power");
        this.mPartialWakeLock = pm.newWakeLock(1, "EmergencyCallHelper");
        this.mPartialWakeLock.acquire(300000L);
        powerOnRadio();
        startRetryTimer();
    }

    private void onServiceStateChanged(Message msg) {
        ServiceState state = (ServiceState) ((AsyncResult) msg.obj).result;
        boolean okToCall = state.getState() != 3;
        if (okToCall) {
            unregisterForServiceStateChanged();
            placeEmergencyCall();
        }
    }

    private void onDisconnect(Message msg) {
        Connection conn = (Connection) ((AsyncResult) msg.obj).result;
        Connection.DisconnectCause cause = conn.getDisconnectCause();
        if (cause == Connection.DisconnectCause.OUT_OF_SERVICE) {
            scheduleRetryOrBailOut();
        } else {
            cleanup();
        }
    }

    private void onRetryTimeout() {
        PhoneConstants.State phoneState = this.mCM.getState();
        int serviceState = this.mCM.getDefaultPhone().getServiceState().getState();
        if (phoneState == PhoneConstants.State.OFFHOOK) {
            cleanup();
        } else if (serviceState != 3) {
            unregisterForServiceStateChanged();
            placeEmergencyCall();
        } else {
            powerOnRadio();
            scheduleRetryOrBailOut();
        }
    }

    private void powerOnRadio() {
        registerForServiceStateChanged();
        if (Settings.Global.getInt(this.mApp.getContentResolver(), "airplane_mode_on", 0) > 0) {
            Settings.Global.putInt(this.mApp.getContentResolver(), "airplane_mode_on", 0);
            Intent intent = new Intent("android.intent.action.AIRPLANE_MODE");
            intent.putExtra("state", false);
            this.mApp.sendBroadcastAsUser(intent, UserHandle.ALL);
            return;
        }
        this.mCM.getDefaultPhone().setRadioPower(true);
    }

    private void placeEmergencyCall() {
        boolean success;
        registerForDisconnect();
        int sub = this.mApp.getVoiceSubscriptionInService();
        Phone phone = this.mApp.getPhone(sub);
        int callStatus = PhoneUtils.placeCall(this.mApp, phone, this.mNumber, null, true);
        switch (callStatus) {
            case 0:
                success = true;
                break;
            default:
                Log.w("EmergencyCallHelper", "placeEmergencyCall(): placeCall() failed: callStatus = " + callStatus);
                success = false;
                break;
        }
        if (!success) {
            scheduleRetryOrBailOut();
        }
    }

    private void scheduleRetryOrBailOut() {
        this.mNumRetriesSoFar++;
        if (this.mNumRetriesSoFar > 6) {
            Log.w("EmergencyCallHelper", "scheduleRetryOrBailOut: hit MAX_NUM_RETRIES; giving up...");
            cleanup();
        } else {
            startRetryTimer();
        }
    }

    private void cleanup() {
        unregisterForServiceStateChanged();
        unregisterForDisconnect();
        cancelRetryTimer();
        if (this.mPartialWakeLock != null) {
            if (this.mPartialWakeLock.isHeld()) {
                this.mPartialWakeLock.release();
            }
            this.mPartialWakeLock = null;
        }
    }

    private void startRetryTimer() {
        removeMessages(4);
        sendEmptyMessageDelayed(4, 5000L);
    }

    private void cancelRetryTimer() {
        removeMessages(4);
    }

    private void registerForServiceStateChanged() {
        Phone phone = this.mCM.getDefaultPhone();
        phone.unregisterForServiceStateChanged(this);
        phone.registerForServiceStateChanged(this, 2, (Object) null);
    }

    private void unregisterForServiceStateChanged() {
        Phone phone = this.mCM.getDefaultPhone();
        if (phone != null) {
            phone.unregisterForServiceStateChanged(this);
        }
        removeMessages(2);
    }

    private void registerForDisconnect() {
        this.mCM.registerForDisconnect(this, 3, (Object) null);
    }

    private void unregisterForDisconnect() {
        this.mCM.unregisterForDisconnect(this);
        removeMessages(3);
    }
}
