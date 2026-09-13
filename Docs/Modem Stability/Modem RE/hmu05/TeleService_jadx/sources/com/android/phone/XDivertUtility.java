package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.codeaurora.telephony.msim.SubscriptionManager;

/* JADX INFO: loaded from: classes.dex */
public class XDivertUtility {
    protected static XDivertUtility sMe;
    private MSimPhoneGlobals mApp;
    private MSimCallNotifier mCallNotifier;
    private Context mContext;
    Handler mHandler;
    private boolean[] mHasImsiChanged;
    private String[] mImsiFromSim;
    private String[] mLineNumber;
    private int mNumPhones;
    private Phone mPhone;
    private BroadcastReceiver mReceiver;
    private String[] mStoredImsi;

    public XDivertUtility() {
        this.mNumPhones = 0;
        this.mHandler = new Handler() { // from class: com.android.phone.XDivertUtility.1
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception == null) {
                            int subscription = ((Integer) ar.userObj).intValue();
                            Log.d("XDivertUtility", "subscription = " + subscription);
                            XDivertUtility.this.mImsiFromSim[subscription] = MSimTelephonyManager.getDefault().getSubscriberId(subscription);
                            XDivertUtility.this.mStoredImsi[subscription] = XDivertUtility.this.getSimImsi(subscription);
                            Log.d("XDivertUtility", "SIM_RECORDS_LOADED mImsiFromSim = " + XDivertUtility.this.mImsiFromSim[subscription] + "mStoredImsi = " + XDivertUtility.this.mStoredImsi[subscription]);
                            if (XDivertUtility.this.mStoredImsi[subscription] == null || (XDivertUtility.this.mImsiFromSim[subscription] != null && !XDivertUtility.this.mImsiFromSim[subscription].equals(XDivertUtility.this.mStoredImsi[subscription]))) {
                                XDivertUtility.this.mCallNotifier.setXDivertStatus(false);
                                XDivertUtility.this.setSimImsi(XDivertUtility.this.mImsiFromSim[subscription], subscription);
                                XDivertUtility.this.storeNumber(null, subscription);
                            } else if (XDivertUtility.this.mStoredImsi[subscription] != null && XDivertUtility.this.mImsiFromSim[subscription] != null && XDivertUtility.this.mImsiFromSim[subscription].equals(XDivertUtility.this.mStoredImsi[subscription])) {
                                XDivertUtility.this.mLineNumber[subscription] = XDivertUtility.this.getNumber(subscription);
                                XDivertUtility.this.mHasImsiChanged[subscription] = false;
                                Log.d("XDivertUtility", "Stored Line Number = " + XDivertUtility.this.mLineNumber[subscription]);
                            }
                            if (!XDivertUtility.this.mHasImsiChanged[0] && !XDivertUtility.this.mHasImsiChanged[1]) {
                                boolean status = XDivertUtility.this.mCallNotifier.getXDivertStatus();
                                XDivertUtility.this.mCallNotifier.onXDivertChanged(status);
                                break;
                            }
                        }
                        break;
                    case 2:
                        Log.d("XDivertUtility", "EVENT_SUBSCRIPTION_DEACTIVATED");
                        XDivertUtility.this.onSubscriptionDeactivated();
                        break;
                    default:
                        super.handleMessage(msg);
                        break;
                }
            }
        };
        sMe = this;
    }

    static XDivertUtility init(MSimPhoneGlobals mSimPhoneGlobals, Phone phone, MSimCallNotifier mSimCallNotifier, Context context) {
        XDivertUtility xDivertUtility;
        synchronized (XDivertUtility.class) {
            if (sMe == null) {
                sMe = new XDivertUtility(mSimPhoneGlobals, phone, mSimCallNotifier, context);
            } else {
                Log.wtf("XDivertUtility", "init() called multiple times!  sInstance = " + sMe);
            }
            xDivertUtility = sMe;
        }
        return xDivertUtility;
    }

    private XDivertUtility(MSimPhoneGlobals app, Phone phone, MSimCallNotifier callNotifier, Context context) {
        this.mNumPhones = 0;
        this.mHandler = new Handler() { // from class: com.android.phone.XDivertUtility.1
            @Override // android.os.Handler
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception == null) {
                            int subscription = ((Integer) ar.userObj).intValue();
                            Log.d("XDivertUtility", "subscription = " + subscription);
                            XDivertUtility.this.mImsiFromSim[subscription] = MSimTelephonyManager.getDefault().getSubscriberId(subscription);
                            XDivertUtility.this.mStoredImsi[subscription] = XDivertUtility.this.getSimImsi(subscription);
                            Log.d("XDivertUtility", "SIM_RECORDS_LOADED mImsiFromSim = " + XDivertUtility.this.mImsiFromSim[subscription] + "mStoredImsi = " + XDivertUtility.this.mStoredImsi[subscription]);
                            if (XDivertUtility.this.mStoredImsi[subscription] == null || (XDivertUtility.this.mImsiFromSim[subscription] != null && !XDivertUtility.this.mImsiFromSim[subscription].equals(XDivertUtility.this.mStoredImsi[subscription]))) {
                                XDivertUtility.this.mCallNotifier.setXDivertStatus(false);
                                XDivertUtility.this.setSimImsi(XDivertUtility.this.mImsiFromSim[subscription], subscription);
                                XDivertUtility.this.storeNumber(null, subscription);
                            } else if (XDivertUtility.this.mStoredImsi[subscription] != null && XDivertUtility.this.mImsiFromSim[subscription] != null && XDivertUtility.this.mImsiFromSim[subscription].equals(XDivertUtility.this.mStoredImsi[subscription])) {
                                XDivertUtility.this.mLineNumber[subscription] = XDivertUtility.this.getNumber(subscription);
                                XDivertUtility.this.mHasImsiChanged[subscription] = false;
                                Log.d("XDivertUtility", "Stored Line Number = " + XDivertUtility.this.mLineNumber[subscription]);
                            }
                            if (!XDivertUtility.this.mHasImsiChanged[0] && !XDivertUtility.this.mHasImsiChanged[1]) {
                                boolean status = XDivertUtility.this.mCallNotifier.getXDivertStatus();
                                XDivertUtility.this.mCallNotifier.onXDivertChanged(status);
                                break;
                            }
                        }
                        break;
                    case 2:
                        Log.d("XDivertUtility", "EVENT_SUBSCRIPTION_DEACTIVATED");
                        XDivertUtility.this.onSubscriptionDeactivated();
                        break;
                    default:
                        super.handleMessage(msg);
                        break;
                }
            }
        };
        Log.d("XDivertUtility", "onCreate()...");
        SubscriptionManager subMgr = SubscriptionManager.getInstance();
        this.mApp = app;
        this.mPhone = phone;
        this.mCallNotifier = callNotifier;
        this.mContext = context;
        this.mReceiver = new XDivertBroadcastReceiver();
        this.mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();
        this.mImsiFromSim = new String[this.mNumPhones];
        this.mStoredImsi = new String[this.mNumPhones];
        this.mLineNumber = new String[this.mNumPhones];
        this.mHasImsiChanged = new boolean[this.mNumPhones];
        for (int i = 0; i < this.mNumPhones; i++) {
            subMgr.registerForSubscriptionDeactivated(i, this.mHandler, 2, (Object) null);
            this.mPhone = app.getPhone(i);
            this.mPhone.registerForSimRecordsLoaded(this.mHandler, 1, Integer.valueOf(i));
            this.mHasImsiChanged[i] = true;
        }
        IntentFilter intentFilter = new IntentFilter("android.intent.action.RADIO_TECHNOLOGY");
        this.mContext.registerReceiver(this.mReceiver, intentFilter);
    }

    static XDivertUtility getInstance() {
        return sMe;
    }

    private class XDivertBroadcastReceiver extends BroadcastReceiver {
        private XDivertBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v("XDivertUtility", "Action intent recieved:" + action);
            int subscription = intent.getIntExtra("subscription", XDivertUtility.this.mApp.getDefaultSubscription());
            if (action.equals("android.intent.action.RADIO_TECHNOLOGY")) {
                Phone phone = XDivertUtility.this.mApp.getPhone(subscription);
                phone.unregisterForSimRecordsLoaded(XDivertUtility.this.mHandler);
                phone.registerForSimRecordsLoaded(XDivertUtility.this.mHandler, 1, Integer.valueOf(subscription));
            }
        }
    }

    protected boolean checkImsiReady() {
        for (int i = 0; i < this.mNumPhones; i++) {
            this.mStoredImsi[i] = getSimImsi(i);
            this.mImsiFromSim[i] = MSimTelephonyManager.getDefault().getSubscriberId(i);
            if (this.mImsiFromSim[i] == null || this.mImsiFromSim[i] == "") {
                return false;
            }
            if (this.mStoredImsi[i] == null || (this.mImsiFromSim[i] != null && !this.mImsiFromSim[i].equals(this.mStoredImsi[i]))) {
                this.mCallNotifier.setXDivertStatus(false);
                setSimImsi(this.mImsiFromSim[i], i);
                storeNumber(null, i);
                this.mHasImsiChanged[i] = true;
            }
        }
        return true;
    }

    protected String[] getLineNumbers() {
        return this.mLineNumber;
    }

    protected String getSimImsi(int subscription) {
        Log.d("XDivertUtility", "getSimImsi sub = " + subscription);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        return sp.getString("sim_imsi_key" + subscription, null);
    }

    protected void setSimImsi(String imsi, int subscription) {
        Log.d("XDivertUtility", "setSimImsi imsi = " + imsi + "sub = " + subscription);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("sim_imsi_key" + subscription, imsi);
        editor.apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSubscriptionDeactivated() {
        this.mCallNotifier.onXDivertChanged(false);
    }

    protected String getNumber(int subscription) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        return sp.getString("sim_number_key" + subscription, null);
    }

    protected void storeNumber(String number, int subscription) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("sim_number_key" + subscription, number);
        editor.apply();
        this.mLineNumber[subscription] = number;
    }
}
