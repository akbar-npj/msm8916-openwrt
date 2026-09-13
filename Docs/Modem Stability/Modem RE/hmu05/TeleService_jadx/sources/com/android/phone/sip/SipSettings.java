package com.android.phone.sip;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.phone.CallFeaturesSetting;
import com.android.phone.MSimCallFeaturesSetting;
import com.android.phone.R;
import com.android.phone.SipUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class SipSettings extends PreferenceActivity {
    private CheckBoxPreference mButtonSipReceiveCalls;
    private CallManager mCallManager;
    private PackageManager mPackageManager;
    private SipProfile mProfile;
    private SipProfileDb mProfileDb;
    private PreferenceCategory mSipListContainer;
    private SipManager mSipManager;
    private Map<String, SipPreference> mSipPreferenceMap;
    private List<SipProfile> mSipProfileList;
    private SipSharedPreferences mSipSharedPreferences;
    private int mUid = Process.myUid();

    private class SipPreference extends Preference {
        SipProfile mProfile;

        SipPreference(Context c, SipProfile p) {
            super(c);
            setProfile(p);
        }

        void setProfile(SipProfile p) {
            this.mProfile = p;
            setTitle(SipSettings.this.getProfileName(p));
            updateSummary(SipSettings.this.mSipSharedPreferences.isReceivingCallsEnabled() ? SipSettings.this.getString(R.string.registration_status_checking_status) : SipSettings.this.getString(R.string.registration_status_not_receiving));
        }

        void updateSummary(String registrationStatus) {
            String summary;
            int profileUid = this.mProfile.getCallingUid();
            boolean isPrimary = this.mProfile.getUriString().equals(SipSettings.this.mSipSharedPreferences.getPrimaryAccount());
            Log.v("SipSettings", "profile uid is " + profileUid + " isPrimary:" + isPrimary + " registration:" + registrationStatus + " Primary:" + SipSettings.this.mSipSharedPreferences.getPrimaryAccount() + " status:" + registrationStatus);
            if (profileUid > 0 && profileUid != SipSettings.this.mUid) {
                summary = SipSettings.this.getString(R.string.third_party_account_summary, new Object[]{SipSettings.this.getPackageNameFromUid(profileUid)});
            } else if (isPrimary) {
                summary = SipSettings.this.getString(R.string.primary_account_summary_with, new Object[]{registrationStatus});
            } else {
                summary = registrationStatus;
            }
            setSummary(summary);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String getPackageNameFromUid(int uid) {
        try {
            String[] pkgs = this.mPackageManager.getPackagesForUid(uid);
            ApplicationInfo ai = this.mPackageManager.getApplicationInfo(pkgs[0], 0);
            return ai.loadLabel(this.mPackageManager).toString();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SipSettings", "cannot find name of uid " + uid, e);
            return "uid:" + uid;
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mSipManager = SipManager.newInstance(this);
        this.mSipSharedPreferences = new SipSharedPreferences(this);
        this.mProfileDb = new SipProfileDb(this);
        this.mPackageManager = getPackageManager();
        setContentView(R.layout.sip_settings_ui);
        addPreferencesFromResource(R.xml.sip_setting);
        this.mSipListContainer = (PreferenceCategory) findPreference("sip_account_list");
        registerForReceiveCallsCheckBox();
        this.mCallManager = CallManager.getInstance();
        updateProfilesStatus();
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        if (this.mCallManager.getState() != PhoneConstants.State.IDLE) {
            this.mButtonSipReceiveCalls.setEnabled(false);
        } else {
            this.mButtonSipReceiveCalls.setEnabled(true);
        }
    }

    @Override // android.preference.PreferenceActivity, android.app.ListActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        unregisterForContextMenu(getListView());
    }

    /* JADX WARN: Type inference failed for: r0v2, types: [com.android.phone.sip.SipSettings$1] */
    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onActivityResult(int requestCode, final int resultCode, final Intent intent) {
        if (resultCode == -1 || resultCode == 1) {
            if (this.mSipProfileList == null) {
                Log.v("SipSettings", "mSipProfileList is null");
            } else {
                new Thread() { // from class: com.android.phone.sip.SipSettings.1
                    @Override // java.lang.Thread, java.lang.Runnable
                    public void run() {
                        try {
                            if (SipSettings.this.mProfile != null) {
                                Log.v("SipSettings", "Removed Profile:" + SipSettings.this.mProfile.getProfileName());
                                SipSettings.this.deleteProfile(SipSettings.this.mProfile);
                            }
                            SipProfile profile = (SipProfile) intent.getParcelableExtra("sip_profile");
                            if (resultCode == -1) {
                                Log.v("SipSettings", "New Profile Name:" + profile.getProfileName());
                                SipSettings.this.addProfile(profile);
                            }
                            SipSettings.this.updateProfilesStatus();
                        } catch (IOException e) {
                            Log.v("SipSettings", "Can not handle the profile : " + e.getMessage());
                        }
                    }
                }.start();
            }
        }
    }

    private void registerForReceiveCallsCheckBox() {
        this.mButtonSipReceiveCalls = (CheckBoxPreference) findPreference("sip_receive_calls_key");
        this.mButtonSipReceiveCalls.setChecked(this.mSipSharedPreferences.isReceivingCallsEnabled());
        this.mButtonSipReceiveCalls.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() { // from class: com.android.phone.sip.SipSettings.2
            @Override // android.preference.Preference.OnPreferenceClickListener
            public boolean onPreferenceClick(Preference preference) {
                final boolean enabled = ((CheckBoxPreference) preference).isChecked();
                new Thread(new Runnable() { // from class: com.android.phone.sip.SipSettings.2.1
                    @Override // java.lang.Runnable
                    public void run() {
                        SipSettings.this.handleSipReceiveCallsOption(enabled);
                    }
                }).start();
                return true;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void handleSipReceiveCallsOption(boolean enabled) {
        this.mSipSharedPreferences.setReceivingCallsEnabled(enabled);
        List<SipProfile> sipProfileList = this.mProfileDb.retrieveSipProfileList();
        for (SipProfile p : sipProfileList) {
            String sipUri = p.getUriString();
            SipProfile p2 = updateAutoRegistrationFlag(p, enabled);
            if (enabled) {
                try {
                    this.mSipManager.open(p2, SipUtil.createIncomingCallPendingIntent(), null);
                } catch (Exception e) {
                    Log.e("SipSettings", "register failed", e);
                }
            } else {
                this.mSipManager.close(sipUri);
                if (this.mSipSharedPreferences.isPrimaryAccount(sipUri)) {
                    this.mSipManager.open(p2);
                }
            }
        }
        updateProfilesStatus();
    }

    private SipProfile updateAutoRegistrationFlag(SipProfile p, boolean enabled) {
        SipProfile newProfile = new SipProfile.Builder(p).setAutoRegistration(enabled).build();
        try {
            this.mProfileDb.deleteProfile(p);
            this.mProfileDb.saveProfile(newProfile);
        } catch (Exception e) {
            Log.e("SipSettings", "updateAutoRegistrationFlag error", e);
        }
        return newProfile;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateProfilesStatus() {
        new Thread(new Runnable() { // from class: com.android.phone.sip.SipSettings.3
            @Override // java.lang.Runnable
            public void run() {
                try {
                    SipSettings.this.retrieveSipLists();
                } catch (Exception e) {
                    Log.e("SipSettings", "isRegistered", e);
                }
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String getProfileName(SipProfile profile) {
        String profileName = profile.getProfileName();
        if (TextUtils.isEmpty(profileName)) {
            return profile.getUserName() + "@" + profile.getSipDomain();
        }
        return profileName;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void retrieveSipLists() {
        this.mSipPreferenceMap = new LinkedHashMap();
        this.mSipProfileList = this.mProfileDb.retrieveSipProfileList();
        processActiveProfilesFromSipService();
        Collections.sort(this.mSipProfileList, new Comparator<SipProfile>() { // from class: com.android.phone.sip.SipSettings.4
            @Override // java.util.Comparator
            public int compare(SipProfile p1, SipProfile p2) {
                return SipSettings.this.getProfileName(p1).compareTo(SipSettings.this.getProfileName(p2));
            }
        });
        this.mSipListContainer.removeAll();
        Iterator<SipProfile> it = this.mSipProfileList.iterator();
        while (it.hasNext()) {
            addPreferenceFor(it.next());
        }
        if (this.mSipSharedPreferences.isReceivingCallsEnabled()) {
            for (SipProfile p : this.mSipProfileList) {
                if (this.mUid == p.getCallingUid()) {
                    try {
                        this.mSipManager.setRegistrationListener(p.getUriString(), createRegistrationListener());
                    } catch (SipException e) {
                        Log.e("SipSettings", "cannot set registration listener", e);
                    }
                }
            }
        }
    }

    private void processActiveProfilesFromSipService() {
        SipProfile[] activeList = this.mSipManager.getListOfProfiles();
        for (SipProfile activeProfile : activeList) {
            SipProfile profile = getProfileFromList(activeProfile);
            if (profile == null) {
                this.mSipProfileList.add(activeProfile);
            } else {
                profile.setCallingUid(activeProfile.getCallingUid());
            }
        }
    }

    private SipProfile getProfileFromList(SipProfile activeProfile) {
        for (SipProfile p : this.mSipProfileList) {
            if (p.getUriString().equals(activeProfile.getUriString())) {
                return p;
            }
        }
        return null;
    }

    private void addPreferenceFor(SipProfile p) {
        Log.v("SipSettings", "addPreferenceFor profile uri" + p.getUri());
        SipPreference pref = new SipPreference(this, p);
        this.mSipPreferenceMap.put(p.getUriString(), pref);
        this.mSipListContainer.addPreference(pref);
        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() { // from class: com.android.phone.sip.SipSettings.5
            @Override // android.preference.Preference.OnPreferenceClickListener
            public boolean onPreferenceClick(Preference pref2) {
                SipSettings.this.handleProfileClick(((SipPreference) pref2).mProfile);
                return true;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleProfileClick(final SipProfile profile) {
        int uid = profile.getCallingUid();
        if (uid == this.mUid || uid == 0) {
            startSipEditor(profile);
        } else {
            new AlertDialog.Builder(this).setTitle(R.string.alert_dialog_close).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(R.string.close_profile, new DialogInterface.OnClickListener() { // from class: com.android.phone.sip.SipSettings.6
                @Override // android.content.DialogInterface.OnClickListener
                public void onClick(DialogInterface dialog, int w) {
                    SipSettings.this.deleteProfile(profile);
                    SipSettings.this.unregisterProfile(profile);
                }
            }).setNegativeButton(android.R.string.cancel, (DialogInterface.OnClickListener) null).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void unregisterProfile(final SipProfile p) {
        new Thread(new Runnable() { // from class: com.android.phone.sip.SipSettings.7
            @Override // java.lang.Runnable
            public void run() {
                try {
                    SipSettings.this.mSipManager.close(p.getUriString());
                } catch (Exception e) {
                    Log.e("SipSettings", "unregister failed, SipService died?", e);
                }
            }
        }, "unregisterProfile").start();
    }

    void deleteProfile(SipProfile p) {
        this.mSipProfileList.remove(p);
        SipPreference pref = this.mSipPreferenceMap.remove(p.getUriString());
        this.mSipListContainer.removePreference(pref);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void addProfile(SipProfile p) throws IOException {
        try {
            this.mSipManager.setRegistrationListener(p.getUriString(), createRegistrationListener());
        } catch (Exception e) {
            Log.e("SipSettings", "cannot set registration listener", e);
        }
        this.mSipProfileList.add(p);
        addPreferenceFor(p);
    }

    private void startSipEditor(SipProfile sipProfile) {
        this.mProfile = sipProfile;
        Intent intent = new Intent(this, (Class<?>) SipEditor.class);
        intent.putExtra("sip_profile", (Parcelable) sipProfile);
        startActivityForResult(intent, 1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showRegistrationMessage(final String profileUri, final String message) {
        runOnUiThread(new Runnable() { // from class: com.android.phone.sip.SipSettings.8
            @Override // java.lang.Runnable
            public void run() {
                SipPreference pref = (SipPreference) SipSettings.this.mSipPreferenceMap.get(profileUri);
                if (pref != null) {
                    pref.updateSummary(message);
                }
            }
        });
    }

    private SipRegistrationListener createRegistrationListener() {
        return new SipRegistrationListener() { // from class: com.android.phone.sip.SipSettings.9
            @Override // android.net.sip.SipRegistrationListener
            public void onRegistrationDone(String profileUri, long expiryTime) {
                SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_done));
            }

            @Override // android.net.sip.SipRegistrationListener
            public void onRegistering(String profileUri) {
                SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_registering));
            }

            @Override // android.net.sip.SipRegistrationListener
            public void onRegistrationFailed(String profileUri, int errorCode, String message) {
                switch (errorCode) {
                    case -12:
                        SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_server_unreachable));
                        break;
                    case -11:
                    case -7:
                    case -6:
                    case -5:
                    default:
                        SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_failed_try_later, new Object[]{message}));
                        break;
                    case -10:
                        if (SipManager.isSipWifiOnly(SipSettings.this.getApplicationContext())) {
                            SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_no_wifi_data));
                        } else {
                            SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_no_data));
                        }
                        break;
                    case -9:
                        SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_still_trying));
                        break;
                    case -8:
                        SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_invalid_credentials));
                        break;
                    case -4:
                        SipSettings.this.showRegistrationMessage(profileUri, SipSettings.this.getString(R.string.registration_status_not_running));
                        break;
                }
            }
        };
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 1, 0, R.string.add_sip_account).setShowAsAction(1);
        return true;
    }

    @Override // android.app.Activity
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(1).setEnabled(this.mCallManager.getState() == PhoneConstants.State.IDLE);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override // android.preference.PreferenceActivity, android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case 1:
                startSipEditor(null);
                return true;
            case android.R.id.home:
                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    MSimCallFeaturesSetting.goUpToTopLevelSetting(this);
                    return true;
                }
                CallFeaturesSetting.goUpToTopLevelSetting(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
