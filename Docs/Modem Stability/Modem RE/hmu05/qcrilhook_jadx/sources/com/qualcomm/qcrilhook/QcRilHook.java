package com.qualcomm.qcrilhook;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.util.Log;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.uicc.IccUtils;
import com.qualcomm.qcrilmsgtunnel.IQcrilMsgTunnel;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class QcRilHook implements IQcRilHook {
    public static final String ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW = "android.intent.action.ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW";
    private static final int AVOIDANCE_BUFF_LEN = 164;
    private static final int BYTE_SIZE = 1;
    private static final int INT_SIZE = 4;
    private static final String LOG_TAG = "QC_RIL_OEM_HOOK";
    private static final int MAX_PDC_ID_LEN = 124;
    private static final int MAX_SPC_LEN = 6;
    public static final String QCRIL_MSG_TUNNEL_PACKAGE_NAME = "com.qualcomm.qcrilmsgtunnel";
    public static final String QCRIL_MSG_TUNNEL_SERVICE_NAME = "com.qualcomm.qcrilmsgtunnel.QcrilMsgTunnelService";
    private static final int RESPONSE_BUFFER_SIZE = 2048;
    private static RegistrantList mRegistrants;
    private boolean mBound;
    private Context mContext;
    private final int mHeaderSize;
    private BroadcastReceiver mIntentReceiver;
    private final String mOemIdentifier;
    private QcRilHookCallback mQcrilHookCb;
    private ServiceConnection mQcrilMsgTunnelConnection;
    private IQcrilMsgTunnel mService;

    public QcRilHook(Context context, QcRilHookCallback cb) {
        this(context);
        this.mQcrilHookCb = cb;
    }

    @Deprecated
    public QcRilHook(Context context) {
        this.mOemIdentifier = QmiOemHookConstants.OEM_IDENTIFIER;
        this.mHeaderSize = QmiOemHookConstants.OEM_IDENTIFIER.length() + 8;
        this.mService = null;
        this.mBound = false;
        this.mQcrilHookCb = null;
        this.mIntentReceiver = new BroadcastReceiver() { // from class: com.qualcomm.qcrilhook.QcRilHook.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                String action = intent.getAction();
                if (action.equals(QcRilHook.ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW)) {
                    Log.d(QcRilHook.LOG_TAG, "Received Broadcast Intent ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW");
                    byte[] payload = intent.getByteArrayExtra("payload");
                    if (payload != null) {
                        if (payload.length < QcRilHook.this.mHeaderSize) {
                            Log.e(QcRilHook.LOG_TAG, "UNSOL_RESPONSE_OEM_HOOK_RAW incomplete header");
                            Log.e(QcRilHook.LOG_TAG, "Expected " + QcRilHook.this.mHeaderSize + " bytes. Received " + payload.length + " bytes.");
                            return;
                        }
                        ByteBuffer response = QcRilHook.createBufferWithNativeByteOrder(payload);
                        byte[] oem_id_bytes = new byte[QmiOemHookConstants.OEM_IDENTIFIER.length()];
                        response.get(oem_id_bytes);
                        String oem_id_str = new String(oem_id_bytes);
                        Log.d(QcRilHook.LOG_TAG, "Oem ID in QCRILHOOK UNSOL RESP is " + oem_id_str);
                        if (!oem_id_str.equals(QmiOemHookConstants.OEM_IDENTIFIER)) {
                            Log.w(QcRilHook.LOG_TAG, "Incorrect Oem ID in QCRILHOOK UNSOL RESP. Expected QOEMHOOK. Received " + oem_id_str);
                            return;
                        }
                        int remainingSize = payload.length - QmiOemHookConstants.OEM_IDENTIFIER.length();
                        if (remainingSize > 0) {
                            byte[] remainingPayload = new byte[remainingSize];
                            response.get(remainingPayload);
                            AsyncResult ar = new AsyncResult((Object) null, remainingPayload, (Throwable) null);
                            QcRilHook.notifyRegistrants(ar);
                            return;
                        }
                        return;
                    }
                    return;
                }
                Log.w(QcRilHook.LOG_TAG, "Received Unknown Intent: action = " + action);
            }
        };
        this.mQcrilMsgTunnelConnection = new ServiceConnection() { // from class: com.qualcomm.qcrilhook.QcRilHook.3
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName name, IBinder service) {
                QcRilHook.this.mService = IQcrilMsgTunnel.Stub.asInterface(service);
                if (QcRilHook.this.mService == null) {
                    Log.e(QcRilHook.LOG_TAG, "QcrilMsgTunnelService Connect Failed (onServiceConnected)");
                } else {
                    Log.d(QcRilHook.LOG_TAG, "QcrilMsgTunnelService Connected Successfully (onServiceConnected)");
                }
                QcRilHook.this.mBound = true;
                if (QcRilHook.this.mQcrilHookCb != null) {
                    Log.d(QcRilHook.LOG_TAG, "Calling onQcRilHookReady callback");
                    QcRilHook.this.mQcrilHookCb.onQcRilHookReady();
                }
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName name) {
                Log.d(QcRilHook.LOG_TAG, "The connection to the service got disconnected unexpectedly!");
                QcRilHook.this.mService = null;
                QcRilHook.this.mBound = false;
            }
        };
        mRegistrants = new RegistrantList();
        this.mContext = context;
        Intent intent = new Intent();
        intent.setClassName(QCRIL_MSG_TUNNEL_PACKAGE_NAME, QCRIL_MSG_TUNNEL_SERVICE_NAME);
        Log.d(LOG_TAG, "Starting QcrilMsgTunnel Service");
        this.mContext.startService(intent);
        this.mContext.bindService(intent, this.mQcrilMsgTunnelConnection, 1);
        Log.d(LOG_TAG, "The QcrilMsgTunnelService will be connected soon ");
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW);
            this.mContext.registerReceiver(this.mIntentReceiver, filter);
            Log.d(LOG_TAG, "Registering for intent ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Uncaught Exception while while registering ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW intent. Reason: " + e);
        }
    }

    public void dispose() {
        if (this.mContext != null) {
            if (this.mBound) {
                Log.v(LOG_TAG, "dispose(): Unbinding service");
                this.mContext.unbindService(this.mQcrilMsgTunnelConnection);
                this.mBound = false;
            }
            Log.v(LOG_TAG, "dispose(): Unregistering receiver");
            this.mContext.unregisterReceiver(this.mIntentReceiver);
        }
    }

    public static ByteBuffer createBufferWithNativeByteOrder(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.nativeOrder());
        return buf;
    }

    private void addQcRilHookHeader(ByteBuffer buf, int requestId, int requestSize) {
        buf.put(QmiOemHookConstants.OEM_IDENTIFIER.getBytes());
        buf.putInt(requestId);
        buf.putInt(requestSize);
    }

    private AsyncResult sendRilOemHookMsg(int requestId, byte[] request) {
        return sendRilOemHookMsg(requestId, request, 0);
    }

    private AsyncResult sendRilOemHookMsg(int requestId, byte[] request, int sub) {
        byte[] response = new byte[2048];
        Log.v(LOG_TAG, "sendRilOemHookMsg: Outgoing Data is " + IccUtils.bytesToHexString(request));
        try {
            int retVal = this.mService.sendOemRilRequestRaw(request, response, sub);
            Log.d(LOG_TAG, "sendOemRilRequestRaw returns value = " + retVal);
            if (retVal >= 0) {
                byte[] validResponseBytes = null;
                if (retVal > 0) {
                    validResponseBytes = new byte[retVal];
                    System.arraycopy(response, 0, validResponseBytes, 0, retVal);
                }
                AsyncResult ar = new AsyncResult(Integer.valueOf(retVal), validResponseBytes, (Throwable) null);
                return ar;
            }
            CommandException ex = CommandException.fromRilErrno(retVal * (-1));
            AsyncResult ar2 = new AsyncResult(request, (Object) null, ex);
            return ar2;
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "sendOemRilRequestRaw RequestID = " + requestId + " exception, unable to send RIL request from this application", e);
            AsyncResult ar3 = new AsyncResult(Integer.valueOf(requestId), (Object) null, e);
            return ar3;
        } catch (NullPointerException e2) {
            Log.e(LOG_TAG, "NullPointerException caught at sendOemRilRequestRaw.RequestID = " + requestId + ". Return Error");
            AsyncResult ar4 = new AsyncResult(Integer.valueOf(requestId), (Object) null, e2);
            return ar4;
        }
    }

    private void sendRilOemHookMsgAsync(int requestId, byte[] request, IOemHookCallback oemHookCb, int sub) throws NullPointerException {
        Log.v(LOG_TAG, "sendRilOemHookMsgAsync: Outgoing Data is " + IccUtils.bytesToHexString(request));
        try {
            this.mService.sendOemRilRequestRawAsync(request, oemHookCb, sub);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "sendOemRilRequestRawAsync RequestID = " + requestId + " exception, unable to send RIL request from this application", e);
        } catch (NullPointerException e2) {
            Log.e(LOG_TAG, "NullPointerException caught at sendOemRilRequestRawAsync.RequestID = " + requestId + ". Throw to the caller");
            throw e2;
        }
    }

    public String qcRilGetConfig(int sub) {
        byte[] payload = new byte[this.mHeaderSize + 4];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(payload);
        addQcRilHookHeader(reqBuffer, IQcRilHook.QCRIL_EVT_HOOK_GET_CONFIG, 4);
        reqBuffer.putInt(sub);
        AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_GET_CONFIG, payload);
        if (ar.exception != null) {
            Log.w(LOG_TAG, "QCRIL_EVT_HOOK_GET_CONFIG failed w/ " + ar.exception);
            return null;
        }
        if (ar.result == null) {
            Log.w(LOG_TAG, "QCRIL_EVT_HOOK_GET_CONFIG failed w/ null result");
            return null;
        }
        String result = new String((byte[]) ar.result);
        Log.v(LOG_TAG, "QCRIL_EVT_HOOK_GET_CONFIG returned w/ " + result);
        return result;
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public String qcRilGetConfig() {
        return qcRilGetConfig(0);
    }

    public boolean qcRilSetConfig(String file, String config, int subMask) {
        if (!config.isEmpty() && config.length() <= MAX_PDC_ID_LEN && !file.isEmpty()) {
            byte[] payload = new byte[this.mHeaderSize + 3 + file.length() + config.length()];
            ByteBuffer buf = createBufferWithNativeByteOrder(payload);
            addQcRilHookHeader(buf, IQcRilHook.QCRIL_EVT_HOOK_SET_CONFIG, file.length() + 3 + config.length());
            buf.put((byte) subMask);
            buf.put(file.getBytes());
            buf.put((byte) 0);
            buf.put(config.getBytes());
            buf.put((byte) 0);
            AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_SET_CONFIG, payload);
            if (ar.exception != null) {
                Log.e(LOG_TAG, "QCRIL_EVT_HOOK_SET_CONFIG failed w/ " + ar.exception);
                return false;
            }
            return true;
        }
        Log.e(LOG_TAG, "set with incorrect config id: " + config);
        return false;
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public boolean qcRilSetConfig(String file) {
        return qcRilSetConfig(file, file, 1);
    }

    public boolean qcRilSetConfig(String file, int subMask) {
        return qcRilSetConfig(file, file, subMask);
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public String[] qcRilGetAvailableConfigs(String device) {
        String[] result = null;
        AsyncResult ar = sendQcRilHookMsg(IQcRilHook.QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS);
        if (ar.exception != null) {
            Log.w(LOG_TAG, "QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS failed w/ " + ar.exception);
            return null;
        }
        if (ar.result == null) {
            Log.e(LOG_TAG, "QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS failed w/ null result");
            return null;
        }
        Log.v(LOG_TAG, "QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS raw: " + Arrays.toString((byte[]) ar.result));
        try {
            ByteBuffer payload = ByteBuffer.wrap((byte[]) ar.result);
            payload.order(ByteOrder.nativeOrder());
            int numStrings = payload.get();
            Log.d(LOG_TAG, "QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS success: " + numStrings);
            if (numStrings <= 0) {
                Log.e(LOG_TAG, "QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS failed w/invalid payload, numStrings = 0");
                return null;
            }
            result = new String[numStrings];
            for (int i = 0; i < numStrings; i++) {
                int i2 = payload.get();
                byte[] data = new byte[i2];
                payload.get(data);
                result[i] = new String(data);
                Log.d(LOG_TAG, "QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS string " + i2 + " " + result[i]);
            }
            return result;
        } catch (BufferUnderflowException e) {
            Log.e(LOG_TAG, "QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS failed to parse payload w/ " + e);
        }
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public boolean qcRilCleanupConfigs() {
        AsyncResult ar = sendQcRilHookMsg(IQcRilHook.QCRIL_EVT_HOOK_DELETE_ALL_CONFIGS);
        if (ar.exception == null) {
            return true;
        }
        Log.e(LOG_TAG, "QCRIL_EVT_HOOK_DELETE_ALL_CONFIGS failed w/ " + ar.exception);
        return false;
    }

    public boolean qcRilDeactivateConfigs() {
        AsyncResult ar = sendQcRilHookMsg(IQcRilHook.QCRIL_EVT_HOOK_DEACT_CONFIGS);
        if (ar.exception == null) {
            return true;
        }
        Log.e(LOG_TAG, "QCRIL_EVT_HOOK_DEACT_CONFIGS failed w/ " + ar.exception);
        return false;
    }

    public boolean qcRilSelectConfig(String config, int subMask) {
        if (!config.isEmpty() && config.length() <= MAX_PDC_ID_LEN) {
            byte[] payload = new byte[this.mHeaderSize + 1 + config.getBytes().length];
            ByteBuffer buf = createBufferWithNativeByteOrder(payload);
            addQcRilHookHeader(buf, IQcRilHook.QCRIL_EVT_HOOK_SEL_CONFIG, config.getBytes().length + 1);
            buf.put((byte) subMask);
            buf.put(config.getBytes());
            AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_SEL_CONFIG, payload);
            if (ar.exception != null) {
                Log.e(LOG_TAG, "QCRIL_EVT_HOOK_SEL_CONFIG failed w/ " + ar.exception);
                return false;
            }
            return true;
        }
        Log.e(LOG_TAG, "select with incorrect config id: " + config);
        return false;
    }

    public String qcRilGetMetaInfoForConfig(String config) {
        String result = null;
        if (!config.isEmpty() && config.length() <= MAX_PDC_ID_LEN) {
            byte[] payload = new byte[this.mHeaderSize + config.getBytes().length];
            ByteBuffer buf = createBufferWithNativeByteOrder(payload);
            addQcRilHookHeader(buf, IQcRilHook.QCRIL_EVT_HOOK_GET_META_INFO, config.getBytes().length);
            buf.put(config.getBytes());
            AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_GET_META_INFO, payload);
            if (ar.exception != null) {
                Log.w(LOG_TAG, "QCRIL_EVT_HOOK_GET_META_INFO failed w/ " + ar.exception);
                return null;
            }
            if (ar.result == null) {
                Log.w(LOG_TAG, "QCRIL_EVT_HOOK_GET_META_INFO failed w/ null result");
                return null;
            }
            result = new String((byte[]) ar.result);
            Log.v(LOG_TAG, "QCRIL_EVT_HOOK_GET_META_INFO returned w/ " + result);
        } else {
            Log.e(LOG_TAG, "get meta info with incorrect config id: " + config);
        }
        return result;
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public boolean qcRilGoDormant(String interfaceName) {
        AsyncResult result = sendQcRilHookMsg(IQcRilHook.QCRILHOOK_GO_DORMANT, interfaceName);
        if (result.exception == null) {
            return true;
        }
        Log.w(LOG_TAG, "Go Dormant Command returned Exception: " + result.exception);
        return false;
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public boolean qcRilSetCdmaSubSrcWithSpc(int cdmaSubscription, String spc) {
        Log.v(LOG_TAG, "qcRilSetCdmaSubSrcWithSpc: Set Cdma Subscription to " + cdmaSubscription);
        if (!spc.isEmpty() && spc.length() <= 6) {
            byte[] payload = new byte[spc.length() + 1];
            ByteBuffer buf = createBufferWithNativeByteOrder(payload);
            buf.put((byte) cdmaSubscription);
            buf.put(spc.getBytes());
            AsyncResult ar = sendQcRilHookMsg(IQcRilHook.QCRIL_EVT_HOOK_SET_CDMA_SUB_SRC_WITH_SPC, payload);
            if (ar.exception == null) {
                if (ar.result == null) {
                    return false;
                }
                byte[] result = (byte[]) ar.result;
                ByteBuffer byteBuf = ByteBuffer.wrap(result);
                byte succeed = byteBuf.get();
                Log.v(LOG_TAG, "QCRIL Set Cdma Subscription Source Command " + (succeed == 1 ? "Succeed." : "Failed."));
                if (succeed == 1) {
                    return true;
                }
                return false;
            }
            Log.e(LOG_TAG, "QCRIL Set Cdma Subscription Source Command returned Exception: " + ar.exception);
            return false;
        }
        Log.e(LOG_TAG, "QCRIL Set Cdma Subscription Source Command incorrect SPC: " + spc);
        return false;
    }

    public boolean qcRilInformShutDown(int sub) {
        Log.d(LOG_TAG, "QCRIL Inform shutdown for SUB" + sub);
        OemHookCallback oemHookCb = new OemHookCallback(null) { // from class: com.qualcomm.qcrilhook.QcRilHook.2
            @Override // com.qualcomm.qcrilhook.OemHookCallback, com.qualcomm.qcrilhook.IOemHookCallback
            public void onOemHookResponse(byte[] response) throws RemoteException {
                Log.d(QcRilHook.LOG_TAG, "QCRIL Inform shutdown DONE!");
            }
        };
        sendQcRilHookMsgAsync(IQcRilHook.QCRIL_EVT_HOOK_INFORM_SHUTDOWN, null, oemHookCb, sub);
        return true;
    }

    public boolean qcRilCdmaAvoidCurNwk() {
        AsyncResult ar = sendQcRilHookMsg(IQcRilHook.QCRIL_EVT_HOOK_CDMA_AVOID_CUR_NWK);
        if (ar.exception == null) {
            return true;
        }
        Log.e(LOG_TAG, "QCRIL Avoid the current cdma network Command returned Exception: " + ar.exception);
        return false;
    }

    public boolean qcRilSetFieldTestMode(int sub, byte ratType, int enable) {
        byte[] request = new byte[this.mHeaderSize + 8];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, IQcRilHook.QCRIL_EVT_HOOK_ENABLE_ENGINEER_MODE, 0);
        reqBuffer.putInt(ratType);
        reqBuffer.putInt(enable);
        Log.d(LOG_TAG, "enable = " + enable + "ratType =" + ((int) ratType));
        AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_ENABLE_ENGINEER_MODE, request, sub);
        if (ar.exception == null) {
            return true;
        }
        Log.e(LOG_TAG, "QCRIL enable engineer mode cmd returned exception: " + ar.exception);
        return false;
    }

    public boolean qcRilCdmaClearAvoidanceList() {
        AsyncResult ar = sendQcRilHookMsg(IQcRilHook.QCRIL_EVT_HOOK_CDMA_CLEAR_AVOIDANCE_LIST);
        if (ar.exception == null) {
            return true;
        }
        Log.e(LOG_TAG, "QCRIL Clear the cdma avoidance list Command returned Exception: " + ar.exception);
        return false;
    }

    public byte[] qcRilCdmaGetAvoidanceList() {
        AsyncResult ar = sendQcRilHookMsg(IQcRilHook.QCRIL_EVT_HOOK_CDMA_GET_AVOIDANCE_LIST);
        if (ar.exception == null) {
            if (ar.result != null) {
                byte[] result = (byte[]) ar.result;
                if (result.length == AVOIDANCE_BUFF_LEN) {
                    return result;
                }
                Log.e(LOG_TAG, "QCRIL Get unexpected cdma avoidance list buffer length: " + result.length);
                return null;
            }
            Log.e(LOG_TAG, "QCRIL Get cdma avoidance list command returned a null result.");
            return null;
        }
        Log.e(LOG_TAG, "QCRIL Get the cdma avoidance list Command returned Exception: " + ar.exception);
        return null;
    }

    public boolean qcRilPerformIncrManualScan(int sub) {
        byte[] request = new byte[this.mHeaderSize];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, IQcRilHook.QCRIL_EVT_HOOK_PERFORM_INCREMENTAL_NW_SCAN, sub);
        AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_PERFORM_INCREMENTAL_NW_SCAN, request, sub);
        if (ar.exception == null) {
            return true;
        }
        Log.e(LOG_TAG, "QCRIL perform incr manual scan returned exception " + ar.exception);
        return false;
    }

    public boolean qcrilSetBuiltInPLMNList(byte[] payload, int sub) {
        boolean retval = false;
        if (payload == null) {
            Log.e(LOG_TAG, "payload is null");
            return false;
        }
        byte[] request = new byte[this.mHeaderSize + payload.length];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, IQcRilHook.QCRIL_EVT_HOOK_SET_BUILTIN_PLMN_LIST, payload.length);
        reqBuffer.put(payload);
        AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_SET_BUILTIN_PLMN_LIST, request, sub);
        if (ar.exception == null) {
            retval = true;
        } else {
            Log.e(LOG_TAG, "QCRIL set builtin PLMN list returned exception: " + ar.exception);
        }
        return retval;
    }

    public boolean qcRilSetPreferredNetworkAcqOrder(int acqOrder, int sub) {
        byte[] request = new byte[this.mHeaderSize + 4];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        Log.d(LOG_TAG, "acq order: " + acqOrder);
        addQcRilHookHeader(reqBuffer, IQcRilHook.QCRIL_EVT_HOOK_SET_PREFERRED_NETWORK_ACQ_ORDER, 4);
        reqBuffer.putInt(acqOrder);
        AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_SET_PREFERRED_NETWORK_ACQ_ORDER, request, sub);
        if (ar.exception == null) {
            return true;
        }
        Log.e(LOG_TAG, "QCRIL set acq order cmd returned exception: " + ar.exception);
        return false;
    }

    public byte qcRilGetPreferredNetworkAcqOrder(int sub) {
        byte[] request = new byte[this.mHeaderSize];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, IQcRilHook.QCRIL_EVT_HOOK_GET_PREFERRED_NETWORK_ACQ_ORDER, 4);
        AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_GET_PREFERRED_NETWORK_ACQ_ORDER, request, sub);
        if (ar.exception == null) {
            if (ar.result != null) {
                byte[] result = (byte[]) ar.result;
                ByteBuffer byteBuf = ByteBuffer.wrap(result);
                byte acq_order = byteBuf.get();
                Log.v(LOG_TAG, "acq order is " + ((int) acq_order));
                return acq_order;
            }
            Log.e(LOG_TAG, "no acq order result return");
            return (byte) 0;
        }
        Log.e(LOG_TAG, "QCRIL set acq order cmd returned exception: " + ar.exception);
        return (byte) 0;
    }

    public boolean qcRilSetPreferredNetworkBandPref(int bandPref, int sub) {
        byte[] request = new byte[this.mHeaderSize + 4];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        Log.d(LOG_TAG, "band pref: " + bandPref);
        addQcRilHookHeader(reqBuffer, IQcRilHook.QCRIL_EVT_HOOK_SET_PREFERRED_NETWORK_BAND_PREF, 4);
        reqBuffer.putInt(bandPref);
        AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_SET_PREFERRED_NETWORK_BAND_PREF, request, sub);
        if (ar.exception == null) {
            return true;
        }
        Log.e(LOG_TAG, "QCRIL set band pref cmd returned exception: " + ar.exception);
        return false;
    }

    public byte qcRilGetPreferredNetworkBandPref(int bandType, int sub) {
        byte[] request = new byte[this.mHeaderSize];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, IQcRilHook.QCRIL_EVT_HOOK_GET_PREFERRED_NETWORK_BAND_PREF, 4);
        reqBuffer.putInt(bandType);
        AsyncResult ar = sendRilOemHookMsg(IQcRilHook.QCRIL_EVT_HOOK_GET_PREFERRED_NETWORK_BAND_PREF, request, sub);
        if (ar.exception == null) {
            if (ar.result != null) {
                byte[] result = (byte[]) ar.result;
                ByteBuffer byteBuf = ByteBuffer.wrap(result);
                byte band_pref = byteBuf.get();
                Log.v(LOG_TAG, "band pref is " + ((int) band_pref));
                return band_pref;
            }
            Log.e(LOG_TAG, "no band pref result return");
            return (byte) 0;
        }
        Log.e(LOG_TAG, "QCRIL get band perf cmd returned exception: " + ar.exception);
        return (byte) 0;
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public AsyncResult sendQcRilHookMsg(int requestId) {
        byte[] request = new byte[this.mHeaderSize];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, requestId, 0);
        return sendRilOemHookMsg(requestId, request);
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public AsyncResult sendQcRilHookMsg(int requestId, byte payload) {
        byte[] request = new byte[this.mHeaderSize + 1];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, requestId, 1);
        reqBuffer.put(payload);
        return sendRilOemHookMsg(requestId, request);
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public AsyncResult sendQcRilHookMsg(int requestId, byte[] payload) {
        byte[] request = new byte[this.mHeaderSize + payload.length];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, requestId, payload.length);
        reqBuffer.put(payload);
        return sendRilOemHookMsg(requestId, request);
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public AsyncResult sendQcRilHookMsg(int requestId, int payload) {
        byte[] request = new byte[this.mHeaderSize + 4];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, requestId, 4);
        reqBuffer.putInt(payload);
        return sendRilOemHookMsg(requestId, request);
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public AsyncResult sendQcRilHookMsg(int requestId, String payload) {
        byte[] request = new byte[this.mHeaderSize + payload.length()];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, requestId, payload.length());
        reqBuffer.put(payload.getBytes());
        return sendRilOemHookMsg(requestId, request);
    }

    public void sendQcRilHookMsgAsync(int requestId, byte[] payload, OemHookCallback oemHookCb) {
        sendQcRilHookMsgAsync(requestId, payload, oemHookCb, 0);
    }

    public void sendQcRilHookMsgAsync(int requestId, byte[] payload, OemHookCallback oemHookCb, int sub) {
        int payloadLength = 0;
        if (payload != null) {
            payloadLength = payload.length;
        }
        byte[] request = new byte[this.mHeaderSize + payloadLength];
        ByteBuffer reqBuffer = createBufferWithNativeByteOrder(request);
        addQcRilHookHeader(reqBuffer, requestId, payloadLength);
        if (payload != null) {
            reqBuffer.put(payload);
        }
        sendRilOemHookMsgAsync(requestId, request, oemHookCb, sub);
    }

    public static void register(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (mRegistrants) {
            mRegistrants.add(r);
        }
    }

    public static void unregister(Handler h) {
        synchronized (mRegistrants) {
            mRegistrants.remove(h);
        }
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public void registerForFieldTestData(Handler h, int what, Object obj) {
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public void unregisterForFieldTestData(Handler h) {
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public void registerForExtendedDbmIntl(Handler h, int what, Object obj) {
    }

    @Override // com.qualcomm.qcrilhook.IQcRilHook
    public void unregisterForExtendedDbmIntl(Handler h) {
    }

    protected void finalize() {
        Log.v(LOG_TAG, "is destroyed");
    }

    public static void notifyRegistrants(AsyncResult ar) {
        if (mRegistrants != null) {
            mRegistrants.notifyRegistrants(ar);
        } else {
            Log.e(LOG_TAG, "QcRilOemHook notifyRegistrants Failed");
        }
    }
}
