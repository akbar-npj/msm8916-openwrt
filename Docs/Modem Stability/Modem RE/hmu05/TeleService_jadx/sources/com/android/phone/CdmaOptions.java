package com.android.phone;

import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.Phone;

/* JADX INFO: loaded from: classes.dex */
public class CdmaOptions {
    private PreferenceScreen mButtonAPNExpand;
    private CdmaSubscriptionListPreference mButtonCdmaSubscription;
    private CdmaSystemSelectListPreference mButtonCdmaSystemSelect;
    private PreferenceScreen mButtonOperatorSelectionExpand;
    private Phone mPhone;
    private PreferenceActivity mPrefActivity;
    private PreferenceScreen mPrefScreen;
    private int mSubscription;

    public CdmaOptions(PreferenceActivity prefActivity, PreferenceScreen prefScreen, Phone phone) {
        this.mSubscription = 0;
        this.mPrefActivity = prefActivity;
        this.mPrefScreen = prefScreen;
        this.mPhone = phone;
        this.mSubscription = 0;
        create();
    }

    public CdmaOptions(PreferenceActivity prefActivity, PreferenceScreen prefScreen, Phone phone, int subscription) {
        this.mSubscription = 0;
        this.mPrefActivity = prefActivity;
        this.mPrefScreen = prefScreen;
        this.mPhone = phone;
        this.mSubscription = subscription;
        create();
    }

    protected void create() {
        this.mPrefActivity.addPreferencesFromResource(R.xml.cdma_options);
        this.mButtonAPNExpand = (PreferenceScreen) this.mPrefScreen.findPreference("button_apn_key");
        this.mButtonAPNExpand.getIntent().putExtra("subscription", this.mSubscription);
        if (this.mPrefActivity.getResources().getBoolean(R.bool.world_phone)) {
            this.mPrefScreen.removePreference(this.mButtonAPNExpand);
        }
        this.mButtonCdmaSystemSelect = (CdmaSystemSelectListPreference) this.mPrefScreen.findPreference("cdma_system_select_key");
        this.mButtonCdmaSubscription = (CdmaSubscriptionListPreference) this.mPrefScreen.findPreference("cdma_subscription_key");
        this.mButtonCdmaSystemSelect.setEnabled(true);
        if (deviceSupportsNvAndRuim()) {
            if (this.mPrefActivity.getResources().getBoolean(R.bool.disable_cdma_subscription)) {
                this.mButtonCdmaSubscription.setEnabled(false);
            } else {
                log("Both NV and Ruim supported, ENABLE subscription type selection");
                this.mButtonCdmaSubscription.setEnabled(true);
            }
        } else {
            log("Both NV and Ruim NOT supported, REMOVE subscription type selection");
            this.mPrefScreen.removePreference(this.mPrefScreen.findPreference("cdma_subscription_key"));
        }
        this.mButtonOperatorSelectionExpand = (PreferenceScreen) this.mPrefScreen.findPreference("button_carrier_sel_key");
        this.mButtonOperatorSelectionExpand.setEnabled(false);
        boolean voiceCapable = this.mPrefActivity.getResources().getBoolean(android.R.bool.config_audio_ringer_mode_affects_alarm_stream);
        boolean isLTE = this.mPhone.getLteOnCdmaMode() == 1;
        if (voiceCapable || isLTE) {
            this.mPrefScreen.removePreference(this.mPrefScreen.findPreference("cdma_activate_device_key"));
        }
    }

    private boolean deviceSupportsNvAndRuim() {
        String subscriptionsSupported = SystemProperties.get("ril.subscription.types");
        boolean nvSupported = false;
        boolean ruimSupported = false;
        log("deviceSupportsnvAnRum: prop=" + subscriptionsSupported);
        if (!TextUtils.isEmpty(subscriptionsSupported)) {
            String[] arr$ = subscriptionsSupported.split(",");
            for (String str : arr$) {
                String subscriptionType = str.trim();
                if (subscriptionType.equalsIgnoreCase("NV")) {
                    nvSupported = true;
                }
                if (subscriptionType.equalsIgnoreCase("RUIM")) {
                    ruimSupported = true;
                }
            }
        }
        log("deviceSupportsnvAnRum: nvSupported=" + nvSupported + " ruimSupported=" + ruimSupported);
        return nvSupported && ruimSupported;
    }

    public boolean preferenceTreeClick(Preference preference) {
        if (preference.getKey().equals("cdma_system_select_key")) {
            log("preferenceTreeClick: return BUTTON_CDMA_ROAMING_KEY true");
            return true;
        }
        if (preference.getKey().equals("cdma_subscription_key")) {
            log("preferenceTreeClick: return CDMA_SUBSCRIPTION_KEY true");
            return true;
        }
        return false;
    }

    public void showDialog(Preference preference) {
        if (preference.getKey().equals("cdma_system_select_key")) {
            this.mButtonCdmaSystemSelect.showDialog(null);
        } else if (preference.getKey().equals("cdma_subscription_key")) {
            this.mButtonCdmaSubscription.showDialog(null);
        }
    }

    protected void log(String s) {
        Log.d("CdmaOptions", s);
    }
}
