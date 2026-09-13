package com.android.internal.telephony.dataconnection;

import android.app.PendingIntent;
import android.content.Context;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.ProxyProperties;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Patterns;
import android.util.TimeUtils;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.cdma.CallFailCause;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: loaded from: classes.dex */
public final class DataConnection extends StateMachine {
    static final int BASE = 262144;
    private static final int CMD_TO_STRING_COUNT = 12;
    private static final boolean DBG = true;
    private static final String DEFAULT_DATA_RETRY_CONFIG = "default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000";
    static final int EVENT_CONNECT = 262144;
    static final int EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED = 262155;
    static final int EVENT_DATA_STATE_CHANGED = 262151;
    static final int EVENT_DEACTIVATE_DONE = 262147;
    static final int EVENT_DISCONNECT = 262148;
    static final int EVENT_DISCONNECT_ALL = 262150;
    static final int EVENT_GET_LAST_FAIL_DONE = 262146;
    static final int EVENT_LOST_CONNECTION = 262153;
    static final int EVENT_RETRY_CONNECTION = 262154;
    static final int EVENT_RIL_CONNECTED = 262149;
    static final int EVENT_SETUP_DATA_CONNECTION_DONE = 262145;
    static final int EVENT_TEAR_DOWN_NOW = 262152;
    private static final String NULL_IP = "0.0.0.0";
    private static final String SECONDARY_DATA_RETRY_CONFIG = "max_retries=3, 5000, 5000, 5000";
    private static final boolean VDBG = true;
    private static AtomicInteger mInstanceNumber = new AtomicInteger(0);
    private static String[] sCmdToString = new String[12];
    private AsyncChannel mAc;
    private DcActivatingState mActivatingState;
    private DcActiveState mActiveState;
    List<ApnContext> mApnContexts;
    private DataProfile mApnSetting;
    int mCid;
    private ConnectionParams mConnectionParams;
    private long mCreateTime;
    private int mDataRegState;
    private DcController mDcController;
    private DcFailCause mDcFailCause;
    private DcRetryAlarmController mDcRetryAlarmController;
    private DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    private DcTrackerBase mDct;
    private DcDefaultState mDefaultState;
    private DisconnectParams mDisconnectParams;
    private DcDisconnectionErrorCreatingConnection mDisconnectingErrorCreatingConnection;
    private DcDisconnectingState mDisconnectingState;
    private int mId;
    private DcInactiveState mInactiveState;
    private DcFailCause mLastFailCause;
    private long mLastFailTime;
    private LinkCapabilities mLinkCapabilities;
    private LinkProperties mLinkProperties;
    private PhoneBase mPhone;
    PendingIntent mReconnectIntent;
    RetryManager mRetryManager;
    private DcRetryingState mRetryingState;
    private int mRilRat;
    int mTag;
    private Object mUserData;

    static {
        sCmdToString[0] = "EVENT_CONNECT";
        sCmdToString[1] = "EVENT_SETUP_DATA_CONNECTION_DONE";
        sCmdToString[2] = "EVENT_GET_LAST_FAIL_DONE";
        sCmdToString[3] = "EVENT_DEACTIVATE_DONE";
        sCmdToString[4] = "EVENT_DISCONNECT";
        sCmdToString[5] = "EVENT_RIL_CONNECTED";
        sCmdToString[6] = "EVENT_DISCONNECT_ALL";
        sCmdToString[7] = "EVENT_DATA_STATE_CHANGED";
        sCmdToString[8] = "EVENT_TEAR_DOWN_NOW";
        sCmdToString[9] = "EVENT_LOST_CONNECTION";
        sCmdToString[10] = "EVENT_RETRY_CONNECTION";
        sCmdToString[11] = "EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED";
    }

    static class ConnectionParams {
        ApnContext mApnContext;
        int mInitialMaxRetry;
        Message mOnCompletedMsg;
        int mProfileId;
        int mRilRat;
        int mTag;

        ConnectionParams(ApnContext apnContext, int initialMaxRetry, int profileId, int rilRadioTechnology, Message onCompletedMsg) {
            this.mApnContext = apnContext;
            this.mInitialMaxRetry = initialMaxRetry;
            this.mProfileId = profileId;
            this.mRilRat = rilRadioTechnology;
            this.mOnCompletedMsg = onCompletedMsg;
        }

        public String toString() {
            return "{mTag=" + this.mTag + " mApnContext=" + this.mApnContext + " mInitialMaxRetry=" + this.mInitialMaxRetry + " mProfileId=" + this.mProfileId + " mRat=" + this.mRilRat + " mOnCompletedMsg=" + DataConnection.msgToString(this.mOnCompletedMsg) + "}";
        }
    }

    static class DisconnectParams {
        ApnContext mApnContext;
        Message mOnCompletedMsg;
        String mReason;
        int mTag;

        DisconnectParams(ApnContext apnContext, String reason, Message onCompletedMsg) {
            this.mApnContext = apnContext;
            this.mReason = reason;
            this.mOnCompletedMsg = onCompletedMsg;
        }

        public String toString() {
            return "{mTag=" + this.mTag + " mApnContext=" + this.mApnContext + " mReason=" + this.mReason + " mOnCompletedMsg=" + DataConnection.msgToString(this.mOnCompletedMsg) + "}";
        }
    }

    static String cmdToString(int cmd) {
        String value;
        int cmd2 = cmd - SmsEnvelope.TELESERVICE_MWI;
        if (cmd2 >= 0 && cmd2 < sCmdToString.length) {
            value = sCmdToString[cmd2];
        } else {
            value = DcAsyncChannel.cmdToString(cmd2 + SmsEnvelope.TELESERVICE_MWI);
        }
        if (value == null) {
            String value2 = "0x" + Integer.toHexString(cmd2 + SmsEnvelope.TELESERVICE_MWI);
            return value2;
        }
        return value;
    }

    static DataConnection makeDataConnection(PhoneBase phone, int id, DcTrackerBase dct, DcTesterFailBringUpAll failBringUpAll, DcController dcc) {
        String tag = "DC-";
        if (phone.getPhoneType() == 1) {
            tag = "GsmDC-";
        } else if (phone.getPhoneType() == 2) {
            tag = "CdmaDC-";
        }
        DataConnection dc = new DataConnection(phone, tag + mInstanceNumber.incrementAndGet(), id, dct, failBringUpAll, dcc);
        dc.start();
        dc.log("Made " + dc.getName());
        return dc;
    }

    void dispose() {
        log("dispose: call quiteNow()");
        quitNow();
    }

    LinkCapabilities getCopyLinkCapabilities() {
        return new LinkCapabilities(this.mLinkCapabilities);
    }

    LinkProperties getCopyLinkProperties() {
        return new LinkProperties(this.mLinkProperties);
    }

    boolean getIsInactive() {
        return getCurrentState() == this.mInactiveState;
    }

    int getCid() {
        return this.mCid;
    }

    DataProfile getDataProfile() {
        return this.mApnSetting;
    }

    void setLinkPropertiesHttpProxy(ProxyProperties proxy) {
        this.mLinkProperties.setHttpProxy(proxy);
    }

    static class UpdateLinkPropertyResult {
        public LinkProperties newLp;
        public LinkProperties oldLp;
        public DataCallResponse.SetupResult setupResult = DataCallResponse.SetupResult.SUCCESS;

        public UpdateLinkPropertyResult(LinkProperties curLp) {
            this.oldLp = curLp;
            this.newLp = curLp;
        }
    }

    UpdateLinkPropertyResult updateLinkProperty(DataCallResponse newState) {
        UpdateLinkPropertyResult result = new UpdateLinkPropertyResult(this.mLinkProperties);
        if (newState != null) {
            result.newLp = new LinkProperties();
            result.setupResult = setLinkProperties(newState, result.newLp);
            if (result.setupResult != DataCallResponse.SetupResult.SUCCESS) {
                log("updateLinkProperty failed : " + result.setupResult);
            } else {
                result.newLp.setHttpProxy(this.mLinkProperties.getHttpProxy());
                if (!result.oldLp.equals(result.newLp)) {
                    log("updateLinkProperty old LP=" + result.oldLp);
                    log("updateLinkProperty new LP=" + result.newLp);
                }
                this.mLinkProperties = result.newLp;
            }
        }
        return result;
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    private DataConnection(PhoneBase phone, String name, int id, DcTrackerBase dct, DcTesterFailBringUpAll failBringUpAll, DcController dcc) {
        super(name, dcc.getHandler());
        this.mDct = null;
        this.mLinkProperties = new LinkProperties();
        this.mLinkCapabilities = new LinkCapabilities();
        this.mRilRat = Integer.MAX_VALUE;
        this.mDataRegState = Integer.MAX_VALUE;
        this.mApnContexts = null;
        this.mReconnectIntent = null;
        this.mRetryManager = new RetryManager();
        this.mDefaultState = new DcDefaultState();
        this.mInactiveState = new DcInactiveState();
        this.mRetryingState = new DcRetryingState();
        this.mActivatingState = new DcActivatingState();
        this.mActiveState = new DcActiveState();
        this.mDisconnectingState = new DcDisconnectingState();
        this.mDisconnectingErrorCreatingConnection = new DcDisconnectionErrorCreatingConnection();
        setLogRecSize(300);
        setLogOnlyTransitions(true);
        log("DataConnection constructor E");
        this.mPhone = phone;
        this.mDct = dct;
        this.mDcTesterFailBringUpAll = failBringUpAll;
        this.mDcController = dcc;
        this.mId = id;
        this.mCid = -1;
        this.mDcRetryAlarmController = new DcRetryAlarmController(this.mPhone, this);
        this.mRilRat = this.mPhone.getServiceState().getRilDataRadioTechnology();
        this.mDataRegState = this.mPhone.getServiceState().getDataRegState();
        addState(this.mDefaultState);
        addState(this.mInactiveState, this.mDefaultState);
        addState(this.mActivatingState, this.mDefaultState);
        addState(this.mRetryingState, this.mDefaultState);
        addState(this.mActiveState, this.mDefaultState);
        addState(this.mDisconnectingState, this.mDefaultState);
        addState(this.mDisconnectingErrorCreatingConnection, this.mDefaultState);
        setInitialState(this.mInactiveState);
        this.mApnContexts = new ArrayList();
        log("DataConnection constructor X");
    }

    private String getRetryConfig(boolean forDefault) {
        int nt = this.mPhone.getServiceState().getNetworkType();
        if (Build.IS_DEBUGGABLE) {
            String config = SystemProperties.get("test.data_retry_config");
            if (!TextUtils.isEmpty(config)) {
                return config;
            }
        }
        if (nt == 4 || nt == 7 || nt == 5 || nt == 6 || nt == 12 || nt == 14) {
            String config2 = SystemProperties.get("ro.cdma.data_retry_config");
            return config2;
        }
        if (forDefault) {
            String config3 = SystemProperties.get("ro.gsm.data_retry_config");
            return config3;
        }
        String config4 = SystemProperties.get("ro.gsm.2nd_data_retry_config");
        return config4;
    }

    private void configureRetry(boolean forDefault) {
        String retryConfig = getRetryConfig(forDefault);
        if (!this.mRetryManager.configure(retryConfig)) {
            if (forDefault) {
                if (!this.mRetryManager.configure(DEFAULT_DATA_RETRY_CONFIG)) {
                    loge("configureRetry: Could not configure using DEFAULT_DATA_RETRY_CONFIG=default_randomization=2000,5000,10000,20000,40000,80000:5000,160000:5000,320000:5000,640000:5000,1280000:5000,1800000:5000");
                    this.mRetryManager.configure(5, 2000, CallFailCause.CDMA_LOCKED_UNTIL_POWER_CYCLE);
                }
            } else if (!this.mRetryManager.configure(SECONDARY_DATA_RETRY_CONFIG)) {
                loge("configureRetry: Could note configure using SECONDARY_DATA_RETRY_CONFIG=max_retries=3, 5000, 5000, 5000");
                this.mRetryManager.configure(5, 2000, CallFailCause.CDMA_LOCKED_UNTIL_POWER_CYCLE);
            }
        }
        log("configureRetry: forDefault=" + forDefault + " mRetryManager=" + this.mRetryManager);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onConnect(ConnectionParams cp) {
        int dataProfileId;
        String protocol;
        log("onConnect: mApnSetting=" + this.mApnSetting + " cp=" + cp);
        if (this.mDcTesterFailBringUpAll.getDcFailBringUp().mCounter > 0) {
            DataCallResponse response = new DataCallResponse();
            response.version = this.mPhone.mCi.getRilVersion();
            response.status = this.mDcTesterFailBringUpAll.getDcFailBringUp().mFailCause.getErrorCode();
            response.cid = 0;
            response.active = 0;
            response.type = "";
            response.ifname = "";
            response.addresses = new String[0];
            response.dnses = new String[0];
            response.gateways = new String[0];
            response.suggestedRetryTime = this.mDcTesterFailBringUpAll.getDcFailBringUp().mSuggestedRetryTime;
            Message msg = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp);
            AsyncResult.forMessage(msg, response, (Throwable) null);
            sendMessage(msg);
            log("onConnect: FailBringUpAll=" + this.mDcTesterFailBringUpAll.getDcFailBringUp() + " send error response=" + response);
            this.mDcTesterFailBringUpAll.getDcFailBringUp().mCounter--;
            return;
        }
        this.mCreateTime = -1L;
        this.mLastFailTime = -1L;
        this.mLastFailCause = DcFailCause.NONE;
        if (this.mApnSetting.getDataProfileType() == DataProfile.DataProfileType.PROFILE_TYPE_OMH) {
            dataProfileId = this.mApnSetting.getProfileId() + CallFailCause.CDMA_LOCKED_UNTIL_POWER_CYCLE;
            log("OMH profile, dataProfile id = " + dataProfileId);
        } else {
            dataProfileId = cp.mProfileId;
        }
        Message msg2 = obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp);
        msg2.obj = cp;
        int authType = this.mApnSetting.authType;
        if (authType == -1) {
            authType = TextUtils.isEmpty(this.mApnSetting.user) ? 0 : 3;
        }
        if (this.mPhone.getServiceState().getRoaming()) {
            protocol = this.mApnSetting.roamingProtocol;
        } else {
            protocol = this.mApnSetting.protocol;
        }
        this.mPhone.mCi.setupDataCall(Integer.toString(cp.mRilRat + 2), Integer.toString(dataProfileId), this.mApnSetting.apn, this.mApnSetting.user, this.mApnSetting.password, Integer.toString(authType), protocol, msg2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void tearDownData(Object o) {
        int discReason = 0;
        if (o != null && (o instanceof DisconnectParams)) {
            DisconnectParams dp = (DisconnectParams) o;
            if (TextUtils.equals(dp.mReason, Phone.REASON_RADIO_TURNED_OFF)) {
                discReason = 1;
            } else if (TextUtils.equals(dp.mReason, Phone.REASON_PDP_RESET)) {
                discReason = 2;
            }
        }
        if (this.mPhone.mCi.getRadioState().isOn() || this.mPhone.getServiceState().getRilDataRadioTechnology() == 18) {
            log("tearDownData radio is on, call deactivateDataCall");
            this.mPhone.mCi.deactivateDataCall(this.mCid, discReason, obtainMessage(EVENT_DEACTIVATE_DONE, this.mTag, 0, o));
        } else {
            log("tearDownData radio is off sendMessage EVENT_DEACTIVATE_DONE immediately");
            AsyncResult ar = new AsyncResult(o, (Object) null, (Throwable) null);
            sendMessage(obtainMessage(EVENT_DEACTIVATE_DONE, this.mTag, 0, ar));
        }
    }

    private void notifyAllWithEvent(ApnContext alreadySent, int event, String reason) {
        for (ApnContext apnContext : this.mApnContexts) {
            if (apnContext != alreadySent) {
                if (reason != null) {
                    apnContext.setReason(reason);
                }
                Message msg = this.mDct.obtainMessage(event, apnContext);
                AsyncResult.forMessage(msg);
                msg.sendToTarget();
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyAllOfConnected(String reason) {
        notifyAllWithEvent(null, 270336, reason);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyAllOfDisconnectDcRetrying(String reason) {
        notifyAllWithEvent(null, 270370, reason);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyAllDisconnectCompleted(DcFailCause cause) {
        notifyAllWithEvent(null, 270351, cause.toString());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyConnectCompleted(ConnectionParams cp, DcFailCause cause, boolean sendAll) {
        ApnContext alreadySent = null;
        if (cp != null && cp.mOnCompletedMsg != null) {
            Message connectionCompletedMsg = cp.mOnCompletedMsg;
            cp.mOnCompletedMsg = null;
            if (connectionCompletedMsg.obj instanceof ApnContext) {
                alreadySent = (ApnContext) connectionCompletedMsg.obj;
            }
            long timeStamp = System.currentTimeMillis();
            connectionCompletedMsg.arg1 = this.mCid;
            if (cause == DcFailCause.NONE) {
                this.mCreateTime = timeStamp;
                AsyncResult.forMessage(connectionCompletedMsg);
            } else {
                this.mLastFailCause = cause;
                this.mLastFailTime = timeStamp;
                if (cause == null) {
                    cause = DcFailCause.UNKNOWN;
                }
                AsyncResult.forMessage(connectionCompletedMsg, cause, new Throwable(cause.toString()));
            }
            log("notifyConnectCompleted at " + timeStamp + " cause=" + cause + " connectionCompletedMsg=" + msgToString(connectionCompletedMsg));
            connectionCompletedMsg.sendToTarget();
        }
        if (sendAll) {
            notifyAllWithEvent(alreadySent, 270371, cause.toString());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyDisconnectCompleted(DisconnectParams dp, boolean sendAll) {
        log("NotifyDisconnectCompleted");
        ApnContext alreadySent = null;
        String reason = null;
        if (dp != null && dp.mOnCompletedMsg != null) {
            Message msg = dp.mOnCompletedMsg;
            dp.mOnCompletedMsg = null;
            if (msg.obj instanceof ApnContext) {
                alreadySent = (ApnContext) msg.obj;
            }
            reason = dp.mReason;
            Object[] objArr = new Object[2];
            objArr[0] = msg.toString();
            objArr[1] = msg.obj instanceof String ? (String) msg.obj : "<no-reason>";
            log(String.format("msg=%s msg.obj=%s", objArr));
            AsyncResult.forMessage(msg);
            msg.sendToTarget();
        }
        if (sendAll) {
            if (reason == null) {
                reason = DcFailCause.UNKNOWN.toString();
            }
            notifyAllWithEvent(alreadySent, 270351, reason);
        }
        log("NotifyDisconnectCompleted DisconnectParams=" + dp);
    }

    public int getDataConnectionId() {
        return this.mId;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void clearSettings() {
        log("clearSettings");
        this.mCreateTime = -1L;
        this.mLastFailTime = -1L;
        this.mLastFailCause = DcFailCause.NONE;
        this.mCid = -1;
        this.mLinkProperties = new LinkProperties();
        this.mApnContexts.clear();
        this.mApnSetting = null;
        this.mDcFailCause = null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public DataCallResponse.SetupResult onSetupConnectionCompleted(AsyncResult ar) {
        DataCallResponse response = (DataCallResponse) ar.result;
        ConnectionParams cp = (ConnectionParams) ar.userObj;
        if (cp.mTag != this.mTag) {
            log("onSetupConnectionCompleted stale cp.tag=" + cp.mTag + ", mtag=" + this.mTag);
            return DataCallResponse.SetupResult.ERR_Stale;
        }
        if (ar.exception != null) {
            log("onSetupConnectionCompleted failed, ar.exception=" + ar.exception + " response=" + response);
            if ((ar.exception instanceof CommandException) && ((CommandException) ar.exception).getCommandError() == CommandException.Error.RADIO_NOT_AVAILABLE) {
                DataCallResponse.SetupResult result = DataCallResponse.SetupResult.ERR_BadCommand;
                result.mFailCause = DcFailCause.RADIO_NOT_AVAILABLE;
                return result;
            }
            if (response == null || response.version < 4) {
                return DataCallResponse.SetupResult.ERR_GetLastErrorFromRil;
            }
            DataCallResponse.SetupResult result2 = DataCallResponse.SetupResult.ERR_RilError;
            result2.mFailCause = DcFailCause.fromInt(response.status);
            return result2;
        }
        if (response.status != 0) {
            DataCallResponse.SetupResult result3 = DataCallResponse.SetupResult.ERR_RilError;
            result3.mFailCause = DcFailCause.fromInt(response.status);
            return result3;
        }
        log("onSetupConnectionCompleted received DataCallResponse: " + response);
        this.mCid = response.cid;
        return updateLinkProperty(response).setupResult;
    }

    private boolean isDnsOk(String[] domainNameServers) {
        if (NULL_IP.equals(domainNameServers[0]) && NULL_IP.equals(domainNameServers[1]) && !this.mPhone.isDnsCheckDisabled()) {
            ApnSetting apnSetting = (ApnSetting) this.mApnSetting;
            if (!apnSetting.types[0].equals("mms") || !isIpAddress(apnSetting.mmsProxy)) {
                log(String.format("isDnsOk: return false apn.types[0]=%s APN_TYPE_MMS=%s isIpAddress(%s)=%s", apnSetting.types[0], "mms", apnSetting.mmsProxy, Boolean.valueOf(isIpAddress(apnSetting.mmsProxy))));
                return false;
            }
        }
        return true;
    }

    private boolean isIpAddress(String address) {
        if (address == null) {
            return false;
        }
        return Patterns.IP_ADDRESS.matcher(address).matches();
    }

    private DataCallResponse.SetupResult setLinkProperties(DataCallResponse response, LinkProperties lp) {
        String propertyPrefix = "net." + response.ifname + ".";
        String[] dnsServers = {SystemProperties.get(propertyPrefix + "dns1"), SystemProperties.get(propertyPrefix + "dns2")};
        boolean okToUseSystemPropertyDns = isDnsOk(dnsServers);
        return response.setLinkProperties(lp, okToUseSystemPropertyDns);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean initConnection(ConnectionParams cp) {
        ApnContext apnContext = cp.mApnContext;
        if (this.mApnSetting == null) {
            this.mApnSetting = apnContext.getDataProfile();
        } else if (!this.mApnSetting.canHandleType(apnContext.getDataProfileType())) {
            log("initConnection: incompatible apnSetting in ConnectionParams cp=" + cp + " dc=" + this);
            return false;
        }
        this.mTag++;
        this.mConnectionParams = cp;
        this.mConnectionParams.mTag = this.mTag;
        if (!this.mApnContexts.contains(apnContext)) {
            this.mApnContexts.add(apnContext);
        }
        configureRetry(this.mApnSetting.canHandleType("default"));
        this.mRetryManager.setRetryCount(0);
        this.mRetryManager.setCurMaxRetryCount(this.mConnectionParams.mInitialMaxRetry);
        this.mRetryManager.setRetryForever(false);
        log("initConnection:  RefCount=" + this.mApnContexts.size() + " mApnList=" + this.mApnContexts + " mConnectionParams=" + this.mConnectionParams);
        return true;
    }

    private class DcDefaultState extends State {
        private DcDefaultState() {
        }

        public void enter() {
            DataConnection.this.log("DcDefaultState: enter");
            DataConnection.this.mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(DataConnection.this.getHandler(), DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED, null);
            DataConnection.this.mDcController.addDc(DataConnection.this);
        }

        public void exit() {
            DataConnection.this.log("DcDefaultState: exit");
            DataConnection.this.mPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(DataConnection.this.getHandler());
            DataConnection.this.mDcController.removeDc(DataConnection.this);
            if (DataConnection.this.mAc != null) {
                DataConnection.this.mAc.disconnected();
                DataConnection.this.mAc = null;
            }
            DataConnection.this.mDcRetryAlarmController.dispose();
            DataConnection.this.mDcRetryAlarmController = null;
            DataConnection.this.mApnContexts = null;
            DataConnection.this.mReconnectIntent = null;
            DataConnection.this.mDct = null;
            DataConnection.this.mApnSetting = null;
            DataConnection.this.mPhone = null;
            DataConnection.this.mLinkProperties = null;
            DataConnection.this.mLinkCapabilities = null;
            DataConnection.this.mLastFailCause = null;
            DataConnection.this.mUserData = null;
            DataConnection.this.mDcController = null;
            DataConnection.this.mDcTesterFailBringUpAll = null;
        }

        public boolean processMessage(Message msg) {
            DataConnection.this.log("DcDefault msg=" + DataConnection.this.getWhatToString(msg.what) + " RefCount=" + DataConnection.this.mApnContexts.size());
            switch (msg.what) {
                case 69633:
                    if (DataConnection.this.mAc != null) {
                        DataConnection.this.log("Disconnecting to previous connection mAc=" + DataConnection.this.mAc);
                        DataConnection.this.mAc.replyToMessage(msg, 69634, 3);
                    } else {
                        DataConnection.this.mAc = new AsyncChannel();
                        DataConnection.this.mAc.connected((Context) null, DataConnection.this.getHandler(), msg.replyTo);
                        DataConnection.this.log("DcDefaultState: FULL_CONNECTION reply connected");
                        DataConnection.this.mAc.replyToMessage(msg, 69634, 0, DataConnection.this.mId, "hi");
                    }
                    return true;
                case 69636:
                    DataConnection.this.log("CMD_CHANNEL_DISCONNECTED");
                    DataConnection.this.quit();
                    return true;
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    DataConnection.this.log("DcDefaultState: msg.what=EVENT_CONNECT, fail not expected");
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    DataConnection.this.notifyConnectCompleted(cp, DcFailCause.UNKNOWN, false);
                    return true;
                case DataConnection.EVENT_DISCONNECT /* 262148 */:
                    DataConnection.this.log("DcDefaultState deferring msg.what=EVENT_DISCONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                    DataConnection.this.deferMessage(msg);
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /* 262150 */:
                    DataConnection.this.log("DcDefaultState deferring msg.what=EVENT_DISCONNECT_ALL RefCount=" + DataConnection.this.mApnContexts.size());
                    DataConnection.this.deferMessage(msg);
                    return true;
                case DataConnection.EVENT_TEAR_DOWN_NOW /* 262152 */:
                    DataConnection.this.log("DcDefaultState EVENT_TEAR_DOWN_NOW");
                    DataConnection.this.mPhone.mCi.deactivateDataCall(DataConnection.this.mCid, 0, null);
                    return true;
                case DataConnection.EVENT_LOST_CONNECTION /* 262153 */:
                    String s = "DcDefaultState ignore EVENT_LOST_CONNECTION tag=" + msg.arg1 + ":mTag=" + DataConnection.this.mTag;
                    DataConnection.this.logAndAddLogRec(s);
                    return true;
                case DataConnection.EVENT_RETRY_CONNECTION /* 262154 */:
                    String s2 = "DcDefaultState ignore EVENT_RETRY_CONNECTION tag=" + msg.arg1 + ":mTag=" + DataConnection.this.mTag;
                    DataConnection.this.logAndAddLogRec(s2);
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /* 262155 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    Pair<Integer, Integer> drsRatPair = (Pair) ar.result;
                    DataConnection.this.mDataRegState = ((Integer) drsRatPair.first).intValue();
                    DataConnection.this.mRilRat = ((Integer) drsRatPair.second).intValue();
                    DataConnection.this.log("DcDefaultState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED drs=" + DataConnection.this.mDataRegState + " mRilRat=" + DataConnection.this.mRilRat);
                    return true;
                case 266240:
                    boolean val = DataConnection.this.getIsInactive();
                    DataConnection.this.log("REQ_IS_INACTIVE  isInactive=" + val);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_IS_INACTIVE, val ? 1 : 0);
                    return true;
                case DcAsyncChannel.REQ_GET_CID /* 266242 */:
                    int cid = DataConnection.this.getCid();
                    DataConnection.this.log("REQ_GET_CID  cid=" + cid);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_GET_CID, cid);
                    return true;
                case DcAsyncChannel.REQ_GET_APNSETTING /* 266244 */:
                    DataProfile apnSetting = DataConnection.this.getDataProfile();
                    DataConnection.this.log("REQ_GET_APNSETTING  mApnSetting=" + apnSetting);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_GET_APNSETTING, apnSetting);
                    return true;
                case DcAsyncChannel.REQ_GET_LINK_PROPERTIES /* 266246 */:
                    LinkProperties lp = DataConnection.this.getCopyLinkProperties();
                    DataConnection.this.log("REQ_GET_LINK_PROPERTIES linkProperties" + lp);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_GET_LINK_PROPERTIES, lp);
                    return true;
                case DcAsyncChannel.REQ_SET_LINK_PROPERTIES_HTTP_PROXY /* 266248 */:
                    ProxyProperties proxy = (ProxyProperties) msg.obj;
                    DataConnection.this.log("REQ_SET_LINK_PROPERTIES_HTTP_PROXY proxy=" + proxy);
                    DataConnection.this.setLinkPropertiesHttpProxy(proxy);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_SET_LINK_PROPERTIES_HTTP_PROXY);
                    return true;
                case DcAsyncChannel.REQ_GET_LINK_CAPABILITIES /* 266250 */:
                    LinkCapabilities lc = DataConnection.this.getCopyLinkCapabilities();
                    DataConnection.this.log("REQ_GET_LINK_CAPABILITIES linkCapabilities" + lc);
                    DataConnection.this.mAc.replyToMessage(msg, DcAsyncChannel.RSP_GET_LINK_CAPABILITIES, lc);
                    return true;
                case DcAsyncChannel.REQ_RESET /* 266252 */:
                    DataConnection.this.log("DcDefaultState: msg.what=REQ_RESET");
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                default:
                    DataConnection.this.log("DcDefaultState: shouldn't happen but ignore msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return true;
            }
        }
    }

    private class DcInactiveState extends State {
        private DcInactiveState() {
        }

        public void setEnterNotificationParams(ConnectionParams cp, DcFailCause cause) {
            DataConnection.this.log("DcInactiveState: setEnterNoticationParams cp,cause");
            DataConnection.this.mConnectionParams = cp;
            DataConnection.this.mDisconnectParams = null;
            DataConnection.this.mDcFailCause = cause;
        }

        public void setEnterNotificationParams(DisconnectParams dp) {
            DataConnection.this.log("DcInactiveState: setEnterNoticationParams dp");
            DataConnection.this.mConnectionParams = null;
            DataConnection.this.mDisconnectParams = dp;
            DataConnection.this.mDcFailCause = DcFailCause.NONE;
        }

        public void setEnterNotificationParams(DcFailCause cause) {
            DataConnection.this.mConnectionParams = null;
            DataConnection.this.mDisconnectParams = null;
            DataConnection.this.mDcFailCause = cause;
        }

        public void enter() {
            DataConnection.this.mTag++;
            DataConnection.this.log("DcInactiveState: enter() mTag=" + DataConnection.this.mTag);
            if (DataConnection.this.mConnectionParams != null) {
                DataConnection.this.log("DcInactiveState: enter notifyConnectCompleted +ALL failCause=" + DataConnection.this.mDcFailCause);
                DataConnection.this.notifyConnectCompleted(DataConnection.this.mConnectionParams, DataConnection.this.mDcFailCause, true);
            }
            if (DataConnection.this.mDisconnectParams != null) {
                DataConnection.this.log("DcInactiveState: enter notifyDisconnectCompleted +ALL failCause=" + DataConnection.this.mDcFailCause);
                DataConnection.this.notifyDisconnectCompleted(DataConnection.this.mDisconnectParams, true);
            }
            if (DataConnection.this.mDisconnectParams == null && DataConnection.this.mConnectionParams == null && DataConnection.this.mDcFailCause != null) {
                DataConnection.this.log("DcInactiveState: enter notifyAllDisconnectCompleted failCause=" + DataConnection.this.mDcFailCause);
                DataConnection.this.notifyAllDisconnectCompleted(DataConnection.this.mDcFailCause);
            }
            DataConnection.this.mDcController.removeActiveDcByCid(DataConnection.this);
            DataConnection.this.clearSettings();
        }

        public void exit() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    DataConnection.this.log("DcInactiveState: mag.what=EVENT_CONNECT");
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    if (DataConnection.this.initConnection(cp)) {
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        DataConnection.this.log("DcInactiveState: msg.what=EVENT_CONNECT initConnection failed");
                        DataConnection.this.notifyConnectCompleted(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT /* 262148 */:
                    DataConnection.this.log("DcInactiveState: msg.what=EVENT_DISCONNECT");
                    DataConnection.this.notifyDisconnectCompleted((DisconnectParams) msg.obj, false);
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /* 262150 */:
                    DataConnection.this.log("DcInactiveState: msg.what=EVENT_DISCONNECT_ALL");
                    DataConnection.this.notifyDisconnectCompleted((DisconnectParams) msg.obj, false);
                    return true;
                case DcAsyncChannel.REQ_RESET /* 266252 */:
                    DataConnection.this.log("DcInactiveState: msg.what=RSP_RESET, ignore we're already reset");
                    return true;
                default:
                    DataConnection.this.log("DcInactiveState nothandled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    private class DcRetryingState extends State {
        private DcRetryingState() {
        }

        public void enter() {
            if (DataConnection.this.mConnectionParams.mRilRat != DataConnection.this.mRilRat || DataConnection.this.mDataRegState != 0) {
                String s = "DcRetryingState: enter() not retrying rat changed, mConnectionParams.mRilRat=" + DataConnection.this.mConnectionParams.mRilRat + " != mRilRat:" + DataConnection.this.mRilRat + " transitionTo(mInactiveState)";
                DataConnection.this.logAndAddLogRec(s);
                DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                return;
            }
            DataConnection.this.log("DcRetryingState: enter() mTag=" + DataConnection.this.mTag + ", call notifyAllOfDisconnectDcRetrying lostConnection");
            DataConnection.this.notifyAllOfDisconnectDcRetrying(Phone.REASON_LOST_DATA_CONNECTION);
            DataConnection.this.mDcController.removeActiveDcByCid(DataConnection.this);
            DataConnection.this.mCid = -1;
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    DataConnection.this.log("DcRetryingState: msg.what=EVENT_CONNECT RefCount=" + DataConnection.this.mApnContexts.size() + " cp=" + cp + " mConnectionParams=" + DataConnection.this.mConnectionParams);
                    if (DataConnection.this.initConnection(cp)) {
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        DataConnection.this.log("DcRetryingState: msg.what=EVENT_CONNECT initConnection failed");
                        DataConnection.this.notifyConnectCompleted(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT /* 262148 */:
                    DisconnectParams dp = (DisconnectParams) msg.obj;
                    if (DataConnection.this.mApnContexts.remove(dp.mApnContext) && DataConnection.this.mApnContexts.size() == 0) {
                        DataConnection.this.log("DcRetryingState msg.what=EVENT_DISCONNECT  RefCount=" + DataConnection.this.mApnContexts.size() + " dp=" + dp);
                        DataConnection.this.mInactiveState.setEnterNotificationParams(dp);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else {
                        DataConnection.this.log("DcRetryingState: msg.what=EVENT_DISCONNECT");
                        DataConnection.this.notifyDisconnectCompleted(dp, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /* 262150 */:
                    DataConnection.this.log("DcRetryingState msg.what=EVENT_DISCONNECT/DISCONNECT_ALL RefCount=" + DataConnection.this.mApnContexts.size());
                    DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                case DataConnection.EVENT_RETRY_CONNECTION /* 262154 */:
                    if (msg.arg1 == DataConnection.this.mTag) {
                        DataConnection.this.mRetryManager.increaseRetryCount();
                        DataConnection.this.log("DcRetryingState EVENT_RETRY_CONNECTION RetryCount=" + DataConnection.this.mRetryManager.getRetryCount() + " mConnectionParams=" + DataConnection.this.mConnectionParams);
                        DataConnection.this.onConnect(DataConnection.this.mConnectionParams);
                        DataConnection.this.transitionTo(DataConnection.this.mActivatingState);
                    } else {
                        DataConnection.this.log("DcRetryingState stale EVENT_RETRY_CONNECTION tag:" + msg.arg1 + " != mTag:" + DataConnection.this.mTag);
                    }
                    return true;
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /* 262155 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    Pair<Integer, Integer> drsRatPair = (Pair) ar.result;
                    int drs = ((Integer) drsRatPair.first).intValue();
                    int rat = ((Integer) drsRatPair.second).intValue();
                    if (rat == DataConnection.this.mRilRat && drs == DataConnection.this.mDataRegState) {
                        DataConnection.this.log("DcRetryingState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED strange no change in drs=" + drs + " rat=" + rat + " ignoring");
                    } else {
                        if (drs != 0) {
                            DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                            DataConnection.this.deferMessage(msg);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            String s = "DcRetryingState: EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED giving up changed from " + DataConnection.this.mRilRat + " to rat=" + rat + " or drs changed from " + DataConnection.this.mDataRegState + " to drs=" + drs;
                            DataConnection.this.logAndAddLogRec(s);
                        }
                        DataConnection.this.mDataRegState = drs;
                        DataConnection.this.mRilRat = rat;
                    }
                    return true;
                case DcAsyncChannel.REQ_RESET /* 266252 */:
                    DataConnection.this.log("DcRetryingState: msg.what=RSP_RESET, ignore we're already reset");
                    DataConnection.this.mInactiveState.setEnterNotificationParams(DataConnection.this.mConnectionParams, DcFailCause.RESET_BY_FRAMEWORK);
                    DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    return true;
                default:
                    DataConnection.this.log("DcRetryingState nothandled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    private class DcActivatingState extends State {
        private DcActivatingState() {
        }

        public boolean processMessage(Message msg) {
            DataConnection.this.log("DcActivatingState: msg=" + DataConnection.msgToString(msg));
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                case DataConnection.EVENT_DATA_CONNECTION_DRS_OR_RAT_CHANGED /* 262155 */:
                    DataConnection.this.deferMessage(msg);
                    return true;
                case DataConnection.EVENT_SETUP_DATA_CONNECTION_DONE /* 262145 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    ConnectionParams cp = (ConnectionParams) ar.userObj;
                    DataCallResponse.SetupResult result = DataConnection.this.onSetupConnectionCompleted(ar);
                    if (result != DataCallResponse.SetupResult.ERR_Stale && DataConnection.this.mConnectionParams != cp) {
                        DataConnection.this.loge("DcActivatingState: WEIRD mConnectionsParams:" + DataConnection.this.mConnectionParams + " != cp:" + cp);
                    }
                    DataConnection.this.log("DcActivatingState onSetupConnectionCompleted result=" + result + " dc=" + DataConnection.this);
                    switch (result) {
                        case SUCCESS:
                            DataConnection.this.mDcFailCause = DcFailCause.NONE;
                            DataConnection.this.transitionTo(DataConnection.this.mActiveState);
                            return true;
                        case ERR_BadCommand:
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            return true;
                        case ERR_UnacceptableParameter:
                            DataConnection.this.tearDownData(cp);
                            DataConnection.this.transitionTo(DataConnection.this.mDisconnectingErrorCreatingConnection);
                            return true;
                        case ERR_GetLastErrorFromRil:
                            DataConnection.this.mPhone.mCi.getLastDataCallFailCause(DataConnection.this.obtainMessage(DataConnection.EVENT_GET_LAST_FAIL_DONE, cp));
                            return true;
                        case ERR_RilError:
                            int delay = DataConnection.this.mDcRetryAlarmController.getSuggestedRetryTime(DataConnection.this, ar);
                            DataConnection.this.log("DcActivatingState: ERR_RilError  delay=" + delay + " isRetryNeeded=" + DataConnection.this.mRetryManager.isRetryNeeded() + " result=" + result + " result.isRestartRadioFail=" + result.mFailCause.isRestartRadioFail() + " result.isPermanentFail=" + result.mFailCause.isPermanentFail());
                            if (result.mFailCause.isRestartRadioFail()) {
                                DataConnection.this.log("DcActivatingState: ERR_RilError restart radio");
                                DataConnection.this.mDct.sendRestartRadio();
                                DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                                DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            } else if (result.mFailCause.isPermanentFail()) {
                                DataConnection.this.log("DcActivatingState: ERR_RilError perm error");
                                DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                                DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            } else if (delay >= 0) {
                                DataConnection.this.log("DcActivatingState: ERR_RilError retry");
                                DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, delay);
                                DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                            } else {
                                DataConnection.this.log("DcActivatingState: ERR_RilError no retry");
                                DataConnection.this.mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                                DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                            }
                            return true;
                        case ERR_Stale:
                            DataConnection.this.loge("DcActivatingState: stale EVENT_SETUP_DATA_CONNECTION_DONE tag:" + cp.mTag + " != mTag:" + DataConnection.this.mTag);
                            return true;
                        default:
                            throw new RuntimeException("Unknown SetupResult, should not happen");
                    }
                case DataConnection.EVENT_GET_LAST_FAIL_DONE /* 262146 */:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    ConnectionParams cp2 = (ConnectionParams) ar2.userObj;
                    if (cp2.mTag == DataConnection.this.mTag) {
                        if (DataConnection.this.mConnectionParams != cp2) {
                            DataConnection.this.loge("DcActivatingState: WEIRD mConnectionsParams:" + DataConnection.this.mConnectionParams + " != cp:" + cp2);
                        }
                        DcFailCause cause = DcFailCause.UNKNOWN;
                        if (ar2.exception == null) {
                            int rilFailCause = ((int[]) ar2.result)[0];
                            cause = DcFailCause.fromInt(rilFailCause);
                            if (cause == DcFailCause.NONE) {
                                DataConnection.this.log("DcActivatingState msg.what=EVENT_GET_LAST_FAIL_DONE BAD: error was NONE, change to UNKNOWN");
                                cause = DcFailCause.UNKNOWN;
                            }
                        }
                        DataConnection.this.mDcFailCause = cause;
                        int retryDelay = DataConnection.this.mRetryManager.getRetryTimer();
                        DataConnection.this.log("DcActivatingState msg.what=EVENT_GET_LAST_FAIL_DONE cause=" + cause + " retryDelay=" + retryDelay + " isRetryNeeded=" + DataConnection.this.mRetryManager.isRetryNeeded() + " dc=" + DataConnection.this);
                        if (cause.isRestartRadioFail()) {
                            DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE restart radio");
                            DataConnection.this.mDct.sendRestartRadio();
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp2, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        } else if (cause.isPermanentFail()) {
                            DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE perm er");
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp2, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        } else if (retryDelay >= 0 && DataConnection.this.mRetryManager.isRetryNeeded()) {
                            DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE retry");
                            DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, retryDelay);
                            DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                        } else {
                            DataConnection.this.log("DcActivatingState: EVENT_GET_LAST_FAIL_DONE no retry");
                            DataConnection.this.mInactiveState.setEnterNotificationParams(cp2, cause);
                            DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                        }
                    } else {
                        DataConnection.this.loge("DcActivatingState: stale EVENT_GET_LAST_FAIL_DONE tag:" + cp2.mTag + " != mTag:" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    DataConnection.this.log("DcActivatingState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what) + " RefCount=" + DataConnection.this.mApnContexts.size());
                    return false;
            }
        }
    }

    private class DcActiveState extends State {
        private DcActiveState() {
        }

        public void enter() {
            DataConnection.this.log("DcActiveState: enter dc=" + DataConnection.this);
            if (DataConnection.this.mRetryManager.getRetryCount() != 0) {
                DataConnection.this.log("DcActiveState: connected after retrying call notifyAllOfConnected");
                DataConnection.this.mRetryManager.setRetryCount(0);
            }
            DataConnection.this.notifyAllOfConnected(Phone.REASON_CONNECTED);
            DataConnection.this.mRetryManager.restoreCurMaxRetryCount();
            DataConnection.this.mDcController.addActiveDcByCid(DataConnection.this);
        }

        public void exit() {
            DataConnection.this.log("DcActiveState: exit dc=" + this);
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    DataConnection.this.log("DcActiveState: EVENT_CONNECT cp=" + cp + " dc=" + DataConnection.this);
                    if (DataConnection.this.mApnContexts.contains(cp.mApnContext)) {
                        DataConnection.this.log("DcActiveState ERROR already added apnContext=" + cp.mApnContext);
                    } else {
                        DataConnection.this.mApnContexts.add(cp.mApnContext);
                        DataConnection.this.log("DcActiveState msg.what=EVENT_CONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                    }
                    DataConnection.this.notifyConnectCompleted(cp, DcFailCause.NONE, false);
                    return true;
                case DataConnection.EVENT_DISCONNECT /* 262148 */:
                    DisconnectParams dp = (DisconnectParams) msg.obj;
                    DataConnection.this.log("DcActiveState: EVENT_DISCONNECT dp=" + dp + " dc=" + DataConnection.this);
                    if (DataConnection.this.mApnContexts.contains(dp.mApnContext)) {
                        DataConnection.this.log("DcActiveState msg.what=EVENT_DISCONNECT RefCount=" + DataConnection.this.mApnContexts.size());
                        if (DataConnection.this.mApnContexts.size() == 1) {
                            DataConnection.this.mApnContexts.clear();
                            DataConnection.this.mDisconnectParams = dp;
                            DataConnection.this.mConnectionParams = null;
                            dp.mTag = DataConnection.this.mTag;
                            DataConnection.this.tearDownData(dp);
                            DataConnection.this.transitionTo(DataConnection.this.mDisconnectingState);
                        } else {
                            DataConnection.this.mApnContexts.remove(dp.mApnContext);
                            DataConnection.this.notifyDisconnectCompleted(dp, false);
                        }
                    } else {
                        DataConnection.this.log("DcActiveState ERROR no such apnContext=" + dp.mApnContext + " in this dc=" + DataConnection.this);
                        DataConnection.this.notifyDisconnectCompleted(dp, false);
                    }
                    return true;
                case DataConnection.EVENT_DISCONNECT_ALL /* 262150 */:
                    DataConnection.this.log("DcActiveState EVENT_DISCONNECT clearing apn contexts, dc=" + DataConnection.this);
                    DisconnectParams dp2 = (DisconnectParams) msg.obj;
                    DataConnection.this.mDisconnectParams = dp2;
                    DataConnection.this.mConnectionParams = null;
                    dp2.mTag = DataConnection.this.mTag;
                    DataConnection.this.tearDownData(dp2);
                    DataConnection.this.transitionTo(DataConnection.this.mDisconnectingState);
                    return true;
                case DataConnection.EVENT_LOST_CONNECTION /* 262153 */:
                    DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION dc=" + DataConnection.this);
                    if (!DataConnection.this.mRetryManager.isRetryNeeded()) {
                        DataConnection.this.mInactiveState.setEnterNotificationParams(DcFailCause.LOST_CONNECTION);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else {
                        int delayMillis = DataConnection.this.mRetryManager.getRetryTimer();
                        DataConnection.this.log("DcActiveState EVENT_LOST_CONNECTION startRetryAlarm mTag=" + DataConnection.this.mTag + " delay=" + delayMillis + "ms");
                        DataConnection.this.mDcRetryAlarmController.startRetryAlarm(DataConnection.EVENT_RETRY_CONNECTION, DataConnection.this.mTag, delayMillis);
                        DataConnection.this.transitionTo(DataConnection.this.mRetryingState);
                    }
                    return true;
                default:
                    DataConnection.this.log("DcActiveState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    private class DcDisconnectingState extends State {
        private DcDisconnectingState() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case SmsEnvelope.TELESERVICE_MWI /* 262144 */:
                    DataConnection.this.log("DcDisconnectingState msg.what=EVENT_CONNECT. Defer. RefCount = " + DataConnection.this.mApnContexts.size());
                    DataConnection.this.deferMessage(msg);
                    return true;
                case DataConnection.EVENT_SETUP_DATA_CONNECTION_DONE /* 262145 */:
                case DataConnection.EVENT_GET_LAST_FAIL_DONE /* 262146 */:
                default:
                    DataConnection.this.log("DcDisconnectingState not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
                case DataConnection.EVENT_DEACTIVATE_DONE /* 262147 */:
                    DataConnection.this.log("DcDisconnectingState msg.what=EVENT_DEACTIVATE_DONE RefCount=" + DataConnection.this.mApnContexts.size());
                    AsyncResult ar = (AsyncResult) msg.obj;
                    DisconnectParams dp = (DisconnectParams) ar.userObj;
                    if (dp.mTag == DataConnection.this.mTag) {
                        DataConnection.this.mInactiveState.setEnterNotificationParams((DisconnectParams) ar.userObj);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else {
                        DataConnection.this.log("DcDisconnectState stale EVENT_DEACTIVATE_DONE dp.tag=" + dp.mTag + " mTag=" + DataConnection.this.mTag);
                    }
                    return true;
            }
        }
    }

    private class DcDisconnectionErrorCreatingConnection extends State {
        private DcDisconnectionErrorCreatingConnection() {
        }

        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case DataConnection.EVENT_DEACTIVATE_DONE /* 262147 */:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    ConnectionParams cp = (ConnectionParams) ar.userObj;
                    if (cp.mTag == DataConnection.this.mTag) {
                        DataConnection.this.log("DcDisconnectionErrorCreatingConnection msg.what=EVENT_DEACTIVATE_DONE");
                        DataConnection.this.mInactiveState.setEnterNotificationParams(cp, DcFailCause.UNACCEPTABLE_NETWORK_PARAMETER);
                        DataConnection.this.transitionTo(DataConnection.this.mInactiveState);
                    } else {
                        DataConnection.this.log("DcDisconnectionErrorCreatingConnection stale EVENT_DEACTIVATE_DONE dp.tag=" + cp.mTag + ", mTag=" + DataConnection.this.mTag);
                    }
                    return true;
                default:
                    DataConnection.this.log("DcDisconnectionErrorCreatingConnection not handled msg.what=" + DataConnection.this.getWhatToString(msg.what));
                    return false;
            }
        }
    }

    void tearDownNow() {
        log("tearDownNow()");
        sendMessage(obtainMessage(EVENT_TEAR_DOWN_NOW));
    }

    protected String getWhatToString(int what) {
        return cmdToString(what);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String msgToString(Message msg) {
        if (msg == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder();
        b.append("{what=");
        b.append(cmdToString(msg.what));
        b.append(" when=");
        TimeUtils.formatDuration(msg.getWhen() - SystemClock.uptimeMillis(), b);
        if (msg.arg1 != 0) {
            b.append(" arg1=");
            b.append(msg.arg1);
        }
        if (msg.arg2 != 0) {
            b.append(" arg2=");
            b.append(msg.arg2);
        }
        if (msg.obj != null) {
            b.append(" obj=");
            b.append(msg.obj);
        }
        b.append(" target=");
        b.append(msg.getTarget());
        b.append(" replyTo=");
        b.append(msg.replyTo);
        b.append("}");
        String retVal = b.toString();
        return retVal;
    }

    static void slog(String s) {
        Rlog.d("DC", s);
    }

    protected void log(String s) {
        Rlog.d(getName(), s);
    }

    protected void logd(String s) {
        Rlog.d(getName(), s);
    }

    protected void logv(String s) {
        Rlog.v(getName(), s);
    }

    protected void logi(String s) {
        Rlog.i(getName(), s);
    }

    protected void logw(String s) {
        Rlog.w(getName(), s);
    }

    protected void loge(String s) {
        Rlog.e(getName(), s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(getName(), s, e);
    }

    public String toStringSimple() {
        return getName() + ": State=" + getCurrentState().getName() + " mApnSetting=" + this.mApnSetting + " RefCount=" + this.mApnContexts.size() + " mCid=" + this.mCid + " mCreateTime=" + this.mCreateTime + " mLastastFailTime=" + this.mLastFailTime + " mLastFailCause=" + this.mLastFailCause + " mTag=" + this.mTag + " mRetryManager=" + this.mRetryManager + " mLinkProperties=" + this.mLinkProperties + " mLinkCapabilities=" + this.mLinkCapabilities;
    }

    public String toString() {
        return "{" + toStringSimple() + " mApnContexts=" + this.mApnContexts + "}";
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("DataConnection ");
        super.dump(fd, pw, args);
        pw.println(" mApnContexts.size=" + this.mApnContexts.size());
        pw.println(" mApnContexts=" + this.mApnContexts);
        pw.flush();
        pw.println(" mDataConnectionTracker=" + this.mDct);
        pw.println(" mApnSetting=" + this.mApnSetting);
        pw.println(" mTag=" + this.mTag);
        pw.println(" mCid=" + this.mCid);
        pw.println(" mRetryManager=" + this.mRetryManager);
        pw.println(" mConnectionParams=" + this.mConnectionParams);
        pw.println(" mDisconnectParams=" + this.mDisconnectParams);
        pw.println(" mDcFailCause=" + this.mDcFailCause);
        pw.flush();
        pw.println(" mPhone=" + this.mPhone);
        pw.flush();
        pw.println(" mLinkProperties=" + this.mLinkProperties);
        pw.flush();
        pw.println(" mDataRegState=" + this.mDataRegState);
        pw.println(" mRilRat=" + this.mRilRat);
        pw.println(" mLinkCapabilities=" + this.mLinkCapabilities);
        pw.println(" mCreateTime=" + TimeUtils.logTimeOfDay(this.mCreateTime));
        pw.println(" mLastFailTime=" + TimeUtils.logTimeOfDay(this.mLastFailTime));
        pw.println(" mLastFailCause=" + this.mLastFailCause);
        pw.flush();
        pw.println(" mUserData=" + this.mUserData);
        pw.println(" mInstanceNumber=" + mInstanceNumber);
        pw.println(" mAc=" + this.mAc);
        pw.println(" mDcRetryAlarmController=" + this.mDcRetryAlarmController);
        pw.flush();
    }
}
