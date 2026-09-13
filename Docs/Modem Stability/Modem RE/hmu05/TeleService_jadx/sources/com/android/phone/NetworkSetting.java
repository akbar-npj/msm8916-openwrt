package com.android.phone;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.NativeTextHelper;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import java.util.HashMap;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class NetworkSetting extends PreferenceActivity implements DialogInterface.OnCancelListener {
    private Preference mAutoSelect;
    private ConnectivityManager mCm;
    private PreferenceGroup mNetworkList;
    private HashMap<Preference, OperatorInfo> mNetworkMap;
    String mNetworkSelectMsg;
    Phone mPhone;
    private HashMap<String, String> mRatMap;
    private Preference mSearchButton;
    NetworkSettingDataManager mDataManager = null;
    protected boolean mIsForeground = false;
    private final Handler mHandler = new Handler() { // from class: com.android.phone.NetworkSetting.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    NetworkSetting.this.networksListLoaded((List) msg.obj, msg.arg1);
                    break;
                case 200:
                    NetworkSetting.this.removeDialog(100);
                    NetworkSetting.this.getPreferenceScreen().setEnabled(true);
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        NetworkSetting.this.displayNetworkSelectionFailed(ar.exception);
                    } else {
                        NetworkSetting.this.displayNetworkSelectionSucceeded();
                    }
                    break;
                case 300:
                    try {
                        NetworkSetting.this.dismissDialog(300);
                    } catch (IllegalArgumentException e) {
                        Log.w("phone", "[NetworksList] Fail to dismiss auto select dialog", e);
                    }
                    NetworkSetting.this.getPreferenceScreen().setEnabled(true);
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    if (ar2.exception != null) {
                        NetworkSetting.this.displayNetworkSelectionFailed(ar2.exception);
                    } else {
                        NetworkSetting.this.displayNetworkSelectionSucceeded();
                    }
                    break;
                case 500:
                    NetworkSetting.this.log("EVENT_NETWORK_DATA_MANAGER_DONE: " + msg.arg1);
                    if (msg.arg1 == 1) {
                        NetworkSetting.this.loadNetworksList();
                    }
                    NetworkSetting.this.mSearchButton.setEnabled(true);
                    break;
            }
        }
    };
    private INetworkQueryService mNetworkQueryService = null;
    private final ServiceConnection mNetworkQueryServiceConnection = new ServiceConnection() { // from class: com.android.phone.NetworkSetting.2
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName className, IBinder service) {
            NetworkSetting.this.mNetworkQueryService = ((NetworkQueryService.LocalBinder) service).getService();
            if (NetworkSetting.this.isDataDisableRequired()) {
                NetworkSetting.this.mSearchButton.setEnabled(false);
                Message onCompleteMsg = NetworkSetting.this.mHandler.obtainMessage(500);
                NetworkSetting.this.mDataManager.updateDataState(false, onCompleteMsg);
                return;
            }
            NetworkSetting.this.loadNetworksList();
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName className) {
            NetworkSetting.this.mNetworkQueryService = null;
        }
    };
    private final INetworkQueryServiceCallback mCallback = new INetworkQueryServiceCallback.Stub() { // from class: com.android.phone.NetworkSetting.3
        @Override // com.android.phone.INetworkQueryServiceCallback
        public void onQueryComplete(List<OperatorInfo> networkInfoArray, int status) {
            Message msg = NetworkSetting.this.mHandler.obtainMessage(100, status, 0, networkInfoArray);
            msg.sendToTarget();
        }
    };

    @Override // android.preference.PreferenceActivity
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == this.mSearchButton) {
            if (isDataDisableRequired()) {
                this.mSearchButton.setEnabled(false);
                Message onCompleteMsg = this.mHandler.obtainMessage(500);
                this.mDataManager.updateDataState(false, onCompleteMsg);
            } else {
                loadNetworksList();
            }
            return true;
        }
        if (preference == this.mAutoSelect) {
            selectNetworkAutomatic();
            return true;
        }
        String networkStr = preference.getTitle().toString();
        Message msg = this.mHandler.obtainMessage(200);
        this.mPhone.selectNetworkManually(this.mNetworkMap.get(preference), msg);
        displayNetworkSeletionInProgress(networkStr);
        return true;
    }

    @Override // android.content.DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        try {
            this.mNetworkQueryService.stopNetworkQuery(this.mCallback);
            finish();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        addPreferencesFromResource(R.xml.carrier_select);
        int intExtra = getIntent().getIntExtra("subscription", MSimPhoneGlobals.getInstance().getDefaultSubscription());
        log("onCreate subscription :" + intExtra);
        this.mPhone = MSimPhoneGlobals.getInstance().getPhone(intExtra);
        Intent intent = new Intent(this, (Class<?>) NetworkQueryService.class);
        intent.putExtra("subscription", intExtra);
        this.mNetworkList = (PreferenceGroup) getPreferenceScreen().findPreference("list_networks_key");
        this.mNetworkMap = new HashMap<>();
        this.mCm = (ConnectivityManager) getSystemService("connectivity");
        this.mRatMap = new HashMap<>();
        initRatMap();
        this.mSearchButton = getPreferenceScreen().findPreference("button_srch_netwrks_key");
        this.mAutoSelect = getPreferenceScreen().findPreference("button_auto_select_key");
        startService(intent);
        bindService(new Intent(this, (Class<?>) NetworkQueryService.class), this.mNetworkQueryServiceConnection, 1);
        if (isDataDisableRequired()) {
            this.mDataManager = new NetworkSettingDataManager(getApplicationContext());
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
    }

    @Override // android.preference.PreferenceActivity, android.app.ListActivity, android.app.Activity
    protected void onDestroy() {
        if (this.mDataManager != null) {
            this.mDataManager.updateDataState(true, null);
        }
        unbindService(this.mNetworkQueryServiceConnection);
        super.onDestroy();
    }

    @Override // android.app.Activity
    protected Dialog onCreateDialog(int id) {
        if (id == 100 || id == 200 || id == 300) {
            ProgressDialog dialog = new ProgressDialog(this);
            switch (id) {
                case 100:
                    dialog.setMessage(this.mNetworkSelectMsg);
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    return dialog;
                case 300:
                    dialog.setMessage(getResources().getString(R.string.register_automatically));
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    return dialog;
                default:
                    dialog.setMessage(getResources().getString(R.string.load_networks_progress));
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setOnCancelListener(this);
                    return dialog;
            }
        }
        return null;
    }

    @Override // android.app.Activity
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == 100 || id == 200 || id == 300) {
            getPreferenceScreen().setEnabled(false);
        }
    }

    private void displayEmptyNetworkList(boolean flag) {
        this.mNetworkList.setTitle(flag ? R.string.empty_networks_list : R.string.label_available);
    }

    private void displayNetworkSeletionInProgress(String networkStr) {
        this.mNetworkSelectMsg = getResources().getString(R.string.register_on_network, networkStr);
        if (this.mIsForeground) {
            showDialog(100);
        }
    }

    private void displayNetworkQueryFailed(int error) {
        String status = getResources().getString(R.string.network_query_error);
        PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(4, status);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void displayNetworkSelectionFailed(Throwable ex) {
        String status;
        if (ex != null && (ex instanceof CommandException) && ((CommandException) ex).getCommandError() == CommandException.Error.ILLEGAL_SIM_OR_ME) {
            status = getResources().getString(R.string.not_allowed);
        } else {
            status = getResources().getString(R.string.connect_later);
        }
        PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(4, status);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void displayNetworkSelectionSucceeded() {
        String status = getResources().getString(R.string.registration_done);
        PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(4, status);
        this.mHandler.postDelayed(new Runnable() { // from class: com.android.phone.NetworkSetting.4
            @Override // java.lang.Runnable
            public void run() {
                NetworkSetting.this.finish();
            }
        }, 3000L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadNetworksList() {
        if (this.mIsForeground) {
            showDialog(200);
        }
        try {
            this.mNetworkQueryService.startNetworkQuery(this.mCallback);
        } catch (RemoteException e) {
        }
        displayEmptyNetworkList(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void networksListLoaded(List<OperatorInfo> result, int status) {
        try {
            dismissDialog(200);
        } catch (IllegalArgumentException e) {
        }
        if (this.mDataManager != null) {
            this.mDataManager.updateDataState(true, null);
        }
        getPreferenceScreen().setEnabled(true);
        clearList();
        if (status != 0) {
            displayNetworkQueryFailed(status);
            displayEmptyNetworkList(true);
            return;
        }
        if (result != null) {
            displayEmptyNetworkList(false);
            for (OperatorInfo ni : result) {
                Preference carrier = new Preference(this, null);
                carrier.setTitle(getNetworkTitle(ni));
                carrier.setPersistent(false);
                this.mNetworkList.addPreference(carrier);
                this.mNetworkMap.put(carrier, ni);
            }
            return;
        }
        displayEmptyNetworkList(true);
    }

    private String getNetworkTitle(OperatorInfo ni) {
        String title = ni.getOperatorNumeric();
        if (!TextUtils.isEmpty(ni.getOperatorAlphaLong())) {
            title = NativeTextHelper.getInternalLocalString(this, ni.getOperatorAlphaLong(), R.array.original_carrier_names, R.array.locale_carrier_names);
        } else if (!TextUtils.isEmpty(ni.getOperatorAlphaShort())) {
            title = NativeTextHelper.getInternalLocalString(this, ni.getOperatorAlphaShort(), R.array.original_carrier_names, R.array.locale_carrier_names);
        }
        if (ni.getState() == OperatorInfo.State.FORBIDDEN) {
            title = title + getString(R.string.network_forbidden);
        }
        if (!ni.getRadioTech().equals("")) {
            return title + " " + this.mRatMap.get(ni.getRadioTech());
        }
        return title;
    }

    private void clearList() {
        for (Preference p : this.mNetworkMap.keySet()) {
            this.mNetworkList.removePreference(p);
        }
        this.mNetworkMap.clear();
    }

    private void selectNetworkAutomatic() {
        if (this.mIsForeground) {
            showDialog(300);
        }
        Message msg = this.mHandler.obtainMessage(300);
        this.mPhone.setNetworkSelectionModeAutomatic(msg);
    }

    private void initRatMap() {
        this.mRatMap.put(String.valueOf(0), "Unknown");
        this.mRatMap.put(String.valueOf(1), "2G");
        this.mRatMap.put(String.valueOf(2), "2G");
        this.mRatMap.put(String.valueOf(3), "3G");
        this.mRatMap.put(String.valueOf(4), "2G");
        this.mRatMap.put(String.valueOf(5), "2G");
        this.mRatMap.put(String.valueOf(6), "2G");
        this.mRatMap.put(String.valueOf(7), "3G");
        this.mRatMap.put(String.valueOf(8), "3G");
        this.mRatMap.put(String.valueOf(9), "3G");
        this.mRatMap.put(String.valueOf(10), "3G");
        this.mRatMap.put(String.valueOf(11), "3G");
        this.mRatMap.put(String.valueOf(12), "3G");
        this.mRatMap.put(String.valueOf(13), "3G");
        this.mRatMap.put(String.valueOf(14), "4G");
        this.mRatMap.put(String.valueOf(15), "3G");
        this.mRatMap.put(String.valueOf(16), "2G");
        this.mRatMap.put(String.valueOf(17), "3G");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isDataDisableRequired() {
        boolean isRequired = getApplicationContext().getResources().getBoolean(R.bool.config_disable_data_manual_plmn);
        if (MSimTelephonyManager.getDefault().getMultiSimConfiguration() == MSimTelephonyManager.MultiSimVariants.DSDA && MSimTelephonyManager.getDefault().getDefaultDataSubscription() != this.mPhone.getSubscription()) {
            return false;
        }
        return isRequired;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Log.d("phone", "[NetworksList] " + msg);
    }
}
