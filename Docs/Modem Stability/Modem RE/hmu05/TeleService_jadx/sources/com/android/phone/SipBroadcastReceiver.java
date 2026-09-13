package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.telephony.Rlog;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.phone.sip.SipProfileDb;
import com.android.phone.sip.SipSharedPreferences;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class SipBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = SipBroadcastReceiver.class.getSimpleName();
    private SipSharedPreferences mSipSharedPreferences;

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!PhoneUtils.isVoipSupported(context)) {
            log("SIP VOIP not supported: " + action);
            return;
        }
        this.mSipSharedPreferences = new SipSharedPreferences(context);
        if (action.equals("com.android.phone.SIP_INCOMING_CALL")) {
            takeCall(intent);
            return;
        }
        if (action.equals("com.android.phone.SIP_ADD_PHONE")) {
            String localSipUri = intent.getStringExtra("android:localSipUri");
            SipPhone phone = PhoneFactory.makeSipPhone(localSipUri);
            if (phone != null) {
                CallManager.getInstance().registerPhone(phone);
            }
            log("onReceive: add phone" + localSipUri + " #phones=" + CallManager.getInstance().getAllPhones().size());
            return;
        }
        if (action.equals("com.android.phone.SIP_REMOVE_PHONE")) {
            String localSipUri2 = intent.getStringExtra("android:localSipUri");
            removeSipPhone(localSipUri2);
            log("onReceive: remove phone: " + localSipUri2 + " #phones=" + CallManager.getInstance().getAllPhones().size());
        } else if (action.equals("android.net.sip.SIP_SERVICE_UP")) {
            log("onReceive: start auto registration");
            registerAllProfiles();
        } else {
            log("onReceive: action not processed: " + action);
        }
    }

    private void removeSipPhone(String sipUri) {
        for (SipPhone sipPhone : CallManager.getInstance().getAllPhones()) {
            if (sipPhone.getPhoneType() == 3 && sipPhone.getSipUri().equals(sipUri)) {
                CallManager.getInstance().unregisterPhone(sipPhone);
                return;
            }
        }
        log("RemoveSipPhone: failed:cannot find phone with uri " + sipUri);
    }

    private void takeCall(Intent intent) {
        Context phoneContext = PhoneGlobals.getInstance();
        try {
            SipAudioCall sipAudioCall = SipManager.newInstance(phoneContext).takeAudioCall(intent, null);
            for (SipPhone sipPhone : CallManager.getInstance().getAllPhones()) {
                if (sipPhone.getPhoneType() == 3 && sipPhone.canTake(sipAudioCall)) {
                    log("takeCall: SIP call: " + intent);
                }
            }
            log("takeCall: not taken, drop SIP call: " + intent);
        } catch (SipException e) {
            loge("takeCall: error incoming SIP call", e);
        }
    }

    private void registerAllProfiles() {
        final Context context = PhoneGlobals.getInstance();
        new Thread(new Runnable() { // from class: com.android.phone.SipBroadcastReceiver.1
            @Override // java.lang.Runnable
            public void run() {
                SipManager sipManager = SipManager.newInstance(context);
                SipProfileDb profileDb = new SipProfileDb(context);
                List<SipProfile> sipProfileList = profileDb.retrieveSipProfileList();
                for (SipProfile profile : sipProfileList) {
                    try {
                        if (profile.getAutoRegistration() || profile.getUriString().equals(SipBroadcastReceiver.this.mSipSharedPreferences.getPrimaryAccount())) {
                            sipManager.open(profile, SipUtil.createIncomingCallPendingIntent(), null);
                            SipBroadcastReceiver.this.log("registerAllProfiles: profile=" + profile);
                        }
                    } catch (SipException e) {
                        SipBroadcastReceiver.this.loge("registerAllProfiles: failed" + profile.getProfileName(), e);
                    }
                }
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String s) {
        Rlog.d(TAG, s);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loge(String s, Throwable t) {
        Rlog.e(TAG, s, t);
    }
}
