package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

/* JADX INFO: loaded from: classes.dex */
public class UPLMNEditor extends PreferenceActivity implements Preference.OnPreferenceChangeListener, TextWatcher {
    private IntentFilter mIntentFilter;
    private EditText mNWIDText;
    private Preference mNWIDPref = null;
    private EditTextPreference mPRIpref = null;
    private ListPreference mNWMPref = null;
    private String mNoSet = null;
    private boolean mAirplaneModeOn = false;
    private AlertDialog mNWIDDialog = null;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() { // from class: com.android.phone.UPLMNEditor.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.intent.action.AIRPLANE_MODE".equals(action)) {
                UPLMNEditor.this.mAirplaneModeOn = intent.getBooleanExtra("state", false);
                UPLMNEditor.this.setScreenEnabled();
            }
        }
    };
    private DialogInterface.OnClickListener mNWIDPrefListener = new DialogInterface.OnClickListener() { // from class: com.android.phone.UPLMNEditor.2
        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            if (which == -1) {
                String summary = UPLMNEditor.this.genText(UPLMNEditor.this.mNWIDText.getText().toString());
                Log.d("UPLMNEditor", "input network id is " + summary);
                UPLMNEditor.this.mNWIDPref.setSummary(summary);
            }
        }
    };

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.uplmn_editor);
        this.mNoSet = getResources().getString(R.string.voicemail_number_not_set);
        this.mNWIDPref = findPreference("network_id_key");
        this.mPRIpref = (EditTextPreference) findPreference("priority_key");
        this.mNWMPref = (ListPreference) findPreference("network_mode_key");
        this.mPRIpref.setOnPreferenceChangeListener(this);
        this.mNWMPref.setOnPreferenceChangeListener(this);
        this.mIntentFilter = new IntentFilter("android.intent.action.AIRPLANE_MODE");
        registerReceiver(this.mReceiver, this.mIntentFilter);
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        displayNetworkInfo(getIntent());
        this.mAirplaneModeOn = Settings.System.getInt(getContentResolver(), "airplane_mode_on", -1) == 1;
        setScreenEnabled();
    }

    @Override // android.preference.PreferenceActivity, android.app.ListActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    @Override // android.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference preference, Object object) {
        String value = object.toString();
        if (preference == this.mPRIpref) {
            this.mPRIpref.setSummary(genText(value));
            return true;
        }
        if (preference == this.mNWMPref) {
            this.mNWMPref.setValue(value);
            int index = Integer.parseInt(value);
            String summary = getResources().getStringArray(R.array.uplmn_prefer_network_mode_td_choices)[index];
            this.mNWMPref.setSummary(summary);
            return true;
        }
        return true;
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (!getIntent().getBooleanExtra("uplmn_add", false)) {
            menu.add(0, 1, 0, android.R.string.face_acquired_too_left);
        }
        menu.add(0, 2, 0, R.string.save);
        menu.add(0, 3, 0, android.R.string.cancel);
        return true;
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public boolean onMenuOpened(int featureId, Menu menu) {
        boolean z = false;
        super.onMenuOpened(featureId, menu);
        boolean isEmpty = this.mNoSet.equals(this.mNWIDPref.getSummary()) || this.mNoSet.equals(this.mPRIpref.getSummary());
        if (menu != null) {
            menu.setGroupEnabled(0, !this.mAirplaneModeOn);
            if (getIntent().getBooleanExtra("uplmn_add", true)) {
                MenuItem item = menu.getItem(0);
                if (!this.mAirplaneModeOn && !isEmpty) {
                    z = true;
                }
                item.setEnabled(z);
            } else {
                MenuItem item2 = menu.getItem(1);
                if (!this.mAirplaneModeOn && !isEmpty) {
                    z = true;
                }
                item2.setEnabled(z);
            }
        }
        return true;
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                setRemovedNWInfo();
                break;
            case 2:
                setSavedNWInfo();
                break;
            case android.R.id.home:
                finish();
                return true;
        }
        finish();
        return super.onOptionsItemSelected(item);
    }

    private void setSavedNWInfo() {
        Intent intent = new Intent(this, (Class<?>) UserPLMNListPreference.class);
        genNWInfoToIntent(intent);
        setResult(101, intent);
    }

    private void genNWInfoToIntent(Intent intent) {
        intent.putExtra("uplmn_code", this.mNWIDPref.getSummary());
        int priority = 0;
        int size = getIntent().getIntExtra("uplmn_size", 0);
        try {
            priority = Integer.parseInt(String.valueOf(this.mPRIpref.getSummary()));
        } catch (NumberFormatException e) {
            Log.d("UPLMNEditor", "parse value of basband error");
        }
        if (getIntent().getBooleanExtra("uplmn_add", false)) {
            if (priority > size) {
                priority = size;
            }
        } else if (priority >= size) {
            priority = size - 1;
        }
        intent.putExtra("uplmn_priority", priority);
        try {
            intent.putExtra("uplmn_service", convertApMode2EF(Integer.parseInt(String.valueOf(this.mNWMPref.getValue()))));
        } catch (NumberFormatException e2) {
            intent.putExtra("uplmn_service", convertApMode2EF(0));
        }
    }

    private void setRemovedNWInfo() {
        Intent intent = new Intent(this, (Class<?>) UserPLMNListPreference.class);
        genNWInfoToIntent(intent);
        setResult(102, intent);
    }

    public static int convertEFMode2Ap(int mode) {
        if (mode == 13) {
            return 3;
        }
        if (mode == 4) {
            return 1;
        }
        if (mode == 8) {
            return 2;
        }
        return 0;
    }

    public static int convertApMode2EF(int mode) {
        if (mode == 3) {
            return 13;
        }
        if (mode == 2) {
            return 8;
        }
        if (mode == 1) {
            return 4;
        }
        return 1;
    }

    private void displayNetworkInfo(Intent intent) {
        String number = intent.getStringExtra("uplmn_code");
        this.mNWIDPref.setSummary(genText(number));
        int priority = intent.getIntExtra("uplmn_priority", 0);
        this.mPRIpref.setSummary(String.valueOf(priority));
        this.mPRIpref.setText(String.valueOf(priority));
        int act = intent.getIntExtra("uplmn_service", 0);
        Log.d("UPLMNEditor", "act = " + act);
        int act2 = convertEFMode2Ap(act);
        if (act2 < 0 || act2 > 3) {
            act2 = 0;
        }
        this.mNWMPref.setEntries(getResources().getTextArray(R.array.uplmn_prefer_network_mode_td_choices));
        String summary = getResources().getStringArray(R.array.uplmn_prefer_network_mode_td_choices)[act2];
        this.mNWMPref.setSummary(summary);
        this.mNWMPref.setValue(String.valueOf(act2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String genText(String value) {
        if (value == null || value.length() == 0) {
            return this.mNoSet;
        }
        return value;
    }

    public void buttonEnabled() {
        int len = this.mNWIDText.getText().toString().length();
        boolean state = true;
        if (len < 5 || len > 6) {
            state = false;
        }
        if (this.mNWIDDialog != null) {
            this.mNWIDDialog.getButton(-1).setEnabled(state);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setScreenEnabled() {
        getPreferenceScreen().setEnabled(!this.mAirplaneModeOn);
        invalidateOptionsMenu();
    }

    @Override // android.preference.PreferenceActivity
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference == this.mNWIDPref) {
            removeDialog(0);
            showDialog(0);
            buttonEnabled();
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    @Override // android.app.Activity
    public Dialog onCreateDialog(int id) {
        if (id != 0) {
            return null;
        }
        this.mNWIDText = new EditText(this);
        if (!this.mNoSet.equals(this.mNWIDPref.getSummary())) {
            this.mNWIDText.setText(this.mNWIDPref.getSummary());
        }
        this.mNWIDText.addTextChangedListener(this);
        this.mNWIDText.setInputType(2);
        this.mNWIDDialog = new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.network_id)).setView(this.mNWIDText).setPositiveButton(getResources().getString(android.R.string.ok), this.mNWIDPrefListener).setNegativeButton(getResources().getString(android.R.string.cancel), (DialogInterface.OnClickListener) null).create();
        this.mNWIDDialog.getWindow().setSoftInputMode(4);
        return this.mNWIDDialog;
    }

    @Override // android.text.TextWatcher
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override // android.text.TextWatcher
    public void afterTextChanged(Editable s) {
        buttonEnabled();
    }

    @Override // android.text.TextWatcher
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
}
