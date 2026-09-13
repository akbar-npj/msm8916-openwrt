package android.telephony;

import android.app.ActivityThread;
import android.app.PendingIntent;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.SmsRawData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public final class SmsManager {
    public static final int RESULT_ERROR_FDN_CHECK_FAILURE = 6;
    public static final int RESULT_ERROR_GENERIC_FAILURE = 1;
    public static final int RESULT_ERROR_LIMIT_EXCEEDED = 5;
    public static final int RESULT_ERROR_NO_SERVICE = 4;
    public static final int RESULT_ERROR_NULL_PDU = 3;
    public static final int RESULT_ERROR_RADIO_OFF = 2;
    public static final int STATUS_ON_ICC_FREE = 0;
    public static final int STATUS_ON_ICC_READ = 1;
    public static final int STATUS_ON_ICC_SENT = 5;
    public static final int STATUS_ON_ICC_UNREAD = 3;
    public static final int STATUS_ON_ICC_UNSENT = 7;
    private static final SmsManager sInstance = new SmsManager();

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                iccISms.sendText(ActivityThread.currentPackageName(), destinationAddress, scAddress, text, sentIntent, deliveryIntent);
            }
        } catch (RemoteException e) {
        }
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                iccISms.sendTextWithOptions(ActivityThread.currentPackageName(), destinationAddress, scAddress, text, sentIntent, deliveryIntent, priority, isExpectMore, validityPeriod);
            }
        } catch (RemoteException e) {
        }
    }

    public ArrayList<String> divideMessage(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        return SmsMessage.fragmentText(text);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        }
        if (parts.size() > 1) {
            try {
                ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
                if (iccISms != null) {
                    iccISms.sendMultipartText(ActivityThread.currentPackageName(), destinationAddress, scAddress, parts, sentIntents, deliveryIntents);
                    return;
                }
                return;
            } catch (RemoteException e) {
                return;
            }
        }
        PendingIntent sentIntent = null;
        PendingIntent deliveryIntent = null;
        if (sentIntents != null && sentIntents.size() > 0) {
            sentIntent = sentIntents.get(0);
        }
        if (deliveryIntents != null && deliveryIntents.size() > 0) {
            deliveryIntent = deliveryIntents.get(0);
        }
        sendTextMessage(destinationAddress, scAddress, parts.get(0), sentIntent, deliveryIntent);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, int priority, boolean isExpectMore, int validityPeriod) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        }
        if (parts.size() > 1) {
            try {
                ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
                if (iccISms != null) {
                    iccISms.sendMultipartTextWithOptions(ActivityThread.currentPackageName(), destinationAddress, scAddress, parts, sentIntents, deliveryIntents, priority, isExpectMore, validityPeriod);
                    return;
                }
                return;
            } catch (RemoteException e) {
                return;
            }
        }
        PendingIntent sentIntent = null;
        PendingIntent deliveryIntent = null;
        if (sentIntents != null && sentIntents.size() > 0) {
            sentIntent = sentIntents.get(0);
        }
        if (deliveryIntents != null && deliveryIntents.size() > 0) {
            deliveryIntent = deliveryIntents.get(0);
        }
        sendTextMessage(destinationAddress, scAddress, parts.get(0), sentIntent, deliveryIntent, priority, isExpectMore, validityPeriod);
    }

    public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                iccISms.sendData(ActivityThread.currentPackageName(), destinationAddress, scAddress, destinationPort & 65535, data, sentIntent, deliveryIntent);
            }
        } catch (RemoteException e) {
        }
    }

    public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, short originatorPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                iccISms.sendDataWithOrigPort(ActivityThread.currentPackageName(), destinationAddress, scAddress, destinationPort & 65535, originatorPort & 65535, data, sentIntent, deliveryIntent);
            }
        } catch (RemoteException e) {
        }
    }

    public static SmsManager getDefault() {
        return sInstance;
    }

    private SmsManager() {
    }

    public boolean copyMessageToIcc(byte[] smsc, byte[] pdu, int status) {
        if (pdu == null) {
            throw new IllegalArgumentException("pdu is NULL");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.copyMessageToIccEf(ActivityThread.currentPackageName(), status, pdu, smsc);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean deleteMessageFromIcc(int messageIndex) {
        byte[] pdu = new byte[175];
        Arrays.fill(pdu, (byte) -1);
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(), messageIndex, 0, pdu);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean updateMessageOnIcc(int messageIndex, int newStatus, byte[] pdu) {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(), messageIndex, newStatus, pdu);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public static ArrayList<SmsMessage> getAllMessagesFromIcc() {
        List<SmsRawData> records = null;
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                records = iccISms.getAllMessagesFromIccEf(ActivityThread.currentPackageName());
            }
        } catch (RemoteException e) {
        }
        return createMessageListFromRawRecords(records);
    }

    public boolean enableCellBroadcast(int messageIdentifier) {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.enableCellBroadcast(messageIdentifier);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean disableCellBroadcast(int messageIdentifier) {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.disableCellBroadcast(messageIdentifier);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.enableCellBroadcastRange(startMessageId, endMessageId);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.disableCellBroadcastRange(startMessageId, endMessageId);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    private static ArrayList<SmsMessage> createMessageListFromRawRecords(List<SmsRawData> records) {
        SmsMessage sms;
        ArrayList<SmsMessage> messages = new ArrayList<>();
        if (records != null) {
            int count = records.size();
            for (int i = 0; i < count; i++) {
                SmsRawData data = records.get(i);
                if (data != null && (sms = SmsMessage.createFromEfRecord(i + 1, data.getBytes())) != null) {
                    messages.add(sms);
                }
            }
        }
        return messages;
    }

    boolean isImsSmsSupported() {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return false;
            }
            boolean boSupported = iccISms.isImsSmsSupported();
            return boSupported;
        } catch (RemoteException e) {
            return false;
        }
    }

    String getImsSmsFormat() {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return "unknown";
            }
            String format = iccISms.getImsSmsFormat();
            return format;
        } catch (RemoteException e) {
            return "unknown";
        }
    }

    public static int getSmsCapacityOnIcc() {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms == null) {
                return -1;
            }
            int ret = iccISms.getSmsCapacityOnIcc();
            return ret;
        } catch (RemoteException e) {
            return -1;
        }
    }
}
