package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyCapabilities;

/* JADX INFO: loaded from: classes.dex */
public class OtaStartupReceiver extends BroadcastReceiver {
    private Context mContext;
    private int mOtaspMode = -1;
    private boolean mPhoneStateListenerRegistered = false;
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() { // from class: com.android.phone.OtaStartupReceiver.1
        public void onOtaspChanged(int otaspMode) {
            OtaStartupReceiver.this.mOtaspMode = otaspMode;
            Log.v("OtaStartupReceiver", "onOtaspChanged: mOtaspMode=" + OtaStartupReceiver.this.mOtaspMode);
        }
    };
    private Handler mHandler = new Handler() { // from class: com.android.phone.OtaStartupReceiver.2
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 10:
                    Log.v("OtaStartupReceiver", "Attempting OtaActivation from handler, mOtaspMode=" + OtaStartupReceiver.this.mOtaspMode);
                    OtaUtils.maybeDoOtaCall(OtaStartupReceiver.this.mContext, OtaStartupReceiver.this.mHandler, 10);
                    break;
                case 11:
                    ServiceState state = (ServiceState) ((AsyncResult) msg.obj).result;
                    if (state.getState() == 0) {
                        Phone phone = PhoneGlobals.getPhone();
                        phone.unregisterForServiceStateChanged(this);
                        OtaUtils.maybeDoOtaCall(OtaStartupReceiver.this.mContext, OtaStartupReceiver.this.mHandler, 10);
                    }
                    break;
            }
        }
    };

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        this.mContext = context;
        PhoneGlobals globals = PhoneGlobals.getInstanceIfPrimary();
        if (globals != null && TelephonyCapabilities.supportsOtasp(PhoneGlobals.getPhone())) {
            if (!this.mPhoneStateListenerRegistered) {
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
                telephonyManager.listen(this.mPhoneStateListener, 512);
                this.mPhoneStateListenerRegistered = true;
            }
            if (!shouldPostpone(context)) {
                PhoneGlobals app = PhoneGlobals.getInstance();
                Phone phone = PhoneGlobals.getPhone();
                if (app.mCM.getServiceState() != 0) {
                    phone.registerForServiceStateChanged(this.mHandler, 11, (Object) null);
                } else {
                    OtaUtils.maybeDoOtaCall(this.mContext, this.mHandler, 10);
                }
            }
        }
    }

    private boolean shouldPostpone(Context context) {
        Intent intent = new Intent("android.intent.action.DEVICE_INITIALIZATION_WIZARD");
        ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, 65536);
        boolean provisioned = Settings.Global.getInt(context.getContentResolver(), "device_provisioned", 0) != 0;
        String mode = SystemProperties.get("ro.setupwizard.mode", "REQUIRED");
        boolean runningSetupWizard = "REQUIRED".equals(mode) || "OPTIONAL".equals(mode);
        return (resolveInfo == null || provisioned || !runningSetupWizard) ? false : true;
    }
}
