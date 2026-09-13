package com.android.phone;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class NetworkSettingDataManager {
    private ConnectivityManager mCm;
    Context mContext;
    Message mMsg;
    private TelephonyManager mTelephonyManager;
    private boolean mNetworkSearchDataDisconnecting = false;
    private boolean mNetworkSearchDataDisabled = false;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.phone.NetworkSettingDataManager.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (NetworkSettingDataManager.this.mNetworkSearchDataDisconnecting && action.equals("android.intent.action.ANY_DATA_STATE") && NetworkSettingDataManager.this.mTelephonyManager.getDataState() == 0) {
                NetworkSettingDataManager.this.log("network disconnect data done");
                NetworkSettingDataManager.this.mNetworkSearchDataDisabled = true;
                NetworkSettingDataManager.this.mNetworkSearchDataDisconnecting = false;
                NetworkSettingDataManager.this.mMsg.arg1 = 1;
                NetworkSettingDataManager.this.mMsg.sendToTarget();
            }
        }
    };

    public NetworkSettingDataManager(Context context) {
        this.mContext = context;
        log("Create NetworkSettingDataManager");
        this.mCm = (ConnectivityManager) this.mContext.getSystemService("connectivity");
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
    }

    public void updateDataState(boolean enable, Message msg) {
        if (!enable) {
            if (this.mTelephonyManager.getDataState() == 2) {
                log("Data is in CONNECTED state");
                this.mMsg = msg;
                ConfirmDialogListener listener = new ConfirmDialogListener(msg);
                AlertDialog d = new AlertDialog.Builder(this.mContext).setTitle(android.R.string.dialog_alert_title).setIcon(android.R.drawable.ic_dialog_alert).setMessage(R.string.disconnect_data_confirm).setPositiveButton(android.R.string.ok, listener).setNegativeButton(android.R.string.no, listener).setOnCancelListener(listener).create();
                d.getWindow().setType(2003);
                d.show();
                return;
            }
            msg.arg1 = 1;
            msg.sendToTarget();
            return;
        }
        if (this.mNetworkSearchDataDisabled || this.mNetworkSearchDataDisconnecting) {
            log("Enabling data");
            this.mCm.setMobileDataEnabled(true);
            this.mContext.unregisterReceiver(this.mReceiver);
            this.mNetworkSearchDataDisabled = false;
            this.mNetworkSearchDataDisconnecting = false;
        }
    }

    private final class ConfirmDialogListener implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
        Message msg1;

        ConfirmDialogListener(Message msg) {
            this.msg1 = msg;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            if (which == -1) {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction("android.intent.action.ANY_DATA_STATE");
                NetworkSettingDataManager.this.mContext.registerReceiver(NetworkSettingDataManager.this.mReceiver, intentFilter);
                NetworkSettingDataManager.this.mNetworkSearchDataDisconnecting = true;
                NetworkSettingDataManager.this.mCm.setMobileDataEnabled(false);
                return;
            }
            if (which == -2) {
                NetworkSettingDataManager.this.log("network search, do nothing");
                this.msg1.arg1 = 0;
                this.msg1.sendToTarget();
            }
        }

        @Override // android.content.DialogInterface.OnCancelListener
        public void onCancel(DialogInterface dialog) {
            this.msg1.arg1 = 0;
            this.msg1.sendToTarget();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("phone", "[NetworkSettingDataManager] " + msg);
    }
}
