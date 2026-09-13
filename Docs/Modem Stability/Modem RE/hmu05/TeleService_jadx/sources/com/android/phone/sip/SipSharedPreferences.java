package com.android.phone.sip;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import com.android.phone.R;

/* JADX INFO: loaded from: classes.dex */
public class SipSharedPreferences {
    private Context mContext;
    private SharedPreferences mPreferences;

    public SipSharedPreferences(Context context) {
        this.mPreferences = context.getSharedPreferences("SIP_PREFERENCES", 1);
        this.mContext = context;
    }

    public void setPrimaryAccount(String accountUri) {
        SharedPreferences.Editor editor = this.mPreferences.edit();
        editor.putString("primary", accountUri);
        editor.apply();
    }

    public void unsetPrimaryAccount() {
        setPrimaryAccount(null);
    }

    public String getPrimaryAccount() {
        return this.mPreferences.getString("primary", null);
    }

    public boolean isPrimaryAccount(String accountUri) {
        return accountUri.equals(this.mPreferences.getString("primary", null));
    }

    public boolean hasPrimaryAccount() {
        return !TextUtils.isEmpty(this.mPreferences.getString("primary", null));
    }

    public void setProfilesCount(int number) {
        SharedPreferences.Editor editor = this.mPreferences.edit();
        editor.putInt("profiles", number);
        editor.apply();
    }

    public int getProfilesCount() {
        return this.mPreferences.getInt("profiles", 0);
    }

    public void setSipCallOption(String option) {
        Settings.System.putString(this.mContext.getContentResolver(), "sip_call_options", option);
    }

    public String getSipCallOption() {
        String option = Settings.System.getString(this.mContext.getContentResolver(), "sip_call_options");
        return option != null ? option : this.mContext.getString(R.string.sip_address_only);
    }

    public void setReceivingCallsEnabled(boolean enabled) {
        Settings.System.putInt(this.mContext.getContentResolver(), "sip_receive_calls", enabled ? 1 : 0);
    }

    public boolean isReceivingCallsEnabled() {
        try {
            return Settings.System.getInt(this.mContext.getContentResolver(), "sip_receive_calls") != 0;
        } catch (Settings.SettingNotFoundException e) {
            Log.d("SIP", "ReceiveCall option is not set; use default value");
            return false;
        }
    }
}
