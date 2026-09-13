package com.qualcomm.qcrilhook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class TunerOemHook {
    public static final short QCRILHOOK_TUNER_RFRPE_GET_PROVISIONED_TABLE_REVISION_REQ = 34;
    public static final short QCRILHOOK_TUNER_RFRPE_GET_RFM_SCENARIO_REQ = 33;
    public static final short QCRILHOOK_TUNER_RFRPE_SET_RFM_SCENARIO_REQ = 32;
    private static final byte TLV_TYPE_COMMON_REQ_SCENARIO_ID = 1;
    private static final byte TLV_TYPE_GET_PROVISION_TABLE_OPTIONAL_TAG1 = 16;
    private static final byte TLV_TYPE_GET_PROVISION_TABLE_OPTIONAL_TAG2 = 17;
    private static final short TUNER_SERVICE_ID = 4;
    private static TunerOemHook mInstance;
    Context mContext;
    private QmiOemHook mQmiOemHook;
    private static String LOG_TAG = "TunerOemHook";
    private static int mRefCount = 0;

    public static class TunerSolResponse {
        public Object data;
        public int result;
    }

    public static class TunerUnsolIndication {
        public Object obj;
        public int oemHookMesgId;
    }

    private TunerOemHook(Context context, Looper listenerLooper) {
        this.mContext = context;
        this.mQmiOemHook = QmiOemHook.getInstance(context, listenerLooper);
    }

    public static TunerOemHook getInstance(Context context, Handler listenerHandler) {
        if (mInstance == null) {
            mInstance = new TunerOemHook(context, listenerHandler.getLooper());
        } else {
            mInstance.mContext = context;
        }
        mRefCount++;
        return mInstance;
    }

    public synchronized void registerOnReadyCb(Handler h, int what, Object obj) {
        QmiOemHook.registerOnReadyCb(h, what, null);
    }

    public synchronized void unregisterOnReadyCb(Handler h) {
        QmiOemHook.unregisterOnReadyCb(h);
    }

    public synchronized void dispose() {
        mRefCount--;
        if (mRefCount == 0) {
            Log.v(LOG_TAG, "dispose(): Unregistering service");
            this.mQmiOemHook.dispose();
            this.mQmiOemHook = null;
            mInstance = null;
        } else {
            Log.v(LOG_TAG, "dispose mRefCount = " + mRefCount);
        }
    }

    public Integer tuner_send_proximity_updates(int[] proximityValues) {
        ScenarioRequest req = new ScenarioRequest(proximityValues);
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(TUNER_SERVICE_ID, (short) 32, req.getTypes(), req.getItems());
            return (Integer) receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int tuner_get_provisioned_table_revision() {
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(TUNER_SERVICE_ID, (short) 34);
            return ((Integer) receive(hashMap)).intValue();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static Object receive(HashMap<Integer, Object> map) {
        ((Integer) map.get(1)).intValue();
        int responseSize = ((Integer) map.get(2)).intValue();
        int successStatus = ((Integer) map.get(3)).intValue();
        short messageId = ((Short) map.get(8)).shortValue();
        ByteBuffer respByteBuf = (ByteBuffer) map.get(6);
        Log.v(LOG_TAG, "receive respByteBuf = " + respByteBuf);
        Log.v(LOG_TAG, " responseSize=" + responseSize + " successStatus=" + successStatus + " messageId= " + ((int) messageId));
        Integer returnObject = Integer.valueOf(successStatus);
        switch (messageId) {
            case 32:
                Log.v(LOG_TAG, "Response: QCRILHOOK_TUNER_RFRPE_SET_RFM_SCENARIO_REQ=" + successStatus);
                return returnObject;
            case 33:
                Log.v(LOG_TAG, "Response: QCRILHOOK_TUNER_RFRPE_GET_RFM_SCENARIO_REQ=" + successStatus);
                return returnObject;
            case 34:
                Log.v(LOG_TAG, "Response: QCRILHOOK_TUNER_RFRPE_GET_PROVISIONED_TABLE_REVISION_REQ=" + successStatus);
                ProvisionTable info = new ProvisionTable(respByteBuf);
                return Integer.valueOf(info.prv_tbl_rev);
            default:
                Log.v(LOG_TAG, "Invalid request");
                return returnObject;
        }
    }

    public static class ProvisionTable {
        public int[] prv_tbl_oem;
        public int prv_tbl_rev;

        public ProvisionTable(ByteBuffer buf) {
            this.prv_tbl_oem = null;
            this.prv_tbl_rev = -1;
            Log.d(TunerOemHook.LOG_TAG, "ProvsionTableInfo: " + buf.toString());
            while (buf.hasRemaining() && buf.remaining() >= 3) {
                int type = PrimitiveParser.toUnsigned(buf.get());
                int length = PrimitiveParser.toUnsigned(buf.getShort());
                switch (type) {
                    case 16:
                        byte[] data = new byte[length];
                        for (int i = 0; i < length; i++) {
                            data[i] = buf.get();
                        }
                        ByteBuffer wrapped = ByteBuffer.wrap(data);
                        wrapped.order(ByteOrder.LITTLE_ENDIAN);
                        this.prv_tbl_rev = wrapped.getInt();
                        Log.i(TunerOemHook.LOG_TAG, "Provision Table Rev = " + this.prv_tbl_rev);
                        break;
                    case 17:
                        int i2 = buf.get();
                        this.prv_tbl_oem = new int[i2];
                        for (int i3 = 0; i3 < i2; i3++) {
                            this.prv_tbl_oem[i3] = buf.getShort();
                        }
                        Log.i(TunerOemHook.LOG_TAG, "Provsions Table OEM = " + Arrays.toString(this.prv_tbl_oem));
                        break;
                    default:
                        Log.i(TunerOemHook.LOG_TAG, "Invalid TLV type");
                        break;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiInteger> intArrayToQmiArray(int[] arr) {
        QmiPrimitiveTypes.QmiInteger[] qmiIntArray = new QmiPrimitiveTypes.QmiInteger[arr.length];
        for (int i = 0; i < arr.length; i++) {
            qmiIntArray[i] = new QmiPrimitiveTypes.QmiInteger(arr[i]);
        }
        return new QmiPrimitiveTypes.QmiArray<>(qmiIntArray, (short) arr.length, QmiPrimitiveTypes.QmiInteger.class);
    }

    public class ScenarioRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiInteger> list;

        public ScenarioRequest(int[] list) {
            this.list = TunerOemHook.this.intArrayToQmiArray(list);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.list};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1};
        }
    }
}
