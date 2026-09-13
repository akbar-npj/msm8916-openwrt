package com.android.phone.ims;

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import com.android.phone.R;
import java.util.HashSet;
import org.codeaurora.ims.IImsService;
import org.codeaurora.ims.IImsServiceListener;

/* JADX INFO: loaded from: classes.dex */
public class ImsEditor extends PreferenceActivity implements Preference.OnPreferenceChangeListener {
    private ListPreference mCallTypePref;
    private Button mRemoveButton;
    private ImsSharedPreferences mSharedPreferences;
    private MultiSelectListPreference mUseAlwaysPref;
    private static final String TAG = ImsEditor.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable("IMS", 3);
    private IImsService mImsService = null;
    private boolean mIsImsListenerRegistered = false;
    private Handler mHandler = new Handler() { // from class: com.android.phone.ims.ImsEditor.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                case 2:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar == null || ar.exception == null) {
                        ImsEditor.this.enablePref(ImsEditor.this.mUseAlwaysPref, false);
                    } else {
                        Log.e(ImsEditor.TAG, msg.what + " failed " + ar.exception.toString());
                        Toast toast = Toast.makeText(ImsEditor.this.getApplicationContext(), "Querying/Setting IMS Service Failed", 1);
                        toast.show();
                        ImsEditor.this.enablePref(ImsEditor.this.mUseAlwaysPref, true);
                    }
                    break;
                default:
                    Log.e(ImsEditor.TAG, "Unhandled message " + msg.what);
                    break;
            }
        }
    };
    private ServiceConnection ImsServiceConnection = new ServiceConnection() { // from class: com.android.phone.ims.ImsEditor.2
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(ImsEditor.TAG, "ImsEditor Ims Service Connected");
            ImsEditor.this.mImsService = IImsService.Stub.asInterface(service);
            if (ImsEditor.this.mImsService != null && !ImsEditor.this.mIsImsListenerRegistered) {
                try {
                    int result = ImsEditor.this.mImsService.registerCallback(ImsEditor.this.imsServListener);
                    if (result == 0) {
                        ImsEditor.this.mIsImsListenerRegistered = true;
                    }
                } catch (Exception e) {
                    Log.e(ImsEditor.TAG, "Exception in mImsService.registerCallback");
                }
                try {
                    ImsEditor.this.mImsService.queryImsServiceStatus(1, ImsEditor.this.createMessenger());
                    ImsEditor.this.enablePref(ImsEditor.this.mUseAlwaysPref, false);
                } catch (Exception e2) {
                    Log.e(ImsEditor.TAG, "Exception = " + e2);
                }
            }
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName arg0) {
            Log.v(ImsEditor.TAG, "Ims Service onServiceDisconnected");
            ImsEditor.this.mImsService = null;
        }
    };
    IImsServiceListener imsServListener = new IImsServiceListener.Stub() { // from class: com.android.phone.ims.ImsEditor.3
        @Override // org.codeaurora.ims.IImsServiceListener
        public void imsUpdateServiceStatus(int service, int status) {
            Log.v(ImsEditor.TAG, "imsUpdateServiceStatus response service " + service + "status = " + status);
            ImsEditor.this.mSharedPreferences.setImsSrvStatus(service, status);
            ImsEditor.this.loadPreferences();
        }

        @Override // org.codeaurora.ims.IImsServiceListener
        public void imsRegStateChanged(int imsRegState) {
        }

        @Override // org.codeaurora.ims.IImsServiceListener
        public void imsRegStateChangeReqFailed() {
        }
    };

    enum PreferenceKey {
        CALLTYPE(R.string.call_type, R.string.default_call_type, R.string.default_preference_summary);

        final int defaultSummary;
        final int initValue;
        Preference preference;
        final int text;

        PreferenceKey(int text, int initValue, int defaultSummary) {
            this.text = text;
            this.initValue = initValue;
            this.defaultSummary = defaultSummary;
        }

        String getValue() {
            if (this.preference instanceof ListPreference) {
                return ((ListPreference) this.preference).getValue();
            }
            throw new RuntimeException("getValue() for the preference " + this);
        }

        void setValue(String value) {
            if (this.preference instanceof ListPreference) {
                ((ListPreference) this.preference).setValue(value);
            }
            if (this.preference != null) {
                if (TextUtils.isEmpty(value)) {
                    this.preference.setSummary(this.defaultSummary);
                } else {
                    this.preference.setSummary(value);
                }
            }
            String oldValue = getValue();
            if (ImsEditor.DBG) {
                Log.v(ImsEditor.TAG, this + ": setValue() " + value + ": " + oldValue + " --> " + getValue());
            }
        }
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        getPreferenceScreen().setEnabled(true);
        if (this.mRemoveButton != null) {
            this.mRemoveButton.setEnabled(true);
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public void onCreate(Bundle savedInstanceState) {
        if (DBG) {
            Log.v(TAG, "start profile editor");
        }
        super.onCreate(savedInstanceState);
        this.mSharedPreferences = new ImsSharedPreferences(this);
        setContentView(R.layout.ims_settings_ui);
        addPreferencesFromResource(R.xml.ims_edit);
        PreferenceGroup screen = getPreferenceScreen();
        int n = screen.getPreferenceCount();
        for (int i = 0; i < n; i++) {
            setupPreference(screen.getPreference(i));
        }
        this.mUseAlwaysPref = (MultiSelectListPreference) getPreferenceScreen().findPreference(getString(R.string.ims_call_type_control));
        this.mCallTypePref = (ListPreference) getPreferenceScreen().findPreference(getString(R.string.call_type));
        screen.setTitle(R.string.ims_edit_title);
        bindImsService();
        loadPreferences();
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enablePref(Preference pref, boolean enable) {
        if (pref != null) {
            pref.setSelectable(enable);
            pref.setEnabled(enable);
        }
    }

    private void bindImsService() {
        try {
            boolean bound = bindService(new Intent("org.codeaurora.ims.IImsService"), this.ImsServiceConnection, 1);
            Log.v(TAG, "ImsEditor IMSService bound request" + bound);
        } catch (NoClassDefFoundError e) {
            Log.v(TAG, "Ignoring IMS class not found exception " + e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Messenger createMessenger() {
        Messenger msg = new Messenger(this.mHandler);
        return msg;
    }

    @Override // android.app.Activity
    public void onPause() {
        if (DBG) {
            Log.v(TAG, "ImsEditor onPause(): finishing? " + isFinishing());
        }
        if (!isFinishing()) {
            validateAndSetResult();
        }
        super.onPause();
    }

    @Override // android.preference.PreferenceActivity, android.app.ListActivity, android.app.Activity
    public void onDestroy() {
        super.onDestroy();
        if (this.mIsImsListenerRegistered) {
            try {
                this.mImsService.deregisterCallback(this.imsServListener);
                this.mIsImsListenerRegistered = false;
            } catch (Exception e) {
                Log.e(TAG, "Exception " + e);
            }
        }
        if (this.mImsService != null) {
            unbindService(this.ImsServiceConnection);
            this.mImsService = null;
        }
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 1, 0, R.string.ims_menu_save).setShowAsAction(1);
        menu.add(0, 2, 0, R.string.ims_menu_discard).setShowAsAction(1);
        menu.add(0, 3, 0, R.string.remove_ims_account).setShowAsAction(0);
        return true;
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
            case android.R.id.home:
                validateAndSetResult();
                return true;
            case 2:
                finish();
                return true;
            case 3:
                removePreferencesAndFinish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case 4:
                validateAndSetResult();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private String convertCallTypeToStr(int callType) {
        String callTypeStr = getResources().getString(R.string.default_call_type);
        switch (callType) {
            case 0:
                String callTypeStr2 = getResources().getString(R.string.ims_call_type_voice);
                return callTypeStr2;
            case 1:
            case 2:
            default:
                return callTypeStr;
            case 3:
                String callTypeStr3 = getResources().getString(R.string.ims_call_type_video);
                return callTypeStr3;
        }
    }

    private int convertCallTypeToInt(String callType) {
        if ("Voice".equalsIgnoreCase(callType)) {
            return 0;
        }
        if (!"Video".equalsIgnoreCase(callType)) {
            return 10;
        }
        return 3;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadPreferences() {
        HashSet<String> callTypeSet = new HashSet<>();
        boolean voiceSupp = this.mSharedPreferences.isImsSrvAllowed(0);
        boolean vtSupp = this.mSharedPreferences.isImsSrvAllowed(3);
        if (voiceSupp) {
            callTypeSet.add("Voice");
        }
        if (vtSupp) {
            callTypeSet.add("Video");
        }
        this.mUseAlwaysPref.setValues(callTypeSet);
        if (callTypeSet.isEmpty()) {
            this.mUseAlwaysPref.setSummary(R.string.ims_service_capability);
        } else {
            this.mUseAlwaysPref.setSummary(callTypeSet.toString());
        }
        this.mSharedPreferences.setIsImsCapEnabled(0, voiceSupp);
        this.mSharedPreferences.setIsImsCapEnabled(3, vtSupp);
        if (voiceSupp && vtSupp) {
            this.mCallTypePref.setEntries(R.array.ims_call_types_choices);
            this.mCallTypePref.setEntryValues(R.array.ims_call_types_values);
            this.mSharedPreferences.setCallTypeSelectable(true);
        } else if (voiceSupp) {
            this.mCallTypePref.setEntries(R.array.ims_voice_cs_call_types_choices);
            this.mCallTypePref.setEntryValues(R.array.ims_voice_cs_call_types_values);
            if (this.mSharedPreferences.getCallType() == 3) {
                this.mSharedPreferences.setCallType(0);
            }
            this.mSharedPreferences.setCallTypeSelectable(true);
        } else if (vtSupp) {
            this.mCallTypePref.setEntries(R.array.ims_video_cs_call_types_choices);
            this.mCallTypePref.setEntryValues(R.array.ims_video_cs_call_types_values);
            if (this.mSharedPreferences.getCallType() == 0) {
                this.mSharedPreferences.setCallType(10);
            }
            this.mSharedPreferences.setCallTypeSelectable(true);
        } else {
            this.mCallTypePref.setEntries(R.array.cs_call_type_choices);
            this.mCallTypePref.setEntryValues(R.array.cs_call_type_values);
            if (this.mSharedPreferences.getCallType() == 0 || this.mSharedPreferences.getCallType() == 3) {
                this.mSharedPreferences.setCallType(10);
            }
            this.mSharedPreferences.setCallTypeSelectable(false);
        }
        if (voiceSupp | vtSupp) {
            enablePref(this.mUseAlwaysPref, true);
        } else {
            enablePref(this.mUseAlwaysPref, false);
        }
        PreferenceKey.CALLTYPE.setValue(convertCallTypeToStr(this.mSharedPreferences.getCallType()));
        PreferenceKey.CALLTYPE.preference.setSelectable(this.mSharedPreferences.isCallTypeSelectable());
    }

    private void validateAndSetResult() {
        Log.v(TAG, "validateAndSetResult");
        this.mSharedPreferences.setCallType(convertCallTypeToInt(PreferenceKey.CALLTYPE.getValue()));
        if (this.mUseAlwaysPref.getSummary().toString().contains("Voice")) {
            this.mSharedPreferences.setIsImsCapEnabled(0, true);
        }
        if (this.mUseAlwaysPref.getSummary().toString().contains("Video")) {
            this.mSharedPreferences.setIsImsCapEnabled(3, true);
        }
        setResult(-1);
        Toast.makeText(this, R.string.saving_account, 0).show();
        finish();
    }

    private void removePreferencesAndFinish() {
        Log.v(TAG, "removePreferencesAndFinish");
        this.mSharedPreferences.setCallType(10);
        this.mSharedPreferences.setIsImsCapEnabled(0, false);
        this.mSharedPreferences.setIsImsCapEnabled(3, false);
        setResult(-1);
        finish();
    }

    /* JADX WARN: Code duplicated, block: B:19:0x0077  */
    /* JADX WARN: Code duplicated, block: B:6:0x0029 A[Catch: Exception -> 0x007b, TryCatch #0 {Exception -> 0x007b, blocks: (B:4:0x0020, B:11:0x0049, B:17:0x0070, B:13:0x0052, B:16:0x0067, B:6:0x0029, B:9:0x003e), top: B:23:0x0020 }] */
    /* JADX WARN: Code duplicated, block: B:8:0x003d  */
    private void handleCallDefaultPrefChange(Preference pref, Object newValue) {
        int i;
        boolean hasVoice = pref.getSummary().toString().contains("Voice");
        boolean hasVT = pref.getSummary().toString().contains("Video");
        if (!hasVoice) {
            Log.d(TAG, "Voice Pref Changed - sending SET Request");
            IImsService iImsService = this.mImsService;
            if (this.mSharedPreferences.getisImsCapEnabled(0)) {
                i = 0;
            } else {
                i = 1;
            }
            iImsService.setServiceStatus(0, -1, i, 0, 2, createMessenger());
        } else {
            try {
                if (!this.mSharedPreferences.getisImsCapEnabled(0)) {
                    Log.d(TAG, "Voice Pref Changed - sending SET Request");
                    IImsService iImsService2 = this.mImsService;
                    if (this.mSharedPreferences.getisImsCapEnabled(0)) {
                        i = 0;
                    } else {
                        i = 1;
                    }
                    iImsService2.setServiceStatus(0, -1, i, 0, 2, createMessenger());
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception " + e);
                return;
            }
        }
        if (!hasVT || !this.mSharedPreferences.getisImsCapEnabled(3)) {
            Log.d(TAG, "Video Pref Changed - sending SET Request");
            this.mImsService.setServiceStatus(3, -1, this.mSharedPreferences.getisImsCapEnabled(3) ? 0 : 1, 0, 2, createMessenger());
        }
        enablePref(this.mUseAlwaysPref, false);
    }

    @Override // android.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        String summary;
        String value = newValue == null ? "" : newValue.toString();
        if (pref.equals(this.mUseAlwaysPref) && ((HashSet) newValue).isEmpty()) {
            summary = getString(R.string.ims_service_capability);
        } else {
            summary = value;
        }
        if (TextUtils.isEmpty(value) && summary == null) {
            pref.setSummary(getPreferenceKey(pref).defaultSummary);
        } else if (summary != null) {
            pref.setSummary(summary);
        }
        if (pref.equals(this.mUseAlwaysPref)) {
            handleCallDefaultPrefChange(pref, newValue);
            return true;
        }
        return true;
    }

    private PreferenceKey getPreferenceKey(Preference pref) {
        PreferenceKey[] arr$ = PreferenceKey.values();
        for (PreferenceKey key : arr$) {
            if (key.preference == pref) {
                return key;
            }
        }
        throw new RuntimeException("not possible to reach here");
    }

    private void setupPreference(Preference pref) {
        pref.setOnPreferenceChangeListener(this);
        PreferenceKey[] arr$ = PreferenceKey.values();
        for (PreferenceKey key : arr$) {
            String name = getString(key.text);
            if (name.equals(pref.getKey())) {
                key.preference = pref;
                return;
            }
        }
    }
}
