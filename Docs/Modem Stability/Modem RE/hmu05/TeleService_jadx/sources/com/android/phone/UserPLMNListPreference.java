package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.codeaurora.telephony.msim.MSimUiccController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class UserPLMNListPreference extends TimeConsumingPreferenceActivity {
    private IntentFilter mIntentFilter;
    private UPLMNInfoWithEf mOldInfo;
    private int mSubscription;
    private List<UPLMNInfoWithEf> mUPLMNList;
    private PreferenceScreen mUPLMNListContainer;
    private IccFileHandler mIccFileHandler = null;
    private Map<Preference, UPLMNInfoWithEf> mPreferenceMap = new LinkedHashMap();
    private MyHandler mHandler = new MyHandler();
    private int mNumRec = 0;
    private boolean mAirplaneModeOn = false;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.phone.UserPLMNListPreference.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.intent.action.AIRPLANE_MODE".equals(action)) {
                UserPLMNListPreference.this.mAirplaneModeOn = intent.getBooleanExtra("state", false);
                UserPLMNListPreference.this.setScreenEnabled();
            }
        }
    };

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.uplmn_list);
        this.mUPLMNListContainer = (PreferenceScreen) findPreference("button_uplmn_list_key");
        this.mSubscription = getIntent().getIntExtra("subscription", 0);
        loadIccFileHandler();
        this.mIntentFilter = new IntentFilter("android.intent.action.AIRPLANE_MODE");
        registerReceiver(this.mReceiver, this.mIntentFilter);
    }

    private void loadIccFileHandler() {
        UiccCard newCard = null;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimUiccController uiccController = MSimUiccController.getInstance();
            if (uiccController != null) {
                newCard = uiccController.getUiccCard(this.mSubscription);
            }
        } else {
            UiccController uiccController2 = UiccController.getInstance();
            if (uiccController2 != null) {
                newCard = uiccController2.getUiccCard();
            }
        }
        Log.d("UserPLMNListPreference", "newCard = " + newCard);
        if (newCard != null) {
            UiccCardApplication newUiccApplication = newCard.getApplication(1);
            Log.d("UserPLMNListPreference", "newUiccApplication = " + newUiccApplication);
            if (newUiccApplication != null) {
                this.mIccFileHandler = newUiccApplication.getIccFileHandler();
                Log.d("UserPLMNListPreference", "fh = " + this.mIccFileHandler);
            }
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.ListActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    @Override // com.android.phone.TimeConsumingPreferenceActivity, android.app.Activity
    public void onResume() {
        super.onResume();
        getUPLMNInfoFromEf();
        init(this, false);
        this.mAirplaneModeOn = Settings.System.getInt(getContentResolver(), "airplane_mode_on", -1) == 1;
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 1, 0, R.string.uplmn_list_setting_add_plmn).setShowAsAction(1);
        return true;
    }

    @Override // android.app.Activity
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu != null) {
            menu.setGroupEnabled(0, !this.mAirplaneModeOn);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case 1:
                Intent intent = new Intent(this, (Class<?>) UPLMNEditor.class);
                intent.putExtra("uplmn_code", "");
                intent.putExtra("uplmn_priority", 0);
                intent.putExtra("uplmn_service", 0);
                intent.putExtra("uplmn_add", true);
                intent.putExtra("uplmn_size", this.mUPLMNList.size());
                startActivityForResult(intent, 101);
                break;
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void init(TimeConsumingPreferenceListener listener, boolean skipReading) {
        Log.d("UserPLMNListPreference", "init ... ...");
        if (!skipReading && listener != null) {
            listener.onStarted(this.mUPLMNListContainer, true);
        }
    }

    @Override // com.android.phone.TimeConsumingPreferenceActivity, com.android.phone.TimeConsumingPreferenceListener
    public void onFinished(Preference preference, boolean reading) {
        super.onFinished(preference, reading);
        setScreenEnabled();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void getUPLMNInfoFromEf() {
        Log.d("UserPLMNListPreference", "UPLMNInfoFromEf Start read...");
        if (this.mIccFileHandler != null) {
            readEfFromIcc(this.mIccFileHandler, 28512);
        } else {
            Log.w("UserPLMNListPreference", "mIccFileHandler is null");
        }
    }

    private void readEfFromIcc(IccFileHandler mfh, int efid) {
        mfh.loadEFTransparent(efid, this.mHandler.obtainMessage(1));
    }

    private void writeEfToIcc(IccFileHandler mfh, byte[] efdata, int efid) {
        mfh.updateEFTransparent(efid, efdata, this.mHandler.obtainMessage(2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshUPLMNListPreference(ArrayList<UPLMNInfoWithEf> list) {
        if (this.mUPLMNListContainer.getPreferenceCount() != 0) {
            this.mUPLMNListContainer.removeAll();
        }
        if (this.mPreferenceMap != null) {
            this.mPreferenceMap.clear();
        }
        if (this.mUPLMNList != null) {
            this.mUPLMNList.clear();
        }
        this.mUPLMNList = list;
        if (list == null) {
            Log.d("UserPLMNListPreference", "refreshUPLMNListPreference : NULL UPLMN list!");
        } else {
            Log.d("UserPLMNListPreference", "refreshUPLMNListPreference : list.size()" + list.size());
        }
        if (list == null || list.size() == 0) {
            Log.d("UserPLMNListPreference", "refreshUPLMNListPreference : NULL UPLMN list!");
            if (list == null) {
                this.mUPLMNList = new ArrayList();
                return;
            }
            return;
        }
        for (UPLMNInfoWithEf network : list) {
            addUPLMNPreference(network);
            Log.d("UserPLMNListPreference", network.toString());
        }
    }

    class UPLMNInfoWithEf {
        private int mNetworkMode;
        private String mOperatorNumeric;
        private int mPriority;

        public String getOperatorNumeric() {
            return this.mOperatorNumeric;
        }

        public int getNetworMode() {
            return this.mNetworkMode;
        }

        public int getPriority() {
            return this.mPriority;
        }

        public void setOperatorNumeric(String operatorNumeric) {
            this.mOperatorNumeric = operatorNumeric;
        }

        public void setPriority(int index) {
            this.mPriority = index;
        }

        public UPLMNInfoWithEf(String operatorNumeric, int mNetworkMode, int mPriority) {
            this.mOperatorNumeric = operatorNumeric;
            this.mNetworkMode = mNetworkMode;
            this.mPriority = mPriority;
        }

        public String toString() {
            return "UPLMNInfoWithEf " + this.mOperatorNumeric + "/" + this.mNetworkMode + "/" + this.mPriority;
        }
    }

    class PriorityCompare implements Comparator<UPLMNInfoWithEf> {
        PriorityCompare() {
        }

        @Override // java.util.Comparator
        public int compare(UPLMNInfoWithEf object1, UPLMNInfoWithEf object2) {
            return object1.getPriority() - object2.getPriority();
        }
    }

    private void addUPLMNPreference(UPLMNInfoWithEf network) {
        Preference pref = new Preference(this);
        String plmnName = network.getOperatorNumeric();
        String extendName = getNetWorkModeString(network.getNetworMode());
        pref.setTitle(plmnName + "(" + extendName + ")");
        this.mUPLMNListContainer.addPreference(pref);
        this.mPreferenceMap.put(pref, network);
    }

    @Override // android.preference.PreferenceActivity
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Intent intent = new Intent(this, (Class<?>) UPLMNEditor.class);
        UPLMNInfoWithEf uPLMNInfoWithEf = this.mPreferenceMap.get(preference);
        this.mOldInfo = uPLMNInfoWithEf;
        intent.putExtra("uplmn_code", uPLMNInfoWithEf.getOperatorNumeric());
        intent.putExtra("uplmn_priority", uPLMNInfoWithEf.getPriority());
        intent.putExtra("uplmn_service", uPLMNInfoWithEf.getNetworMode());
        intent.putExtra("uplmn_add", false);
        intent.putExtra("uplmn_size", this.mUPLMNList.size());
        startActivityForResult(intent, 102);
        return true;
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d("UserPLMNListPreference", "resultCode = " + resultCode);
        Log.d("UserPLMNListPreference", "requestCode = " + requestCode);
        if (intent != null) {
            UPLMNInfoWithEf newInfo = createNetworkInfofromIntent(intent);
            if (resultCode == 102) {
                handleSetUPLMN(handleDeleteList(this.mOldInfo));
                return;
            }
            if (resultCode == 101) {
                if (requestCode == 101) {
                    handleAddList(newInfo);
                } else if (requestCode == 102) {
                    handleSetUPLMN(handleModifiedList(newInfo, this.mOldInfo));
                }
            }
        }
    }

    private UPLMNInfoWithEf createNetworkInfofromIntent(Intent intent) {
        String numberName = intent.getStringExtra("uplmn_code");
        int priority = intent.getIntExtra("uplmn_priority", 0);
        int act = intent.getIntExtra("uplmn_service", 0);
        return new UPLMNInfoWithEf(numberName, act, priority);
    }

    private void handleSetUPLMN(ArrayList<UPLMNInfoWithEf> list) {
        UPLMNInfoWithEf ni;
        String strOperNumeric;
        onStarted(this.mUPLMNListContainer, false);
        byte[] data = new byte[this.mNumRec * 5];
        byte[] bArr = new byte[6];
        for (int i = 0; i < this.mNumRec; i++) {
            data[i * 5] = -1;
            data[(i * 5) + 1] = -1;
            data[(i * 5) + 2] = -1;
            data[(i * 5) + 3] = 0;
            data[(i * 5) + 4] = 0;
        }
        for (int i2 = 0; i2 < list.size() && i2 < this.mNumRec && (strOperNumeric = (ni = list.get(i2)).getOperatorNumeric()) != null; i2++) {
            Log.d("UserPLMNListPreference", "strOperNumeric = " + strOperNumeric);
            if (strOperNumeric.length() == 5) {
                strOperNumeric = strOperNumeric + "F";
            }
            byte[] mccmnc = hexStringToBytes(strOperNumeric);
            data[i2 * 5] = (byte) ((mccmnc[1] << 4) + mccmnc[0]);
            Log.d("UserPLMNListPreference", "mccmnc[0] = " + ((int) mccmnc[0]));
            Log.d("UserPLMNListPreference", "mccmnc[1] = " + ((int) mccmnc[1]));
            Log.d("UserPLMNListPreference", "data[i*UPLMN_W_ACT_LEN] = " + ((int) data[i2 * 5]));
            data[(i2 * 5) + 1] = (byte) ((mccmnc[5] << 4) + mccmnc[2]);
            Log.d("UserPLMNListPreference", "data[1] = " + ((int) data[1]));
            data[(i2 * 5) + 2] = (byte) ((mccmnc[4] << 4) + mccmnc[3]);
            Log.d("UserPLMNListPreference", "data[2] = " + ((int) data[2]));
            if ((ni.getNetworMode() & 4) != 0) {
                data[(i2 * 5) + 3] = -128;
            } else {
                data[(i2 * 5) + 3] = 0;
            }
            if ((ni.getNetworMode() & 8) != 0) {
                data[(i2 * 5) + 3] = (byte) (data[(i2 * 5) + 3] | 64);
            }
            if ((ni.getNetworMode() & 1) != 0) {
                data[(i2 * 5) + 4] = -128;
            } else {
                data[(i2 * 5) + 4] = 0;
            }
            if ((ni.getNetworMode() & 2) != 0) {
                data[(i2 * 5) + 4] = (byte) (data[(i2 * 5) + 4] | 64);
            }
        }
        Log.d("UserPLMNListPreference", "update EFuplmn Start.");
        if (this.mIccFileHandler != null) {
            writeEfToIcc(this.mIccFileHandler, data, 28512);
        }
    }

    private void handleAddList(UPLMNInfoWithEf newInfo) {
        Log.d("UserPLMNListPreference", "handleAddList: add new network: " + newInfo);
        dumpUPLMNInfo(this.mUPLMNList);
        ArrayList<UPLMNInfoWithEf> list = new ArrayList<>();
        for (int i = 0; i < this.mUPLMNList.size(); i++) {
            list.add(this.mUPLMNList.get(i));
        }
        PriorityCompare pc = new PriorityCompare();
        int position = Collections.binarySearch(this.mUPLMNList, newInfo, pc);
        if (position > 0) {
            list.add(position, newInfo);
        } else {
            list.add(newInfo);
        }
        updateListPriority(list);
        dumpUPLMNInfo(list);
        handleSetUPLMN(list);
    }

    private void dumpUPLMNInfo(List<UPLMNInfoWithEf> list) {
        for (int i = 0; i < list.size(); i++) {
            Log.d("UserPLMNListPreference", "dumpUPLMNInfo : " + list.get(i).toString());
        }
    }

    private ArrayList<UPLMNInfoWithEf> handleModifiedList(UPLMNInfoWithEf newInfo, UPLMNInfoWithEf oldInfo) {
        Log.d("UserPLMNListPreference", "handleModifiedList: change old info: " + oldInfo.toString() + "-------new info: " + newInfo.toString());
        dumpUPLMNInfo(this.mUPLMNList);
        PriorityCompare pc = new PriorityCompare();
        int oldposition = Collections.binarySearch(this.mUPLMNList, oldInfo, pc);
        int newposition = Collections.binarySearch(this.mUPLMNList, newInfo, pc);
        ArrayList<UPLMNInfoWithEf> list = new ArrayList<>();
        for (int i = 0; i < this.mUPLMNList.size(); i++) {
            list.add(this.mUPLMNList.get(i));
        }
        if (oldposition > newposition) {
            list.remove(oldposition);
            list.add(newposition, newInfo);
        } else if (oldposition < newposition) {
            list.add(newposition + 1, newInfo);
            list.remove(oldposition);
        } else {
            list.remove(oldposition);
            list.add(oldposition, newInfo);
        }
        updateListPriority(list);
        dumpUPLMNInfo(list);
        return list;
    }

    private void updateListPriority(ArrayList<UPLMNInfoWithEf> list) {
        int priority = 0;
        for (UPLMNInfoWithEf info : list) {
            info.setPriority(priority);
            priority++;
        }
    }

    private ArrayList<UPLMNInfoWithEf> handleDeleteList(UPLMNInfoWithEf network) {
        Log.d("UserPLMNListPreference", "handleDeleteList : " + network.toString());
        dumpUPLMNInfo(this.mUPLMNList);
        ArrayList<UPLMNInfoWithEf> list = new ArrayList<>();
        PriorityCompare pc = new PriorityCompare();
        int position = Collections.binarySearch(this.mUPLMNList, network, pc);
        for (int i = 0; i < this.mUPLMNList.size(); i++) {
            list.add(this.mUPLMNList.get(i));
        }
        list.remove(position);
        network.setOperatorNumeric(null);
        list.add(network);
        updateListPriority(list);
        dumpUPLMNInfo(list);
        return list;
    }

    private class MyHandler extends Handler {
        private MyHandler() {
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    handleGetUPLMNList(msg);
                    break;
                case 1:
                    handleGetEFDone(msg);
                    break;
                case 2:
                    handleSetEFDone(msg);
                    break;
            }
        }

        public void handleGetUPLMNList(Message msg) {
            Log.d("UserPLMNListPreference", "handleGetUPLMNList: done");
            if (msg.arg2 == 0) {
                UserPLMNListPreference.this.onFinished(UserPLMNListPreference.this.mUPLMNListContainer, true);
            } else {
                UserPLMNListPreference.this.onFinished(UserPLMNListPreference.this.mUPLMNListContainer, false);
            }
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                UserPLMNListPreference.this.refreshUPLMNListPreference((ArrayList) ar.result);
                return;
            }
            Log.d("UserPLMNListPreference", "handleGetUPLMNList with exception = " + ar.exception);
            if (UserPLMNListPreference.this.mUPLMNList == null) {
                UserPLMNListPreference.this.mUPLMNList = new ArrayList();
            }
        }

        public void handleSetEFDone(Message msg) {
            Log.d("UserPLMNListPreference", "handleSetEFDone: done");
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Log.d("UserPLMNListPreference", "handleSetEFDone with exception = " + ar.exception);
            } else {
                Log.d("UserPLMNListPreference", "handleSetEFDone: with OK result!");
            }
            UserPLMNListPreference.this.getUPLMNInfoFromEf();
        }

        public void handleGetEFDone(Message msg) {
            int num_mnc_digits;
            Log.d("UserPLMNListPreference", "handleGetEFDone: done");
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Log.d("UserPLMNListPreference", "handleGetEFDone with exception = " + ar.exception);
                Message message = UserPLMNListPreference.this.mHandler.obtainMessage();
                message.what = 0;
                message.obj = msg.obj;
                UserPLMNListPreference.this.mHandler.sendMessage(message);
                return;
            }
            byte[] data = (byte[]) ar.result;
            Log.d("UserPLMNListPreference", "result=" + IccUtils.bytesToHexString(data));
            UserPLMNListPreference.this.mNumRec = data.length / 5;
            Log.d("UserPLMNListPreference", "mNumRec=" + UserPLMNListPreference.this.mNumRec);
            ArrayList<UPLMNInfoWithEf> ret = new ArrayList<>(UserPLMNListPreference.this.mNumRec);
            byte[] mcc = new byte[3];
            byte[] mnc = new byte[3];
            String strOperName = null;
            for (int i = 0; i < UserPLMNListPreference.this.mNumRec; i++) {
                int access_tech = 0;
                mcc[0] = (byte) (data[i * 5] & 15);
                mcc[1] = (byte) ((data[i * 5] & 240) >> 4);
                mcc[2] = (byte) (data[(i * 5) + 1] & 15);
                mnc[0] = (byte) (data[(i * 5) + 2] & 15);
                mnc[1] = (byte) ((data[(i * 5) + 2] & 240) >> 4);
                if (((byte) (data[(i * 5) + 1] & 240)) == -16) {
                    num_mnc_digits = 2;
                    mnc[2] = (byte) ((data[(i * 5) + 1] & 240) >> 4);
                } else {
                    num_mnc_digits = 3;
                    mnc[2] = (byte) ((data[(i * 5) + 1] & 240) >> 4);
                }
                if ((data[(i * 5) + 3] & 64) != 0) {
                    access_tech = 0 | 8;
                }
                if ((data[(i * 5) + 3] & 128) != 0) {
                    access_tech |= 4;
                }
                if ((data[(i * 5) + 4] & 128) != 0) {
                    access_tech |= 1;
                }
                if ((data[(i * 5) + 4] & 64) != 0) {
                    access_tech |= 2;
                }
                if (data[i * 5] != -1 && data[(i * 5) + 1] != -1 && data[(i * 5) + 2] != -1) {
                    if (num_mnc_digits == 2) {
                        strOperName = UserPLMNListPreference.this.bytesToHexString(mcc) + UserPLMNListPreference.this.bytesToHexString(mnc).substring(0, 2);
                    } else if (num_mnc_digits == 3) {
                        strOperName = UserPLMNListPreference.this.bytesToHexString(mcc) + UserPLMNListPreference.this.bytesToHexString(mnc);
                    }
                    ret.add(UserPLMNListPreference.this.new UPLMNInfoWithEf(strOperName, access_tech, i));
                }
            }
            Message message2 = UserPLMNListPreference.this.mHandler.obtainMessage();
            message2.what = 0;
            if (ret == null) {
                Log.d("UserPLMNListPreference", "handleGetEFDone : NULL ret list!");
            } else {
                Log.d("UserPLMNListPreference", "handleGetEFDone : ret.size()" + ret.size());
            }
            AsyncResult mret = new AsyncResult(message2.obj, ret, (Throwable) null);
            message2.obj = mret;
            UserPLMNListPreference.this.mHandler.sendMessage(message2);
        }
    }

    private String getNetWorkModeString(int EFNWMode) {
        int index = UPLMNEditor.convertEFMode2Ap(EFNWMode);
        String summary = getResources().getStringArray(R.array.uplmn_prefer_network_mode_td_choices)[index];
        return summary;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setScreenEnabled() {
        getPreferenceScreen().setEnabled(!this.mAirplaneModeOn);
        invalidateOptionsMenu();
    }

    public String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder ret = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int b2 = b & 15;
            ret.append("0123456789abcdef".charAt(b2));
        }
        return ret.toString();
    }

    public static byte[] hexStringToBytes(String s) {
        if (s == null) {
            return null;
        }
        int sz = s.length();
        byte[] ret = new byte[sz];
        for (int i = 0; i < sz; i++) {
            ret[i] = (byte) hexCharToInt(s.charAt(i));
            Log.d("UserPLMNListPreference", "hexStringToBytes = " + ((int) ret[i]));
        }
        return ret;
    }

    static int hexCharToInt(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return (c - 'A') + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return (c - 'a') + 10;
        }
        throw new RuntimeException("invalid hex char '" + c + "'");
    }
}
