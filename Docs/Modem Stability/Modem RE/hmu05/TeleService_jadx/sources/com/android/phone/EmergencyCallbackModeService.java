package com.android.phone;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/* JADX INFO: loaded from: classes.dex */
public class EmergencyCallbackModeService extends Service {
    private NotificationManager mNotificationManager = null;
    private CountDownTimer mTimer = null;
    private long mTimeLeft = 0;
    private Phone mPhone = null;
    private boolean mInEmergencyCall = false;
    private boolean mIsImsPhone = false;
    private int mSubscription = 0;
    private Handler mHandler = new Handler() { // from class: com.android.phone.EmergencyCallbackModeService.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    EmergencyCallbackModeService.this.resetEcmTimer((AsyncResult) msg.obj);
                    break;
            }
        }
    };
    private BroadcastReceiver mEcmReceiver = new BroadcastReceiver() { // from class: com.android.phone.EmergencyCallbackModeService.2
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED")) {
                if (!intent.getBooleanExtra("phoneinECMState", false)) {
                    EmergencyCallbackModeService.this.stopSelf();
                }
            } else if (intent.getAction().equals("android.intent.action.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS")) {
                context.startActivity(new Intent("android.intent.action.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS").setFlags(268435456));
            }
        }
    };
    private final IBinder mBinder = new LocalBinder();

    @Override // android.app.Service
    public void onCreate() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        filter.addAction("android.intent.action.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS");
        registerReceiver(this.mEcmReceiver, filter);
        this.mNotificationManager = (NotificationManager) getSystemService("notification");
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        PhoneGlobals app = PhoneGlobals.getInstance();
        if (intent != null) {
            this.mIsImsPhone = intent.getBooleanExtra("ims_phone", false);
            this.mSubscription = intent.getIntExtra("subscription", app.getDefaultSubscription());
        } else {
            Log.e("EmergencyCallbackModeService", "onStartCommand: intent null");
        }
        if (this.mIsImsPhone) {
            this.mPhone = PhoneUtils.getImsPhone(PhoneGlobals.getInstance().mCM);
        } else {
            this.mPhone = app.getPhone(this.mSubscription);
        }
        if (this.mPhone.getPhoneType() == 1) {
            Log.e("EmergencyCallbackModeService", "Error! Emergency Callback Mode not supported for " + PhoneFactory.getDefaultPhone().getPhoneName() + " phones");
            stopSelf();
        }
        this.mPhone.registerForEcmTimerReset(this.mHandler, 1, (Object) null);
        startTimerNotification();
        return 1;
    }

    @Override // android.app.Service
    public void onDestroy() {
        unregisterReceiver(this.mEcmReceiver);
        if (this.mPhone != null) {
            this.mPhone.unregisterForEcmTimerReset(this.mHandler);
        }
        this.mNotificationManager.cancel(R.string.phone_in_ecm_notification_title);
        if (this.mTimer != null) {
            this.mTimer.cancel();
        }
    }

    /* JADX WARN: Type inference failed for: r0v1, types: [com.android.phone.EmergencyCallbackModeService$3] */
    private void startTimerNotification() {
        long ecmTimeout = SystemProperties.getLong("ro.cdma.ecmexittimer", 300000L);
        showNotification(ecmTimeout);
        this.mTimer = new CountDownTimer(ecmTimeout, 1000L) { // from class: com.android.phone.EmergencyCallbackModeService.3
            @Override // android.os.CountDownTimer
            public void onTick(long millisUntilFinished) {
                EmergencyCallbackModeService.this.mTimeLeft = millisUntilFinished;
                EmergencyCallbackModeService.this.showNotification(millisUntilFinished);
            }

            @Override // android.os.CountDownTimer
            public void onFinish() {
            }
        }.start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showNotification(long millisUntilFinished) {
        String text;
        Notification notification = new Notification(R.drawable.picture_emergency25x25, getText(R.string.phone_entered_ecm_text), 0L);
        Intent intent = new Intent("com.android.phone.action.ACTION_SHOW_ECM_EXIT_DIALOG");
        intent.putExtra("ims_phone", this.mIsImsPhone);
        intent.putExtra("subscription", this.mSubscription);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
        if (this.mInEmergencyCall) {
            text = getText(R.string.phone_in_ecm_call_notification_text).toString();
        } else {
            int minutes = (int) (millisUntilFinished / 60000);
            String time = String.format("%d:%02d", Integer.valueOf(minutes), Long.valueOf((millisUntilFinished % 60000) / 1000));
            text = String.format(getResources().getQuantityText(R.plurals.phone_in_ecm_notification_time, minutes).toString(), time);
        }
        notification.setLatestEventInfo(this, getText(R.string.phone_in_ecm_notification_title), text, contentIntent);
        notification.flags = 2;
        this.mNotificationManager.notify(R.string.phone_in_ecm_notification_title, notification);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resetEcmTimer(AsyncResult r) {
        boolean isTimerCanceled = ((Boolean) r.result).booleanValue();
        if (isTimerCanceled) {
            this.mInEmergencyCall = true;
            this.mTimer.cancel();
            showNotification(0L);
        } else {
            this.mInEmergencyCall = false;
            startTimerNotification();
        }
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    public class LocalBinder extends Binder {
        public LocalBinder() {
        }

        EmergencyCallbackModeService getService() {
            return EmergencyCallbackModeService.this;
        }
    }

    public long getEmergencyCallbackModeTimeout() {
        return this.mTimeLeft;
    }

    public boolean getEmergencyCallbackModeCallState() {
        return this.mInEmergencyCall;
    }

    public boolean isEcbmOnIms() {
        return this.mIsImsPhone;
    }
}
