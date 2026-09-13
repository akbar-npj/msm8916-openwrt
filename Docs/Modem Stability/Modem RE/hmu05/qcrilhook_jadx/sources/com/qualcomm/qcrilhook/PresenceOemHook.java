package com.qualcomm.qcrilhook;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class PresenceOemHook {
    public static final int OEM_HOOK_UNSOL_IND = 1;
    private static final short PRESENCE_SERVICE_ID = 3;
    public static final short QCRILHOOK_PRESENCE_IMS_ENABLER_STATE_REQ = 36;
    public static final short QCRILHOOK_PRESENCE_IMS_GET_EVENT_REPORT_REQ = 46;
    public static final short QCRILHOOK_PRESENCE_IMS_GET_NOTIFY_FMT_REQ = 44;
    public static final short QCRILHOOK_PRESENCE_IMS_SEND_PUBLISH_REQ = 37;
    public static final short QCRILHOOK_PRESENCE_IMS_SEND_PUBLISH_XML_REQ = 38;
    public static final short QCRILHOOK_PRESENCE_IMS_SEND_SUBSCRIBE_REQ = 40;
    public static final short QCRILHOOK_PRESENCE_IMS_SEND_SUBSCRIBE_XML_REQ = 41;
    public static final short QCRILHOOK_PRESENCE_IMS_SEND_UNPUBLISH_REQ = 39;
    public static final short QCRILHOOK_PRESENCE_IMS_SEND_UNSUBSCRIBE_REQ = 42;
    public static final short QCRILHOOK_PRESENCE_IMS_SET_EVENT_REPORT_REQ = 45;
    public static final short QCRILHOOK_PRESENCE_IMS_SET_NOTIFY_FMT_REQ = 43;
    public static final short QCRILHOOK_PRESENCE_IMS_UNSOL_ENABLER_STATE = 35;
    public static final short QCRILHOOK_PRESENCE_IMS_UNSOL_NOTIFY_UPDATE = 34;
    public static final short QCRILHOOK_PRESENCE_IMS_UNSOL_NOTIFY_XML_UPDATE = 33;
    public static final short QCRILHOOK_PRESENCE_IMS_UNSOL_PUBLISH_TRIGGER = 32;
    private static PresenceOemHook mInstance;
    Context mContext;
    private QmiOemHook mQmiOemHook;
    private static String LOG_TAG = "PresenceOemHook";
    public static final String[] IMS_ENABLER_RESPONSE = {"UNKNOWN", "UNINITIALIZED", "INITIALIZED", "AIRPLANE", "REGISTERED"};
    private static int mRefCount = 0;

    public static class PresenceSolResponse {
        public Object data;
        public int result;
    }

    public static class PresenceUnsolIndication {
        public Object obj;
        public int oemHookMesgId;
    }

    public enum SubscriptionType {
        NONE,
        SIMPLE,
        POLLING
    }

    private PresenceOemHook(Context context, Looper listenerLooper) {
        this.mContext = context;
        this.mQmiOemHook = QmiOemHook.getInstance(context, listenerLooper);
    }

    public static PresenceOemHook getInstance(Context context, Handler listenerHandler) {
        if (mInstance == null) {
            mInstance = new PresenceOemHook(context, listenerHandler.getLooper());
            QmiOemHook.registerService(PRESENCE_SERVICE_ID, listenerHandler, 1);
            Log.v(LOG_TAG, "Registered PresenceOemHook with QmiOemHook");
        } else {
            mInstance.mContext = context;
        }
        mRefCount++;
        return mInstance;
    }

    public synchronized void dispose() {
        mRefCount--;
        if (mRefCount == 0) {
            Log.v(LOG_TAG, "dispose(): Unregistering service");
            QmiOemHook.unregisterService(3);
            this.mQmiOemHook.dispose();
            mInstance = null;
        } else {
            Log.v(LOG_TAG, "dispose mRefCount = " + mRefCount);
        }
    }

    public Object imsp_get_enabler_state_req() {
        PresenceMsgBuilder.NoTlvPayloadRequest req = new PresenceMsgBuilder.NoTlvPayloadRequest();
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 36, req.getTypes(), req.getItems());
            return receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Integer imsp_send_publish_req(int publish_status, String contact_uri, String description, String ver, String service_id, int is_audio_supported, int audio_capability, int is_video_supported, int video_capability) {
        PresenceMsgBuilder.Publish.PublishStructRequest req = new PresenceMsgBuilder.Publish.PublishStructRequest(publish_status, contact_uri, description, ver, service_id, is_audio_supported, audio_capability, is_video_supported, video_capability);
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 37, req.getTypes(), req.getItems());
            return (Integer) receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Integer imsp_send_publish_xml_req(String xml) {
        PresenceMsgBuilder.Publish.PublishXMLRequest req = new PresenceMsgBuilder.Publish.PublishXMLRequest(xml);
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 38, req.getTypes(), req.getItems());
            return (Integer) receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Integer imsp_send_unpublish_req() {
        new PresenceMsgBuilder.UnPublish.UnPublishRequest();
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 39, (short[]) null, (BaseQmiTypes.BaseQmiItemType[]) null);
            return (Integer) receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Integer imsp_send_subscribe_req(SubscriptionType subscriptionType, ArrayList<String> contactList) {
        PresenceMsgBuilder.Subscribe.SubscribeStructRequest req = new PresenceMsgBuilder.Subscribe.SubscribeStructRequest(subscriptionType, contactList);
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 40, req.getTypes(), req.getItems());
            return (Integer) receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Integer imsp_send_subscribe_xml_req(String xml) {
        PresenceMsgBuilder.Subscribe.SubscribeXMLRequest req = new PresenceMsgBuilder.Subscribe.SubscribeXMLRequest(xml);
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 41, req.getTypes(), req.getItems());
            return (Integer) receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Integer imsp_send_unsubscribe_req(String peerURI) {
        PresenceMsgBuilder.UnSubscribe.UnSubscribeRequest req = new PresenceMsgBuilder.UnSubscribe.UnSubscribeRequest(peerURI);
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 42, req.getTypes(), req.getItems());
            return (Integer) receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object imsp_set_notify_fmt_req(int flag) {
        PresenceMsgBuilder.NotifyFmt.SetFmt req = new PresenceMsgBuilder.NotifyFmt.SetFmt((short) flag);
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 43, req.getTypes(), req.getItems());
            return receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object imsp_get_notify_fmt_req() {
        new PresenceMsgBuilder.NoTlvPayloadRequest();
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 44, (short[]) null, (BaseQmiTypes.BaseQmiItemType[]) null);
            return receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object imsp_set_event_report_req(int mask) {
        PresenceMsgBuilder.EventReport.SetEventReport req = new PresenceMsgBuilder.EventReport.SetEventReport(mask);
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 45, req.getTypes(), req.getItems());
            return receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object imsp_get_event_report_req() {
        new PresenceMsgBuilder.NoTlvPayloadRequest();
        try {
            HashMap<Integer, Object> hashMap = this.mQmiOemHook.sendQmiMessageSync(PRESENCE_SERVICE_ID, (short) 46, (short[]) null, (BaseQmiTypes.BaseQmiItemType[]) null);
            return receive(hashMap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
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
        Object objValueOf = Integer.valueOf(successStatus);
        switch (messageId) {
            case 32:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_UNSOL_PUBLISH_TRIGGER=" + successStatus);
                int val = PresenceMsgParser.parsePublishTrigger(respByteBuf);
                PresenceUnsolIndication ind = new PresenceUnsolIndication();
                ind.oemHookMesgId = 32;
                ind.obj = Integer.valueOf(val);
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_UNSOL_PUBLISH_TRIGGER result=" + successStatus + " publish_trigger=" + val);
                objValueOf = ind;
                break;
            case 33:
                Log.v(LOG_TAG, "Ind: QCRILHOOK_PRESENCE_IMS_UNSOL_NOTIFY_XML_UPDATE=" + successStatus);
                String xml = PresenceMsgParser.parseNotifyUpdateXML(respByteBuf);
                PresenceUnsolIndication presenceUnSolInd = new PresenceUnsolIndication();
                presenceUnSolInd.oemHookMesgId = 33;
                presenceUnSolInd.obj = xml;
                objValueOf = presenceUnSolInd;
                break;
            case 34:
                Log.v(LOG_TAG, "Ind: QCRILHOOK_PRESENCE_IMS_UNSOL_NOTIFY_UPDATE=" + successStatus);
                PresenceUnsolIndication presenceUnSolInd2 = new PresenceUnsolIndication();
                presenceUnSolInd2.oemHookMesgId = 34;
                presenceUnSolInd2.obj = PresenceMsgParser.parseNotifyUpdate(respByteBuf, responseSize, successStatus);
                objValueOf = presenceUnSolInd2;
                break;
            case 35:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_UNSOL_ENABLER_STATE=" + successStatus);
                int val2 = PresenceMsgParser.parseEnablerStateInd(respByteBuf);
                PresenceUnsolIndication ind2 = new PresenceUnsolIndication();
                ind2.oemHookMesgId = 35;
                ind2.obj = Integer.valueOf(val2);
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_UNSOL_ENABLER_STATE result=" + successStatus + " enabler_state=" + val2);
                objValueOf = ind2;
                break;
            case 36:
                PresenceSolResponse presenceSolResp = new PresenceSolResponse();
                if (successStatus == 0) {
                    int enablerState = PresenceMsgParser.parseEnablerState(respByteBuf);
                    Log.v(LOG_TAG, "Enabler state = " + enablerState);
                    presenceSolResp.result = successStatus;
                    presenceSolResp.data = Integer.valueOf(enablerState);
                    objValueOf = presenceSolResp;
                    String state = IMS_ENABLER_RESPONSE[enablerState];
                    Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_ENABLER_STATE_REQ=" + state);
                } else {
                    Log.v(LOG_TAG, "OemHookError: QCRILHOOK_PRESENCE_IMS_ENABLER_STATE_REQ=" + successStatus);
                    presenceSolResp.result = successStatus;
                    presenceSolResp.data = 0;
                    return presenceSolResp;
                }
                break;
            case 37:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_SEND_PUBLISH_REQ=" + successStatus);
                break;
            case 38:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_SEND_PUBLISH_XML_REQ=" + successStatus);
                break;
            case 39:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_SEND_UNPUBLISH_REQ=" + successStatus);
                break;
            case 40:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_SEND_SUBSCRIBE_REQ=" + successStatus);
                break;
            case 41:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_SEND_SUBSCRIBE_XML_REQ=" + successStatus);
                break;
            case 42:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_SEND_UNSUBSCRIBE_REQ=" + successStatus);
                break;
            case 43:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_SET_NOTIFY_FMT_REQ=" + successStatus);
                PresenceSolResponse presenceSolResp2 = new PresenceSolResponse();
                presenceSolResp2.result = successStatus;
                presenceSolResp2.data = -1;
                objValueOf = presenceSolResp2;
                break;
            case 44:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_GET_NOTIFY_FMT_REQ=" + successStatus);
                PresenceSolResponse presenceSolResp3 = new PresenceSolResponse();
                if (successStatus == 0) {
                    int val3 = PresenceMsgParser.parseGetNotifyReq(respByteBuf);
                    presenceSolResp3.result = successStatus;
                    presenceSolResp3.data = Integer.valueOf(val3);
                    Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_GET_NOTIFY_FMT_REQ update_with_struct_info=" + val3);
                } else {
                    presenceSolResp3.result = successStatus;
                    presenceSolResp3.data = -1;
                }
                objValueOf = presenceSolResp3;
                break;
            case 45:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_SET_EVENT_REPORT_REQ=" + successStatus);
                PresenceSolResponse presenceSolResp4 = new PresenceSolResponse();
                presenceSolResp4.result = successStatus;
                presenceSolResp4.data = -1;
                objValueOf = presenceSolResp4;
                break;
            case 46:
                Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_GET_EVENT_REPORT_REQ=" + successStatus);
                PresenceSolResponse presenceSolResp5 = new PresenceSolResponse();
                if (successStatus == 0) {
                    int val4 = PresenceMsgParser.parseGetEventReport(respByteBuf);
                    presenceSolResp5.result = successStatus;
                    presenceSolResp5.data = Integer.valueOf(val4);
                    Log.v(LOG_TAG, "Response: QCRILHOOK_PRESENCE_IMS_GET_EVENT_REPORT_REQ event_report_bit_masks=" + val4);
                } else {
                    presenceSolResp5.result = successStatus;
                    presenceSolResp5.data = -1;
                }
                objValueOf = presenceSolResp5;
                break;
        }
        return objValueOf;
    }

    public static Object handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                AsyncResult ar = (AsyncResult) msg.obj;
                HashMap<Integer, Object> map = (HashMap) ar.result;
                if (map == null) {
                    Log.e(LOG_TAG, "Hashmap async userobj is NULL");
                    return null;
                }
                return receive(map);
            default:
                Log.d(LOG_TAG, "Recieved msg.what=" + msg.what);
                return null;
        }
    }

    protected void finalize() {
        Log.v(LOG_TAG, "finalize() hit");
    }
}
