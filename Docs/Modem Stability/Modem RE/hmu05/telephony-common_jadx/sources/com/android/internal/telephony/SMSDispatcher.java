package com.android.internal.telephony;

import android.R;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.Spanned;
import android.util.EventLog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: loaded from: classes.dex */
public abstract class SMSDispatcher extends Handler {
    static final boolean DBG = false;
    private static final int EVENT_CONFIRM_SEND_TO_POSSIBLE_PREMIUM_SHORT_CODE = 8;
    private static final int EVENT_CONFIRM_SEND_TO_PREMIUM_SHORT_CODE = 9;
    protected static final int EVENT_HANDLE_STATUS_REPORT = 10;
    protected static final int EVENT_ICC_CHANGED = 15;
    protected static final int EVENT_IMS_STATE_CHANGED = 12;
    protected static final int EVENT_IMS_STATE_DONE = 13;
    protected static final int EVENT_NEW_ICC_SMS = 14;
    protected static final int EVENT_RADIO_ON = 11;
    static final int EVENT_SEND_CONFIRMED_SMS = 5;
    private static final int EVENT_SEND_LIMIT_REACHED_CONFIRMATION = 4;
    private static final int EVENT_SEND_RETRY = 3;
    protected static final int EVENT_SEND_SMS_COMPLETE = 2;
    protected static final int EVENT_SMS_ON_ICC = 16;
    static final int EVENT_STOP_SENDING = 7;
    private static final int MAX_SEND_RETRIES = 3;
    private static final int MO_MSG_QUEUE_LIMIT = 5;
    private static final int PREMIUM_RULE_USE_BOTH = 3;
    private static final int PREMIUM_RULE_USE_NETWORK = 2;
    private static final int PREMIUM_RULE_USE_SIM = 1;
    private static final String SEND_NEXT_MSG_EXTRA = "SendNextMsg";
    private static final int SEND_RETRY_DELAY = 2000;
    private static final String SEND_SMS_NO_CONFIRMATION_PERMISSION = "android.permission.SEND_SMS_NO_CONFIRMATION";
    private static final int SINGLE_PART_SMS = 1;
    static final String TAG = "SMSDispatcher";
    private static int sConcatenatedRef = new Random().nextInt(256);
    protected final CommandsInterface mCi;
    protected final Context mContext;
    protected ImsSMSDispatcher mImsSMSDispatcher;
    private int mPendingTrackerCount;
    protected PhoneBase mPhone;
    protected final ContentResolver mResolver;
    private final SettingsObserver mSettingsObserver;
    protected boolean mSmsCapable;
    protected boolean mSmsSendDisabled;
    protected final TelephonyManager mTelephonyManager;
    private SmsUsageMonitor mUsageMonitor;
    private final AtomicInteger mPremiumSmsRule = new AtomicInteger(1);
    protected int mRemainingMessages = -1;
    protected final ArrayList<SmsTracker> deliveryPendingList = new ArrayList<>();

    protected abstract GsmAlphabet.TextEncodingDetails calculateLength(CharSequence charSequence, boolean z);

    protected abstract String getFormat();

    protected abstract void sendData(String str, String str2, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2);

    protected abstract void sendNewSubmitPdu(String str, String str2, String str3, SmsHeader smsHeader, int i, PendingIntent pendingIntent, PendingIntent pendingIntent2, boolean z, int i2, boolean z2, int i3);

    protected abstract void sendSms(SmsTracker smsTracker);

    protected abstract void sendText(String str, String str2, String str3, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i, boolean z, int i2);

    protected static int getNextConcatenatedRef() {
        sConcatenatedRef++;
        return sConcatenatedRef;
    }

    protected SMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        this.mSmsCapable = true;
        this.mPhone = phone;
        this.mImsSMSDispatcher = imsSMSDispatcher;
        this.mContext = phone.getContext();
        this.mResolver = this.mContext.getContentResolver();
        this.mCi = phone.mCi;
        this.mUsageMonitor = usageMonitor;
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        this.mSettingsObserver = new SettingsObserver(this, this.mPremiumSmsRule, this.mContext);
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("sms_short_code_rule"), false, this.mSettingsObserver);
        this.mSmsCapable = this.mContext.getResources().getBoolean(R.bool.config_autoBrightnessResetAmbientLuxAfterWarmUp);
        this.mSmsSendDisabled = !SystemProperties.getBoolean("telephony.sms.send", this.mSmsCapable);
        Rlog.d(TAG, "SMSDispatcher: ctor mSmsCapable=" + this.mSmsCapable + " format=" + getFormat() + " mSmsSendDisabled=" + this.mSmsSendDisabled);
    }

    private static class SettingsObserver extends ContentObserver {
        private final Context mContext;
        private final AtomicInteger mPremiumSmsRule;

        SettingsObserver(Handler handler, AtomicInteger premiumSmsRule, Context context) {
            super(handler);
            this.mPremiumSmsRule = premiumSmsRule;
            this.mContext = context;
            onChange(false);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            this.mPremiumSmsRule.set(Settings.Global.getInt(this.mContext.getContentResolver(), "sms_short_code_rule", 1));
        }
    }

    protected void updatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mUsageMonitor = phone.mSmsUsageMonitor;
        Rlog.d(TAG, "Active phone changed to " + this.mPhone.getPhoneName());
    }

    public void dispose() {
        this.mContext.getContentResolver().unregisterContentObserver(this.mSettingsObserver);
    }

    protected void handleStatusReport(Object o) {
        Rlog.d(TAG, "handleStatusReport() called with no subclass.");
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 2:
                handleSendComplete((AsyncResult) msg.obj);
                break;
            case 3:
                Rlog.d(TAG, "SMS retry..");
                sendRetrySms((SmsTracker) msg.obj);
                break;
            case 4:
                handleReachSentLimit((SmsTracker) msg.obj);
                break;
            case 5:
                SmsTracker tracker = (SmsTracker) msg.obj;
                if (tracker.isMultipart()) {
                    sendMultipartSms(tracker);
                } else {
                    sendSms(tracker);
                }
                this.mPendingTrackerCount--;
                break;
            case 6:
            default:
                Rlog.e(TAG, "handleMessage() ignoring message of unexpected type " + msg.what);
                break;
            case 7:
                SmsTracker tracker2 = (SmsTracker) msg.obj;
                if (tracker2.mSentIntent != null) {
                    try {
                        tracker2.mSentIntent.send(5);
                    } catch (PendingIntent.CanceledException e) {
                        Rlog.e(TAG, "failed to send RESULT_ERROR_LIMIT_EXCEEDED");
                    }
                }
                this.mPendingTrackerCount--;
                break;
            case 8:
                handleConfirmShortCode(false, (SmsTracker) msg.obj);
                break;
            case 9:
                handleConfirmShortCode(true, (SmsTracker) msg.obj);
                break;
            case 10:
                handleStatusReport(msg.obj);
                break;
        }
    }

    protected void handleSendComplete(AsyncResult ar) {
        SmsTracker tracker = (SmsTracker) ar.userObj;
        PendingIntent sentIntent = tracker.mSentIntent;
        if (ar.result != null) {
            tracker.mMessageRef = ((SmsResponse) ar.result).mMessageRef;
        } else {
            Rlog.d(TAG, "SmsResponse was null");
        }
        if (ar.exception == null) {
            if (SmsApplication.shouldWriteMessageForPackage(tracker.mAppInfo.applicationInfo.packageName, this.mContext)) {
                tracker.writeSentMessage(this.mContext);
            }
            if (tracker.mDeliveryIntent != null) {
                this.deliveryPendingList.add(tracker);
            }
            if (sentIntent != null) {
                try {
                    if (this.mRemainingMessages > -1) {
                        this.mRemainingMessages--;
                    }
                    if (this.mRemainingMessages == 0) {
                        Intent sendNext = new Intent();
                        sendNext.putExtra(SEND_NEXT_MSG_EXTRA, true);
                        sentIntent.send(this.mContext, -1, sendNext);
                        return;
                    }
                    sentIntent.send(-1);
                    return;
                } catch (PendingIntent.CanceledException e) {
                    return;
                }
            }
            return;
        }
        int ss = this.mPhone.getServiceState().getState();
        if (tracker.mImsRetry > 0 && ss != 0) {
            tracker.mRetryCount = 3;
            Rlog.d(TAG, "handleSendComplete: Skipping retry:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SS= " + this.mPhone.getServiceState().getState());
        }
        if (!isIms() && ss != 0) {
            handleNotInService(ss, tracker.mSentIntent);
            return;
        }
        if (((CommandException) ar.exception).getCommandError() == CommandException.Error.SMS_FAIL_RETRY && tracker.mRetryCount < 3) {
            tracker.mRetryCount++;
            Message retryMsg = obtainMessage(3, tracker);
            sendMessageDelayed(retryMsg, 2000L);
        } else if (tracker.mSentIntent != null) {
            int error = 1;
            if (((CommandException) ar.exception).getCommandError() == CommandException.Error.FDN_CHECK_FAILURE) {
                error = 6;
            }
            try {
                Intent fillIn = new Intent();
                if (ar.result != null) {
                    fillIn.putExtra("errorCode", ((SmsResponse) ar.result).mErrorCode);
                }
                if (this.mRemainingMessages > -1) {
                    this.mRemainingMessages--;
                }
                if (this.mRemainingMessages == 0) {
                    fillIn.putExtra(SEND_NEXT_MSG_EXTRA, true);
                }
                tracker.mSentIntent.send(this.mContext, error, fillIn);
            } catch (PendingIntent.CanceledException e2) {
            }
        }
    }

    protected static void handleNotInService(int ss, PendingIntent sentIntent) {
        if (sentIntent != null) {
            try {
                if (ss == 3) {
                    sentIntent.send(2);
                } else {
                    sentIntent.send(4);
                }
            } catch (PendingIntent.CanceledException e) {
            }
        }
    }

    protected void sendMultipartText(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, int priority, boolean isExpectMore, int validityPeriod) {
        int refNumber = getNextConcatenatedRef() & 255;
        int msgCount = parts.size();
        int encoding = 0;
        this.mRemainingMessages = msgCount;
        GsmAlphabet.TextEncodingDetails[] encodingForParts = new GsmAlphabet.TextEncodingDetails[msgCount];
        for (int i = 0; i < msgCount; i++) {
            GsmAlphabet.TextEncodingDetails details = calculateLength(parts.get(i), false);
            if (encoding != details.codeUnitSize && (encoding == 0 || encoding == 1)) {
                encoding = details.codeUnitSize;
            }
            encodingForParts[i] = details;
        }
        int i2 = 0;
        while (i2 < msgCount) {
            SmsHeader.ConcatRef concatRef = new SmsHeader.ConcatRef();
            concatRef.refNumber = refNumber;
            concatRef.seqNumber = i2 + 1;
            concatRef.msgCount = msgCount;
            concatRef.isEightBits = true;
            SmsHeader smsHeader = new SmsHeader();
            smsHeader.concatRef = concatRef;
            if (encoding == 1) {
                smsHeader.languageTable = encodingForParts[i2].languageTable;
                smsHeader.languageShiftTable = encodingForParts[i2].languageShiftTable;
            }
            PendingIntent sentIntent = null;
            if (sentIntents != null && sentIntents.size() > i2) {
                sentIntent = sentIntents.get(i2);
            }
            PendingIntent deliveryIntent = null;
            if (deliveryIntents != null && deliveryIntents.size() > i2) {
                deliveryIntent = deliveryIntents.get(i2);
            }
            sendNewSubmitPdu(destAddr, scAddr, parts.get(i2), smsHeader, encoding, sentIntent, deliveryIntent, i2 == msgCount + (-1), priority, isExpectMore, validityPeriod);
            i2++;
        }
    }

    protected void sendRawPdu(SmsTracker tracker) {
        byte[] pdu = (byte[]) tracker.mData.get("pdu");
        PendingIntent sentIntent = tracker.mSentIntent;
        if (this.mSmsSendDisabled) {
            if (sentIntent != null) {
                try {
                    sentIntent.send(4);
                } catch (PendingIntent.CanceledException e) {
                }
            }
            Rlog.d(TAG, "Device does not support sending sms.");
            return;
        }
        if (pdu == null) {
            if (sentIntent != null) {
                try {
                    sentIntent.send(3);
                    return;
                } catch (PendingIntent.CanceledException e2) {
                    return;
                }
            }
            return;
        }
        PackageManager pm = this.mContext.getPackageManager();
        String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());
        if (packageNames == null || packageNames.length == 0) {
            Rlog.e(TAG, "Can't get calling app package name: refusing to send SMS");
            if (sentIntent != null) {
                try {
                    sentIntent.send(1);
                    return;
                } catch (PendingIntent.CanceledException e3) {
                    Rlog.e(TAG, "failed to send error result");
                    return;
                }
            }
            return;
        }
        try {
            PackageInfo appInfo = pm.getPackageInfo(packageNames[0], 64);
            if (checkDestination(tracker)) {
                if (!this.mUsageMonitor.check(appInfo.packageName, 1)) {
                    sendMessage(obtainMessage(4, tracker));
                    return;
                }
                int ss = this.mPhone.getServiceState().getState();
                if (!isIms() && ss != 0) {
                    handleNotInService(ss, tracker.mSentIntent);
                } else {
                    sendSms(tracker);
                }
            }
        } catch (PackageManager.NameNotFoundException e4) {
            Rlog.e(TAG, "Can't get calling app package info: refusing to send SMS");
            if (sentIntent != null) {
                try {
                    sentIntent.send(1);
                } catch (PendingIntent.CanceledException e5) {
                    Rlog.e(TAG, "failed to send error result");
                }
            }
        }
    }

    boolean checkDestination(SmsTracker tracker) {
        int event;
        if (this.mContext.checkCallingOrSelfPermission(SEND_SMS_NO_CONFIRMATION_PERMISSION) == 0) {
            return true;
        }
        int rule = this.mPremiumSmsRule.get();
        int smsCategory = 0;
        if (rule == 1 || rule == 3) {
            String simCountryIso = this.mTelephonyManager.getSimCountryIso();
            if (simCountryIso == null || simCountryIso.length() != 2) {
                Rlog.e(TAG, "Can't get SIM country Iso: trying network country Iso");
                simCountryIso = this.mTelephonyManager.getNetworkCountryIso();
            }
            smsCategory = this.mUsageMonitor.checkDestination(tracker.mDestAddress, simCountryIso);
        }
        if (rule == 2 || rule == 3) {
            String networkCountryIso = this.mTelephonyManager.getNetworkCountryIso();
            if (networkCountryIso == null || networkCountryIso.length() != 2) {
                Rlog.e(TAG, "Can't get Network country Iso: trying SIM country Iso");
                networkCountryIso = this.mTelephonyManager.getSimCountryIso();
            }
            smsCategory = SmsUsageMonitor.mergeShortCodeCategories(smsCategory, this.mUsageMonitor.checkDestination(tracker.mDestAddress, networkCountryIso));
        }
        if (smsCategory == 0 || smsCategory == 1 || smsCategory == 2) {
            return true;
        }
        int premiumSmsPermission = this.mUsageMonitor.getPremiumSmsPermission(tracker.mAppInfo.packageName);
        if (premiumSmsPermission == 0) {
            premiumSmsPermission = 1;
        }
        switch (premiumSmsPermission) {
            case 2:
                Rlog.w(TAG, "User denied this app from sending to premium SMS");
                sendMessage(obtainMessage(7, tracker));
                return false;
            case 3:
                Rlog.d(TAG, "User approved this app to send to premium SMS");
                return true;
            default:
                if (smsCategory == 3) {
                    event = 8;
                } else {
                    event = 9;
                }
                sendMessage(obtainMessage(event, tracker));
                return false;
        }
    }

    private boolean denyIfQueueLimitReached(SmsTracker tracker) {
        if (this.mPendingTrackerCount >= 5) {
            try {
                if (tracker.mSentIntent != null) {
                    tracker.mSentIntent.send(5);
                }
            } catch (PendingIntent.CanceledException e) {
                Rlog.e(TAG, "failed to send back RESULT_ERROR_LIMIT_EXCEEDED");
            }
            return true;
        }
        this.mPendingTrackerCount++;
        return false;
    }

    private CharSequence getAppLabel(String appPackage) {
        PackageManager pm = this.mContext.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(appPackage, 0);
            return appInfo.loadLabel(pm);
        } catch (PackageManager.NameNotFoundException e) {
            Rlog.e(TAG, "PackageManager Name Not Found for package " + appPackage);
            return appPackage;
        }
    }

    protected void handleReachSentLimit(SmsTracker tracker) {
        if (!denyIfQueueLimitReached(tracker)) {
            CharSequence appLabel = getAppLabel(tracker.mAppInfo.packageName);
            Resources r = Resources.getSystem();
            Spanned messageText = Html.fromHtml(r.getString(R.string.fingerprint_recalibrate_notification_title, appLabel));
            ConfirmDialogListener listener = new ConfirmDialogListener(tracker, null);
            AlertDialog d = new AlertDialog.Builder(this.mContext).setTitle(R.string.fingerprint_recalibrate_notification_name).setIcon(R.drawable.stat_sys_warning).setMessage(messageText).setPositiveButton(r.getString(R.string.fingerprint_udfps_error_not_match), listener).setNegativeButton(r.getString(R.string.fingerprints), listener).setOnCancelListener(listener).create();
            d.getWindow().setType(2003);
            d.show();
        }
    }

    protected void handleConfirmShortCode(boolean isPremium, SmsTracker tracker) {
        int detailsId;
        if (!denyIfQueueLimitReached(tracker)) {
            if (isPremium) {
                detailsId = R.string.font_family_body_1_material;
            } else {
                detailsId = R.string.floating_toolbar_open_overflow_description;
            }
            CharSequence appLabel = getAppLabel(tracker.mAppInfo.packageName);
            Resources r = Resources.getSystem();
            Spanned messageText = Html.fromHtml(r.getString(R.string.floating_toolbar_close_overflow_description, appLabel, tracker.mDestAddress));
            LayoutInflater inflater = (LayoutInflater) this.mContext.getSystemService("layout_inflater");
            View layout = inflater.inflate(R.layout.language_picker_bilingual_item, (ViewGroup) null);
            ConfirmDialogListener listener = new ConfirmDialogListener(tracker, (TextView) layout.findViewById(R.id.floating));
            TextView messageView = (TextView) layout.findViewById(R.id.flagRequestShortcutWarningDialogSpokenFeedback);
            messageView.setText(messageText);
            ViewGroup detailsLayout = (ViewGroup) layout.findViewById(R.id.flagRequestTouchExplorationMode);
            TextView detailsView = (TextView) detailsLayout.findViewById(R.id.flagSendMotionEvents);
            detailsView.setText(detailsId);
            CheckBox rememberChoice = (CheckBox) layout.findViewById(R.id.flagServiceHandlesDoubleTap);
            rememberChoice.setOnCheckedChangeListener(listener);
            AlertDialog d = new AlertDialog.Builder(this.mContext).setView(layout).setPositiveButton(r.getString(R.string.font_family_body_2_material), listener).setNegativeButton(r.getString(R.string.font_family_button_material), listener).setOnCancelListener(listener).create();
            d.getWindow().setType(2003);
            d.show();
            listener.setPositiveButton(d.getButton(-1));
            listener.setNegativeButton(d.getButton(-2));
        }
    }

    public int getPremiumSmsPermission(String packageName) {
        return this.mUsageMonitor.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        this.mUsageMonitor.setPremiumSmsPermission(packageName, permission);
    }

    public void sendRetrySms(SmsTracker tracker) {
        if (this.mImsSMSDispatcher != null) {
            this.mImsSMSDispatcher.sendRetrySms(tracker);
        } else {
            Rlog.e(TAG, this.mImsSMSDispatcher + " is null. Retry failed");
        }
    }

    private void sendMultipartSms(SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;
        String destinationAddress = (String) map.get("destination");
        String scAddress = (String) map.get("scaddress");
        ArrayList<String> parts = (ArrayList) map.get("parts");
        ArrayList<PendingIntent> sentIntents = (ArrayList) map.get("sentIntents");
        ArrayList<PendingIntent> deliveryIntents = (ArrayList) map.get("deliveryIntents");
        int ss = this.mPhone.getServiceState().getState();
        if (!isIms() && ss != 0) {
            int count = parts.size();
            for (int i = 0; i < count; i++) {
                PendingIntent sentIntent = null;
                if (sentIntents != null && sentIntents.size() > i) {
                    sentIntent = sentIntents.get(i);
                }
                handleNotInService(ss, sentIntent);
            }
            return;
        }
        sendMultipartText(destinationAddress, scAddress, parts, sentIntents, deliveryIntents, -1, tracker.mExpectMore, tracker.mvalidityPeriod);
    }

    protected static final class SmsTracker {
        public final PackageInfo mAppInfo;
        public final HashMap<String, Object> mData;
        public final PendingIntent mDeliveryIntent;
        public final String mDestAddress;
        public boolean mExpectMore;
        String mFormat;
        public int mImsRetry;
        public int mMessageRef;
        public int mRetryCount;
        public final PendingIntent mSentIntent;
        private Uri mSentMessageUri;
        private long mTimestamp;
        public int mvalidityPeriod;

        private SmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, PackageInfo appInfo, String destAddr, String format, boolean isExpectMore, int validityPeriod) {
            this.mTimestamp = System.currentTimeMillis();
            this.mData = data;
            this.mSentIntent = sentIntent;
            this.mDeliveryIntent = deliveryIntent;
            this.mRetryCount = 0;
            this.mAppInfo = appInfo;
            this.mDestAddress = destAddr;
            this.mFormat = format;
            this.mExpectMore = isExpectMore;
            this.mImsRetry = 0;
            this.mMessageRef = 0;
            this.mvalidityPeriod = validityPeriod;
        }

        boolean isMultipart() {
            return this.mData.containsKey("parts");
        }

        void writeSentMessage(Context context) {
            String text = (String) this.mData.get(Telephony.Mms.Part.TEXT);
            if (text != null) {
                boolean deliveryReport = this.mDeliveryIntent != null;
                this.mSentMessageUri = Telephony.Sms.addMessageToUri(context.getContentResolver(), Telephony.Sms.Sent.CONTENT_URI, this.mDestAddress, text, (String) null, Long.valueOf(this.mTimestamp), true, deliveryReport, 0L);
            }
        }

        public void updateSentMessageStatus(Context context, int status) {
            if (this.mSentMessageUri != null) {
                ContentValues values = new ContentValues(1);
                values.put(Telephony.TextBasedSmsColumns.STATUS, Integer.valueOf(status));
                SqliteWrapper.update(context, context.getContentResolver(), this.mSentMessageUri, values, (String) null, (String[]) null);
            }
        }
    }

    protected SmsTracker getSmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, String format) {
        return getSmsTracker(data, sentIntent, deliveryIntent, format, false, -1);
    }

    protected SmsTracker getSmsTracker(HashMap<String, Object> data, PendingIntent sentIntent, PendingIntent deliveryIntent, String format, boolean isExpectMore, int validityPeriod) {
        PackageManager pm = this.mContext.getPackageManager();
        String[] packageNames = pm.getPackagesForUid(Binder.getCallingUid());
        PackageInfo appInfo = null;
        if (packageNames != null && packageNames.length > 0) {
            try {
                appInfo = pm.getPackageInfo(packageNames[0], 64);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        String destAddr = PhoneNumberUtils.extractNetworkPortion((String) data.get("destAddr"));
        return new SmsTracker(data, sentIntent, deliveryIntent, appInfo, destAddr, format, isExpectMore, validityPeriod);
    }

    protected HashMap<String, Object> getSmsTrackerMap(String destAddr, String scAddr, String text, SmsMessageBase.SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put(Telephony.Mms.Part.TEXT, text);
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);
        return map;
    }

    protected HashMap<String, Object> getSmsTrackerMap(String destAddr, String scAddr, int destPort, int origPort, byte[] data, SmsMessageBase.SubmitPduBase pdu) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("destAddr", destAddr);
        map.put("scAddr", scAddr);
        map.put("destPort", Integer.valueOf(destPort));
        map.put("origPort", Integer.valueOf(origPort));
        map.put("data", data);
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);
        return map;
    }

    private final class ConfirmDialogListener implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener, CompoundButton.OnCheckedChangeListener {
        private Button mNegativeButton;
        private Button mPositiveButton;
        private boolean mRememberChoice;
        private final TextView mRememberUndoInstruction;
        private final SmsTracker mTracker;

        ConfirmDialogListener(SmsTracker tracker, TextView textView) {
            this.mTracker = tracker;
            this.mRememberUndoInstruction = textView;
        }

        void setPositiveButton(Button button) {
            this.mPositiveButton = button;
        }

        void setNegativeButton(Button button) {
            this.mNegativeButton = button;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            int newSmsPermission = 1;
            if (which == -1) {
                Rlog.d(SMSDispatcher.TAG, "CONFIRM sending SMS");
                EventLog.writeEvent(EventLogTags.EXP_DET_SMS_SENT_BY_USER, this.mTracker.mAppInfo.applicationInfo != null ? this.mTracker.mAppInfo.applicationInfo.uid : -1);
                SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(5, this.mTracker));
                if (this.mRememberChoice) {
                    newSmsPermission = 3;
                }
            } else if (which == -2) {
                Rlog.d(SMSDispatcher.TAG, "DENY sending SMS");
                EventLog.writeEvent(EventLogTags.EXP_DET_SMS_DENIED_BY_USER, this.mTracker.mAppInfo.applicationInfo != null ? this.mTracker.mAppInfo.applicationInfo.uid : -1);
                SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(7, this.mTracker));
                if (this.mRememberChoice) {
                    newSmsPermission = 2;
                }
            }
            SMSDispatcher.this.setPremiumSmsPermission(this.mTracker.mAppInfo.packageName, newSmsPermission);
        }

        @Override // android.content.DialogInterface.OnCancelListener
        public void onCancel(DialogInterface dialog) {
            Rlog.d(SMSDispatcher.TAG, "dialog dismissed: don't send SMS");
            SMSDispatcher.this.sendMessage(SMSDispatcher.this.obtainMessage(7, this.mTracker));
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Rlog.d(SMSDispatcher.TAG, "remember this choice: " + isChecked);
            this.mRememberChoice = isChecked;
            if (isChecked) {
                this.mPositiveButton.setText(R.string.font_family_display_2_material);
                this.mNegativeButton.setText(R.string.font_family_display_3_material);
                if (this.mRememberUndoInstruction != null) {
                    this.mRememberUndoInstruction.setText(R.string.font_family_display_1_material);
                    this.mRememberUndoInstruction.setPadding(0, 0, 0, 32);
                    return;
                }
                return;
            }
            this.mPositiveButton.setText(R.string.font_family_body_2_material);
            this.mNegativeButton.setText(R.string.font_family_button_material);
            if (this.mRememberUndoInstruction != null) {
                this.mRememberUndoInstruction.setText("");
                this.mRememberUndoInstruction.setPadding(0, 0, 0, 0);
            }
        }
    }

    public boolean isIms() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.isIms();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return false;
    }

    public String getImsSmsFormat() {
        if (this.mImsSMSDispatcher != null) {
            return this.mImsSMSDispatcher.getImsSmsFormat();
        }
        Rlog.e(TAG, this.mImsSMSDispatcher + " is null");
        return null;
    }
}
