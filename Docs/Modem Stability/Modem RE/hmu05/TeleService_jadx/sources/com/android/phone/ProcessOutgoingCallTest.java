package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class ProcessOutgoingCallTest extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.NEW_OUTGOING_CALL")) {
            String number = intent.getStringExtra("android.intent.extra.PHONE_NUMBER");
            if (number.equals("411")) {
                setResultData("18004664411");
            }
            if (number.length() == 7) {
                setResultData("617" + number);
            }
            if (number.startsWith("##")) {
                Intent newIntent = new Intent("android.intent.action.SEARCH");
                newIntent.putExtra("query", number.substring(2));
                newIntent.addFlags(268435456);
                context.startActivity(newIntent);
                setResultData(null);
            }
            int length = number.length();
            if (length >= 7) {
                String exchange = number.substring(length - 7, length - 4);
                Log.v("ProcessOutgoingCallTest", "exchange = " + exchange);
                if (exchange.equals("555")) {
                    setResultData(null);
                }
            }
        }
    }
}
