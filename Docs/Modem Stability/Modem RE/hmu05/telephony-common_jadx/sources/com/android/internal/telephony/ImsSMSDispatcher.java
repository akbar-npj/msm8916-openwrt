package com.android.internal.telephony;

import android.R;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.cdma.CdmaSMSDispatcher;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmSMSDispatcher;
import java.util.ArrayList;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class ImsSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "RIL_ImsSms";
    protected SMSDispatcher mCdmaDispatcher;
    protected CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    protected SMSDispatcher mGsmDispatcher;
    protected GsmInboundSmsHandler mGsmInboundSmsHandler;
    private boolean mIms;
    private boolean mImsSmsEnabled;
    private String mImsSmsFormat;

    public ImsSMSDispatcher(PhoneBase phone, SmsStorageMonitor storageMonitor, SmsUsageMonitor usageMonitor) {
        super(phone, usageMonitor, null);
        this.mIms = false;
        this.mImsSmsFormat = "unknown";
        this.mImsSmsEnabled = true;
        Rlog.d(TAG, "ImsSMSDispatcher created");
        initDispatchers(phone, storageMonitor, usageMonitor);
        this.mCi.registerForOn(this, 11, null);
        this.mCi.registerForImsNetworkStateChanged(this, 12, null);
    }

    protected void initDispatchers(PhoneBase phone, SmsStorageMonitor storageMonitor, SmsUsageMonitor usageMonitor) {
        this.mCdmaDispatcher = new CdmaSMSDispatcher(phone, usageMonitor, this);
        this.mGsmInboundSmsHandler = GsmInboundSmsHandler.makeInboundSmsHandler(phone.getContext(), storageMonitor, phone);
        this.mCdmaInboundSmsHandler = CdmaInboundSmsHandler.makeInboundSmsHandler(phone.getContext(), storageMonitor, phone, (CdmaSMSDispatcher) this.mCdmaDispatcher);
        this.mGsmDispatcher = new GsmSMSDispatcher(phone, usageMonitor, this, this.mGsmInboundSmsHandler);
        Thread broadcastThread = new Thread(new SmsBroadcastUndelivered(phone.getContext(), this.mGsmInboundSmsHandler, this.mCdmaInboundSmsHandler));
        broadcastThread.start();
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void updatePhoneObject(PhoneBase phone) {
        Rlog.d(TAG, "In IMS updatePhoneObject ");
        super.updatePhoneObject(phone);
        this.mCdmaDispatcher.updatePhoneObject(phone);
        this.mGsmDispatcher.updatePhoneObject(phone);
        this.mGsmInboundSmsHandler.updatePhoneObject(phone);
        this.mCdmaInboundSmsHandler.updatePhoneObject(phone);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void dispose() {
        this.mCi.unregisterForOn(this);
        this.mCi.unregisterForImsNetworkStateChanged(this);
        this.mGsmDispatcher.dispose();
        this.mCdmaDispatcher.dispose();
        this.mGsmInboundSmsHandler.dispose();
        this.mCdmaInboundSmsHandler.dispose();
    }

    @Override // com.android.internal.telephony.SMSDispatcher, android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 11:
            case 12:
                this.mCi.getImsRegistrationState(obtainMessage(13));
                break;
            case 13:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    updateImsInfo(ar);
                } else {
                    Rlog.e(TAG, "IMS State query failed with exp " + ar.exception);
                }
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private void setImsSmsFormat(int format) {
        switch (format) {
            case 1:
                this.mImsSmsFormat = SmsMessage.FORMAT_3GPP;
                break;
            case 2:
                this.mImsSmsFormat = SmsMessage.FORMAT_3GPP2;
                break;
            default:
                this.mImsSmsFormat = "unknown";
                break;
        }
    }

    private void updateImsInfo(AsyncResult ar) {
        int[] responseArray = (int[]) ar.result;
        this.mIms = false;
        if (responseArray[0] == 1) {
            Rlog.d(TAG, "IMS is registered!");
            this.mIms = true;
        } else {
            Rlog.d(TAG, "IMS is NOT registered!");
        }
        setImsSmsFormat(responseArray[1]);
        if ("unknown".equals(this.mImsSmsFormat)) {
            Rlog.e(TAG, "IMS format was unknown!");
            this.mIms = false;
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendData(String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendData(destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
        } else {
            this.mGsmDispatcher.sendData(destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendMultipartText(String destAddr, String scAddr, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, int priority, boolean isExpectMore, int validityPeriod) {
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, priority, isExpectMore, validityPeriod);
        } else {
            this.mGsmDispatcher.sendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, priority, isExpectMore, validityPeriod);
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendSms(SMSDispatcher.SmsTracker tracker) {
        Rlog.e(TAG, "sendSms should never be called from here!");
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendText(String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod) {
        Rlog.d(TAG, "sendText");
        if (isCdmaMo()) {
            this.mCdmaDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, priority, isExpectMore, validityPeriod);
        } else {
            this.mGsmDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, priority, isExpectMore, validityPeriod);
        }
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public void sendRetrySms(SMSDispatcher.SmsTracker tracker) {
        String oldFormat = tracker.mFormat;
        String newFormat = 2 == this.mPhone.getPhoneType() ? this.mCdmaDispatcher.getFormat() : this.mGsmDispatcher.getFormat();
        if (oldFormat.equals(newFormat)) {
            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format matched new format (cdma)");
                shouldSendSmsOverIms();
                this.mCdmaDispatcher.sendSms(tracker);
                return;
            } else {
                Rlog.d(TAG, "old format matched new format (gsm)");
                this.mGsmDispatcher.sendSms(tracker);
                return;
            }
        }
        HashMap<String, Object> map = tracker.mData;
        if (!map.containsKey("scAddr") || !map.containsKey("destAddr") || (!map.containsKey(Telephony.Mms.Part.TEXT) && (!map.containsKey("data") || !map.containsKey("destPort")))) {
            Rlog.e(TAG, "sendRetrySms failed to re-encode per missing fields!");
            if (tracker.mSentIntent != null) {
                try {
                    tracker.mSentIntent.send(this.mContext, 1, (Intent) null);
                    return;
                } catch (PendingIntent.CanceledException e) {
                    return;
                }
            }
            return;
        }
        String scAddr = (String) map.get("scAddr");
        String destAddr = (String) map.get("destAddr");
        SmsMessageBase.SubmitPduBase pdu = null;
        if (map.containsKey(Telephony.Mms.Part.TEXT)) {
            Rlog.d(TAG, "sms failed was text");
            String text = (String) map.get(Telephony.Mms.Part.TEXT);
            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddr, destAddr, text, tracker.mDeliveryIntent != null, (SmsHeader) null);
                shouldSendSmsOverIms();
            } else {
                Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddr, destAddr, text, tracker.mDeliveryIntent != null, (byte[]) null);
            }
        } else if (map.containsKey("data")) {
            Rlog.d(TAG, "sms failed was data");
            byte[] data = (byte[]) map.get("data");
            Integer destPort = (Integer) map.get("destPort");
            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddr, destAddr, destPort.intValue(), data, tracker.mDeliveryIntent != null);
                shouldSendSmsOverIms();
            } else {
                Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddr, destAddr, destPort.intValue(), data, tracker.mDeliveryIntent != null);
            }
        }
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);
        SMSDispatcher dispatcher = isCdmaFormat(newFormat) ? this.mCdmaDispatcher : this.mGsmDispatcher;
        tracker.mFormat = dispatcher.getFormat();
        dispatcher.sendSms(tracker);
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected String getFormat() {
        Rlog.e(TAG, "getFormat should never be called from here!");
        return "unknown";
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected GsmAlphabet.TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
        return null;
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    protected void sendNewSubmitPdu(String destinationAddress, String scAddress, String message, SmsHeader smsHeader, int format, PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart, int priority, boolean isExpectMore, int validityPeriod) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public boolean isIms() {
        return this.mIms;
    }

    @Override // com.android.internal.telephony.SMSDispatcher
    public String getImsSmsFormat() {
        return this.mImsSmsFormat;
    }

    private boolean isCdmaMo() {
        if (isIms() && shouldSendSmsOverIms()) {
            return isCdmaFormat(this.mImsSmsFormat);
        }
        return 2 == this.mPhone.getPhoneType();
    }

    private boolean isCdmaFormat(String format) {
        return this.mCdmaDispatcher.getFormat().equals(format);
    }

    public void enableSendSmsOverIms(boolean enable) {
        this.mImsSmsEnabled = enable;
    }

    public boolean isImsSmsEnabled() {
        return this.mImsSmsEnabled;
    }

    public boolean shouldSendSmsOverIms() {
        boolean sendSmsOn1x = this.mContext.getResources().getBoolean(R.bool.config_canRemoveFirstAccount);
        int currentCallState = this.mTelephonyManager.getCallState();
        int currentVoiceNetwork = this.mTelephonyManager.getVoiceNetworkType();
        int currentDataNetwork = this.mTelephonyManager.getDataNetworkType();
        Rlog.d(TAG, "data = " + currentDataNetwork + " voice = " + currentVoiceNetwork + " call state = " + currentCallState);
        if (sendSmsOn1x && currentDataNetwork == 14 && currentVoiceNetwork == 7) {
            TelephonyManager telephonyManager = this.mTelephonyManager;
            if (currentCallState != 0) {
                enableSendSmsOverIms(false);
                return false;
            }
        }
        return true;
    }
}
