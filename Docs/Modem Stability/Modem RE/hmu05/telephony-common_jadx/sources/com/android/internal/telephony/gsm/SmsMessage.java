package com.android.internal.telephony.gsm;

import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.format.Time;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.telephony.uicc.IccUtils;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;

/* JADX INFO: loaded from: classes.dex */
public class SmsMessage extends SmsMessageBase {
    private static final int INVALID_VALIDITY_PERIOD = -1;
    static final String LOG_TAG = "SmsMessage";
    private static final int VALIDITY_PERIOD_FORMAT_ABSOLUTE = 3;
    private static final int VALIDITY_PERIOD_FORMAT_ENHANCED = 1;
    private static final int VALIDITY_PERIOD_FORMAT_NONE = 0;
    private static final int VALIDITY_PERIOD_FORMAT_RELATIVE = 2;
    private static final int VALIDITY_PERIOD_MAX = 635040;
    private static final int VALIDITY_PERIOD_MIN = 5;
    private static final boolean VDBG = false;
    private int mDataCodingScheme;
    private int mMti;
    private int mProtocolIdentifier;
    private int mStatus;
    private SmsConstants.MessageClass messageClass;
    private boolean mReplyPathPresent = false;
    private boolean mIsStatusReportMessage = false;
    private int mVoiceMailCount = 0;

    public static class SubmitPdu extends SmsMessageBase.SubmitPduBase {
    }

    public static SmsMessage createFromPdu(byte[] pdu) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(pdu);
            return msg;
        } catch (OutOfMemoryError e) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed with out of memory: ", e);
            return null;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public boolean isTypeZero() {
        return this.mProtocolIdentifier == 64;
    }

    public static SmsMessage newFromCMT(String[] lines) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(IccUtils.hexStringToBytes(lines[1]));
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static SmsMessage newFromCDS(String line) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.parsePdu(IccUtils.hexStringToBytes(line));
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "CDS SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static SmsMessage createFromEfRecord(int index, byte[] data) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.mIndexOnIcc = index;
            if ((data[0] & 1) == 0) {
                Rlog.w(LOG_TAG, "SMS parsing failed: Trying to parse a free record");
                msg = null;
            } else {
                msg.mStatusOnIcc = data[0] & 7;
                int size = data.length - 1;
                byte[] pdu = new byte[size];
                System.arraycopy(data, 1, pdu, 0, size);
                msg.parsePdu(pdu);
            }
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static int getTPLayerLengthForPDU(String pdu) {
        int len = pdu.length() / 2;
        int smscLen = Integer.parseInt(pdu.substring(0, 2), 16);
        return (len - smscLen) - 1;
    }

    public static int getRelativeValidityPeriod(int validityPeriod) {
        int relValidityPeriod = -1;
        if (validityPeriod < 5 || validityPeriod > VALIDITY_PERIOD_MAX) {
            Rlog.e(LOG_TAG, "Invalid Validity Period" + validityPeriod);
            return -1;
        }
        if (validityPeriod <= 720) {
            relValidityPeriod = (validityPeriod / 5) - 1;
        } else if (validityPeriod <= 1440) {
            relValidityPeriod = ((validityPeriod - 720) / 30) + BearerData.RELATIVE_TIME_MINS_LIMIT;
        } else if (validityPeriod <= 43200) {
            relValidityPeriod = (validityPeriod / 1440) + 166;
        } else if (validityPeriod <= VALIDITY_PERIOD_MAX) {
            relValidityPeriod = (validityPeriod / 10080) + 192;
        }
        return relValidityPeriod;
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, byte[] header) {
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, header, 0, 0, 0);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, byte[] header, int encoding, int languageTable, int languageShiftTable) {
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, header, encoding, languageTable, languageShiftTable, -1);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, byte[] header, int encoding, int languageTable, int languageShiftTable, int validityPeriod) {
        byte[] userData;
        if (message == null || destinationAddress == null) {
            return null;
        }
        if (encoding == 0) {
            GsmAlphabet.TextEncodingDetails ted = calculateLength(message, false);
            encoding = ted.codeUnitSize;
            languageTable = ted.languageTable;
            languageShiftTable = ted.languageShiftTable;
            if (encoding == 1 && (languageTable != 0 || languageShiftTable != 0)) {
                if (header != null) {
                    SmsHeader smsHeader = SmsHeader.fromByteArray(header);
                    if (smsHeader.languageTable != languageTable || smsHeader.languageShiftTable != languageShiftTable) {
                        Rlog.w(LOG_TAG, "Updating language table in SMS header: " + smsHeader.languageTable + " -> " + languageTable + ", " + smsHeader.languageShiftTable + " -> " + languageShiftTable);
                        smsHeader.languageTable = languageTable;
                        smsHeader.languageShiftTable = languageShiftTable;
                        header = SmsHeader.toByteArray(smsHeader);
                    }
                } else {
                    SmsHeader smsHeader2 = new SmsHeader();
                    smsHeader2.languageTable = languageTable;
                    smsHeader2.languageShiftTable = languageShiftTable;
                    header = SmsHeader.toByteArray(smsHeader2);
                }
            }
        }
        SubmitPdu ret = new SubmitPdu();
        int validityPeriodFormat = 0;
        int relativeValidityPeriod = getRelativeValidityPeriod(validityPeriod);
        if (relativeValidityPeriod >= 0) {
            validityPeriodFormat = 2;
        }
        byte mtiByte = (byte) ((header != null ? 64 : 0) | (validityPeriodFormat << 3) | 1);
        ByteArrayOutputStream bo = getSubmitPduHead(scAddress, destinationAddress, mtiByte, statusReportRequested, ret);
        try {
            if (encoding == 1) {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, languageTable, languageShiftTable);
            } else {
                try {
                    userData = encodeUCS2(message, header);
                } catch (UnsupportedEncodingException uex) {
                    Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", uex);
                    return null;
                }
            }
        } catch (EncodeException e) {
            try {
                userData = encodeUCS2(message, header);
                encoding = 3;
            } catch (UnsupportedEncodingException uex2) {
                Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException ", uex2);
                return null;
            }
        }
        if (encoding == 1) {
            if ((userData[0] & 255) > 160) {
                Rlog.e(LOG_TAG, "Message too long (" + (userData[0] & 255) + " septets)");
                return null;
            }
            bo.write(0);
        } else {
            if ((userData[0] & 255) > 140) {
                Rlog.e(LOG_TAG, "Message too long (" + (userData[0] & 255) + " bytes)");
                return null;
            }
            bo.write(8);
        }
        if (validityPeriodFormat == 2) {
            bo.write(relativeValidityPeriod);
        }
        bo.write(userData, 0, userData.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    private static byte[] encodeUCS2(String message, byte[] header) throws UnsupportedEncodingException {
        byte[] userData;
        byte[] textPart = message.getBytes("utf-16be");
        if (header != null) {
            userData = new byte[header.length + textPart.length + 1];
            userData[0] = (byte) header.length;
            System.arraycopy(header, 0, userData, 1, header.length);
            System.arraycopy(textPart, 0, userData, header.length + 1, textPart.length);
        } else {
            userData = textPart;
        }
        byte[] ret = new byte[userData.length + 1];
        ret[0] = (byte) (userData.length & 255);
        System.arraycopy(userData, 0, ret, 1, userData.length);
        return ret;
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested) {
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, (byte[]) null);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, String message, boolean statusReportRequested, int validityPeriod) {
        return getSubmitPdu(scAddress, destinationAddress, message, statusReportRequested, null, 0, 0, 0, validityPeriod);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, int destinationPort, byte[] data, boolean statusReportRequested) {
        return getSubmitPdu(scAddress, destinationAddress, destinationPort, 0, data, statusReportRequested);
    }

    public static SubmitPdu getSubmitPdu(String scAddress, String destinationAddress, int destinationPort, int originatorPort, byte[] data, boolean statusReportRequested) {
        SmsHeader.PortAddrs portAddrs = new SmsHeader.PortAddrs();
        portAddrs.destPort = destinationPort;
        portAddrs.origPort = originatorPort;
        portAddrs.areEightBits = false;
        SmsHeader smsHeader = new SmsHeader();
        smsHeader.portAddrs = portAddrs;
        byte[] smsHeaderData = SmsHeader.toByteArray(smsHeader);
        if (data.length + smsHeaderData.length + 1 > 140) {
            Rlog.e(LOG_TAG, "SMS data message may only contain " + ((140 - smsHeaderData.length) - 1) + " bytes");
            return null;
        }
        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = getSubmitPduHead(scAddress, destinationAddress, (byte) 65, statusReportRequested, ret);
        bo.write(4);
        bo.write(data.length + smsHeaderData.length + 1);
        bo.write(smsHeaderData.length);
        bo.write(smsHeaderData, 0, smsHeaderData.length);
        bo.write(data, 0, data.length);
        ret.encodedMessage = bo.toByteArray();
        return ret;
    }

    private static ByteArrayOutputStream getSubmitPduHead(String scAddress, String destinationAddress, byte mtiByte, boolean statusReportRequested, SubmitPdu ret) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(180);
        if (scAddress == null) {
            ret.encodedScAddress = null;
        } else {
            ret.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        }
        if (statusReportRequested) {
            mtiByte = (byte) (mtiByte | 32);
        }
        bo.write(mtiByte);
        bo.write(0);
        byte[] daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(destinationAddress);
        bo.write(((daBytes.length - 1) * 2) - ((daBytes[daBytes.length + (-1)] & 240) == 240 ? 1 : 0));
        bo.write(daBytes, 0, daBytes.length);
        bo.write(0);
        return bo;
    }

    private static class PduParser {
        byte[] mPdu;
        byte[] mUserData;
        SmsHeader mUserDataHeader;
        int mCur = 0;
        int mUserDataSeptetPadding = 0;

        PduParser(byte[] pdu) {
            this.mPdu = pdu;
        }

        String getSCAddress() {
            String ret;
            int len = getByte();
            if (len == 0) {
                ret = null;
            } else {
                try {
                    ret = PhoneNumberUtils.calledPartyBCDToString(this.mPdu, this.mCur, len);
                } catch (RuntimeException tr) {
                    Rlog.d(SmsMessage.LOG_TAG, "invalid SC address: ", tr);
                    ret = null;
                }
            }
            this.mCur += len;
            return ret;
        }

        int getByte() {
            byte[] bArr = this.mPdu;
            int i = this.mCur;
            this.mCur = i + 1;
            return bArr[i] & 255;
        }

        GsmSmsAddress getAddress() {
            int addressLength = this.mPdu[this.mCur] & 255;
            int lengthBytes = ((addressLength + 1) / 2) + 2;
            try {
                GsmSmsAddress ret = new GsmSmsAddress(this.mPdu, this.mCur, lengthBytes);
                this.mCur += lengthBytes;
                return ret;
            } catch (ParseException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        long getSCTimestampMillis() {
            byte[] bArr = this.mPdu;
            int i = this.mCur;
            this.mCur = i + 1;
            int year = IccUtils.gsmBcdByteToInt(bArr[i]);
            byte[] bArr2 = this.mPdu;
            int i2 = this.mCur;
            this.mCur = i2 + 1;
            int month = IccUtils.gsmBcdByteToInt(bArr2[i2]);
            byte[] bArr3 = this.mPdu;
            int i3 = this.mCur;
            this.mCur = i3 + 1;
            int day = IccUtils.gsmBcdByteToInt(bArr3[i3]);
            byte[] bArr4 = this.mPdu;
            int i4 = this.mCur;
            this.mCur = i4 + 1;
            int hour = IccUtils.gsmBcdByteToInt(bArr4[i4]);
            byte[] bArr5 = this.mPdu;
            int i5 = this.mCur;
            this.mCur = i5 + 1;
            int minute = IccUtils.gsmBcdByteToInt(bArr5[i5]);
            byte[] bArr6 = this.mPdu;
            int i6 = this.mCur;
            this.mCur = i6 + 1;
            int second = IccUtils.gsmBcdByteToInt(bArr6[i6]);
            byte[] bArr7 = this.mPdu;
            int i7 = this.mCur;
            this.mCur = i7 + 1;
            byte tzByte = bArr7[i7];
            int timezoneOffset = IccUtils.gsmBcdByteToInt((byte) (tzByte & (-9)));
            if ((tzByte & 8) != 0) {
                timezoneOffset = -timezoneOffset;
            }
            Time time = new Time("UTC");
            time.year = year >= 90 ? year + 1900 : year + 2000;
            time.month = month - 1;
            time.monthDay = day;
            time.hour = hour;
            time.minute = minute;
            time.second = second;
            return time.toMillis(true) - ((long) (((timezoneOffset * 15) * 60) * com.android.internal.telephony.cdma.CallFailCause.CDMA_LOCKED_UNTIL_POWER_CYCLE));
        }

        int constructUserData(boolean hasUserDataHeader, boolean dataInSeptets) {
            int offset;
            int bufferLen;
            int offset2 = this.mCur;
            int offset3 = offset2 + 1;
            int userDataLength = this.mPdu[offset2] & 255;
            int headerSeptets = 0;
            int userDataHeaderLength = 0;
            if (hasUserDataHeader) {
                int offset4 = offset3 + 1;
                userDataHeaderLength = this.mPdu[offset3] & 255;
                byte[] udh = new byte[userDataHeaderLength];
                System.arraycopy(this.mPdu, offset4, udh, 0, userDataHeaderLength);
                this.mUserDataHeader = SmsHeader.fromByteArray(udh);
                offset = offset4 + userDataHeaderLength;
                int headerBits = (userDataHeaderLength + 1) * 8;
                int headerSeptets2 = headerBits / 7;
                headerSeptets = headerSeptets2 + (headerBits % 7 > 0 ? 1 : 0);
                this.mUserDataSeptetPadding = (headerSeptets * 7) - headerBits;
            } else {
                offset = offset3;
            }
            if (dataInSeptets) {
                bufferLen = this.mPdu.length - offset;
            } else {
                bufferLen = userDataLength - (hasUserDataHeader ? userDataHeaderLength + 1 : 0);
                if (bufferLen < 0) {
                    bufferLen = 0;
                }
            }
            this.mUserData = new byte[bufferLen];
            System.arraycopy(this.mPdu, offset, this.mUserData, 0, this.mUserData.length);
            this.mCur = offset;
            if (dataInSeptets) {
                int count = userDataLength - headerSeptets;
                if (count < 0) {
                    return 0;
                }
                return count;
            }
            return this.mUserData.length;
        }

        byte[] getUserData() {
            return this.mUserData;
        }

        SmsHeader getUserDataHeader() {
            return this.mUserDataHeader;
        }

        String getUserDataGSM7Bit(int septetCount, int languageTable, int languageShiftTable) {
            String ret = GsmAlphabet.gsm7BitPackedToString(this.mPdu, this.mCur, septetCount, this.mUserDataSeptetPadding, languageTable, languageShiftTable);
            this.mCur += (septetCount * 7) / 8;
            return ret;
        }

        String getUserDataUCS2(int byteCount) {
            String ret;
            try {
                ret = new String(this.mPdu, this.mCur, byteCount, "utf-16");
            } catch (UnsupportedEncodingException ex) {
                ret = "";
                Rlog.e(SmsMessage.LOG_TAG, "implausible UnsupportedEncodingException", ex);
            }
            this.mCur += byteCount;
            return ret;
        }

        String getUserDataKSC5601(int byteCount) {
            String ret;
            try {
                ret = new String(this.mPdu, this.mCur, byteCount, "KSC5601");
            } catch (UnsupportedEncodingException ex) {
                ret = "";
                Rlog.e(SmsMessage.LOG_TAG, "implausible UnsupportedEncodingException", ex);
            }
            this.mCur += byteCount;
            return ret;
        }

        boolean moreDataPresent() {
            return this.mPdu.length > this.mCur;
        }
    }

    public static GsmAlphabet.TextEncodingDetails calculateLength(CharSequence msgBody, boolean use7bitOnly) {
        GsmAlphabet.TextEncodingDetails ted = GsmAlphabet.countGsmSeptets(msgBody, use7bitOnly);
        if (ted == null) {
            ted = new GsmAlphabet.TextEncodingDetails();
            int octets = msgBody.length() * 2;
            ted.codeUnitCount = msgBody.length();
            if (octets > 140) {
                ted.msgCount = (octets + UserData.IS91_MSG_TYPE_SHORT_MESSAGE) / 134;
                ted.codeUnitsRemaining = ((ted.msgCount * 134) - octets) / 2;
            } else {
                ted.msgCount = 1;
                ted.codeUnitsRemaining = (140 - octets) / 2;
            }
            ted.codeUnitSize = 3;
        }
        return ted;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public int getProtocolIdentifier() {
        return this.mProtocolIdentifier;
    }

    int getDataCodingScheme() {
        return this.mDataCodingScheme;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isReplace() {
        return (this.mProtocolIdentifier & 192) == 64 && (this.mProtocolIdentifier & 63) > 0 && (this.mProtocolIdentifier & 63) < 8;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isCphsMwiMessage() {
        return ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageClear() || ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet();
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isMWIClearMessage() {
        if (!this.mIsMwi || this.mMwiSense) {
            return this.mOriginatingAddress != null && ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageClear();
        }
        return true;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isMWISetMessage() {
        if (this.mIsMwi && this.mMwiSense) {
            return true;
        }
        return this.mOriginatingAddress != null && ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet();
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isMwiDontStore() {
        if (this.mIsMwi && this.mMwiDontStore) {
            return true;
        }
        return isCphsMwiMessage() && " ".equals(getMessageBody());
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public int getStatus() {
        return this.mStatus;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isStatusReportMessage() {
        return this.mIsStatusReportMessage;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isReplyPathPresent() {
        return this.mReplyPathPresent;
    }

    private void parsePdu(byte[] pdu) {
        this.mPdu = pdu;
        PduParser p = new PduParser(pdu);
        this.mScAddress = p.getSCAddress();
        if (this.mScAddress != null) {
        }
        int firstByte = p.getByte();
        this.mMti = firstByte & 3;
        switch (this.mMti) {
            case 0:
            case 3:
                parseSmsDeliver(p, firstByte);
                return;
            case 1:
                parseSmsSubmit(p, firstByte);
                return;
            case 2:
                parseSmsStatusReport(p, firstByte);
                return;
            default:
                throw new RuntimeException("Unsupported message type");
        }
    }

    private void parseSmsStatusReport(PduParser p, int firstByte) {
        this.mIsStatusReportMessage = true;
        this.mMessageRef = p.getByte();
        this.mRecipientAddress = p.getAddress();
        this.mScTimeMillis = p.getSCTimestampMillis();
        p.getSCTimestampMillis();
        this.mStatus = p.getByte();
        if (p.moreDataPresent()) {
            int extraParams = p.getByte();
            int moreExtraParams = extraParams;
            while ((moreExtraParams & 128) != 0) {
                moreExtraParams = p.getByte();
            }
            if ((extraParams & 120) == 0) {
                if ((extraParams & 1) != 0) {
                    this.mProtocolIdentifier = p.getByte();
                }
                if ((extraParams & 2) != 0) {
                    this.mDataCodingScheme = p.getByte();
                }
                if ((extraParams & 4) != 0) {
                    boolean hasUserDataHeader = (firstByte & 64) == 64;
                    parseUserData(p, hasUserDataHeader);
                }
            }
        }
    }

    private void parseSmsDeliver(PduParser p, int firstByte) {
        this.mReplyPathPresent = (firstByte & 128) == 128;
        this.mOriginatingAddress = p.getAddress();
        if (this.mOriginatingAddress != null) {
        }
        this.mProtocolIdentifier = p.getByte();
        this.mDataCodingScheme = p.getByte();
        this.mScTimeMillis = p.getSCTimestampMillis();
        boolean hasUserDataHeader = (firstByte & 64) == 64;
        parseUserData(p, hasUserDataHeader);
    }

    private void parseSmsSubmit(PduParser p, int firstByte) {
        int validityPeriodLength;
        this.mReplyPathPresent = (firstByte & 128) == 128;
        this.mMessageRef = p.getByte();
        this.mRecipientAddress = p.getAddress();
        if (this.mRecipientAddress != null) {
        }
        this.mProtocolIdentifier = p.getByte();
        this.mDataCodingScheme = p.getByte();
        int validityPeriodFormat = (firstByte >> 3) & 3;
        if (validityPeriodFormat == 0) {
            validityPeriodLength = 0;
        } else if (2 == validityPeriodFormat) {
            validityPeriodLength = 1;
        } else {
            validityPeriodLength = 7;
        }
        while (true) {
            int validityPeriodLength2 = validityPeriodLength;
            validityPeriodLength = validityPeriodLength2 - 1;
            if (validityPeriodLength2 <= 0) {
                break;
            } else {
                p.getByte();
            }
        }
        boolean hasUserDataHeader = (firstByte & 64) == 64;
        parseUserData(p, hasUserDataHeader);
    }

    private void parseUserData(PduParser p, boolean hasUserDataHeader) {
        boolean hasMessageClass = false;
        int encodingType = 0;
        if ((this.mDataCodingScheme & 128) == 0) {
            boolean userDataCompressed = (this.mDataCodingScheme & 32) != 0;
            hasMessageClass = (this.mDataCodingScheme & 16) != 0;
            if (userDataCompressed) {
                Rlog.w(LOG_TAG, "4 - Unsupported SMS data coding scheme (compression) " + (this.mDataCodingScheme & 255));
            } else {
                switch ((this.mDataCodingScheme >> 2) & 3) {
                    case 0:
                        encodingType = 1;
                        break;
                    case 1:
                    case 3:
                        Rlog.w(LOG_TAG, "1 - Unsupported SMS data coding scheme " + (this.mDataCodingScheme & 255));
                        encodingType = 2;
                        break;
                    case 2:
                        encodingType = 3;
                        break;
                }
            }
        } else if ((this.mDataCodingScheme & 240) == 240) {
            hasMessageClass = true;
            encodingType = (this.mDataCodingScheme & 4) == 0 ? 1 : 2;
        } else if ((this.mDataCodingScheme & 240) == 192 || (this.mDataCodingScheme & 240) == 208 || (this.mDataCodingScheme & 240) == 224) {
            if ((this.mDataCodingScheme & 240) == 224) {
                encodingType = 3;
            } else {
                encodingType = 1;
            }
            boolean active = (this.mDataCodingScheme & 8) == 8;
            if ((this.mDataCodingScheme & 3) == 0) {
                this.mIsMwi = true;
                this.mMwiSense = active;
                this.mMwiDontStore = (this.mDataCodingScheme & 240) == 192;
                if (active) {
                    this.mVoiceMailCount = -1;
                } else {
                    this.mVoiceMailCount = 0;
                }
                Rlog.w(LOG_TAG, "MWI in DCS for Vmail. DCS = " + (this.mDataCodingScheme & 255) + " Dont store = " + this.mMwiDontStore + " vmail count = " + this.mVoiceMailCount);
            } else {
                this.mIsMwi = false;
                Rlog.w(LOG_TAG, "MWI in DCS for fax/email/other: " + (this.mDataCodingScheme & 255));
            }
        } else if ((this.mDataCodingScheme & 192) == 128) {
            if (this.mDataCodingScheme == 132) {
                encodingType = 4;
            } else {
                Rlog.w(LOG_TAG, "5 - Unsupported SMS data coding scheme " + (this.mDataCodingScheme & 255));
            }
        } else {
            Rlog.w(LOG_TAG, "3 - Unsupported SMS data coding scheme " + (this.mDataCodingScheme & 255));
        }
        int count = p.constructUserData(hasUserDataHeader, encodingType == 1);
        this.mUserData = p.getUserData();
        this.mUserDataHeader = p.getUserDataHeader();
        if (hasUserDataHeader && this.mUserDataHeader.specialSmsMsgList.size() != 0) {
            for (SmsHeader.SpecialSmsMsg msg : this.mUserDataHeader.specialSmsMsgList) {
                int msgInd = msg.msgIndType & 255;
                if (msgInd == 0 || msgInd == 128) {
                    this.mIsMwi = true;
                    if (msgInd == 128) {
                        this.mMwiDontStore = false;
                    } else if (!this.mMwiDontStore && (((this.mDataCodingScheme & 240) != 208 && (this.mDataCodingScheme & 240) != 224) || (this.mDataCodingScheme & 3) != 0)) {
                        this.mMwiDontStore = true;
                    }
                    this.mVoiceMailCount = msg.msgCount & 255;
                    if (this.mVoiceMailCount > 0) {
                        this.mMwiSense = true;
                    } else {
                        this.mMwiSense = false;
                    }
                    Rlog.w(LOG_TAG, "MWI in TP-UDH for Vmail. Msg Ind = " + msgInd + " Dont store = " + this.mMwiDontStore + " Vmail count = " + this.mVoiceMailCount);
                } else {
                    Rlog.w(LOG_TAG, "TP_UDH fax/email/extended msg/multisubscriber profile. Msg Ind = " + msgInd);
                }
            }
        }
        switch (encodingType) {
            case 0:
            case 2:
                this.mMessageBody = null;
                break;
            case 1:
                this.mMessageBody = p.getUserDataGSM7Bit(count, hasUserDataHeader ? this.mUserDataHeader.languageTable : 0, hasUserDataHeader ? this.mUserDataHeader.languageShiftTable : 0);
                break;
            case 3:
                this.mMessageBody = p.getUserDataUCS2(count);
                break;
            case 4:
                this.mMessageBody = p.getUserDataKSC5601(count);
                break;
        }
        if (this.mMessageBody != null) {
            parseMessageBody();
        }
        if (!hasMessageClass) {
            this.messageClass = SmsConstants.MessageClass.UNKNOWN;
        }
        switch (this.mDataCodingScheme & 3) {
            case 0:
                this.messageClass = SmsConstants.MessageClass.CLASS_0;
                break;
            case 1:
                this.messageClass = SmsConstants.MessageClass.CLASS_1;
                break;
            case 2:
                this.messageClass = SmsConstants.MessageClass.CLASS_2;
                break;
            case 3:
                this.messageClass = SmsConstants.MessageClass.CLASS_3;
                break;
        }
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public SmsConstants.MessageClass getMessageClass() {
        return this.messageClass;
    }

    boolean isUsimDataDownload() {
        return this.messageClass == SmsConstants.MessageClass.CLASS_2 && (this.mProtocolIdentifier == 127 || this.mProtocolIdentifier == 124);
    }

    public int getNumOfVoicemails() {
        if (!this.mIsMwi && isCphsMwiMessage()) {
            if (this.mOriginatingAddress != null && ((GsmSmsAddress) this.mOriginatingAddress).isCphsVoiceMessageSet()) {
                this.mVoiceMailCount = 255;
            } else {
                this.mVoiceMailCount = 0;
            }
            Rlog.v(LOG_TAG, "CPHS voice mail message");
        }
        return this.mVoiceMailCount;
    }
}
