package com.android.phone;

import android.content.Context;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.CallDetails;
import com.android.services.telephony.common.ICallCommandService;

/* JADX INFO: loaded from: classes.dex */
class CallCommandService extends ICallCommandService.Stub {
    private static final boolean DBG;
    private static final String TAG = CallCommandService.class.getSimpleName();
    private final AudioRouter mAudioRouter;
    private final CallManager mCallManager;
    private final CallModeler mCallModeler;
    private final Context mContext;
    private final DTMFTonePlayer mDtmfTonePlayer;

    static {
        DBG = SystemProperties.getInt("ro.debuggable", 0) == 1;
    }

    public CallCommandService(Context context, CallManager callManager, CallModeler callModeler, DTMFTonePlayer dtmfTonePlayer, AudioRouter audioRouter) {
        this.mContext = context;
        this.mCallManager = callManager;
        this.mCallModeler = callModeler;
        this.mDtmfTonePlayer = dtmfTonePlayer;
        this.mAudioRouter = audioRouter;
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void answerCall(int callId) {
        try {
            CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
            if (result != null) {
                answerCallWithCallType(callId, 10);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during answerCall().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void answerCallWithCallType(int callId, int callType) {
        Log.v(TAG, "answerCallWithCallType" + callId + " calltype" + callType);
        try {
            CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
            if (result != null && callType != 10) {
                result.mCall.getCallDetails().setCallType(callType);
            }
            if (this.mCallManager.hasActiveFgCall() && this.mCallManager.hasActiveBgCall()) {
                PhoneUtils.answerAndEndActive(this.mCallManager, result.getConnection().getCall());
            } else {
                PhoneUtils.answerCall(result.getConnection().getCall(), callType);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during answerCall().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void modifyCallInitiate(int callId, int callType) {
        Log.v(TAG, "modifyCallInitiate: callId=" + callId + "callType=" + callType);
        try {
            CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
            if (result != null) {
                PhoneUtils.modifyCallInitiate(result.getConnection(), callType, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during modifyCallInitiate().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void modifyCallConfirm(boolean responseType, int callId) {
        Log.v(TAG, "modifyCallConfirmresponseType " + responseType + "callId" + callId);
        try {
            CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
            if (result != null) {
                CallDetails callModify = result.mCall.getCallModifyDetails();
                PhoneUtils.modifyCallConfirm(responseType, result.getConnection(), callModify.getExtras());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during modifyCallInitiate().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void rejectCall(Call call, boolean rejectWithMessage, String message) {
        int callId = -1;
        String phoneNumber = "";
        int subscription = 0;
        if (call != null) {
            try {
                callId = call.getCallId();
                phoneNumber = call.getNumber();
                subscription = call.getSubscription();
            } catch (Exception e) {
                Log.e(TAG, "Error during rejectCall().", e);
                return;
            }
        }
        CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
        if (result != null) {
            phoneNumber = result.getConnection().getAddress();
            Log.v(TAG, "Hanging up");
            PhoneUtils.hangupRingingCall(result.getConnection().getCall());
        }
        if (rejectWithMessage && !phoneNumber.isEmpty()) {
            RejectWithTextMessageManager.rejectCallWithMessage(phoneNumber, message, subscription);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void disconnectCall(int callId) {
        try {
            CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
            if (DBG) {
                Log.d(TAG, "disconnectCall " + result.getCall());
            }
            if (result != null) {
                int state = result.getCall().getState();
                if (2 == state || 7 == state || 5 == state) {
                    result.getConnection().getCall().hangup();
                } else if (10 == state) {
                    result.getConnection().hangup();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during disconnectCall().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void separateCall(int callId) {
        try {
            CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
            if (DBG) {
                Log.d(TAG, "disconnectCall " + result.getCall());
            }
            if (result != null) {
                int state = result.getCall().getState();
                if (10 == state) {
                    result.getConnection().separate();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error trying to separate call.", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void hold(int callId, boolean hold) {
        try {
            CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
            if (result != null) {
                int state = result.getCall().getState();
                if (hold && 2 == state) {
                    PhoneUtils.switchHoldingAndActive(this.mCallManager.getFirstActiveBgCall());
                } else if (!hold && 7 == state) {
                    PhoneUtils.switchHoldingAndActive(result.getConnection().getCall());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error trying to place call on hold.", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void merge() {
        if (PhoneUtils.okToMergeCalls(this.mCallManager)) {
            PhoneUtils.mergeCalls(this.mCallManager);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void addCall() {
        PhoneUtils.startNewCall(this.mCallManager);
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void swap() {
        try {
            PhoneUtils.swap();
        } catch (Exception e) {
            Log.e(TAG, "Error during swap().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void mute(boolean onOff) {
        try {
            PhoneUtils.setMute(onOff);
        } catch (Exception e) {
            Log.e(TAG, "Error during mute().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void muteInternal(boolean onOff) {
        try {
            PhoneUtils.muteOnNewCall(onOff);
        } catch (Exception e) {
            Log.e(TAG, "Error during mute().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void updateMuteState(int sub, boolean muted) {
        try {
            PhoneUtils.updateMuteState(sub, muted);
        } catch (Exception e) {
            Log.e(TAG, "Error during updateMuteState().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void speaker(boolean onOff) {
        try {
            PhoneUtils.turnOnSpeaker(this.mContext, onOff, true);
        } catch (Exception e) {
            Log.e(TAG, "Error during speaker().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void playDtmfTone(char digit, boolean timedShortTone) {
        try {
            this.mDtmfTonePlayer.playDtmfTone(digit, timedShortTone);
        } catch (Exception e) {
            Log.e(TAG, "Error playing DTMF tone.", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void stopDtmfTone() {
        try {
            this.mDtmfTonePlayer.stopDtmfTone();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping DTMF tone.", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void setAudioMode(int mode) {
        try {
            this.mAudioRouter.setAudioMode(mode);
        } catch (Exception e) {
            Log.e(TAG, "Error setting the audio mode.", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void postDialCancel(int callId) throws RemoteException {
        CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
        if (result != null) {
            result.getConnection().cancelPostDial();
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void postDialWaitContinue(int callId) throws RemoteException {
        CallModeler.CallResult result = this.mCallModeler.getCallWithId(callId);
        if (result != null) {
            result.getConnection().proceedAfterWaitChar();
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void hangupWithReason(int callId, String userUri, boolean mpty, int failCause, String errorInfo) {
        try {
            Log.d(TAG, "hangupWithReason");
            PhoneUtils.hangupWithReason(callId, userUri, mpty, failCause, errorInfo);
        } catch (Exception e) {
            Log.e(TAG, "Error hangupWithReason", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void setSystemBarNavigationEnabled(boolean enable) {
        try {
            NotificationMgr.StatusBarHelper statusBarHelper = PhoneGlobals.getInstance().notificationMgr.statusBarHelper;
            statusBarHelper.enableSystemBarNavigation(enable);
            statusBarHelper.enableExpandedView(enable);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling or disabling system bar navigation", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void setActiveSubscription(int subscriptionId) {
        try {
            PhoneUtils.setActiveSubscription(subscriptionId);
            if (this.mCallManager.getState(subscriptionId) == PhoneConstants.State.OFFHOOK && this.mCallManager.getSubInConversation() == -1) {
                if (DBG) {
                    Log.d(TAG, "setActiveSubscription: call setMute");
                }
                PhoneUtils.setMute(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during setActiveSubscription().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void setSubInConversation(int subscriptionId) {
        try {
            this.mCallManager.setSubInConversation(subscriptionId);
        } catch (Exception e) {
            Log.e(TAG, "Error during setSubInConversation().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public void setActiveAndConversationSub(int subscriptionId) {
        try {
            PhoneUtils.setActiveAndConversationSub(subscriptionId);
        } catch (Exception e) {
            Log.e(TAG, "Error during setActiveAndConversationSub().", e);
        }
    }

    @Override // com.android.services.telephony.common.ICallCommandService
    public int getActiveSubscription() {
        try {
            int subscriptionId = PhoneUtils.getActiveSubscription();
            return subscriptionId;
        } catch (Exception e) {
            Log.e(TAG, "Error during getActiveSubscription().", e);
            return -1;
        }
    }
}
