package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;

/* JADX INFO: loaded from: classes.dex */
@Deprecated
public final class PhoneStateIntentReceiver extends BroadcastReceiver {
    private static final boolean DBG = false;
    private static final String LOG_TAG = "PhoneStatIntentReceiver";
    private static final int NOTIF_PHONE = 1;
    private static final int NOTIF_SERVICE = 2;
    private static final int NOTIF_SIGNAL = 4;
    private int mAsuEventWhat;
    private Context mContext;
    private IntentFilter mFilter;
    PhoneConstants.State mPhoneState;
    private int mPhoneStateEventWhat;
    ServiceState mServiceState;
    private int mServiceStateEventWhat;
    SignalStrength mSignalStrength;
    private Handler mTarget;
    private int mWants;

    public PhoneStateIntentReceiver() {
        this.mPhoneState = PhoneConstants.State.IDLE;
        this.mServiceState = new ServiceState();
        this.mSignalStrength = new SignalStrength();
        this.mFilter = new IntentFilter();
    }

    public PhoneStateIntentReceiver(Context context, Handler target) {
        this();
        setContext(context);
        setTarget(target);
    }

    public void setContext(Context c) {
        this.mContext = c;
    }

    public void setTarget(Handler h) {
        this.mTarget = h;
    }

    public PhoneConstants.State getPhoneState() {
        if ((this.mWants & 1) == 0) {
            throw new RuntimeException("client must call notifyPhoneCallState(int)");
        }
        return this.mPhoneState;
    }

    public ServiceState getServiceState() {
        if ((this.mWants & 2) == 0) {
            throw new RuntimeException("client must call notifyServiceState(int)");
        }
        return this.mServiceState;
    }

    public int getSignalStrengthLevelAsu() {
        if ((this.mWants & 4) == 0) {
            throw new RuntimeException("client must call notifySignalStrength(int)");
        }
        return this.mSignalStrength.getAsuLevel();
    }

    public int getSignalStrengthDbm() {
        if ((this.mWants & 4) == 0) {
            throw new RuntimeException("client must call notifySignalStrength(int)");
        }
        return this.mSignalStrength.getDbm();
    }

    public void notifyPhoneCallState(int eventWhat) {
        this.mWants |= 1;
        this.mPhoneStateEventWhat = eventWhat;
        this.mFilter.addAction("android.intent.action.PHONE_STATE");
    }

    public boolean getNotifyPhoneCallState() {
        return (this.mWants & 1) != 0;
    }

    public void notifyServiceState(int eventWhat) {
        this.mWants |= 2;
        this.mServiceStateEventWhat = eventWhat;
        this.mFilter.addAction("android.intent.action.SERVICE_STATE");
    }

    public boolean getNotifyServiceState() {
        return (this.mWants & 2) != 0;
    }

    public void notifySignalStrength(int eventWhat) {
        this.mWants |= 4;
        this.mAsuEventWhat = eventWhat;
        this.mFilter.addAction("android.intent.action.SIG_STR");
    }

    public boolean getNotifySignalStrength() {
        return (this.mWants & 4) != 0;
    }

    public void registerIntent() {
        this.mContext.registerReceiver(this, this.mFilter);
    }

    public void unregisterIntent() {
        this.mContext.unregisterReceiver(this);
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        try {
            if ("android.intent.action.SIG_STR".equals(action)) {
                this.mSignalStrength = SignalStrength.newFromBundle(intent.getExtras());
                if (this.mTarget != null && getNotifySignalStrength()) {
                    Message message = Message.obtain(this.mTarget, this.mAsuEventWhat);
                    this.mTarget.sendMessage(message);
                }
            } else if ("android.intent.action.PHONE_STATE".equals(action)) {
                String phoneState = intent.getStringExtra(Telephony.CdmaCallOptions.STATE);
                this.mPhoneState = Enum.valueOf(PhoneConstants.State.class, phoneState);
                if (this.mTarget != null && getNotifyPhoneCallState()) {
                    Message message2 = Message.obtain(this.mTarget, this.mPhoneStateEventWhat);
                    this.mTarget.sendMessage(message2);
                }
            } else if ("android.intent.action.SERVICE_STATE".equals(action)) {
                this.mServiceState = ServiceState.newFromBundle(intent.getExtras());
                if (this.mTarget != null && getNotifyServiceState()) {
                    Message message3 = Message.obtain(this.mTarget, this.mServiceStateEventWhat);
                    this.mTarget.sendMessage(message3);
                }
            }
        } catch (Exception ex) {
            Rlog.e(LOG_TAG, "[PhoneStateIntentRecv] caught " + ex);
            ex.printStackTrace();
        }
    }
}
