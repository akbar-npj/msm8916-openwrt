package com.qualcomm.qcrilhook;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.util.Log;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class EmbmsOemHook extends Handler {
    private static final short EMBMSHOOK_MSG_ID_ACTDEACT = 17;
    private static final short EMBMSHOOK_MSG_ID_ACTIVATE = 2;
    private static final short EMBMSHOOK_MSG_ID_DEACTIVATE = 3;
    private static final short EMBMSHOOK_MSG_ID_DELIVER_LOG_PACKET = 22;
    private static final short EMBMSHOOK_MSG_ID_DISABLE = 1;
    private static final short EMBMSHOOK_MSG_ID_ENABLE = 0;
    private static final short EMBMSHOOK_MSG_ID_GET_ACTIVE = 5;
    private static final short EMBMSHOOK_MSG_ID_GET_ACTIVE_LOG_PACKET_IDS = 21;
    private static final short EMBMSHOOK_MSG_ID_GET_AVAILABLE = 4;
    private static final short EMBMSHOOK_MSG_ID_GET_COVERAGE = 8;
    private static final short EMBMSHOOK_MSG_ID_GET_E911_STATE = 27;
    private static final short EMBMSHOOK_MSG_ID_GET_SIB16_COVERAGE = 24;
    private static final short EMBMSHOOK_MSG_ID_GET_SIG_STRENGTH = 9;
    private static final short EMBMSHOOK_MSG_ID_GET_TIME = 26;
    private static final short EMBMSHOOK_MSG_ID_SET_TIME = 23;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_ACTIVE_TMGI_LIST = 12;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_AVAILABLE_TMGI_LIST = 15;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_CELL_ID = 18;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_COVERAGE_STATE = 13;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_E911_STATE = 28;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_OOS_STATE = 16;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_RADIO_STATE = 19;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_SAI_LIST = 20;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_SIB16 = 25;
    private static final short EMBMSHOOK_MSG_ID_UNSOL_STATE_CHANGE = 11;
    private static final short EMBMS_SERVICE_ID = 2;
    private static final int FAILURE = -1;
    private static final int OEM_HOOK_RESPONSE = 1;
    private static final short ONE_BYTE = 1;
    private static final int QCRILHOOK_READY_CALLBACK = 2;
    private static final int SIZE_OF_TMGI = 6;
    private static final byte TLV_TYPE_ACTDEACTIVATE_REQ_ACT_TMGI = 3;
    private static final byte TLV_TYPE_ACTDEACTIVATE_REQ_DEACT_TMGI = 4;
    private static final byte TLV_TYPE_ACTDEACTIVATE_REQ_EARFCN_LIST = 6;
    private static final byte TLV_TYPE_ACTDEACTIVATE_REQ_PRIORITY = 5;
    private static final byte TLV_TYPE_ACTDEACTIVATE_REQ_SAI_LIST = 16;
    private static final byte TLV_TYPE_ACTDEACTIVATE_RESP_ACTTMGI = 17;
    private static final byte TLV_TYPE_ACTDEACTIVATE_RESP_ACT_CODE = 2;
    private static final byte TLV_TYPE_ACTDEACTIVATE_RESP_DEACTTMGI = 18;
    private static final byte TLV_TYPE_ACTDEACTIVATE_RESP_DEACT_CODE = 3;
    private static final byte TLV_TYPE_ACTIVATE_REQ_EARFCN_LIST = 5;
    private static final byte TLV_TYPE_ACTIVATE_REQ_PRIORITY = 4;
    private static final byte TLV_TYPE_ACTIVATE_REQ_SAI_LIST = 16;
    private static final byte TLV_TYPE_ACTIVATE_REQ_TMGI = 3;
    private static final byte TLV_TYPE_ACTIVATE_RESP_TMGI = 17;
    private static final short TLV_TYPE_ACTIVELOGPACKETID_REQ_PACKET_ID_LIST = 2;
    private static final short TLV_TYPE_ACTIVELOGPACKETID_RESP_PACKET_ID_LIST = 2;
    private static final byte TLV_TYPE_COMMON_REQ_CALL_ID = 2;
    private static final byte TLV_TYPE_COMMON_REQ_TRACE_ID = 1;
    private static final byte TLV_TYPE_COMMON_RESP_CALL_ID = 16;
    private static final byte TLV_TYPE_COMMON_RESP_CODE = 2;
    private static final byte TLV_TYPE_COMMON_RESP_TRACE_ID = 1;
    private static final byte TLV_TYPE_DEACTIVATE_REQ_TMGI = 3;
    private static final byte TLV_TYPE_DEACTIVATE_RESP_TMGI = 17;
    private static final short TLV_TYPE_DELIVERLOGPACKET_REQ_LOG_PACKET = 3;
    private static final short TLV_TYPE_DELIVERLOGPACKET_REQ_PACKET_ID = 2;
    private static final byte TLV_TYPE_ENABLE_RESP_IFNAME = 17;
    private static final byte TLV_TYPE_ENABLE_RESP_IF_INDEX = 18;
    private static final byte TLV_TYPE_GET_ACTIVE_RESP_TMGI_ARRAY = 16;
    private static final byte TLV_TYPE_GET_AVAILABLE_RESP_TMGI_ARRAY = 16;
    private static final byte TLV_TYPE_GET_COVERAGE_STATE_RESP_STATE = 16;
    private static final short TLV_TYPE_GET_E911_RESP_STATE = 16;
    private static final byte TLV_TYPE_GET_SIG_STRENGTH_RESP_ACTIVE_TMGI_LIST = 20;
    private static final byte TLV_TYPE_GET_SIG_STRENGTH_RESP_EXCESS_SNR = 18;
    private static final byte TLV_TYPE_GET_SIG_STRENGTH_RESP_MBSFN_AREA_ID = 16;
    private static final byte TLV_TYPE_GET_SIG_STRENGTH_RESP_NUMBER_OF_TMGI_PER_MBSFN = 19;
    private static final byte TLV_TYPE_GET_SIG_STRENGTH_RESP_SNR = 17;
    private static final byte TLV_TYPE_GET_TIME_RESP_DAY_LIGHT_SAVING = 16;
    private static final byte TLV_TYPE_GET_TIME_RESP_LEAP_SECONDS = 17;
    private static final byte TLV_TYPE_GET_TIME_RESP_LOCAL_TIME_OFFSET = 18;
    private static final byte TLV_TYPE_GET_TIME_RESP_TIME_MSECONDS = 3;
    private static final byte TLV_TYPE_SET_TIME_REQ_SNTP_SUCCESS = 1;
    private static final byte TLV_TYPE_SET_TIME_REQ_TIME_MSECONDS = 16;
    private static final byte TLV_TYPE_SET_TIME_REQ_TIME_STAMP = 17;
    private static final short TLV_TYPE_UNSOL_ACTIVE_IND_TMGI_ARRAY = 2;
    private static final short TLV_TYPE_UNSOL_AVAILABLE_IND_TMGI_ARRAY_OR_RESPONSE_CODE = 2;
    private static final short TLV_TYPE_UNSOL_CELL_ID_IND_CID = 4;
    private static final short TLV_TYPE_UNSOL_CELL_ID_IND_MCC = 2;
    private static final short TLV_TYPE_UNSOL_CELL_ID_IND_MNC = 3;
    private static final short TLV_TYPE_UNSOL_COVERAGE_IND_STATE_OR_RESPONSE_CODE = 2;
    private static final short TLV_TYPE_UNSOL_E911_STATE_OR_RESPONSE_CODE = 2;
    private static final short TLV_TYPE_UNSOL_OOS_IND_STATE = 2;
    private static final short TLV_TYPE_UNSOL_OOS_IND_TMGI_ARRAY = 3;
    private static final short TLV_TYPE_UNSOL_RADIO_STATE = 2;
    private static final short TLV_TYPE_UNSOL_SAI_IND_AVAILABLE_SAI_LIST = 4;
    private static final short TLV_TYPE_UNSOL_SAI_IND_CAMPED_SAI_LIST = 2;
    private static final short TLV_TYPE_UNSOL_SAI_IND_SAI_PER_GROUP_LIST = 3;
    private static final short TLV_TYPE_UNSOL_SIB16 = 1;
    private static final short TLV_TYPE_UNSOL_STATE_IND_IF_INDEX = 3;
    private static final short TLV_TYPE_UNSOL_STATE_IND_IP_ADDRESS = 2;
    private static final short TLV_TYPE_UNSOL_STATE_IND_STATE = 1;
    private static final short TWO_BYTES = 2;
    private static final int UNSOL_BASE_QCRILHOOK = 4096;
    public static final int UNSOL_TYPE_ACTIVE_TMGI_LIST = 2;
    public static final int UNSOL_TYPE_AVAILABLE_TMGI_LIST = 4;
    public static final int UNSOL_TYPE_BROADCAST_COVERAGE = 3;
    public static final int UNSOL_TYPE_CELL_ID = 6;
    public static final int UNSOL_TYPE_E911_STATE = 10;
    public static final int UNSOL_TYPE_EMBMSOEMHOOK_READY_CALLBACK = 4097;
    public static final int UNSOL_TYPE_OOS_STATE = 5;
    public static final int UNSOL_TYPE_RADIO_STATE = 7;
    public static final int UNSOL_TYPE_SAI_LIST = 8;
    public static final int UNSOL_TYPE_SIB16_COVERAGE = 9;
    public static final int UNSOL_TYPE_STATE_CHANGE = 1;
    private static EmbmsOemHook sInstance;
    private QmiOemHook mQmiOemHook;
    private RegistrantList mRegistrants;
    private static String LOG_TAG = "EmbmsOemHook";
    private static final int SUCCESS = 0;
    private static int mRefCount = SUCCESS;

    private EmbmsOemHook(Context context) {
        Log.v(LOG_TAG, "EmbmsOemHook ()");
        this.mQmiOemHook = QmiOemHook.getInstance(context);
        QmiOemHook.registerService((short) 2, this, 1);
        QmiOemHook.registerOnReadyCb(this, 2, null);
        this.mRegistrants = new RegistrantList();
    }

    public static synchronized EmbmsOemHook getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new EmbmsOemHook(context);
            Log.d(LOG_TAG, "Singleton Instance of Embms created.");
        }
        mRefCount++;
        return sInstance;
    }

    public synchronized void dispose() {
        int i = mRefCount + FAILURE;
        mRefCount = i;
        if (i == 0) {
            Log.d(LOG_TAG, "dispose(): Unregistering receiver");
            QmiOemHook.unregisterService(2);
            QmiOemHook.unregisterOnReadyCb(this);
            this.mQmiOemHook.dispose();
            this.mQmiOemHook = null;
            sInstance = null;
            this.mRegistrants.removeCleared();
        } else {
            Log.v(LOG_TAG, "dispose mRefCount = " + mRefCount);
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        Log.i(LOG_TAG, "received message : " + msg.what);
        AsyncResult ar = (AsyncResult) msg.obj;
        switch (msg.what) {
            case 1:
                HashMap<Integer, Object> map = (HashMap) ar.result;
                if (map == null) {
                    Log.e(LOG_TAG, "Hashmap async userobj is NULL");
                } else {
                    handleResponse(map);
                }
                break;
            case 2:
                Object payload = ar.result;
                notifyUnsol(UNSOL_TYPE_EMBMSOEMHOOK_READY_CALLBACK, payload);
                break;
            default:
                Log.e(LOG_TAG, "Unexpected message received from QmiOemHook what = " + msg.what);
                break;
        }
    }

    private void handleResponse(HashMap<Integer, Object> map) {
        short msgId = ((Short) map.get(8)).shortValue();
        int responseSize = ((Integer) map.get(2)).intValue();
        int successStatus = ((Integer) map.get(3)).intValue();
        Message msg = (Message) map.get(4);
        ByteBuffer respByteBuf = (ByteBuffer) map.get(6);
        Log.v(LOG_TAG, " responseSize=" + responseSize + " successStatus=" + successStatus);
        switch (msgId) {
            case SUCCESS /* 0 */:
                msg.obj = new EnableResponse(successStatus, respByteBuf);
                msg.sendToTarget();
                break;
            case 1:
                msg.obj = new DisableResponse(successStatus, respByteBuf);
                msg.sendToTarget();
                break;
            case 2:
            case 3:
                msg.obj = new TmgiResponse(successStatus, respByteBuf);
                msg.sendToTarget();
                break;
            case 4:
            case 15:
                if (msgId == 4 && successStatus != 0) {
                    Log.e(LOG_TAG, "Error received in EMBMSHOOK_MSG_ID_GET_AVAILABLE: " + successStatus);
                } else {
                    TmgiListIndication list = new TmgiListIndication(respByteBuf, msgId);
                    notifyUnsol(4, list);
                }
                break;
            case 5:
            case 12:
                if (msgId == 5 && successStatus != 0) {
                    Log.e(LOG_TAG, "Error received in EMBMSHOOK_MSG_ID_GET_ACTIVE: " + successStatus);
                } else {
                    TmgiListIndication list2 = new TmgiListIndication(respByteBuf, msgId);
                    notifyUnsol(2, list2);
                }
                break;
            case 6:
            case 7:
            case UNSOL_TYPE_E911_STATE /* 10 */:
            case 14:
            default:
                Log.e(LOG_TAG, "received unexpected msgId " + ((int) msgId));
                break;
            case 8:
            case 13:
                if (msgId == 8 && successStatus != 0) {
                    Log.e(LOG_TAG, "Error received in EMBMSHOOK_MSG_ID_GET_COVERAGE: " + successStatus);
                } else {
                    CoverageState cs = new CoverageState(respByteBuf, msgId);
                    notifyUnsol(3, cs);
                }
                break;
            case UNSOL_TYPE_SIB16_COVERAGE /* 9 */:
                msg.obj = new SigStrengthResponse(successStatus, respByteBuf);
                msg.sendToTarget();
                break;
            case 11:
                StateChangeInfo info = new StateChangeInfo(respByteBuf);
                notifyUnsol(1, info);
                break;
            case 16:
                OosState state = new OosState(respByteBuf);
                notifyUnsol(5, state);
                break;
            case 17:
                msg.obj = new ActDeactResponse(successStatus, respByteBuf);
                msg.sendToTarget();
                break;
            case 18:
                CellIdIndication ind = new CellIdIndication(respByteBuf);
                notifyUnsol(6, ind);
                break;
            case 19:
                RadioStateIndication ind2 = new RadioStateIndication(respByteBuf);
                notifyUnsol(7, ind2);
                break;
            case 20:
                SaiIndication ind3 = new SaiIndication(respByteBuf);
                notifyUnsol(8, ind3);
                break;
            case 21:
                msg.obj = new ActiveLogPacketIDsResponse(successStatus, respByteBuf);
                msg.sendToTarget();
                break;
            case 22:
                Log.v(LOG_TAG, " deliverLogPacket response successStatus=" + successStatus);
                break;
            case 23:
                msg.arg1 = successStatus;
                msg.sendToTarget();
                break;
            case 24:
            case 25:
                if (msgId == 24 && successStatus != 0) {
                    Log.e(LOG_TAG, "Error received in EMBMSHOOK_MSG_ID_GET_SIB16_COVERAGE: " + successStatus);
                } else {
                    Sib16Coverage ind4 = new Sib16Coverage(respByteBuf);
                    notifyUnsol(9, ind4);
                }
                break;
            case 26:
                msg.obj = new TimeResponse(successStatus, respByteBuf);
                msg.sendToTarget();
                break;
            case 27:
            case 28:
                E911StateIndication ind5 = new E911StateIndication(respByteBuf, msgId);
                notifyUnsol(10, ind5);
                break;
        }
    }

    private void notifyUnsol(int type, Object payload) {
        UnsolObject obj = new UnsolObject(type, payload);
        AsyncResult ar = new AsyncResult((Object) null, obj, (Throwable) null);
        Log.i(LOG_TAG, "Notifying registrants type = " + type);
        this.mRegistrants.notifyRegistrants(ar);
    }

    public void registerForNotifications(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        synchronized (this.mRegistrants) {
            Log.i(LOG_TAG, "Adding a registrant");
            this.mRegistrants.add(r);
        }
    }

    public void unregisterForNotifications(Handler h) {
        synchronized (this.mRegistrants) {
            Log.i(LOG_TAG, "Removing a registrant");
            this.mRegistrants.remove(h);
        }
    }

    public int enable(int traceId, Message msg) {
        try {
            BasicRequest req = new BasicRequest(traceId);
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_ENABLE, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during enable !!!!!!");
            return FAILURE;
        }
    }

    public int activateTmgi(int traceId, byte callId, byte[] tmgi, int priority, int[] saiList, int[] earfcnList, Message msg) {
        TmgiActivateRequest req = new TmgiActivateRequest(traceId, callId, tmgi, priority, saiList, earfcnList);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, (short) 2, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during activate !!!!!!");
            return FAILURE;
        }
    }

    public int deactivateTmgi(int traceId, byte callId, byte[] tmgi, Message msg) {
        TmgiDeActivateRequest req = new TmgiDeActivateRequest(traceId, tmgi, callId);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, (short) 3, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during deactivate !!!!!!");
            return FAILURE;
        }
    }

    public int actDeactTmgi(int traceId, byte callId, byte[] actTmgi, byte[] deActTmgi, int priority, int[] saiList, int[] earfcnList, Message msg) {
        ActDeactRequest req = new ActDeactRequest(traceId, callId, actTmgi, deActTmgi, priority, saiList, earfcnList);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_ACTDEACT, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during activate-deactivate !!!!!!");
            return FAILURE;
        }
    }

    public int getAvailableTMGIList(int traceId, byte callId) {
        GenericRequest req = new GenericRequest(traceId, callId);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, (short) 4, req.getTypes(), req.getItems(), (Message) null);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during getAvailableTMGIList !!!!!!");
            return FAILURE;
        }
    }

    public int getActiveTMGIList(int traceId, byte callId) {
        GenericRequest req = new GenericRequest(traceId, callId);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_GET_ACTIVE, req.getTypes(), req.getItems(), (Message) null);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during getActiveTMGIList !!!!!!");
            return FAILURE;
        }
    }

    public int getCoverageState(int traceId) {
        try {
            BasicRequest req = new BasicRequest(traceId);
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_GET_COVERAGE, req.getTypes(), req.getItems(), (Message) null);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during getActiveTMGIList !!!!!!");
            return FAILURE;
        }
    }

    public int getSignalStrength(int traceId, Message msg) {
        try {
            BasicRequest req = new BasicRequest(traceId);
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_GET_SIG_STRENGTH, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during enable !!!!!!");
            return FAILURE;
        }
    }

    public int disable(int traceId, byte callId, Message msg) {
        GenericRequest req = new GenericRequest(traceId, callId);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, (short) 1, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during disable !!!!!!");
            return FAILURE;
        }
    }

    public int getActiveLogPacketIDs(int traceId, int[] supportedLogPacketIdList, Message msg) {
        ActiveLogPacketIDsRequest req = new ActiveLogPacketIDsRequest(traceId, supportedLogPacketIdList);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_GET_ACTIVE_LOG_PACKET_IDS, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during activate log packet ID's !!!!!!");
            return FAILURE;
        }
    }

    public int deliverLogPacket(int traceId, int logPacketId, byte[] logPacket) {
        DeliverLogPacketRequest req = new DeliverLogPacketRequest(traceId, logPacketId, logPacket);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_DELIVER_LOG_PACKET, req.getTypes(), req.getItems(), (Message) null);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during deliver logPacket !!!!!!");
            return FAILURE;
        }
    }

    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder ret = new StringBuilder(bytes.length * 2);
        for (int i = SUCCESS; i < bytes.length; i++) {
            int b = (bytes[i] >> 4) & 15;
            ret.append("0123456789abcdef".charAt(b));
            int b2 = bytes[i] & 15;
            ret.append("0123456789abcdef".charAt(b2));
        }
        return ret.toString();
    }

    public int getTime(int traceId, Message msg) {
        try {
            BasicRequest req = new BasicRequest(traceId);
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_GET_TIME, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during getTime !!!!!!");
            return FAILURE;
        }
    }

    public int getSib16CoverageStatus(Message msg) {
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_GET_SIB16_COVERAGE, msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during getSIB16 !!!!!!");
            return FAILURE;
        }
    }

    public int setTime(boolean sntpSuccess, long timeMseconds, long timeStamp, Message msg) {
        byte success = 0;
        if (sntpSuccess) {
            success = 1;
        }
        Log.i(LOG_TAG, "setTime success = " + ((int) success) + " timeMseconds = " + timeMseconds + " timeStamp = " + timeStamp);
        SetTimeRequest req = new SetTimeRequest(success, timeMseconds, timeStamp);
        try {
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_SET_TIME, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occured during setTime !!!!!!");
            return FAILURE;
        }
    }

    public int getE911State(int traceId, Message msg) {
        try {
            BasicRequest req = new BasicRequest(traceId);
            this.mQmiOemHook.sendQmiMessageAsync((short) 2, EMBMSHOOK_MSG_ID_GET_E911_STATE, req.getTypes(), req.getItems(), msg);
            return SUCCESS;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException occurred during getE911State !!!!!!");
            return FAILURE;
        }
    }

    public class UnsolObject {
        public Object obj;
        public int unsolId;

        public UnsolObject(int i, Object o) {
            this.unsolId = i;
            this.obj = o;
        }
    }

    public class StateChangeInfo {
        public int ifIndex;
        public String ipAddress;
        public int state;

        public StateChangeInfo(int state, String address, int index) {
            this.state = state;
            this.ipAddress = address;
            this.ifIndex = index;
        }

        public StateChangeInfo(ByteBuffer buf) {
            while (buf.hasRemaining()) {
                int type = PrimitiveParser.toUnsigned(buf.get());
                int length = PrimitiveParser.toUnsigned(buf.getShort());
                switch (type) {
                    case 1:
                        this.state = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "State = " + this.state);
                        break;
                    case 2:
                        byte[] address = new byte[length];
                        for (int i = EmbmsOemHook.SUCCESS; i < length; i++) {
                            address[i] = buf.get();
                        }
                        this.ipAddress = new QmiPrimitiveTypes.QmiString(address).toString();
                        Log.i(EmbmsOemHook.LOG_TAG, "ip Address = " + this.ipAddress);
                        break;
                    case 3:
                        this.ifIndex = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "index = " + this.ifIndex);
                        break;
                    default:
                        Log.e(EmbmsOemHook.LOG_TAG, "StateChangeInfo: Unexpected Type " + type);
                        break;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public byte[] parseTmgi(ByteBuffer buf) {
        int index = SUCCESS;
        byte totalTmgis = buf.get();
        byte[] tmgi = new byte[totalTmgis * TLV_TYPE_ACTDEACTIVATE_REQ_EARFCN_LIST];
        int i = SUCCESS;
        while (i < totalTmgis) {
            byte tmgiLength = buf.get();
            int j = SUCCESS;
            int index2 = index;
            while (j < tmgiLength) {
                tmgi[index2] = buf.get();
                j++;
                index2++;
            }
            i++;
            index = index2;
        }
        return tmgi;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public byte[] parseActiveTmgi(ByteBuffer buf) {
        int index = SUCCESS;
        short totalTmgis = buf.getShort();
        byte[] tmgi = new byte[totalTmgis * 6];
        int i = SUCCESS;
        while (i < totalTmgis) {
            byte tmgiLength = buf.get();
            int j = SUCCESS;
            int index2 = index;
            while (j < tmgiLength) {
                tmgi[index2] = buf.get();
                j++;
                index2++;
            }
            i++;
            index = index2;
        }
        return tmgi;
    }

    public class TmgiListIndication {
        public int code;
        public byte[] list;
        public byte[] sessions = null;
        public int traceId;

        public TmgiListIndication(ByteBuffer buf, short msgId) {
            this.list = new byte[EmbmsOemHook.SUCCESS];
            this.traceId = EmbmsOemHook.SUCCESS;
            this.code = EmbmsOemHook.SUCCESS;
            while (buf.hasRemaining()) {
                try {
                    int type = PrimitiveParser.toUnsigned(buf.get());
                    PrimitiveParser.toUnsigned(buf.getShort());
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            continue;
                        case 2:
                            if (msgId == 4 || msgId == 5) {
                                this.code = buf.getInt();
                                Log.i(EmbmsOemHook.LOG_TAG, "response code = " + this.code);
                            }
                            break;
                        case 16:
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "TmgiListIndication: Unexpected Type " + type);
                            continue;
                    }
                    this.list = EmbmsOemHook.this.parseTmgi(buf);
                    Log.i(EmbmsOemHook.LOG_TAG, "tmgiArray = " + EmbmsOemHook.bytesToHexString(this.list));
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Invalid format of byte buffer received in TmgiListIndication");
                }
            }
        }
    }

    public class OosState {
        public byte[] list;
        public int state;
        public int traceId;

        public OosState(ByteBuffer buf) {
            this.list = null;
            this.traceId = EmbmsOemHook.SUCCESS;
            while (buf.hasRemaining()) {
                int type = PrimitiveParser.toUnsigned(buf.get());
                PrimitiveParser.toUnsigned(buf.getShort());
                switch (type) {
                    case 1:
                        this.traceId = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                        break;
                    case 2:
                        this.state = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "OOs State = " + this.state);
                        break;
                    case 3:
                        this.list = EmbmsOemHook.this.parseTmgi(buf);
                        Log.i(EmbmsOemHook.LOG_TAG, "tmgiArray = " + EmbmsOemHook.bytesToHexString(this.list));
                        break;
                    default:
                        Log.e(EmbmsOemHook.LOG_TAG, "OosState: Unexpected Type " + type);
                        break;
                }
            }
        }
    }

    public class CellIdIndication {
        public String id;
        public String mcc;
        public String mnc;
        public int traceId;

        public CellIdIndication(ByteBuffer buf) {
            this.mcc = null;
            this.mnc = null;
            this.id = null;
            this.traceId = EmbmsOemHook.SUCCESS;
            while (buf.hasRemaining()) {
                try {
                    int type = PrimitiveParser.toUnsigned(buf.get());
                    int length = PrimitiveParser.toUnsigned(buf.getShort());
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            break;
                        case 2:
                            byte[] temp = new byte[length];
                            for (int i = EmbmsOemHook.SUCCESS; i < length; i++) {
                                temp[i] = buf.get();
                            }
                            this.mcc = new QmiPrimitiveTypes.QmiString(temp).toStringValue();
                            Log.i(EmbmsOemHook.LOG_TAG, "MCC = " + this.mcc);
                            break;
                        case 3:
                            byte[] temp2 = new byte[length];
                            for (int i2 = EmbmsOemHook.SUCCESS; i2 < length; i2++) {
                                temp2[i2] = buf.get();
                            }
                            this.mnc = new QmiPrimitiveTypes.QmiString(temp2).toStringValue();
                            Log.i(EmbmsOemHook.LOG_TAG, "MNC = " + this.mnc);
                            break;
                        case 4:
                            this.id = String.format("%7s", Integer.toHexString(buf.getInt())).replace(' ', '0');
                            Log.i(EmbmsOemHook.LOG_TAG, "CellId = " + this.id);
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "CellIdIndication: Unexpected Type " + type);
                            break;
                    }
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Unexpected buffer format when parsing for CellIdIndication");
                }
            }
        }
    }

    public class RadioStateIndication {
        public int state;
        public int traceId;

        public RadioStateIndication(ByteBuffer buf) {
            this.state = EmbmsOemHook.SUCCESS;
            this.traceId = EmbmsOemHook.SUCCESS;
            while (buf.hasRemaining()) {
                try {
                    int type = PrimitiveParser.toUnsigned(buf.get());
                    PrimitiveParser.toUnsigned(buf.getShort());
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            break;
                        case 2:
                            this.state = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "radio = " + this.state);
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "RadioStateIndication: Unexpected Type " + type);
                            break;
                    }
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Unexpected buffer format when parsing for RadioStateIndication");
                }
            }
        }
    }

    public class SaiIndication {
        public int[] availableSaiList;
        public int[] campedSaiList;
        public int[] numSaiPerGroupList;
        public int traceId;

        public SaiIndication(ByteBuffer buf) {
            this.campedSaiList = null;
            this.numSaiPerGroupList = null;
            this.availableSaiList = null;
            this.traceId = EmbmsOemHook.SUCCESS;
            while (buf.hasRemaining()) {
                try {
                    int type = buf.get();
                    buf.getShort();
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            break;
                        case 2:
                            int i = buf.get();
                            int[] list = new int[i];
                            for (int i2 = EmbmsOemHook.SUCCESS; i2 < i; i2++) {
                                list[i2] = buf.getInt();
                            }
                            this.campedSaiList = list;
                            Log.i(EmbmsOemHook.LOG_TAG, "Camped list = " + Arrays.toString(this.campedSaiList));
                            break;
                        case 3:
                            int i3 = buf.get();
                            int[] list2 = new int[i3];
                            for (int i4 = EmbmsOemHook.SUCCESS; i4 < i3; i4++) {
                                list2[i4] = buf.getInt();
                            }
                            this.numSaiPerGroupList = list2;
                            Log.i(EmbmsOemHook.LOG_TAG, "Number of SAI per group list = " + Arrays.toString(this.numSaiPerGroupList));
                            break;
                        case 4:
                            int i5 = buf.getShort();
                            int[] list3 = new int[i5];
                            for (int i6 = EmbmsOemHook.SUCCESS; i6 < i5; i6++) {
                                list3[i6] = buf.getInt();
                            }
                            this.availableSaiList = list3;
                            Log.i(EmbmsOemHook.LOG_TAG, "Available SAI list = " + Arrays.toString(this.availableSaiList));
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "SaiIndication: Unexpected Type " + type);
                            break;
                    }
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Unexpected buffer format when parsing for SaiIndication");
                }
            }
        }
    }

    public class CoverageState {
        public int code;
        public int state;
        public int status;
        public int traceId;

        public CoverageState(ByteBuffer buf, short msgId) {
            this.traceId = EmbmsOemHook.SUCCESS;
            this.code = EmbmsOemHook.SUCCESS;
            while (buf.hasRemaining()) {
                try {
                    int type = PrimitiveParser.toUnsigned(buf.get());
                    PrimitiveParser.toUnsigned(buf.getShort());
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            continue;
                        case 2:
                            if (msgId == 8) {
                                this.code = buf.getInt();
                                Log.i(EmbmsOemHook.LOG_TAG, "response code = " + this.code);
                            }
                            break;
                        case 16:
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "CoverageState: Unexpected Type " + type);
                            continue;
                    }
                    this.state = buf.getInt();
                    Log.i(EmbmsOemHook.LOG_TAG, "Coverage State = " + this.state);
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Invalid format of byte buffer received in CoverageState");
                }
            }
        }
    }

    public class Sib16Coverage {
        public boolean inCoverage;

        public Sib16Coverage(ByteBuffer buf) {
            this.inCoverage = false;
            while (buf.hasRemaining()) {
                try {
                    int type = PrimitiveParser.toUnsigned(buf.get());
                    PrimitiveParser.toUnsigned(buf.getShort());
                    switch (type) {
                        case 1:
                            byte coverage = buf.get();
                            if (coverage == 1) {
                                this.inCoverage = true;
                            }
                            Log.i(EmbmsOemHook.LOG_TAG, "Unsol SIB16 coverage status = " + this.inCoverage);
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "Sib16Coverage: Unexpected Type " + type);
                            break;
                    }
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Unexpected buffer format when parsing for Sib16Coverage");
                }
            }
        }
    }

    public class E911StateIndication {
        public int code;
        public int state;
        public int traceId;

        public E911StateIndication(ByteBuffer buf, short msgId) {
            this.traceId = EmbmsOemHook.SUCCESS;
            while (buf.hasRemaining()) {
                try {
                    int type = PrimitiveParser.toUnsigned(buf.get());
                    PrimitiveParser.toUnsigned(buf.getShort());
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            continue;
                        case 2:
                            if (msgId == 27) {
                                this.code = buf.getInt();
                                Log.i(EmbmsOemHook.LOG_TAG, "response code = " + this.code);
                            }
                            break;
                        case 16:
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "E911 State: Unexpected Type " + type);
                            continue;
                    }
                    this.state = buf.getInt();
                    Log.i(EmbmsOemHook.LOG_TAG, "E911 State = " + this.state);
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Unexpected buffer format when parsing for E911 Notification");
                }
            }
        }
    }

    public class EnableResponse {
        public byte callId;
        public int code;
        public int ifIndex;
        public String interfaceName;
        public int status;
        public int traceId;

        public EnableResponse(int error, ByteBuffer buf) {
            this.code = EmbmsOemHook.SUCCESS;
            this.callId = (byte) 0;
            this.interfaceName = null;
            this.ifIndex = EmbmsOemHook.SUCCESS;
            this.status = error;
            while (buf.hasRemaining()) {
                int type = PrimitiveParser.toUnsigned(buf.get());
                int length = PrimitiveParser.toUnsigned(buf.getShort());
                switch (type) {
                    case 1:
                        this.traceId = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                        break;
                    case 2:
                        this.code = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "code = " + this.code);
                        break;
                    case 16:
                        this.callId = buf.get();
                        Log.i(EmbmsOemHook.LOG_TAG, "callid = " + ((int) this.callId));
                        break;
                    case 17:
                        byte[] name = new byte[length];
                        for (int i = EmbmsOemHook.SUCCESS; i < length; i++) {
                            name[i] = buf.get();
                        }
                        this.interfaceName = new QmiPrimitiveTypes.QmiString(name).toStringValue();
                        Log.i(EmbmsOemHook.LOG_TAG, "ifName = " + this.interfaceName);
                        break;
                    case 18:
                        this.ifIndex = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "ifIndex = " + this.ifIndex);
                        break;
                    default:
                        Log.e(EmbmsOemHook.LOG_TAG, "EnableResponse: Unexpected Type " + type);
                        break;
                }
            }
        }
    }

    public class DisableResponse {
        public byte callId;
        public int code;
        public int status;
        public int traceId;

        public DisableResponse(int error, ByteBuffer buf) {
            this.code = EmbmsOemHook.SUCCESS;
            this.callId = (byte) 0;
            this.status = error;
            while (buf.hasRemaining()) {
                int type = PrimitiveParser.toUnsigned(buf.get());
                PrimitiveParser.toUnsigned(buf.getShort());
                switch (type) {
                    case 1:
                        this.traceId = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                        break;
                    case 2:
                        this.code = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "code = " + this.code);
                        break;
                    case 16:
                        this.callId = buf.get();
                        Log.i(EmbmsOemHook.LOG_TAG, "callid = " + ((int) this.callId));
                        break;
                    default:
                        Log.e(EmbmsOemHook.LOG_TAG, "DisableResponse: Unexpected Type " + type);
                        break;
                }
            }
        }
    }

    public class BasicRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiInteger traceId;

        public BasicRequest(int trace) {
            this.traceId = new QmiPrimitiveTypes.QmiInteger(trace);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.traceId};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1};
        }
    }

    public class GenericRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiByte callId;
        public QmiPrimitiveTypes.QmiInteger traceId;

        public GenericRequest(int trace, byte callId) {
            this.traceId = new QmiPrimitiveTypes.QmiInteger(trace);
            this.callId = new QmiPrimitiveTypes.QmiByte(callId);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.traceId, this.callId};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1, 2};
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiByte> byteArrayToQmiArray(short vSize, byte[] arr) {
        QmiPrimitiveTypes.QmiByte[] qmiByteArray = new QmiPrimitiveTypes.QmiByte[arr.length];
        for (int i = SUCCESS; i < arr.length; i++) {
            qmiByteArray[i] = new QmiPrimitiveTypes.QmiByte(arr[i]);
        }
        return new QmiPrimitiveTypes.QmiArray<>(qmiByteArray, QmiPrimitiveTypes.QmiByte.class, vSize);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiInteger> intArrayToQmiArray(short vSize, int[] arr) {
        int length = arr == null ? SUCCESS : arr.length;
        QmiPrimitiveTypes.QmiInteger[] qmiIntArray = new QmiPrimitiveTypes.QmiInteger[length];
        for (int i = SUCCESS; i < length; i++) {
            qmiIntArray[i] = new QmiPrimitiveTypes.QmiInteger(arr[i]);
        }
        return new QmiPrimitiveTypes.QmiArray<>(qmiIntArray, QmiPrimitiveTypes.QmiInteger.class, vSize);
    }

    public class TmgiActivateRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiByte callId;
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiInteger> earfcnList;
        public QmiPrimitiveTypes.QmiInteger priority;
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiInteger> saiList;
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiByte> tmgi;
        public QmiPrimitiveTypes.QmiInteger traceId;

        public TmgiActivateRequest(int trace, byte callId, byte[] tmgi, int priority, int[] saiList, int[] earfcnList) {
            this.traceId = new QmiPrimitiveTypes.QmiInteger(trace);
            this.callId = new QmiPrimitiveTypes.QmiByte(callId);
            this.priority = new QmiPrimitiveTypes.QmiInteger(priority);
            this.tmgi = EmbmsOemHook.this.byteArrayToQmiArray((short) 1, tmgi);
            this.saiList = EmbmsOemHook.this.intArrayToQmiArray((short) 1, saiList);
            this.earfcnList = EmbmsOemHook.this.intArrayToQmiArray((short) 1, earfcnList);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.traceId, this.callId, this.tmgi, this.priority, this.saiList, this.earfcnList};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1, 2, 3, 4, 16, EmbmsOemHook.EMBMSHOOK_MSG_ID_GET_ACTIVE};
        }
    }

    public class ActDeactRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiByte> actTmgi;
        public QmiPrimitiveTypes.QmiByte callId;
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiByte> deActTmgi;
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiInteger> earfcnList;
        public QmiPrimitiveTypes.QmiInteger priority;
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiInteger> saiList;
        public QmiPrimitiveTypes.QmiInteger traceId;

        public ActDeactRequest(int trace, byte callId, byte[] actTmgi, byte[] deActTmgi, int priority, int[] saiList, int[] earfcnList) {
            this.traceId = new QmiPrimitiveTypes.QmiInteger(trace);
            this.callId = new QmiPrimitiveTypes.QmiByte(callId);
            this.priority = new QmiPrimitiveTypes.QmiInteger(priority);
            this.actTmgi = EmbmsOemHook.this.byteArrayToQmiArray((short) 1, actTmgi);
            this.deActTmgi = EmbmsOemHook.this.byteArrayToQmiArray((short) 1, deActTmgi);
            this.saiList = EmbmsOemHook.this.intArrayToQmiArray((short) 1, saiList);
            this.earfcnList = EmbmsOemHook.this.intArrayToQmiArray((short) 1, earfcnList);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.traceId, this.callId, this.actTmgi, this.deActTmgi, this.priority, this.saiList, this.earfcnList};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1, 2, 3, 4, EmbmsOemHook.EMBMSHOOK_MSG_ID_GET_ACTIVE, 16, 6};
        }
    }

    public class TmgiDeActivateRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiByte callId;
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiByte> tmgi;
        public QmiPrimitiveTypes.QmiInteger traceId;

        public TmgiDeActivateRequest(int trace, byte[] tmgi, byte callId) {
            this.traceId = new QmiPrimitiveTypes.QmiInteger(trace);
            this.tmgi = EmbmsOemHook.this.byteArrayToQmiArray((short) 1, tmgi);
            this.callId = new QmiPrimitiveTypes.QmiByte(callId);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.traceId, this.callId, this.tmgi};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1, 2, 3};
        }
    }

    public class SetTimeRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiByte sntpSuccess;
        public QmiPrimitiveTypes.QmiLong timeMseconds;
        public QmiPrimitiveTypes.QmiLong timeStamp;

        public SetTimeRequest(byte sntpSuccess, long timeMseconds, long timeStamp) {
            this.sntpSuccess = new QmiPrimitiveTypes.QmiByte(sntpSuccess);
            this.timeMseconds = new QmiPrimitiveTypes.QmiLong(timeMseconds);
            this.timeStamp = new QmiPrimitiveTypes.QmiLong(timeStamp);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.sntpSuccess, this.timeMseconds, this.timeStamp};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1, 16, EmbmsOemHook.EMBMSHOOK_MSG_ID_ACTDEACT};
        }
    }

    public class ActiveLogPacketIDsRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiInteger> supportedLogPacketIdList;
        public QmiPrimitiveTypes.QmiInteger traceId;

        public ActiveLogPacketIDsRequest(int trace, int[] supportedLogPacketIdList) {
            this.traceId = new QmiPrimitiveTypes.QmiInteger(trace);
            this.supportedLogPacketIdList = EmbmsOemHook.this.intArrayToQmiArray((short) 2, supportedLogPacketIdList);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.traceId, this.supportedLogPacketIdList};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1, 2};
        }
    }

    public class DeliverLogPacketRequest extends BaseQmiTypes.BaseQmiStructType {
        public QmiPrimitiveTypes.QmiArray<QmiPrimitiveTypes.QmiByte> logPacket;
        public QmiPrimitiveTypes.QmiInteger logPacketId;
        public QmiPrimitiveTypes.QmiInteger traceId;

        public DeliverLogPacketRequest(int trace, int logPacketId, byte[] logPacket) {
            this.traceId = new QmiPrimitiveTypes.QmiInteger(trace);
            this.logPacketId = new QmiPrimitiveTypes.QmiInteger(logPacketId);
            this.logPacket = EmbmsOemHook.this.byteArrayToQmiArray((short) 2, logPacket);
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public BaseQmiTypes.BaseQmiItemType[] getItems() {
            return new BaseQmiTypes.BaseQmiItemType[]{this.traceId, this.logPacketId, this.logPacket};
        }

        @Override // com.qualcomm.qcrilhook.BaseQmiTypes.BaseQmiStructType
        public short[] getTypes() {
            return new short[]{1, 2, 3};
        }
    }

    public class TmgiResponse {
        public int code;
        public int status;
        public byte[] tmgi;
        public int traceId;

        public TmgiResponse(int status, ByteBuffer buf) {
            this.code = EmbmsOemHook.SUCCESS;
            this.traceId = EmbmsOemHook.SUCCESS;
            this.tmgi = null;
            this.status = status;
            while (buf.hasRemaining()) {
                int type = PrimitiveParser.toUnsigned(buf.get());
                PrimitiveParser.toUnsigned(buf.getShort());
                switch (type) {
                    case 1:
                        this.traceId = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                        break;
                    case 2:
                        this.code = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "code = " + this.code);
                        break;
                    case 16:
                        byte id = buf.get();
                        Log.i(EmbmsOemHook.LOG_TAG, "callid = " + ((int) id));
                        break;
                    case 17:
                        int i = buf.get();
                        byte[] tmgi = new byte[i];
                        for (int i2 = EmbmsOemHook.SUCCESS; i2 < i; i2++) {
                            tmgi[i2] = buf.get();
                        }
                        this.tmgi = tmgi;
                        Log.i(EmbmsOemHook.LOG_TAG, "tmgi = " + EmbmsOemHook.bytesToHexString(this.tmgi));
                        break;
                    default:
                        Log.e(EmbmsOemHook.LOG_TAG, "TmgiResponse: Unexpected Type " + type);
                        break;
                }
            }
        }
    }

    public class SigStrengthResponse {
        public int code;
        public float[] esnr;
        public int[] mbsfnAreaId;
        public float[] snr;
        public int status;
        public int[] tmgiPerMbsfn;
        public byte[] tmgilist;
        public int traceId;

        public SigStrengthResponse(int status, ByteBuffer buf) {
            this.code = EmbmsOemHook.SUCCESS;
            this.traceId = EmbmsOemHook.SUCCESS;
            this.snr = null;
            this.mbsfnAreaId = null;
            this.esnr = null;
            this.tmgiPerMbsfn = null;
            this.tmgilist = null;
            this.status = status;
            while (buf.hasRemaining()) {
                try {
                    int type = buf.get();
                    buf.getShort();
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            break;
                        case 2:
                            this.code = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "code = " + this.code);
                            break;
                        case 16:
                            int i = buf.get();
                            int[] mbsfnArray = new int[i];
                            for (int i2 = EmbmsOemHook.SUCCESS; i2 < i; i2++) {
                                mbsfnArray[i2] = buf.getInt();
                            }
                            this.mbsfnAreaId = mbsfnArray;
                            Log.i(EmbmsOemHook.LOG_TAG, "MBSFN_Area_ID = " + Arrays.toString(this.mbsfnAreaId));
                            break;
                        case 17:
                            int i3 = buf.get();
                            float[] snrArray = new float[i3];
                            for (int i4 = EmbmsOemHook.SUCCESS; i4 < i3; i4++) {
                                snrArray[i4] = buf.getFloat();
                            }
                            this.snr = snrArray;
                            Log.i(EmbmsOemHook.LOG_TAG, "SNR = " + Arrays.toString(this.snr));
                            break;
                        case 18:
                            int i5 = buf.get();
                            float[] esnrArray = new float[i5];
                            for (int i6 = EmbmsOemHook.SUCCESS; i6 < i5; i6++) {
                                esnrArray[i6] = buf.getFloat();
                            }
                            this.esnr = esnrArray;
                            Log.i(EmbmsOemHook.LOG_TAG, "EXCESS SNR = " + Arrays.toString(this.esnr));
                            break;
                        case 19:
                            int i7 = buf.get();
                            int[] tmgiPerMbsfnArray = new int[i7];
                            for (int i8 = EmbmsOemHook.SUCCESS; i8 < i7; i8++) {
                                tmgiPerMbsfnArray[i8] = buf.getInt();
                            }
                            this.tmgiPerMbsfn = tmgiPerMbsfnArray;
                            Log.i(EmbmsOemHook.LOG_TAG, "NUMBER OF TMGI PER MBSFN = " + Arrays.toString(this.tmgiPerMbsfn));
                            break;
                        case 20:
                            this.tmgilist = EmbmsOemHook.this.parseActiveTmgi(buf);
                            Log.i(EmbmsOemHook.LOG_TAG, "tmgiArray = " + EmbmsOemHook.bytesToHexString(this.tmgilist));
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "SigStrengthResponse: Unexpected Type " + type);
                            break;
                    }
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Invalid format of byte buffer received in SigStrengthResponse");
                }
            }
            if (this.snr == null) {
                this.snr = new float[EmbmsOemHook.SUCCESS];
            }
            if (this.esnr == null) {
                this.esnr = new float[EmbmsOemHook.SUCCESS];
            }
            if (this.tmgiPerMbsfn == null) {
                this.tmgiPerMbsfn = new int[EmbmsOemHook.SUCCESS];
            }
            if (this.mbsfnAreaId == null) {
                this.mbsfnAreaId = new int[EmbmsOemHook.SUCCESS];
            }
            if (this.tmgilist == null) {
                this.tmgilist = new byte[EmbmsOemHook.SUCCESS];
            }
        }
    }

    public class ActDeactResponse {
        public short actCode;
        public byte[] actTmgi;
        public short deactCode;
        public byte[] deactTmgi;
        public int status;
        public int traceId;

        public ActDeactResponse(int status, ByteBuffer buf) {
            this.actCode = EmbmsOemHook.EMBMSHOOK_MSG_ID_ENABLE;
            this.deactCode = EmbmsOemHook.EMBMSHOOK_MSG_ID_ENABLE;
            this.traceId = EmbmsOemHook.SUCCESS;
            this.actTmgi = null;
            this.deactTmgi = null;
            this.status = status;
            while (buf.hasRemaining()) {
                int type = PrimitiveParser.toUnsigned(buf.get());
                PrimitiveParser.toUnsigned(buf.getShort());
                switch (type) {
                    case 1:
                        this.traceId = buf.getInt();
                        Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                        break;
                    case 2:
                        this.actCode = buf.getShort();
                        Log.i(EmbmsOemHook.LOG_TAG, "Act code = " + ((int) this.actCode));
                        break;
                    case 3:
                        this.deactCode = buf.getShort();
                        Log.i(EmbmsOemHook.LOG_TAG, "Deact code = " + ((int) this.deactCode));
                        break;
                    case 16:
                        byte id = buf.get();
                        Log.i(EmbmsOemHook.LOG_TAG, "callid = " + ((int) id));
                        break;
                    case 17:
                        int i = buf.get();
                        byte[] tmgi = new byte[i];
                        for (int i2 = EmbmsOemHook.SUCCESS; i2 < i; i2++) {
                            tmgi[i2] = buf.get();
                        }
                        this.actTmgi = tmgi;
                        Log.i(EmbmsOemHook.LOG_TAG, "Act tmgi = " + EmbmsOemHook.bytesToHexString(this.actTmgi));
                        break;
                    case 18:
                        int i3 = buf.get();
                        byte[] tmgi2 = new byte[i3];
                        for (int i4 = EmbmsOemHook.SUCCESS; i4 < i3; i4++) {
                            tmgi2[i4] = buf.get();
                        }
                        this.deactTmgi = tmgi2;
                        Log.i(EmbmsOemHook.LOG_TAG, "Deact tmgi = " + EmbmsOemHook.bytesToHexString(this.deactTmgi));
                        break;
                    default:
                        Log.e(EmbmsOemHook.LOG_TAG, "TmgiResponse: Unexpected Type " + type);
                        break;
                }
            }
        }
    }

    public class TimeResponse {
        public boolean additionalInfo;
        public int code;
        public boolean dayLightSaving;
        public byte leapSeconds;
        public long localTimeOffset;
        public int status;
        public long timeMseconds;
        public int traceId;

        public TimeResponse(int status, ByteBuffer buf) {
            this.code = EmbmsOemHook.SUCCESS;
            this.timeMseconds = 0L;
            this.additionalInfo = false;
            this.dayLightSaving = false;
            this.leapSeconds = (byte) 0;
            this.traceId = EmbmsOemHook.SUCCESS;
            this.localTimeOffset = 0L;
            this.status = status;
            while (buf.hasRemaining()) {
                try {
                    int type = PrimitiveParser.toUnsigned(buf.get());
                    PrimitiveParser.toUnsigned(buf.getShort());
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            break;
                        case 2:
                            this.code = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "code = " + this.code);
                            break;
                        case 3:
                            this.timeMseconds = buf.getLong();
                            Log.i(EmbmsOemHook.LOG_TAG, "timeMseconds = " + this.timeMseconds);
                            break;
                        case 16:
                            this.additionalInfo = true;
                            byte isdayLightSaving = buf.get();
                            if (isdayLightSaving == 1) {
                                this.dayLightSaving = true;
                            }
                            Log.i(EmbmsOemHook.LOG_TAG, "dayLightSaving = " + this.dayLightSaving);
                            break;
                        case 17:
                            this.additionalInfo = true;
                            this.leapSeconds = buf.get();
                            Log.i(EmbmsOemHook.LOG_TAG, "leapSeconds = " + ((int) this.leapSeconds));
                            break;
                        case 18:
                            this.additionalInfo = true;
                            this.localTimeOffset = buf.get();
                            Log.i(EmbmsOemHook.LOG_TAG, "localTimeOffset = " + this.localTimeOffset);
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "TimeResponse: Unexpected Type " + type);
                            break;
                    }
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Invalid format of byte buffer received in TimeResponse");
                }
            }
            Log.i(EmbmsOemHook.LOG_TAG, "additionalInfo = " + this.additionalInfo);
        }

        public TimeResponse(int traceId, int status, long timeMseconds, boolean additonalInfo, long localTimeOffset, boolean dayLightSaving, byte leapSeconds) {
            this.code = EmbmsOemHook.SUCCESS;
            this.timeMseconds = 0L;
            this.additionalInfo = false;
            this.dayLightSaving = false;
            this.leapSeconds = (byte) 0;
            this.traceId = EmbmsOemHook.SUCCESS;
            this.localTimeOffset = 0L;
            this.status = status;
            this.traceId = traceId;
            this.code = EmbmsOemHook.SUCCESS;
            this.timeMseconds = timeMseconds;
            this.localTimeOffset = localTimeOffset;
            this.additionalInfo = this.additionalInfo;
            this.dayLightSaving = dayLightSaving;
            this.leapSeconds = leapSeconds;
            Log.i(EmbmsOemHook.LOG_TAG, "TimeResponse: traceId = " + this.traceId + " code = " + this.code + " timeMseconds = " + this.timeMseconds + "additionalInfo = " + this.additionalInfo + " localTimeOffset = " + this.localTimeOffset + " dayLightSaving = " + this.dayLightSaving + " leapSeconds = " + ((int) this.leapSeconds));
        }
    }

    public class ActiveLogPacketIDsResponse {
        public int[] activePacketIdList;
        public int status;
        public int traceId;

        public ActiveLogPacketIDsResponse(int status, ByteBuffer buf) {
            this.traceId = EmbmsOemHook.SUCCESS;
            this.activePacketIdList = null;
            this.status = status;
            while (buf.hasRemaining()) {
                try {
                    int type = PrimitiveParser.toUnsigned(buf.get());
                    PrimitiveParser.toUnsigned(buf.getShort());
                    switch (type) {
                        case 1:
                            this.traceId = buf.getInt();
                            Log.i(EmbmsOemHook.LOG_TAG, "traceId = " + this.traceId);
                            break;
                        case 2:
                            int i = buf.getShort();
                            int[] activeLogPacketIdListArray = new int[i];
                            for (int i2 = EmbmsOemHook.SUCCESS; i2 < i; i2++) {
                                activeLogPacketIdListArray[i2] = buf.getInt();
                            }
                            this.activePacketIdList = activeLogPacketIdListArray;
                            Log.i(EmbmsOemHook.LOG_TAG, "Active log packet Id's = " + Arrays.toString(this.activePacketIdList));
                            break;
                        default:
                            Log.e(EmbmsOemHook.LOG_TAG, "ActiveLogPacketIDsResponse: Unexpected Type " + type);
                            break;
                    }
                } catch (BufferUnderflowException e) {
                    Log.e(EmbmsOemHook.LOG_TAG, "Invalid format of byte buffer received in ActiveLogPacketIDsResponse");
                }
            }
        }
    }
}
