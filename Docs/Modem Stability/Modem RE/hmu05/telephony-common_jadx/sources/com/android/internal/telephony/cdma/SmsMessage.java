package com.android.internal.telephony.cdma;

import android.R;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.util.Log;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.CdmaSmsSubaddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.util.BitwiseInputStream;
import com.android.internal.util.HexDump;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class SmsMessage extends SmsMessageBase {
    private static final byte BEARER_DATA = 8;
    private static final byte BEARER_REPLY_OPTION = 6;
    private static final byte CAUSE_CODES = 7;
    private static final byte DESTINATION_ADDRESS = 4;
    private static final byte DESTINATION_SUB_ADDRESS = 5;
    private static final String LOGGABLE_TAG = "CDMA:SMS";
    static final String LOG_TAG = "SmsMessage";
    private static final byte ORIGINATING_ADDRESS = 2;
    private static final byte ORIGINATING_SUB_ADDRESS = 3;
    private static final int RETURN_ACK = 1;
    private static final int RETURN_NO_ACK = 0;
    private static final byte SERVICE_CATEGORY = 1;
    private static final byte TELESERVICE_IDENTIFIER = 0;
    private static final boolean VDBG = false;
    private BearerData mBearerData;
    private SmsEnvelope mEnvelope;
    private int status;

    public static class SubmitPdu extends SmsMessageBase.SubmitPduBase {
    }

    public static SmsMessage createFromPdu(byte[] pdu) {
        SmsMessage msg = new SmsMessage();
        try {
            msg.parsePdu(pdu);
            return msg;
        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "SMS PDU parsing failed with out of memory: ", e);
            return null;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static SmsMessage newFromParcel(Parcel p) {
        SmsMessage msg = new SmsMessage();
        SmsEnvelope env = new SmsEnvelope();
        CdmaSmsAddress addr = new CdmaSmsAddress();
        CdmaSmsSubaddress subaddr = new CdmaSmsSubaddress();
        env.teleService = p.readInt();
        if (p.readByte() != 0) {
            env.messageType = 1;
        } else if (env.teleService == 0) {
            env.messageType = 2;
        } else {
            env.messageType = 0;
        }
        env.serviceCategory = p.readInt();
        int addressDigitMode = p.readInt();
        addr.digitMode = (byte) (addressDigitMode & 255);
        addr.numberMode = (byte) (p.readInt() & 255);
        addr.ton = p.readInt();
        addr.numberPlan = (byte) (p.readInt() & 255);
        int i = p.readByte();
        addr.numberOfDigits = i;
        byte[] data = new byte[i];
        for (int index = 0; index < i; index++) {
            data[index] = p.readByte();
            if (addressDigitMode == 0) {
                data[index] = msg.convertDtmfToAscii(data[index]);
            }
        }
        addr.origBytes = data;
        subaddr.type = p.readInt();
        subaddr.odd = p.readByte();
        int i2 = p.readByte();
        if (i2 < 0) {
            i2 = 0;
        }
        byte[] data2 = new byte[i2];
        for (int index2 = 0; index2 < i2; index2++) {
            data2[index2] = p.readByte();
        }
        subaddr.origBytes = data2;
        int countInt = p.readInt();
        if (countInt < 0) {
            countInt = 0;
        }
        byte[] data3 = new byte[countInt];
        for (int index3 = 0; index3 < countInt; index3++) {
            data3[index3] = p.readByte();
        }
        env.bearerData = data3;
        env.origAddress = addr;
        env.origSubaddress = subaddr;
        msg.mOriginatingAddress = addr;
        msg.mEnvelope = env;
        msg.createPdu();
        return msg;
    }

    public static SmsMessage createFromEfRecord(int index, byte[] data) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.mIndexOnIcc = index;
            if ((data[0] & SERVICE_CATEGORY) == 0) {
                Rlog.w(LOG_TAG, "SMS parsing failed: Trying to parse a free record");
                msg = null;
            } else {
                msg.mStatusOnIcc = data[0] & CAUSE_CODES;
                int size = data[1] & 255;
                byte[] pdu = new byte[size];
                System.arraycopy(data, 2, pdu, 0, size);
                msg.parsePduFromEfRecord(pdu);
            }
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static int getTPLayerLengthForPDU(String pdu) {
        Rlog.w(LOG_TAG, "getTPLayerLengthForPDU: is not supported in CDMA mode.");
        return 0;
    }

    public static SubmitPdu getSubmitPdu(String scAddr, String destAddr, String message, boolean statusReportRequested, SmsHeader smsHeader) {
        return getSubmitPdu(scAddr, destAddr, message, statusReportRequested, smsHeader, -1);
    }

    public static SubmitPdu getSubmitPdu(String scAddr, String destAddr, String message, boolean statusReportRequested, SmsHeader smsHeader, int priority) {
        if (message == null || destAddr == null) {
            return null;
        }
        UserData uData = new UserData();
        uData.payloadStr = message;
        uData.userDataHeader = smsHeader;
        return privateGetSubmitPdu(destAddr, statusReportRequested, uData, priority);
    }

    public static SubmitPdu getSubmitPdu(String scAddr, String destAddr, int destPort, byte[] data, boolean statusReportRequested) {
        return getSubmitPdu(scAddr, destAddr, destPort, 0, data, statusReportRequested);
    }

    public static SubmitPdu getSubmitPdu(String scAddr, String destAddr, int destPort, int origPort, byte[] data, boolean statusReportRequested) {
        SmsHeader.PortAddrs portAddrs = new SmsHeader.PortAddrs();
        portAddrs.destPort = destPort;
        portAddrs.origPort = origPort;
        portAddrs.areEightBits = false;
        SmsHeader smsHeader = new SmsHeader();
        smsHeader.portAddrs = portAddrs;
        UserData uData = new UserData();
        uData.userDataHeader = smsHeader;
        uData.msgEncoding = 0;
        uData.msgEncodingSet = true;
        uData.payload = data;
        return privateGetSubmitPdu(destAddr, statusReportRequested, uData);
    }

    public static SubmitPdu getSubmitPdu(String destAddr, UserData userData, boolean statusReportRequested) {
        return privateGetSubmitPdu(destAddr, statusReportRequested, userData);
    }

    public static SubmitPdu getSubmitPdu(String destAddr, UserData userData, boolean statusReportRequested, int priority) {
        return privateGetSubmitPdu(destAddr, statusReportRequested, userData, priority);
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public int getProtocolIdentifier() {
        Rlog.w(LOG_TAG, "getProtocolIdentifier: is not supported in CDMA mode.");
        return 0;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isReplace() {
        Rlog.w(LOG_TAG, "isReplace: is not supported in CDMA mode.");
        return false;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isCphsMwiMessage() {
        Rlog.w(LOG_TAG, "isCphsMwiMessage: is not supported in CDMA mode.");
        return false;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isMWIClearMessage() {
        return this.mBearerData != null && this.mBearerData.numberOfMessages == 0;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isMWISetMessage() {
        return this.mBearerData != null && this.mBearerData.numberOfMessages > 0;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isMwiDontStore() {
        return this.mBearerData != null && this.mBearerData.numberOfMessages > 0 && this.mBearerData.userData == null;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public int getStatus() {
        return this.status << 16;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isStatusReportMessage() {
        return this.mBearerData.messageType == 4;
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public boolean isReplyPathPresent() {
        Rlog.w(LOG_TAG, "isReplyPathPresent: is not supported in CDMA mode.");
        return false;
    }

    public static GsmAlphabet.TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return BearerData.calcTextEncodingDetails(messageBody, use7bitOnly);
    }

    int getTeleService() {
        return this.mEnvelope.teleService;
    }

    int getMessageType() {
        return this.mEnvelope.serviceCategory != 0 ? 1 : 0;
    }

    private void parsePdu(byte[] pdu) {
        ByteArrayInputStream bais = new ByteArrayInputStream(pdu);
        DataInputStream dis = new DataInputStream(bais);
        SmsEnvelope env = new SmsEnvelope();
        CdmaSmsAddress addr = new CdmaSmsAddress();
        try {
            env.messageType = dis.readInt();
            env.teleService = dis.readInt();
            env.serviceCategory = dis.readInt();
            addr.digitMode = dis.readByte();
            addr.numberMode = dis.readByte();
            addr.ton = dis.readByte();
            addr.numberPlan = dis.readByte();
            int length = dis.readUnsignedByte();
            addr.numberOfDigits = length;
            if (length > pdu.length) {
                throw new RuntimeException("createFromPdu: Invalid pdu, addr.numberOfDigits " + length + " > pdu len " + pdu.length);
            }
            addr.origBytes = new byte[length];
            dis.read(addr.origBytes, 0, length);
            env.bearerReply = dis.readInt();
            env.replySeqNo = dis.readByte();
            env.errorClass = dis.readByte();
            env.causeCode = dis.readByte();
            int bearerDataLength = dis.readInt();
            if (bearerDataLength > pdu.length) {
                throw new RuntimeException("createFromPdu: Invalid pdu, bearerDataLength " + bearerDataLength + " > pdu len " + pdu.length);
            }
            env.bearerData = new byte[bearerDataLength];
            dis.read(env.bearerData, 0, bearerDataLength);
            dis.close();
            this.mOriginatingAddress = addr;
            env.origAddress = addr;
            this.mEnvelope = env;
            this.mPdu = pdu;
            parseSms();
        } catch (IOException ex) {
            throw new RuntimeException("createFromPdu: conversion from byte array to object failed: " + ex, ex);
        } catch (Exception ex2) {
            Rlog.e(LOG_TAG, "createFromPdu: conversion from byte array to object failed: " + ex2);
        }
    }

    private void parsePduFromEfRecord(byte[] pdu) {
        ByteArrayInputStream bais = new ByteArrayInputStream(pdu);
        DataInputStream dis = new DataInputStream(bais);
        SmsEnvelope env = new SmsEnvelope();
        CdmaSmsAddress addr = new CdmaSmsAddress();
        CdmaSmsSubaddress subAddr = new CdmaSmsSubaddress();
        try {
            env.messageType = dis.readByte();
            while (dis.available() > 0) {
                int parameterId = dis.readByte();
                int parameterLen = dis.readUnsignedByte();
                byte[] parameterData = new byte[parameterLen];
                switch (parameterId) {
                    case 0:
                        env.teleService = dis.readUnsignedShort();
                        Rlog.i(LOG_TAG, "teleservice = " + env.teleService);
                        break;
                    case 1:
                        env.serviceCategory = dis.readUnsignedShort();
                        break;
                    case 2:
                    case 4:
                        dis.read(parameterData, 0, parameterLen);
                        BitwiseInputStream addrBis = new BitwiseInputStream(parameterData);
                        addr.digitMode = addrBis.read(1);
                        addr.numberMode = addrBis.read(1);
                        int numberType = 0;
                        if (addr.digitMode == 1) {
                            numberType = addrBis.read(3);
                            addr.ton = numberType;
                            if (addr.numberMode == 0) {
                                addr.numberPlan = addrBis.read(4);
                            }
                        }
                        addr.numberOfDigits = addrBis.read(8);
                        byte[] data = new byte[addr.numberOfDigits];
                        if (addr.digitMode == 0) {
                            for (int index = 0; index < addr.numberOfDigits; index++) {
                                byte b = (byte) (addrBis.read(4) & 15);
                                data[index] = convertDtmfToAscii(b);
                            }
                        } else if (addr.digitMode == 1) {
                            if (addr.numberMode == 0) {
                                for (int index2 = 0; index2 < addr.numberOfDigits; index2++) {
                                    byte b2 = (byte) (addrBis.read(8) & 255);
                                    data[index2] = b2;
                                }
                            } else if (addr.numberMode == 1) {
                                if (numberType == 2) {
                                    Rlog.e(LOG_TAG, "TODO: Originating Addr is email id");
                                } else {
                                    Rlog.e(LOG_TAG, "TODO: Originating Addr is data network address");
                                }
                            } else {
                                Rlog.e(LOG_TAG, "Originating Addr is of incorrect type");
                            }
                        } else {
                            Rlog.e(LOG_TAG, "Incorrect Digit mode");
                        }
                        addr.origBytes = data;
                        Rlog.i(LOG_TAG, "Originating Addr=" + addr.toString());
                        if (parameterId == 4) {
                            env.destAddress = addr;
                            this.mRecipientAddress = addr;
                        }
                        break;
                    case 3:
                    case 5:
                        dis.read(parameterData, 0, parameterLen);
                        BitwiseInputStream subAddrBis = new BitwiseInputStream(parameterData);
                        subAddr.type = subAddrBis.read(3);
                        subAddr.odd = subAddrBis.readByteArray(1)[0];
                        int subAddrLen = subAddrBis.read(8);
                        byte[] subdata = new byte[subAddrLen];
                        for (int index3 = 0; index3 < subAddrLen; index3++) {
                            byte b3 = (byte) (subAddrBis.read(4) & 255);
                            subdata[index3] = convertDtmfToAscii(b3);
                        }
                        subAddr.origBytes = subdata;
                        break;
                    case 6:
                        dis.read(parameterData, 0, parameterLen);
                        BitwiseInputStream replyOptBis = new BitwiseInputStream(parameterData);
                        env.bearerReply = replyOptBis.read(6);
                        break;
                    case 7:
                        dis.read(parameterData, 0, parameterLen);
                        BitwiseInputStream ccBis = new BitwiseInputStream(parameterData);
                        env.replySeqNo = ccBis.readByteArray(6)[0];
                        env.errorClass = ccBis.readByteArray(2)[0];
                        if (env.errorClass != 0) {
                            env.causeCode = ccBis.readByteArray(8)[0];
                        }
                        break;
                    case 8:
                        dis.read(parameterData, 0, parameterLen);
                        env.bearerData = parameterData;
                        break;
                    default:
                        throw new Exception("unsupported parameterId (" + parameterId + ")");
                }
            }
            bais.close();
            dis.close();
        } catch (Exception ex) {
            Rlog.e(LOG_TAG, "parsePduFromEfRecord: conversion from pdu to SmsMessage failed" + ex);
        }
        this.mOriginatingAddress = addr;
        env.origAddress = addr;
        env.origSubaddress = subAddr;
        this.mEnvelope = env;
        this.mPdu = pdu;
        parseSms();
    }

    protected void parseSms() {
        if (this.mEnvelope.teleService == 262144) {
            this.mBearerData = new BearerData();
            if (this.mEnvelope.bearerData != null) {
                this.mBearerData.numberOfMessages = this.mEnvelope.bearerData[0] & 255;
                return;
            }
            return;
        }
        this.mBearerData = BearerData.decode(this.mEnvelope.bearerData);
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MT raw BearerData = '" + HexDump.toHexString(this.mEnvelope.bearerData) + "'");
            Rlog.d(LOG_TAG, "MT (decoded) BearerData = " + this.mBearerData);
        }
        this.mMessageRef = this.mBearerData.messageId;
        if (this.mBearerData.userData != null) {
            this.mUserData = this.mBearerData.userData.payload;
            this.mUserDataHeader = this.mBearerData.userData.userDataHeader;
            this.mMessageBody = this.mBearerData.userData.payloadStr;
        }
        if (this.mOriginatingAddress != null) {
            this.mOriginatingAddress.address = new String(this.mOriginatingAddress.origBytes);
            if (this.mOriginatingAddress.ton == 1 && this.mOriginatingAddress.address.charAt(0) != '+') {
                this.mOriginatingAddress.address = "+" + this.mOriginatingAddress.address;
            }
        }
        if (this.mBearerData.msgCenterTimeStamp != null) {
            this.mScTimeMillis = this.mBearerData.msgCenterTimeStamp.toMillis(true);
        }
        if (this.mBearerData.messageType == 4) {
            if (!this.mBearerData.messageStatusSet) {
                Rlog.d(LOG_TAG, "DELIVERY_ACK message without msgStatus (" + (this.mUserData == null ? "also missing" : "does have") + " userData).");
                this.status = 0;
            } else {
                this.status = this.mBearerData.errorClass << 8;
                this.status |= this.mBearerData.messageStatus;
            }
        } else if (this.mBearerData.messageType != 1 && this.mBearerData.messageType != 2) {
            throw new RuntimeException("Unsupported message type: " + this.mBearerData.messageType);
        }
        if (this.mMessageBody != null) {
            parseMessageBody();
        } else {
            if (this.mUserData != null) {
            }
        }
    }

    public SmsCbMessage parseBroadcastSms() {
        BearerData bData = BearerData.decode(this.mEnvelope.bearerData, this.mEnvelope.serviceCategory);
        if (bData == null) {
            Rlog.w(LOG_TAG, "BearerData.decode() returned null");
            return null;
        }
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MT raw BearerData = " + HexDump.toHexString(this.mEnvelope.bearerData));
        }
        String plmn = SystemProperties.get("gsm.operator.numeric");
        SmsCbLocation location = new SmsCbLocation(plmn);
        return new SmsCbMessage(2, 1, bData.messageId, location, this.mEnvelope.serviceCategory, bData.getLanguage(), bData.userData.payloadStr, bData.priority, null, bData.cmasWarningInfo);
    }

    @Override // com.android.internal.telephony.SmsMessageBase
    public SmsConstants.MessageClass getMessageClass() {
        return this.mBearerData.displayMode == 0 ? SmsConstants.MessageClass.CLASS_0 : SmsConstants.MessageClass.UNKNOWN;
    }

    static synchronized int getNextMessageId() {
        int msgId;
        msgId = SystemProperties.getInt("persist.radio.cdma.msgid", 1);
        String nextMsgId = Integer.toString((msgId % 65535) + 1);
        SystemProperties.set("persist.radio.cdma.msgid", nextMsgId);
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "next persist.radio.cdma.msgid = " + nextMsgId);
            Rlog.d(LOG_TAG, "readback gets " + SystemProperties.get("persist.radio.cdma.msgid"));
        }
        return msgId;
    }

    private static SubmitPdu privateGetSubmitPdu(String destAddrStr, boolean statusReportRequested, UserData userData) {
        return privateGetSubmitPdu(destAddrStr, statusReportRequested, userData, -1);
    }

    private static SubmitPdu privateGetSubmitPdu(String destAddrStr, boolean statusReportRequested, UserData userData, int priority) {
        boolean ascii7bitForLongMsg;
        CdmaSmsAddress destAddr = CdmaSmsAddress.parse(PhoneNumberUtils.cdmaCheckAndProcessPlusCodeForSms(destAddrStr));
        if (destAddr == null) {
            return null;
        }
        BearerData bearerData = new BearerData();
        bearerData.messageType = 2;
        bearerData.messageId = getNextMessageId();
        bearerData.deliveryAckReq = statusReportRequested;
        bearerData.userAckReq = false;
        bearerData.readAckReq = false;
        bearerData.reportReq = false;
        if (priority != -1) {
            bearerData.priorityIndicatorSet = true;
            bearerData.priority = priority;
        }
        bearerData.userData = userData;
        byte[] encodedBearerData = BearerData.encode(bearerData);
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MO (encoded) BearerData = " + bearerData);
            Rlog.d(LOG_TAG, "MO raw BearerData = '" + HexDump.toHexString(encodedBearerData) + "'");
        }
        if (encodedBearerData == null) {
            return null;
        }
        int teleservice = bearerData.hasUserDataHeader ? SmsEnvelope.TELESERVICE_WEMT : 4098;
        Resources resource = Resources.getSystem();
        if (resource != null && (ascii7bitForLongMsg = resource.getBoolean(R.bool.config_biometricFrrNotificationEnabled))) {
            Rlog.d(LOG_TAG, "ascii7bitForLongMsg = " + ascii7bitForLongMsg);
            teleservice = 4098;
        }
        SmsEnvelope envelope = new SmsEnvelope();
        envelope.messageType = 0;
        envelope.teleService = teleservice;
        envelope.destAddress = destAddr;
        envelope.bearerReply = 1;
        envelope.bearerData = encodedBearerData;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(envelope.teleService);
            dos.writeInt(0);
            dos.writeInt(0);
            dos.write(destAddr.digitMode);
            dos.write(destAddr.numberMode);
            dos.write(destAddr.ton);
            dos.write(destAddr.numberPlan);
            dos.write(destAddr.numberOfDigits);
            dos.write(destAddr.origBytes, 0, destAddr.origBytes.length);
            dos.write(0);
            dos.write(0);
            dos.write(0);
            dos.write(encodedBearerData.length);
            dos.write(encodedBearerData, 0, encodedBearerData.length);
            dos.close();
            SubmitPdu pdu = new SubmitPdu();
            pdu.encodedMessage = baos.toByteArray();
            pdu.encodedScAddress = null;
            return pdu;
        } catch (IOException ex) {
            Rlog.e(LOG_TAG, "creating SubmitPdu failed: " + ex);
            return null;
        }
    }

    private void createPdu() {
        SmsEnvelope env = this.mEnvelope;
        CdmaSmsAddress addr = env.origAddress;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
        DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(baos));
        try {
            dos.writeInt(env.messageType);
            dos.writeInt(env.teleService);
            dos.writeInt(env.serviceCategory);
            dos.writeByte(addr.digitMode);
            dos.writeByte(addr.numberMode);
            dos.writeByte(addr.ton);
            dos.writeByte(addr.numberPlan);
            dos.writeByte(addr.numberOfDigits);
            dos.write(addr.origBytes, 0, addr.origBytes.length);
            dos.writeInt(env.bearerReply);
            dos.writeByte(env.replySeqNo);
            dos.writeByte(env.errorClass);
            dos.writeByte(env.causeCode);
            dos.writeInt(env.bearerData.length);
            dos.write(env.bearerData, 0, env.bearerData.length);
            dos.close();
            this.mPdu = baos.toByteArray();
        } catch (IOException ex) {
            Rlog.e(LOG_TAG, "createPdu: conversion from object to byte array failed: " + ex);
        }
    }

    private byte convertDtmfToAscii(byte dtmfDigit) {
        switch (dtmfDigit) {
            case 0:
                return (byte) 68;
            case 1:
                return (byte) 49;
            case 2:
                return (byte) 50;
            case 3:
                return (byte) 51;
            case 4:
                return (byte) 52;
            case 5:
                return (byte) 53;
            case 6:
                return (byte) 54;
            case 7:
                return (byte) 55;
            case 8:
                return (byte) 56;
            case 9:
                return (byte) 57;
            case 10:
                return (byte) 48;
            case 11:
                return (byte) 42;
            case 12:
                return (byte) 35;
            case 13:
                return (byte) 65;
            case 14:
                return (byte) 66;
            case 15:
                return (byte) 67;
            default:
                return (byte) 32;
        }
    }

    int getNumOfVoicemails() {
        return this.mBearerData.numberOfMessages;
    }

    byte[] getIncomingSmsFingerprint() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(this.mEnvelope.teleService);
        output.write(this.mEnvelope.origAddress.origBytes, 0, this.mEnvelope.origAddress.origBytes.length);
        output.write(this.mEnvelope.bearerData, 0, this.mEnvelope.bearerData.length);
        output.write(this.mEnvelope.origSubaddress.origBytes, 0, this.mEnvelope.origSubaddress.origBytes.length);
        return output.toByteArray();
    }

    public ArrayList<CdmaSmsCbProgramData> getSmsCbProgramData() {
        return this.mBearerData.serviceCategoryProgramData;
    }

    protected boolean processCdmaCTWdpHeader(SmsMessage sms) {
        boolean decodeSuccess = false;
        try {
            BitwiseInputStream inStream = new BitwiseInputStream(sms.getUserData());
            int subparamId = inStream.read(8);
            if (subparamId != 0) {
                Rlog.e(LOG_TAG, "Invalid WDP SubparameterId");
                return false;
            }
            int subParamLen = inStream.read(8);
            if (subParamLen != 3) {
                Rlog.e(LOG_TAG, "Invalid WDP subparameter length");
                return false;
            }
            sms.mBearerData.messageType = inStream.read(4);
            int msgID = (inStream.read(8) << 8) | inStream.read(8);
            sms.mBearerData.hasUserDataHeader = inStream.read(1) == 1;
            if (sms.mBearerData.hasUserDataHeader) {
                Rlog.e(LOG_TAG, "Invalid WDP UserData header value");
                return false;
            }
            inStream.skip(3);
            sms.mBearerData.messageId = msgID;
            sms.mMessageRef = msgID;
            inStream.read(8);
            int subParamLen2 = inStream.read(8) * 8;
            sms.mBearerData.userData.msgEncoding = inStream.read(5);
            if (sms.mBearerData.userData.msgEncoding != 0) {
                Rlog.e(LOG_TAG, "Invalid WDP encoding");
                return false;
            }
            sms.mBearerData.userData.numFields = inStream.read(8);
            int i = 5 + 8;
            int remainigBits = subParamLen2 - 13;
            int dataBits = sms.mBearerData.userData.numFields * 8;
            if (dataBits >= remainigBits) {
                dataBits = remainigBits;
            }
            sms.mBearerData.userData.payload = inStream.readByteArray(dataBits);
            sms.mUserData = sms.mBearerData.userData.payload;
            decodeSuccess = true;
            return decodeSuccess;
        } catch (BitwiseInputStream.AccessException ex) {
            Rlog.e(LOG_TAG, "CT WDP Header decode failed: " + ex);
        }
    }
}
