package com.qualcomm.qcrilhook;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class QmiOemHook extends Handler {
    private static final int RESERVED_SIZE = 8;
    private static final boolean enableVLog = true;
    private static QmiOemHook mInstance;
    private Context mContext;
    private QcRilHook mQcRilOemHook;
    private QcRilHookCallback mQcrilHookCb;
    int mResponseResult;
    public ByteBuffer respByteBuf;
    private static String LOG_TAG = "QMI_OEMHOOK";
    public static HashMap<Short, Registrant> serviceRegistrantsMap = new HashMap<>();
    private static RegistrantList sReadyCbRegistrantList = new RegistrantList();
    private static final int QMI_OEM_HOOK_UNSOL = 0;
    private static int mRefCount = QMI_OEM_HOOK_UNSOL;

    private QmiOemHook(Context context) {
        this.mResponseResult = QMI_OEM_HOOK_UNSOL;
        this.mQcrilHookCb = new QcRilHookCallback() { // from class: com.qualcomm.qcrilhook.QmiOemHook.1
            @Override // com.qualcomm.qcrilhook.QcRilHookCallback
            public void onQcRilHookReady() {
                AsyncResult ar = new AsyncResult((Object) null, Boolean.valueOf(QmiOemHook.enableVLog), (Throwable) null);
                Log.i(QmiOemHook.LOG_TAG, "onQcRilHookReadyCb notifying registrants");
                QmiOemHook.sReadyCbRegistrantList.notifyRegistrants(ar);
            }
        };
        this.mQcRilOemHook = new QcRilHook(context, this.mQcrilHookCb);
        QcRilHook.register(this, QMI_OEM_HOOK_UNSOL, null);
    }

    private QmiOemHook(Context context, Looper looper) {
        super(looper);
        this.mResponseResult = QMI_OEM_HOOK_UNSOL;
        this.mQcrilHookCb = new QcRilHookCallback() { // from class: com.qualcomm.qcrilhook.QmiOemHook.1
            @Override // com.qualcomm.qcrilhook.QcRilHookCallback
            public void onQcRilHookReady() {
                AsyncResult ar = new AsyncResult((Object) null, Boolean.valueOf(QmiOemHook.enableVLog), (Throwable) null);
                Log.i(QmiOemHook.LOG_TAG, "onQcRilHookReadyCb notifying registrants");
                QmiOemHook.sReadyCbRegistrantList.notifyRegistrants(ar);
            }
        };
        this.mQcRilOemHook = new QcRilHook(context, this.mQcrilHookCb);
        QcRilHook.register(this, QMI_OEM_HOOK_UNSOL, null);
    }

    public static synchronized QmiOemHook getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new QmiOemHook(context);
        }
        mRefCount++;
        return mInstance;
    }

    public static synchronized QmiOemHook getInstance(Context context, Looper looper) {
        if (mInstance == null) {
            mInstance = new QmiOemHook(context, looper);
        }
        mRefCount++;
        return mInstance;
    }

    public synchronized void dispose() {
        mRefCount--;
        if (mRefCount == 0) {
            vLog("dispose(): Unregistering QcRilHook and calling QcRilHook dispose");
            QcRilHook.unregister(this);
            this.mQcRilOemHook.dispose();
            mInstance = null;
            sReadyCbRegistrantList.removeCleared();
        } else {
            vLog("dispose mRefCount = " + mRefCount);
        }
    }

    private void vLog(String logString) {
        Log.v(LOG_TAG, logString);
    }

    public static void registerService(short serviceId, Handler h, int what) {
        Log.v(LOG_TAG, "Registering Service Id = " + ((int) serviceId) + " h = " + h + " what = " + what);
        synchronized (serviceRegistrantsMap) {
            serviceRegistrantsMap.put(Short.valueOf(serviceId), new Registrant(h, what, (Object) null));
        }
    }

    public static void registerOnReadyCb(Handler h, int what, Object obj) {
        Log.v(LOG_TAG, "Registering Service for OnQcRilHookReadyCb =  h = " + h + " what = " + what);
        synchronized (sReadyCbRegistrantList) {
            sReadyCbRegistrantList.add(new Registrant(h, what, obj));
        }
    }

    public static void unregisterService(int serviceId) {
        synchronized (serviceRegistrantsMap) {
            serviceRegistrantsMap.remove(Integer.valueOf(serviceId));
        }
    }

    public static void unregisterOnReadyCb(Handler h) {
        synchronized (sReadyCbRegistrantList) {
            sReadyCbRegistrantList.remove(h);
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case QMI_OEM_HOOK_UNSOL /* 0 */:
                Log.v(LOG_TAG, "Thread=" + Thread.currentThread().getName() + " received " + msg);
                Log.v(LOG_TAG, "QMI_OEM_HOOK_UNSOL received");
                AsyncResult ar = (AsyncResult) msg.obj;
                byte[] response = (byte[]) ar.result;
                receive(response, null, QmiOemHookConstants.ResponseType.IS_UNSOL);
                break;
        }
    }

    public static HashMap<Integer, Object> receive(byte[] payload, Message msg, QmiOemHookConstants.ResponseType responseType) {
        Log.v(LOG_TAG, "receive responseData = " + EmbmsOemHook.bytesToHexString(payload) + " message=" + msg + " responseType= " + responseType);
        ByteBuffer respByteBuf = ByteBuffer.wrap(payload);
        if (respByteBuf == null) {
            Log.v(LOG_TAG, "respByteBuf is null");
            return null;
        }
        respByteBuf.order(BaseQmiTypes.BaseQmiItemType.QMI_BYTE_ORDER);
        Log.v(LOG_TAG, "receive respByteBuf after ByteBuffer.wrap(payload) = " + EmbmsOemHook.bytesToHexString(respByteBuf.array()));
        Log.v(LOG_TAG, "receive respByteBuf = " + respByteBuf);
        int requestId = respByteBuf.getInt();
        int responseSize = respByteBuf.getInt();
        int successStatus = -1;
        if (!isValidQmiMessage(responseType, requestId)) {
            Log.e(LOG_TAG, "requestId NOT in QMI OemHook range, No further processing");
            return null;
        }
        if (responseSize > 0) {
            short serviceId = respByteBuf.getShort();
            short messageId = respByteBuf.getShort();
            int responseTlvSize = responseSize - 4;
            if (responseType != QmiOemHookConstants.ResponseType.IS_UNSOL) {
                successStatus = PrimitiveParser.toUnsigned(respByteBuf.getShort());
                responseTlvSize -= 2;
            }
            Log.v(LOG_TAG, "receive requestId=" + requestId + " responseSize=" + responseSize + " responseTlvSize=" + responseTlvSize + " serviceId=" + ((int) serviceId) + " messageId=" + ((int) messageId) + " successStatus = " + successStatus);
            HashMap<Integer, Object> hashMap = new HashMap<>();
            hashMap.put(1, Integer.valueOf(requestId));
            hashMap.put(2, Integer.valueOf(responseTlvSize));
            hashMap.put(7, Short.valueOf(serviceId));
            hashMap.put(8, Short.valueOf(messageId));
            hashMap.put(3, Integer.valueOf(successStatus));
            hashMap.put(4, msg);
            hashMap.put(5, responseType);
            hashMap.put(6, respByteBuf);
            if (responseType == QmiOemHookConstants.ResponseType.IS_UNSOL || responseType == QmiOemHookConstants.ResponseType.IS_ASYNC_RESPONSE) {
                AsyncResult ar = new AsyncResult((Object) null, hashMap, (Throwable) null);
                Registrant r = serviceRegistrantsMap.get(Short.valueOf(serviceId));
                if (r != null) {
                    Log.v(LOG_TAG, "Notifying registrant for responseType = " + responseType);
                    r.notifyRegistrant(ar);
                    return null;
                }
                Log.e(LOG_TAG, "Did not find the registered serviceId = " + ((int) serviceId));
            } else {
                return hashMap;
            }
        }
        return null;
    }

    private static boolean isValidQmiMessage(QmiOemHookConstants.ResponseType responseType, int requestId) {
        if (responseType == QmiOemHookConstants.ResponseType.IS_UNSOL) {
            if (requestId == 525388) {
                return enableVLog;
            }
            return false;
        }
        if (requestId != 524388) {
            return false;
        }
        return enableVLog;
    }

    private byte[] createPayload(short serviceId, short messageId, short[] types, BaseQmiTypes.BaseQmiItemType[] qmiItems) {
        int tlvSize = QMI_OEM_HOOK_UNSOL;
        if (qmiItems == null || types == null || qmiItems[QMI_OEM_HOOK_UNSOL] == null) {
            Log.v(LOG_TAG, "This message has no payload");
        } else {
            for (int i = QMI_OEM_HOOK_UNSOL; i < qmiItems.length; i++) {
                tlvSize += qmiItems[i].getSize() + 3;
            }
        }
        ByteBuffer buf = BaseQmiTypes.QmiBase.createByteBuffer(tlvSize + 12);
        buf.putInt(QMI_OEM_HOOK_UNSOL);
        buf.putInt(QMI_OEM_HOOK_UNSOL);
        buf.putShort(serviceId);
        buf.putShort(messageId);
        Log.v(LOG_TAG, "createPayload: serviceId= " + ((int) serviceId) + " messageId= " + ((int) messageId));
        if (qmiItems != null && types != null && qmiItems[QMI_OEM_HOOK_UNSOL] != null) {
            for (int i2 = QMI_OEM_HOOK_UNSOL; i2 < qmiItems.length; i2++) {
                vLog(qmiItems[i2].toString());
                buf.put(qmiItems[i2].toTlv(types[i2]));
                Log.v(LOG_TAG, "Intermediate buf in QmiOemHook sendQmiMessage Sync or Async = " + EmbmsOemHook.bytesToHexString(qmiItems[i2].toTlv(types[i2])));
            }
        }
        Log.v(LOG_TAG, "Byte buf in QmiOemHook createPayload = " + buf);
        return buf.array();
    }

    public byte[] sendQmiMessage(int serviceHook, short[] types, BaseQmiTypes.BaseQmiItemType[] qmiItems) throws IOException {
        int msgSize = QMI_OEM_HOOK_UNSOL;
        for (int i = QMI_OEM_HOOK_UNSOL; i < qmiItems.length; i++) {
            msgSize += qmiItems[i].getSize() + 3;
        }
        ByteBuffer buf = BaseQmiTypes.QmiBase.createByteBuffer(4 + msgSize);
        buf.putInt(QMI_OEM_HOOK_UNSOL);
        buf.putShort(PrimitiveParser.parseShort(msgSize));
        for (int i2 = QMI_OEM_HOOK_UNSOL; i2 < qmiItems.length; i2++) {
            vLog(qmiItems[i2].toString());
            buf.put(qmiItems[i2].toTlv(types[i2]));
        }
        AsyncResult result = this.mQcRilOemHook.sendQcRilHookMsg(serviceHook, buf.array());
        if (result.exception != null) {
            Log.w(LOG_TAG, String.format("sendQmiMessage() Failed : %s", result.exception.toString()));
            result.exception.printStackTrace();
            throw new IOException();
        }
        return (byte[]) result.result;
    }

    public HashMap<Integer, Object> sendQmiMessageSync(short serviceId, short messageId, short[] types, BaseQmiTypes.BaseQmiItemType[] qmiItems) throws IOException {
        AsyncResult result = this.mQcRilOemHook.sendQcRilHookMsg(IQcRilHook.QCRILHOOK_QMI_OEMHOOK_REQUEST_ID, createPayload(serviceId, messageId, types, qmiItems));
        if (result.exception != null) {
            Log.w(LOG_TAG, String.format("sendQmiMessage() Failed : %s", result.exception.toString()));
            result.exception.printStackTrace();
            throw new IOException();
        }
        byte[] responseData = (byte[]) result.result;
        return receive(responseData, null, QmiOemHookConstants.ResponseType.IS_SYNC_RESPONSE);
    }

    public void sendQmiMessageAsync(short serviceId, short messageId, short[] types, BaseQmiTypes.BaseQmiItemType[] qmiItems, Message msg) throws IOException {
        OemHookCallback qmiOemHookCb = new OemHookCallback(msg);
        this.mQcRilOemHook.sendQcRilHookMsgAsync(IQcRilHook.QCRILHOOK_QMI_OEMHOOK_REQUEST_ID, createPayload(serviceId, messageId, types, qmiItems), qmiOemHookCb);
    }

    public byte[] sendQmiMessage(int serviceHook, short type, BaseQmiTypes.BaseQmiItemType qmiItem) throws IOException {
        return sendQmiMessage(serviceHook, new short[]{type}, new BaseQmiTypes.BaseQmiItemType[]{qmiItem});
    }

    public HashMap<Integer, Object> sendQmiMessageSync(short serviceId, short messageId, short type, BaseQmiTypes.BaseQmiItemType qmiItem) throws IOException {
        return sendQmiMessageSync(serviceId, messageId, new short[]{type}, new BaseQmiTypes.BaseQmiItemType[]{qmiItem});
    }

    public void sendQmiMessageAsync(short serviceId, short messageId, short type, BaseQmiTypes.BaseQmiItemType qmiItem, Message msg) throws IOException {
        sendQmiMessageAsync(serviceId, messageId, new short[]{type}, new BaseQmiTypes.BaseQmiItemType[]{qmiItem}, msg);
    }

    public byte[] sendQmiMessage(int serviceHook) throws IOException {
        return sendQmiMessage(serviceHook, (short) 0, (BaseQmiTypes.BaseQmiItemType) new QmiPrimitiveTypes.QmiNull());
    }

    public HashMap<Integer, Object> sendQmiMessageSync(short serviceId, short messageId) throws IOException {
        return sendQmiMessageSync(serviceId, messageId, (short[]) null, (BaseQmiTypes.BaseQmiItemType[]) null);
    }

    public void sendQmiMessageAsync(short serviceId, short messageId, Message msg) throws IOException {
        sendQmiMessageAsync(serviceId, messageId, (short[]) null, (BaseQmiTypes.BaseQmiItemType[]) null, msg);
    }

    protected void finalize() {
        Log.v(LOG_TAG, "is destroyed");
    }
}
