package com.android.phone;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.IBluetoothHeadsetPhone;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.net.sip.SipManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class PhoneUtils {
    private static int mCallType;
    private static int mClir;
    private static ConnectionHandler mConnectionHandler;
    private static String[] mExtras;
    private static boolean sIsSpeakerEnabled = false;
    private static Hashtable<Connection, Boolean> sConnectionMuteTable = new Hashtable<>();
    private static boolean sIsNoiseSuppressionEnabled = true;
    private static List<String> mAddParticipantList = new ArrayList();
    private static AlertDialog sUssdDialog = null;
    private static StringBuilder sUssdMsg = new StringBuilder();
    static CallerInfoAsyncQuery.OnQueryCompleteListener sCallerInfoQueryListener = new CallerInfoAsyncQuery.OnQueryCompleteListener() { // from class: com.android.phone.PhoneUtils.4
        public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
            PhoneUtils.log("query complete, updating connection.userdata");
            Connection conn = (Connection) cookie;
            PhoneUtils.log("- onQueryComplete: CallerInfo:" + ci);
            if (ci.contactExists || ci.isEmergencyNumber() || ci.isVoiceMailNumber()) {
                if (ci.numberPresentation == 0) {
                    ci.numberPresentation = conn.getNumberPresentation();
                }
            } else {
                CallerInfo newCi = PhoneUtils.getCallerInfo(null, conn);
                if (newCi != null) {
                    newCi.phoneNumber = ci.phoneNumber;
                    newCi.geoDescription = ci.geoDescription;
                    ci = newCi;
                }
            }
            PhoneUtils.log("==> Stashing CallerInfo " + ci + " into the connection...");
            conn.setUserData(ci);
        }
    };

    public static class CallerInfoToken {
        public CallerInfoAsyncQuery asyncQuery;
        public CallerInfo currentInfo;
        public boolean isFinal;
    }

    private static class FgRingCalls {
        private Call fgCall;
        private Call ringing;

        public FgRingCalls(Call fg, Call ring) {
            this.fgCall = fg;
            this.ringing = ring;
        }
    }

    private static class ConnectionHandler extends Handler {
        private ConnectionHandler() {
        }

        /* JADX WARN: Code duplicated, block: B:105:0x020a A[SYNTHETIC] */
        /* JADX WARN: Code duplicated, block: B:106:? A[LOOP:6: B:56:0x01f0->B:106:?, LOOP_END, SYNTHETIC] */
        /* JADX WARN: Code duplicated, block: B:58:0x01f6  */
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case -1:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    PhoneUtils.log("ConnectionHandler: updating mute state for each connection");
                    CallManager cm = (CallManager) ar.userObj;
                    List<Connection> fgConnections = new ArrayList<>();
                    for (Call fgCall : cm.getForegroundCalls()) {
                        if (!fgCall.isIdle()) {
                            fgConnections.addAll(fgCall.getConnections());
                        }
                    }
                    for (Connection cn : fgConnections) {
                        if (PhoneUtils.sConnectionMuteTable.get(cn) == null) {
                            PhoneUtils.sConnectionMuteTable.put(cn, Boolean.FALSE);
                        }
                    }
                    List<Connection> bgConnections = new ArrayList<>();
                    for (Call bgCall : cm.getBackgroundCalls()) {
                        if (!bgCall.isIdle()) {
                            bgConnections.addAll(bgCall.getConnections());
                        }
                    }
                    for (Connection cn2 : bgConnections) {
                        if (PhoneUtils.sConnectionMuteTable.get(cn2) == null) {
                            PhoneUtils.sConnectionMuteTable.put(cn2, Boolean.FALSE);
                        }
                    }
                    Iterator<Connection> cnlist = PhoneUtils.sConnectionMuteTable.keySet().iterator();
                    while (cnlist.hasNext()) {
                        Connection cn3 = cnlist.next();
                        if (!fgConnections.contains(cn3) && !bgConnections.contains(cn3)) {
                            PhoneUtils.log("connection '" + cn3 + "' not accounted for, removing.");
                            for (Connection fgcn : fgConnections) {
                                if (Objects.equal(cn3.getAddress(), fgcn.getAddress())) {
                                    Boolean bMute = (Boolean) PhoneUtils.sConnectionMuteTable.get(cn3);
                                    PhoneUtils.log("updating fg conn '" + fgcn + "' wth mute value: " + bMute + " address: " + fgcn.getAddress());
                                    PhoneUtils.sConnectionMuteTable.put(fgcn, bMute);
                                    for (Connection bgcn : bgConnections) {
                                        if (Objects.equal(cn3.getAddress(), bgcn.getAddress())) {
                                            Boolean bMute2 = (Boolean) PhoneUtils.sConnectionMuteTable.get(cn3);
                                            PhoneUtils.log("updating bg conn '" + bgcn + "' wth mute value: " + bMute2 + " address: " + bgcn.getAddress());
                                            PhoneUtils.sConnectionMuteTable.put(bgcn, bMute2);
                                            cnlist.remove();
                                        }
                                    }
                                    cnlist.remove();
                                }
                            }
                            while (r16.hasNext()) {
                                if (Objects.equal(cn3.getAddress(), bgcn.getAddress())) {
                                    Boolean bMute3 = (Boolean) PhoneUtils.sConnectionMuteTable.get(cn3);
                                    PhoneUtils.log("updating bg conn '" + bgcn + "' wth mute value: " + bMute3 + " address: " + bgcn.getAddress());
                                    PhoneUtils.sConnectionMuteTable.put(bgcn, bMute3);
                                    cnlist.remove();
                                }
                            }
                            cnlist.remove();
                        }
                    }
                    if (cm.getState() == PhoneConstants.State.IDLE) {
                        PhoneUtils.setMuteInternal(cm.getFgPhone(), false);
                    } else {
                        PhoneUtils.restoreMuteState();
                    }
                    break;
                case 100:
                    FgRingCalls frC = (FgRingCalls) msg.obj;
                    if (frC.fgCall != null && frC.fgCall.getState() == Call.State.DISCONNECTING && msg.arg1 < 8) {
                        Message retryMsg = PhoneUtils.mConnectionHandler.obtainMessage(100);
                        retryMsg.arg1 = msg.arg1 + 1;
                        retryMsg.obj = msg.obj;
                        PhoneUtils.mConnectionHandler.sendMessageDelayed(retryMsg, 200L);
                    } else if (frC.ringing.isRinging()) {
                        if (msg.arg1 == 8) {
                            Log.e("PhoneUtils", "DISCONNECTING time out");
                        }
                        PhoneUtils.answerCall(frC.ringing);
                    }
                    break;
                case 101:
                    AsyncResult result = (AsyncResult) msg.obj;
                    if (result == null || result.exception == null) {
                        PhoneUtils.mAddParticipantList.remove(0);
                        PhoneUtils.log("mAddParticipantList = " + PhoneUtils.mAddParticipantList);
                        if (PhoneUtils.mAddParticipantList.size() > 0) {
                            PhoneUtils.addParticipant((String) PhoneUtils.mAddParticipantList.get(0), PhoneUtils.mClir, PhoneUtils.mCallType, PhoneUtils.mExtras, PhoneUtils.mConnectionHandler.obtainMessage(101));
                        }
                    } else {
                        Log.e("PhoneUtils", "addParticipant exception = " + result.exception);
                        PhoneUtils.mAddParticipantList.clear();
                    }
                    break;
            }
        }
    }

    public static void initializeConnectionHandler(CallManager cm) {
        if (mConnectionHandler == null) {
            mConnectionHandler = new ConnectionHandler();
        }
        cm.registerForPreciseCallStateChanged(mConnectionHandler, -1, cm);
    }

    private PhoneUtils() {
    }

    static boolean answerCall(Call ringingCall) {
        return answerCall(ringingCall, 10);
    }

    static boolean answerCall(Call ringingCall, int answerCallType) {
        boolean isRealIncomingCall;
        log("answerCall(" + ringingCall + ")...calltype:" + answerCallType);
        PhoneGlobals app = PhoneGlobals.getInstance();
        CallNotifier notifier = app.notifier;
        notifier.silenceRinger();
        Phone phone = ringingCall.getPhone();
        boolean phoneIsCdma = phone.getPhoneType() == 2;
        boolean answered = false;
        IBluetoothHeadsetPhone btPhone = null;
        if (phoneIsCdma && ringingCall.getState() == Call.State.WAITING) {
            notifier.stopSignalInfoTone();
        }
        if (ringingCall != null && ringingCall.isRinging()) {
            log("answerCall: call state = " + ringingCall.getState());
            if (phoneIsCdma) {
                try {
                    if (app.cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.IDLE) {
                        app.cdmaPhoneCallState.setCurrentCallState(CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
                    } else {
                        notifier.onCdmaCallWaitingAnswered();
                        app.cdmaPhoneCallState.setCurrentCallState(CdmaPhoneCallState.PhoneCallState.CONF_CALL);
                        app.cdmaPhoneCallState.setAddCallMenuStateAfterCallWaiting(true);
                        btPhone = app.getBluetoothPhoneService();
                        if (btPhone != null) {
                            try {
                                btPhone.cdmaSetSecondCallState(true);
                            } catch (RemoteException e) {
                                Log.e("PhoneUtils", Log.getStackTraceString(new Throwable()));
                            }
                        }
                    }
                    isRealIncomingCall = isRealIncomingCall(ringingCall.getState());
                    app.mCM.acceptCall(ringingCall, answerCallType);
                    answered = true;
                    handleWaitingCallOnLchSub(phone.getSubscription(), true);
                    setMute(false);
                    setAudioMode();
                    boolean speakerActivated = activateSpeakerIfDocked(phone);
                    BluetoothManager btManager = app.getBluetoothManager();
                    if (isRealIncomingCall && !speakerActivated && isSpeakerOn(app) && !btManager.isBluetoothHeadsetAudioOn()) {
                        Log.i("PhoneUtils", "Forcing speaker off due to new incoming call...");
                        turnOnSpeaker(app, false, true);
                    }
                } catch (CallStateException ex) {
                    Log.w("PhoneUtils", "answerCall: caught " + ex, ex);
                    if (phoneIsCdma) {
                        app.cdmaPhoneCallState.setCurrentCallState(app.cdmaPhoneCallState.getPreviousCallState());
                        if (btPhone != null) {
                            try {
                                btPhone.cdmaSetSecondCallState(false);
                            } catch (RemoteException e2) {
                                Log.e("PhoneUtils", Log.getStackTraceString(new Throwable()));
                            }
                        }
                    }
                }
            } else {
                isRealIncomingCall = isRealIncomingCall(ringingCall.getState());
                app.mCM.acceptCall(ringingCall, answerCallType);
                answered = true;
                handleWaitingCallOnLchSub(phone.getSubscription(), true);
                setMute(false);
                setAudioMode();
                boolean speakerActivated2 = activateSpeakerIfDocked(phone);
                BluetoothManager btManager2 = app.getBluetoothManager();
                if (isRealIncomingCall) {
                    Log.i("PhoneUtils", "Forcing speaker off due to new incoming call...");
                    turnOnSpeaker(app, false, true);
                }
            }
        }
        return answered;
    }

    public static void modifyCallInitiate(Connection conn, int newCallType, String[] newExtras) {
        Phone phone = conn.getCall().getPhone();
        if (phone != null && phone.getPhoneType() == 4) {
            Log.d("PhoneUtils", "modifyCallInitiate");
            try {
                phone.changeConnectionType((Message) null, conn, newCallType, (Map) null);
            } catch (CallStateException e) {
                Log.e("PhoneUtils", "Exception in modifyCallInitiate" + e);
            }
        }
    }

    public static void modifyCallConfirm(boolean responseType, Connection conn, String[] newExtras) {
        Phone phone = conn.getCall().getPhone();
        if (phone != null && phone.getPhoneType() == 4) {
            Log.d("PhoneUtils", "modifyCallConfirm");
            try {
                if (responseType) {
                    phone.acceptConnectionTypeChange(conn, (Map) null);
                } else {
                    phone.rejectConnectionTypeChange(conn);
                }
            } catch (CallStateException e) {
                Log.e("PhoneUtils", "Exception in modifyCallConfirm" + e);
            }
        }
    }

    public static boolean isVTModifyAllowed(Connection conn) {
        Phone phone = conn.getCall().getPhone();
        if (phone == null || phone.getPhoneType() != 4) {
            return false;
        }
        try {
            boolean ret = phone.isVTModifyAllowed();
            return ret;
        } catch (CallStateException e) {
            Log.e("PhoneUtils", "Exception in isVTModifyAllowed" + e);
            return false;
        }
    }

    static boolean hangup(CallManager cm) {
        boolean hungup = false;
        Call ringing = cm.getFirstActiveRingingCall();
        Call fg = cm.getActiveFgCall();
        Call bg = cm.getFirstActiveBgCall();
        if (!ringing.isIdle()) {
            log("hangup(): hanging up ringing call");
            hungup = hangupRingingCall(ringing);
        } else if (!fg.isIdle()) {
            log("hangup(): hanging up foreground call");
            hungup = hangup(fg);
        } else if (!bg.isIdle()) {
            log("hangup(): hanging up background call");
            hungup = hangup(bg);
        } else {
            log("hangup(): no active call to hang up");
        }
        log("==> hungup = " + hungup);
        return hungup;
    }

    static boolean hangupRingingCall(Call ringing) {
        log("hangup ringing call");
        int phoneType = ringing.getPhone().getPhoneType();
        Call.State state = ringing.getState();
        if (state == Call.State.INCOMING) {
            log("hangupRingingCall(): regular incoming call: hangup()");
            return hangup(ringing);
        }
        if (state == Call.State.WAITING) {
            if (phoneType == 2) {
                log("hangupRingingCall(): CDMA-specific call-waiting hangup");
                CallNotifier notifier = PhoneGlobals.getInstance().notifier;
                notifier.sendCdmaCallWaitingReject();
                return true;
            }
            handleWaitingCallOnLchSub(ringing.getPhone().getSubscription(), false);
            log("hangupRingingCall(): call-waiting call: hangup()");
            return hangup(ringing);
        }
        Log.w("PhoneUtils", "hangupRingingCall: no INCOMING or WAITING call");
        return false;
    }

    static boolean hangupActiveCall(Call foreground) {
        log("hangup active call");
        return hangup(foreground);
    }

    static boolean hangupHoldingCall(Call background) {
        log("hangup holding call");
        return hangup(background);
    }

    static boolean hangupRingingAndActive(Phone phone) {
        boolean hungUpRingingCall = false;
        boolean hungUpFgCall = false;
        CallManager cm = PhoneGlobals.getInstance().mCM;
        Call ringingCall = cm.getFirstActiveRingingCall();
        Call fgCall = cm.getActiveFgCall();
        if (!ringingCall.isIdle()) {
            log("hangupRingingAndActive: Hang up Ringing Call");
            hungUpRingingCall = hangupRingingCall(ringingCall);
        }
        if (!fgCall.isIdle()) {
            log("hangupRingingAndActive: Hang up Foreground Call");
            hungUpFgCall = hangupActiveCall(fgCall);
        }
        return hungUpRingingCall || hungUpFgCall;
    }

    static boolean hangup(Call call) {
        try {
            CallManager cm = PhoneGlobals.getInstance().mCM;
            if (call.getState() == Call.State.ACTIVE && cm.hasActiveBgCall()) {
                log("- hangup(Call): hangupForegroundResumeBackground...");
                cm.hangupForegroundResumeBackground(cm.getFirstActiveBgCall());
            } else {
                log("- hangup(Call): regular hangup()...");
                call.hangup();
            }
            return true;
        } catch (CallStateException ex) {
            Log.e("PhoneUtils", "Call hangup: caught " + ex, ex);
            return false;
        }
    }

    static void hangup(Connection c) {
        if (c != null) {
            try {
                if (c.getCall().getPhone().getPhoneType() == 2 && c.getCall().getState() == Call.State.WAITING) {
                    handleWaitingCallOnLchSub(c.getCall().getPhone().getSubscription(), false);
                }
                c.hangup();
            } catch (CallStateException ex) {
                Log.w("PhoneUtils", "Connection hangup: caught " + ex, ex);
            }
        }
    }

    static boolean answerAndEndActive(CallManager cm, Call ringing) {
        log("answerAndEndActive()...");
        ringing.getPhone();
        cm.getActiveFgCall().getPhone();
        setSubInConversation(getActiveSubscription());
        Call fgCall = cm.getActiveFgCall();
        if (!hangupActiveCall(fgCall)) {
            Log.w("PhoneUtils", "end active call failed!");
            return false;
        }
        mConnectionHandler.removeMessages(100);
        Message msg = mConnectionHandler.obtainMessage(100);
        msg.arg1 = 1;
        msg.obj = new FgRingCalls(fgCall, ringing);
        mConnectionHandler.sendMessage(msg);
        return true;
    }

    private static void updateCdmaCallStateOnNewOutgoingCall(PhoneGlobals app, Connection connection) {
        if (app.cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.IDLE) {
            app.cdmaPhoneCallState.setCurrentCallState(CdmaPhoneCallState.PhoneCallState.SINGLE_ACTIVE);
        } else {
            app.cdmaPhoneCallState.setCurrentCallState(CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE);
            app.getCallModeler().setCdmaOutgoing3WayCall(connection);
        }
    }

    public static int placeCall(Context context, Phone phone, String number, Uri contactRef, boolean isEmergencyCall) {
        return placeCall(context, phone, number, contactRef, isEmergencyCall, CallGatewayManager.EMPTY_INFO, null);
    }

    public static int placeCall(Context context, Phone phone, String number, Uri contactRef, boolean isEmergencyCall, CallGatewayManager.RawGatewayInfo gatewayInfo, CallGatewayManager callGateway) {
        Uri gatewayUri = gatewayInfo.gatewayUri;
        log("placeCall()... number: " + toLogSafePhoneNumber(number) + ", GW: " + (gatewayUri != null ? "non-null" : "null") + ", emergency? " + isEmergencyCall);
        return placeCall(context, phone, number, contactRef, isEmergencyCall, gatewayInfo, callGateway, 0, null);
    }

    public static int placeCall(Context context, Phone phone, String number, Uri contactRef, boolean isEmergencyCall, CallGatewayManager.RawGatewayInfo gatewayInfo, CallGatewayManager callGateway, int callType, String[] extras) {
        String numberToDial;
        Uri gatewayUri = gatewayInfo.gatewayUri;
        log("placeCall '" + number + "' GW:'" + gatewayUri + "' CallType:" + callType);
        PhoneGlobals app = PhoneGlobals.getInstance();
        boolean useGateway = false;
        if (gatewayUri != null && !isEmergencyCall && isRoutableViaGateway(number)) {
            useGateway = true;
        }
        if (isCsvtCallActive()) {
            Log.e("PhoneUtils", "Unsupported another CALL when background CSVT active");
            return 2;
        }
        if (useGateway) {
            if (gatewayUri == null || !"tel".equals(gatewayUri.getScheme())) {
                Log.e("PhoneUtils", "Unsupported URL:" + gatewayUri);
                return 2;
            }
            numberToDial = gatewayUri.getSchemeSpecificPart();
        } else {
            numberToDial = number;
        }
        boolean initiallyIdle = false;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
                initiallyIdle = initiallyIdle || app.mCM.getState(i) == PhoneConstants.State.IDLE;
            }
        } else {
            initiallyIdle = app.mCM.getState() == PhoneConstants.State.IDLE;
        }
        if (isCallOnImsEnabled() && (PhoneNumberUtils.isLocalEmergencyNumber(number, app) || PhoneNumberUtils.isPotentialLocalEmergencyNumber(number, app))) {
            Phone imsPhone = getImsPhone(app.mCM);
            if (imsPhone.getSubscription() == phone.getSubscription()) {
                Log.d("PhoneUtils", "Emergency call on ims phone: " + imsPhone.getSubscription());
                phone = imsPhone;
            } else {
                Log.d("PhoneUtils", "Emergency call on CS phone: " + phone.getSubscription());
            }
        }
        try {
            Connection connection = app.mCM.dial(phone, numberToDial, callType, extras);
            int phoneType = phone.getPhoneType();
            if (connection == null) {
                if ((phoneType == 1 || phoneType == 4) && gatewayUri == null) {
                    log("dialed MMI code: " + number);
                    return 1;
                }
                return 2;
            }
            setActiveAndConversationSub(phone.getSubscription());
            if (callGateway != null) {
                callGateway.setGatewayInfoForConnection(connection, gatewayInfo);
            }
            if (phoneType == 2) {
                updateCdmaCallStateOnNewOutgoingCall(app, connection);
            }
            if (gatewayUri == null) {
                context.getContentResolver();
                if (contactRef != null && contactRef.getScheme().equals("content")) {
                    Object userDataObject = connection.getUserData();
                    if (userDataObject == null) {
                        connection.setUserData(contactRef);
                    } else if (userDataObject instanceof CallerInfo) {
                        ((CallerInfo) userDataObject).contactRefUri = contactRef;
                    } else {
                        ((CallerInfoToken) userDataObject).currentInfo.contactRefUri = contactRef;
                    }
                }
            }
            startGetCallerInfo(context, connection, null, null, gatewayInfo);
            if (isEmergencyCall) {
                setMute(false);
            }
            setAudioMode();
            log("about to activate speaker");
            boolean speakerActivated = activateSpeakerIfDocked(phone);
            BluetoothManager btManager = app.getBluetoothManager();
            if (!initiallyIdle || speakerActivated || !isSpeakerOn(app) || btManager.isBluetoothHeadsetAudioOn()) {
                return 0;
            }
            Log.i("PhoneUtils", "Forcing speaker off when initiating a new outgoing call...");
            turnOnSpeaker(app, false, true);
            return 0;
        } catch (CallStateException ex) {
            Log.w("PhoneUtils", "Exception from app.mCM.dial()", ex);
            return 2;
        }
    }

    static String toLogSafePhoneNumber(String number) {
        if (number == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c == '-' || c == '@' || c == '.') {
                builder.append(c);
            } else {
                builder.append('x');
            }
        }
        return builder.toString();
    }

    static void sendEmptyFlash(Phone phone) {
        if (phone.getPhoneType() == 2) {
            Call fgCall = phone.getForegroundCall();
            if (fgCall.getState() == Call.State.ACTIVE) {
                Log.d("PhoneUtils", "onReceive: (CDMA) sending empty flash to network");
                switchHoldingAndActive(phone.getBackgroundCall());
            }
        }
    }

    static void swap() {
        IBluetoothHeadsetPhone btPhone;
        PhoneGlobals mApp = PhoneGlobals.getInstance();
        if (okToSwapCalls(mApp.mCM)) {
            switchHoldingAndActive(mApp.mCM.getFirstActiveBgCall());
            if (mApp.mCM.getBgPhone().getPhoneType() == 2 && (btPhone = mApp.getBluetoothPhoneService()) != null) {
                try {
                    btPhone.cdmaSwapSecondCallState();
                } catch (RemoteException e) {
                    Log.e("PhoneUtils", Log.getStackTraceString(new Throwable()));
                }
            }
        }
    }

    static void switchHoldingAndActive(Call heldCall) {
        log("switchHoldingAndActive()...");
        try {
            CallManager cm = PhoneGlobals.getInstance().mCM;
            if (heldCall.isIdle()) {
                cm.switchHoldingAndActive(cm.getFgPhone().getBackgroundCall());
            } else {
                cm.switchHoldingAndActive(heldCall);
            }
            setAudioMode(cm);
        } catch (CallStateException ex) {
            Log.w("PhoneUtils", "switchHoldingAndActive: caught " + ex, ex);
        }
    }

    static Boolean restoreMuteState() {
        Phone phone = PhoneGlobals.getInstance().mCM.getFgPhone();
        Connection c = phone.getForegroundCall().getEarliestConnection();
        if (c != null) {
            int phoneType = phone.getPhoneType();
            Boolean shouldMute = null;
            if (phoneType == 2) {
                Boolean shouldMute2 = sConnectionMuteTable.get(phone.getForegroundCall().getLatestConnection());
                shouldMute = shouldMute2;
            } else if (phoneType == 1 || phoneType == 3 || phoneType == 4) {
                Boolean shouldMute3 = sConnectionMuteTable.get(c);
                shouldMute = shouldMute3;
            }
            if (shouldMute == null) {
                log("problem retrieving mute value for this connection.");
                shouldMute = Boolean.FALSE;
            }
            setMute(shouldMute.booleanValue());
            return shouldMute;
        }
        Boolean shouldMute4 = Boolean.valueOf(getMute());
        return shouldMute4;
    }

    static void mergeCalls() {
        mergeCalls(PhoneGlobals.getInstance().mCM);
    }

    static void mergeCalls(CallManager cm) {
        int phoneType = cm.getFgPhone().getPhoneType();
        if (phoneType == 2) {
            log("mergeCalls(): CDMA...");
            PhoneGlobals app = PhoneGlobals.getInstance();
            if (app.cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                app.cdmaPhoneCallState.setCurrentCallState(CdmaPhoneCallState.PhoneCallState.CONF_CALL);
                log("- sending flash...");
                switchHoldingAndActive(cm.getFirstActiveBgCall());
                return;
            }
            return;
        }
        try {
            log("mergeCalls(): calling cm.conference()...");
            cm.conference(cm.getFirstActiveBgCall());
        } catch (CallStateException ex) {
            Log.w("PhoneUtils", "mergeCalls: caught " + ex, ex);
        }
    }

    static Dialog displayMMIInitiate(Context context, MmiCode mmiCode, Message buttonCallbackMessage, Dialog previousAlert) {
        log("displayMMIInitiate: " + mmiCode);
        if (previousAlert != null) {
            previousAlert.dismiss();
        }
        boolean isCancelable = mmiCode != null && mmiCode.isCancelable();
        if (!isCancelable) {
            log("not a USSD code, displaying status toast.");
            CharSequence text = context.getText(R.string.mmiStarted);
            Toast.makeText(context, text, 0).show();
            return null;
        }
        log("running USSD code, displaying indeterminate progress.");
        ProgressDialog pd = new ProgressDialog(context);
        pd.setMessage(context.getText(R.string.ussdRunning));
        pd.setCancelable(false);
        pd.setIndeterminate(true);
        pd.getWindow().addFlags(2);
        pd.show();
        return pd;
    }

    /* JADX WARN: Code duplicated, block: B:11:0x0075  */
    /* JADX WARN: Code duplicated, block: B:22:0x00e1  */
    /* JADX WARN: Code duplicated, block: B:25:0x00e8  */
    /* JADX WARN: Code duplicated, block: B:27:0x010b  */
    /* JADX WARN: Code duplicated, block: B:36:0x0181  */
    /* JADX WARN: Code duplicated, block: B:37:? A[ADDED_TO_REGION, RETURN, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:8:0x006c  */
    static void displayMMIComplete(final Phone phone, Context context, final MmiCode mmiCode, Message message, AlertDialog alertDialog) {
        CharSequence text;
        int i;
        final PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
        MmiCode.State state = mmiCode.getState();
        log("displayMMIComplete: state=" + state);
        switch (AnonymousClass5.$SwitchMap$com$android$internal$telephony$MmiCode$State[state.ordinal()]) {
            case 1:
                CharSequence message2 = mmiCode.getMessage();
                log("- using text from PENDING MMI message: '" + ((Object) message2) + "'");
                text = message2;
                i = 0;
                if (alertDialog != null) {
                    alertDialog.dismiss();
                }
                if (phoneGlobals.getPUKEntryActivity() == null && state == MmiCode.State.COMPLETE) {
                    log("displaying PUK unblocking progress dialog.");
                    ProgressDialog progressDialog = new ProgressDialog(phoneGlobals);
                    progressDialog.setTitle(i);
                    progressDialog.setMessage(text);
                    progressDialog.setCancelable(false);
                    progressDialog.setIndeterminate(true);
                    progressDialog.getWindow().setType(2008);
                    progressDialog.getWindow().addFlags(2);
                    progressDialog.show();
                    phoneGlobals.setPukEntryProgressDialog(progressDialog);
                    return;
                }
                if (phoneGlobals.getPUKEntryActivity() != null) {
                    phoneGlobals.setPukEntryActivity(null);
                }
                if (state != MmiCode.State.PENDING) {
                    log("MMI code has finished running.");
                    log("Extended NW displayMMIInitiate (" + ((Object) text) + ")");
                    if (text == null && text.length() != 0) {
                        if (sUssdDialog == null) {
                            sUssdDialog = new AlertDialog.Builder(context).setPositiveButton(R.string.ok, (DialogInterface.OnClickListener) null).setCancelable(true).setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.android.phone.PhoneUtils.1
                                @Override // android.content.DialogInterface.OnDismissListener
                                public void onDismiss(DialogInterface dialog) {
                                    PhoneUtils.sUssdMsg.setLength(0);
                                }
                            }).create();
                            sUssdDialog.getWindow().setType(2008);
                            sUssdDialog.getWindow().addFlags(2);
                        }
                        if (sUssdMsg.length() != 0) {
                            sUssdMsg.insert(0, "\n").insert(0, phoneGlobals.getResources().getString(R.string.ussd_dialog_sep)).insert(0, "\n");
                        }
                        sUssdMsg.insert(0, text);
                        sUssdDialog.setMessage(sUssdMsg.toString());
                        sUssdDialog.show();
                        return;
                    }
                    return;
                }
                log("USSD code has requested user input. Constructing input dialog.");
                View viewInflate = ((LayoutInflater) context.getSystemService("layout_inflater")).inflate(R.layout.dialog_ussd_response, (ViewGroup) null);
                final EditText editText = (EditText) viewInflate.findViewById(R.id.input_field);
                DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() { // from class: com.android.phone.PhoneUtils.2
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int whichButton) {
                        switch (whichButton) {
                            case -2:
                                if (mmiCode.isCancelable()) {
                                    mmiCode.cancel();
                                }
                                break;
                            case -1:
                                if (editText.length() < 1 || editText.length() > 160) {
                                    Toast.makeText(phoneGlobals, phoneGlobals.getResources().getString(R.string.enter_input, 1, 160), 1).show();
                                    if (mmiCode.isCancelable()) {
                                        mmiCode.cancel();
                                    }
                                } else {
                                    phone.sendUssdResponse(editText.getText().toString());
                                }
                                break;
                        }
                    }
                };
                final AlertDialog alertDialogCreate = new AlertDialog.Builder(context).setMessage(text).setView(viewInflate).setPositiveButton(R.string.send_button, onClickListener).setNegativeButton(R.string.cancel, onClickListener).setCancelable(false).create();
                editText.setOnKeyListener(new View.OnKeyListener() { // from class: com.android.phone.PhoneUtils.3
                    @Override // android.view.View.OnKeyListener
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        switch (keyCode) {
                            case 5:
                            case 66:
                                if (event.getAction() == 0) {
                                    phone.sendUssdResponse(editText.getText().toString());
                                    alertDialogCreate.dismiss();
                                }
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                editText.requestFocus();
                alertDialogCreate.getWindow().setType(2008);
                alertDialogCreate.getWindow().addFlags(2);
                alertDialogCreate.show();
                return;
            case 2:
                i = 0;
                text = null;
                if (alertDialog != null) {
                    alertDialog.dismiss();
                }
                if (phoneGlobals.getPUKEntryActivity() == null) {
                    break;
                }
                if (phoneGlobals.getPUKEntryActivity() != null) {
                    phoneGlobals.setPukEntryActivity(null);
                }
                if (state != MmiCode.State.PENDING) {
                    log("MMI code has finished running.");
                    log("Extended NW displayMMIInitiate (" + ((Object) text) + ")");
                    if (text == null) {
                        return;
                    } else {
                        return;
                    }
                }
                log("USSD code has requested user input. Constructing input dialog.");
                View viewInflate2 = ((LayoutInflater) context.getSystemService("layout_inflater")).inflate(R.layout.dialog_ussd_response, (ViewGroup) null);
                final EditText editText2 = (EditText) viewInflate2.findViewById(R.id.input_field);
                DialogInterface.OnClickListener onClickListener2 = new DialogInterface.OnClickListener() { // from class: com.android.phone.PhoneUtils.2
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int whichButton) {
                        switch (whichButton) {
                            case -2:
                                if (mmiCode.isCancelable()) {
                                    mmiCode.cancel();
                                }
                                break;
                            case -1:
                                if (editText2.length() < 1 || editText2.length() > 160) {
                                    Toast.makeText(phoneGlobals, phoneGlobals.getResources().getString(R.string.enter_input, 1, 160), 1).show();
                                    if (mmiCode.isCancelable()) {
                                        mmiCode.cancel();
                                    }
                                } else {
                                    phone.sendUssdResponse(editText2.getText().toString());
                                }
                                break;
                        }
                    }
                };
                final AlertDialog alertDialogCreate2 = new AlertDialog.Builder(context).setMessage(text).setView(viewInflate2).setPositiveButton(R.string.send_button, onClickListener2).setNegativeButton(R.string.cancel, onClickListener2).setCancelable(false).create();
                editText2.setOnKeyListener(new View.OnKeyListener() { // from class: com.android.phone.PhoneUtils.3
                    @Override // android.view.View.OnKeyListener
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        switch (keyCode) {
                            case 5:
                            case 66:
                                if (event.getAction() == 0) {
                                    phone.sendUssdResponse(editText2.getText().toString());
                                    alertDialogCreate2.dismiss();
                                }
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                editText2.requestFocus();
                alertDialogCreate2.getWindow().setType(2008);
                alertDialogCreate2.getWindow().addFlags(2);
                alertDialogCreate2.show();
                return;
            case 3:
                if (phoneGlobals.getPUKEntryActivity() != null) {
                    i = android.R.string.PERSOSUBSTATE_RUIM_HRPD_PUK_SUCCESS;
                    text = context.getText(R.string.puk_unlocked);
                }
                if (alertDialog != null) {
                    alertDialog.dismiss();
                }
                if (phoneGlobals.getPUKEntryActivity() == null) {
                    break;
                }
                if (phoneGlobals.getPUKEntryActivity() != null) {
                    phoneGlobals.setPukEntryActivity(null);
                }
                if (state != MmiCode.State.PENDING) {
                    log("MMI code has finished running.");
                    log("Extended NW displayMMIInitiate (" + ((Object) text) + ")");
                    if (text == null) {
                        return;
                    } else {
                        return;
                    }
                }
                log("USSD code has requested user input. Constructing input dialog.");
                View viewInflate3 = ((LayoutInflater) context.getSystemService("layout_inflater")).inflate(R.layout.dialog_ussd_response, (ViewGroup) null);
                final EditText editText3 = (EditText) viewInflate3.findViewById(R.id.input_field);
                DialogInterface.OnClickListener onClickListener3 = new DialogInterface.OnClickListener() { // from class: com.android.phone.PhoneUtils.2
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int whichButton) {
                        switch (whichButton) {
                            case -2:
                                if (mmiCode.isCancelable()) {
                                    mmiCode.cancel();
                                }
                                break;
                            case -1:
                                if (editText3.length() < 1 || editText3.length() > 160) {
                                    Toast.makeText(phoneGlobals, phoneGlobals.getResources().getString(R.string.enter_input, 1, 160), 1).show();
                                    if (mmiCode.isCancelable()) {
                                        mmiCode.cancel();
                                    }
                                } else {
                                    phone.sendUssdResponse(editText3.getText().toString());
                                }
                                break;
                        }
                    }
                };
                final AlertDialog alertDialogCreate3 = new AlertDialog.Builder(context).setMessage(text).setView(viewInflate3).setPositiveButton(R.string.send_button, onClickListener3).setNegativeButton(R.string.cancel, onClickListener3).setCancelable(false).create();
                editText3.setOnKeyListener(new View.OnKeyListener() { // from class: com.android.phone.PhoneUtils.3
                    @Override // android.view.View.OnKeyListener
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        switch (keyCode) {
                            case 5:
                            case 66:
                                if (event.getAction() == 0) {
                                    phone.sendUssdResponse(editText3.getText().toString());
                                    alertDialogCreate3.dismiss();
                                }
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                editText3.requestFocus();
                alertDialogCreate3.getWindow().setType(2008);
                alertDialogCreate3.getWindow().addFlags(2);
                alertDialogCreate3.show();
                return;
            case 4:
                CharSequence message3 = mmiCode.getMessage();
                log("- using text from MMI message: '" + ((Object) message3) + "'");
                text = message3;
                i = 0;
                if (alertDialog != null) {
                    alertDialog.dismiss();
                }
                if (phoneGlobals.getPUKEntryActivity() == null) {
                    break;
                }
                if (phoneGlobals.getPUKEntryActivity() != null) {
                    phoneGlobals.setPukEntryActivity(null);
                }
                if (state != MmiCode.State.PENDING) {
                    log("MMI code has finished running.");
                    log("Extended NW displayMMIInitiate (" + ((Object) text) + ")");
                    if (text == null) {
                        return;
                    } else {
                        return;
                    }
                }
                log("USSD code has requested user input. Constructing input dialog.");
                View viewInflate4 = ((LayoutInflater) context.getSystemService("layout_inflater")).inflate(R.layout.dialog_ussd_response, (ViewGroup) null);
                final EditText editText4 = (EditText) viewInflate4.findViewById(R.id.input_field);
                DialogInterface.OnClickListener onClickListener4 = new DialogInterface.OnClickListener() { // from class: com.android.phone.PhoneUtils.2
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int whichButton) {
                        switch (whichButton) {
                            case -2:
                                if (mmiCode.isCancelable()) {
                                    mmiCode.cancel();
                                }
                                break;
                            case -1:
                                if (editText4.length() < 1 || editText4.length() > 160) {
                                    Toast.makeText(phoneGlobals, phoneGlobals.getResources().getString(R.string.enter_input, 1, 160), 1).show();
                                    if (mmiCode.isCancelable()) {
                                        mmiCode.cancel();
                                    }
                                } else {
                                    phone.sendUssdResponse(editText4.getText().toString());
                                }
                                break;
                        }
                    }
                };
                final AlertDialog alertDialogCreate4 = new AlertDialog.Builder(context).setMessage(text).setView(viewInflate4).setPositiveButton(R.string.send_button, onClickListener4).setNegativeButton(R.string.cancel, onClickListener4).setCancelable(false).create();
                editText4.setOnKeyListener(new View.OnKeyListener() { // from class: com.android.phone.PhoneUtils.3
                    @Override // android.view.View.OnKeyListener
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        switch (keyCode) {
                            case 5:
                            case 66:
                                if (event.getAction() == 0) {
                                    phone.sendUssdResponse(editText4.getText().toString());
                                    alertDialogCreate4.dismiss();
                                }
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                editText4.requestFocus();
                alertDialogCreate4.getWindow().setType(2008);
                alertDialogCreate4.getWindow().addFlags(2);
                alertDialogCreate4.show();
                return;
            default:
                throw new IllegalStateException("Unexpected MmiCode state: " + state);
        }
    }

    /* JADX INFO: renamed from: com.android.phone.PhoneUtils$5, reason: invalid class name */
    static /* synthetic */ class AnonymousClass5 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$MmiCode$State = new int[MmiCode.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$MmiCode$State[MmiCode.State.PENDING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$MmiCode$State[MmiCode.State.CANCELLED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$MmiCode$State[MmiCode.State.COMPLETE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$MmiCode$State[MmiCode.State.FAILED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
        }
    }

    static boolean cancelMmiCode(Phone phone) {
        List pendingMmiCodes = phone.getPendingMmiCodes();
        int size = pendingMmiCodes.size();
        log("cancelMmiCode: num pending MMIs = " + size);
        if (size > 0) {
            MmiCode mmiCode = (MmiCode) pendingMmiCodes.get(0);
            if (mmiCode.isCancelable()) {
                mmiCode.cancel();
                return true;
            }
        }
        return false;
    }

    public static class VoiceMailNumberMissingException extends Exception {
        VoiceMailNumberMissingException() {
        }

        VoiceMailNumberMissingException(String msg) {
            super(msg);
        }
    }

    public static String getInitialNumber(Intent intent) throws VoiceMailNumberMissingException {
        log("getInitialNumber(): " + intent);
        if (TextUtils.isEmpty(intent.getAction())) {
            return null;
        }
        if (intent.hasExtra("android.phone.extra.ACTUAL_NUMBER_TO_DIAL")) {
            String stringExtra = intent.getStringExtra("android.phone.extra.ACTUAL_NUMBER_TO_DIAL");
            log("==> got EXTRA_ACTUAL_NUMBER_TO_DIAL; returning '" + toLogSafePhoneNumber(stringExtra) + "'");
            return stringExtra;
        }
        return getNumberFromIntent(PhoneGlobals.getInstance(), intent);
    }

    private static String getNumberFromIntent(Context context, Intent intent) throws VoiceMailNumberMissingException {
        Uri data = intent.getData();
        String scheme = data.getScheme();
        if ("sip".equals(scheme)) {
            return data.getSchemeSpecificPart();
        }
        String numberFromIntent = PhoneNumberUtils.getNumberFromIntent(intent, context);
        if (!"voicemail".equals(scheme)) {
            return numberFromIntent;
        }
        if (numberFromIntent == null || TextUtils.isEmpty(numberFromIntent)) {
            throw new VoiceMailNumberMissingException();
        }
        return numberFromIntent;
    }

    static CallerInfo getCallerInfo(Context context, Connection connection) {
        CallerInfo callerInfo = null;
        if (connection != null) {
            Object userData = connection.getUserData();
            if (userData instanceof Uri) {
                callerInfo = CallerInfo.getCallerInfo(context, (Uri) userData);
                if (callerInfo != null) {
                    connection.setUserData(callerInfo);
                }
            } else {
                if (userData instanceof CallerInfoToken) {
                    callerInfo = ((CallerInfoToken) userData).currentInfo;
                } else {
                    callerInfo = (CallerInfo) userData;
                }
                if (callerInfo == null) {
                    String address = connection.getAddress();
                    log("getCallerInfo: number = " + toLogSafePhoneNumber(address));
                    if (!TextUtils.isEmpty(address) && (callerInfo = CallerInfo.getCallerInfo(context, address)) != null) {
                        connection.setUserData(callerInfo);
                    }
                }
            }
        }
        return callerInfo;
    }

    static CallerInfoToken startGetCallerInfo(Context context, Connection c, CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie) {
        return startGetCallerInfo(context, c, listener, cookie, null);
    }

    static CallerInfoToken startGetCallerInfo(Context context, Connection connection, CallerInfoAsyncQuery.OnQueryCompleteListener onQueryCompleteListener, Object obj, CallGatewayManager.RawGatewayInfo rawGatewayInfo) {
        if (connection == null) {
            CallerInfoToken callerInfoToken = new CallerInfoToken();
            callerInfoToken.asyncQuery = null;
            return callerInfoToken;
        }
        Object userData = connection.getUserData();
        if (userData instanceof Uri) {
            CallerInfoToken callerInfoToken2 = new CallerInfoToken();
            callerInfoToken2.currentInfo = new CallerInfo();
            callerInfoToken2.asyncQuery = CallerInfoAsyncQuery.startQuery(-1, context, (Uri) userData, sCallerInfoQueryListener, connection);
            callerInfoToken2.asyncQuery.addQueryListener(-1, onQueryCompleteListener, obj);
            callerInfoToken2.isFinal = false;
            connection.setUserData(callerInfoToken2);
            log("startGetCallerInfo: query based on Uri: " + userData);
            return callerInfoToken2;
        }
        if (userData == null) {
            String address = connection.getAddress();
            if (rawGatewayInfo != null && rawGatewayInfo != CallGatewayManager.EMPTY_INFO) {
                address = rawGatewayInfo.trueNumber;
            }
            log("PhoneUtils.startGetCallerInfo: new query for phone number...");
            log("- number (address): " + toLogSafePhoneNumber(address));
            log("- c: " + connection);
            log("- phone: " + connection.getCall().getPhone());
            int phoneType = connection.getCall().getPhone().getPhoneType();
            log("- phoneType: " + phoneType);
            switch (phoneType) {
                case 0:
                    log("  ==> PHONE_TYPE_NONE");
                    break;
                case 1:
                    log("  ==> PHONE_TYPE_GSM");
                    break;
                case 2:
                    log("  ==> PHONE_TYPE_CDMA");
                    break;
                case 3:
                    log("  ==> PHONE_TYPE_SIP");
                    break;
                case 4:
                    log("  ==> PHONE_TYPE_IMS");
                    break;
                default:
                    log("  ==> Unknown phone type");
                    break;
            }
            CallerInfoToken callerInfoToken3 = new CallerInfoToken();
            callerInfoToken3.currentInfo = new CallerInfo();
            callerInfoToken3.currentInfo.cnapName = connection.getCnapName();
            callerInfoToken3.currentInfo.name = callerInfoToken3.currentInfo.cnapName;
            callerInfoToken3.currentInfo.numberPresentation = connection.getNumberPresentation();
            callerInfoToken3.currentInfo.namePresentation = connection.getCnapNamePresentation();
            if (!TextUtils.isEmpty(address)) {
                address = modifyForSpecialCnapCases(context, callerInfoToken3.currentInfo, address, callerInfoToken3.currentInfo.numberPresentation);
                callerInfoToken3.currentInfo.phoneNumber = address;
                if (callerInfoToken3.currentInfo.numberPresentation != PhoneConstants.PRESENTATION_ALLOWED) {
                    callerInfoToken3.isFinal = true;
                } else {
                    log("==> Actually starting CallerInfoAsyncQuery.startQuery()...");
                    callerInfoToken3.asyncQuery = CallerInfoAsyncQuery.startQuery(-1, context, address, sCallerInfoQueryListener, connection);
                    callerInfoToken3.asyncQuery.addQueryListener(-1, onQueryCompleteListener, obj);
                    callerInfoToken3.isFinal = false;
                }
            } else {
                log("startGetCallerInfo: No query to start, send trivial reply.");
                callerInfoToken3.isFinal = true;
            }
            connection.setUserData(callerInfoToken3);
            log("startGetCallerInfo: query based on number: " + toLogSafePhoneNumber(address));
            return callerInfoToken3;
        }
        if (userData instanceof CallerInfoToken) {
            CallerInfoToken callerInfoToken4 = (CallerInfoToken) userData;
            if (callerInfoToken4.asyncQuery != null) {
                callerInfoToken4.asyncQuery.addQueryListener(-1, onQueryCompleteListener, obj);
                log("startGetCallerInfo: query already running, adding listener: " + onQueryCompleteListener.getClass().toString());
                return callerInfoToken4;
            }
            String address2 = connection.getAddress();
            if (rawGatewayInfo != null) {
                address2 = rawGatewayInfo.trueNumber;
            }
            log("startGetCallerInfo: updatedNumber initially = " + toLogSafePhoneNumber(address2));
            if (!TextUtils.isEmpty(address2)) {
                callerInfoToken4.currentInfo.cnapName = connection.getCnapName();
                callerInfoToken4.currentInfo.name = callerInfoToken4.currentInfo.cnapName;
                callerInfoToken4.currentInfo.numberPresentation = connection.getNumberPresentation();
                callerInfoToken4.currentInfo.namePresentation = connection.getCnapNamePresentation();
                String strModifyForSpecialCnapCases = modifyForSpecialCnapCases(context, callerInfoToken4.currentInfo, address2, callerInfoToken4.currentInfo.numberPresentation);
                callerInfoToken4.currentInfo.phoneNumber = strModifyForSpecialCnapCases;
                log("startGetCallerInfo: updatedNumber=" + toLogSafePhoneNumber(strModifyForSpecialCnapCases));
                log("startGetCallerInfo: CNAP Info from FW(2)");
                if (callerInfoToken4.currentInfo.numberPresentation != PhoneConstants.PRESENTATION_ALLOWED) {
                    callerInfoToken4.isFinal = true;
                    return callerInfoToken4;
                }
                callerInfoToken4.asyncQuery = CallerInfoAsyncQuery.startQuery(-1, context, strModifyForSpecialCnapCases, sCallerInfoQueryListener, connection);
                callerInfoToken4.asyncQuery.addQueryListener(-1, onQueryCompleteListener, obj);
                callerInfoToken4.isFinal = false;
                return callerInfoToken4;
            }
            log("startGetCallerInfo: No query to attach to, send trivial reply.");
            if (callerInfoToken4.currentInfo == null) {
                callerInfoToken4.currentInfo = new CallerInfo();
            }
            callerInfoToken4.currentInfo.cnapName = connection.getCnapName();
            callerInfoToken4.currentInfo.name = callerInfoToken4.currentInfo.cnapName;
            callerInfoToken4.currentInfo.numberPresentation = connection.getNumberPresentation();
            callerInfoToken4.currentInfo.namePresentation = connection.getCnapNamePresentation();
            log("startGetCallerInfo: CNAP Info from FW(3)");
            callerInfoToken4.isFinal = true;
            return callerInfoToken4;
        }
        CallerInfoToken callerInfoToken5 = new CallerInfoToken();
        callerInfoToken5.currentInfo = (CallerInfo) userData;
        callerInfoToken5.asyncQuery = null;
        callerInfoToken5.isFinal = true;
        log("startGetCallerInfo: query already done, returning CallerInfo");
        log("==> cit.currentInfo = " + callerInfoToken5.currentInfo);
        return callerInfoToken5;
    }

    static boolean isConferenceCall(Call call) {
        return call.isMultiparty() && isRealConferenceCall(call);
    }

    static boolean isRealConferenceCall(Call call) {
        List<Connection> connections = call.getConnections();
        if (connections == null) {
            return false;
        }
        int i = 0;
        for (Connection connection : connections) {
            log("  - CONN: " + connection + ", state = " + connection.getState());
            int i2 = connection.getState() == Call.State.ACTIVE ? i + 1 : i;
            if (i2 > 1) {
                return true;
            }
            i = i2;
        }
        return false;
    }

    static boolean startNewCall(CallManager callManager) {
        PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
        if (!okToAddCall(callManager)) {
            Log.w("PhoneUtils", "startNewCall: can't add a new call in the current state");
            dumpCallManager();
            return false;
        }
        Intent intent = new Intent("android.intent.action.DIAL");
        intent.addFlags(268435456);
        intent.putExtra("add_call_mode", true);
        try {
            phoneGlobals.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Log.e("PhoneUtils", "Activity for adding calls isn't found.");
            return false;
        }
    }

    static void turnOnSpeaker(Context context, boolean z, boolean z2) {
        log("turnOnSpeaker(flag=" + z + ", store=" + z2 + ")...");
        PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
        ((AudioManager) context.getSystemService("audio")).setSpeakerphoneOn(z);
        if (z2) {
            sIsSpeakerEnabled = z;
        }
        phoneGlobals.notificationMgr.updateSpeakerNotification(z);
        phoneGlobals.updateWakeState();
        phoneGlobals.mCM.setEchoSuppressionEnabled(z);
    }

    static void restoreSpeakerMode(Context context) {
        log("restoreSpeakerMode, restoring to: " + sIsSpeakerEnabled);
        if (isSpeakerOn(context) != sIsSpeakerEnabled) {
            turnOnSpeaker(context, sIsSpeakerEnabled, false);
        }
    }

    static boolean isSpeakerOn(Context context) {
        return ((AudioManager) context.getSystemService("audio")).isSpeakerphoneOn();
    }

    static void muteOnNewCall(boolean muted) {
        CallManager cm = PhoneGlobals.getInstance().mCM;
        if (cm.hasActiveFgCall()) {
            setMuteInternal(cm.getActiveFgCall().getPhone(), muted);
        }
    }

    static void setMute(boolean z) {
        CallManager callManager = PhoneGlobals.getInstance().mCM;
        if (isInEmergencyCall(callManager)) {
            z = false;
        }
        int activeSubscription = getActiveSubscription();
        if (callManager.getLocalCallHoldStatus(activeSubscription) && callManager.getSubInConversation() == -1) {
            log("setMute: muted:" + z);
            if (!z) {
                callManager.setSubInConversation(activeSubscription);
                callManager.setAudioMode();
            }
            ((MSimCallNotifier) PhoneGlobals.getInstance().notifier).manageMSimInCallTones(false);
        }
        setMuteInternal(callManager.getFgPhone(), z);
        for (Connection connection : callManager.getActiveFgCall().getConnections()) {
            if (sConnectionMuteTable.get(connection) == null) {
                log("problem retrieving mute value for this connection.");
            }
            sConnectionMuteTable.put(connection, Boolean.valueOf(z));
        }
        if (callManager.hasActiveBgCall()) {
            for (Connection connection2 : callManager.getFirstActiveBgCall().getConnections()) {
                if (sConnectionMuteTable.get(connection2) == null) {
                    log("problem retrieving mute value for this connection.");
                }
                sConnectionMuteTable.put(connection2, Boolean.valueOf(z));
            }
        }
    }

    public static void updateMuteState(int sub, boolean muted) {
        CallManager cm = PhoneGlobals.getInstance().mCM;
        Phone phone = PhoneGlobals.getInstance().getPhone(sub);
        for (Connection cn : phone.getForegroundCall().getConnections()) {
            if (sConnectionMuteTable.get(cn) == null) {
                log("problem retrieving mute value for this connection.");
            }
            sConnectionMuteTable.put(cn, Boolean.valueOf(muted));
        }
        if (cm.hasActiveBgCall(sub)) {
            for (Connection cn2 : cm.getFirstActiveBgCall(sub).getConnections()) {
                if (sConnectionMuteTable.get(cn2) == null) {
                    log("problem retrieving mute value for this connection.");
                }
                sConnectionMuteTable.put(cn2, Boolean.valueOf(muted));
            }
        }
    }

    static boolean isInEmergencyCall(CallManager cm) {
        for (Connection cn : cm.getActiveFgCall().getConnections()) {
            if (PhoneNumberUtils.isLocalEmergencyNumber(cn.getAddress(), PhoneGlobals.getInstance())) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void setMuteInternal(Phone phone, boolean z) {
        PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
        if (phoneGlobals.getResources().getBoolean(R.bool.send_mic_mute_to_AudioManager)) {
            AudioManager audioManager = (AudioManager) phoneGlobals.getSystemService("audio");
            log("setMuteInternal: using setMicrophoneMute(" + z + ")...");
            audioManager.setMicrophoneMute(z);
        } else {
            log("setMuteInternal: using phone.setMute(" + z + ")...");
            phone.setMute(z);
        }
        phoneGlobals.notificationMgr.updateMuteNotification();
        phoneGlobals.getAudioRouter().onMuteChange(z);
    }

    static boolean getMute() {
        PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
        return phoneGlobals.getResources().getBoolean(R.bool.send_mic_mute_to_AudioManager) ? ((AudioManager) phoneGlobals.getSystemService("audio")).isMicrophoneMute() : phoneGlobals.mCM.getMute();
    }

    static void setAudioMode() {
        setAudioMode(PhoneGlobals.getInstance().mCM);
    }

    static void setAudioMode(CallManager callManager) {
        Log.d("PhoneUtils", "setAudioMode()..." + callManager.getState());
        AudioManager audioManager = (AudioManager) PhoneGlobals.getInstance().getSystemService("audio");
        int mode = audioManager.getMode();
        callManager.setAudioMode();
        if (mode == audioManager.getMode()) {
            Log.d("PhoneUtils", "setAudioMode() no change: " + audioModeToString(mode));
        }
    }

    private static String audioModeToString(int i) {
        switch (i) {
            case -2:
                return "MODE_INVALID";
            case -1:
                return "MODE_CURRENT";
            case 0:
                return "MODE_NORMAL";
            case 1:
                return "MODE_RINGTONE";
            case 2:
                return "MODE_IN_CALL";
            default:
                return String.valueOf(i);
        }
    }

    static boolean handleHeadsetHook(Phone phone, KeyEvent keyEvent) {
        Connection latestConnection;
        log("handleHeadsetHook()..." + keyEvent.getAction() + " " + keyEvent.getRepeatCount());
        PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
        if (phone.getState() == PhoneConstants.State.IDLE) {
            return false;
        }
        boolean z = !phone.getRingingCall().isIdle();
        boolean z2 = !phone.getForegroundCall().isIdle();
        boolean z3 = !phone.getBackgroundCall().isIdle();
        if (z && keyEvent.getRepeatCount() == 0 && keyEvent.getAction() == 1) {
            int phoneType = phone.getPhoneType();
            if (phoneType == 2) {
                answerCall(phone.getRingingCall());
            } else if (phoneType == 1 || phoneType == 3 || phoneType == 4) {
                if (z2 && z3) {
                    log("handleHeadsetHook: ringing (both lines in use) ==> answer!");
                    answerAndEndActive(phoneGlobals.mCM, phone.getRingingCall());
                } else {
                    log("handleHeadsetHook: ringing ==> answer!");
                    answerCall(phone.getRingingCall());
                }
            } else {
                throw new IllegalStateException("Unexpected phone type: " + phoneType);
            }
        } else if (keyEvent.isLongPress()) {
            log("handleHeadsetHook: longpress -> hangup");
            hangup(phoneGlobals.mCM);
        } else if (keyEvent.getAction() == 1 && keyEvent.getRepeatCount() == 0 && (latestConnection = phone.getForegroundCall().getLatestConnection()) != null && !PhoneNumberUtils.isLocalEmergencyNumber(latestConnection.getAddress(), PhoneGlobals.getInstance())) {
            if (getMute()) {
                log("handleHeadsetHook: UNmuting...");
                setMute(false);
            } else {
                log("handleHeadsetHook: muting...");
                setMute(true);
            }
        }
        return true;
    }

    static boolean okToHoldCall(CallManager cm) {
        Call fgCall = cm.getActiveFgCall();
        boolean hasHoldingCall = cm.hasActiveBgCall();
        Call.State fgCallState = fgCall.getState();
        boolean okToHold = fgCallState == Call.State.ACTIVE && !hasHoldingCall;
        boolean okToUnhold = cm.hasActiveBgCall() && fgCallState == Call.State.IDLE;
        return okToHold || okToUnhold;
    }

    static boolean okToHoldCall(CallManager cm, int subscription) {
        Call fgCall = cm.getActiveFgCall(subscription);
        boolean hasHoldingCall = cm.hasActiveBgCall(subscription);
        Call.State fgCallState = fgCall.getState();
        boolean okToHold = fgCallState == Call.State.ACTIVE && !hasHoldingCall;
        boolean okToUnhold = cm.hasActiveBgCall(subscription) && fgCallState == Call.State.IDLE;
        return okToHold || okToUnhold;
    }

    static boolean okToSupportHold(CallManager cm) {
        Call bgCall;
        Call fgCall = cm.getActiveFgCall();
        boolean hasHoldingCall = cm.hasActiveBgCall();
        Call.State fgCallState = fgCall.getState();
        if (TelephonyCapabilities.supportsHoldAndUnhold(fgCall.getPhone())) {
            return true;
        }
        if (!hasHoldingCall || fgCallState != Call.State.IDLE || (bgCall = cm.getFirstActiveBgCall()) == null || !TelephonyCapabilities.supportsHoldAndUnhold(bgCall.getPhone())) {
            return false;
        }
        return true;
    }

    static boolean okToSupportHold(CallManager cm, int subscription) {
        Call bgCall;
        Call fgCall = cm.getActiveFgCall(subscription);
        boolean hasHoldingCall = cm.hasActiveBgCall(subscription);
        Call.State fgCallState = fgCall.getState();
        if (TelephonyCapabilities.supportsHoldAndUnhold(fgCall.getPhone())) {
            return true;
        }
        if (!hasHoldingCall || fgCallState != Call.State.IDLE || (bgCall = cm.getFirstActiveBgCall(subscription)) == null || !TelephonyCapabilities.supportsHoldAndUnhold(bgCall.getPhone())) {
            return false;
        }
        return true;
    }

    static boolean okToSwapCalls(CallManager callManager) {
        int phoneType = callManager.getFgPhone().getPhoneType();
        if (phoneType == 2) {
            return PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.CONF_CALL;
        }
        if (phoneType == 1 || phoneType == 3 || phoneType == 4) {
            return !callManager.hasActiveRingingCall() && callManager.getActiveFgCall().getState() == Call.State.ACTIVE && callManager.getFirstActiveBgCall().getState() == Call.State.HOLDING;
        }
        throw new IllegalStateException("Unexpected phone type: " + phoneType);
    }

    static boolean okToSwapCalls(CallManager callManager, int i) {
        int phoneType = callManager.getFgPhone(i).getPhoneType();
        if (phoneType == 2) {
            return PhoneGlobals.getInstance().cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.CONF_CALL;
        }
        if (phoneType == 1 || phoneType == 3 || phoneType == 4) {
            return !callManager.hasActiveRingingCall(i) && callManager.getActiveFgCall(i).getState() == Call.State.ACTIVE && callManager.getFirstActiveBgCall(i).getState() == Call.State.HOLDING;
        }
        throw new IllegalStateException("Unexpected phone type: " + phoneType);
    }

    static boolean okToMergeCalls(CallManager cm) {
        int phoneType = cm.getFgPhone().getPhoneType();
        int bgPhoneType = cm.getBgPhone().getPhoneType();
        if (phoneType != bgPhoneType) {
            return false;
        }
        if (phoneType != 2) {
            return !cm.hasActiveRingingCall() && cm.hasActiveFgCall() && cm.hasActiveBgCall() && cm.canConference(cm.getFirstActiveBgCall());
        }
        PhoneGlobals app = PhoneGlobals.getInstance();
        return app.cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE && !app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing();
    }

    static boolean okToMergeCalls(CallManager cm, int subscription) {
        int phoneType = cm.getFgPhone(subscription).getPhoneType();
        int bgPhoneType = cm.getBgPhone(subscription).getPhoneType();
        if (phoneType != bgPhoneType) {
            return false;
        }
        if (phoneType != 2) {
            return !cm.hasActiveRingingCall(subscription) && cm.hasActiveFgCall(subscription) && cm.hasActiveBgCall(subscription) && cm.canConference(cm.getFirstActiveBgCall(subscription), subscription);
        }
        PhoneGlobals app = PhoneGlobals.getInstance();
        return app.cdmaPhoneCallState.getCurrentCallState() == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE && !app.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing();
    }

    static boolean okToAddCall(CallManager callManager) {
        boolean z = true;
        if (!isCallOnImsEnabled()) {
            Phone phone = callManager.getActiveFgCall().getPhone();
            if (isPhoneInEcm(phone)) {
                return false;
            }
            int phoneType = phone.getPhoneType();
            Call.State state = callManager.getActiveFgCall().getState();
            if (phoneType == 2) {
                return state == Call.State.ACTIVE && PhoneGlobals.getInstance().cdmaPhoneCallState.getAddCallMenuStateAfterCallWaiting();
            }
            if (phoneType == 1 || phoneType == 3) {
                boolean zHasActiveRingingCall = callManager.hasActiveRingingCall();
                boolean z2 = callManager.hasActiveFgCall() && callManager.hasActiveBgCall();
                if (zHasActiveRingingCall || z2 || (state != Call.State.ACTIVE && state != Call.State.IDLE && state != Call.State.DISCONNECTED)) {
                    z = false;
                }
                return z;
            }
            if (phoneType == 4) {
                Log.e("PhoneUtils", "Unexpected IMS phone type add call not allowed");
                return false;
            }
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }
        return okToAddCallForIms(callManager);
    }

    static boolean canAddParticipant(CallManager cm) {
        Phone phone = cm.getActiveFgCall().getPhone();
        int phoneType = phone.getPhoneType();
        if (phoneType != 4) {
            return false;
        }
        return true;
    }

    static boolean okToAddCall(CallManager callManager, int i) {
        boolean z = true;
        if (!isCallOnImsEnabled()) {
            Phone phone = callManager.getActiveFgCall(i).getPhone();
            if (isPhoneInEcm(phone)) {
                return false;
            }
            int phoneType = phone.getPhoneType();
            Call.State state = callManager.getActiveFgCall(i).getState();
            if (phoneType == 2) {
                return state == Call.State.ACTIVE && PhoneGlobals.getInstance().cdmaPhoneCallState.getAddCallMenuStateAfterCallWaiting();
            }
            if (phoneType == 1 || phoneType == 3) {
                boolean zHasActiveRingingCall = callManager.hasActiveRingingCall(i);
                boolean z2 = callManager.hasActiveFgCall(i) && callManager.hasActiveBgCall(i);
                if (zHasActiveRingingCall || z2 || (state != Call.State.ACTIVE && state != Call.State.IDLE && state != Call.State.DISCONNECTED)) {
                    z = false;
                }
                return z;
            }
            if (phoneType == 4) {
                Log.e("PhoneUtils", "Unexpected IMS phone type add call not allowed");
                return false;
            }
            throw new IllegalStateException("Unexpected phone type: " + phoneType);
        }
        return okToAddCallForIms(callManager);
    }

    static boolean okToAddCallForIms(CallManager cm) {
        boolean z = true;
        Phone phone = cm.getPhoneInCall();
        if (isPhoneInEcm(phone)) {
            return false;
        }
        Call.State fgCallState = cm.getActiveFgCall().getState();
        boolean hasRingingCall = cm.hasActiveRingingCall();
        boolean hasActiveCall = cm.hasActiveFgCall();
        boolean hasHoldingCall = cm.hasActiveBgCall();
        boolean allLinesTaken = hasActiveCall && hasHoldingCall;
        if (hasRingingCall || allLinesTaken || (fgCallState != Call.State.ACTIVE && fgCallState != Call.State.IDLE && fgCallState != Call.State.DISCONNECTED)) {
            z = false;
        }
        return z;
    }

    private static int checkCnapSpecialCases(String str) {
        if (str.equals("PRIVATE") || str.equals("P") || str.equals("RES")) {
            log("checkCnapSpecialCases, PRIVATE string: " + str);
            return PhoneConstants.PRESENTATION_RESTRICTED;
        }
        if (str.equals("UNAVAILABLE") || str.equals("UNKNOWN") || str.equals("UNA") || str.equals("U")) {
            log("checkCnapSpecialCases, UNKNOWN string: " + str);
            return PhoneConstants.PRESENTATION_UNKNOWN;
        }
        log("checkCnapSpecialCases, normal str. number: " + str);
        return -1;
    }

    static String modifyForSpecialCnapCases(Context context, CallerInfo callerInfo, String str, int i) {
        int iCheckCnapSpecialCases;
        if (callerInfo != null && str != null) {
            log("modifyForSpecialCnapCases: initially, number=" + toLogSafePhoneNumber(str) + ", presentation=" + i + " ci " + callerInfo);
            if (Arrays.asList(context.getResources().getStringArray(R.array.absent_num)).contains(str) && i == PhoneConstants.PRESENTATION_ALLOWED) {
                str = context.getString(R.string.unknown);
                callerInfo.numberPresentation = PhoneConstants.PRESENTATION_UNKNOWN;
            }
            if ((callerInfo.numberPresentation == PhoneConstants.PRESENTATION_ALLOWED || (callerInfo.numberPresentation != i && i == PhoneConstants.PRESENTATION_ALLOWED)) && (iCheckCnapSpecialCases = checkCnapSpecialCases(str)) != -1) {
                if (iCheckCnapSpecialCases == PhoneConstants.PRESENTATION_RESTRICTED) {
                    str = context.getString(R.string.private_num);
                } else if (iCheckCnapSpecialCases == PhoneConstants.PRESENTATION_UNKNOWN) {
                    str = context.getString(R.string.unknown);
                }
                log("SpecialCnap: number=" + toLogSafePhoneNumber(str) + "; presentation now=" + iCheckCnapSpecialCases);
                callerInfo.numberPresentation = iCheckCnapSpecialCases;
            }
            log("modifyForSpecialCnapCases: returning number string=" + toLogSafePhoneNumber(str));
        }
        return str;
    }

    static void copyImsExtras(Intent intent, Intent intent2) {
        if (intent == null || intent2 == null) {
            Log.e("PhoneUtils", "intent is null");
            return;
        }
        intent2.putExtra("android.phone.extra.CALL_TYPE", intent.getIntExtra("android.phone.extra.CALL_TYPE", 0));
        intent2.putExtra("android.phone.extra.CALL_DOMAIN", intent.getIntExtra("android.phone.extra.CALL_DOMAIN", 1));
        intent2.putExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", intent.getBooleanExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", false));
    }

    private static boolean isRoutableViaGateway(String number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        String number2 = PhoneNumberUtils.stripSeparators(number);
        if (number2.equals(PhoneNumberUtils.convertKeypadLettersToDigits(number2))) {
            return PhoneNumberUtils.isGlobalPhoneNumber(PhoneNumberUtils.extractNetworkPortion(number2));
        }
        return false;
    }

    private static boolean activateSpeakerIfDocked(Phone phone) {
        log("activateSpeakerIfDocked()...");
        if (PhoneGlobals.mDockState != 0) {
            log("activateSpeakerIfDocked(): In a dock -> may need to turn on speaker.");
            PhoneGlobals phoneGlobals = PhoneGlobals.getInstance();
            BluetoothManager bluetoothManager = phoneGlobals.getBluetoothManager();
            WiredHeadsetManager wiredHeadsetManager = phoneGlobals.getWiredHeadsetManager();
            AudioRouter audioRouter = phoneGlobals.getAudioRouter();
            if (!wiredHeadsetManager.isHeadsetPlugged() && !bluetoothManager.isBluetoothHeadsetAudioOn()) {
                audioRouter.setSpeaker(true);
                return true;
            }
        }
        return false;
    }

    static boolean isPhoneInEcm(Phone phone) {
        String str;
        if (phone == null || !TelephonyCapabilities.supportsEcm(phone) || (str = SystemProperties.get("ril.cdma.inecmmode")) == null) {
            return false;
        }
        return str.equals("true");
    }

    public static Phone pickPhoneBasedOnNumber(CallManager callManager, String str, String str2, String str3, int i) {
        Phone imsPhone;
        Phone sipPhoneFromUri;
        log("pickPhoneBasedOnNumber: scheme " + str + ", number " + toLogSafePhoneNumber(str2) + ", sipUri " + (str3 != null ? Uri.parse(str3).toSafeString() : "null") + ", subscription" + i);
        if (str3 == null || (sipPhoneFromUri = getSipPhoneFromUri(callManager, str3)) == null) {
            return (!"sip".equals(str) || (imsPhone = getImsPhone(callManager)) == null) ? PhoneGlobals.getInstance().getPhone(i) : imsPhone;
        }
        return sipPhoneFromUri;
    }

    public static Phone getSipPhoneFromUri(CallManager callManager, String str) {
        for (SipPhone sipPhone : callManager.getAllPhones()) {
            if (sipPhone.getPhoneType() == 3 && str.equals(sipPhone.getSipUri())) {
                log("- pickPhoneBasedOnNumber:found SipPhone! obj = " + sipPhone + ", " + sipPhone.getClass());
                return sipPhone;
            }
        }
        return null;
    }

    public static Phone getImsPhone(CallManager callManager) {
        log("Find IMS phone:");
        for (Phone phone : callManager.getAllPhones()) {
            if (phone.getPhoneType() == 4) {
                log("found IMSPhone = " + phone + ", " + phone.getClass());
                return phone;
            }
        }
        log("IMS phone not present");
        return null;
    }

    public static boolean isRealIncomingCall(Call.State state) {
        return state == Call.State.INCOMING && !PhoneGlobals.getInstance().mCM.hasActiveFgCallAnyPhone();
    }

    static boolean isVoipSupported(Context context) {
        return SipManager.isVoipSupported(context) && context.getResources().getBoolean(android.R.bool.config_autoPowerModeUseMotionSensor) && context.getResources().getBoolean(android.R.bool.config_audio_ringer_mode_affects_alarm_stream);
    }

    public static boolean isImsCallIntent(String str, Intent intent) {
        if (!"sip".equals(str)) {
            return false;
        }
        int intExtra = intent.getIntExtra("android.phone.extra.CALL_DOMAIN", 1);
        log("In isIMSCall, call domain:" + intExtra);
        return intExtra == 2;
    }

    public static boolean isImsVideoCall(Call call) {
        log("In isImsVideoCall call=" + call);
        Phone phone = call.getPhone();
        if (phone.getPhoneType() == 4) {
            try {
                int callType = phone.getCallType(call);
                if (callType == 3 || callType == 2 || callType == 1) {
                    return true;
                }
            } catch (CallStateException e) {
                Log.e("PhoneUtils", "isIMSVideoCall: caught " + e, e);
            }
        }
        return false;
    }

    public static boolean isImsVideoCallActive(Call call) {
        if (call == null) {
            return false;
        }
        if (call.getState() != Call.State.ACTIVE) {
            log("Call is not active");
            return false;
        }
        return isImsVideoCall(call);
    }

    public static boolean isCallOnImsEnabled() {
        return isCallOnImsEnabled(10);
    }

    public static boolean isCallOnImsEnabled(int callType) {
        boolean isVoiceSupported = PhoneGlobals.getImsServiceStatus(0) != 3;
        boolean isVideoSupported = PhoneGlobals.getImsServiceStatus(3) != 3;
        switch (callType) {
            case 0:
                boolean isEnabled = isVoiceSupported;
                return isEnabled;
            case 3:
                boolean isEnabled2 = isVideoSupported;
                return isEnabled2;
            case 10:
                boolean isEnabled3 = isVoiceSupported | isVideoSupported;
                return isEnabled3;
            default:
                return false;
        }
    }

    public static boolean isCallOnCsvtEnabled() {
        return CallManager.isCallOnCsvtEnabled();
    }

    public static void convertCallToIms(Intent intent, int i) {
        String scheme = intent.getData().getScheme();
        String strStripSeparators = PhoneNumberUtils.stripSeparators(PhoneNumberUtils.getNumberFromIntent(intent, PhoneGlobals.getInstance()));
        log("Intent for IMS conversion:" + intent + "extras" + intent.getExtras());
        if (isImsCallIntent(scheme, intent)) {
            log("IMS Conversion not required ");
            return;
        }
        if (strStripSeparators == null) {
            strStripSeparators = "";
        }
        intent.setData(Uri.fromParts("sip", strStripSeparators, null));
        intent.putExtra("android.phone.extra.CALL_TYPE", i);
        boolean booleanExtra = intent.getBooleanExtra("org.codeaurora.extra.DIAL_CONFERENCE_URI", false);
        if (intent.hasExtra("android.phone.extra.ACTUAL_NUMBER_TO_DIAL") && !booleanExtra) {
            intent.putExtra("android.phone.extra.ACTUAL_NUMBER_TO_DIAL", strStripSeparators);
        }
        log("IMS Converted intent: " + intent + "extras" + intent.getExtras());
    }

    public static void addParticipant(String str, int i, int i2, String[] strArr) {
        Phone imsPhone = getImsPhone(PhoneGlobals.getInstance().getCallManager());
        if (imsPhone != null) {
            Log.d("PhoneUtils", "addParticipant dialString=" + str);
            String[] strArrSplit = str.split(";");
            if (strArrSplit != null && strArrSplit.length > 1) {
                addParticipantList(strArrSplit, i, i2, strArr);
                return;
            }
            try {
                imsPhone.addParticipant(str, i, i2, strArr);
            } catch (CallStateException e) {
                Log.e("PhoneUtils", "Exception in addParticipant" + e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void addParticipant(String str, int i, int i2, String[] strArr, Message message) {
        Phone imsPhone = getImsPhone(PhoneGlobals.getInstance().getCallManager());
        if (imsPhone != null) {
            Log.d("PhoneUtils", "addParticipantwithMsg dialString=" + str);
            try {
                imsPhone.addParticipant(str, i, i2, strArr, message);
            } catch (CallStateException e) {
                Log.e("PhoneUtils", "Exception in addParticipantMsg" + e);
            }
        }
    }

    private static void addParticipantList(String[] strArr, int i, int i2, String[] strArr2) {
        if (strArr == null || strArr.length < 2) {
            Log.e("PhoneUtils", "dialList not accepted");
            return;
        }
        mAddParticipantList.clear();
        for (String str : strArr) {
            mAddParticipantList.add(str);
        }
        mClir = i;
        mCallType = i2;
        mExtras = strArr2;
        log("addParticipantList mAddParticipantList= " + mAddParticipantList + ";mClir = " + mClir + ";mCallType = " + mCallType);
        addParticipant(mAddParticipantList.get(0), mClir, mCallType, mExtras, mConnectionHandler.obtainMessage(101));
    }

    public static void hangupWithReason(int i, String str, boolean z, int i2, String str2) {
        Phone imsPhone = getImsPhone(PhoneGlobals.getInstance().getCallManager());
        if (imsPhone != null) {
            Log.d("PhoneUtils", "hangupWithReason");
            try {
                imsPhone.hangupWithReason(i, str, z, i2, str2);
            } catch (CallStateException e) {
                Log.e("PhoneUtils", "Exception in hangupWithReason" + e);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String msg) {
        Log.d("PhoneUtils", msg);
    }

    static void dumpCallManager() {
        CallManager callManager = PhoneGlobals.getInstance().mCM;
        StringBuilder sb = new StringBuilder(128);
        Log.d("PhoneUtils", "############### dumpCallManager() ##############");
        Log.d("PhoneUtils", "CallManager: state = " + callManager.getState());
        sb.setLength(0);
        Call activeFgCall = callManager.getActiveFgCall();
        sb.append(" - FG call: ").append(callManager.hasActiveFgCall() ? "YES " : "NO ");
        sb.append(activeFgCall);
        sb.append("  State: ").append(callManager.getActiveFgCallState());
        sb.append("  Conn: ").append(callManager.getFgCallConnections());
        Log.d("PhoneUtils", sb.toString());
        sb.setLength(0);
        Call firstActiveBgCall = callManager.getFirstActiveBgCall();
        sb.append(" - BG call: ").append(callManager.hasActiveBgCall() ? "YES " : "NO ");
        sb.append(firstActiveBgCall);
        sb.append("  State: ").append(callManager.getFirstActiveBgCall().getState());
        sb.append("  Conn: ").append(callManager.getBgCallConnections());
        Log.d("PhoneUtils", sb.toString());
        sb.setLength(0);
        Call firstActiveRingingCall = callManager.getFirstActiveRingingCall();
        sb.append(" - RINGING call: ").append(callManager.hasActiveRingingCall() ? "YES " : "NO ");
        sb.append(firstActiveRingingCall);
        sb.append("  State: ").append(callManager.getFirstActiveRingingCall().getState());
        Log.d("PhoneUtils", sb.toString());
        for (Phone phone : CallManager.getInstance().getAllPhones()) {
            if (phone != null) {
                Log.d("PhoneUtils", "Phone: " + phone + ", name = " + phone.getPhoneName() + ", state = " + phone.getState());
                sb.setLength(0);
                Call foregroundCall = phone.getForegroundCall();
                sb.append(" - FG call: ").append(foregroundCall);
                sb.append("  State: ").append(foregroundCall.getState());
                sb.append("  Conn: ").append(foregroundCall.hasConnections());
                Log.d("PhoneUtils", sb.toString());
                sb.setLength(0);
                Call backgroundCall = phone.getBackgroundCall();
                sb.append(" - BG call: ").append(backgroundCall);
                sb.append("  State: ").append(backgroundCall.getState());
                sb.append("  Conn: ").append(backgroundCall.hasConnections());
                Log.d("PhoneUtils", sb.toString());
                sb.setLength(0);
                Call ringingCall = phone.getRingingCall();
                sb.append(" - RINGING call: ").append(ringingCall);
                sb.append("  State: ").append(ringingCall.getState());
                sb.append("  Conn: ").append(ringingCall.hasConnections());
                Log.d("PhoneUtils", sb.toString());
            }
        }
        Log.d("PhoneUtils", "############## END dumpCallManager() ###############");
    }

    public static String[] getExtrasFromMap(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        String[] strArr = new String[map.size()];
        if (strArr != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                strArr[0] = "" + entry.getKey() + "=" + entry.getValue();
            }
        }
        return strArr;
    }

    public static void setActiveSubscription(int subscription) {
        CallManager cm = PhoneGlobals.getInstance().mCM;
        int activeSub = getActiveSubscription();
        if (activeSub != subscription) {
            cm.setActiveSubscription(subscription);
        }
    }

    public static void setSubInConversation(int i) {
        CallManager callManager = PhoneGlobals.getInstance().mCM;
        int subInConversation = callManager.getSubInConversation();
        if (subInConversation != i) {
            log("setSubInConversation:" + i);
            callManager.setSubInConversation(i);
            callManager.setAudioMode();
            if (callManager.getState(i) == PhoneConstants.State.OFFHOOK && callManager.getState(subInConversation) == PhoneConstants.State.OFFHOOK) {
                ((MSimCallNotifier) PhoneGlobals.getInstance().notifier).manageMSimInCallTones(true);
            }
        }
    }

    public static void setActiveAndConversationSub(int subscription) {
        setActiveSubscription(subscription);
        setSubInConversation(subscription);
    }

    public static int getActiveSubscription() {
        return PhoneGlobals.getInstance().mCM.getActiveSubscription();
    }

    public static boolean isAnyOtherSubActive(int subscription) {
        return getOtherActiveSub(subscription) != -1;
    }

    public static int getOtherActiveSub(int i) {
        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        CallManager callManager = MSimPhoneGlobals.getInstance().mCM;
        Log.d("PhoneUtils", "getOtherActiveSub: sub = " + i + " count = " + phoneCount);
        for (int i2 = 0; i2 < phoneCount; i2++) {
            if (i2 != i && callManager.getState(i2) != PhoneConstants.State.IDLE) {
                Log.d("PhoneUtils", "getOtherActiveSub: active sub  = " + i2);
                return i2;
            }
        }
        return -1;
    }

    static boolean isAnySubActive() {
        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        CallManager callManager = MSimPhoneGlobals.getInstance().mCM;
        Log.d("PhoneUtils", "isAnySubActive");
        for (int i = 0; i < phoneCount; i++) {
            if (callManager.getState(i) != PhoneConstants.State.IDLE) {
                Log.d("PhoneUtils", "isAnySubActive: active sub  = " + i);
                return true;
            }
        }
        return false;
    }

    public static boolean isCsvtCallActive() {
        try {
            return (PhoneGlobals.mCsvtService == null || PhoneGlobals.mCsvtService.isIdle()) ? false : true;
        } catch (RemoteException e) {
            Log.e("PhoneUtils", "Failed to retrieve Csvt call state. " + e);
            return false;
        }
    }

    public static void handleWaitingCallOnLchSub(int i, boolean z) {
        CallManager callManager = PhoneGlobals.getInstance().mCM;
        boolean localCallHoldStatus = callManager.getLocalCallHoldStatus(i);
        int otherActiveSub = getOtherActiveSub(i);
        if (localCallHoldStatus && i == getActiveSubscription() && otherActiveSub != -1 && callManager.getState(otherActiveSub) != PhoneConstants.State.IDLE) {
            if (z) {
                Log.i("PhoneUtils", " re-start playing SCH tone, sub = " + otherActiveSub);
                ((MSimCallNotifier) PhoneGlobals.getInstance().notifier).stopMSimInCallTones();
            } else {
                Log.i("PhoneUtils", " Switching back to active sub = " + otherActiveSub);
                setActiveSubscription(callManager.getSubInConversation());
            }
        }
    }

    public static int getNextSubscriptionId(int curSub) {
        int nextSub = curSub + 1;
        if (nextSub >= MSimTelephonyManager.getDefault().getPhoneCount()) {
            return 0;
        }
        return nextSub;
    }

    public static boolean isImsVtCallPresent() {
        boolean z = false;
        Phone imsPhone = getImsPhone(PhoneGlobals.getInstance().mCM);
        if (imsPhone != null && (isImsVideoCall(imsPhone.getForegroundCall()) || isImsVideoCall(imsPhone.getBackgroundCall()) || isImsVideoCall(imsPhone.getRingingCall()))) {
            z = true;
        }
        log("isImsVtCallPresent: " + z);
        return z;
    }

    public static boolean isImsVtCallNotAllowed(int i) {
        boolean z = false;
        if (i == 3 || i == 2 || i == 1) {
            z = Settings.Secure.getInt(getImsPhone(PhoneGlobals.getInstance().mCM).getContext().getContentResolver(), "preferred_tty_mode", 0) != 0;
        }
        log("isImsVtCallNotAllowed: " + z);
        return z;
    }

    public static boolean isLTE(int network) {
        return network == 22 || network == 20 || network == 19 || network == 17 || network == 15 || network == 12 || network == 11 || network == 10 || network == 9 || network == 8;
    }

    public static boolean isTDDDataOnly(Context context, int i) {
        try {
            return MSimTelephonyManager.getIntAtIndex(context.getContentResolver(), "preferred_network_mode", i) == 11 && MSimTelephonyManager.getIntAtIndex(context.getContentResolver(), "tdd_data_only", i) == 1;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }
}
