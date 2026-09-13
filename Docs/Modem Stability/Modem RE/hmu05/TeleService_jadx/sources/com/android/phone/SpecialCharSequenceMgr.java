package com.android.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyCapabilities;

/* JADX INFO: loaded from: classes.dex */
public class SpecialCharSequenceMgr {
    private SpecialCharSequenceMgr() {
    }

    static boolean handleChars(Context context, String input) {
        return handleChars(context, input, null);
    }

    static boolean handleChars(Context context, String input, Activity pukInputActivity) {
        String dialString = PhoneNumberUtils.stripSeparators(input);
        return handleIMEIDisplay(context, dialString) || handleRegulatoryInfoDisplay(context, dialString) || handlePinEntry(context, dialString, pukInputActivity) || handleAdnEntry(context, dialString) || handleSecretCode(context, dialString);
    }

    static boolean handleCharsForLockedDevice(Context context, String input, Activity pukInputActivity) {
        String dialString = PhoneNumberUtils.stripSeparators(input);
        return handlePinEntry(context, dialString, pukInputActivity);
    }

    private static boolean handleSecretCode(Context context, String input) {
        int len = input.length();
        if (len <= 8 || !input.startsWith("*#*#") || !input.endsWith("#*#*")) {
            return false;
        }
        Intent intent = new Intent("android.provider.Telephony.SECRET_CODE", Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
        context.sendBroadcast(intent);
        return true;
    }

    private static boolean handleAdnEntry(Context context, String input) {
        int len;
        boolean z = false;
        if (PhoneGlobals.getInstance().getKeyguardManager().inKeyguardRestrictedInputMode() || (len = input.length()) <= 1 || len >= 5 || !input.endsWith("#")) {
            return false;
        }
        try {
            int index = Integer.parseInt(input.substring(0, len - 1));
            Intent intent = new Intent("android.intent.action.PICK");
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                intent.setClassName("com.android.phone", "com.android.phone.MSimContacts");
            } else {
                intent.setClassName("com.android.phone", "com.android.phone.SimContacts");
            }
            intent.setFlags(268435456);
            intent.putExtra("index", index);
            PhoneGlobals.getInstance().startActivity(intent);
            z = true;
            return true;
        } catch (NumberFormatException e) {
            return z;
        }
    }

    private static boolean handlePinEntry(Context context, String input, Activity pukInputActivity) {
        if ((!input.startsWith("**04") && !input.startsWith("**05")) || !input.endsWith("#")) {
            return false;
        }
        PhoneGlobals app = PhoneGlobals.getInstance();
        Phone phone = app.phone;
        if (app instanceof MSimPhoneGlobals) {
            int voiceSub = app.getVoiceSubscription();
            phone = app.getPhone(voiceSub);
        }
        boolean isMMIHandled = phone.handlePinMmi(input);
        if (isMMIHandled && input.startsWith("**05")) {
            app.setPukEntryActivity(pukInputActivity);
            return isMMIHandled;
        }
        return isMMIHandled;
    }

    private static boolean handleIMEIDisplay(Context context, String input) {
        if (!input.equals("*#06#")) {
            return false;
        }
        showDeviceIdPanel(context);
        return true;
    }

    private static void showDeviceIdPanel(Context context) {
        Phone phone = PhoneGlobals.getPhone();
        int labelId = TelephonyCapabilities.getDeviceIdLabel(phone);
        String deviceId = phone.getDeviceId();
        AlertDialog alert = new AlertDialog.Builder(context).setTitle(labelId).setMessage(deviceId).setPositiveButton(R.string.ok, (DialogInterface.OnClickListener) null).setCancelable(false).create();
        alert.getWindow().setType(2007);
        alert.show();
    }

    private static boolean handleRegulatoryInfoDisplay(Context context, String input) {
        if (input.equals("*#07#")) {
            log("handleRegulatoryInfoDisplay() sending intent to settings app");
            ComponentName regInfoDisplayActivity = new ComponentName("com.android.settings", "com.android.settings.RegulatoryInfoDisplayActivity");
            Intent showRegInfoIntent = new Intent("android.settings.SHOW_REGULATORY_INFO");
            showRegInfoIntent.setComponent(regInfoDisplayActivity);
            try {
                context.startActivity(showRegInfoIntent);
            } catch (ActivityNotFoundException e) {
                Log.e("PhoneApp", "startActivity() failed: " + e);
            }
            return true;
        }
        return false;
    }

    private static void log(String msg) {
        Log.d("PhoneApp", "[SpecialCharSequenceMgr] " + msg);
    }
}
