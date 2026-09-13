package com.android.phone.sip;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
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
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.R;
import com.android.phone.SipUtil;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class SipEditor extends PreferenceActivity implements Preference.OnPreferenceChangeListener {
    private static final String TAG = SipEditor.class.getSimpleName();
    private AdvancedSettings mAdvancedSettings;
    private CallManager mCallManager;
    private boolean mDisplayNameSet;
    private boolean mHomeButtonClicked;
    private SipProfile mOldProfile;
    private PrimaryAccountSelector mPrimaryAccountSelector;
    private SipProfileDb mProfileDb;
    private Button mRemoveButton;
    private SipSharedPreferences mSharedPreferences;
    private SipManager mSipManager;
    private boolean mUpdateRequired;

    enum PreferenceKey {
        Username(R.string.username, 0, R.string.default_preference_summary),
        Password(R.string.password, 0, R.string.default_preference_summary),
        DomainAddress(R.string.domain_address, 0, R.string.default_preference_summary),
        DisplayName(R.string.display_name, 0, R.string.display_name_summary),
        ProxyAddress(R.string.proxy_address, 0, R.string.optional_summary),
        Port(R.string.port, R.string.default_port, R.string.default_port),
        Transport(R.string.transport, R.string.default_transport, 0),
        SendKeepAlive(R.string.send_keepalive, R.string.sip_system_decide, 0),
        AuthUserName(R.string.auth_username, 0, R.string.optional_summary);

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
            if (this.preference instanceof EditTextPreference) {
                return ((EditTextPreference) this.preference).getText();
            }
            if (this.preference instanceof ListPreference) {
                return ((ListPreference) this.preference).getValue();
            }
            throw new RuntimeException("getValue() for the preference " + this);
        }

        void setValue(String value) {
            if (this.preference instanceof EditTextPreference) {
                String oldValue = getValue();
                ((EditTextPreference) this.preference).setText(value);
                if (this != Password) {
                    Log.v(SipEditor.TAG, this + ": setValue() " + value + ": " + oldValue + " --> " + getValue());
                }
            } else if (this.preference instanceof ListPreference) {
                ((ListPreference) this.preference).setValue(value);
            }
            if (TextUtils.isEmpty(value)) {
                this.preference.setSummary(this.defaultSummary);
                return;
            }
            if (this == Password) {
                this.preference.setSummary(SipEditor.scramble(value));
            } else if (this == DisplayName && value.equals(SipEditor.getDefaultDisplayName())) {
                this.preference.setSummary(this.defaultSummary);
            } else {
                this.preference.setSummary(value);
            }
        }
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        this.mHomeButtonClicked = false;
        if (this.mCallManager.getState() != PhoneConstants.State.IDLE) {
            this.mAdvancedSettings.show();
            getPreferenceScreen().setEnabled(false);
            if (this.mRemoveButton != null) {
                this.mRemoveButton.setEnabled(false);
                return;
            }
            return;
        }
        getPreferenceScreen().setEnabled(true);
        if (this.mRemoveButton != null) {
            this.mRemoveButton.setEnabled(true);
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "start profile editor");
        super.onCreate(savedInstanceState);
        this.mSipManager = SipManager.newInstance(this);
        this.mSharedPreferences = new SipSharedPreferences(this);
        this.mProfileDb = new SipProfileDb(this);
        this.mCallManager = CallManager.getInstance();
        setContentView(R.layout.sip_settings_ui);
        addPreferencesFromResource(R.xml.sip_edit);
        SipProfile p = (SipProfile) (savedInstanceState == null ? getIntent().getParcelableExtra("sip_profile") : savedInstanceState.getParcelable("profile"));
        this.mOldProfile = p;
        PreferenceGroup screen = getPreferenceScreen();
        int n = screen.getPreferenceCount();
        for (int i = 0; i < n; i++) {
            setupPreference(screen.getPreference(i));
        }
        if (p == null) {
            screen.setTitle(R.string.sip_edit_new_title);
        }
        this.mAdvancedSettings = new AdvancedSettings();
        this.mPrimaryAccountSelector = new PrimaryAccountSelector(this, p);
        loadPreferencesFromProfile(p);
    }

    @Override // android.app.Activity
    public void onPause() {
        Log.v(TAG, "SipEditor onPause(): finishing? " + isFinishing());
        if (!isFinishing()) {
            this.mHomeButtonClicked = true;
        }
        super.onPause();
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 2, 0, R.string.sip_menu_discard).setShowAsAction(1);
        menu.add(0, 1, 0, R.string.sip_menu_save).setShowAsAction(1);
        menu.add(0, 3, 0, R.string.remove_sip_account).setShowAsAction(0);
        return true;
    }

    @Override // android.app.Activity
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem removeMenu = menu.findItem(3);
        removeMenu.setVisible(this.mOldProfile != null);
        menu.findItem(1).setEnabled(this.mUpdateRequired);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                validateAndSetResult();
                return true;
            case 2:
                finish();
                return true;
            case 3:
                setRemovedProfileAndFinish();
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

    /* JADX INFO: Access modifiers changed from: private */
    public void saveAndRegisterProfile(SipProfile p) throws IOException {
        if (p != null) {
            this.mProfileDb.saveProfile(p);
            if (p.getAutoRegistration() || this.mSharedPreferences.isPrimaryAccount(p.getUriString())) {
                try {
                    this.mSipManager.open(p, SipUtil.createIncomingCallPendingIntent(), null);
                } catch (Exception e) {
                    Log.e(TAG, "register failed: " + p.getUriString(), e);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void deleteAndUnregisterProfile(SipProfile p) {
        if (p != null) {
            this.mProfileDb.deleteProfile(p);
            unregisterProfile(p.getUriString());
        }
    }

    private void unregisterProfile(String uri) {
        try {
            this.mSipManager.close(uri);
        } catch (Exception e) {
            Log.e(TAG, "unregister failed: " + uri, e);
        }
    }

    private void setRemovedProfileAndFinish() {
        setResult(1, new Intent(this, (Class<?>) SipSettings.class));
        Toast.makeText(this, R.string.removing_account, 0).show();
        replaceProfile(this.mOldProfile, null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAlert(Throwable e) {
        String msg = e.getMessage();
        if (TextUtils.isEmpty(msg)) {
            msg = e.toString();
        }
        showAlert(msg);
    }

    private void showAlert(final String message) {
        if (this.mHomeButtonClicked) {
            Log.v(TAG, "Home button clicked, don't show dialog: " + message);
        } else {
            runOnUiThread(new Runnable() { // from class: com.android.phone.sip.SipEditor.1
                @Override // java.lang.Runnable
                public void run() {
                    new AlertDialog.Builder(SipEditor.this).setTitle(android.R.string.dialog_alert_title).setIconAttribute(android.R.attr.alertDialogIcon).setMessage(message).setPositiveButton(R.string.alert_dialog_ok, (DialogInterface.OnClickListener) null).show();
                }
            });
        }
    }

    private boolean isEditTextEmpty(PreferenceKey key) {
        EditTextPreference pref = (EditTextPreference) key.preference;
        return TextUtils.isEmpty(pref.getText()) || pref.getSummary().equals(getString(key.defaultSummary));
    }

    private void validateAndSetResult() {
        CharSequence title;
        int i;
        CharSequence charSequence = null;
        PreferenceKey[] preferenceKeyArrValues = PreferenceKey.values();
        int length = preferenceKeyArrValues.length;
        int i2 = 0;
        boolean z = true;
        while (i2 < length) {
            PreferenceKey preferenceKey = preferenceKeyArrValues[i2];
            Preference preference = preferenceKey.preference;
            if (preference instanceof EditTextPreference) {
                EditTextPreference editTextPreference = (EditTextPreference) preference;
                boolean zIsEditTextEmpty = isEditTextEmpty(preferenceKey);
                if (z && !zIsEditTextEmpty) {
                    z = false;
                }
                if (zIsEditTextEmpty) {
                    switch (preferenceKey) {
                        case DisplayName:
                            editTextPreference.setText(getDefaultDisplayName());
                            title = charSequence;
                            continue;
                        case AuthUserName:
                        case ProxyAddress:
                            title = charSequence;
                            continue;
                        case Port:
                            editTextPreference.setText(getString(R.string.default_port));
                            title = charSequence;
                            continue;
                        default:
                            if (charSequence == null) {
                                title = editTextPreference.getTitle();
                                continue;
                            }
                            break;
                    }
                } else if (preferenceKey == PreferenceKey.Port) {
                    try {
                        i = Integer.parseInt(PreferenceKey.Port.getValue());
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Get port failed", e);
                        i = 0;
                    }
                    if (i < 1000 || i > 65534) {
                        showAlert(getString(R.string.not_a_valid_port));
                        return;
                    }
                }
                title = charSequence;
            } else {
                title = charSequence;
            }
            i2++;
            z = z;
            charSequence = title;
        }
        if (z || !this.mUpdateRequired) {
            finish();
            return;
        }
        if (charSequence != null) {
            showAlert(getString(R.string.empty_alert, new Object[]{charSequence}));
            return;
        }
        try {
            SipProfile sipProfileCreateSipProfile = createSipProfile();
            Intent intent = new Intent(this, (Class<?>) SipSettings.class);
            intent.putExtra("sip_profile", (Parcelable) sipProfileCreateSipProfile);
            setResult(-1, intent);
            Toast.makeText(this, R.string.saving_account, 0).show();
            replaceProfile(this.mOldProfile, sipProfileCreateSipProfile);
        } catch (Exception e2) {
            Log.w(TAG, "Can not create new SipProfile", e2);
            showAlert(getString(R.string.invalid_username_or_hostname));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unregisterOldPrimaryAccount() {
        String primaryAccountUri = this.mSharedPreferences.getPrimaryAccount();
        Log.v(TAG, "old primary: " + primaryAccountUri);
        if (primaryAccountUri != null && !this.mSharedPreferences.isReceivingCallsEnabled()) {
            Log.v(TAG, "unregister old primary: " + primaryAccountUri);
            unregisterProfile(primaryAccountUri);
        }
    }

    private void replaceProfile(final SipProfile oldProfile, final SipProfile newProfile) {
        new Thread(new Runnable() { // from class: com.android.phone.sip.SipEditor.2
            @Override // java.lang.Runnable
            public void run() {
                try {
                    if (newProfile != null && SipEditor.this.mPrimaryAccountSelector.isSelected()) {
                        SipEditor.this.unregisterOldPrimaryAccount();
                    }
                    SipEditor.this.mPrimaryAccountSelector.commit(newProfile);
                    SipEditor.this.deleteAndUnregisterProfile(oldProfile);
                    SipEditor.this.saveAndRegisterProfile(newProfile);
                    SipEditor.this.finish();
                } catch (Exception e) {
                    Log.e(SipEditor.TAG, "Can not save/register new SipProfile", e);
                    SipEditor.this.showAlert(e);
                }
            }
        }, "SipEditor").start();
    }

    private String getProfileName() {
        return PreferenceKey.Username.getValue() + "@" + PreferenceKey.DomainAddress.getValue();
    }

    private SipProfile createSipProfile() throws Exception {
        return new SipProfile.Builder(PreferenceKey.Username.getValue(), PreferenceKey.DomainAddress.getValue()).setProfileName(getProfileName()).setPassword(PreferenceKey.Password.getValue()).setOutboundProxy(PreferenceKey.ProxyAddress.getValue()).setProtocol(PreferenceKey.Transport.getValue()).setDisplayName(PreferenceKey.DisplayName.getValue()).setPort(Integer.parseInt(PreferenceKey.Port.getValue())).setSendKeepAlive(isAlwaysSendKeepAlive()).setAutoRegistration(this.mSharedPreferences.isReceivingCallsEnabled()).setAuthUserName(PreferenceKey.AuthUserName.getValue()).build();
    }

    @Override // android.preference.Preference.OnPreferenceChangeListener
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        if (!this.mUpdateRequired) {
            this.mUpdateRequired = true;
            if (this.mOldProfile != null) {
                unregisterProfile(this.mOldProfile.getUriString());
            }
        }
        if (pref instanceof CheckBoxPreference) {
            invalidateOptionsMenu();
        } else {
            String value = newValue == null ? "" : newValue.toString();
            if (TextUtils.isEmpty(value)) {
                pref.setSummary(getPreferenceKey(pref).defaultSummary);
            } else if (pref == PreferenceKey.Password.preference) {
                pref.setSummary(scramble(value));
            } else {
                pref.setSummary(value);
            }
            if (pref == PreferenceKey.DisplayName.preference) {
                ((EditTextPreference) pref).setText(value);
                checkIfDisplayNameSet();
            }
            invalidateOptionsMenu();
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

    private void loadPreferencesFromProfile(SipProfile sipProfile) {
        if (sipProfile != null) {
            Log.v(TAG, "Edit the existing profile : " + sipProfile.getProfileName());
            try {
                for (PreferenceKey preferenceKey : PreferenceKey.values()) {
                    Method method = SipProfile.class.getMethod("get" + getString(preferenceKey.text), (Class[]) null);
                    if (preferenceKey == PreferenceKey.SendKeepAlive) {
                        preferenceKey.setValue(getString(((Boolean) method.invoke(sipProfile, (Object[]) null)).booleanValue() ? R.string.sip_always_send_keepalive : R.string.sip_system_decide));
                    } else {
                        Object objInvoke = method.invoke(sipProfile, (Object[]) null);
                        preferenceKey.setValue(objInvoke == null ? "" : objInvoke.toString());
                    }
                }
                checkIfDisplayNameSet();
                return;
            } catch (Exception e) {
                Log.e(TAG, "Can not load pref from profile", e);
                return;
            }
        }
        Log.v(TAG, "Edit a new profile");
        for (PreferenceKey preferenceKey2 : PreferenceKey.values()) {
            preferenceKey2.preference.setOnPreferenceChangeListener(this);
            if (preferenceKey2.initValue != 0) {
                preferenceKey2.setValue(getString(preferenceKey2.initValue));
            }
        }
        this.mDisplayNameSet = false;
    }

    private boolean isAlwaysSendKeepAlive() {
        ListPreference pref = (ListPreference) PreferenceKey.SendKeepAlive.preference;
        return getString(R.string.sip_always_send_keepalive).equals(pref.getValue());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setupPreference(Preference pref) {
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

    private void checkIfDisplayNameSet() {
        String displayName = PreferenceKey.DisplayName.getValue();
        this.mDisplayNameSet = (TextUtils.isEmpty(displayName) || displayName.equals(getDefaultDisplayName())) ? false : true;
        Log.d(TAG, "displayName set? " + this.mDisplayNameSet);
        if (this.mDisplayNameSet) {
            PreferenceKey.DisplayName.preference.setSummary(displayName);
        } else {
            PreferenceKey.DisplayName.setValue("");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String getDefaultDisplayName() {
        return PreferenceKey.Username.getValue();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String scramble(String s) {
        char[] cc = new char[s.length()];
        Arrays.fill(cc, '*');
        return new String(cc);
    }

    private class PrimaryAccountSelector {
        private CheckBoxPreference mCheckbox;
        private final boolean mWasPrimaryAccount;
        final /* synthetic */ SipEditor this$0;

        PrimaryAccountSelector(SipEditor sipEditor, SipProfile profile) {
            boolean z = false;
            this.this$0 = sipEditor;
            this.mCheckbox = (CheckBoxPreference) sipEditor.getPreferenceScreen().findPreference(sipEditor.getString(R.string.set_primary));
            boolean noPrimaryAccountSet = !sipEditor.mSharedPreferences.hasPrimaryAccount();
            boolean editNewProfile = profile == null;
            this.mWasPrimaryAccount = !editNewProfile && sipEditor.mSharedPreferences.isPrimaryAccount(profile.getUriString());
            Log.v(SipEditor.TAG, " noPrimaryAccountSet: " + noPrimaryAccountSet);
            Log.v(SipEditor.TAG, " editNewProfile: " + editNewProfile);
            Log.v(SipEditor.TAG, " mWasPrimaryAccount: " + this.mWasPrimaryAccount);
            CheckBoxPreference checkBoxPreference = this.mCheckbox;
            if (this.mWasPrimaryAccount || (editNewProfile && noPrimaryAccountSet)) {
                z = true;
            }
            checkBoxPreference.setChecked(z);
        }

        boolean isSelected() {
            return this.mCheckbox.isChecked();
        }

        void commit(SipProfile profile) {
            if (profile != null && this.mCheckbox.isChecked()) {
                this.this$0.mSharedPreferences.setPrimaryAccount(profile.getUriString());
            } else if (this.mWasPrimaryAccount) {
                this.this$0.mSharedPreferences.unsetPrimaryAccount();
            }
            Log.d(SipEditor.TAG, " primary account changed to : " + this.this$0.mSharedPreferences.getPrimaryAccount());
        }
    }

    private class AdvancedSettings implements Preference.OnPreferenceClickListener {
        private Preference mAdvancedSettingsTrigger;
        private Preference[] mPreferences;
        private boolean mShowing = false;

        AdvancedSettings() {
            this.mAdvancedSettingsTrigger = SipEditor.this.getPreferenceScreen().findPreference(SipEditor.this.getString(R.string.advanced_settings));
            this.mAdvancedSettingsTrigger.setOnPreferenceClickListener(this);
            loadAdvancedPreferences();
        }

        private void loadAdvancedPreferences() {
            PreferenceGroup screen = SipEditor.this.getPreferenceScreen();
            SipEditor.this.addPreferencesFromResource(R.xml.sip_advanced_edit);
            PreferenceGroup group = (PreferenceGroup) screen.findPreference(SipEditor.this.getString(R.string.advanced_settings_container));
            screen.removePreference(group);
            this.mPreferences = new Preference[group.getPreferenceCount()];
            int order = screen.getPreferenceCount();
            int i = 0;
            int n = this.mPreferences.length;
            int order2 = order;
            while (i < n) {
                Preference pref = group.getPreference(i);
                pref.setOrder(order2);
                SipEditor.this.setupPreference(pref);
                this.mPreferences[i] = pref;
                i++;
                order2++;
            }
        }

        void show() {
            this.mShowing = true;
            this.mAdvancedSettingsTrigger.setSummary(R.string.advanced_settings_hide);
            PreferenceGroup screen = SipEditor.this.getPreferenceScreen();
            Preference[] arr$ = this.mPreferences;
            for (Preference pref : arr$) {
                screen.addPreference(pref);
                Log.v(SipEditor.TAG, "add pref " + pref.getKey() + ": order=" + pref.getOrder());
            }
        }

        private void hide() {
            this.mShowing = false;
            this.mAdvancedSettingsTrigger.setSummary(R.string.advanced_settings_show);
            PreferenceGroup screen = SipEditor.this.getPreferenceScreen();
            Preference[] arr$ = this.mPreferences;
            for (Preference pref : arr$) {
                screen.removePreference(pref);
            }
        }

        @Override // android.preference.Preference.OnPreferenceClickListener
        public boolean onPreferenceClick(Preference preference) {
            Log.v(SipEditor.TAG, "optional settings clicked");
            if (!this.mShowing) {
                show();
                return true;
            }
            hide();
            return true;
        }
    }
}
