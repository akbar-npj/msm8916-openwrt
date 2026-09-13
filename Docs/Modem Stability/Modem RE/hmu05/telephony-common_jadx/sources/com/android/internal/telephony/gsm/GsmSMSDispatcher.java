package com.android.internal.telephony.gsm;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.Rlog;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

/* JADX INFO: loaded from: classes.dex */
public class GsmSMSDispatcher extends SMSDispatcher {
    private static final int EVENT_NEW_SMS_STATUS_REPORT = 100;
    private static final String TAG = "GsmSMSDispatcher";
    private static final boolean VDBG = false;
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private AtomicReference<IccRecords> mIccRecords;
    private AtomicReference<UiccCardApplication> mUiccApplication;
    protected UiccController mUiccController;

    public GsmSMSDispatcher(PhoneBase phone, SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher, GsmInboundSmsHandler gsmInboundSmsHandler) {
        super(phone, usageMonitor, imsSMSDispatcher);
        this.mUiccController = null;
        this.mIccRecords = new AtomicReference<>();
        this.mUiccApplication = new AtomicReference<>();
        this.mCi.setOnSmsStatus(this, 100, null);
        this.mCi.setOnSmsOnSim(this, 16, null);
        this.mImsSMSDispatcher = imsSMSDispatcher;
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 15, null);
        Rlog.d(TAG, "GsmSMSDispatcher created");
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void dispose() {
        super.dispose();
        this.mCi.unSetOnSmsStatus(this);
        this.mUiccController.unregisterForIccChanged(this);
        this.mCi.unSetOnSmsOnSim(this);
        if (this.mIccRecords.get() != null) {
            this.mIccRecords.get().unregisterForNewSms(this);
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected String getFormat() {
        return android.telephony.SmsMessage.FORMAT_3GPP;
    }

    @Override // com.android.internal.telephony.SMSDispatcher, android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 14:
                this.mGsmInboundSmsHandler.sendMessage(1, msg.obj);
                break;
            case 15:
                onUpdateIccAvailability();
                break;
            case 16:
                if (this.mIccRecords.get() != null) {
                    ((SIMRecords) this.mIccRecords.get()).handleSmsOnIcc((AsyncResult) msg.obj);
                }
                break;
            case 100:
                handleStatusReport((AsyncResult) msg.obj);
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private void handleStatusReport(AsyncResult ar) {
        String pduString = (String) ar.result;
        SmsMessage sms = SmsMessage.newFromCDS(pduString);
        if (sms != null) {
            int tpStatus = sms.getStatus();
            int messageRef = sms.mMessageRef;
            int count = this.deliveryPendingList.size();
            for (int i = 0; i < count; i++) {
                SMSDispatcher.SmsTracker tracker = this.deliveryPendingList.get(i);
                if (tracker.mMessageRef == messageRef) {
                    if (tpStatus >= 64 || tpStatus < 32) {
                        this.deliveryPendingList.remove(i);
                        tracker.updateSentMessageStatus(this.mContext, tpStatus);
                    }
                    PendingIntent intent = tracker.mDeliveryIntent;
                    Intent fillIn = new Intent();
                    fillIn.putExtra("pdu", IccUtils.hexStringToBytes(pduString));
                    fillIn.putExtra(Telephony.CellBroadcasts.MESSAGE_FORMAT, getFormat());
                    try {
                        intent.send(this.mContext, -1, fillIn);
                        break;
                    } catch (PendingIntent.CanceledException e) {
                        break;
                    }
                }
            }
        }
        this.mCi.acknowledgeLastIncomingGsmSms(true, 1, null);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendData(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, destPort, origPort, data, deliveryIntent != null);
        if (pdu != null) {
            SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, destPort, origPort, data, pdu), sentIntent, deliveryIntent, getFormat());
            sendRawPdu(tracker);
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendData(): getSubmitPdu() returned null");
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddr, destAddr, text, deliveryIntent != null, validityPeriod);
        if (pdu != null) {
            SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destAddr, scAddr, text, pdu), sentIntent, deliveryIntent, getFormat(), isExpectMore, validityPeriod);
            sendRawPdu(tracker);
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendText(): getSubmitPdu() returned null");
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected GsmAlphabet.TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendNewSubmitPdu(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int encoding, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, int priority, boolean isExpectMore, int validityPeriod) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, deliveryIntent != null, SmsHeader.toByteArray(smsHeader), encoding, smsHeader.languageTable, smsHeader.languageShiftTable, validityPeriod);
        if (pdu != null) {
            SMSDispatcher.SmsTracker tracker = getSmsTracker(getSmsTrackerMap(destinationAddress, scAddress, message, pdu), sentIntent, deliveryIntent, getFormat(), !lastPart || isExpectMore, validityPeriod);
            sendRawPdu(tracker);
        } else {
            Rlog.e(TAG, "GsmSMSDispatcher.sendNewSubmitPdu(): getSubmitPdu() returned null");
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendSms(SMSDispatcher.SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;
        byte[] smsc = (byte[]) map.get("smsc");
        byte[] pdu = (byte[]) map.get("pdu");
        Message reply = obtainMessage(2, tracker);
        if (tracker.mRetryCount > 0) {
            Rlog.d(TAG, "sendSms:  mRetryCount=" + tracker.mRetryCount + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
            if ((pdu[0] & 1) == 1) {
                pdu[0] = (byte) (pdu[0] | 4);
                pdu[1] = (byte) tracker.mMessageRef;
            }
        }
        Rlog.d(TAG, "sendSms:  isIms()=" + isIms() + " mRetryCount=" + tracker.mRetryCount + " mImsRetry=" + tracker.mImsRetry + " mMessageRef=" + tracker.mMessageRef + " SS=" + this.mPhone.getServiceState().getState());
        if (tracker.mImsRetry == 0 && !isIms()) {
            if (tracker.mRetryCount > 0 && (pdu[0] & 1) == 1) {
                pdu[0] = (byte) (pdu[0] | 4);
                pdu[1] = (byte) tracker.mMessageRef;
            }
            if (tracker.mRetryCount == 0 && tracker.mExpectMore) {
                this.mCi.sendSMSExpectMore(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), reply);
                return;
            } else {
                this.mCi.sendSMS(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), reply);
                return;
            }
        }
        this.mCi.sendImsGsmSms(IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), tracker.mImsRetry, tracker.mMessageRef, reply);
        tracker.mImsRetry++;
    }

    protected UiccCardApplication getUiccCardApplication() {
        return this.mUiccController.getUiccCardApplication(1);
    }

    private void onUpdateIccAvailability() {
        UiccCardApplication newUiccApplication;
        UiccCardApplication app;
        if (this.mUiccController != null && (app = this.mUiccApplication.get()) != (newUiccApplication = getUiccCardApplication())) {
            if (app != null) {
                Rlog.d(TAG, "Removing stale icc objects.");
                if (this.mIccRecords.get() != null) {
                    this.mIccRecords.get().unregisterForNewSms(this);
                }
                this.mIccRecords.set(null);
                this.mUiccApplication.set(null);
            }
            if (newUiccApplication != null) {
                Rlog.d(TAG, "New Uicc application found");
                this.mUiccApplication.set(newUiccApplication);
                this.mIccRecords.set(newUiccApplication.getIccRecords());
                if (this.mIccRecords.get() != null) {
                    this.mIccRecords.get().registerForNewSms(this, 14, null);
                }
            }
        }
    }
}
