package com.android.internal.telephony;

import android.provider.Telephony;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public abstract class SmsMessageBase {
    protected String mEmailBody;
    protected String mEmailFrom;
    protected boolean mIsEmail;
    protected boolean mIsMwi;
    protected String mMessageBody;
    public int mMessageRef;
    protected boolean mMwiDontStore;
    protected boolean mMwiSense;
    protected SmsAddress mOriginatingAddress;
    protected byte[] mPdu;
    protected String mPseudoSubject;
    protected SmsAddress mRecipientAddress;
    protected String mScAddress;
    protected long mScTimeMillis;
    protected byte[] mUserData;
    protected SmsHeader mUserDataHeader;
    protected int mStatusOnIcc = -1;
    protected int mIndexOnIcc = -1;

    public abstract SmsConstants.MessageClass getMessageClass();

    public abstract int getProtocolIdentifier();

    public abstract int getStatus();

    public abstract boolean isCphsMwiMessage();

    public abstract boolean isMWIClearMessage();

    public abstract boolean isMWISetMessage();

    public abstract boolean isMwiDontStore();

    public abstract boolean isReplace();

    public abstract boolean isReplyPathPresent();

    public abstract boolean isStatusReportMessage();

    public static abstract class SubmitPduBase {
        public byte[] encodedMessage;
        public byte[] encodedScAddress;

        public String toString() {
            return "SubmitPdu: encodedScAddress = " + Arrays.toString(this.encodedScAddress) + ", encodedMessage = " + Arrays.toString(this.encodedMessage);
        }
    }

    public String getServiceCenterAddress() {
        return this.mScAddress;
    }

    public String getOriginatingAddress() {
        if (this.mOriginatingAddress == null) {
            return null;
        }
        return this.mOriginatingAddress.getAddressString();
    }

    public String getDisplayOriginatingAddress() {
        return this.mIsEmail ? this.mEmailFrom : getOriginatingAddress();
    }

    public String getMessageBody() {
        return this.mMessageBody;
    }

    public String getDisplayMessageBody() {
        return this.mIsEmail ? this.mEmailBody : getMessageBody();
    }

    public String getPseudoSubject() {
        return this.mPseudoSubject == null ? "" : this.mPseudoSubject;
    }

    public long getTimestampMillis() {
        return this.mScTimeMillis;
    }

    public boolean isEmail() {
        return this.mIsEmail;
    }

    public String getEmailBody() {
        return this.mEmailBody;
    }

    public String getEmailFrom() {
        return this.mEmailFrom;
    }

    public byte[] getUserData() {
        return this.mUserData;
    }

    public SmsHeader getUserDataHeader() {
        return this.mUserDataHeader;
    }

    public byte[] getPdu() {
        return this.mPdu;
    }

    public int getStatusOnIcc() {
        return this.mStatusOnIcc;
    }

    public int getIndexOnIcc() {
        return this.mIndexOnIcc;
    }

    protected void parseMessageBody() {
        if (this.mOriginatingAddress != null && this.mOriginatingAddress.couldBeEmailGateway()) {
            extractEmailAddressFromMessageBody();
        }
    }

    protected void extractEmailAddressFromMessageBody() {
        String[] parts = this.mMessageBody.split("( /)|( )", 2);
        if (parts.length >= 2) {
            this.mEmailFrom = parts[0];
            this.mEmailBody = parts[1];
            this.mIsEmail = Telephony.Mms.isEmailAddress(this.mEmailFrom);
        }
    }

    public String getRecipientAddress() {
        if (this.mRecipientAddress == null) {
            return null;
        }
        return this.mRecipientAddress.getAddressString();
    }
}
