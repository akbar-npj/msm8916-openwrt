package com.android.phone;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.CallDetails;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.services.telephony.common.Call;
import com.google.android.collect.Maps;
import com.google.android.collect.Sets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: loaded from: classes.dex */
public class CallModeler extends Handler {
    private static final String TAG = CallModeler.class.getSimpleName();
    private final CallGatewayManager mCallGatewayManager;
    private final CallManager mCallManager;
    private final CallStateMonitor mCallStateMonitor;
    private Connection mCdmaIncomingConnection;
    private Connection mCdmaOutgoingConnection;
    private SuppServiceNotification mSuppSvcNotification;
    private final HashMap<Connection, Call> mCallMap = Maps.newHashMap();
    private final HashMap<Connection, Call> mConfCallMap = Maps.newHashMap();
    private final AtomicInteger mNextCallId = new AtomicInteger(1);
    private final ArrayList<Listener> mListeners = new ArrayList<>();
    private boolean videocallstarted = false;
    private long videocallstarttime = 0;
    private long videocallDuration = 0;
    private String VIDEO_CALL_DURATION_KEY = "video_call_duration_key";
    private int mConfVersion = -1;
    private final ImmutableMap<Connection.DisconnectCause, Call.DisconnectCause> CAUSE_MAP = ImmutableMap.builder().put(Connection.DisconnectCause.BUSY, Call.DisconnectCause.BUSY).put(Connection.DisconnectCause.CALL_BARRED, Call.DisconnectCause.CALL_BARRED).put(Connection.DisconnectCause.CDMA_ACCESS_BLOCKED, Call.DisconnectCause.CDMA_ACCESS_BLOCKED).put(Connection.DisconnectCause.CDMA_ACCESS_FAILURE, Call.DisconnectCause.CDMA_ACCESS_FAILURE).put(Connection.DisconnectCause.CDMA_DROP, Call.DisconnectCause.CDMA_DROP).put(Connection.DisconnectCause.CDMA_INTERCEPT, Call.DisconnectCause.CDMA_INTERCEPT).put(Connection.DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE, Call.DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE).put(Connection.DisconnectCause.CDMA_NOT_EMERGENCY, Call.DisconnectCause.CDMA_NOT_EMERGENCY).put(Connection.DisconnectCause.CDMA_PREEMPTED, Call.DisconnectCause.CDMA_PREEMPTED).put(Connection.DisconnectCause.CDMA_REORDER, Call.DisconnectCause.CDMA_REORDER).put(Connection.DisconnectCause.CDMA_RETRY_ORDER, Call.DisconnectCause.CDMA_RETRY_ORDER).put(Connection.DisconnectCause.CDMA_SO_REJECT, Call.DisconnectCause.CDMA_SO_REJECT).put(Connection.DisconnectCause.CONGESTION, Call.DisconnectCause.CONGESTION).put(Connection.DisconnectCause.CS_RESTRICTED, Call.DisconnectCause.CS_RESTRICTED).put(Connection.DisconnectCause.CS_RESTRICTED_EMERGENCY, Call.DisconnectCause.CS_RESTRICTED_EMERGENCY).put(Connection.DisconnectCause.CS_RESTRICTED_NORMAL, Call.DisconnectCause.CS_RESTRICTED_NORMAL).put(Connection.DisconnectCause.ERROR_UNSPECIFIED, Call.DisconnectCause.ERROR_UNSPECIFIED).put(Connection.DisconnectCause.FDN_BLOCKED, Call.DisconnectCause.FDN_BLOCKED).put(Connection.DisconnectCause.ICC_ERROR, Call.DisconnectCause.ICC_ERROR).put(Connection.DisconnectCause.INCOMING_MISSED, Call.DisconnectCause.INCOMING_MISSED).put(Connection.DisconnectCause.INCOMING_REJECTED, Call.DisconnectCause.INCOMING_REJECTED).put(Connection.DisconnectCause.INVALID_CREDENTIALS, Call.DisconnectCause.INVALID_CREDENTIALS).put(Connection.DisconnectCause.INVALID_NUMBER, Call.DisconnectCause.INVALID_NUMBER).put(Connection.DisconnectCause.LIMIT_EXCEEDED, Call.DisconnectCause.LIMIT_EXCEEDED).put(Connection.DisconnectCause.LOCAL, Call.DisconnectCause.LOCAL).put(Connection.DisconnectCause.LOST_SIGNAL, Call.DisconnectCause.LOST_SIGNAL).put(Connection.DisconnectCause.MMI, Call.DisconnectCause.MMI).put(Connection.DisconnectCause.NORMAL, Call.DisconnectCause.NORMAL).put(Connection.DisconnectCause.NOT_DISCONNECTED, Call.DisconnectCause.NOT_DISCONNECTED).put(Connection.DisconnectCause.NUMBER_UNREACHABLE, Call.DisconnectCause.NUMBER_UNREACHABLE).put(Connection.DisconnectCause.OUT_OF_NETWORK, Call.DisconnectCause.OUT_OF_NETWORK).put(Connection.DisconnectCause.OUT_OF_SERVICE, Call.DisconnectCause.OUT_OF_SERVICE).put(Connection.DisconnectCause.POWER_OFF, Call.DisconnectCause.POWER_OFF).put(Connection.DisconnectCause.SERVER_ERROR, Call.DisconnectCause.SERVER_ERROR).put(Connection.DisconnectCause.SERVER_UNREACHABLE, Call.DisconnectCause.SERVER_UNREACHABLE).put(Connection.DisconnectCause.TIMED_OUT, Call.DisconnectCause.TIMED_OUT).put(Connection.DisconnectCause.UNOBTAINABLE_NUMBER, Call.DisconnectCause.UNOBTAINABLE_NUMBER).put(Connection.DisconnectCause.DIAL_MODIFIED_TO_USSD, Call.DisconnectCause.DIAL_MODIFIED_TO_USSD).put(Connection.DisconnectCause.DIAL_MODIFIED_TO_SS, Call.DisconnectCause.DIAL_MODIFIED_TO_SS).put(Connection.DisconnectCause.DIAL_MODIFIED_TO_DIAL, Call.DisconnectCause.DIAL_MODIFIED_TO_DIAL).put(Connection.DisconnectCause.SRVCC_CALL_DROP, Call.DisconnectCause.SRVCC_CALL_DROP).put(Connection.DisconnectCause.CALL_FAIL_MISC, Call.DisconnectCause.CALL_FAIL_MISC).build();

    public interface Listener {
        void onActiveSubChanged(int i);

        void onDisconnect(Call call);

        void onIncoming(Call call);

        void onModifyCall(Call call);

        void onPostDialAction(Connection.PostDialState postDialState, int i, String str, char c);

        void onSuppServiceFailed(int i);

        void onUpdate(List<Call> list);
    }

    public CallModeler(CallStateMonitor callStateMonitor, CallManager callManager, CallGatewayManager callGatewayManager) {
        this.mCallStateMonitor = callStateMonitor;
        this.mCallManager = callManager;
        this.mCallGatewayManager = callGatewayManager;
        this.mCallStateMonitor.addListener(this);
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
            case 4:
                onPhoneStateChanged((AsyncResult) msg.obj);
                break;
            case 3:
                onDisconnect((Connection) ((AsyncResult) msg.obj).result);
                break;
            case 13:
                onPostDialChars((AsyncResult) msg.obj, (char) msg.arg1);
                break;
            case 14:
                Log.d(TAG, "Received Supplementary Notification");
                if (msg.obj != null && ((AsyncResult) msg.obj).result != null) {
                    this.mSuppSvcNotification = (SuppServiceNotification) ((AsyncResult) msg.obj).result;
                    if (this.mSuppSvcNotification.code == 2 || this.mSuppSvcNotification.code == 3) {
                        onPhoneStateChanged(null);
                    }
                    break;
                }
                break;
            case 15:
                onActiveSubChanged((AsyncResult) msg.obj);
                break;
            case 17:
                AsyncResult r = (AsyncResult) msg.obj;
                Phone.SuppService service = (Phone.SuppService) r.result;
                int val = service.ordinal();
                Log.d(TAG, "SUPP_SERVICE_FAILED..." + service);
                for (int i = 0; i < this.mListeners.size(); i++) {
                    this.mListeners.get(i).onSuppServiceFailed(val);
                }
                break;
        }
    }

    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);
        Preconditions.checkNotNull(this.mListeners);
        if (!this.mListeners.contains(listener)) {
            this.mListeners.add(listener);
        }
    }

    public List<Call> getFullList() {
        List<Call> calls = Lists.newArrayListWithCapacity(this.mCallMap.size() + this.mConfCallMap.size());
        calls.addAll(this.mCallMap.values());
        calls.addAll(this.mConfCallMap.values());
        return calls;
    }

    public CallResult getCallWithId(int callId) {
        AnonymousClass1 anonymousClass1 = null;
        for (Map.Entry<Connection, Call> entry : this.mCallMap.entrySet()) {
            if (entry.getValue().getCallId() == callId) {
                return new CallResult(entry.getValue(), entry.getKey(), anonymousClass1);
            }
        }
        for (Map.Entry<Connection, Call> entry2 : this.mConfCallMap.entrySet()) {
            if (entry2.getValue().getCallId() == callId) {
                return new CallResult(entry2.getValue(), entry2.getKey(), anonymousClass1);
            }
        }
        return null;
    }

    public boolean hasLiveCall() {
        return hasLiveCallInternal(this.mCallMap) || hasLiveCallInternal(this.mConfCallMap);
    }

    public void onCdmaCallWaiting(CdmaCallWaitingNotification callWaitingInfo) {
        Connection connection;
        String number;
        com.android.internal.telephony.Call teleCall = this.mCallManager.getFirstActiveRingingCall();
        if (teleCall.getState() == com.android.internal.telephony.Call.State.WAITING && (connection = teleCall.getLatestConnection()) != null && (number = connection.getAddress()) != null && number.equals(callWaitingInfo.number)) {
            onNewRingingConnection(connection);
            this.mCdmaIncomingConnection = connection;
        } else {
            Log.e(TAG, "CDMA Call waiting notification without a matching connection.");
        }
    }

    public void onCdmaCallWaitingReject() {
        if (this.mCdmaIncomingConnection != null) {
            onDisconnect(this.mCdmaIncomingConnection);
            this.mCdmaIncomingConnection = null;
        } else {
            Log.e(TAG, "CDMA Call waiting rejection without an incoming call.");
        }
    }

    public void setCdmaOutgoing3WayCall(Connection connection) {
        boolean wasSet = this.mCdmaOutgoingConnection != null;
        this.mCdmaOutgoingConnection = connection;
        if (wasSet && this.mCdmaOutgoingConnection == null) {
            onPhoneStateChanged(null);
        }
    }

    private boolean hasLiveCallInternal(HashMap<Connection, Call> map) {
        for (Call call : map.values()) {
            int state = call.getState();
            if (state == 2 || state == 4 || state == 10 || state == 5 || state == 6 || state == 3 || state == 7 || state == 8) {
                return true;
            }
        }
        return false;
    }

    public boolean hasOutstandingActiveOrDialingCall() {
        return hasOutstandingActiveOrDialingCallInternal(this.mCallMap) || hasOutstandingActiveOrDialingCallInternal(this.mConfCallMap);
    }

    private static boolean hasOutstandingActiveOrDialingCallInternal(HashMap<Connection, Call> map) {
        for (Call call : map.values()) {
            int state = call.getState();
            if (state == 2 || Call.State.isDialing(state)) {
                return true;
            }
        }
        return false;
    }

    private void onPostDialChars(AsyncResult r, char ch) {
        Connection c = (Connection) r.result;
        if (c != null) {
            Connection.PostDialState state = (Connection.PostDialState) r.userObj;
            switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$Connection$PostDialState[state.ordinal()]) {
                case 1:
                    Call call = getCallFromMap(this.mCallMap, c, false);
                    if (call == null) {
                        Log.i(TAG, "Call no longer exists. Skipping onPostDialWait().");
                    } else {
                        for (Listener mListener : this.mListeners) {
                            mListener.onPostDialAction(state, call.getCallId(), c.getRemainingPostDialString(), ch);
                        }
                    }
                    break;
                default:
                    for (Listener mListener2 : this.mListeners) {
                        mListener2.onPostDialAction(state, 0, "", ch);
                    }
                    break;
            }
        }
    }

    Call onNewRingingConnection(Connection conn) {
        Log.i(TAG, "onNewRingingConnection");
        Call call = getCallFromMap(this.mCallMap, conn, true);
        if (call != null) {
            updateCallFromConnection(call, conn, false);
            for (int i = 0; i < this.mListeners.size(); i++) {
                this.mListeners.get(i).onIncoming(call);
            }
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                int subscription = conn.getCall().getPhone().getSubscription();
                Log.i(TAG, "Setting Active sub : '" + subscription + "'");
                PhoneUtils.setActiveSubscription(subscription);
                MSimCallNotifier notifier = (MSimCallNotifier) PhoneGlobals.getInstance().notifier;
                notifier.manageLocalCallWaitingTone();
            }
        }
        PhoneGlobals.getInstance().updateWakeState();
        return call;
    }

    void onUnsolCallModify(Connection conn) {
        Call call = getCallFromMap(this.mCallMap, conn, false);
        copyDetails(conn.getCallModify().call_details, call.getCallModifyDetails(), conn.getCallModify().error + "");
        for (int i = 0; i < this.mListeners.size(); i++) {
            this.mListeners.get(i).onModifyCall(call);
        }
    }

    private void onDisconnect(Connection conn) {
        Log.i(TAG, "onDisconnect");
        Call call = getCallFromMap(this.mCallMap, conn, false);
        int sub = conn.getCall().getPhone().getSubscription();
        if (call != null) {
            boolean wasConferenced = call.getState() == 10;
            updateCallFromConnection(call, conn, false);
            updateSsNotificationData(call);
            for (int i = 0; i < this.mListeners.size(); i++) {
                this.mListeners.get(i).onDisconnect(call);
            }
            if (wasConferenced) {
                onPhoneStateChanged(null);
            }
            this.mCallMap.remove(conn);
        }
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled() && call != null) {
            this.mCallManager.clearDisconnected(sub);
        } else {
            this.mCallManager.clearDisconnected();
        }
        PhoneGlobals.getInstance().updateWakeState();
    }

    private void onPhoneStateChanged(AsyncResult r) {
        Log.i(TAG, "onPhoneStateChanged: ");
        if (PhoneGlobals.getInstance().isCsvtActive()) {
            Log.d(TAG, "csvt is active, do not update phone UI.");
            return;
        }
        List<Call> updatedCalls = Lists.newArrayList();
        doUpdate(false, updatedCalls);
        if (updatedCalls.size() > 0) {
            for (int i = 0; i < this.mListeners.size(); i++) {
                this.mListeners.get(i).onUpdate(updatedCalls);
            }
        }
        PhoneGlobals.getInstance().updateWakeState();
    }

    private void doUpdate(boolean fullUpdate, List<Call> out) {
        Log.d(TAG, "doUpdate, fullUpdate = " + fullUpdate);
        List<com.android.internal.telephony.Call> telephonyCalls = Lists.newArrayList();
        telephonyCalls.addAll(this.mCallManager.getRingingCalls());
        telephonyCalls.addAll(this.mCallManager.getForegroundCalls());
        telephonyCalls.addAll(this.mCallManager.getBackgroundCalls());
        Set<Connection> orphanedConnections = Sets.newHashSet();
        orphanedConnections.addAll(this.mCallMap.keySet());
        orphanedConnections.addAll(this.mConfCallMap.keySet());
        for (com.android.internal.telephony.Call telephonyCall : telephonyCalls) {
            for (Connection connection : telephonyCall.getConnections()) {
                Log.d(TAG, "connection: " + connection + connection.getState());
                if (orphanedConnections.contains(connection)) {
                    orphanedConnections.remove(connection);
                }
                boolean shouldUpdate = (connection.getState() == com.android.internal.telephony.Call.State.DISCONNECTED || connection.getState() == com.android.internal.telephony.Call.State.IDLE || connection.getState().isRinging()) ? false : true;
                boolean isDisconnecting = connection.getState() == com.android.internal.telephony.Call.State.DISCONNECTING;
                boolean shouldCreate = shouldUpdate && !isDisconnecting;
                Log.d(TAG, "shouldUpdate = " + shouldUpdate);
                Log.d(TAG, "isDisconnecting = " + isDisconnecting);
                Log.d(TAG, "shouldCreate = " + shouldCreate);
                Call call = getCallFromMap(this.mCallMap, connection, shouldCreate);
                if (call == null || !shouldUpdate) {
                    Log.d(TAG, "update skipped");
                } else {
                    boolean changed = updateCallFromConnection(call, connection, false);
                    if (fullUpdate || changed) {
                        out.add(call);
                    }
                }
            }
            Iterator i$ = telephonyCall.getConnections().iterator();
            while (i$.hasNext()) {
                updateForConferenceCalls((Connection) i$.next(), out);
            }
        }
        for (Connection orphanedConnection : orphanedConnections) {
            if (this.mCallMap.containsKey(orphanedConnection)) {
                Call call2 = this.mCallMap.get(orphanedConnection);
                call2.setState(1);
                out.add(call2);
                this.mCallMap.remove(orphanedConnection);
            }
            if (this.mConfCallMap.containsKey(orphanedConnection)) {
                Call call3 = this.mConfCallMap.get(orphanedConnection);
                call3.setState(1);
                out.add(call3);
                this.mConfCallMap.remove(orphanedConnection);
            }
        }
    }

    private boolean updateForConferenceCalls(Connection connection, List<Call> updatedCalls) {
        boolean isConferenceCallConnection = isPartOfLiveConferenceCall(connection) && getEarliestLiveConnection(connection.getCall()) == connection;
        if (isConferenceCallConnection) {
            Call confCall = getCallFromMap(this.mConfCallMap, connection, true);
            boolean changed = updateCallFromConnection(confCall, connection, true);
            if (changed) {
                updatedCalls.add(confCall);
            }
            Log.d(TAG, "Updating a conference call: " + confCall);
            return changed;
        }
        Call oldConfCall = getCallFromMap(this.mConfCallMap, connection, false);
        if (oldConfCall == null) {
            return false;
        }
        Log.d(TAG, "Cleaning up an old conference call: " + oldConfCall);
        this.mConfCallMap.remove(connection);
        oldConfCall.setState(1);
        updatedCalls.add(oldConfCall);
        return true;
    }

    private Connection getEarliestLiveConnection(com.android.internal.telephony.Call call) {
        List<Connection> connections = call.getConnections();
        int size = connections.size();
        Connection earliestConn = null;
        long earliestTime = Long.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            Connection connection = connections.get(i);
            if (connection.isAlive()) {
                long time = connection.getCreateTime();
                if (time < earliestTime) {
                    earliestTime = time;
                    earliestConn = connection;
                }
            }
        }
        return earliestConn;
    }

    private void setNewState(Call call, int newState, Connection connection) {
        Preconditions.checkState(call.getState() != newState);
        CallGatewayManager.RawGatewayInfo info = this.mCallGatewayManager.getGatewayInfo(connection);
        if (Call.State.isDialing(newState)) {
            if (!info.isEmpty()) {
                call.setGatewayNumber(info.getFormattedGatewayNumber());
                call.setGatewayPackage(info.packageName);
            }
        } else if (!Call.State.isConnected(newState)) {
            this.mCallGatewayManager.clearGatewayData(connection);
        }
        call.setState(newState);
    }

    private void mapCallDetails(Call call, Connection connection) {
        copyDetails(connection.getCallDetails(), call.getCallDetails(), connection.errorInfo);
        if (connection.getCallModify() != null) {
            copyDetails(connection.getCallModify().call_details, call.getCallModifyDetails(), connection.errorInfo);
        }
        if (connection.getCall().getConfUriList() != null) {
            String[] confList = connection.getCall().getConfUriList();
            call.getCallDetails().setConfUriList(confList);
        }
        call.getCallDetails().setMpty(PhoneUtils.isConferenceCall(connection.getCall()));
    }

    private boolean checkAndMapConfDetails(Call call, Connection connection) {
        Log.i(TAG, "checkAndMapConfDetailszzz call=" + call);
        int i = -1;
        if (connection.getCall().getConfStateInfo() != null && connection.getCall().getConfStateInfo().usersMap != null) {
            Log.i(TAG, "Conference participant list");
            int i2 = connection.getCall().getConfStateInfo().version;
            Log.i(TAG, "confVersion updated to " + i2 + ";current version=" + i2);
            HashMap map = new HashMap();
            Iterator it = connection.getCall().getConfStateInfo().usersMap.entrySet().iterator();
            while (it.hasNext()) {
                com.android.internal.telephony.Call.ConfUser confUser = (com.android.internal.telephony.Call.ConfUser) ((Map.Entry) it.next()).getValue();
                Log.i(TAG, "User: " + confUser.uri + " : " + confUser.displayText + " : " + confUser.status);
                ArrayList arrayList = new ArrayList();
                arrayList.add("CONF_URI=" + confUser.uri);
                arrayList.add("CONF_DISPLAY_TEXT=" + confUser.displayText);
                arrayList.add("CONF_STATUS=" + confUser.status);
                map.put(confUser.uri, arrayList);
            }
            call.getCallDetails().setConfDetailsFromMap(map);
            i = i2;
        }
        Log.i(TAG, "after checkAndMapConfDetailszzz call=" + call);
        boolean z = this.mConfVersion != i;
        this.mConfVersion = i;
        return z;
    }

    private void copyDetails(CallDetails src, com.android.services.telephony.common.CallDetails dest, String errorInfo) {
        dest.setCallType(src.call_type);
        dest.setCallDomain(src.call_domain);
        dest.setExtras(src.extras);
        dest.setErrorInfo(errorInfo);
    }

    private boolean updateCallFromConnection(Call call, Connection connection, boolean z) {
        boolean z2;
        boolean z3;
        boolean z4;
        int iTranslateStateFromTelephony = translateStateFromTelephony(connection, z);
        int state = call.getState();
        int callType = call.getCallDetails().getCallType();
        int i = connection.getCallDetails().call_type;
        if (call.getState() != iTranslateStateFromTelephony) {
            Log.d(TAG, "updateCallFromConnection, call.getState() = " + call.getState() + ", newState = " + iTranslateStateFromTelephony + ", changed = true");
            setNewState(call, iTranslateStateFromTelephony, connection);
            if (iTranslateStateFromTelephony == 2 && state != 7) {
                this.videocallDuration = 0L;
                this.videocallstarted = false;
                if (i == 3) {
                    startVideoCallTimer();
                }
            }
            if (state == 2 && iTranslateStateFromTelephony != 7) {
                if (callType == 3) {
                    stopVideoCallTimer();
                }
                updateVideoCallDuration(this.videocallDuration / 1000, connection);
            }
            z2 = true;
        } else {
            z2 = false;
        }
        if (callType != i && state == 2 && callType != 10) {
            Log.d(TAG, "updateCallFromConnection, oldCallType = " + callType + ", newCallType = " + i + ", changed = true, call modified");
            if (i == 0 && this.videocallstarted) {
                stopVideoCallTimer();
            }
            if (i == 3 && !this.videocallstarted) {
                startVideoCallTimer();
            }
            z2 = true;
        }
        mapCallDetails(call, connection);
        if (checkAndMapConfDetails(call, connection)) {
            z2 = true;
        }
        Call.DisconnectCause disconnectCauseTranslateDisconnectCauseFromTelephony = translateDisconnectCauseFromTelephony(connection.getDisconnectCause());
        if (call.getDisconnectCause() != disconnectCauseTranslateDisconnectCauseFromTelephony) {
            call.setDisconnectCause(disconnectCauseTranslateDisconnectCauseFromTelephony);
            z2 = true;
        }
        if (call.getConnectTime() != connection.getConnectTime()) {
            call.setConnectTime(connection.getConnectTime());
            z3 = true;
        } else {
            z3 = z2;
        }
        if (!z) {
            String number = call.getNumber();
            String address = connection.getAddress();
            CallGatewayManager.RawGatewayInfo gatewayInfo = this.mCallGatewayManager.getGatewayInfo(connection);
            if (!gatewayInfo.isEmpty()) {
                address = gatewayInfo.trueNumber;
            }
            if (TextUtils.isEmpty(number) || !number.equals(address)) {
                call.setNumber(address);
                z4 = true;
            } else {
                z4 = z3;
            }
            int numberPresentation = connection.getNumberPresentation();
            if (call.getNumberPresentation() != numberPresentation) {
                call.setNumberPresentation(numberPresentation);
                z4 = true;
            }
            String cnapName = call.getCnapName();
            if (TextUtils.isEmpty(cnapName) || !cnapName.equals(connection.getCnapName())) {
                call.setCnapName(connection.getCnapName());
                z4 = true;
            }
            int cnapNamePresentation = connection.getCnapNamePresentation();
            if (call.getCnapNamePresentation() != cnapNamePresentation) {
                call.setCnapNamePresentation(cnapNamePresentation);
                z4 = true;
            }
        } else {
            ImmutableSortedSet<Integer> childCallIds = call.getChildCallIds();
            call.removeAllChildren();
            if (connection.getCall() != null) {
                for (Connection connection2 : connection.getCall().getConnections()) {
                    Call callFromMap = getCallFromMap(this.mCallMap, connection2, false);
                    if (callFromMap != null && connection2.isAlive()) {
                        call.addChildId(callFromMap.getCallId());
                    }
                }
            }
            z4 = (!childCallIds.equals(call.getChildCallIds())) | z3;
        }
        if (call.getSubscription() == -1) {
            call.setSubscription(connection.getCall().getPhone().getSubscription());
        }
        int capabilitiesFor = getCapabilitiesFor(connection, call, z);
        if (call.getCapabilities() == capabilitiesFor) {
            return z4;
        }
        call.setCapabilities(capabilitiesFor);
        return true;
    }

    private void startVideoCallTimer() {
        if (!this.videocallstarted) {
            Log.d(TAG, "updateCallFromConnection, startVideoCallTimer");
            this.videocallstarttime = System.currentTimeMillis();
            this.videocallstarted = true;
            return;
        }
        Log.d(TAG, "updateCallFromConnection, startVideoCallTimer, no need to started");
    }

    private void stopVideoCallTimer() {
        if (this.videocallstarted) {
            Log.d(TAG, "updateCallFromConnection, stopVideoCallTimer");
            this.videocallDuration = (System.currentTimeMillis() - this.videocallstarttime) + this.videocallDuration;
            this.videocallstarted = false;
            this.videocallstarttime = 0L;
            return;
        }
        Log.d(TAG, "updateCallFromConnection, stopVideoCallTimer, no need to stop!");
    }

    private void updateVideoCallDuration(long j, Connection connection) {
        Log.d(TAG, "updateVideoCallDuration, videocallDuration = " + j);
        if (j == 0) {
            Log.d(TAG, "updateVideoCallDuration, no video call, do not update ");
            return;
        }
        String[] strArr = connection.getCallDetails().extras;
        String[] strArr2 = null;
        String str = this.VIDEO_CALL_DURATION_KEY + "=" + j;
        if (strArr != null) {
            String[] strArr3 = new String[strArr.length + 1];
            for (int i = 0; i < strArr.length; i++) {
                strArr3[i] = strArr[i];
            }
            strArr3[strArr.length] = str;
            strArr2 = strArr3;
        }
        connection.getCallDetails().setExtras(strArr2);
        Log.d(TAG, "updateCallFromConnection, conn testlog = " + connection.getCallDetails().getValueForKeyFromExtras(connection.getCallDetails().extras, this.VIDEO_CALL_DURATION_KEY));
    }

    private int getCapabilitiesFor(Connection connection, Call call, boolean isForConference) {
        boolean supportHold;
        boolean canHold;
        boolean canAddCall;
        boolean canMute;
        boolean callIsActive = call.getState() == 2;
        Phone phone = connection.getCall().getPhone();
        PhoneGlobals app = PhoneGlobals.getInstance();
        boolean canMergeCall = false;
        boolean canSwapCall = false;
        boolean canModifyCall = false;
        boolean genericConf = phone.getPhoneType() == 2 && (isForConference || app.cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE);
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            supportHold = PhoneUtils.okToSupportHold(this.mCallManager);
            canHold = supportHold ? PhoneUtils.okToHoldCall(this.mCallManager) : false;
            if (callIsActive) {
                canMergeCall = PhoneUtils.okToMergeCalls(this.mCallManager);
                canSwapCall = PhoneUtils.okToSwapCalls(this.mCallManager);
            }
            canAddCall = PhoneUtils.okToAddCall(this.mCallManager);
        } else {
            int subscription = call.getSubscription();
            supportHold = PhoneUtils.okToSupportHold(this.mCallManager, subscription);
            canHold = supportHold ? PhoneUtils.okToHoldCall(this.mCallManager, subscription) : false;
            if (callIsActive) {
                canMergeCall = PhoneUtils.okToMergeCalls(this.mCallManager, subscription);
                canSwapCall = PhoneUtils.okToSwapCalls(this.mCallManager, subscription);
            }
            canAddCall = PhoneUtils.okToAddCall(this.mCallManager, subscription);
        }
        if (callIsActive) {
            canModifyCall = PhoneUtils.isVTModifyAllowed(connection);
        }
        boolean canAddParticipant = PhoneUtils.canAddParticipant(this.mCallManager) && canAddCall;
        boolean isEmergencyCall = false;
        if (connection != null) {
            isEmergencyCall = PhoneNumberUtils.isLocalEmergencyNumber(connection.getAddress(), phone.getContext());
        }
        boolean isECM = PhoneUtils.isPhoneInEcm(phone);
        if (isEmergencyCall || isECM) {
            canMute = false;
        } else {
            canMute = callIsActive;
        }
        boolean canRespondViaText = RejectWithTextMessageManager.allowRespondViaSmsForCall(call, connection);
        if (phone.getPhoneType() == 2) {
            canAddCall = true;
        }
        int retval = 0;
        if (canHold) {
            retval = 0 | 1;
        }
        if (supportHold) {
            retval |= 2;
        }
        if (canAddCall) {
            retval |= 16;
        }
        if (canMergeCall) {
            retval |= 4;
        }
        if (canSwapCall) {
            retval |= 8;
        }
        if (canRespondViaText) {
            retval |= 32;
        }
        if (canMute) {
            retval |= 64;
        }
        if (canAddParticipant) {
            retval |= 512;
        }
        if (genericConf) {
            retval |= 128;
        }
        if (canModifyCall) {
            return retval | 256;
        }
        return retval;
    }

    private boolean isPartOfLiveConferenceCall(Connection connection) {
        boolean ret = false;
        if (connection.getCall() != null && connection.getCall().isMultiparty()) {
            int count = 0;
            if (connection.getCallDetails().call_domain == 2) {
                ret = true;
            } else {
                for (Connection currConn : connection.getCall().getConnections()) {
                    if (currConn.isAlive() && currConn != this.mCdmaOutgoingConnection && (count = count + 1) >= 2) {
                        return true;
                    }
                }
            }
        }
        return ret;
    }

    private int translateStateFromTelephony(Connection connection, boolean isForConference) {
        com.android.internal.telephony.Call.State connState = connection.getState();
        if (this.mCdmaOutgoingConnection == connection) {
            connState = com.android.internal.telephony.Call.State.DIALING;
        }
        int retval = 1;
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$Call$State[connState.ordinal()]) {
            case 1:
                retval = 2;
                break;
            case 2:
                retval = 3;
                break;
            case 3:
            case 4:
                if (PhoneGlobals.getInstance().notifier.getIsCdmaRedialCall()) {
                    retval = 6;
                } else {
                    retval = 5;
                }
                break;
            case 5:
                retval = 4;
                break;
            case 6:
                retval = 7;
                break;
            case 7:
                retval = 8;
                break;
            case 8:
                retval = 9;
                break;
        }
        if (!isForConference && isPartOfLiveConferenceCall(connection) && connection.isAlive()) {
            return 10;
        }
        return retval;
    }

    /* JADX INFO: renamed from: com.android.phone.CallModeler$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$Call$State = new int[com.android.internal.telephony.Call.State.values().length];
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$Connection$PostDialState;

        static {
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[com.android.internal.telephony.Call.State.ACTIVE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[com.android.internal.telephony.Call.State.INCOMING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[com.android.internal.telephony.Call.State.DIALING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[com.android.internal.telephony.Call.State.ALERTING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[com.android.internal.telephony.Call.State.WAITING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[com.android.internal.telephony.Call.State.HOLDING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[com.android.internal.telephony.Call.State.DISCONNECTING.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$Call$State[com.android.internal.telephony.Call.State.DISCONNECTED.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            $SwitchMap$com$android$internal$telephony$Connection$PostDialState = new int[Connection.PostDialState.values().length];
            try {
                $SwitchMap$com$android$internal$telephony$Connection$PostDialState[Connection.PostDialState.WAIT.ordinal()] = 1;
            } catch (NoSuchFieldError e9) {
            }
        }
    }

    private void onActiveSubChanged(AsyncResult asyncResult) {
        int iIntValue = ((Integer) asyncResult.result).intValue();
        Log.i(TAG, "onActiveSubChanged: " + iIntValue);
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 < this.mListeners.size()) {
                this.mListeners.get(i2).onActiveSubChanged(iIntValue);
                i = i2 + 1;
            } else {
                return;
            }
        }
    }

    private Call.DisconnectCause translateDisconnectCauseFromTelephony(Connection.DisconnectCause causeSource) {
        return this.CAUSE_MAP.containsKey(causeSource) ? this.CAUSE_MAP.get(causeSource) : Call.DisconnectCause.UNKNOWN;
    }

    private Call getCallFromMap(HashMap<Connection, Call> map, Connection conn, boolean createIfMissing) {
        if (conn == null) {
            return null;
        }
        if (map.containsKey(conn)) {
            return map.get(conn);
        }
        if (!createIfMissing) {
            return null;
        }
        Call call = createNewCall();
        map.put(conn, call);
        return call;
    }

    private Call createNewCall() {
        int i;
        do {
            i = this.mNextCallId.get();
        } while (!this.mNextCallId.compareAndSet(i, i == Integer.MAX_VALUE ? 1 : i + 1));
        return new Call(i);
    }

    private void updateSsNotificationData(Call call) {
        if (call != null && this.mSuppSvcNotification != null) {
            Call.SsNotification ssNotification = new Call.SsNotification();
            ssNotification.notificationType = this.mSuppSvcNotification.notificationType;
            ssNotification.code = this.mSuppSvcNotification.code;
            ssNotification.index = this.mSuppSvcNotification.index;
            ssNotification.type = this.mSuppSvcNotification.type;
            ssNotification.number = this.mSuppSvcNotification.number;
            call.setSuppServNotification(ssNotification);
            this.mSuppSvcNotification = null;
        }
    }

    public static class CallResult {
        public Call mActionableCall;
        public Call mCall;
        public Connection mConnection;

        /* synthetic */ CallResult(Call x0, Connection x1, AnonymousClass1 x2) {
            this(x0, x1);
        }

        private CallResult(Call call, Connection connection) {
            this(call, call, connection);
        }

        private CallResult(Call call, Call actionableCall, Connection connection) {
            this.mCall = call;
            this.mActionableCall = actionableCall;
            this.mConnection = connection;
        }

        public Call getCall() {
            return this.mCall;
        }

        public Connection getConnection() {
            return this.mConnection;
        }
    }
}
