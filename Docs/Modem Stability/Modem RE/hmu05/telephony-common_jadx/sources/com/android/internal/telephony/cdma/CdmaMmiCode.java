package com.android.internal.telephony.cdma;

import android.R;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class CdmaMmiCode extends Handler implements MmiCode {
    static final String ACTION_REGISTER = "**";
    static final int EVENT_SET_COMPLETE = 1;
    static final String LOG_TAG = "CdmaMmiCode";
    static final int MATCH_GROUP_ACTION = 2;
    static final int MATCH_GROUP_DIALING_NUMBER = 12;
    static final int MATCH_GROUP_POUND_STRING = 1;
    static final int MATCH_GROUP_PWD_CONFIRM = 11;
    static final int MATCH_GROUP_SERVICE_CODE = 3;
    static final int MATCH_GROUP_SIA = 5;
    static final int MATCH_GROUP_SIB = 7;
    static final int MATCH_GROUP_SIC = 9;
    static final String SC_PIN = "04";
    static final String SC_PIN2 = "042";
    static final String SC_PUK = "05";
    static final String SC_PUK2 = "052";
    static Pattern sPatternSuppService = Pattern.compile("((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)");
    String mAction;
    Context mContext;
    String mDialingNumber;
    CharSequence mMessage;
    CDMAPhone mPhone;
    String mPoundString;
    String mPwd;
    String mSc;
    String mSia;
    String mSib;
    String mSic;
    MmiCode.State mState;
    UiccCardApplication mUiccApplication;

    public static CdmaMmiCode newFromDialString(String dialString, CDMAPhone phone, UiccCardApplication app) {
        Matcher m = sPatternSuppService.matcher(dialString);
        if (!m.matches()) {
            return null;
        }
        CdmaMmiCode ret = new CdmaMmiCode(phone, app);
        ret.mPoundString = makeEmptyNull(m.group(1));
        ret.mAction = makeEmptyNull(m.group(2));
        ret.mSc = makeEmptyNull(m.group(3));
        ret.mSia = makeEmptyNull(m.group(5));
        ret.mSib = makeEmptyNull(m.group(7));
        ret.mSic = makeEmptyNull(m.group(9));
        ret.mPwd = makeEmptyNull(m.group(11));
        ret.mDialingNumber = makeEmptyNull(m.group(12));
        return ret;
    }

    private static String makeEmptyNull(String s) {
        if (s == null || s.length() != 0) {
            return s;
        }
        return null;
    }

    CdmaMmiCode(CDMAPhone phone, UiccCardApplication app) {
        super(phone.getHandler().getLooper());
        this.mState = MmiCode.State.PENDING;
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mUiccApplication = app;
    }

    @Override // com.android.internal.telephony.MmiCode
    public MmiCode.State getState() {
        return this.mState;
    }

    @Override // com.android.internal.telephony.MmiCode
    public CharSequence getMessage() {
        return this.mMessage;
    }

    @Override // com.android.internal.telephony.MmiCode
    public Phone getPhone() {
        return this.mPhone;
    }

    @Override // com.android.internal.telephony.MmiCode
    public void cancel() {
        if (this.mState != MmiCode.State.COMPLETE && this.mState != MmiCode.State.FAILED) {
            this.mState = MmiCode.State.CANCELLED;
            this.mPhone.onMMIDone(this);
        }
    }

    @Override // com.android.internal.telephony.MmiCode
    public boolean isCancelable() {
        return false;
    }

    boolean isPinPukCommand() {
        return this.mSc != null && (this.mSc.equals(SC_PIN) || this.mSc.equals(SC_PIN2) || this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2));
    }

    boolean isRegister() {
        return this.mAction != null && this.mAction.equals(ACTION_REGISTER);
    }

    @Override // com.android.internal.telephony.MmiCode
    public boolean isUssdRequest() {
        Rlog.w(LOG_TAG, "isUssdRequest is not implemented in CdmaMmiCode");
        return false;
    }

    void processCode() {
        try {
            if (isPinPukCommand()) {
                String oldPinOrPuk = this.mSia;
                String newPinOrPuk = this.mSib;
                int pinLen = newPinOrPuk.length();
                if (isRegister()) {
                    if (!newPinOrPuk.equals(this.mSic)) {
                        handlePasswordError(R.string.PERSOSUBSTATE_RUIM_CORPORATE_ENTRY);
                        return;
                    }
                    if (pinLen < 4 || pinLen > 8) {
                        handlePasswordError(R.string.PERSOSUBSTATE_RUIM_CORPORATE_ERROR);
                        return;
                    }
                    if (this.mSc.equals(SC_PIN) && this.mUiccApplication != null && this.mUiccApplication.getState() == IccCardApplicationStatus.AppState.APPSTATE_PUK) {
                        handlePasswordError(R.string.PERSOSUBSTATE_RUIM_CORPORATE_PUK_ENTRY);
                        return;
                    }
                    if (this.mUiccApplication != null) {
                        if (this.mSc.equals(SC_PIN)) {
                            this.mUiccApplication.changeIccLockPassword(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                            return;
                        }
                        if (this.mSc.equals(SC_PIN2)) {
                            this.mUiccApplication.changeIccFdnPassword(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                            return;
                        } else if (this.mSc.equals(SC_PUK)) {
                            this.mUiccApplication.supplyPuk(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                            return;
                        } else {
                            if (this.mSc.equals(SC_PUK2)) {
                                this.mUiccApplication.supplyPuk2(oldPinOrPuk, newPinOrPuk, obtainMessage(1, this));
                                return;
                            }
                            return;
                        }
                    }
                    return;
                }
                throw new RuntimeException("Unsupported service code=" + this.mSc);
            }
            throw new RuntimeException("No application mUiccApplicaiton is null");
        } catch (RuntimeException e) {
            this.mState = MmiCode.State.FAILED;
            this.mMessage = this.mContext.getText(R.string.CnirMmi);
            this.mPhone.onMMIDone(this);
        }
    }

    private void handlePasswordError(int res) {
        this.mState = MmiCode.State.FAILED;
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        sb.append(this.mContext.getText(res));
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        if (msg.what == 1) {
            AsyncResult ar = (AsyncResult) msg.obj;
            onSetComplete(msg, ar);
        } else {
            Rlog.e(LOG_TAG, "Unexpected reply");
        }
    }

    private CharSequence getScString() {
        return (this.mSc == null || !isPinPukCommand()) ? "" : this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_HRPD_PUK_SUCCESS);
    }

    private void onSetComplete(Message msg, AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            if (ar.exception instanceof CommandException) {
                CommandException.Error err = ((CommandException) ar.exception).getCommandError();
                if (err == CommandException.Error.PASSWORD_INCORRECT) {
                    if (isPinPukCommand()) {
                        sb.append(this.mContext.getText(R.string.Noon));
                        if (this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2)) {
                            sb.append(this.mContext.getText(R.string.Noon));
                        } else {
                            sb.append(this.mContext.getText(R.string.NetworkPreferenceSwitchTitle));
                        }
                        int attemptsRemaining = msg.arg1;
                        if (attemptsRemaining <= 0) {
                            Rlog.d(LOG_TAG, "onSetComplete: PUK locked, cancel as lock screen will handle this");
                            this.mState = MmiCode.State.CANCELLED;
                        } else if (attemptsRemaining > 0) {
                            Rlog.d(LOG_TAG, "onSetComplete: attemptsRemaining=" + attemptsRemaining);
                            sb.append(this.mContext.getResources().getQuantityString(R.fraction.config_autoBrightnessAdjustmentMaxGamma, attemptsRemaining, Integer.valueOf(attemptsRemaining)));
                        }
                    } else {
                        sb.append(this.mContext.getText(R.string.Midnight));
                    }
                } else if (err == CommandException.Error.SIM_PUK2) {
                    sb.append(this.mContext.getText(R.string.NetworkPreferenceSwitchTitle));
                    sb.append("\n");
                    sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_CORPORATE_PUK_ERROR));
                } else if (err == CommandException.Error.REQUEST_NOT_SUPPORTED) {
                    if (this.mSc.equals(SC_PIN)) {
                        sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_CORPORATE_PUK_IN_PROGRESS));
                    }
                } else {
                    sb.append(this.mContext.getText(R.string.CnirMmi));
                }
            } else {
                sb.append(this.mContext.getText(R.string.CnirMmi));
            }
        } else if (isRegister()) {
            this.mState = MmiCode.State.COMPLETE;
            sb.append(this.mContext.getText(R.string.EmergencyCallWarningSummary));
        } else {
            this.mState = MmiCode.State.FAILED;
            sb.append(this.mContext.getText(R.string.CnirMmi));
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }
}
