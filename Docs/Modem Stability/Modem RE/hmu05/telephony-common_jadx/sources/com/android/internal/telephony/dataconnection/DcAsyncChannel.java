package com.android.internal.telephony.dataconnection;

import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.ProxyProperties;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.util.AsyncChannel;

/* JADX INFO: loaded from: classes.dex */
public class DcAsyncChannel extends AsyncChannel {
    public static final int BASE = 266240;
    private static final int CMD_TO_STRING_COUNT = 14;
    private static final boolean DBG = false;
    public static final int REQ_GET_APNSETTING = 266244;
    public static final int REQ_GET_CID = 266242;
    public static final int REQ_GET_LINK_CAPABILITIES = 266250;
    public static final int REQ_GET_LINK_PROPERTIES = 266246;
    public static final int REQ_IS_INACTIVE = 266240;
    public static final int REQ_RESET = 266252;
    public static final int REQ_SET_LINK_PROPERTIES_HTTP_PROXY = 266248;
    public static final int RSP_GET_APNSETTING = 266245;
    public static final int RSP_GET_CID = 266243;
    public static final int RSP_GET_LINK_CAPABILITIES = 266251;
    public static final int RSP_GET_LINK_PROPERTIES = 266247;
    public static final int RSP_IS_INACTIVE = 266241;
    public static final int RSP_RESET = 266253;
    public static final int RSP_SET_LINK_PROPERTIES_HTTP_PROXY = 266249;
    private static String[] sCmdToString = new String[14];
    private DataConnection mDc;
    private long mDcThreadId;
    private String mLogTag;

    static {
        sCmdToString[0] = "REQ_IS_INACTIVE";
        sCmdToString[1] = "RSP_IS_INACTIVE";
        sCmdToString[2] = "REQ_GET_CID";
        sCmdToString[3] = "RSP_GET_CID";
        sCmdToString[4] = "REQ_GET_APNSETTING";
        sCmdToString[5] = "RSP_GET_APNSETTING";
        sCmdToString[6] = "REQ_GET_LINK_PROPERTIES";
        sCmdToString[7] = "RSP_GET_LINK_PROPERTIES";
        sCmdToString[8] = "REQ_SET_LINK_PROPERTIES_HTTP_PROXY";
        sCmdToString[9] = "RSP_SET_LINK_PROPERTIES_HTTP_PROXY";
        sCmdToString[10] = "REQ_GET_LINK_CAPABILITIES";
        sCmdToString[11] = "RSP_GET_LINK_CAPABILITIES";
        sCmdToString[12] = "REQ_RESET";
        sCmdToString[13] = "RSP_RESET";
    }

    protected static String cmdToString(int cmd) {
        int cmd2 = cmd - 266240;
        return (cmd2 < 0 || cmd2 >= sCmdToString.length) ? AsyncChannel.cmdToString(cmd2 + 266240) : sCmdToString[cmd2];
    }

    public enum LinkPropertyChangeAction {
        NONE,
        CHANGED,
        RESET;

        public static LinkPropertyChangeAction fromInt(int value) {
            if (value == NONE.ordinal()) {
                return NONE;
            }
            if (value == CHANGED.ordinal()) {
                return CHANGED;
            }
            if (value == RESET.ordinal()) {
                return RESET;
            }
            throw new RuntimeException("LinkPropertyChangeAction.fromInt: bad value=" + value);
        }
    }

    public DcAsyncChannel(DataConnection dc, String logTag) {
        this.mDc = dc;
        this.mDcThreadId = this.mDc.getHandler().getLooper().getThread().getId();
        this.mLogTag = logTag;
    }

    public void reqIsInactive() {
        sendMessage(266240);
    }

    public boolean rspIsInactive(Message response) {
        return response.arg1 == 1;
    }

    public boolean isInactiveSync() {
        if (isCallerOnDifferentThread()) {
            Message response = sendMessageSynchronously(266240);
            if (response != null && response.what == 266241) {
                boolean value = rspIsInactive(response);
                return value;
            }
            log("rspIsInactive error response=" + response);
            return false;
        }
        boolean value2 = this.mDc.getIsInactive();
        return value2;
    }

    public void reqCid() {
        sendMessage(REQ_GET_CID);
    }

    public int rspCid(Message response) {
        int retVal = response.arg1;
        return retVal;
    }

    public int getCidSync() {
        if (isCallerOnDifferentThread()) {
            Message response = sendMessageSynchronously(REQ_GET_CID);
            if (response != null && response.what == 266243) {
                int value = rspCid(response);
                return value;
            }
            log("rspCid error response=" + response);
            return -1;
        }
        int value2 = this.mDc.getCid();
        return value2;
    }

    public void reqApnSetting() {
        sendMessage(REQ_GET_APNSETTING);
    }

    public DataProfile rspApnSetting(Message response) {
        DataProfile retVal = (DataProfile) response.obj;
        return retVal;
    }

    public DataProfile getApnSettingSync() {
        if (isCallerOnDifferentThread()) {
            Message response = sendMessageSynchronously(REQ_GET_APNSETTING);
            if (response != null && response.what == 266245) {
                DataProfile value = rspApnSetting(response);
                return value;
            }
            log("getApnSetting error response=" + response);
            return null;
        }
        DataProfile value2 = this.mDc.getDataProfile();
        return value2;
    }

    public void reqLinkProperties() {
        sendMessage(REQ_GET_LINK_PROPERTIES);
    }

    public LinkProperties rspLinkProperties(Message response) {
        LinkProperties retVal = (LinkProperties) response.obj;
        return retVal;
    }

    public LinkProperties getLinkPropertiesSync() {
        if (isCallerOnDifferentThread()) {
            Message response = sendMessageSynchronously(REQ_GET_LINK_PROPERTIES);
            if (response != null && response.what == 266247) {
                LinkProperties value = rspLinkProperties(response);
                return value;
            }
            log("getLinkProperties error response=" + response);
            return null;
        }
        LinkProperties value2 = this.mDc.getCopyLinkProperties();
        return value2;
    }

    public void reqSetLinkPropertiesHttpProxy(ProxyProperties proxy) {
        sendMessage(REQ_SET_LINK_PROPERTIES_HTTP_PROXY, proxy);
    }

    public void setLinkPropertiesHttpProxySync(ProxyProperties proxy) {
        if (isCallerOnDifferentThread()) {
            Message response = sendMessageSynchronously(REQ_SET_LINK_PROPERTIES_HTTP_PROXY, proxy);
            if (response == null || response.what != 266249) {
                log("setLinkPropertiesHttpPoxy error response=" + response);
                return;
            }
            return;
        }
        this.mDc.setLinkPropertiesHttpProxy(proxy);
    }

    public void reqLinkCapabilities() {
        sendMessage(REQ_GET_LINK_CAPABILITIES);
    }

    public LinkCapabilities rspLinkCapabilities(Message response) {
        LinkCapabilities retVal = (LinkCapabilities) response.obj;
        return retVal;
    }

    public LinkCapabilities getLinkCapabilitiesSync() {
        if (isCallerOnDifferentThread()) {
            Message response = sendMessageSynchronously(REQ_GET_LINK_CAPABILITIES);
            if (response != null && response.what == 266251) {
                LinkCapabilities value = rspLinkCapabilities(response);
                return value;
            }
            return null;
        }
        LinkCapabilities value2 = this.mDc.getCopyLinkCapabilities();
        return value2;
    }

    public void reqReset() {
        sendMessage(REQ_RESET);
    }

    public void bringUp(ApnContext apnContext, int initialMaxRetry, int profileId, int rilRadioTechnology, Message onCompletedMsg) {
        sendMessage(SmsEnvelope.TELESERVICE_MWI, new DataConnection.ConnectionParams(apnContext, initialMaxRetry, profileId, rilRadioTechnology, onCompletedMsg));
    }

    public void tearDown(ApnContext apnContext, String reason, Message onCompletedMsg) {
        sendMessage(262148, new DataConnection.DisconnectParams(apnContext, reason, onCompletedMsg));
    }

    public void tearDownAll(String reason, Message onCompletedMsg) {
        sendMessage(262150, new DataConnection.DisconnectParams(null, reason, onCompletedMsg));
    }

    public int getDataConnectionIdSync() {
        return this.mDc.getDataConnectionId();
    }

    public String toString() {
        return this.mDc.getName();
    }

    private boolean isCallerOnDifferentThread() {
        long curThreadId = Thread.currentThread().getId();
        return this.mDcThreadId != curThreadId;
    }

    private void log(String s) {
        Rlog.d(this.mLogTag, "DataConnectionAc " + s);
    }
}
