package com.android.internal.telephony;

import android.content.Context;
import android.media.AudioManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.telephony.MSimTelephonyManager;
import android.telephony.Rlog;
import com.android.internal.telephony.sip.SipPhone;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class ExtCallManager extends CallManager {
    private static final boolean DBG = true;
    private static final int EVENT_LOCAL_CALL_HOLD = 202;
    private static final int LOCAL_CALL_HOLD_ACTIVE = 1;
    private static final int LOCAL_CALL_HOLD_INACTIVE = 0;
    private static final String LOG_TAG = "ExtCallManager";
    private static final boolean VDBG = false;
    private static int mActiveSub = 0;
    private static int mSubInConversation = -1;
    private LchState[] mLchStatus = {LchState.INACTIVE, LchState.INACTIVE, LchState.INACTIVE};
    private AudioManager mAudioManager = null;
    private final RegistrantList mActiveSubChangeRegistrants = new RegistrantList();

    private enum LchState {
        INACTIVE,
        ACTIVE
    }

    public static CallManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ExtCallManager();
        }
        return INSTANCE;
    }

    @Override // com.android.internal.telephony.CallManager
    protected void initHandler() {
        if (this.mHandler == null) {
            this.mHandler = new ExtCmHandler();
        }
    }

    private Phone getPhone(int subscription) {
        for (Phone phone : this.mPhones) {
            if (phone.getSubscription() == subscription) {
                return phone;
            }
        }
        return null;
    }

    @Override // com.android.internal.telephony.CallManager
    public PhoneConstants.State getState() {
        return getState(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public PhoneConstants.State getState(int subscription) {
        PhoneConstants.State s = PhoneConstants.State.IDLE;
        for (Phone phone : this.mPhones) {
            if (phone.getSubscription() == subscription) {
                if (phone.getState() == PhoneConstants.State.RINGING) {
                    s = PhoneConstants.State.RINGING;
                } else if (phone.getState() == PhoneConstants.State.OFFHOOK && s == PhoneConstants.State.IDLE) {
                    s = PhoneConstants.State.OFFHOOK;
                }
            }
        }
        return s;
    }

    @Override // com.android.internal.telephony.CallManager
    public int getServiceState(int subscription) {
        int resultState = 1;
        for (Phone phone : this.mPhones) {
            if (phone.getSubscription() == subscription) {
                int serviceState = phone.getServiceState().getState();
                if (serviceState == 0) {
                    return serviceState;
                }
                if (serviceState == 1) {
                    if (resultState == 2 || resultState == 3) {
                        resultState = serviceState;
                    }
                } else if (serviceState == 2 && resultState == 3) {
                    resultState = serviceState;
                }
            }
        }
        return resultState;
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean registerPhone(Phone phone) {
        boolean retVal = super.registerPhone(phone);
        Context context = getContext();
        if (context != null && this.mAudioManager == null) {
            this.mAudioManager = (AudioManager) context.getSystemService("audio");
        }
        return retVal;
    }

    @Override // com.android.internal.telephony.CallManager
    public Phone getFgPhone(int subscription) {
        return getActiveFgCall(subscription).getPhone();
    }

    @Override // com.android.internal.telephony.CallManager
    public Phone getBgPhone(int subscription) {
        return getFirstActiveBgCall(subscription).getPhone();
    }

    @Override // com.android.internal.telephony.CallManager
    public Phone getRingingPhone(int subscription) {
        return getFirstActiveRingingCall(subscription).getPhone();
    }

    @Override // com.android.internal.telephony.CallManager
    public Phone getPhoneInCall(int subscription) {
        if (!getFirstActiveRingingCall(subscription).isIdle()) {
            Phone phone = getFirstActiveRingingCall(subscription).getPhone();
            return phone;
        }
        if (!getActiveFgCall(subscription).isIdle()) {
            Phone phone2 = getActiveFgCall(subscription).getPhone();
            return phone2;
        }
        Phone phone3 = getFirstActiveBgCall(subscription).getPhone();
        return phone3;
    }

    @Override // com.android.internal.telephony.CallManager
    public void setActiveSubscription(int subscription) {
        Rlog.d(LOG_TAG, "setActiveSubscription existing:" + mActiveSub + "new = " + subscription);
        mActiveSub = subscription;
        this.mActiveSubChangeRegistrants.notifyRegistrants(new AsyncResult((Object) null, Integer.valueOf(mActiveSub), (Throwable) null));
    }

    @Override // com.android.internal.telephony.CallManager
    public int getActiveSubscription() {
        return mActiveSub;
    }

    @Override // com.android.internal.telephony.CallManager
    public void setSubInConversation(int subscription) {
        Rlog.d(LOG_TAG, "setSubInConversation  existing:" + mSubInConversation + " new:" + subscription);
        mSubInConversation = subscription;
    }

    @Override // com.android.internal.telephony.CallManager
    public int getSubInConversation() {
        return mSubInConversation;
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean getLocalCallHoldStatus(int subscription) {
        if (subscription == -1 || this.mLchStatus[subscription] == LchState.INACTIVE) {
            return false;
        }
        return true;
    }

    private void updateLchStatus(int sub) {
        LchState lchStatus = LchState.INACTIVE;
        Phone offHookPhone = getFgPhone(sub);
        Call call = offHookPhone.getForegroundCall();
        if (getActiveFgCallState(sub) == Call.State.IDLE) {
            offHookPhone = getBgPhone(sub);
            call = offHookPhone.getBackgroundCall();
        }
        Call.State state = call.getState();
        if ((state == Call.State.ACTIVE || state == Call.State.DIALING || state == Call.State.HOLDING || state == Call.State.ALERTING) && sub != getSubInConversation()) {
            lchStatus = LchState.ACTIVE;
        }
        if (lchStatus != this.mLchStatus[sub]) {
            Rlog.d(LOG_TAG, " setLocal Call Hold to  = " + lchStatus);
            offHookPhone.setLocalCallHold(lchStatus == LchState.ACTIVE ? 1 : 0, this.mHandler.obtainMessage(EVENT_LOCAL_CALL_HOLD));
            this.mLchStatus[sub] = lchStatus;
        }
    }

    @Override // com.android.internal.telephony.CallManager
    public void setAudioMode() {
        if (this.mAudioManager == null) {
            Rlog.e(LOG_TAG, "setAudioMode: Audio Service is null!! ");
            return;
        }
        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$PhoneConstants$State[getState().ordinal()]) {
            case 1:
                int curAudioMode = this.mAudioManager.getMode();
                if (curAudioMode != 1) {
                    if (this.mAudioManager.getStreamVolume(2) >= 0) {
                        Rlog.d(LOG_TAG, "requestAudioFocus on STREAM_RING");
                        this.mAudioManager.requestAudioFocusForCall(2, 2);
                    }
                    if (!this.mSpeedUpAudioForMtCall) {
                        Rlog.d(LOG_TAG, "setAudioMode RINGING");
                        this.mAudioManager.setMode(1);
                    }
                }
                if (this.mSpeedUpAudioForMtCall && curAudioMode != 2) {
                    Rlog.d(LOG_TAG, "setAudioMode IN_CALL");
                    this.mAudioManager.setMode(2);
                }
                break;
            case 2:
                for (int sub = 0; sub < MSimTelephonyManager.getDefault().getPhoneCount(); sub++) {
                    updateLchStatus(sub);
                }
                Phone offHookPhone = getFgPhone();
                int newAudioMode = 2;
                if (offHookPhone instanceof SipPhone) {
                    Rlog.d(LOG_TAG, "setAudioMode Set audio mode for SIP call!");
                    newAudioMode = 3;
                }
                int currMode = this.mAudioManager.getMode();
                if (currMode != newAudioMode || this.mSpeedUpAudioForMtCall) {
                    this.mAudioManager.requestAudioFocusForCall(0, 2);
                    Rlog.d(LOG_TAG, "setAudioMode Setting audio mode from " + currMode + " to " + newAudioMode);
                    this.mAudioManager.setMode(newAudioMode);
                }
                this.mSpeedUpAudioForMtCall = false;
                break;
            case 3:
                if (this.mAudioManager.getMode() != 0) {
                    for (int sub2 = 0; sub2 < MSimTelephonyManager.getDefault().getPhoneCount(); sub2++) {
                        updateLchStatus(sub2);
                    }
                    this.mAudioManager.setMode(0);
                    Rlog.d(LOG_TAG, "abandonAudioFocus");
                    this.mAudioManager.abandonAudioFocusForCall();
                }
                this.mSpeedUpAudioForMtCall = false;
                break;
        }
        Rlog.d(LOG_TAG, "setAudioMode State = " + getState());
    }

    /* JADX INFO: renamed from: com.android.internal.telephony.ExtCallManager$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$PhoneConstants$State = new int[PhoneConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.RINGING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.OFFHOOK.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$PhoneConstants$State[PhoneConstants.State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    public int getOtherActiveSub(int subscription) {
        int count = MSimTelephonyManager.getDefault().getPhoneCount();
        Rlog.d(LOG_TAG, "is other sub active = " + subscription + count);
        for (int i = 0; i < count; i++) {
            Rlog.d(LOG_TAG, "Count ** " + i);
            if (i != subscription && getState(i) != PhoneConstants.State.IDLE) {
                Rlog.d(LOG_TAG, "got other active sub  = " + i);
                int otherSub = i;
                return otherSub;
            }
        }
        return -1;
    }

    public void updateLchOnOtherSub(int subscription) {
        Phone bgPhone = null;
        int otherActiveSub = getOtherActiveSub(subscription);
        Rlog.d(LOG_TAG, " updateLchOnOtherSub subscription: " + subscription);
        if (otherActiveSub != -1) {
            if (getActiveFgCallState(otherActiveSub) == Call.State.IDLE) {
                if (hasActiveBgCall(otherActiveSub)) {
                    bgPhone = getBgPhone(otherActiveSub);
                }
            } else {
                bgPhone = getFgPhone(otherActiveSub);
            }
            if (LchState.ACTIVE != this.mLchStatus[otherActiveSub] && bgPhone != null) {
                Rlog.d(LOG_TAG, " setLocal Call Hold on sub: " + otherActiveSub);
                bgPhone.setLocalCallHold(1, this.mHandler.obtainMessage(EVENT_LOCAL_CALL_HOLD));
                this.mLchStatus[otherActiveSub] = LchState.ACTIVE;
            }
        }
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean canConference(Call heldCall, int subscription) {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall(subscription)) {
            activePhone = getActiveFgCall(subscription).getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        return heldPhone.getClass().equals(activePhone.getClass());
    }

    @Override // com.android.internal.telephony.CallManager
    public void conference(Call heldCall) throws CallStateException {
        int subscription = heldCall.getPhone().getSubscription();
        Phone fgPhone = getFgPhone(subscription);
        if (fgPhone instanceof SipPhone) {
            ((SipPhone) fgPhone).conference(heldCall);
        } else {
            if (canConference(heldCall)) {
                fgPhone.conference();
                return;
            }
            throw new CallStateException("Can't conference foreground and selected background call");
        }
    }

    @Override // com.android.internal.telephony.CallManager
    public void acceptCall(Call ringingCall, int callType) throws CallStateException {
        setSubInConversation(ringingCall.getPhone().getSubscription());
        updateLchOnOtherSub(ringingCall.getPhone().getSubscription());
        super.acceptCall(ringingCall, callType);
    }

    @Override // com.android.internal.telephony.CallManager
    public Connection dial(Phone phone, String dialString, int callType, String[] extras) throws CallStateException {
        Phone basePhone = getPhoneBase(phone);
        int subscription = phone.getSubscription();
        this.mDialString = dialString;
        if (!canDial(phone)) {
            throw new CallStateException("cannot dial in current state");
        }
        if (hasActiveFgCall(subscription)) {
            Phone activePhone = getActiveFgCall(subscription).getPhone();
            boolean hasBgCall = !activePhone.getBackgroundCall().isIdle();
            Rlog.d(LOG_TAG, "hasBgCall: " + hasBgCall + " sameChannel:" + (activePhone == basePhone));
            if (activePhone != basePhone) {
                if (hasBgCall) {
                    Rlog.d(LOG_TAG, "Hangup");
                    getActiveFgCall(subscription).hangup();
                } else {
                    Rlog.d(LOG_TAG, "Switch");
                    activePhone.switchHoldingAndActive();
                }
            }
        }
        setSubInConversation(subscription);
        if (!basePhone.isMMI(dialString)) {
            updateLchOnOtherSub(subscription);
        }
        if (phone.getPhoneType() == 4) {
            Connection result = basePhone.dial(dialString, callType, extras);
            return result;
        }
        Connection result2 = basePhone.dial(dialString);
        return result2;
    }

    @Override // com.android.internal.telephony.CallManager
    protected boolean canDial(Phone phone) {
        int serviceState = phone.getServiceState().getState();
        int subscription = phone.getSubscription();
        boolean hasRingingCall = hasActiveRingingCallOnAnySub();
        Call.State fgCallState = getActiveFgCallState(subscription);
        boolean result = (serviceState == 3 || hasRingingCall || (fgCallState != Call.State.ACTIVE && fgCallState != Call.State.IDLE && ((fgCallState != Call.State.ALERTING || !isExplicitCallTransferMMI(this.mDialString)) && fgCallState != Call.State.DISCONNECTED))) ? false : true;
        if (!result) {
            Rlog.d(LOG_TAG, "canDial serviceState=" + serviceState + " hasRingingCall=" + hasRingingCall + " fgCallState=" + fgCallState);
        }
        return result;
    }

    public boolean hasActiveRingingCallOnAnySub() {
        return super.hasActiveRingingCall();
    }

    @Override // com.android.internal.telephony.CallManager
    public void clearDisconnected() {
        clearDisconnected(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public void clearDisconnected(int subscription) {
        for (Phone phone : this.mPhones) {
            if (phone.getSubscription() == subscription) {
                phone.clearDisconnected();
            }
        }
    }

    public boolean canTransfer(Call heldCall, int subscription) {
        Phone activePhone = null;
        Phone heldPhone = null;
        if (hasActiveFgCall(subscription)) {
            activePhone = getActiveFgCall(subscription).getPhone();
        }
        if (heldCall != null) {
            heldPhone = heldCall.getPhone();
        }
        return heldPhone == activePhone && activePhone.canTransfer();
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasActiveFgCall() {
        return hasActiveFgCall(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasActiveFgCall(int subscription) {
        return getFirstActiveCall(this.mForegroundCalls, subscription) != null;
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasActiveFgCallAnyPhone() {
        return super.hasActiveFgCall();
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasActiveBgCall() {
        return hasActiveBgCall(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasActiveBgCall(int subscription) {
        return getFirstActiveCall(this.mBackgroundCalls, subscription) != null;
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasActiveRingingCall() {
        return hasActiveRingingCall(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasActiveRingingCall(int subscription) {
        return getFirstActiveCall(this.mRingingCalls, subscription) != null;
    }

    @Override // com.android.internal.telephony.CallManager
    public Call getActiveFgCall() {
        return getActiveFgCall(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public Call getActiveFgCall(int subscription) {
        Call call = getFirstNonIdleCall(this.mForegroundCalls, subscription);
        if (call == null) {
            Phone phone = getPhone(subscription);
            if (phone == null) {
                return null;
            }
            return phone.getForegroundCall();
        }
        return call;
    }

    private Call getFirstNonIdleCall(List<Call> calls, int subscription) {
        Call result = null;
        for (Call call : calls) {
            if (call.getPhone().getSubscription() == subscription || (call.getPhone() instanceof SipPhone)) {
                if (call.isIdle()) {
                    if (call.getState() != Call.State.IDLE && result == null) {
                        result = call;
                    }
                } else {
                    return call;
                }
            }
        }
        return result;
    }

    @Override // com.android.internal.telephony.CallManager
    public Call getFirstActiveBgCall() {
        return getFirstActiveBgCall(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public Call getFirstActiveBgCall(int subscription) {
        Phone phone = getPhone(subscription);
        if (hasMoreThanOneHoldingCall(subscription)) {
            return phone.getBackgroundCall();
        }
        Call call = getFirstNonIdleCall(this.mBackgroundCalls, subscription);
        if (call == null) {
            if (phone == null) {
                return null;
            }
            return phone.getBackgroundCall();
        }
        return call;
    }

    @Override // com.android.internal.telephony.CallManager
    public Call getFirstActiveRingingCall() {
        return getFirstActiveRingingCall(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public Call getFirstActiveRingingCall(int subscription) {
        Phone phone = getPhone(subscription);
        Call call = getFirstNonIdleCall(this.mRingingCalls, subscription);
        if (call == null) {
            if (phone == null) {
                return null;
            }
            return phone.getRingingCall();
        }
        return call;
    }

    @Override // com.android.internal.telephony.CallManager
    public Call.State getActiveFgCallState() {
        return getActiveFgCallState(getActiveSubscription());
    }

    @Override // com.android.internal.telephony.CallManager
    public Call.State getActiveFgCallState(int subscription) {
        Call fgCall = getActiveFgCall(subscription);
        return fgCall != null ? fgCall.getState() : Call.State.IDLE;
    }

    @Override // com.android.internal.telephony.CallManager
    public List<Connection> getFgCallConnections(int subscription) {
        Call fgCall = getActiveFgCall(subscription);
        return fgCall != null ? fgCall.getConnections() : this.mEmptyConnections;
    }

    public List<Connection> getBgCallConnections(int subscription) {
        Call bgCall = getFirstActiveBgCall(subscription);
        return bgCall != null ? bgCall.getConnections() : this.mEmptyConnections;
    }

    @Override // com.android.internal.telephony.CallManager
    public Connection getFgCallLatestConnection(int subscription) {
        Call fgCall = getActiveFgCall(subscription);
        if (fgCall != null) {
            return fgCall.getLatestConnection();
        }
        return null;
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasDisconnectedFgCall(int subscription) {
        return getFirstCallOfState(this.mForegroundCalls, Call.State.DISCONNECTED, subscription) != null;
    }

    @Override // com.android.internal.telephony.CallManager
    public boolean hasDisconnectedBgCall(int subscription) {
        return getFirstCallOfState(this.mBackgroundCalls, Call.State.DISCONNECTED, subscription) != null;
    }

    private Call getFirstActiveCall(ArrayList<Call> calls, int subscription) {
        Iterator<Call> it = calls.iterator();
        while (it.hasNext()) {
            Call call = it.next();
            if (!call.isIdle() && (call.getPhone().getSubscription() == subscription || (call.getPhone() instanceof SipPhone))) {
                return call;
            }
        }
        return null;
    }

    private Call getFirstCallOfState(ArrayList<Call> calls, Call.State state, int subscription) {
        Iterator<Call> it = calls.iterator();
        while (it.hasNext()) {
            Call call = it.next();
            if (call.getState() == state || call.getPhone().getSubscription() == subscription || (call.getPhone() instanceof SipPhone)) {
                return call;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean hasMoreThanOneRingingCall() {
        int subscription = getActiveSubscription();
        int count = 0;
        for (Call call : this.mRingingCalls) {
            if (call.getState().isRinging() && (call.getPhone().getSubscription() == subscription || (call.getPhone() instanceof SipPhone))) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMoreThanOneHoldingCall(int subscription) {
        int count = 0;
        for (Call call : this.mBackgroundCalls) {
            if (call.getState() == Call.State.HOLDING && (call.getPhone().getSubscription() == subscription || (call.getPhone() instanceof SipPhone))) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.CallManager
    public void startDtmf(char c, int subscription) {
        getPhone(subscription).startDtmf(c);
    }

    @Override // com.android.internal.telephony.CallManager
    public void stopDtmf(int subscription) {
        getPhone(subscription).stopDtmf();
    }

    @Override // com.android.internal.telephony.CallManager
    public void registerForSubscriptionChange(Handler h, int what, Object obj) {
        this.mActiveSubChangeRegistrants.addUnique(h, what, obj);
    }

    @Override // com.android.internal.telephony.CallManager
    public void unregisterForSubscriptionChange(Handler h) {
        this.mActiveSubChangeRegistrants.remove(h);
    }

    protected class ExtCmHandler extends CallManager.CmHandler {
        protected ExtCmHandler() {
            super();
        }

        @Override // com.android.internal.telephony.CallManager.CmHandler, android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 102:
                    Connection c = (Connection) ((AsyncResult) msg.obj).result;
                    int sub = c.getCall().getPhone().getSubscription();
                    if (ExtCallManager.this.getActiveFgCallState(sub).isDialing() || ExtCallManager.this.hasMoreThanOneRingingCall()) {
                        try {
                            Rlog.d(ExtCallManager.LOG_TAG, "silently drop incoming call: " + c.getCall());
                            c.getCall().hangup();
                        } catch (CallStateException e) {
                            Rlog.w(ExtCallManager.LOG_TAG, "new ringing connection", e);
                            return;
                        }
                    } else {
                        ExtCallManager.this.mNewRingingConnectionRegistrants.notifyRegistrants((AsyncResult) msg.obj);
                    }
                    break;
                case ExtCallManager.EVENT_LOCAL_CALL_HOLD /* 202 */:
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    @Override // com.android.internal.telephony.CallManager
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("ExtCallManager {");
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            b.append("\nSUB" + i);
            b.append("\nstate = " + getState(i));
            Call call = getActiveFgCall(i);
            b.append("\n- Foreground: " + getActiveFgCallState(i));
            b.append(" from " + call.getPhone());
            b.append("\n  Conn: ").append(getFgCallConnections(i));
            Call call2 = getFirstActiveBgCall(i);
            b.append("\n- Background: " + call2.getState());
            b.append(" from " + call2.getPhone());
            b.append("\n  Conn: ").append(getBgCallConnections(i));
            Call call3 = getFirstActiveRingingCall(i);
            b.append("\n- Ringing: " + call3.getState());
            b.append(" from " + call3.getPhone());
        }
        for (Phone phone : getAllPhones()) {
            if (phone != null) {
                b.append("\nPhone: " + phone + ", name = " + phone.getPhoneName() + ", state = " + phone.getState());
                Call call4 = phone.getForegroundCall();
                b.append("\n- Foreground: ").append(call4);
                Call call5 = phone.getBackgroundCall();
                b.append(" Background: ").append(call5);
                Call call6 = phone.getRingingCall();
                b.append(" Ringing: ").append(call6);
            }
        }
        b.append("\n}");
        return b.toString();
    }
}
