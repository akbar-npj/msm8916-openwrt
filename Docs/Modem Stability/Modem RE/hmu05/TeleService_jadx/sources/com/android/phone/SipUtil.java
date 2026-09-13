package com.android.phone;

import android.app.PendingIntent;
import android.content.Intent;

/* JADX INFO: loaded from: classes.dex */
public class SipUtil {
    private SipUtil() {
    }

    public static PendingIntent createIncomingCallPendingIntent() {
        PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
        Intent intent = new Intent(phoneGlobals, (Class<?>) SipBroadcastReceiver.class);
        intent.setAction("com.android.phone.SIP_INCOMING_CALL");
        return PendingIntent.getBroadcast(phoneGlobals, 0, intent, 134217728);
    }
}
