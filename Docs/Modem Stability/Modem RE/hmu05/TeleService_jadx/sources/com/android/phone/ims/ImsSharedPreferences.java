package com.android.phone.ims;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class ImsSharedPreferences {
    private static final boolean DBG = Log.isLoggable("IMS", 3);
    private SharedPreferences mPreferences;

    public ImsSharedPreferences(Context context) {
        this.mPreferences = context.getSharedPreferences("IMS_PREFERENCES", 1);
    }

    public void setCallType(int callType) {
        if (DBG) {
            Log.d("IMSPreferences", "setCallType: " + callType);
        }
        SharedPreferences.Editor editor = this.mPreferences.edit();
        editor.putInt("ims_call_type", callType);
        editor.apply();
    }

    public int getCallType() {
        int callType = this.mPreferences.getInt("ims_call_type", 10);
        if (DBG) {
            Log.d("IMSPreferences", "getCallType: " + callType);
        }
        return callType;
    }

    public boolean isCallTypeSelectable() {
        boolean isSelectable = this.mPreferences.getBoolean("ims_is_call_type_selectable", false);
        if (DBG) {
            Log.d("IMSPreferences", "isCallTypeSelectable: " + isSelectable);
        }
        return isSelectable;
    }

    public void setCallTypeSelectable(boolean isSelectable) {
        if (DBG) {
            Log.d("IMSPreferences", "setCallTypeSelectable: " + isSelectable);
        }
        SharedPreferences.Editor editor = this.mPreferences.edit();
        editor.putBoolean("ims_is_call_type_selectable", isSelectable);
        editor.apply();
    }

    public boolean getisImsCapEnabled(int service) {
        switch (service) {
            case 0:
                boolean isImsDefault = this.mPreferences.getBoolean("ims_is_voice_cap", false);
                return isImsDefault;
            case 1:
            case 2:
            default:
                Log.e("IMSPreferences", "getisImsCapEnabled not supported " + service);
                return false;
            case 3:
                boolean isImsDefault2 = this.mPreferences.getBoolean("ims_is_default_video", false);
                return isImsDefault2;
        }
    }

    public void setIsImsCapEnabled(int service, boolean enable) {
        SharedPreferences.Editor editor = this.mPreferences.edit();
        switch (service) {
            case 0:
                editor.putBoolean("ims_is_voice_cap", enable);
                break;
            case 1:
            case 2:
            default:
                Log.e("IMSPreferences", "setIsImsCapEnabled not supported " + service + " " + enable);
                return;
            case 3:
                editor.putBoolean("ims_is_default_video", enable);
                break;
        }
        editor.apply();
    }

    public void setImsSrvStatus(int service, int status) {
        SharedPreferences.Editor editor = this.mPreferences.edit();
        switch (service) {
            case 0:
                editor.putInt("ims_is_voice_srv_allowed", status);
                break;
            case 1:
            case 2:
            default:
                Log.e("IMSPreferences", "setImsSrvStatus not supported " + service + " " + status);
                return;
            case 3:
                editor.putInt("ims_is_video_srv_allowed", status);
                break;
        }
        editor.apply();
    }

    public int getImsSrvStatus(int service) {
        switch (service) {
            case 0:
                int status = this.mPreferences.getInt("ims_is_voice_srv_allowed", 3);
                return status;
            case 1:
            case 2:
            default:
                Log.e("IMSPreferences", "getImsSrvStatus not supported service " + service);
                return 3;
            case 3:
                int status2 = this.mPreferences.getInt("ims_is_video_srv_allowed", 3);
                return status2;
        }
    }

    public boolean isImsSrvAllowed(int service) {
        int status = getImsSrvStatus(service);
        return status == 1 || status == 2;
    }
}
