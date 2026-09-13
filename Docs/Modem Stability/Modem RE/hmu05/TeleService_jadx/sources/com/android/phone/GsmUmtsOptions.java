package com.android.phone;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.codeaurora.telephony.msim.SubscriptionManager;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class GsmUmtsOptions {
    private PreferenceScreen mButtonAPNExpand;
    private PreferenceScreen mButtonOperatorSelectionExpand;
    private Phone mPhone;
    private PreferenceActivity mPrefActivity;
    private PreferenceScreen mPrefScreen;
    private int mSubscription;

    public GsmUmtsOptions(PreferenceActivity prefActivity, PreferenceScreen prefScreen) {
        this(prefActivity, prefScreen, 0);
    }

    public GsmUmtsOptions(PreferenceActivity prefActivity, PreferenceScreen prefScreen, int subscription) {
        this.mSubscription = 0;
        this.mPrefActivity = prefActivity;
        this.mPrefScreen = prefScreen;
        this.mSubscription = subscription;
        this.mPhone = PhoneGlobals.getInstance().getPhone(this.mSubscription);
        create();
    }

    protected void create() {
        this.mPrefActivity.addPreferencesFromResource(R.xml.gsm_umts_options);
        this.mButtonAPNExpand = (PreferenceScreen) this.mPrefScreen.findPreference("button_apn_key");
        if (needDisableSub2Apn(this.mSubscription)) {
            log("disable sub2 apn");
            this.mButtonAPNExpand.setEnabled(false);
        } else {
            this.mButtonAPNExpand.getIntent().putExtra("subscription", this.mSubscription);
        }
        this.mButtonOperatorSelectionExpand = (PreferenceScreen) this.mPrefScreen.findPreference("button_carrier_sel_key");
        this.mButtonOperatorSelectionExpand.getIntent().putExtra("subscription", this.mSubscription);
        enableScreen();
    }

    private void enablePlmnIncSearch() {
        if (this.mButtonOperatorSelectionExpand != null) {
            PackageManager pm = this.mButtonOperatorSelectionExpand.getContext().getPackageManager();
            Intent intent = new Intent("org.codeaurora.settings.NETWORK_OPERATOR_SETTINGS_ASYNC");
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo resolveInfo : list) {
                if ((resolveInfo.activityInfo.applicationInfo.flags & 1) != 0) {
                    intent.putExtra("subscription", this.mSubscription);
                    this.mButtonOperatorSelectionExpand.setIntent(intent);
                }
            }
        }
    }

    public void enableScreen() {
        if (this.mPhone.getPhoneType() != 1) {
            log("Not a GSM phone, disabling GSM preferences (select operator)");
            this.mButtonOperatorSelectionExpand.setEnabled(false);
        } else {
            log("Not a CDMA phone");
            Resources res = this.mPrefActivity.getResources();
            if (!res.getBoolean(R.bool.config_apn_expand)) {
                this.mPrefScreen.removePreference(this.mPrefScreen.findPreference("button_apn_key"));
            }
            if (!res.getBoolean(R.bool.config_operator_selection_expand) && this.mButtonOperatorSelectionExpand != null) {
                this.mPrefScreen.removePreference(this.mButtonOperatorSelectionExpand);
            }
        }
        updateOperatorSelectionVisibility();
    }

    private void updateOperatorSelectionVisibility() {
        log("updateOperatorSelectionVisibility. mPhone = " + this.mPhone.getPhoneName());
        Resources res = this.mPrefActivity.getResources();
        if (this.mButtonOperatorSelectionExpand == null) {
            Log.e("GsmUmtsOptions", "mButtonOperatorSelectionExpand is null");
            return;
        }
        enablePlmnIncSearch();
        if (!this.mPhone.isManualNetSelAllowed()) {
            log("Manual network selection not allowed.Disabling Operator Selection menu.");
            this.mButtonOperatorSelectionExpand.setEnabled(false);
        } else if (res.getBoolean(R.bool.csp_enabled)) {
            if (this.mPhone.isCspPlmnEnabled()) {
                log("[CSP] Enabling Operator Selection menu.");
                this.mButtonOperatorSelectionExpand.setEnabled(true);
            } else {
                log("[CSP] Disabling Operator Selection menu.");
                if (this.mButtonOperatorSelectionExpand != null) {
                    this.mPrefScreen.removePreference(this.mButtonOperatorSelectionExpand);
                }
            }
        }
    }

    public boolean preferenceTreeClick(Preference preference) {
        log("preferenceTreeClick: return false");
        return false;
    }

    protected void log(String s) {
        Log.d("GsmUmtsOptions", s);
    }

    protected boolean needDisableSub2Apn(int sub) {
        if (this.mPrefActivity.getResources().getBoolean(R.bool.disable_data_sub2)) {
            return 1 == sub && MSimTelephonyManager.getDefault().getMultiSimConfiguration().equals(MSimTelephonyManager.MultiSimVariants.DSDS) && 2 == SubscriptionManager.getInstance().getActiveSubscriptionsCount();
        }
        return false;
    }
}
