package com.android.internal.telephony;

import android.R;
import android.telephony.Rlog;

/* JADX INFO: loaded from: classes.dex */
public class TelephonyCapabilities {
    private static final String LOG_TAG = "TelephonyCapabilities";

    private TelephonyCapabilities() {
    }

    public static boolean supportsEcm(Phone phone) {
        return phone.getPhoneType() == 2 || phone.getPhoneType() == 4;
    }

    public static boolean supportsOtasp(Phone phone) {
        return phone.getPhoneType() == 2;
    }

    public static boolean supportsVoiceMessageCount(Phone phone) {
        return phone.getVoiceMessageCount() != -1;
    }

    public static boolean supportsNetworkSelection(Phone phone) {
        return phone.getPhoneType() == 1;
    }

    public static int getDeviceIdLabel(Phone phone) {
        if (phone.getPhoneType() == 1) {
            return R.string.PERSOSUBSTATE_RUIM_CORPORATE_PUK_SUCCESS;
        }
        if (phone.getPhoneType() == 2) {
            return R.string.PERSOSUBSTATE_RUIM_CORPORATE_SUCCESS;
        }
        Rlog.w(LOG_TAG, "getDeviceIdLabel: no known label for phone " + phone.getPhoneName());
        return 0;
    }

    public static boolean supportsConferenceCallManagement(Phone phone) {
        return phone.getPhoneType() == 1 || phone.getPhoneType() == 3 || phone.getPhoneType() == 4;
    }

    public static boolean supportsCallModify(Phone phone) {
        return phone.getPhoneType() == 4;
    }

    public static boolean supportsHoldAndUnhold(Phone phone) {
        return phone.getPhoneType() == 1 || phone.getPhoneType() == 3 || phone.getPhoneType() == 4;
    }

    public static boolean supportsAnswerAndHold(Phone phone) {
        return phone.getPhoneType() == 1 || phone.getPhoneType() == 3 || phone.getPhoneType() == 4;
    }

    public static boolean supportsAdn(int phoneType) {
        return phoneType == 1 || phoneType == 2;
    }

    public static boolean canDistinguishDialingAndConnected(int phoneType) {
        return phoneType == 1;
    }
}
