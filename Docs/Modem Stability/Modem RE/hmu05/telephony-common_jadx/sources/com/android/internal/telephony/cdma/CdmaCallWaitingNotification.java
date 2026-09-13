package com.android.internal.telephony.cdma;

import android.telephony.Rlog;
import com.android.internal.telephony.PhoneConstants;

/* JADX INFO: loaded from: classes.dex */
public class CdmaCallWaitingNotification {
    static final String LOG_TAG = "CdmaCallWaitingNotification";
    public String number = null;
    public int numberPresentation = 0;
    public String name = null;
    public int namePresentation = 0;
    public int numberType = 0;
    public int numberPlan = 0;
    public int isPresent = 0;
    public int signalType = 0;
    public int alertPitch = 0;
    public int signal = 0;

    public String toString() {
        return super.toString() + "Call Waiting Notification   number: " + this.number + " numberPresentation: " + this.numberPresentation + " name: " + this.name + " namePresentation: " + this.namePresentation + " numberType: " + this.numberType + " numberPlan: " + this.numberPlan + " isPresent: " + this.isPresent + " signalType: " + this.signalType + " alertPitch: " + this.alertPitch + " signal: " + this.signal;
    }

    public static int presentationFromCLIP(int cli) {
        switch (cli) {
            case 0:
                return PhoneConstants.PRESENTATION_ALLOWED;
            case 1:
                return PhoneConstants.PRESENTATION_RESTRICTED;
            case 2:
                return PhoneConstants.PRESENTATION_UNKNOWN;
            default:
                Rlog.d(LOG_TAG, "Unexpected presentation " + cli);
                return PhoneConstants.PRESENTATION_UNKNOWN;
        }
    }
}
