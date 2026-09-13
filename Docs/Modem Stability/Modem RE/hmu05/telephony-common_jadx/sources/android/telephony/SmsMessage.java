package android.telephony;

import android.os.Parcel;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import java.util.ArrayList;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class SmsMessage {
    public static final int ENCODING_16BIT = 3;
    public static final int ENCODING_7BIT = 1;
    public static final int ENCODING_8BIT = 2;
    public static final int ENCODING_KSC5601 = 4;
    public static final int ENCODING_UNKNOWN = 0;
    public static final String FORMAT_3GPP = "3gpp";
    public static final String FORMAT_3GPP2 = "3gpp2";
    private static final String LOG_TAG = "SmsMessage";
    public static final int MAX_USER_DATA_BYTES = 140;
    public static final int MAX_USER_DATA_BYTES_WITH_HEADER = 134;
    public static final int MAX_USER_DATA_SEPTETS = 160;
    public static final int MAX_USER_DATA_SEPTETS_WITH_HEADER = 153;
    private int mSubId = 0;
    public SmsMessageBase mWrappedSmsMessage;

    public enum MessageClass {
        UNKNOWN,
        CLASS_0,
        CLASS_1,
        CLASS_2,
        CLASS_3
    }

    public void setSubId(int subId) {
        this.mSubId = subId;
    }

    public int getSubId() {
        return this.mSubId;
    }

    public static class SubmitPdu {
        public byte[] encodedMessage;
        public byte[] encodedScAddress;

        public String toString() {
            return "SubmitPdu: encodedScAddress = " + Arrays.toString(this.encodedScAddress) + ", encodedMessage = " + Arrays.toString(this.encodedMessage);
        }

        protected SubmitPdu(SmsMessageBase.SubmitPduBase spb) {
            this.encodedMessage = spb.encodedMessage;
            this.encodedScAddress = spb.encodedScAddress;
        }
    }

    private SmsMessage(SmsMessageBase smb) {
        this.mWrappedSmsMessage = smb;
    }

    public static SmsMessage createFromPdu(byte[] pdu) {
        int activePhone = TelephonyManager.getDefault().getCurrentPhoneType();
        String format = 2 == activePhone ? FORMAT_3GPP2 : FORMAT_3GPP;
        SmsMessage message = createFromPdu(pdu, format);
        if (message == null || message.mWrappedSmsMessage == null) {
            String format2 = 2 == activePhone ? FORMAT_3GPP : FORMAT_3GPP2;
            return createFromPdu(pdu, format2);
        }
        return message;
    }

    public static SmsMessage createFromPdu(byte[] pdu, String format) {
        SmsMessageBase wrappedMessage;
        if (FORMAT_3GPP2.equals(format)) {
            wrappedMessage = com.android.internal.telephony.cdma.SmsMessage.createFromPdu(pdu);
        } else if (FORMAT_3GPP.equals(format)) {
            wrappedMessage = com.android.internal.telephony.gsm.SmsMessage.createFromPdu(pdu);
        } else {
            Rlog.e(LOG_TAG, "createFromPdu(): unsupported message format " + format);
            return null;
        }
        return new SmsMessage(wrappedMessage);
    }

    public static SmsMessage newFromCMT(String[] lines) {
        SmsMessageBase wrappedMessage = com.android.internal.telephony.gsm.SmsMessage.newFromCMT(lines);
        return new SmsMessage(wrappedMessage);
    }

    public static SmsMessage newFromParcel(Parcel p) {
        SmsMessageBase wrappedMessage = com.android.internal.telephony.cdma.SmsMessage.newFromParcel(p);
        return new SmsMessage(wrappedMessage);
    }

    public static SmsMessage createFromEfRecord(int index, byte[] data) {
        SmsMessageBase wrappedMessage;
        if (isCdmaVoice()) {
            wrappedMessage = com.android.internal.telephony.cdma.SmsMessage.createFromEfRecord(index, data);
        } else {
            wrappedMessage = com.android.internal.telephony.gsm.SmsMessage.createFromEfRecord(index, data);
        }
        if (wrappedMessage != null) {
            return new SmsMessage(wrappedMessage);
        }
        return null;
    }

    public static SmsMessage createFromEfRecord(int index, byte[] data, int sub) {
        SmsMessageBase wrappedMessage;
        if (isCdmaVoice(sub)) {
            wrappedMessage = com.android.internal.telephony.cdma.SmsMessage.createFromEfRecord(index, data);
        } else {
            wrappedMessage = com.android.internal.telephony.gsm.SmsMessage.createFromEfRecord(index, data);
        }
        if (wrappedMessage != null) {
            return new SmsMessage(wrappedMessage);
        }
        return null;
    }

    public static int getTPLayerLengthForPDU(String pdu) {
        return isCdmaVoice() ? com.android.internal.telephony.cdma.SmsMessage.getTPLayerLengthForPDU(pdu) : com.android.internal.telephony.gsm.SmsMessage.getTPLayerLengthForPDU(pdu);
    }

    public static int[] calculateLength(CharSequence msgBody, boolean use7bitOnly) {
        GsmAlphabet.TextEncodingDetails ted = useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.calculateLength(msgBody, use7bitOnly) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(msgBody, use7bitOnly);
        int[] ret = {ted.msgCount, ted.codeUnitCount, ted.codeUnitsRemaining, ted.codeUnitSize};
        return ret;
    }

    public static ArrayList<String> fragmentText(String text) {
        int limit;
        int nextPos;
        int udhLength;
        GsmAlphabet.TextEncodingDetails ted = useCdmaFormatForMoSms() ? com.android.internal.telephony.cdma.SmsMessage.calculateLength(text, false) : com.android.internal.telephony.gsm.SmsMessage.calculateLength(text, false);
        if (ted.codeUnitSize == 1) {
            if (ted.languageTable != 0 && ted.languageShiftTable != 0) {
                udhLength = 7;
            } else if (ted.languageTable != 0 || ted.languageShiftTable != 0) {
                udhLength = 4;
            } else {
                udhLength = 0;
            }
            if (ted.msgCount > 1) {
                udhLength += 6;
            }
            if (udhLength != 0) {
                udhLength++;
            }
            limit = 160 - udhLength;
        } else if (ted.msgCount > 1) {
            limit = 134;
        } else {
            limit = 140;
        }
        int pos = 0;
        int textLen = text.length();
        ArrayList<String> result = new ArrayList<>(ted.msgCount);
        while (pos < textLen) {
            if (ted.codeUnitSize == 1) {
                if (useCdmaFormatForMoSms() && ted.msgCount == 1) {
                    nextPos = pos + Math.min(limit, textLen - pos);
                } else {
                    nextPos = GsmAlphabet.findGsmSeptetLimitIndex(text, pos, limit, ted.languageTable, ted.languageShiftTable);
                }
            } else {
                nextPos = pos + Math.min(limit / 2, textLen - pos);
            }
            if (nextPos <= pos || nextPos > textLen) {
                Rlog.e(LOG_TAG, "fragmentText failed (" + pos + " >= " + nextPos + " or " + nextPos + " >= " + textLen + ")");
                break;
            }
            result.add(text.substring(pos, nextPos));
            pos = nextPos;
        }
        return result;
    }

    public static int[] calculateLength(String messageBody, boolean use7bitOnly) {
        return calculateLength((CharSequence) messageBody, use7bitOnly);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested) {
        SmsMessageBase.SubmitPduBase spb;
        if (useCdmaFormatForMoSms()) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, (SmsHeader) null);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested);
        }
        return new SubmitPdu(spb);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, int sub) {
        SmsMessageBase.SubmitPduBase spb;
        if (useCdmaFormatForMoSms(sub)) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, (SmsHeader) null);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested);
        }
        return new SubmitPdu(spb);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, short destinationPort, byte[] data, boolean statusReportRequested) {
        SmsMessageBase.SubmitPduBase spb;
        if (useCdmaFormatForMoSms()) {
            spb = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddress, destinationAddress, destinationPort, data, statusReportRequested);
        } else {
            spb = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddress, destinationAddress, destinationPort, data, statusReportRequested);
        }
        return new SubmitPdu(spb);
    }

    public String getServiceCenterAddress() {
        return this.mWrappedSmsMessage.getServiceCenterAddress();
    }

    public String getOriginatingAddress() {
        return this.mWrappedSmsMessage.getOriginatingAddress();
    }

    public String getDisplayOriginatingAddress() {
        return this.mWrappedSmsMessage.getDisplayOriginatingAddress();
    }

    public String getMessageBody() {
        return this.mWrappedSmsMessage.getMessageBody();
    }

    /* JADX INFO: renamed from: android.telephony.SmsMessage$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass = new int[SmsConstants.MessageClass.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[SmsConstants.MessageClass.CLASS_0.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[SmsConstants.MessageClass.CLASS_1.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[SmsConstants.MessageClass.CLASS_2.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[SmsConstants.MessageClass.CLASS_3.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
        }
    }

    public MessageClass getMessageClass() {
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$SmsConstants$MessageClass[this.mWrappedSmsMessage.getMessageClass().ordinal()]) {
            case 1:
                return MessageClass.CLASS_0;
            case 2:
                return MessageClass.CLASS_1;
            case 3:
                return MessageClass.CLASS_2;
            case 4:
                return MessageClass.CLASS_3;
            default:
                return MessageClass.UNKNOWN;
        }
    }

    public String getDisplayMessageBody() {
        return this.mWrappedSmsMessage.getDisplayMessageBody();
    }

    public String getPseudoSubject() {
        return this.mWrappedSmsMessage.getPseudoSubject();
    }

    public long getTimestampMillis() {
        return this.mWrappedSmsMessage.getTimestampMillis();
    }

    public boolean isEmail() {
        return this.mWrappedSmsMessage.isEmail();
    }

    public String getEmailBody() {
        return this.mWrappedSmsMessage.getEmailBody();
    }

    public String getEmailFrom() {
        return this.mWrappedSmsMessage.getEmailFrom();
    }

    public int getProtocolIdentifier() {
        return this.mWrappedSmsMessage.getProtocolIdentifier();
    }

    public boolean isReplace() {
        return this.mWrappedSmsMessage.isReplace();
    }

    public boolean isCphsMwiMessage() {
        return this.mWrappedSmsMessage.isCphsMwiMessage();
    }

    public boolean isMWIClearMessage() {
        return this.mWrappedSmsMessage.isMWIClearMessage();
    }

    public boolean isMWISetMessage() {
        return this.mWrappedSmsMessage.isMWISetMessage();
    }

    public boolean isMwiDontStore() {
        return this.mWrappedSmsMessage.isMwiDontStore();
    }

    public byte[] getUserData() {
        return this.mWrappedSmsMessage.getUserData();
    }

    public byte[] getPdu() {
        return this.mWrappedSmsMessage.getPdu();
    }

    @Deprecated
    public int getStatusOnSim() {
        return this.mWrappedSmsMessage.getStatusOnIcc();
    }

    public int getStatusOnIcc() {
        return this.mWrappedSmsMessage.getStatusOnIcc();
    }

    @Deprecated
    public int getIndexOnSim() {
        return this.mWrappedSmsMessage.getIndexOnIcc();
    }

    public int getIndexOnIcc() {
        return this.mWrappedSmsMessage.getIndexOnIcc();
    }

    public int getStatus() {
        return this.mWrappedSmsMessage.getStatus();
    }

    public boolean isStatusReportMessage() {
        return this.mWrappedSmsMessage.isStatusReportMessage();
    }

    public boolean isReplyPathPresent() {
        return this.mWrappedSmsMessage.isReplyPathPresent();
    }

    private static boolean useCdmaFormatForMoSms() {
        return !SmsManager.getDefault().isImsSmsSupported() ? isCdmaVoice() : FORMAT_3GPP2.equals(SmsManager.getDefault().getImsSmsFormat());
    }

    private static boolean useCdmaFormatForMoSms(int sub) {
        return !MSimSmsManager.getDefault().isImsSmsSupported(sub) ? isCdmaVoice(sub) : FORMAT_3GPP2.equals(MSimSmsManager.getDefault().getImsSmsFormat(sub));
    }

    private static boolean isCdmaVoice() {
        int activePhone = TelephonyManager.getDefault().getCurrentPhoneType();
        return 2 == activePhone;
    }

    private static boolean isCdmaVoice(int subscription) {
        int activePhone = MSimTelephonyManager.getDefault().getCurrentPhoneType(subscription);
        return 2 == activePhone;
    }

    public String getRecipientAddress() {
        return this.mWrappedSmsMessage.getRecipientAddress();
    }
}
