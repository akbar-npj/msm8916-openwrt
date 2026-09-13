package com.android.phone;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.ServiceState;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.google.common.base.Preconditions;

/* JADX INFO: loaded from: classes.dex */
public class HfaLogic {
    private static final String TAG = HfaLogic.class.getSimpleName();
    private HfaLogicCallback mCallback;
    private Context mContext;
    private BroadcastReceiver mReceiver;
    private PendingIntent mResponseIntent;
    private int mRetryCount;
    private int mPhoneMonitorState = 0;
    private Handler mHandler = new Handler() { // from class: com.android.phone.HfaLogic.2
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    ServiceState state = (ServiceState) ((AsyncResult) msg.obj).result;
                    HfaLogic.this.onServiceStateChange(state);
                    break;
            }
        }
    };

    public interface HfaLogicCallback {
        void onError(String str);

        void onSuccess();
    }

    public HfaLogic(Context context, HfaLogicCallback callback, PendingIntent intent) {
        this.mCallback = (HfaLogicCallback) Preconditions.checkNotNull(callback);
        this.mContext = (Context) Preconditions.checkNotNull(context);
        this.mResponseIntent = intent;
    }

    public void start() {
        Log.i(TAG, "start:");
        this.mRetryCount = 1;
        startHfaIntentReceiver();
        startProvisioning();
    }

    private void startProvisioning() {
        Log.i(TAG, "startProvisioning:");
        sendHfaCommand("com.android.action.START_HFA");
    }

    private void sendHfaCommand(String action) {
        Log.i(TAG, "sendHfaCommand: command=" + action);
        this.mContext.sendBroadcast(new Intent(action));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onHfaError(String errorMsg) {
        Log.i(TAG, "onHfaError: call mCallBack.onError errorMsg=" + errorMsg + " mRetryCount=" + this.mRetryCount);
        this.mRetryCount--;
        if (this.mRetryCount >= 0) {
            Log.i(TAG, "onHfaError: retry");
            startProvisioning();
            return;
        }
        Log.i(TAG, "onHfaError: Declare OTASP_FAILURE");
        this.mRetryCount = 0;
        stopHfaIntentReceiver();
        sendFinalResponse(3, errorMsg);
        this.mCallback.onError(errorMsg);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onHfaSuccess() {
        Log.i(TAG, "onHfaSuccess: NOT bouncing radio call onTotalSuccess");
        stopHfaIntentReceiver();
        onTotalSuccess();
    }

    private void onTotalSuccess() {
        Log.i(TAG, "onTotalSuccess: call mCallBack.onSuccess");
        sendFinalResponse(2, null);
        this.mCallback.onSuccess();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onServiceStateChange(ServiceState state) {
        boolean radioIsOff = state.getVoiceRegState() == 3;
        PhoneGlobals.getInstance();
        Phone phone = PhoneGlobals.getPhone();
        Log.i(TAG, "Radio is on: " + (!radioIsOff));
        if (this.mPhoneMonitorState == 1) {
            if (radioIsOff) {
                this.mPhoneMonitorState = 2;
                phone.setRadioPower(true);
                return;
            }
            return;
        }
        if (this.mPhoneMonitorState == 2 && !radioIsOff) {
            this.mPhoneMonitorState = 0;
            phone.unregisterForServiceStateChanged(this.mHandler);
            onTotalSuccess();
        }
    }

    private void startHfaIntentReceiver() {
        IntentFilter filter = new IntentFilter("com.android.action.COMPLETE_HFA");
        filter.addAction("com.android.action.ERROR_HFA");
        this.mReceiver = new BroadcastReceiver() { // from class: com.android.phone.HfaLogic.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals("com.android.action.ERROR_HFA")) {
                    HfaLogic.this.onHfaError(intent.getStringExtra("errorCode"));
                } else if (action.equals("com.android.action.COMPLETE_HFA")) {
                    Log.i(HfaLogic.TAG, "Hfa Successful");
                    HfaLogic.this.onHfaSuccess();
                }
            }
        };
        this.mContext.registerReceiver(this.mReceiver, filter);
    }

    private void stopHfaIntentReceiver() {
        if (this.mReceiver != null) {
            this.mContext.unregisterReceiver(this.mReceiver);
            this.mReceiver = null;
        }
    }

    private void sendFinalResponse(int responseCode, String errorCode) {
        if (this.mResponseIntent != null) {
            Intent extraStuff = new Intent();
            extraStuff.putExtra("otasp_result_code", responseCode);
            if (responseCode == 3 && errorCode != null) {
                extraStuff.putExtra("otasp_error_code", errorCode);
            }
            try {
                Log.i(TAG, "Sending OTASP confirmation with result code: " + responseCode);
                this.mResponseIntent.send(this.mContext, 0, extraStuff);
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "Pending Intent canceled");
            }
        }
    }
}
