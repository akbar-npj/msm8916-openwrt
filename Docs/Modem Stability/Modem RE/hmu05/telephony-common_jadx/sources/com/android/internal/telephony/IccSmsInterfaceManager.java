package com.android.internal.telephony;

import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.util.Log;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.util.HexDump;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class IccSmsInterfaceManager extends ISms.Stub {
    protected static final boolean DBG = true;
    private static final int EVENT_LOAD_DONE = 1;
    protected static final int EVENT_SET_BROADCAST_ACTIVATION_DONE = 3;
    protected static final int EVENT_SET_BROADCAST_CONFIG_DONE = 4;
    private static final int EVENT_UPDATE_DONE = 2;
    protected static final String LOG_TAG = "IccSmsInterfaceManager";
    private static final int SMS_CB_CODE_SCHEME_MAX = 255;
    private static final int SMS_CB_CODE_SCHEME_MIN = 0;
    protected final AppOpsManager mAppOps;
    protected final Context mContext;
    protected SMSDispatcher mDispatcher;
    protected PhoneBase mPhone;
    private List<SmsRawData> mSms;
    protected boolean mSuccess;
    protected final Object mLock = new Object();
    private CellBroadcastRangeManager mCellBroadcastRangeManager = new CellBroadcastRangeManager();
    private CdmaBroadcastRangeManager mCdmaBroadcastRangeManager = new CdmaBroadcastRangeManager();
    protected Handler mHandler = new Handler() { // from class: com.android.internal.telephony.IccSmsInterfaceManager.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccSmsInterfaceManager.this.mSms = IccSmsInterfaceManager.this.buildValidRawData((ArrayList) ar.result);
                            IccSmsInterfaceManager.this.markMessagesAsRead((ArrayList) ar.result);
                        } else {
                            if (Rlog.isLoggable("SMS", 3)) {
                                IccSmsInterfaceManager.this.log("Cannot load Sms records");
                            }
                            if (IccSmsInterfaceManager.this.mSms != null) {
                                IccSmsInterfaceManager.this.mSms.clear();
                            }
                        }
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                        break;
                    }
                    return;
                case 2:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        IccSmsInterfaceManager.this.mSuccess = ar2.exception == null;
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                        break;
                    }
                    return;
                case 3:
                case 4:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    synchronized (IccSmsInterfaceManager.this.mLock) {
                        IccSmsInterfaceManager.this.mSuccess = ar3.exception == null;
                        IccSmsInterfaceManager.this.mLock.notifyAll();
                        break;
                    }
                    return;
                default:
                    return;
            }
        }
    };

    /* JADX WARN: Multi-variable type inference failed */
    protected IccSmsInterfaceManager(PhoneBase phone) {
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mAppOps = (AppOpsManager) this.mContext.getSystemService("appops");
        initDispatchers();
        if (ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    protected void initDispatchers() {
        Log.d(LOG_TAG, "IccSmsInterfaceManager: initDispatchers()");
        this.mDispatcher = new ImsSMSDispatcher(this.mPhone, this.mPhone.mSmsStorageMonitor, this.mPhone.mSmsUsageMonitor);
    }

    protected void markMessagesAsRead(ArrayList<byte[]> messages) {
        if (messages != null) {
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh == null) {
                if (Rlog.isLoggable("SMS", 3)) {
                    log("markMessagesAsRead - aborting, no icc card present.");
                    return;
                }
                return;
            }
            int count = messages.size();
            for (int i = 0; i < count; i++) {
                byte[] ba = messages.get(i);
                if (ba[0] == 3) {
                    int n = ba.length;
                    byte[] nba = new byte[n - 1];
                    System.arraycopy(ba, 1, nba, 0, n - 1);
                    byte[] record = makeSmsRecordData(1, nba);
                    fh.updateEFLinearFixed(IccConstants.EF_SMS, i + 1, record, null, null);
                    if (Rlog.isLoggable("SMS", 3)) {
                        log("SMS " + (i + 1) + " marked as read");
                    }
                }
            }
        }
    }

    protected void updatePhoneObject(PhoneBase phone) {
        this.mPhone = phone;
        this.mDispatcher.updatePhoneObject(phone);
    }

    protected void enforceReceiveAndSend(String message) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", message);
        this.mContext.enforceCallingOrSelfPermission("android.permission.SEND_SMS", message);
    }

    public boolean updateMessageOnIccEf(String callingPackage, int index, int status, byte[] pdu) {
        log("updateMessageOnIccEf: index=" + index + " status=" + status + " ==> (" + Arrays.toString(pdu) + ")");
        enforceReceiveAndSend("Updating message on Icc");
        if (this.mAppOps.noteOp(22, Binder.getCallingUid(), callingPackage) != 0) {
            return false;
        }
        synchronized (this.mLock) {
            this.mSuccess = false;
            Message response = this.mHandler.obtainMessage(2);
            if (status == 0) {
                if (1 == this.mPhone.getPhoneType()) {
                    this.mPhone.mCi.deleteSmsOnSim(index, response);
                } else {
                    this.mPhone.mCi.deleteSmsOnRuim(index, response);
                }
            } else {
                IccFileHandler fh = this.mPhone.getIccFileHandler();
                if (fh == null) {
                    response.recycle();
                    return this.mSuccess;
                }
                byte[] record = makeSmsRecordData(status, pdu);
                fh.updateEFLinearFixed(IccConstants.EF_SMS, index, record, null, response);
            }
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
            return this.mSuccess;
        }
    }

    public boolean copyMessageToIccEf(String callingPackage, int status, byte[] pdu, byte[] smsc) {
        log("copyMessageToIccEf: status=" + status + " ==> pdu=(" + Arrays.toString(pdu) + "), smsc=(" + Arrays.toString(smsc) + ")");
        enforceReceiveAndSend("Copying message to Icc");
        if (this.mAppOps.noteOp(22, Binder.getCallingUid(), callingPackage) != 0) {
            return false;
        }
        synchronized (this.mLock) {
            this.mSuccess = false;
            Message response = this.mHandler.obtainMessage(2);
            if (1 == this.mPhone.getPhoneType()) {
                this.mPhone.mCi.writeSmsToSim(status, IccUtils.bytesToHexString(smsc), IccUtils.bytesToHexString(pdu), response);
            } else {
                this.mPhone.mCi.writeSmsToRuim(status, IccUtils.bytesToHexString(pdu), response);
            }
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by index");
            }
        }
        return this.mSuccess;
    }

    public List<SmsRawData> getAllMessagesFromIccEf(String callingPackage) {
        log("getAllMessagesFromEF");
        this.mContext.enforceCallingOrSelfPermission("android.permission.RECEIVE_SMS", "Reading messages from Icc");
        if (this.mAppOps.noteOp(21, Binder.getCallingUid(), callingPackage) != 0) {
            return new ArrayList();
        }
        synchronized (this.mLock) {
            IccFileHandler fh = this.mPhone.getIccFileHandler();
            if (fh == null) {
                Rlog.e(LOG_TAG, "Cannot load Sms records. No icc card?");
                if (this.mSms != null) {
                    this.mSms.clear();
                    return this.mSms;
                }
            }
            Message response = this.mHandler.obtainMessage(1);
            fh.loadEFLinearFixedAll(IccConstants.EF_SMS, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to load from the Icc");
            }
            return this.mSms;
        }
    }

    public void sendData(String callingPackage, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendData: destAddr=" + destAddr + " scAddr=" + scAddr + " destPort=" + destPort + " data='" + HexDump.toHexString(data) + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendData(destAddr, scAddr, destPort, 0, data, sentIntent, deliveryIntent);
        }
    }

    public void sendDataWithOrigPort(String callingPackage, String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendDataWithOrigPort: destAddr=" + destAddr + " scAddr=" + scAddr + " destPort=" + destPort + "origPort=" + origPort + " data='" + HexDump.toHexString(data) + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendData(destAddr, scAddr, destPort, origPort, data, sentIntent, deliveryIntent);
        }
    }

    public void sendText(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, -1, false, -1);
        }
    }

    public void sendTextWithOptions(String callingPackage, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            log("sendText: destAddr=" + destAddr + " scAddr=" + scAddr + " text='" + text + "' sentIntent=" + sentIntent + " deliveryIntent=" + deliveryIntent + "validityPeriod" + validityPeriod);
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent, priority, isExpectMore, validityPeriod);
        }
    }

    public void sendMultipartText(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            int i = 0;
            for (String part : parts) {
                log("sendMultipartText: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + part);
                i++;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, -1, false, -1);
        }
    }

    public void sendMultipartTextWithOptions(String callingPackage, String destAddr, String scAddr, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, int priority, boolean isExpectMore, int validityPeriod) {
        this.mPhone.getContext().enforceCallingPermission("android.permission.SEND_SMS", "Sending SMS message");
        if (Rlog.isLoggable("SMS", 2)) {
            int i = 0;
            for (String part : parts) {
                log("sendMultipartText: destAddr=" + destAddr + ", srAddr=" + scAddr + ", part[" + i + "]=" + part);
                i++;
            }
        }
        if (this.mAppOps.noteOp(20, Binder.getCallingUid(), callingPackage) == 0) {
            this.mDispatcher.sendMultipartText(destAddr, scAddr, (ArrayList) parts, (ArrayList) sentIntents, (ArrayList) deliveryIntents, priority, isExpectMore, validityPeriod);
        }
    }

    public int getPremiumSmsPermission(String packageName) {
        return this.mDispatcher.getPremiumSmsPermission(packageName);
    }

    public void setPremiumSmsPermission(String packageName, int permission) {
        this.mDispatcher.setPremiumSmsPermission(packageName, permission);
    }

    protected ArrayList<SmsRawData> buildValidRawData(ArrayList<byte[]> messages) {
        int count = messages.size();
        ArrayList<SmsRawData> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] ba = messages.get(i);
            if (ba[0] == 0) {
                ret.add(null);
            } else {
                ret.add(new SmsRawData(messages.get(i)));
            }
        }
        return ret;
    }

    protected byte[] makeSmsRecordData(int status, byte[] pdu) {
        byte[] data;
        if (1 == this.mPhone.getPhoneType()) {
            data = new byte[IccConstants.SMS_RECORD_LENGTH];
        } else {
            data = new byte[255];
        }
        data[0] = (byte) (status & 7);
        System.arraycopy(pdu, 0, data, 1, pdu.length);
        for (int j = pdu.length + 1; j < data.length; j++) {
            data[j] = -1;
        }
        return data;
    }

    public boolean enableCellBroadcast(int messageIdentifier) {
        return enableCellBroadcastRange(messageIdentifier, messageIdentifier);
    }

    public boolean disableCellBroadcast(int messageIdentifier) {
        return disableCellBroadcastRange(messageIdentifier, messageIdentifier);
    }

    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        return 1 == this.mPhone.getPhoneType() ? enableGsmBroadcastRange(startMessageId, endMessageId) : enableCdmaBroadcastRange(startMessageId, endMessageId);
    }

    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        return 1 == this.mPhone.getPhoneType() ? disableGsmBroadcastRange(startMessageId, endMessageId) : disableCdmaBroadcastRange(startMessageId, endMessageId);
    }

    public synchronized boolean enableGsmBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("enableGsmBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (!this.mCellBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
                log("Failed to add cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            } else {
                log("Added cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                setCellBroadcastActivation(this.mCellBroadcastRangeManager.isEmpty() ? false : true);
                z = true;
            }
        }
        return z;
    }

    public synchronized boolean disableGsmBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("disableGsmBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (!this.mCellBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
                log("Failed to remove cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            } else {
                log("Removed cell broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                setCellBroadcastActivation(this.mCellBroadcastRangeManager.isEmpty() ? false : true);
                z = true;
            }
        }
        return z;
    }

    public synchronized boolean enableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("enableCdmaBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Enabling cdma broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (!this.mCdmaBroadcastRangeManager.enableRange(startMessageId, endMessageId, client)) {
                log("Failed to add cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            } else {
                log("Added cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                setCdmaBroadcastActivation(this.mCdmaBroadcastRangeManager.isEmpty() ? false : true);
                z = true;
            }
        }
        return z;
    }

    public synchronized boolean disableCdmaBroadcastRange(int startMessageId, int endMessageId) {
        boolean z = false;
        synchronized (this) {
            log("disableCdmaBroadcastRange");
            Context context = this.mPhone.getContext();
            context.enforceCallingPermission("android.permission.RECEIVE_SMS", "Disabling cell broadcast SMS");
            String client = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            if (!this.mCdmaBroadcastRangeManager.disableRange(startMessageId, endMessageId, client)) {
                log("Failed to remove cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
            } else {
                log("Removed cdma broadcast subscription for MID range " + startMessageId + " to " + endMessageId + " from client " + client);
                setCdmaBroadcastActivation(this.mCdmaBroadcastRangeManager.isEmpty() ? false : true);
                z = true;
            }
        }
        return z;
    }

    class CellBroadcastRangeManager extends IntRangeManager {
        private ArrayList<SmsBroadcastConfigInfo> mConfigList = new ArrayList<>();

        CellBroadcastRangeManager() {
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected void startUpdate() {
            this.mConfigList.clear();
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected void addRange(int startId, int endId, boolean selected) {
            this.mConfigList.add(new SmsBroadcastConfigInfo(startId, endId, 0, 255, selected));
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            SmsBroadcastConfigInfo[] configs = (SmsBroadcastConfigInfo[]) this.mConfigList.toArray(new SmsBroadcastConfigInfo[this.mConfigList.size()]);
            return IccSmsInterfaceManager.this.setCellBroadcastConfig(configs);
        }
    }

    class CdmaBroadcastRangeManager extends IntRangeManager {
        private ArrayList<CdmaSmsBroadcastConfigInfo> mConfigList = new ArrayList<>();

        CdmaBroadcastRangeManager() {
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected void startUpdate() {
            this.mConfigList.clear();
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected void addRange(int startId, int endId, boolean selected) {
            this.mConfigList.add(new CdmaSmsBroadcastConfigInfo(startId, endId, 1, selected));
        }

        @Override // com.android.internal.telephony.IntRangeManager
        protected boolean finishUpdate() {
            if (this.mConfigList.isEmpty()) {
                return true;
            }
            CdmaSmsBroadcastConfigInfo[] configs = (CdmaSmsBroadcastConfigInfo[]) this.mConfigList.toArray(new CdmaSmsBroadcastConfigInfo[this.mConfigList.size()]);
            return IccSmsInterfaceManager.this.setCdmaBroadcastConfig(configs);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean setCellBroadcastConfig(SmsBroadcastConfigInfo[] configs) {
        log("Calling setGsmBroadcastConfig with " + configs.length + " configurations");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(4);
            this.mSuccess = false;
            this.mPhone.mCi.setGsmBroadcastConfig(configs, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast config");
            }
        }
        return this.mSuccess;
    }

    private boolean setCellBroadcastActivation(boolean activate) {
        log("Calling setCellBroadcastActivation(" + activate + ')');
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(3);
            this.mSuccess = false;
            this.mPhone.mCi.setGsmBroadcastActivation(activate, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cell broadcast activation");
            }
        }
        return this.mSuccess;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs) {
        log("Calling setCdmaBroadcastConfig with " + configs.length + " configurations");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(4);
            this.mSuccess = false;
            this.mPhone.mCi.setCdmaBroadcastConfig(configs, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast config");
            }
        }
        return this.mSuccess;
    }

    private boolean setCdmaBroadcastActivation(boolean activate) {
        log("Calling setCdmaBroadcastActivation(" + activate + ")");
        synchronized (this.mLock) {
            Message response = this.mHandler.obtainMessage(3);
            this.mSuccess = false;
            this.mPhone.mCi.setCdmaBroadcastActivation(activate, response);
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to set cdma broadcast activation");
            }
        }
        return this.mSuccess;
    }

    protected void log(String msg) {
        Log.d(LOG_TAG, "[IccSmsInterfaceManager] " + msg);
    }

    public boolean isImsSmsSupported() {
        return this.mDispatcher.isIms();
    }

    public String getImsSmsFormat() {
        return this.mDispatcher.getImsSmsFormat();
    }

    public int getSmsCapacityOnIcc() {
        int numberOnIcc = -1;
        IccRecords ir = this.mPhone.getIccRecords();
        if (ir != null) {
            numberOnIcc = ir.getSmsCapacityOnIcc();
        } else {
            log("getSmsCapacityOnIcc - aborting, no icc card present.");
        }
        log("getSmsCapacityOnIcc().numberOnIcc = " + numberOnIcc);
        return numberOnIcc;
    }
}
