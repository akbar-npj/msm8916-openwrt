package com.android.internal.telephony;

import android.R;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import com.android.internal.util.HexDump;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public abstract class InboundSmsHandler extends StateMachine {
    static final int ADDRESS_COLUMN = 6;
    static final int COUNT_COLUMN = 5;
    static final int DATE_COLUMN = 3;
    protected static final boolean DBG = true;
    static final int DESTINATION_PORT_COLUMN = 2;
    static final int EVENT_BROADCAST_COMPLETE = 3;
    static final int EVENT_BROADCAST_SMS = 2;
    public static final int EVENT_NEW_SMS = 1;
    static final int EVENT_RELEASE_WAKELOCK = 5;
    static final int EVENT_RETURN_TO_IDLE = 4;
    static final int EVENT_START_ACCEPTING_SMS = 6;
    static final int EVENT_UPDATE_PHONE_OBJECT = 7;
    static final int ID_COLUMN = 7;
    static final int PDU_COLUMN = 0;
    static final int REFERENCE_NUMBER_COLUMN = 4;
    static final String SELECT_BY_ID = "_id=?";
    static final String SELECT_BY_REFERENCE = "address=? AND reference_number=? AND count=?";
    static final int SEQUENCE_COLUMN = 1;
    private static final boolean VDBG = false;
    private static final int WAKELOCK_TIMEOUT = 3000;
    protected CellBroadcastHandler mCellBroadcastHandler;
    protected final Context mContext;
    final DefaultState mDefaultState;
    final DeliveringState mDeliveringState;
    final IdleState mIdleState;
    protected PhoneBase mPhone;
    private final ContentResolver mResolver;
    private final boolean mSmsReceiveDisabled;
    final StartupState mStartupState;
    protected SmsStorageMonitor mStorageMonitor;
    final WaitingState mWaitingState;
    final PowerManager.WakeLock mWakeLock;
    private final WapPushOverSms mWapPush;
    private static final String[] PDU_PROJECTION = {"pdu"};
    private static final String[] PDU_SEQUENCE_PORT_PROJECTION = {"pdu", "sequence", "destination_port"};
    private static final Uri sRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");

    protected abstract void acknowledgeLastIncomingSms(boolean z, int i, Message message);

    protected abstract int dispatchMessageRadioSpecific(SmsMessageBase smsMessageBase);

    protected abstract boolean is3gpp2();

    protected InboundSmsHandler(String name, Context context, SmsStorageMonitor storageMonitor, PhoneBase phone, CellBroadcastHandler cellBroadcastHandler) {
        super(name);
        this.mDefaultState = new DefaultState();
        this.mStartupState = new StartupState();
        this.mIdleState = new IdleState();
        this.mDeliveringState = new DeliveringState();
        this.mWaitingState = new WaitingState();
        this.mContext = context;
        this.mStorageMonitor = storageMonitor;
        this.mPhone = phone;
        this.mCellBroadcastHandler = cellBroadcastHandler;
        this.mResolver = context.getContentResolver();
        this.mWapPush = new WapPushOverSms(context);
        boolean smsCapable = this.mContext.getResources().getBoolean(R.bool.config_autoBrightnessResetAmbientLuxAfterWarmUp);
        this.mSmsReceiveDisabled = !SystemProperties.getBoolean("telephony.sms.receive", smsCapable);
        PowerManager pm = (PowerManager) this.mContext.getSystemService("power");
        this.mWakeLock = pm.newWakeLock(1, name);
        this.mWakeLock.acquire();
        addState(this.mDefaultState);
        addState(this.mStartupState, this.mDefaultState);
        addState(this.mIdleState, this.mDefaultState);
        addState(this.mDeliveringState, this.mDefaultState);
        addState(this.mWaitingState, this.mDeliveringState);
        setInitialState(this.mStartupState);
        log("created InboundSmsHandler");
    }

    public void dispose() {
        quit();
    }

    public void updatePhoneObject(PhoneBase phone) {
        sendMessage(7, phone);
    }

    protected void onQuitting() {
        this.mWapPush.dispose();
        while (this.mWakeLock.isHeld()) {
            this.mWakeLock.release();
        }
    }

    public PhoneBase getPhone() {
        return this.mPhone;
    }

    class DefaultState extends State {
        DefaultState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 7:
                    InboundSmsHandler.this.onUpdatePhoneObject((PhoneBase) msg.obj);
                    return true;
                default:
                    String errorText = "processMessage: unhandled message type " + msg.what;
                    if (Build.IS_DEBUGGABLE) {
                        throw new RuntimeException(errorText);
                    }
                    InboundSmsHandler.this.loge(errorText);
                    return true;
            }
        }
    }

    class StartupState extends State {
        StartupState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 1:
                case 2:
                    InboundSmsHandler.this.deferMessage(msg);
                    return true;
                case 3:
                case 4:
                case 5:
                default:
                    return false;
                case 6:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
            }
        }
    }

    class IdleState extends State {
        IdleState() {
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Idle state");
            InboundSmsHandler.this.sendMessageDelayed(5, 3000L);
        }

        public void exit() {
            InboundSmsHandler.this.mWakeLock.acquire();
            InboundSmsHandler.this.log("acquired wakelock, leaving Idle state");
        }

        public boolean processMessage(Message msg) {
            InboundSmsHandler.this.log("Idle state processing message type " + msg.what);
            switch (msg.what) {
                case 1:
                case 2:
                    InboundSmsHandler.this.deferMessage(msg);
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mDeliveringState);
                    return true;
                case 3:
                default:
                    return false;
                case 4:
                    return true;
                case 5:
                    InboundSmsHandler.this.mWakeLock.release();
                    if (InboundSmsHandler.this.mWakeLock.isHeld()) {
                        InboundSmsHandler.this.log("mWakeLock is still held after release");
                        return true;
                    }
                    InboundSmsHandler.this.log("mWakeLock released");
                    return true;
            }
        }
    }

    class DeliveringState extends State {
        DeliveringState() {
        }

        public void enter() {
            InboundSmsHandler.this.log("entering Delivering state");
        }

        public void exit() {
            InboundSmsHandler.this.log("leaving Delivering state");
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    InboundSmsHandler.this.handleNewSms((AsyncResult) msg.obj);
                    InboundSmsHandler.this.sendMessage(4);
                    return true;
                case 2:
                    if (InboundSmsHandler.this.processMessagePart((InboundSmsTracker) msg.obj)) {
                        InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mWaitingState);
                    }
                    return true;
                case 3:
                default:
                    return false;
                case 4:
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mIdleState);
                    return true;
                case 5:
                    InboundSmsHandler.this.mWakeLock.release();
                    if (!InboundSmsHandler.this.mWakeLock.isHeld()) {
                        InboundSmsHandler.this.loge("mWakeLock released while delivering/broadcasting!");
                    }
                    return true;
            }
        }
    }

    class WaitingState extends State {
        WaitingState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case 2:
                    InboundSmsHandler.this.deferMessage(msg);
                    return true;
                case 3:
                    InboundSmsHandler.this.sendMessage(4);
                    InboundSmsHandler.this.transitionTo(InboundSmsHandler.this.mDeliveringState);
                    return true;
                case 4:
                    return true;
                default:
                    return false;
            }
        }
    }

    void handleNewSms(AsyncResult ar) {
        int result;
        if (ar.exception != null) {
            loge("Exception processing incoming SMS: " + ar.exception);
            return;
        }
        try {
            SmsMessage sms = (SmsMessage) ar.result;
            result = dispatchMessage(sms.mWrappedSmsMessage);
        } catch (RuntimeException ex) {
            loge("Exception dispatching message", ex);
            result = 2;
        }
        if (result != -1) {
            boolean handled = result == 1;
            notifyAndAcknowledgeLastIncomingSms(handled, result, null);
        }
    }

    public int dispatchMessage(SmsMessageBase smsb) {
        if (smsb == null) {
            loge("dispatchSmsMessage: message is null");
            return 2;
        }
        if (this.mSmsReceiveDisabled) {
            log("Received short message on device which doesn't support receiving SMS. Ignored.");
            return 1;
        }
        return dispatchMessageRadioSpecific(smsb);
    }

    protected void onUpdatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mStorageMonitor = this.mPhone.mSmsStorageMonitor;
        log("onUpdatePhoneObject: phone=" + this.mPhone.getClass().getSimpleName());
    }

    void notifyAndAcknowledgeLastIncomingSms(boolean success, int result, Message response) {
        if (!success) {
            Intent intent = new Intent(Telephony.Sms.Intents.SMS_REJECTED_ACTION);
            intent.putExtra("result", result);
            this.mContext.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
        }
        acknowledgeLastIncomingSms(success, result, response);
    }

    protected int dispatchNormalMessage(SmsMessageBase sms) {
        InboundSmsTracker tracker;
        SmsHeader smsHeader = sms.getUserDataHeader();
        if (smsHeader == null || smsHeader.concatRef == null) {
            int destPort = -1;
            if (smsHeader != null && smsHeader.portAddrs != null) {
                destPort = smsHeader.portAddrs.destPort;
                log("destination port: " + destPort);
            }
            tracker = new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), destPort, is3gpp2(), false);
        } else {
            SmsHeader.ConcatRef concatRef = smsHeader.concatRef;
            SmsHeader.PortAddrs portAddrs = smsHeader.portAddrs;
            tracker = new InboundSmsTracker(sms.getPdu(), sms.getTimestampMillis(), portAddrs != null ? portAddrs.destPort : -1, is3gpp2(), sms.getOriginatingAddress(), concatRef.refNumber, concatRef.seqNumber, concatRef.msgCount, false);
        }
        return addTrackerToRawTableAndSendMessage(tracker);
    }

    protected int addTrackerToRawTableAndSendMessage(InboundSmsTracker tracker) {
        switch (addTrackerToRawTable(tracker)) {
            case 1:
                sendMessage(2, tracker);
                return 1;
            case 5:
                return 1;
            default:
                return 2;
        }
    }

    /* JADX WARN: Code duplicated, block: B:44:0x00fc  */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v9, types: [android.content.Intent] */
    /* JADX WARN: Type inference failed for: r1v3, types: [java.io.Serializable] */
    boolean processMessagePart(InboundSmsTracker tracker) {
        byte[][] pdus;
        Intent intent;
        int messageCount = tracker.getMessageCount();
        int destPort = tracker.getDestPort();
        String address = "";
        if (messageCount != 1) {
            Cursor cursor = null;
            try {
                try {
                    address = tracker.getAddress();
                    String refNumber = Integer.toString(tracker.getReferenceNumber());
                    String count = Integer.toString(tracker.getMessageCount());
                    String[] whereArgs = {address, refNumber, count};
                    cursor = this.mResolver.query(sRawUri, PDU_SEQUENCE_PORT_PROJECTION, SELECT_BY_REFERENCE, whereArgs, null);
                    int cursorCount = cursor.getCount();
                    if (cursorCount < messageCount) {
                        if (cursor == null) {
                            return false;
                        }
                        cursor.close();
                        return false;
                    }
                    pdus = new byte[messageCount][];
                    while (cursor.moveToNext()) {
                        int index = cursor.getInt(1) - tracker.getIndexOffset();
                        pdus[index] = HexDump.hexStringToByteArray(cursor.getString(0));
                        if (index == 0 && !cursor.isNull(2)) {
                            int port = InboundSmsTracker.getRealDestPort(cursor.getInt(2));
                            if (port != -1) {
                                destPort = port;
                            }
                        }
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                } catch (SQLException e) {
                    loge("Can't access multipart SMS database", e);
                    if (cursor == null) {
                        return false;
                    }
                    cursor.close();
                    return false;
                }
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
                throw th;
            }
            if (cursor != null) {
                cursor.close();
            }
            throw th;
        }
        pdus = new byte[][]{tracker.getPdu()};
        BroadcastReceiver resultReceiver = new SmsBroadcastReceiver(tracker);
        if (destPort != 2948) {
            if (destPort == -1) {
                intent = new Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION);
                ComponentName componentName = SmsApplication.getDefaultSmsApplication(this.mContext, true);
                if (componentName != null) {
                    intent.setComponent(componentName);
                    log("Delivering SMS to: " + componentName.getPackageName() + " " + componentName.getClassName());
                }
            } else {
                Uri uri = Uri.parse("sms://localhost:" + destPort);
                intent = new Intent(Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION, uri);
            }
            intent.putExtra("pdus", (Serializable) pdus);
            intent.putExtra(Telephony.CellBroadcasts.MESSAGE_FORMAT, tracker.getFormat());
            dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, resultReceiver);
            return true;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[][] arr$ = pdus;
        for (byte[] pdu : arr$) {
            if (!tracker.is3gpp2()) {
                SmsMessage msg = SmsMessage.createFromPdu(pdu, SmsMessage.FORMAT_3GPP);
                pdu = msg.getUserData();
                if (address == "") {
                    address = msg.getOriginatingAddress();
                }
            } else if (address == "") {
                address = tracker.getAddress();
            }
            output.write(pdu, 0, pdu.length);
        }
        int result = this.mWapPush.dispatchWapPdu(output.toByteArray(), resultReceiver, this, address);
        log("dispatchWapPdu() returned " + result);
        return result == -1;
    }

    protected void dispatchIntent(Intent intent, String permission, int appOp, BroadcastReceiver resultReceiver) {
        intent.addFlags(134217728);
        this.mContext.sendOrderedBroadcast(intent, permission, appOp, resultReceiver, getHandler(), -1, (String) null, (Bundle) null);
    }

    void deleteFromRawTable(String deleteWhere, String[] deleteWhereArgs) {
        int rows = this.mResolver.delete(sRawUri, deleteWhere, deleteWhereArgs);
        if (rows == 0) {
            loge("No rows were deleted from raw table!");
        } else {
            log("Deleted " + rows + " rows from raw table.");
        }
    }

    private int addTrackerToRawTable(InboundSmsTracker tracker) {
        if (tracker.getMessageCount() != 1) {
            Cursor cursor = null;
            try {
                try {
                    int sequence = tracker.getSequenceNumber();
                    String address = tracker.getAddress();
                    String refNumber = Integer.toString(tracker.getReferenceNumber());
                    String count = Integer.toString(tracker.getMessageCount());
                    String seqNumber = Integer.toString(sequence);
                    String[] deleteWhereArgs = {address, refNumber, count};
                    tracker.setDeleteWhere(SELECT_BY_REFERENCE, deleteWhereArgs);
                    Cursor cursor2 = this.mResolver.query(sRawUri, PDU_PROJECTION, "address=? AND reference_number=? AND count=? AND sequence=?", new String[]{address, refNumber, count, seqNumber}, null);
                    if (cursor2.moveToNext()) {
                        loge("Discarding duplicate message segment, refNumber=" + refNumber + " seqNumber=" + seqNumber);
                        String oldPduString = cursor2.getString(0);
                        byte[] pdu = tracker.getPdu();
                        byte[] oldPdu = HexDump.hexStringToByteArray(oldPduString);
                        if (!Arrays.equals(oldPdu, tracker.getPdu())) {
                            loge("Warning: dup message segment PDU of length " + pdu.length + " is different from existing PDU of length " + oldPdu.length);
                        }
                        if (cursor2 == null) {
                            return 5;
                        }
                        cursor2.close();
                        return 5;
                    }
                    cursor2.close();
                    if (cursor2 != null) {
                        cursor2.close();
                    }
                } catch (Throwable th) {
                    if (0 != 0) {
                        cursor.close();
                    }
                    throw th;
                }
            } catch (SQLException e) {
                loge("Can't access multipart SMS database", e);
                if (0 == 0) {
                    return 2;
                }
                cursor.close();
                return 2;
            }
        }
        ContentValues values = tracker.getContentValues();
        Uri newUri = this.mResolver.insert(sRawUri, values);
        log("URI of new row -> " + newUri);
        try {
            long rowId = ContentUris.parseId(newUri);
            if (tracker.getMessageCount() == 1) {
                tracker.setDeleteWhere(SELECT_BY_ID, new String[]{Long.toString(rowId)});
            }
            return 1;
        } catch (Exception e2) {
            loge("error parsing URI for new row: " + newUri, e2);
            return 2;
        }
    }

    static boolean isCurrentFormat3gpp2() {
        int activePhone = TelephonyManager.getDefault().getCurrentPhoneType();
        return 2 == activePhone;
    }

    protected void storeVoiceMailCount() {
        String imsi = this.mPhone.getSubscriberId();
        int mwi = this.mPhone.getVoiceMessageCount();
        int sub = this.mPhone.getSubscription();
        StringBuilder sbAppend = new StringBuilder().append("Storing Voice Mail Count = ").append(mwi).append(" for imsi = ").append(imsi).append(" for mVmCountKey = ");
        PhoneBase phoneBase = this.mPhone;
        StringBuilder sbAppend2 = sbAppend.append(PhoneBase.VM_COUNT).append(" vmId = ");
        PhoneBase phoneBase2 = this.mPhone;
        log(sbAppend2.append(PhoneBase.VM_ID).append(" in preferences.").toString());
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor editor = sp.edit();
        StringBuilder sb = new StringBuilder();
        PhoneBase phoneBase3 = this.mPhone;
        editor.putInt(sb.append(PhoneBase.VM_COUNT).append(sub).toString(), mwi);
        StringBuilder sb2 = new StringBuilder();
        PhoneBase phoneBase4 = this.mPhone;
        editor.putString(sb2.append(PhoneBase.VM_ID).append(sub).toString(), imsi);
        editor.commit();
    }

    private final class SmsBroadcastReceiver extends BroadcastReceiver {
        private long mBroadcastTimeNano = System.nanoTime();
        private final String mDeleteWhere;
        private final String[] mDeleteWhereArgs;

        SmsBroadcastReceiver(InboundSmsTracker tracker) {
            this.mDeleteWhere = tracker.getDeleteWhere();
            this.mDeleteWhereArgs = tracker.getDeleteWhereArgs();
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Telephony.Sms.Intents.SMS_DELIVER_ACTION)) {
                intent.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
                intent.setComponent(null);
                InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this);
                return;
            }
            if (action.equals(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION)) {
                intent.setAction(Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION);
                intent.setComponent(null);
                InboundSmsHandler.this.dispatchIntent(intent, "android.permission.RECEIVE_SMS", 16, this);
                return;
            }
            if (!Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION.equals(action) && !Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION.equals(action) && !Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(action)) {
                InboundSmsHandler.this.loge("unexpected BroadcastReceiver action: " + action);
            }
            int rc = getResultCode();
            if (rc != -1 && rc != 1) {
                InboundSmsHandler.this.loge("a broadcast receiver set the result code to " + rc + ", deleting from raw table anyway!");
            } else {
                InboundSmsHandler.this.log("successful broadcast, deleting from raw table.");
            }
            InboundSmsHandler.this.deleteFromRawTable(this.mDeleteWhere, this.mDeleteWhereArgs);
            InboundSmsHandler.this.sendMessage(3);
            int durationMillis = (int) ((System.nanoTime() - this.mBroadcastTimeNano) / 1000000);
            if (durationMillis >= 5000) {
                InboundSmsHandler.this.loge("Slow ordered broadcast completion time: " + durationMillis + " ms");
            } else {
                InboundSmsHandler.this.log("ordered broadcast completed in: " + durationMillis + " ms");
            }
        }
    }

    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }
}
