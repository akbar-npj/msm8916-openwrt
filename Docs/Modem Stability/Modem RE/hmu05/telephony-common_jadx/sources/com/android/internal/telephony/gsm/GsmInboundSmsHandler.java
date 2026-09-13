package com.android.internal.telephony.gsm;

import android.content.Context;
import android.os.Message;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UsimServiceTable;

/* JADX INFO: loaded from: classes.dex */
public class GsmInboundSmsHandler extends InboundSmsHandler {
    private final UsimDataDownloadHandler mDataDownloadHandler;

    protected GsmInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, PhoneBase phone) {
        super("GsmInboundSmsHandler", context, storageMonitor, phone, null);
        init(context, phone);
        phone.mCi.setOnNewGsmSms(getHandler(), 1, null);
        this.mDataDownloadHandler = new UsimDataDownloadHandler(phone.mCi);
    }

    protected void init(Context context, PhoneBase phone) {
        this.mCellBroadcastHandler = GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(context, phone);
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected void onQuitting() {
        this.mPhone.mCi.unSetOnNewGsmSms(getHandler());
        this.mCellBroadcastHandler.dispose();
        log("unregistered for 3GPP SMS");
        super.onQuitting();
    }

    public static GsmInboundSmsHandler makeInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, PhoneBase phone) {
        GsmInboundSmsHandler handler = new GsmInboundSmsHandler(context, storageMonitor, phone);
        handler.start();
        return handler;
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected boolean is3gpp2() {
        return false;
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        SmsMessage sms = (SmsMessage) smsb;
        if (sms.isTypeZero()) {
            log("Received short message type 0, Don't display or store it. Send Ack");
            return 1;
        }
        if (sms.isUsimDataDownload()) {
            UsimServiceTable ust = this.mPhone.getUsimServiceTable();
            return this.mDataDownloadHandler.handleUsimDataDownload(ust, sms);
        }
        boolean handled = false;
        if (sms.isMWISetMessage()) {
            updateMessageWaitingIndicator(sms.getNumOfVoicemails());
            handled = sms.isMwiDontStore();
            log("Received voice mail indicator set SMS shouldStore=" + (handled ? false : true));
        } else if (sms.isMWIClearMessage()) {
            updateMessageWaitingIndicator(0);
            handled = sms.isMwiDontStore();
            log("Received voice mail indicator clear SMS shouldStore=" + (handled ? false : true));
        }
        if (handled) {
            return 1;
        }
        if (!this.mStorageMonitor.isStorageAvailable() && sms.getMessageClass() != SmsConstants.MessageClass.CLASS_0) {
            return 3;
        }
        return dispatchNormalMessage(smsb);
    }

    void updateMessageWaitingIndicator(int voicemailCount) {
        if (voicemailCount < 0) {
            voicemailCount = -1;
        } else if (voicemailCount > 255) {
            voicemailCount = 255;
        }
        this.mPhone.setVoiceMessageCount(voicemailCount);
        IccRecords records = UiccController.getInstance().getIccRecords(1);
        if (records != null) {
            log("updateMessageWaitingIndicator: updating SIM Records");
            records.setVoiceMessageWaiting(1, voicemailCount);
        } else {
            log("updateMessageWaitingIndicator: SIM Records not found");
        }
        storeVoiceMailCount();
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        this.mPhone.mCi.acknowledgeLastIncomingGsmSms(success, resultToCause(result), response);
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected void onUpdatePhoneObject(PhoneBase phone) {
        super.onUpdatePhoneObject(phone);
        log("onUpdatePhoneObject: dispose of old CellBroadcastHandler and make a new one");
        this.mCellBroadcastHandler.dispose();
        init(this.mContext, phone);
    }

    private static int resultToCause(int rc) {
        switch (rc) {
            case -1:
            case 1:
                return 0;
            case 0:
            case 2:
            default:
                return 255;
            case 3:
                return 211;
        }
    }
}
