package com.android.internal.telephony.sip;

import android.content.Context;
import android.net.sip.SipProfile;
import android.telephony.Rlog;
import com.android.internal.telephony.PhoneNotifier;
import java.text.ParseException;

/* JADX INFO: loaded from: classes.dex */
public class SipPhoneFactory {
    public static SipPhone makePhone(String sipUri, Context context, PhoneNotifier phoneNotifier) {
        try {
            SipProfile profile = new SipProfile.Builder(sipUri).build();
            return new SipPhone(context, phoneNotifier, profile);
        } catch (ParseException e) {
            Rlog.w("SipPhoneFactory", "makePhone", e);
            return null;
        }
    }
}
