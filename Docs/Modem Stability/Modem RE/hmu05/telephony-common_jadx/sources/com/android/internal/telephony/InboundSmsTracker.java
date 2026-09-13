package com.android.internal.telephony;

import android.content.ContentValues;
import android.database.Cursor;
import android.telephony.SmsMessage;
import com.android.internal.util.HexDump;
import java.util.Arrays;
import java.util.Date;

/* JADX INFO: loaded from: classes.dex */
public final class InboundSmsTracker {
    private static final int DEST_PORT_FLAG_3GPP = 131072;
    private static final int DEST_PORT_FLAG_3GPP2 = 262144;
    private static final int DEST_PORT_FLAG_3GPP2_WAP_PDU = 524288;
    private static final int DEST_PORT_FLAG_NO_PORT = 65536;
    private static final int DEST_PORT_MASK = 65535;
    private final String mAddress;
    private String mDeleteWhere;
    private String[] mDeleteWhereArgs;
    private final int mDestPort;
    private final boolean mIs3gpp2;
    private final boolean mIs3gpp2WapPdu;
    private final int mMessageCount;
    private final byte[] mPdu;
    private final int mReferenceNumber;
    private final int mSequenceNumber;
    private final long mTimestamp;

    InboundSmsTracker(byte[] pdu, long timestamp, int destPort, boolean is3gpp2, boolean is3gpp2WapPdu) {
        this.mPdu = pdu;
        this.mTimestamp = timestamp;
        this.mDestPort = destPort;
        this.mIs3gpp2 = is3gpp2;
        this.mIs3gpp2WapPdu = is3gpp2WapPdu;
        this.mAddress = null;
        this.mReferenceNumber = -1;
        this.mSequenceNumber = getIndexOffset();
        this.mMessageCount = 1;
    }

    public InboundSmsTracker(byte[] pdu, long timestamp, int destPort, boolean is3gpp2, String address, int referenceNumber, int sequenceNumber, int messageCount, boolean is3gpp2WapPdu) {
        this.mPdu = pdu;
        this.mTimestamp = timestamp;
        this.mDestPort = destPort;
        this.mIs3gpp2 = is3gpp2;
        this.mIs3gpp2WapPdu = is3gpp2WapPdu;
        this.mAddress = address;
        this.mReferenceNumber = referenceNumber;
        this.mSequenceNumber = sequenceNumber;
        this.mMessageCount = messageCount;
    }

    InboundSmsTracker(Cursor cursor, boolean isCurrentFormat3gpp2) {
        this.mPdu = HexDump.hexStringToByteArray(cursor.getString(0));
        if (cursor.isNull(2)) {
            this.mDestPort = -1;
            this.mIs3gpp2 = isCurrentFormat3gpp2;
            this.mIs3gpp2WapPdu = false;
        } else {
            int destPort = cursor.getInt(2);
            if ((DEST_PORT_FLAG_3GPP & destPort) != 0) {
                this.mIs3gpp2 = false;
            } else if ((262144 & destPort) != 0) {
                this.mIs3gpp2 = true;
            } else {
                this.mIs3gpp2 = isCurrentFormat3gpp2;
            }
            this.mIs3gpp2WapPdu = (DEST_PORT_FLAG_3GPP2_WAP_PDU & destPort) != 0;
            this.mDestPort = getRealDestPort(destPort);
        }
        this.mTimestamp = cursor.getLong(3);
        if (cursor.isNull(5)) {
            long rowId = cursor.getLong(7);
            this.mAddress = null;
            this.mReferenceNumber = -1;
            this.mSequenceNumber = getIndexOffset();
            this.mMessageCount = 1;
            this.mDeleteWhere = "_id=?";
            this.mDeleteWhereArgs = new String[]{Long.toString(rowId)};
            return;
        }
        this.mAddress = cursor.getString(6);
        this.mReferenceNumber = cursor.getInt(4);
        this.mMessageCount = cursor.getInt(5);
        this.mSequenceNumber = cursor.getInt(1);
        int index = this.mSequenceNumber - getIndexOffset();
        if (index < 0 || index >= this.mMessageCount) {
            throw new IllegalArgumentException("invalid PDU sequence " + this.mSequenceNumber + " of " + this.mMessageCount);
        }
        this.mDeleteWhere = "address=? AND reference_number=? AND count=?";
        this.mDeleteWhereArgs = new String[]{this.mAddress, Integer.toString(this.mReferenceNumber), Integer.toString(this.mMessageCount)};
    }

    ContentValues getContentValues() {
        int destPort;
        int destPort2;
        ContentValues values = new ContentValues();
        values.put("pdu", HexDump.toHexString(this.mPdu));
        values.put("date", Long.valueOf(this.mTimestamp));
        if (this.mDestPort == -1) {
            destPort = DEST_PORT_FLAG_NO_PORT;
        } else {
            destPort = this.mDestPort & 65535;
        }
        if (this.mIs3gpp2) {
            destPort2 = destPort | 262144;
        } else {
            destPort2 = destPort | DEST_PORT_FLAG_3GPP;
        }
        if (this.mIs3gpp2WapPdu) {
            destPort2 |= DEST_PORT_FLAG_3GPP2_WAP_PDU;
        }
        values.put("destination_port", Integer.valueOf(destPort2));
        if (this.mAddress != null) {
            values.put("address", this.mAddress);
            values.put("reference_number", Integer.valueOf(this.mReferenceNumber));
            values.put("sequence", Integer.valueOf(this.mSequenceNumber));
            values.put("count", Integer.valueOf(this.mMessageCount));
        }
        return values;
    }

    static int getRealDestPort(int destPort) {
        if ((DEST_PORT_FLAG_NO_PORT & destPort) != 0) {
            return -1;
        }
        return 65535 & destPort;
    }

    void setDeleteWhere(String deleteWhere, String[] deleteWhereArgs) {
        this.mDeleteWhere = deleteWhere;
        this.mDeleteWhereArgs = deleteWhereArgs;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder("SmsTracker{timestamp=");
        builder.append(new Date(this.mTimestamp));
        builder.append(" destPort=").append(this.mDestPort);
        builder.append(" is3gpp2=").append(this.mIs3gpp2);
        if (this.mAddress != null) {
            builder.append(" address=").append(this.mAddress);
            builder.append(" refNumber=").append(this.mReferenceNumber);
            builder.append(" seqNumber=").append(this.mSequenceNumber);
            builder.append(" msgCount=").append(this.mMessageCount);
        }
        if (this.mDeleteWhere != null) {
            builder.append(" deleteWhere(").append(this.mDeleteWhere);
            builder.append(") deleteArgs=(").append(Arrays.toString(this.mDeleteWhereArgs));
            builder.append(')');
        }
        builder.append('}');
        return builder.toString();
    }

    byte[] getPdu() {
        return this.mPdu;
    }

    long getTimestamp() {
        return this.mTimestamp;
    }

    int getDestPort() {
        return this.mDestPort;
    }

    boolean is3gpp2() {
        return this.mIs3gpp2;
    }

    String getFormat() {
        return this.mIs3gpp2 ? SmsMessage.FORMAT_3GPP2 : SmsMessage.FORMAT_3GPP;
    }

    int getIndexOffset() {
        return (this.mIs3gpp2 && this.mIs3gpp2WapPdu) ? 0 : 1;
    }

    String getAddress() {
        return this.mAddress;
    }

    int getReferenceNumber() {
        return this.mReferenceNumber;
    }

    int getSequenceNumber() {
        return this.mSequenceNumber;
    }

    int getMessageCount() {
        return this.mMessageCount;
    }

    String getDeleteWhere() {
        return this.mDeleteWhere;
    }

    String[] getDeleteWhereArgs() {
        return this.mDeleteWhereArgs;
    }
}
