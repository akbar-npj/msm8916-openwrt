package com.android.internal.telephony.gsm;

import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class SmsCbHeader {
    static final int FORMAT_ETWS_PRIMARY = 3;
    static final int FORMAT_GSM = 1;
    static final int FORMAT_UMTS = 2;
    private static final int MESSAGE_TYPE_CBS_MESSAGE = 1;
    static final int PDU_HEADER_LENGTH = 6;
    private static final int PDU_LENGTH_ETWS = 56;
    private static final int PDU_LENGTH_GSM = 88;
    private final SmsCbCmasInfo mCmasInfo;
    private final int mDataCodingScheme;
    private final SmsCbEtwsInfo mEtwsInfo;
    private final int mFormat;
    private final int mGeographicalScope;
    private final int mMessageIdentifier;
    private final int mNrOfPages;
    private final int mPageIndex;
    private final int mSerialNumber;

    public SmsCbHeader(byte[] pdu) throws IllegalArgumentException {
        byte[] warningSecurityInfo;
        if (pdu == null || pdu.length < 6) {
            throw new IllegalArgumentException("Illegal PDU");
        }
        if (pdu.length <= PDU_LENGTH_GSM) {
            this.mGeographicalScope = (pdu[0] & 192) >>> 6;
            this.mSerialNumber = ((pdu[0] & 255) << 8) | (pdu[1] & 255);
            this.mMessageIdentifier = ((pdu[2] & 255) << 8) | (pdu[3] & 255);
            if (isEtwsMessage() && pdu.length <= PDU_LENGTH_ETWS) {
                this.mFormat = 3;
                this.mDataCodingScheme = -1;
                this.mPageIndex = -1;
                this.mNrOfPages = -1;
                boolean emergencyUserAlert = (pdu[4] & 1) != 0;
                boolean activatePopup = (pdu[5] & 128) != 0;
                int warningType = (pdu[4] & 254) >>> 1;
                if (pdu.length > 6) {
                    warningSecurityInfo = Arrays.copyOfRange(pdu, 6, pdu.length);
                } else {
                    warningSecurityInfo = null;
                }
                this.mEtwsInfo = new SmsCbEtwsInfo(warningType, emergencyUserAlert, activatePopup, warningSecurityInfo);
                this.mCmasInfo = null;
                return;
            }
            this.mFormat = 1;
            this.mDataCodingScheme = pdu[4] & 255;
            int pageIndex = (pdu[5] & 240) >>> 4;
            int nrOfPages = pdu[5] & 15;
            if (pageIndex == 0 || nrOfPages == 0 || pageIndex > nrOfPages) {
                pageIndex = 1;
                nrOfPages = 1;
            }
            this.mPageIndex = pageIndex;
            this.mNrOfPages = nrOfPages;
        } else {
            this.mFormat = 2;
            int messageType = pdu[0];
            if (messageType != 1) {
                throw new IllegalArgumentException("Unsupported message type " + messageType);
            }
            this.mMessageIdentifier = ((pdu[1] & 255) << 8) | (pdu[2] & 255);
            this.mGeographicalScope = (pdu[3] & 192) >>> 6;
            this.mSerialNumber = ((pdu[3] & 255) << 8) | (pdu[4] & 255);
            this.mDataCodingScheme = pdu[5] & 255;
            this.mPageIndex = 1;
            this.mNrOfPages = 1;
        }
        if (isEtwsMessage()) {
            boolean emergencyUserAlert2 = isEtwsEmergencyUserAlert();
            boolean activatePopup2 = isEtwsPopupAlert();
            int warningType2 = getEtwsWarningType();
            this.mEtwsInfo = new SmsCbEtwsInfo(warningType2, emergencyUserAlert2, activatePopup2, null);
            this.mCmasInfo = null;
            return;
        }
        if (isCmasMessage()) {
            int messageClass = getCmasMessageClass();
            int severity = getCmasSeverity();
            int urgency = getCmasUrgency();
            int certainty = getCmasCertainty();
            this.mEtwsInfo = null;
            this.mCmasInfo = new SmsCbCmasInfo(messageClass, -1, -1, severity, urgency, certainty);
            return;
        }
        this.mEtwsInfo = null;
        this.mCmasInfo = null;
    }

    int getGeographicalScope() {
        return this.mGeographicalScope;
    }

    int getSerialNumber() {
        return this.mSerialNumber;
    }

    int getServiceCategory() {
        return this.mMessageIdentifier;
    }

    int getDataCodingScheme() {
        return this.mDataCodingScheme;
    }

    int getPageIndex() {
        return this.mPageIndex;
    }

    int getNumberOfPages() {
        return this.mNrOfPages;
    }

    SmsCbEtwsInfo getEtwsInfo() {
        return this.mEtwsInfo;
    }

    SmsCbCmasInfo getCmasInfo() {
        return this.mCmasInfo;
    }

    boolean isEmergencyMessage() {
        return this.mMessageIdentifier >= 4352 && this.mMessageIdentifier <= 6399;
    }

    private boolean isEtwsMessage() {
        return (this.mMessageIdentifier & SmsCbConstants.MESSAGE_ID_ETWS_TYPE_MASK) == 4352;
    }

    boolean isEtwsPrimaryNotification() {
        return this.mFormat == 3;
    }

    boolean isUmtsFormat() {
        return this.mFormat == 2;
    }

    private boolean isCmasMessage() {
        return this.mMessageIdentifier >= 4370 && this.mMessageIdentifier <= 4399;
    }

    private boolean isEtwsPopupAlert() {
        return (this.mSerialNumber & 4096) != 0;
    }

    private boolean isEtwsEmergencyUserAlert() {
        return (this.mSerialNumber & SmsCbConstants.SERIAL_NUMBER_ETWS_EMERGENCY_USER_ALERT) != 0;
    }

    private int getEtwsWarningType() {
        return this.mMessageIdentifier - 4352;
    }

    private int getCmasMessageClass() {
        switch (this.mMessageIdentifier) {
            case 4370:
                return 0;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED /* 4371 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY /* 4372 */:
                return 1;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED /* 4373 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY /* 4374 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED /* 4375 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY /* 4376 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED /* 4377 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY /* 4378 */:
                return 2;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY /* 4379 */:
                return 3;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST /* 4380 */:
                return 4;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE /* 4381 */:
                return 5;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE /* 4382 */:
                return 6;
            default:
                return -1;
        }
    }

    private int getCmasSeverity() {
        switch (this.mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED /* 4371 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY /* 4372 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED /* 4373 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY /* 4374 */:
                return 0;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED /* 4375 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY /* 4376 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED /* 4377 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY /* 4378 */:
                return 1;
            default:
                return -1;
        }
    }

    private int getCmasUrgency() {
        switch (this.mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED /* 4371 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY /* 4372 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED /* 4375 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY /* 4376 */:
                return 0;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED /* 4373 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY /* 4374 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED /* 4377 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY /* 4378 */:
                return 1;
            default:
                return -1;
        }
    }

    private int getCmasCertainty() {
        switch (this.mMessageIdentifier) {
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED /* 4371 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED /* 4373 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_OBSERVED /* 4375 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_OBSERVED /* 4377 */:
                return 0;
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY /* 4372 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_LIKELY /* 4374 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_IMMEDIATE_LIKELY /* 4376 */:
            case SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY /* 4378 */:
                return 1;
            default:
                return -1;
        }
    }

    public String toString() {
        return "SmsCbHeader{GS=" + this.mGeographicalScope + ", serialNumber=0x" + Integer.toHexString(this.mSerialNumber) + ", messageIdentifier=0x" + Integer.toHexString(this.mMessageIdentifier) + ", DCS=0x" + Integer.toHexString(this.mDataCodingScheme) + ", page " + this.mPageIndex + " of " + this.mNrOfPages + '}';
    }
}
