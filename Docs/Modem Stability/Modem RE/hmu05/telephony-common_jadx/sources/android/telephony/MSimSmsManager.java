package android.telephony;

import android.app.ActivityThread;
import android.app.PendingIntent;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import com.android.internal.telephony.SmsRawData;
import com.android.internal.telephony.msim.ISmsMSim;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class MSimSmsManager {
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
    private final int DEFAULT_SUB = 0;
    private static final MSimSmsManager sInstance = new MSimSmsManager();
    protected static boolean isMultiSimEnabled = MSimTelephonyManager.getDefault().isMultiSimEnabled();

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                iccISms.sendText(ActivityThread.currentPackageName(), destinationAddress, scAddress, text, sentIntent, deliveryIntent, subscription);
            }
        } catch (RemoteException e) {
        }
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                iccISms.sendTextWithOptions(ActivityThread.currentPackageName(), destinationAddress, scAddress, text, sentIntent, deliveryIntent, priority, isExpectMore, validityPeriod, subscription);
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

    public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        }
        if (parts.size() > 1) {
            try {
                ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
                if (iccISms != null) {
                    iccISms.sendMultipartText(ActivityThread.currentPackageName(), destinationAddress, scAddress, parts, sentIntents, deliveryIntents, subscription);
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
        sendTextMessage(destinationAddress, scAddress, parts.get(0), sentIntent, deliveryIntent, subscription);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts, ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents, int priority, boolean isExpectMore, int validityPeriod, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        }
        if (parts.size() > 1) {
            try {
                ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
                if (iccISms != null) {
                    iccISms.sendMultipartTextWithOptions(ActivityThread.currentPackageName(), destinationAddress, scAddress, parts, sentIntents, deliveryIntents, priority, isExpectMore, validityPeriod, subscription);
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
        sendTextMessage(destinationAddress, scAddress, parts.get(0), sentIntent, deliveryIntent, priority, isExpectMore, validityPeriod, subscription);
    }

    public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                iccISms.sendData(ActivityThread.currentPackageName(), destinationAddress, scAddress, destinationPort & 65535, data, sentIntent, deliveryIntent, subscription);
            }
        } catch (RemoteException e) {
        }
    }

    public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, short originatorPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                iccISms.sendDataWithOrigPort(ActivityThread.currentPackageName(), destinationAddress, scAddress, destinationPort & 65535, originatorPort & 65535, data, sentIntent, deliveryIntent, subscription);
            }
        } catch (RemoteException e) {
        }
    }

    public static MSimSmsManager getDefault() {
        return sInstance;
    }

    private MSimSmsManager() {
    }

    public boolean copyMessageToIcc(byte[] smsc, byte[] pdu, int status, int subscription) {
        if (pdu == null) {
            throw new IllegalArgumentException("pdu is NULL");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.copyMessageToIccEf(ActivityThread.currentPackageName(), status, pdu, smsc, subscription);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean deleteMessageFromIcc(int messageIndex, int subscription) {
        byte[] pdu = new byte[175];
        Arrays.fill(pdu, (byte) -1);
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(), messageIndex, 0, pdu, subscription);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean updateMessageOnIcc(int messageIndex, int newStatus, byte[] pdu, int subscription) {
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(), messageIndex, newStatus, pdu, subscription);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public ArrayList<SmsMessage> getAllMessagesFromIcc(int subscription) {
        List<SmsRawData> records = null;
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                records = iccISms.getAllMessagesFromIccEf(ActivityThread.currentPackageName(), subscription);
            }
        } catch (RemoteException e) {
        }
        return createMessageListFromRawRecords(records, subscription);
    }

    public boolean enableCellBroadcast(int messageIdentifier, int subscription) {
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.enableCellBroadcast(messageIdentifier, subscription);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean disableCellBroadcast(int messageIdentifier, int subscription) {
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.disableCellBroadcast(messageIdentifier, subscription);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId, int subscription) {
        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.enableCellBroadcastRange(startMessageId, endMessageId, subscription);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId, int subscription) {
        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return false;
            }
            boolean success = iccISms.disableCellBroadcastRange(startMessageId, endMessageId, subscription);
            return success;
        } catch (RemoteException e) {
            return false;
        }
    }

    private static ArrayList<SmsMessage> createMessageListFromRawRecords(List<SmsRawData> records, int subscription) {
        SmsMessage sms;
        ArrayList<SmsMessage> messages = new ArrayList<>();
        if (records != null) {
            int count = records.size();
            for (int i = 0; i < count; i++) {
                SmsRawData data = records.get(i);
                if (data != null && (sms = SmsMessage.createFromEfRecord(i + 1, data.getBytes(), subscription)) != null) {
                    messages.add(sms);
                }
            }
        }
        return messages;
    }

    boolean isImsSmsSupported(int subscription) {
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return false;
            }
            boolean boSupported = iccISms.isImsSmsSupported(subscription);
            return boSupported;
        } catch (RemoteException e) {
            return false;
        }
    }

    String getImsSmsFormat(int subscription) {
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return "unknown";
            }
            String format = iccISms.getImsSmsFormat(subscription);
            return format;
        } catch (RemoteException e) {
            return "unknown";
        }
    }

    public int getPreferredSmsSubscription() {
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            return iccISms.getPreferredSmsSubscription();
        } catch (RemoteException e) {
            return 0;
        } catch (NullPointerException e2) {
            return 0;
        }
    }

    public boolean isSMSPromptEnabled() {
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            return iccISms.isSMSPromptEnabled();
        } catch (RemoteException e) {
            return false;
        } catch (NullPointerException e2) {
            return false;
        }
    }

    public static int getSmsCapacityOnIcc(int subscription) {
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms == null) {
                return -1;
            }
            int ret = iccISms.getSmsCapacityOnIcc(subscription);
            return ret;
        } catch (RemoteException e) {
            return -1;
        }
    }
}
