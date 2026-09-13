package com.android.internal.telephony.test;

import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public final class SimulatedCommands extends BaseCommands implements CommandsInterface, SimulatedRadioControl {
    private static final String DEFAULT_SIM_PIN2_CODE = "5678";
    private static final String DEFAULT_SIM_PIN_CODE = "1234";
    private static final String LOG_TAG = "SimulatedCommands";
    private static final String SIM_PUK2_CODE = "87654321";
    private static final String SIM_PUK_CODE = "12345678";
    HandlerThread mHandlerThread;
    int mNetworkType;
    int mNextCallFailCause;
    int mPausedResponseCount;
    ArrayList<Message> mPausedResponses;
    String mPin2Code;
    int mPin2UnlockAttempts;
    String mPinCode;
    int mPinUnlockAttempts;
    int mPuk2UnlockAttempts;
    int mPukUnlockAttempts;
    boolean mSimFdnEnabled;
    SimFdnState mSimFdnEnabledState;
    boolean mSimLockEnabled;
    SimLockState mSimLockedState;
    boolean mSsnNotifyOn;
    SimulatedGsmCallState simulatedCallState;
    private static final SimLockState INITIAL_LOCK_STATE = SimLockState.NONE;
    private static final SimFdnState INITIAL_FDN_STATE = SimFdnState.NONE;

    private enum SimFdnState {
        NONE,
        REQUIRE_PIN2,
        REQUIRE_PUK2,
        SIM_PERM_LOCKED
    }

    private enum SimLockState {
        NONE,
        REQUIRE_PIN,
        REQUIRE_PUK,
        SIM_PERM_LOCKED
    }

    public SimulatedCommands() {
        super(null);
        this.mSsnNotifyOn = false;
        this.mPausedResponses = new ArrayList<>();
        this.mNextCallFailCause = 16;
        this.mHandlerThread = new HandlerThread(LOG_TAG);
        this.mHandlerThread.start();
        Looper looper = this.mHandlerThread.getLooper();
        this.simulatedCallState = new SimulatedGsmCallState(looper);
        setRadioState(CommandsInterface.RadioState.RADIO_OFF);
        this.mSimLockedState = INITIAL_LOCK_STATE;
        this.mSimLockEnabled = this.mSimLockedState != SimLockState.NONE;
        this.mPinCode = DEFAULT_SIM_PIN_CODE;
        this.mSimFdnEnabledState = INITIAL_FDN_STATE;
        this.mSimFdnEnabled = this.mSimFdnEnabledState != SimFdnState.NONE;
        this.mPin2Code = DEFAULT_SIM_PIN2_CODE;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIccCardStatus(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin(String pin, Message result) {
        if (this.mSimLockedState != SimLockState.REQUIRE_PIN) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: wrong state, state=" + this.mSimLockedState);
            CommandException ex = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex);
            result.sendToTarget();
            return;
        }
        if (pin != null && pin.equals(this.mPinCode)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: success!");
            this.mPinUnlockAttempts = 0;
            this.mSimLockedState = SimLockState.NONE;
            this.mIccStatusChangedRegistrants.notifyRegistrants();
            if (result != null) {
                AsyncResult.forMessage(result, (Object) null, (Throwable) null);
                result.sendToTarget();
                return;
            }
            return;
        }
        if (result != null) {
            this.mPinUnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: failed! attempt=" + this.mPinUnlockAttempts);
            if (this.mPinUnlockAttempts >= 3) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: set state to REQUIRE_PUK");
                this.mSimLockedState = SimLockState.REQUIRE_PUK;
            }
            CommandException ex2 = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex2);
            result.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk(String puk, String newPin, Message result) {
        if (this.mSimLockedState != SimLockState.REQUIRE_PUK) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: wrong state, state=" + this.mSimLockedState);
            CommandException ex = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex);
            result.sendToTarget();
            return;
        }
        if (puk != null && puk.equals(SIM_PUK_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: success!");
            this.mSimLockedState = SimLockState.NONE;
            this.mPukUnlockAttempts = 0;
            this.mIccStatusChangedRegistrants.notifyRegistrants();
            if (result != null) {
                AsyncResult.forMessage(result, (Object) null, (Throwable) null);
                result.sendToTarget();
                return;
            }
            return;
        }
        if (result != null) {
            this.mPukUnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: failed! attempt=" + this.mPukUnlockAttempts);
            if (this.mPukUnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: set state to SIM_PERM_LOCKED");
                this.mSimLockedState = SimLockState.SIM_PERM_LOCKED;
            }
            CommandException ex2 = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex2);
            result.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin2(String pin2, Message result) {
        if (this.mSimFdnEnabledState != SimFdnState.REQUIRE_PIN2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: wrong state, state=" + this.mSimFdnEnabledState);
            CommandException ex = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex);
            result.sendToTarget();
            return;
        }
        if (pin2 != null && pin2.equals(this.mPin2Code)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: success!");
            this.mPin2UnlockAttempts = 0;
            this.mSimFdnEnabledState = SimFdnState.NONE;
            if (result != null) {
                AsyncResult.forMessage(result, (Object) null, (Throwable) null);
                result.sendToTarget();
                return;
            }
            return;
        }
        if (result != null) {
            this.mPin2UnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: failed! attempt=" + this.mPin2UnlockAttempts);
            if (this.mPin2UnlockAttempts >= 3) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: set state to REQUIRE_PUK2");
                this.mSimFdnEnabledState = SimFdnState.REQUIRE_PUK2;
            }
            CommandException ex2 = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex2);
            result.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        if (this.mSimFdnEnabledState != SimFdnState.REQUIRE_PUK2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: wrong state, state=" + this.mSimLockedState);
            CommandException ex = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex);
            result.sendToTarget();
            return;
        }
        if (puk2 != null && puk2.equals(SIM_PUK2_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: success!");
            this.mSimFdnEnabledState = SimFdnState.NONE;
            this.mPuk2UnlockAttempts = 0;
            if (result != null) {
                AsyncResult.forMessage(result, (Object) null, (Throwable) null);
                result.sendToTarget();
                return;
            }
            return;
        }
        if (result != null) {
            this.mPuk2UnlockAttempts++;
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: failed! attempt=" + this.mPuk2UnlockAttempts);
            if (this.mPuk2UnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: set state to SIM_PERM_LOCKED");
                this.mSimFdnEnabledState = SimFdnState.SIM_PERM_LOCKED;
            }
            CommandException ex2 = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex2);
            result.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin(String oldPin, String newPin, Message result) {
        if (oldPin != null && oldPin.equals(this.mPinCode)) {
            this.mPinCode = newPin;
            if (result != null) {
                AsyncResult.forMessage(result, (Object) null, (Throwable) null);
                result.sendToTarget();
                return;
            }
            return;
        }
        if (result != null) {
            Rlog.i(LOG_TAG, "[SimCmd] changeIccPin: pin failed!");
            CommandException ex = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex);
            result.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        if (oldPin2 != null && oldPin2.equals(this.mPin2Code)) {
            this.mPin2Code = newPin2;
            if (result != null) {
                AsyncResult.forMessage(result, (Object) null, (Throwable) null);
                result.sendToTarget();
                return;
            }
            return;
        }
        if (result != null) {
            Rlog.i(LOG_TAG, "[SimCmd] changeIccPin2: pin2 failed!");
            CommandException ex = new CommandException(CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, (Object) null, ex);
            result.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSuppServiceNotifications(boolean enable, Message result) {
        resultSuccess(result, null);
        if (enable && this.mSsnNotifyOn) {
            Rlog.w(LOG_TAG, "Supp Service Notifications already enabled!");
        }
        this.mSsnNotifyOn = enable;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryFacilityLock(String facility, String pin, int serviceClass, Message result) {
        queryFacilityLockForApp(facility, pin, serviceClass, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryFacilityLockForApp(String facility, String pin, int serviceClass, String appId, Message result) {
        if (facility != null && facility.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (result != null) {
                int[] r = new int[1];
                r[0] = this.mSimLockEnabled ? 1 : 0;
                Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: SIM is " + (r[0] == 0 ? "unlocked" : "locked"));
                AsyncResult.forMessage(result, r, (Throwable) null);
                result.sendToTarget();
                return;
            }
            return;
        }
        if (facility != null && facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            if (result != null) {
                int[] r2 = new int[1];
                r2[0] = this.mSimFdnEnabled ? 1 : 0;
                Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: FDN is " + (r2[0] == 0 ? "disabled" : "enabled"));
                AsyncResult.forMessage(result, r2, (Throwable) null);
                result.sendToTarget();
                return;
            }
            return;
        }
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setFacilityLock(String facility, boolean lockEnabled, String pin, int serviceClass, Message result) {
        setFacilityLockForApp(facility, lockEnabled, pin, serviceClass, null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setFacilityLockForApp(String facility, boolean lockEnabled, String pin, int serviceClass, String appId, Message result) {
        if (facility != null && facility.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (pin != null && pin.equals(this.mPinCode)) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin is valid");
                this.mSimLockEnabled = lockEnabled;
                if (result != null) {
                    AsyncResult.forMessage(result, (Object) null, (Throwable) null);
                    result.sendToTarget();
                    return;
                }
                return;
            }
            if (result != null) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin failed!");
                CommandException ex = new CommandException(CommandException.Error.GENERIC_FAILURE);
                AsyncResult.forMessage(result, (Object) null, ex);
                result.sendToTarget();
                return;
            }
            return;
        }
        if (facility != null && facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            if (pin != null && pin.equals(this.mPin2Code)) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 is valid");
                this.mSimFdnEnabled = lockEnabled;
                if (result != null) {
                    AsyncResult.forMessage(result, (Object) null, (Throwable) null);
                    result.sendToTarget();
                    return;
                }
                return;
            }
            if (result != null) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 failed!");
                CommandException ex2 = new CommandException(CommandException.Error.GENERIC_FAILURE);
                AsyncResult.forMessage(result, (Object) null, ex2);
                result.sendToTarget();
                return;
            }
            return;
        }
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyDepersonalization(String netpin, String type, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCurrentCalls(Message result) {
        if (this.mState == CommandsInterface.RadioState.RADIO_ON && !isSimLocked()) {
            resultSuccess(result, this.simulatedCallState.getDriverCalls());
        } else {
            resultFail(result, new CommandException(CommandException.Error.RADIO_NOT_AVAILABLE));
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    @Deprecated
    public void getPDPContextList(Message result) {
        getDataCallList(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDataCallList(Message result) {
        resultSuccess(result, new ArrayList(0));
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void dial(String address, int clirMode, Message result) {
        this.simulatedCallState.onDial(address);
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        this.simulatedCallState.onDial(address);
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMSIForApp(String aid, Message result) {
        resultSuccess(result, "012345678901234");
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMEI(Message result) {
        resultSuccess(result, "012345678901234");
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getIMEISV(Message result) {
        resultSuccess(result, "99");
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupConnection(int gsmIndex, Message result) {
        boolean success = this.simulatedCallState.onChld('1', (char) (gsmIndex + 48));
        if (!success) {
            Rlog.i("GSM", "[SimCmd] hangupConnection: resultFail");
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            Rlog.i("GSM", "[SimCmd] hangupConnection: resultSuccess");
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupWaitingOrBackground(Message result) {
        boolean success = this.simulatedCallState.onChld('0', (char) 0);
        if (!success) {
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void hangupForegroundResumeBackground(Message result) {
        boolean success = this.simulatedCallState.onChld('1', (char) 0);
        if (!success) {
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void switchWaitingOrHoldingAndActive(Message result) {
        boolean success = this.simulatedCallState.onChld('2', (char) 0);
        if (!success) {
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void conference(Message result) {
        boolean success = this.simulatedCallState.onChld('3', (char) 0);
        if (!success) {
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void explicitCallTransfer(Message result) {
        boolean success = this.simulatedCallState.onChld('4', (char) 0);
        if (!success) {
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void separateConnection(int gsmIndex, Message result) {
        char ch = (char) (gsmIndex + 48);
        boolean success = this.simulatedCallState.onChld('2', ch);
        if (!success) {
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acceptCall(Message result) {
        boolean success = this.simulatedCallState.onAnswer();
        if (!success) {
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void rejectCall(Message result) {
        boolean success = this.simulatedCallState.onChld('0', (char) 0);
        if (!success) {
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getLastCallFailCause(Message result) {
        int[] ret = {this.mNextCallFailCause};
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    @Deprecated
    public void getLastPdpFailCause(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getLastDataCallFailCause(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setMute(boolean enableMute, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getMute(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getSignalStrength(Message result) {
        int[] ret = {23, 0};
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setBandMode(int bandMode, Message result) {
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryAvailableBandMode(Message result) {
        int[] ret = {4, 2, 3, 4};
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendTerminalResponse(String contents, Message response) {
        resultSuccess(response, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendEnvelope(String contents, Message response) {
        resultSuccess(response, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendEnvelopeWithStatus(String contents, Message response) {
        resultSuccess(response, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void handleCallSetupRequestFromSim(boolean accept, Message response) {
        resultSuccess(response, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getVoiceRegistrationState(Message result) {
        String[] ret = {"5", null, null, null, null, null, null, null, null, null, null, null, null, null};
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDataRegistrationState(Message result) {
        String[] ret = {"5", null, null, "2"};
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getOperator(Message result) {
        String[] ret = {"El Telco Loco", "Telco Loco", "001001"};
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendDtmf(char c, Message result) {
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void startDtmf(char c, Message result) {
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void stopDtmf(Message result) {
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendSMS(String smscPDU, String pdu, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deleteSmsOnSim(int index, Message response) {
        Rlog.d(LOG_TAG, "Delete message at index " + index);
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deleteSmsOnRuim(int index, Message response) {
        Rlog.d(LOG_TAG, "Delete RUIM message at index " + index);
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        Rlog.d(LOG_TAG, "Write SMS to SIM with status " + status);
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void writeSmsToRuim(int status, String pdu, Message response) {
        Rlog.d(LOG_TAG, "Write SMS to RUIM with status " + status);
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setupDataCall(String radioTechnology, String profile, String apn, String user, String password, String authType, String protocol, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void deactivateDataCall(int cid, int reason, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPreferredNetworkType(int networkType, Message result) {
        this.mNetworkType = networkType;
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getPreferredNetworkType(Message result) {
        int[] ret = {this.mNetworkType};
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getNeighboringCids(Message result) {
        int[] ret = new int[7];
        ret[0] = 6;
        for (int i = 1; i < 7; i++) {
            ret[i] = i;
        }
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setLocationUpdates(boolean enable, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getSmscAddress(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setSmscAddress(String address, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void reportSmsMemoryStatus(boolean available, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void reportStkServiceIsRunning(Message result) {
        resultSuccess(result, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCdmaSubscriptionSource(Message result) {
        unimplemented(result);
    }

    private boolean isSimLocked() {
        return this.mSimLockedState != SimLockState.NONE;
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setRadioPower(boolean on, Message result) {
        if (on) {
            setRadioState(CommandsInterface.RadioState.RADIO_ON);
        } else {
            setRadioState(CommandsInterface.RadioState.RADIO_OFF);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, Message response) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCLIP(Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCLIR(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCLIR(int clirMode, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCallWaiting(int serviceClass, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setNetworkSelectionModeAutomatic(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void exitEmergencyCallbackMode(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setNetworkSelectionModeManual(String operatorNumeric, Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getNetworkSelectionMode(Message result) {
        int[] ret = {0};
        resultSuccess(result, ret);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getAvailableNetworks(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getBasebandVersion(Message result) {
        resultSuccess(result, LOG_TAG);
    }

    public void triggerIncomingStkCcAlpha(String alphaString) {
        if (this.mCatCcAlphaRegistrant != null) {
            this.mCatCcAlphaRegistrant.notifyResult(alphaString);
        }
    }

    public void sendStkCcAplha(String alphaString) {
        triggerIncomingStkCcAlpha(alphaString);
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void triggerIncomingUssd(String statusCode, String message) {
        if (this.mUSSDRegistrant != null) {
            String[] result = {statusCode, message};
            this.mUSSDRegistrant.notifyResult(result);
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendUSSD(String ussdString, Message result) {
        if (ussdString.equals("#646#")) {
            resultSuccess(result, null);
            triggerIncomingUssd("0", "You have NNN minutes remaining.");
        } else {
            resultSuccess(result, null);
            triggerIncomingUssd("0", "All Done");
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void cancelPendingUssd(Message response) {
        resultSuccess(response, null);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void resetRadio(Message result) {
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        if (response != null) {
            AsyncResult.forMessage(response).result = data;
            response.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        if (response != null) {
            AsyncResult.forMessage(response).result = strings;
            response.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void triggerRing(String number) {
        this.simulatedCallState.triggerRing(number);
        this.mCallStateRegistrants.notifyRegistrants();
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void progressConnectingCallState() {
        this.simulatedCallState.progressConnectingCallState();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void progressConnectingToActive() {
        this.simulatedCallState.progressConnectingToActive();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void setAutoProgressConnectingCall(boolean b) {
        this.simulatedCallState.setAutoProgressConnectingCall(b);
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void setNextDialFailImmediately(boolean b) {
        this.simulatedCallState.setNextDialFailImmediately(b);
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void setNextCallFailCause(int gsmCause) {
        this.mNextCallFailCause = gsmCause;
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void triggerHangupForeground() {
        this.simulatedCallState.triggerHangupForeground();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void triggerHangupBackground() {
        this.simulatedCallState.triggerHangupBackground();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void triggerSsn(int type, int code) {
        SuppServiceNotification not = new SuppServiceNotification();
        not.notificationType = type;
        not.code = code;
        this.mSsnRegistrant.notifyRegistrant(new AsyncResult((Object) null, not, (Throwable) null));
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void shutdown() {
        setRadioState(CommandsInterface.RadioState.RADIO_UNAVAILABLE);
        Looper looper = this.mHandlerThread.getLooper();
        if (looper != null) {
            looper.quit();
        }
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void triggerHangupAll() {
        this.simulatedCallState.triggerHangupAll();
        this.mCallStateRegistrants.notifyRegistrants();
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void triggerIncomingSMS(String message) {
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void pauseResponses() {
        this.mPausedResponseCount++;
    }

    @Override // com.android.internal.telephony.test.SimulatedRadioControl
    public void resumeResponses() {
        this.mPausedResponseCount--;
        if (this.mPausedResponseCount == 0) {
            int s = this.mPausedResponses.size();
            for (int i = 0; i < s; i++) {
                this.mPausedResponses.get(i).sendToTarget();
            }
            this.mPausedResponses.clear();
            return;
        }
        Rlog.e("GSM", "SimulatedCommands.resumeResponses < 0");
    }

    private void unimplemented(Message result) {
        if (result != null) {
            AsyncResult.forMessage(result).exception = new RuntimeException("Unimplemented");
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    private void resultSuccess(Message result, Object ret) {
        if (result != null) {
            AsyncResult.forMessage(result).result = ret;
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    private void resultFail(Message result, Throwable tr) {
        if (result != null) {
            AsyncResult.forMessage(result).exception = tr;
            if (this.mPausedResponseCount > 0) {
                this.mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getDeviceIdentity(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCDMASubscription(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryCdmaRoamingPreference(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPhoneType(int phoneType) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getPreferredVoicePrivacy(Message result) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(result);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setTTYMode(int ttyMode, Message response) {
        Rlog.w(LOG_TAG, "Not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void queryTTYMode(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendCdmaSms(byte[] pdu, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaBroadcastActivation(boolean activate, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCdmaBroadcastConfig(Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        unimplemented(response);
    }

    public void forceDataDormancy(Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setGsmBroadcastActivation(boolean activate, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getGsmBroadcastConfig(Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPinForApp(String pin, String aid, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPin2ForApp(String pin2, String aid, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void requestIsimAuthentication(String nonce, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getVoiceRadioTechnology(Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getCellInfoList(Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setCellInfoListRate(int rateInMillis, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void setInitialAttachApn(String apn, String protocol, int authType, String username, String password, Message result) {
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void getImsRegistrationState(Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.CommandsInterface
    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccExchangeApdu(int cla, int command, int channel, int p1, int p2, int p3, String data, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccOpenChannel(String aid, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccCloseChannel(int channel, Message response) {
        unimplemented(response);
    }

    @Override // com.android.internal.telephony.BaseCommands, com.android.internal.telephony.CommandsInterface
    public void iccGetAtr(Message response) {
        unimplemented(response);
    }
}
