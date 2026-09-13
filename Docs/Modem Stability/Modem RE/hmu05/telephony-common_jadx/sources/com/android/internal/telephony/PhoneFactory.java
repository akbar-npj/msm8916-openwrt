package com.android.internal.telephony;

import android.R;
import android.content.ComponentName;
import android.content.Context;
import android.net.LocalServerSocket;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.telephony.sip.SipPhoneFactory;
import com.android.internal.telephony.uicc.UiccController;
import java.io.IOException;

/* JADX INFO: loaded from: classes.dex */
public class PhoneFactory {
    static final String LOG_TAG = "PhoneFactory";
    protected static final String PHONE_PACKAGE_NAME = "com.android.phone";
    protected static final int SOCKET_OPEN_MAX_RETRY = 3;
    protected static final int SOCKET_OPEN_RETRY_MILLIS = 2000;
    protected static Context sContext;
    protected static Looper sLooper;
    protected static PhoneNotifier sPhoneNotifier;
    protected static Phone sProxyPhone = null;
    protected static CommandsInterface sCommandsInterface = null;
    protected static boolean sMadeDefaults = false;

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    public static void makeDefaultPhone(Context context) {
        synchronized (Phone.class) {
            if (!sMadeDefaults) {
                sLooper = Looper.myLooper();
                sContext = context;
                if (sLooper == null) {
                    throw new RuntimeException("PhoneFactory.makeDefaultPhone must be called from Looper thread");
                }
                int retryCount = 0;
                while (true) {
                    boolean hasException = false;
                    retryCount++;
                    try {
                        new LocalServerSocket("com.android.internal.telephony");
                    } catch (IOException e) {
                        hasException = true;
                    }
                    if (hasException) {
                        if (retryCount > 3) {
                            throw new RuntimeException("PhoneFactory probably already running");
                        }
                        try {
                            Thread.sleep(2000L);
                        } catch (InterruptedException e2) {
                        }
                    } else {
                        sPhoneNotifier = new DefaultPhoneNotifier();
                        if (TelephonyManager.getLteOnCdmaModeStatic() == 1) {
                        }
                        int networkMode = Settings.Global.getInt(context.getContentResolver(), "preferred_network_mode", 11);
                        if (sContext.getResources().getBoolean(R.bool.config_buttonTextAllCaps)) {
                        }
                        Rlog.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkMode));
                        int cdmaSubscription = CdmaSubscriptionSourceManager.getDefault(context);
                        Rlog.i(LOG_TAG, "Cdma Subscription set to " + cdmaSubscription);
                        sCommandsInterface = new RIL(context, networkMode, cdmaSubscription);
                        UiccController.make(context, sCommandsInterface);
                        int phoneType = TelephonyManager.getPhoneType(networkMode);
                        if (phoneType == 1) {
                            Rlog.i(LOG_TAG, "Creating GSMPhone");
                            sProxyPhone = new PhoneProxy(new GSMPhone(context, sCommandsInterface, sPhoneNotifier));
                        } else if (phoneType == 2) {
                            switch (TelephonyManager.getLteOnCdmaModeStatic()) {
                                case 1:
                                    Rlog.i(LOG_TAG, "Creating CDMALTEPhone");
                                    sProxyPhone = new PhoneProxy(new CDMALTEPhone(context, sCommandsInterface, sPhoneNotifier));
                                    break;
                                default:
                                    Rlog.i(LOG_TAG, "Creating CDMAPhone");
                                    sProxyPhone = new PhoneProxy(new CDMAPhone(context, sCommandsInterface, sPhoneNotifier));
                                    break;
                            }
                        }
                        ComponentName componentName = SmsApplication.getDefaultSmsApplication(context, true);
                        String packageName = "NONE";
                        if (componentName != null) {
                            packageName = componentName.getPackageName();
                        }
                        Rlog.i(LOG_TAG, "defaultSmsApplication: " + packageName);
                        SmsApplication.initSmsPackageMonitor(context);
                        sMadeDefaults = true;
                    }
                }
            }
        }
    }

    public static Phone getDefaultPhone() {
        if (sLooper != Looper.myLooper()) {
            throw new RuntimeException("PhoneFactory.getDefaultPhone must be called from Looper thread");
        }
        if (!sMadeDefaults) {
            throw new IllegalStateException("Default phones haven't been made yet!");
        }
        return sProxyPhone;
    }

    public static Phone getCdmaPhone() {
        Phone phone;
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            switch (TelephonyManager.getLteOnCdmaModeStatic()) {
                case 1:
                    phone = new CDMALTEPhone(sContext, sCommandsInterface, sPhoneNotifier);
                    break;
                default:
                    phone = new CDMAPhone(sContext, sCommandsInterface, sPhoneNotifier);
                    break;
            }
        }
        return phone;
    }

    public static Phone getGsmPhone() {
        Phone phone;
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            phone = new GSMPhone(sContext, sCommandsInterface, sPhoneNotifier);
        }
        return phone;
    }

    public static Context getContext() {
        return sContext;
    }

    public static SipPhone makeSipPhone(String sipUri) {
        return SipPhoneFactory.makePhone(sipUri, sContext, sPhoneNotifier);
    }
}
