package com.android.phone;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

/* JADX INFO: loaded from: classes.dex */
public class ManagedRoaming {
    private static ManagedRoaming sInstance;
    private Context mContext;
    private int mSubscription = 0;
    private boolean mIsMRDialogShown = false;
    private BroadcastReceiver mManagedRoamingReceiver = new BroadcastReceiver() { // from class: com.android.phone.ManagedRoaming.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("codeaurora.intent.action.ACTION_MANAGED_ROAMING_IND")) {
                int subscription = intent.getIntExtra("subscription", PhoneGlobals.getInstance().getDefaultSubscription());
                ManagedRoaming.this.createManagedRoamingDialog(subscription);
            }
        }
    };
    DialogInterface.OnClickListener onManagedRoamingDialogClick = new DialogInterface.OnClickListener() { // from class: com.android.phone.ManagedRoaming.2
        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            switch (which) {
                case -2:
                    break;
                case -1:
                    ManagedRoaming.this.log("Launch network settings activity sub = " + ManagedRoaming.this.mSubscription);
                    Intent networkSettingIntent = new Intent("android.intent.action.MAIN");
                    networkSettingIntent.setClassName("com.android.phone", "com.android.phone.NetworkSetting");
                    networkSettingIntent.addFlags(268435456);
                    networkSettingIntent.putExtra("subscription", ManagedRoaming.this.mSubscription);
                    ManagedRoaming.this.mContext.startActivity(networkSettingIntent);
                    break;
                default:
                    Log.w("ManagedRoaming", "received unknown button type: " + which);
                    break;
            }
            ManagedRoaming.this.mIsMRDialogShown = false;
        }
    };
    DialogInterface.OnKeyListener mManagedRoamingDialogOnKeyListener = new DialogInterface.OnKeyListener() { // from class: com.android.phone.ManagedRoaming.3
        @Override // android.content.DialogInterface.OnKeyListener
        public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
            if (keyCode == 4) {
                ManagedRoaming.this.log(" presed back key, reset local state");
                ManagedRoaming.this.mIsMRDialogShown = false;
            }
            return false;
        }
    };

    static ManagedRoaming init(Context context) {
        ManagedRoaming managedRoaming;
        synchronized (ManagedRoaming.class) {
            if (sInstance == null) {
                sInstance = new ManagedRoaming(context);
            } else {
                Log.wtf("ManagedRoaming", "init() called multiple times!  sInstance = " + sInstance);
            }
            managedRoaming = sInstance;
        }
        return managedRoaming;
    }

    private ManagedRoaming(Context context) {
        this.mContext = context;
        IntentFilter filter = new IntentFilter();
        filter.addAction("codeaurora.intent.action.ACTION_MANAGED_ROAMING_IND");
        this.mContext.registerReceiver(this.mManagedRoamingReceiver, filter);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void createManagedRoamingDialog(int subscription) {
        Resources.getSystem();
        String networkSelection = PreferenceManager.getDefaultSharedPreferences(this.mContext).getString("network_selection_key", "");
        log(" Received Managed Roaming intent, networkSelection " + networkSelection + " Is Dialog Displayed " + this.mIsMRDialogShown + " sub = " + subscription);
        if (!TextUtils.isEmpty(networkSelection) && !this.mIsMRDialogShown) {
            MSimTelephonyManager tm = MSimTelephonyManager.getDefault();
            int[] titleResource = {R.string.managed_roaming_title_sub1, R.string.managed_roaming_title_sub2, R.string.managed_roaming_title_sub3};
            int title = R.string.managed_roaming_title;
            this.mSubscription = subscription;
            if (tm.isMultiSimEnabled() && tm.getPhoneCount() > this.mSubscription) {
                title = titleResource[this.mSubscription];
            }
            AlertDialog managedRoamingDialog = new AlertDialog.Builder(this.mContext).setTitle(title).setMessage(R.string.managed_roaming_dialog_content).setPositiveButton(R.string.managed_roaming_dialog_ok_button, this.onManagedRoamingDialogClick).setNegativeButton(R.string.managed_roaming_dialog_cancel_button, this.onManagedRoamingDialogClick).create();
            managedRoamingDialog.setOnKeyListener(this.mManagedRoamingDialogOnKeyListener);
            this.mIsMRDialogShown = true;
            managedRoamingDialog.getWindow().setType(2003);
            managedRoamingDialog.show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("ManagedRoaming", msg);
    }
}
