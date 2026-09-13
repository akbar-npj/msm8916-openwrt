package com.android.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class CdmaDisplayInfo {
    private static final boolean DBG;
    private static AlertDialog sDisplayInfoDialog;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
        sDisplayInfoDialog = null;
    }

    public static void displayInfoRecord(Context context, String infoMsg) {
        if (DBG) {
            log("displayInfoRecord: infoMsg=" + infoMsg);
        }
        if (sDisplayInfoDialog != null) {
            sDisplayInfoDialog.dismiss();
        }
        sDisplayInfoDialog = new AlertDialog.Builder(context).setIcon(android.R.drawable.ic_dialog_info).setTitle(context.getText(R.string.network_message)).setMessage(infoMsg).setCancelable(true).create();
        sDisplayInfoDialog.getWindow().setType(2008);
        sDisplayInfoDialog.getWindow().addFlags(2);
        sDisplayInfoDialog.show();
        PhoneGlobals.getInstance().wakeUpScreen();
    }

    public static void dismissDisplayInfoRecord() {
        if (DBG) {
            log("Dissmissing Display Info Record...");
        }
        if (sDisplayInfoDialog != null) {
            sDisplayInfoDialog.dismiss();
            sDisplayInfoDialog = null;
        }
    }

    private static void log(String msg) {
        Log.d("CdmaDisplayInfo", "[CdmaDisplayInfo] " + msg);
    }
}
