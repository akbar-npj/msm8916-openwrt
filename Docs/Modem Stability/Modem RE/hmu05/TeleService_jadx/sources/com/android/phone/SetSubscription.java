package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.codeaurora.telephony.msim.CardSubscriptionManager;
import com.codeaurora.telephony.msim.Subscription;
import com.codeaurora.telephony.msim.SubscriptionData;
import com.codeaurora.telephony.msim.SubscriptionManager;

/* JADX INFO: loaded from: classes.dex */
public class SetSubscription extends PreferenceActivity implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener, View.OnClickListener {
    private AlertDialog mAlertDialog;
    private TextView mCancelButton;
    private SubscriptionData[] mCardSubscrInfo;
    private CardSubscriptionManager mCardSubscriptionManager;
    private SubscriptionData mCurrentSelSub;
    private TextView mOkButton;
    private AirplaneModeBroadcastReceiver mReceiver;
    private SubscriptionManager mSubscriptionManager;
    private SubscriptionData mUserSelSub;
    CheckBoxPreference[] subArray;
    private boolean subErr = false;
    private boolean mIsForeground = false;
    private final int MAX_SUBSCRIPTIONS = SubscriptionManager.NUM_SUBSCRIPTIONS;
    private final int EVENT_SET_SUBSCRIPTION_DONE = 1;
    private final int EVENT_SIM_STATE_CHANGED = 2;
    private final int DIALOG_SET_SUBSCRIPTION_IN_PROGRESS = 100;
    Preference.OnPreferenceClickListener mCheckBoxListener = new Preference.OnPreferenceClickListener() { // from class: com.android.phone.SetSubscription.3
        @Override // android.preference.Preference.OnPreferenceClickListener
        public boolean onPreferenceClick(Preference preference) {
            CheckBoxPreference subPref = (CheckBoxPreference) preference;
            String key = subPref.getKey();
            Log.d("SetSubscription", "setSubscription: key = " + key);
            String[] splitKey = key.split(" ");
            String sSlotId = splitKey[0].substring(splitKey[0].indexOf("slot") + 4);
            int slotIndex = Integer.parseInt(sSlotId);
            if (subPref.isChecked()) {
                if (SetSubscription.this.subArray[slotIndex] != null) {
                    SetSubscription.this.subArray[slotIndex].setChecked(false);
                }
                SetSubscription.this.subArray[slotIndex] = subPref;
                return true;
            }
            SetSubscription.this.subArray[slotIndex] = null;
            return true;
        }
    };
    private Handler mHandler = new Handler() { // from class: com.android.phone.SetSubscription.6
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Log.d("SetSubscription", "EVENT_SET_SUBSCRIPTION_DONE");
                    SetSubscription.this.mSubscriptionManager.unRegisterForSetSubscriptionCompleted(SetSubscription.this.mHandler);
                    SetSubscription.this.dismissDialogSafely(100);
                    SetSubscription.this.getPreferenceScreen().setEnabled(true);
                    AsyncResult ar = (AsyncResult) msg.obj;
                    String[] result = (String[]) ar.result;
                    if (result != null) {
                        SetSubscription.this.displayAlertDialog(result);
                    } else {
                        SetSubscription.this.finish();
                    }
                    break;
                case 2:
                    Log.d("SetSubscription", "EVENT_SIM_STATE_CHANGED");
                    PreferenceScreen prefParent = (PreferenceScreen) SetSubscription.this.getPreferenceScreen().findPreference("subscr_parent");
                    for (int i = 0; i < SetSubscription.this.mCardSubscrInfo.length; i++) {
                        PreferenceCategory subGroup = (PreferenceCategory) prefParent.findPreference("sub_group_" + i);
                        if (subGroup != null) {
                            subGroup.removeAll();
                        }
                    }
                    prefParent.removeAll();
                    SetSubscription.this.populateList();
                    SetSubscription.this.updateCheckBoxes();
                    break;
            }
        }
    };

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public void onCreate(Bundle icicle) {
        boolean newCardNotify = getIntent().getBooleanExtra("NOTIFY_NEW_CARD_AVAILABLE", false);
        if (!newCardNotify) {
            setTheme(android.R.style.Theme);
        }
        super.onCreate(icicle);
        this.mSubscriptionManager = SubscriptionManager.getInstance();
        this.mCardSubscriptionManager = CardSubscriptionManager.getInstance();
        if (newCardNotify) {
            Log.d("SetSubscription", "onCreate: Notify new cards are available!!!!");
            notifyNewCardAvailable();
        } else {
            this.mCardSubscrInfo = new SubscriptionData[this.MAX_SUBSCRIPTIONS];
            for (int i = 0; i < this.MAX_SUBSCRIPTIONS; i++) {
                this.mCardSubscrInfo[i] = this.mCardSubscriptionManager.getCardSubscriptions(i);
            }
            addPreferencesFromResource(R.xml.set_subscription_pref);
            setContentView(R.layout.set_subscription_pref_layout);
            this.mOkButton = (TextView) findViewById(R.id.ok);
            this.mOkButton.setOnClickListener(this);
            this.mCancelButton = (TextView) findViewById(R.id.cancel);
            this.mCancelButton.setOnClickListener(this);
            this.subArray = new CheckBoxPreference[this.MAX_SUBSCRIPTIONS];
            if (this.mCardSubscrInfo != null) {
                populateList();
                this.mUserSelSub = new SubscriptionData(this.MAX_SUBSCRIPTIONS);
                updateCheckBoxes();
            } else {
                Log.d("SetSubscription", "onCreate: Card info not available: mCardSubscrInfo == NULL");
            }
            this.mCardSubscriptionManager.registerForSimStateChanged(this.mHandler, 2, (Object) null);
            if (this.mSubscriptionManager.isSetSubscriptionInProgress()) {
                Log.d("SetSubscription", "onCreate: SetSubscription is in progress when started this activity");
                showDialog(100);
                this.mSubscriptionManager.registerForSetSubscriptionCompleted(this.mHandler, 1, (Object) null);
            }
        }
        IntentFilter intentFilter = new IntentFilter("android.intent.action.AIRPLANE_MODE");
        this.mReceiver = new AirplaneModeBroadcastReceiver();
        registerReceiver(this.mReceiver, intentFilter);
    }

    private class AirplaneModeBroadcastReceiver extends BroadcastReceiver {
        private AirplaneModeBroadcastReceiver() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.AIRPLANE_MODE") && SetSubscription.this.isAirplaneModeOn()) {
                Log.d("SetSubscription", "Airplane mode is: on ");
                SetSubscription.this.finish();
            }
        }
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        this.mIsForeground = true;
    }

    @Override // android.app.Activity
    public void onPause() {
        super.onPause();
        this.mIsForeground = false;
        if (this.mAlertDialog != null) {
            try {
                Log.d("SetSubscription", "onPause disimissing dialog = " + this.mAlertDialog);
                this.mAlertDialog.dismiss();
            } catch (IllegalArgumentException e) {
                Log.w("SetSubscription", "Exception dismissing dialog. Ex=" + e);
            }
            this.mAlertDialog = null;
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.ListActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        if (this.mAlertDialog != null) {
            this.mAlertDialog.dismiss();
        }
        this.mCardSubscriptionManager.unRegisterForSimStateChanged(this.mHandler);
        this.mSubscriptionManager.unRegisterForSetSubscriptionCompleted(this.mHandler);
        unregisterReceiver(this.mReceiver);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isAirplaneModeOn() {
        return Settings.System.getInt(getContentResolver(), "airplane_mode_on", 0) != 0;
    }

    private void notifyNewCardAvailable() {
        Log.d("SetSubscription", "notifyNewCardAvailable()");
        this.mAlertDialog = new AlertDialog.Builder(this).setMessage(R.string.new_cards_available).setTitle(R.string.config_sub_title).setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { // from class: com.android.phone.SetSubscription.2
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d("SetSubscription", "new card dialog box:  onClick");
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.android.phone.SetSubscription.1
            @Override // android.content.DialogInterface.OnDismissListener
            public void onDismiss(DialogInterface dialog) {
                Log.d("SetSubscription", "new card dialog box:  onDismiss");
                SetSubscription.this.mAlertDialog = null;
                SetSubscription.this.finish();
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateCheckBoxes() {
        PreferenceScreen prefParent = (PreferenceScreen) getPreferenceScreen().findPreference("subscr_parent");
        for (int i = 0; i < this.mCardSubscrInfo.length; i++) {
            PreferenceCategory subGroup = (PreferenceCategory) prefParent.findPreference("sub_group_" + i);
            if (subGroup != null) {
                int count = subGroup.getPreferenceCount();
                Log.d("SetSubscription", "updateCheckBoxes count = " + count);
                for (int j = 0; j < count; j++) {
                    ((CheckBoxPreference) subGroup.getPreference(j)).setChecked(false);
                }
            }
        }
        this.mCurrentSelSub = new SubscriptionData(this.MAX_SUBSCRIPTIONS);
        for (int i2 = 0; i2 < this.MAX_SUBSCRIPTIONS; i2++) {
            Subscription sub = this.mSubscriptionManager.getCurrentSubscription(i2);
            this.mCurrentSelSub.subscription[i2].copyFrom(sub);
        }
        if (this.mCurrentSelSub != null) {
            for (int i3 = 0; i3 < this.MAX_SUBSCRIPTIONS; i3++) {
                Log.d("SetSubscription", "updateCheckBoxes: mCurrentSelSub.subscription[" + i3 + "] = " + this.mCurrentSelSub.subscription[i3]);
                this.subArray[i3] = null;
                if (this.mCurrentSelSub.subscription[i3].subStatus == Subscription.SubscriptionStatus.SUB_ACTIVATED) {
                    String key = "slot" + this.mCurrentSelSub.subscription[i3].slotId + " index" + this.mCurrentSelSub.subscription[i3].getAppIndex();
                    Log.d("SetSubscription", "updateCheckBoxes: key = " + key);
                    PreferenceCategory subGroup2 = (PreferenceCategory) prefParent.findPreference("sub_group_" + this.mCurrentSelSub.subscription[i3].slotId);
                    if (subGroup2 != null) {
                        CheckBoxPreference checkBoxPref = (CheckBoxPreference) subGroup2.findPreference(key);
                        checkBoxPref.setChecked(true);
                        this.subArray[i3] = checkBoxPref;
                    }
                }
            }
            this.mUserSelSub.copyFrom(this.mCurrentSelSub);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void populateList() {
        PreferenceScreen preferenceScreen = (PreferenceScreen) getPreferenceScreen().findPreference("subscr_parent");
        int[] subGroupTitle = {R.string.card_01, R.string.card_02, R.string.card_03};
        Log.d("SetSubscription", "populateList:  mCardSubscrInfo.length = " + this.mCardSubscrInfo.length);
        int k = 0;
        SubscriptionData[] arr$ = this.mCardSubscrInfo;
        for (SubscriptionData cardSub : arr$) {
            if (cardSub != null && cardSub.getLength() > 0) {
                int i = 0;
                PreferenceCategory subGroup = new PreferenceCategory(this);
                subGroup.setKey("sub_group_" + k);
                subGroup.setTitle(subGroupTitle[k]);
                preferenceScreen.addPreference(subGroup);
                Subscription[] arr$2 = cardSub.subscription;
                for (Subscription sub : arr$2) {
                    if (sub != null && sub.appType != null) {
                        Log.d("SetSubscription", "populateList:  mCardSubscrInfo[" + k + "].subscription[" + i + "] = " + sub);
                        CheckBoxPreference newCheckBox = new CheckBoxPreference(this);
                        newCheckBox.setTitle(sub.appType.subSequence(0, sub.appType.length()));
                        newCheckBox.setKey(new String("slot" + k + " index" + i));
                        newCheckBox.setOnPreferenceClickListener(this.mCheckBoxListener);
                        subGroup.addPreference(newCheckBox);
                    }
                    i++;
                }
            }
            k++;
        }
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View v) {
        if (v == this.mOkButton) {
            setSubscription();
        } else if (v == this.mCancelButton) {
            finish();
        }
    }

    private void setSubscription() {
        Log.d("SetSubscription", "setSubscription");
        int numSubSelected = 0;
        int deactRequiredCount = 0;
        this.subErr = false;
        for (int i = 0; i < this.subArray.length; i++) {
            if (this.subArray[i] != null) {
                numSubSelected++;
            }
        }
        Log.d("SetSubscription", "setSubscription: numSubSelected = " + numSubSelected);
        if (numSubSelected == 0) {
            Toast toast = Toast.makeText(getApplicationContext(), R.string.set_subscription_error_atleast_one, 0);
            toast.show();
            return;
        }
        if (isPhoneInCall()) {
            displayErrorDialog(R.string.set_sub_not_supported_phone_in_call);
            return;
        }
        for (int i2 = 0; i2 < this.MAX_SUBSCRIPTIONS; i2++) {
            if (this.subArray[i2] == null) {
                if (this.mCurrentSelSub.subscription[i2].subStatus == Subscription.SubscriptionStatus.SUB_ACTIVATED) {
                    Log.d("SetSubscription", "setSubscription: Sub " + i2 + " not selected. Setting 99999");
                    this.mUserSelSub.subscription[i2].slotId = 99999;
                    this.mUserSelSub.subscription[i2].m3gppIndex = 99999;
                    this.mUserSelSub.subscription[i2].m3gpp2Index = 99999;
                    this.mUserSelSub.subscription[i2].subId = i2;
                    this.mUserSelSub.subscription[i2].subStatus = Subscription.SubscriptionStatus.SUB_DEACTIVATE;
                    deactRequiredCount++;
                }
            } else {
                String key = this.subArray[i2].getKey();
                Log.d("SetSubscription", "setSubscription: key = " + key);
                String[] splitKey = key.split(" ");
                String sSlotId = splitKey[0].substring(splitKey[0].indexOf("slot") + 4);
                int slotId = Integer.parseInt(sSlotId);
                String sIndexId = splitKey[1].substring(splitKey[1].indexOf("index") + 5);
                int subIndex = Integer.parseInt(sIndexId);
                if (this.mCardSubscrInfo[slotId] == null) {
                    Log.d("SetSubscription", "setSubscription: mCardSubscrInfo is not in sync with SubscriptionManager");
                    this.mUserSelSub.subscription[i2].slotId = 99999;
                    this.mUserSelSub.subscription[i2].m3gppIndex = 99999;
                    this.mUserSelSub.subscription[i2].m3gpp2Index = 99999;
                    this.mUserSelSub.subscription[i2].subId = i2;
                    this.mUserSelSub.subscription[i2].subStatus = Subscription.SubscriptionStatus.SUB_DEACTIVATE;
                    if (this.mCurrentSelSub.subscription[i2].subStatus == Subscription.SubscriptionStatus.SUB_ACTIVATED) {
                        deactRequiredCount++;
                    }
                } else {
                    this.mUserSelSub.subscription[i2].copyFrom(this.mCardSubscrInfo[slotId].subscription[subIndex]);
                    this.mUserSelSub.subscription[i2].subId = i2;
                    if (this.mCurrentSelSub != null) {
                        Subscription.SubscriptionStatus subStatus = this.mCurrentSelSub.subscription[i2].subStatus;
                        this.mUserSelSub.subscription[i2].subStatus = subStatus;
                        if (subStatus != Subscription.SubscriptionStatus.SUB_ACTIVATED || !this.mUserSelSub.subscription[i2].equals(this.mCurrentSelSub.subscription[i2])) {
                            this.mUserSelSub.subscription[i2].subStatus = Subscription.SubscriptionStatus.SUB_ACTIVATE;
                        }
                        if (this.mCurrentSelSub.subscription[i2].subStatus == Subscription.SubscriptionStatus.SUB_ACTIVATED && this.mUserSelSub.subscription[i2].subStatus == Subscription.SubscriptionStatus.SUB_ACTIVATE) {
                            deactRequiredCount++;
                        }
                    } else {
                        this.mUserSelSub.subscription[i2].subStatus = Subscription.SubscriptionStatus.SUB_ACTIVATE;
                    }
                }
            }
        }
        if (deactRequiredCount >= this.MAX_SUBSCRIPTIONS) {
            displayErrorDialog(R.string.deact_all_sub_not_supported);
            return;
        }
        boolean ret = this.mSubscriptionManager.setSubscription(this.mUserSelSub);
        if (ret) {
            if (this.mIsForeground) {
                showDialog(100);
            }
            this.mSubscriptionManager.registerForSetSubscriptionCompleted(this.mHandler, 1, (Object) null);
        }
    }

    private boolean isPhoneInCall() {
        for (int i = 0; i < this.MAX_SUBSCRIPTIONS; i++) {
            if (MSimTelephonyManager.getDefault().getCallState(i) != 0) {
                return true;
            }
        }
        return false;
    }

    private void displayErrorDialog(int messageId) {
        Log.d("SetSubscription", "errorMutipleDeactivate(): " + getResources().getString(messageId));
        this.mAlertDialog = new AlertDialog.Builder(this).setTitle(R.string.config_sub_title).setMessage(messageId).setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { // from class: com.android.phone.SetSubscription.5
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d("SetSubscription", "errorMutipleDeactivate:  onClick");
                SetSubscription.this.updateCheckBoxes();
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.android.phone.SetSubscription.4
            @Override // android.content.DialogInterface.OnDismissListener
            public void onDismiss(DialogInterface dialog) {
                Log.d("SetSubscription", "errorMutipleDeactivate:  onDismiss");
                SetSubscription.this.mAlertDialog = null;
                SetSubscription.this.updateCheckBoxes();
            }
        }).show();
    }

    @Override // android.app.Activity
    protected Dialog onCreateDialog(int id) {
        if (id != 100) {
            return null;
        }
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getResources().getString(R.string.set_uicc_subscription_progress));
        dialog.setCancelable(false);
        dialog.setIndeterminate(true);
        return dialog;
    }

    @Override // android.app.Activity
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == 100) {
            getPreferenceScreen().setEnabled(false);
        }
    }

    private boolean isFailed(String status) {
        Log.d("SetSubscription", "isFailed(" + status + ")");
        return status == null || (status != null && (status.equals("DEACTIVATE FAILED") || status.equals("DEACTIVATE NOT SUPPORTED") || status.equals("ACTIVATE FAILED") || status.equals("ACTIVATE NOT SUPPORTED")));
    }

    String setSubscriptionStatusToString(String status) {
        if (status.equals("ACTIVATE SUCCESS")) {
            String retStr = getResources().getString(R.string.set_sub_activate_success);
            return retStr;
        }
        if (status.equals("DEACTIVATE SUCCESS")) {
            String retStr2 = getResources().getString(R.string.set_sub_deactivate_success);
            return retStr2;
        }
        if (status.equals("DEACTIVATE FAILED")) {
            String retStr3 = getResources().getString(R.string.set_sub_deactivate_failed);
            return retStr3;
        }
        if (status.equals("DEACTIVATE NOT SUPPORTED")) {
            String retStr4 = getResources().getString(R.string.set_sub_deactivate_not_supported);
            return retStr4;
        }
        if (status.equals("ACTIVATE FAILED")) {
            String retStr5 = getResources().getString(R.string.set_sub_activate_failed);
            return retStr5;
        }
        if (status.equals("GLOBAL ACTIVATE FAILED")) {
            String retStr6 = getResources().getString(R.string.set_sub_global_activate_failed);
            return retStr6;
        }
        if (status.equals("GLOBAL DEACTIVATE FAILED")) {
            String retStr7 = getResources().getString(R.string.set_sub_global_deactivate_failed);
            return retStr7;
        }
        if (status.equals("ACTIVATE NOT SUPPORTED")) {
            String retStr8 = getResources().getString(R.string.set_sub_activate_not_supported);
            return retStr8;
        }
        if (!status.equals("NO CHANGE IN SUBSCRIPTION")) {
            return null;
        }
        String retStr9 = getResources().getString(R.string.set_sub_no_change);
        return retStr9;
    }

    void displayAlertDialog(String[] msg) {
        int[] resSubId = {R.string.set_sub_1, R.string.set_sub_2, R.string.set_sub_3};
        String dispMsg = "";
        int title = R.string.set_sub_failed;
        if (msg[0] != null && isFailed(msg[0])) {
            this.subErr = true;
        }
        if (msg[1] != null && isFailed(msg[1])) {
            this.subErr = true;
        }
        for (int i = 0; i < msg.length; i++) {
            if (msg[i] != null) {
                dispMsg = dispMsg + getResources().getString(resSubId[i]) + setSubscriptionStatusToString(msg[i]) + "\n";
            }
        }
        if (!this.subErr) {
            title = R.string.set_sub_success;
        }
        Log.d("SetSubscription", "displayAlertDialog:  dispMsg = " + dispMsg);
        this.mAlertDialog = new AlertDialog.Builder(this).setMessage(dispMsg).setTitle(title).setIcon(android.R.drawable.ic_dialog_alert).setPositiveButton(android.R.string.yes, this).setOnDismissListener(this).show();
    }

    @Override // android.content.DialogInterface.OnDismissListener
    public void onDismiss(DialogInterface dialog) {
        this.mAlertDialog = null;
        if (!this.subErr) {
            finish();
        }
    }

    @Override // android.content.DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog, int which) {
        try {
            dialog.dismiss();
        } catch (IllegalArgumentException e) {
            Log.w("SetSubscription", "Exception dismissing dialog. Ex=" + e);
        }
        updateCheckBoxes();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dismissDialogSafely(int id) {
        Log.d("SetSubscription", "dismissDialogSafely: id = " + id);
        try {
            dismissDialog(id);
        } catch (IllegalArgumentException e) {
        }
    }
}
