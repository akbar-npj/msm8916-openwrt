package com.android.phone;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SmsApplication;
import com.android.services.telephony.common.Call;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class RejectWithTextMessageManager {
    private static final String TAG = RejectWithTextMessageManager.class.getSimpleName();

    public static ArrayList<String> loadCannedResponses() {
        log("loadCannedResponses()...");
        SharedPreferences prefs = PhoneGlobals.getInstance().getSharedPreferences("respond_via_sms_prefs", 0);
        Resources res = PhoneGlobals.getInstance().getResources();
        ArrayList<String> responses = new ArrayList<>(4);
        responses.add(0, prefs.getString("canned_response_pref_1", res.getString(R.string.respond_via_sms_canned_response_1)));
        responses.add(1, prefs.getString("canned_response_pref_2", res.getString(R.string.respond_via_sms_canned_response_2)));
        responses.add(2, prefs.getString("canned_response_pref_3", res.getString(R.string.respond_via_sms_canned_response_3)));
        responses.add(3, prefs.getString("canned_response_pref_4", res.getString(R.string.respond_via_sms_canned_response_4)));
        return responses;
    }

    private static void showMessageSentToast(final String phoneNumber) {
        new Thread(new Runnable() { // from class: com.android.phone.RejectWithTextMessageManager.1
            @Override // java.lang.Runnable
            public void run() {
                Looper.prepare();
                Handler innerHandler = new Handler() { // from class: com.android.phone.RejectWithTextMessageManager.1.1
                    @Override // android.os.Handler
                    public void handleMessage(Message message) {
                        Resources res = PhoneGlobals.getInstance().getResources();
                        String formatString = res.getString(R.string.respond_via_sms_confirmation_format);
                        String confirmationMsg = String.format(formatString, phoneNumber);
                        Toast.makeText(PhoneGlobals.getInstance(), confirmationMsg, 1).show();
                    }

                    @Override // android.os.Handler
                    public void dispatchMessage(Message message) {
                        handleMessage(message);
                    }
                };
                Message message = innerHandler.obtainMessage();
                innerHandler.dispatchMessage(message);
                Looper.loop();
            }
        }).start();
    }

    public static void rejectCallWithMessage(String phoneNumber, String message, int subscription) {
        ComponentName component;
        if (message != null && (component = SmsApplication.getDefaultRespondViaMessageApplication(PhoneGlobals.getInstance(), true)) != null) {
            Uri uri = Uri.fromParts("smsto", phoneNumber, null);
            Intent intent = new Intent("android.intent.action.RESPOND_VIA_MESSAGE", uri);
            intent.putExtra("android.intent.extra.TEXT", message);
            intent.putExtra("subscription", subscription);
            showMessageSentToast(phoneNumber);
            intent.setComponent(component);
            PhoneGlobals.getInstance().startService(intent);
        }
    }

    public static boolean allowRespondViaSmsForCall(Call call, Connection conn) {
        log("allowRespondViaSmsForCall(" + call + ")...");
        if (call == null) {
            Log.w(TAG, "allowRespondViaSmsForCall: null ringingCall!");
            return false;
        }
        if (call.getState() != 3 && call.getState() != 4) {
            Log.w(TAG, "allowRespondViaSmsForCall: ringingCall not ringing! state = " + call.getState());
            return false;
        }
        if (conn == null) {
            Log.w(TAG, "allowRespondViaSmsForCall: null Connection!");
            return false;
        }
        String number = conn.getAddress();
        log("- number: '" + number + "'");
        if (TextUtils.isEmpty(number)) {
            Log.w(TAG, "allowRespondViaSmsForCall: no incoming number!");
            return false;
        }
        if (PhoneNumberUtils.isUriNumber(number)) {
            Log.i(TAG, "allowRespondViaSmsForCall: incoming 'number' is a SIP address.");
            return false;
        }
        int presentation = conn.getNumberPresentation();
        log("- presentation: " + presentation);
        if (presentation != PhoneConstants.PRESENTATION_RESTRICTED) {
            return SmsApplication.getDefaultRespondViaMessageApplication(PhoneGlobals.getInstance(), true) != null;
        }
        Log.i(TAG, "allowRespondViaSmsForCall: PRESENTATION_RESTRICTED.");
        return false;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
