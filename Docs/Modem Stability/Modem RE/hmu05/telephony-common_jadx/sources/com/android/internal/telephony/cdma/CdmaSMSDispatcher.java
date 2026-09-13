package com.android.internal.telephony.cdma;

import android.R;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Telephony;
import android.telephony.Rlog;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.cdma.sms.UserData;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class CdmaSMSDispatcher extends SMSDispatcher {
    protected static final String TAG = "CdmaSMSDispatcher";
    private static final boolean VDBG = false;

    public CdmaSMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        super(phone, usageMonitor, imsSMSDispatcher);
        Rlog.d(TAG, "CdmaSMSDispatcher created");
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected String getFormat() {
        return android.telephony.SmsMessage.FORMAT_3GPP2;
    }

    void sendStatusReportMessage(SmsMessage sms) {
        sendMessage(obtainMessage(10, sms));
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void handleStatusReport(Object o) {
        if (o instanceof SmsMessage) {
            handleCdmaStatusReport((SmsMessage) o);
        } else {
            Rlog.e(TAG, "handleStatusReport() called for object type " + o.getClass().getName());
        }
    }

    void handleCdmaStatusReport(SmsMessage sms) {
        int count = this.deliveryPendingList.size();
        for (int i = 0; i < count; i++) {
            SMSDispatcher.SmsTracker tracker = this.deliveryPendingList.get(i);
            if (tracker.mMessageRef == sms.mMessageRef) {
                this.deliveryPendingList.remove(i);
                tracker.updateSentMessageStatus(this.mContext, 0);
                PendingIntent intent = tracker.mDeliveryIntent;
                Intent fillIn = new Intent();
                fillIn.putExtra("pdu", sms.getPdu());
                fillIn.putExtra(Telephony.CellBroadcasts.MESSAGE_FORMAT, getFormat());
                try {
                    intent.send(this.mContext, -1, fillIn);
                    return;
                } catch (PendingIntent.CanceledException e) {
                    return;
                }
            }
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendData(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, origPort, data, deliveryIntent != null);
        SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, origPort, data, pdu), sentIntent, deliveryIntent, getFormat());
        sendSubmitPdu(tracker);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, (SmsHeader) null, priority);
        SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), isExpectMore, validityPeriod);
        sendSubmitPdu(tracker);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected GsmAlphabet.TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendNewSubmitPdu(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int encoding, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, int priority, boolean isExpectMore, int validityPeriod) {
        UserData uData = new UserData();
        uData.payloadStr = message;
        uData.userDataHeader = smsHeader;
        if (encoding == 1) {
            uData.msgEncoding = 9;
            Context context = this.mPhone.getContext();
            boolean ascii7bitForLongMsg = context.getResources().getBoolean(R.bool.config_biometricFrrNotificationEnabled);
            if (ascii7bitForLongMsg) {
                Rlog.d(TAG, "ascii7bitForLongMsg = " + ascii7bitForLongMsg);
                uData.msgEncoding = 2;
            }
        } else {
            uData.msgEncoding = 4;
        }
        uData.msgEncodingSet = true;
        SmsMessage.SubmitPdu submitPdu = SmsMessage.getSubmitPdu(destinationAddress, uData, deliveryIntent != null && lastPart, priority);
        SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, submitPdu), sentIntent, deliveryIntent, getFormat(), !lastPart || isExpectMore, validityPeriod);
        sendSubmitPdu(tracker);
    }

    protected void sendSubmitPdu(SMSDispatcher.SmsTracker tracker) {
        if (SystemProperties.getBoolean("ril.cdma.inecmmode", false)) {
            if (tracker.mSentIntent != null) {
                try {
                    tracker.mSentIntent.send(4);
                    return;
                } catch (PendingIntent.CanceledException e) {
                    return;
                }
            }
            return;
        }
        sendRawPdu(tracker);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendSms(SMSDispatcher.SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;
        byte[] pdu = (byte[]) map.get("pdu");
        Message reply = obtainMessage(2, tracker);
        Rlog.d(TAG, "sendSms:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
        if (tracker.mImsRetry == 0 && !isIms()) {
            this.mCi.sendCdmaSms(pdu, reply);
        } else if (!this.mImsSMSDispatcher.isImsSmsEnabled()) {
            this.mCi.sendCdmaSms(pdu, reply);
            this.mImsSMSDispatcher.enableSendSmsOverIms(true);
        } else {
            this.mCi.sendImsCdmaSms(pdu, tracker.mImsRetry, tracker.mMessageRef, reply);
            tracker.mImsRetry++;
        }
    }
}
