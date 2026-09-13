package com.android.internal.telephony.cdma;

import android.R;
import android.content.Context;
import android.content.res.Resources;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.SmsCbMessage;
import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class CdmaInboundSmsHandler extends InboundSmsHandler {
    private final boolean mCheckForDuplicatePortsInOmadmWapPush;
    private byte[] mLastAcknowledgedSmsFingerprint;
    private byte[] mLastDispatchedSmsFingerprint;
    protected CdmaServiceCategoryProgramHandler mServiceCategoryProgramHandler;
    private final CdmaSMSDispatcher mSmsDispatcher;

    protected CdmaInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, PhoneBase phone, CdmaSMSDispatcher smsDispatcher) {
        super("CdmaInboundSmsHandler", context, storageMonitor, phone, null);
        this.mCheckForDuplicatePortsInOmadmWapPush = Resources.getSystem().getBoolean(R.bool.config_batterySaverSupported);
        this.mSmsDispatcher = smsDispatcher;
        init(context, phone);
        this.mPhone = phone;
        phone.mCi.setOnNewCdmaSms(getHandler(), 1, null);
    }

    protected void init(Context context, PhoneBase phone) {
        this.mCellBroadcastHandler = CellBroadcastHandler.makeCellBroadcastHandler(context);
        this.mServiceCategoryProgramHandler = CdmaServiceCategoryProgramHandler.makeScpHandler(context, phone.mCi);
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected void onQuitting() {
        this.mPhone.mCi.unSetOnNewCdmaSms(getHandler());
        this.mCellBroadcastHandler.dispose();
        log("unregistered for 3GPP2 SMS");
        super.onQuitting();
    }

    public static CdmaInboundSmsHandler makeInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor, PhoneBase phone, CdmaSMSDispatcher smsDispatcher) {
        CdmaInboundSmsHandler handler = new CdmaInboundSmsHandler(context, storageMonitor, phone, smsDispatcher);
        handler.start();
        return handler;
    }

    private static boolean isInEmergencyCallMode() {
        String inEcm = SystemProperties.get("ril.cdma.inecmmode", "false");
        return "true".equals(inEcm);
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected boolean is3gpp2() {
        return true;
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        if (isInEmergencyCallMode()) {
            return -1;
        }
        SmsMessage sms = (SmsMessage) smsb;
        boolean isBroadcastType = 1 == sms.getMessageType();
        if (isBroadcastType) {
            log("Broadcast type message");
            SmsCbMessage cbMessage = sms.parseBroadcastSms();
            if (cbMessage != null) {
                this.mCellBroadcastHandler.dispatchSmsMessage(cbMessage);
                return 1;
            }
            loge("error trying to parse broadcast SMS");
            return 1;
        }
        this.mLastDispatchedSmsFingerprint = sms.getIncomingSmsFingerprint();
        if (this.mLastAcknowledgedSmsFingerprint != null && Arrays.equals(this.mLastDispatchedSmsFingerprint, this.mLastAcknowledgedSmsFingerprint)) {
            return 1;
        }
        sms.parseSms();
        int teleService = sms.getTeleService();
        switch (teleService) {
            case 4098:
            case SmsEnvelope.TELESERVICE_WEMT /* 4101 */:
                if (sms.isStatusReportMessage()) {
                    this.mSmsDispatcher.sendStatusReportMessage(sms);
                    return 1;
                }
                break;
            case 4099:
            case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                handleVoicemailTeleservice(sms);
                return 1;
            case 4100:
            case SmsEnvelope.TELESERVICE_CT_WAP /* 65002 */:
                break;
            case SmsEnvelope.TELESERVICE_SCPT /* 4102 */:
                this.mServiceCategoryProgramHandler.dispatchSmsMessage(sms);
                return 1;
            default:
                loge("unsupported teleservice 0x" + Integer.toHexString(teleService));
                return 4;
        }
        if (!this.mStorageMonitor.isStorageAvailable() && sms.getMessageClass() != SmsConstants.MessageClass.CLASS_0) {
            return 3;
        }
        if (4100 == teleService) {
            return processCdmaWapPdu(sms.getUserData(), sms.mMessageRef, sms.getOriginatingAddress(), sms.getTimestampMillis());
        }
        if (65002 == teleService) {
            if (sms.processCdmaCTWdpHeader(sms)) {
                return processCdmaWapPdu(sms.getUserData(), sms.mMessageRef, sms.getOriginatingAddress(), sms.getTimestampMillis());
            }
            return 1;
        }
        return dispatchNormalMessage(smsb);
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        if (!isInEmergencyCallMode()) {
            int causeCode = resultToCause(result);
            this.mPhone.mCi.acknowledgeLastIncomingCdmaSms(success, causeCode, response);
            if (causeCode == 0) {
                this.mLastAcknowledgedSmsFingerprint = this.mLastDispatchedSmsFingerprint;
            }
            this.mLastDispatchedSmsFingerprint = null;
        }
    }

    @Override // com.android.internal.telephony.InboundSmsHandler
    protected void onUpdatePhoneObject(PhoneBase phone) {
        super.onUpdatePhoneObject(phone);
        this.mCellBroadcastHandler.updatePhoneObject(phone);
        this.mServiceCategoryProgramHandler.updatePhoneObject(phone);
    }

    private static int resultToCause(int rc) {
        switch (rc) {
            case -1:
            case 1:
                return 0;
            case 0:
            case 2:
            default:
                return 96;
            case 3:
                return 35;
            case 4:
                return 4;
        }
    }

    private void handleVoicemailTeleservice(SmsMessage sms) {
        int voicemailCount = sms.getNumOfVoicemails();
        log("Voicemail count=" + voicemailCount);
        if (voicemailCount < 0) {
            voicemailCount = -1;
        } else if (voicemailCount > 99) {
            voicemailCount = 99;
        }
        this.mPhone.setVoiceMessageCount(voicemailCount);
        storeVoiceMailCount();
    }

    /* JADX WARN: Code duplicated, block: B:17:0x00f7 A[PHI: r5 r13 r15
      0x00f7: PHI (r5v1 'destinationPort' int) = (r5v0 'destinationPort' int), (r5v4 'destinationPort' int), (r5v4 'destinationPort' int) binds: [B:10:0x004a, B:12:0x006e, B:14:0x0076] A[DONT_GENERATE, DONT_INLINE]
      0x00f7: PHI (r13v2 'index' int) = (r13v1 'index' int), (r13v4 'index' int), (r13v4 'index' int) binds: [B:10:0x004a, B:12:0x006e, B:14:0x0076] A[DONT_GENERATE, DONT_INLINE]
      0x00f7: PHI (r15v1 'sourcePort' int) = (r15v0 'sourcePort' int), (r15v4 'sourcePort' int), (r15v4 'sourcePort' int) binds: [B:10:0x004a, B:12:0x006e, B:14:0x0076] A[DONT_GENERATE, DONT_INLINE]] */
    private int processCdmaWapPdu(byte[] pdu, int referenceNumber, String address, long timestamp) {
        int index;
        int index2 = 0 + 1;
        int msgType = pdu[0] & 255;
        if (msgType != 0) {
            log("Received a WAP SMS which is not WDP. Discard.");
            return 1;
        }
        int index3 = index2 + 1;
        int totalSegments = pdu[index2] & 255;
        int index4 = index3 + 1;
        int segment = pdu[index3] & 255;
        if (segment >= totalSegments) {
            loge("WDP bad segment #" + segment + " expecting 0-" + (totalSegments - 1));
            return 1;
        }
        int sourcePort = 0;
        int destinationPort = 0;
        if (segment == 0) {
            int index5 = index4 + 1;
            int sourcePort2 = (pdu[index4] & 255) << 8;
            int index6 = index5 + 1;
            sourcePort = sourcePort2 | (pdu[index5] & 255);
            int index7 = index6 + 1;
            int destinationPort2 = (pdu[index6] & 255) << 8;
            index4 = index7 + 1;
            destinationPort = destinationPort2 | (pdu[index7] & 255);
            if (this.mCheckForDuplicatePortsInOmadmWapPush && checkDuplicatePortOmadmWapPush(pdu, index4)) {
                index = index4 + 4;
            } else {
                index = index4;
            }
        } else {
            index = index4;
        }
        log("Received WAP PDU. Type = " + msgType + ", originator = " + address + ", src-port = " + sourcePort + ", dst-port = " + destinationPort + ", ID = " + referenceNumber + ", segment# = " + segment + '/' + totalSegments);
        byte[] userData = new byte[pdu.length - index];
        System.arraycopy(pdu, index, userData, 0, pdu.length - index);
        InboundSmsTracker tracker = new InboundSmsTracker(userData, timestamp, destinationPort, true, address, referenceNumber, segment, totalSegments, true);
        return addTrackerToRawTableAndSendMessage(tracker);
    }

    private static boolean checkDuplicatePortOmadmWapPush(byte[] origPdu, int index) {
        int index2 = index + 4;
        byte[] omaPdu = new byte[origPdu.length - index2];
        System.arraycopy(origPdu, index2, omaPdu, 0, omaPdu.length);
        WspTypeDecoder pduDecoder = new WspTypeDecoder(omaPdu);
        if (!pduDecoder.decodeUintvarInteger(2)) {
            return false;
        }
        int wspIndex = 2 + pduDecoder.getDecodedDataLength();
        if (!pduDecoder.decodeContentType(wspIndex)) {
            return false;
        }
        String mimeType = pduDecoder.getValueString();
        return WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI.equals(mimeType);
    }
}
