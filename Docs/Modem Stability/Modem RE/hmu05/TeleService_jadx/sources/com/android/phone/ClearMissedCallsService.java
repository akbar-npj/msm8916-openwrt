package com.android.phone;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.provider.CallLog;

/* JADX INFO: loaded from: classes.dex */
public class ClearMissedCallsService extends IntentService {
    private PhoneGlobals mApp;

    public ClearMissedCallsService() {
        super(ClearMissedCallsService.class.getSimpleName());
    }

    @Override // android.app.IntentService, android.app.Service
    public void onCreate() {
        super.onCreate();
        this.mApp = PhoneGlobals.getInstance();
    }

    @Override // android.app.IntentService
    protected void onHandleIntent(Intent intent) {
        if ("com.android.phone.intent.CLEAR_MISSED_CALLS".equals(intent.getAction())) {
            ContentValues values = new ContentValues();
            values.put("new", (Integer) 0);
            values.put("is_read", (Integer) 1);
            getContentResolver().update(CallLog.Calls.CONTENT_URI, values, "new = 1 AND type = ?", new String[]{Integer.toString(3)});
            this.mApp.notificationMgr.cancelMissedCallNotification();
        }
    }
}
