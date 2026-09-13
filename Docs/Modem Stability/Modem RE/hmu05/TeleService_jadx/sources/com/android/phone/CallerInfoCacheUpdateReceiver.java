package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class CallerInfoCacheUpdateReceiver extends BroadcastReceiver {
    private static final boolean DBG;
    private static final String LOG_TAG = CallerInfoCacheUpdateReceiver.class.getSimpleName();

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (DBG) {
            log("CallerInfoCacheUpdateReceiver#onReceive(). Intent: " + intent);
        }
        PhoneGlobals.getInstance().callerInfoCache.startAsyncCache();
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
