package com.qualcomm.fastdormancy;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class FastDormancyReceiver extends BroadcastReceiver {
    private static final String PROPERTY_FAST_DORMANCY_KEY = "persist.env.fastdorm.enabled";
    private static final String TAG = "DormancyReceiver";
    private Context mContext;

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        this.mContext = context;
        if (SystemProperties.getBoolean(PROPERTY_FAST_DORMANCY_KEY, true) && "android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            ComponentName comp = new ComponentName(context.getPackageName(), FastDormancyService.class.getName());
            if (comp != null) {
                ComponentName service = context.startService(new Intent().setComponent(comp));
                if (service == null) {
                    Log.e(TAG, "Could Not Start Service " + comp.toString());
                    return;
                } else {
                    Log.d(TAG, "Fast Dormancy Auto Boot Started Successfully");
                    return;
                }
            }
            Log.d(TAG, "Can't find FD service,not Started Successfully");
            return;
        }
        Log.d(TAG, "Received Unexpected Intent " + intent.toString());
    }
}
