package com.android.internal.telephony;

import android.R;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.Rlog;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import java.util.HashMap;
import java.util.HashSet;

/* JADX INFO: loaded from: classes.dex */
public class SmsBroadcastUndelivered implements Runnable {
    private static final boolean DBG = true;
    private static final String TAG = "SmsBroadcastUndelivered";
    private final CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private final Context mContext;
    private final GsmInboundSmsHandler mGsmInboundSmsHandler;
    private final ContentResolver mResolver;
    private static final String[] PDU_PENDING_MESSAGE_PROJECTION = {"pdu", "sequence", "destination_port", "date", "reference_number", "count", "address", Telephony.MmsSms.WordsTable.ID};
    private static final Uri sRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");

    public SmsBroadcastUndelivered(Context context, GsmInboundSmsHandler gsmInboundSmsHandler, CdmaInboundSmsHandler cdmaInboundSmsHandler) {
        this.mContext = context;
        this.mResolver = context.getContentResolver();
        this.mGsmInboundSmsHandler = gsmInboundSmsHandler;
        this.mCdmaInboundSmsHandler = cdmaInboundSmsHandler;
    }

    @Override // java.lang.Runnable
    public void run() {
        Rlog.d(TAG, "scanning raw table for undelivered messages");
        scanRawTable();
        if (this.mGsmInboundSmsHandler != null) {
            this.mGsmInboundSmsHandler.sendMessage(6);
        }
        if (this.mCdmaInboundSmsHandler != null) {
            this.mCdmaInboundSmsHandler.sendMessage(6);
        }
    }

    private void scanRawTable() {
        String str;
        String str2;
        long startTime = System.nanoTime();
        HashMap<SmsReferenceKey, Integer> multiPartReceivedCount = new HashMap<>(4);
        HashSet<SmsReferenceKey> oldMultiPartMessages = new HashSet<>(4);
        Cursor cursor = null;
        try {
            try {
                Cursor cursor2 = this.mResolver.query(sRawUri, PDU_PENDING_MESSAGE_PROJECTION, null, null, null);
                if (cursor2 == null) {
                    Rlog.e(TAG, "error getting pending message cursor");
                    if (cursor2 != null) {
                        cursor2.close();
                    }
                    str = TAG;
                    str2 = "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms";
                } else {
                    boolean isCurrentFormat3gpp2 = InboundSmsHandler.isCurrentFormat3gpp2();
                    while (cursor2.moveToNext()) {
                        try {
                            InboundSmsTracker tracker = new InboundSmsTracker(cursor2, isCurrentFormat3gpp2);
                            if (tracker.getMessageCount() == 1) {
                                broadcastSms(tracker);
                            } else {
                                SmsReferenceKey reference = new SmsReferenceKey(tracker);
                                Integer receivedCount = multiPartReceivedCount.get(reference);
                                String expireAgeString = this.mContext.getResources().getString(R.string.config_defaultRingtoneVibrationSound);
                                long expireAge = Long.valueOf(expireAgeString).longValue();
                                if (receivedCount == null) {
                                    multiPartReceivedCount.put(reference, 1);
                                    if (tracker.getTimestamp() < System.currentTimeMillis() - expireAge) {
                                        oldMultiPartMessages.add(reference);
                                    }
                                } else {
                                    int newCount = receivedCount.intValue() + 1;
                                    if (newCount == tracker.getMessageCount()) {
                                        Rlog.d(TAG, "found complete multi-part message");
                                        broadcastSms(tracker);
                                        oldMultiPartMessages.remove(reference);
                                    } else {
                                        multiPartReceivedCount.put(reference, Integer.valueOf(newCount));
                                    }
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            Rlog.e(TAG, "error loading SmsTracker: " + e);
                        }
                    }
                    for (SmsReferenceKey message : oldMultiPartMessages) {
                        int rows = this.mResolver.delete(sRawUri, "address=? AND reference_number=? AND count=?", message.getDeleteWhereArgs());
                        if (rows == 0) {
                            Rlog.e(TAG, "No rows were deleted from raw table!");
                        } else {
                            Rlog.d(TAG, "Deleted " + rows + " rows from raw table for incomplete " + message.mMessageCount + " part message");
                        }
                    }
                    if (cursor2 != null) {
                        cursor2.close();
                    }
                    str = TAG;
                    str2 = "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms";
                }
            } catch (SQLException e2) {
                Rlog.e(TAG, "error reading pending SMS messages", e2);
                if (0 != 0) {
                    cursor.close();
                }
                str = TAG;
                str2 = "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms";
            }
            Rlog.d(str, str2);
        } catch (Throwable th) {
            if (0 != 0) {
                cursor.close();
            }
            Rlog.d(TAG, "finished scanning raw table in " + ((System.nanoTime() - startTime) / 1000000) + " ms");
            throw th;
        }
    }

    private void broadcastSms(InboundSmsTracker tracker) {
        InboundSmsHandler handler;
        if (tracker.is3gpp2()) {
            handler = this.mCdmaInboundSmsHandler;
        } else {
            handler = this.mGsmInboundSmsHandler;
        }
        if (handler != null) {
            handler.sendMessage(2, tracker);
        } else {
            Rlog.e(TAG, "null handler for " + tracker.getFormat() + " format, can't deliver.");
        }
    }

    private static class SmsReferenceKey {
        final String mAddress;
        final int mMessageCount;
        final int mReferenceNumber;

        SmsReferenceKey(InboundSmsTracker tracker) {
            this.mAddress = tracker.getAddress();
            this.mReferenceNumber = tracker.getReferenceNumber();
            this.mMessageCount = tracker.getMessageCount();
        }

        String[] getDeleteWhereArgs() {
            return new String[]{this.mAddress, Integer.toString(this.mReferenceNumber), Integer.toString(this.mMessageCount)};
        }

        public int hashCode() {
            return (((this.mReferenceNumber * 31) + this.mMessageCount) * 31) + this.mAddress.hashCode();
        }

        public boolean equals(Object o) {
            if (!(o instanceof SmsReferenceKey)) {
                return false;
            }
            SmsReferenceKey other = (SmsReferenceKey) o;
            return other.mAddress.equals(this.mAddress) && other.mReferenceNumber == this.mReferenceNumber && other.mMessageCount == this.mMessageCount;
        }
    }
}
