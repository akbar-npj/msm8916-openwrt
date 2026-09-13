package com.android.internal.telephony.gsm;

import android.R;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: classes.dex */
public final class GsmMmiCode extends Handler implements MmiCode {
    static final String ACTION_ACTIVATE = "*";
    static final String ACTION_DEACTIVATE = "#";
    static final String ACTION_ERASURE = "##";
    static final String ACTION_INTERROGATE = "*#";
    static final String ACTION_REGISTER = "**";
    static final char END_OF_USSD_COMMAND = '#';
    static final int EVENT_GET_CLIR_COMPLETE = 2;
    static final int EVENT_QUERY_CF_COMPLETE = 3;
    static final int EVENT_QUERY_COMPLETE = 5;
    static final int EVENT_SET_CFF_COMPLETE = 6;
    static final int EVENT_SET_COMPLETE = 1;
    static final int EVENT_USSD_CANCEL_COMPLETE = 7;
    static final int EVENT_USSD_COMPLETE = 4;
    static final String LOG_TAG = "GsmMmiCode";
    static final int MATCH_GROUP_ACTION = 2;
    static final int MATCH_GROUP_DIALING_NUMBER = 12;
    static final int MATCH_GROUP_POUND_STRING = 1;
    static final int MATCH_GROUP_PWD_CONFIRM = 11;
    static final int MATCH_GROUP_SERVICE_CODE = 3;
    static final int MATCH_GROUP_SIA = 5;
    static final int MATCH_GROUP_SIB = 7;
    static final int MATCH_GROUP_SIC = 9;
    static final int MAX_LENGTH_SHORT_CODE = 2;
    static final String SC_BAIC = "35";
    static final String SC_BAICr = "351";
    static final String SC_BAOC = "33";
    static final String SC_BAOIC = "331";
    static final String SC_BAOICxH = "332";
    static final String SC_BA_ALL = "330";
    static final String SC_BA_MO = "333";
    static final String SC_BA_MT = "353";
    static final String SC_CFB = "67";
    static final String SC_CFNR = "62";
    static final String SC_CFNRy = "61";
    static final String SC_CFU = "21";
    static final String SC_CF_All = "002";
    static final String SC_CF_All_Conditional = "004";
    static final String SC_CLIP = "30";
    static final String SC_CLIR = "31";
    static final String SC_PIN = "04";
    static final String SC_PIN2 = "042";
    static final String SC_PUK = "05";
    static final String SC_PUK2 = "052";
    static final String SC_PWD = "03";
    static final String SC_WAIT = "43";
    static Pattern sPatternSuppService = Pattern.compile("((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)");
    private static String[] sTwoDigitNumberPattern;
    String mAction;
    Context mContext;
    String mDialingNumber;
    IccRecords mIccRecords;
    private boolean mIsCallFwdReg;
    private boolean mIsPendingUSSD;
    private boolean mIsSsInfo;
    private boolean mIsUssdRequest;
    CharSequence mMessage;
    GSMPhone mPhone;
    String mPoundString;
    String mPwd;
    String mSc;
    String mSia;
    String mSib;
    String mSic;
    MmiCode.State mState;
    UiccCardApplication mUiccApplication;

    static GsmMmiCode newFromDialString(String dialString, GSMPhone phone, UiccCardApplication app) {
        Matcher m = sPatternSuppService.matcher(dialString);
        if (m.matches()) {
            GsmMmiCode ret = new GsmMmiCode(phone, app);
            ret.mPoundString = makeEmptyNull(m.group(1));
            ret.mAction = makeEmptyNull(m.group(2));
            ret.mSc = makeEmptyNull(m.group(3));
            ret.mSia = makeEmptyNull(m.group(5));
            ret.mSib = makeEmptyNull(m.group(7));
            ret.mSic = makeEmptyNull(m.group(9));
            ret.mPwd = makeEmptyNull(m.group(11));
            ret.mDialingNumber = makeEmptyNull(m.group(12));
            if (ret.mDialingNumber != null && ret.mDialingNumber.endsWith(ACTION_DEACTIVATE) && dialString.endsWith(ACTION_DEACTIVATE)) {
                GsmMmiCode ret2 = new GsmMmiCode(phone, app);
                ret2.mPoundString = dialString;
                return ret2;
            }
            return ret;
        }
        if (dialString.endsWith(ACTION_DEACTIVATE)) {
            GsmMmiCode ret3 = new GsmMmiCode(phone, app);
            ret3.mPoundString = dialString;
            return ret3;
        }
        if (isTwoDigitShortCode(phone.getContext(), dialString) || !isShortCode(dialString, phone)) {
            return null;
        }
        GsmMmiCode ret4 = new GsmMmiCode(phone, app);
        ret4.mDialingNumber = dialString;
        return ret4;
    }

    static GsmMmiCode newNetworkInitiatedUssd(String ussdMessage, boolean isUssdRequest, GSMPhone phone, UiccCardApplication app) {
        GsmMmiCode ret = new GsmMmiCode(phone, app);
        ret.mMessage = ussdMessage;
        ret.mIsUssdRequest = isUssdRequest;
        if (isUssdRequest) {
            ret.mIsPendingUSSD = true;
            ret.mState = MmiCode.State.PENDING;
        } else {
            ret.mState = MmiCode.State.COMPLETE;
        }
        return ret;
    }

    static GsmMmiCode newFromUssdUserInput(String ussdMessge, GSMPhone phone, UiccCardApplication app) {
        GsmMmiCode ret = new GsmMmiCode(phone, app);
        ret.mMessage = ussdMessge;
        ret.mState = MmiCode.State.PENDING;
        ret.mIsPendingUSSD = true;
        return ret;
    }

    void processSsData(AsyncResult data) {
        Rlog.d(LOG_TAG, "In processSsData");
        this.mIsSsInfo = true;
        try {
            SsData ssData = (SsData) data.result;
            parseSsData(ssData);
        } catch (ClassCastException ex) {
            Rlog.e(LOG_TAG, "Class Cast Exception in parsing SS Data : " + ex);
        } catch (NullPointerException ex2) {
            Rlog.e(LOG_TAG, "Null Pointer Exception in parsing SS Data : " + ex2);
        }
    }

    void parseSsData(SsData ssData) {
        CommandException ex = CommandException.fromRilErrno(ssData.result);
        this.mSc = getScStringFromScType(ssData.serviceType);
        this.mAction = getActionStringFromReqType(ssData.requestType);
        Rlog.d(LOG_TAG, "parseSsData msc = " + this.mSc + ", action = " + this.mAction + ", ex = " + ex);
        switch (ssData.requestType) {
            case SS_ACTIVATION:
            case SS_DEACTIVATION:
            case SS_REGISTRATION:
            case SS_ERASURE:
                if (ssData.result == 0 && ssData.serviceType.isTypeUnConditional()) {
                    boolean cffEnabled = (ssData.requestType == SsData.RequestType.SS_ACTIVATION || ssData.requestType == SsData.RequestType.SS_REGISTRATION) && isServiceClassVoiceorNone(ssData.serviceClass);
                    Rlog.d(LOG_TAG, "setVoiceCallForwardingFlag cffEnabled: " + cffEnabled);
                    if (this.mPhone.mIccRecords != null) {
                        this.mIccRecords.setVoiceCallForwardingFlag(1, cffEnabled, null);
                        Rlog.d(LOG_TAG, "setVoiceCallForwardingFlag done from SS Info.");
                    } else {
                        Rlog.e(LOG_TAG, "setVoiceCallForwardingFlag aborted. sim records is null.");
                    }
                }
                onSetComplete(null, new AsyncResult((Object) null, ssData.cfInfo, ex));
                break;
            case SS_INTERROGATION:
                if (ssData.serviceType.isTypeClir()) {
                    Rlog.d(LOG_TAG, "CLIR INTERROGATION");
                    onGetClirComplete(new AsyncResult((Object) null, ssData.ssInfo, ex));
                } else if (ssData.serviceType.isTypeCF()) {
                    Rlog.d(LOG_TAG, "CALL FORWARD INTERROGATION");
                    onQueryCfComplete(new AsyncResult((Object) null, ssData.cfInfo, ex));
                } else {
                    onQueryComplete(new AsyncResult((Object) null, ssData.ssInfo, ex));
                }
                break;
            default:
                Rlog.e(LOG_TAG, "Invaid requestType in SSData : " + ssData.requestType);
                break;
        }
    }

    private String getScStringFromScType(SsData.ServiceType sType) {
        switch (sType) {
            case SS_CFU:
                return SC_CFU;
            case SS_CF_BUSY:
                return SC_CFB;
            case SS_CF_NO_REPLY:
                return SC_CFNRy;
            case SS_CF_NOT_REACHABLE:
                return SC_CFNR;
            case SS_CF_ALL:
                return SC_CF_All;
            case SS_CF_ALL_CONDITIONAL:
                return SC_CF_All_Conditional;
            case SS_CLIP:
                return SC_CLIP;
            case SS_CLIR:
                return SC_CLIR;
            case SS_WAIT:
                return SC_WAIT;
            case SS_BAOC:
                return SC_BAOC;
            case SS_BAOIC:
                return SC_BAOIC;
            case SS_BAOIC_EXC_HOME:
                return SC_BAOICxH;
            case SS_BAIC:
                return SC_BAIC;
            case SS_BAIC_ROAMING:
                return SC_BAICr;
            case SS_ALL_BARRING:
                return SC_BA_ALL;
            case SS_OUTGOING_BARRING:
                return SC_BA_MO;
            case SS_INCOMING_BARRING:
                return SC_BA_MT;
            default:
                return "";
        }
    }

    private String getActionStringFromReqType(SsData.RequestType rType) {
        switch (rType) {
            case SS_ACTIVATION:
                return ACTION_ACTIVATE;
            case SS_DEACTIVATION:
                return ACTION_DEACTIVATE;
            case SS_REGISTRATION:
                return ACTION_REGISTER;
            case SS_ERASURE:
                return ACTION_ERASURE;
            case SS_INTERROGATION:
                return ACTION_INTERROGATE;
            default:
                return "";
        }
    }

    private boolean isServiceClassVoiceorNone(int serviceClass) {
        return (serviceClass & 1) != 0 || serviceClass == 0;
    }

    private static String makeEmptyNull(String s) {
        if (s == null || s.length() != 0) {
            return s;
        }
        return null;
    }

    private static boolean isEmptyOrNull(CharSequence s) {
        return s == null || s.length() == 0;
    }

    private static int scToCallForwardReason(String sc) {
        if (sc == null) {
            throw new RuntimeException("invalid call forward sc");
        }
        if (sc.equals(SC_CF_All)) {
            return 4;
        }
        if (sc.equals(SC_CFU)) {
            return 0;
        }
        if (sc.equals(SC_CFB)) {
            return 1;
        }
        if (sc.equals(SC_CFNR)) {
            return 3;
        }
        if (sc.equals(SC_CFNRy)) {
            return 2;
        }
        if (sc.equals(SC_CF_All_Conditional)) {
            return 5;
        }
        throw new RuntimeException("invalid call forward sc");
    }

    private static int siToServiceClass(String si) {
        if (si == null || si.length() == 0) {
            return 0;
        }
        int serviceCode = Integer.parseInt(si, 10);
        switch (serviceCode) {
            case 10:
                return 13;
            case 11:
                return 1;
            case 12:
                return 12;
            case 13:
                return 4;
            case 16:
                return 8;
            case 19:
                return 5;
            case 20:
                return 48;
            case 21:
                return 160;
            case 22:
                return 80;
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
                return 16;
            case SmsHeader.ELT_ID_CHARACTER_SIZE_WVG_OBJECT /* 25 */:
                return 32;
            case SmsHeader.ELT_ID_EXTENDED_OBJECT_DATA_REQUEST_CMD /* 26 */:
                return 17;
            case 99:
                return 64;
            default:
                throw new RuntimeException("unsupported MMI service code " + si);
        }
    }

    private static int siToTime(String si) {
        if (si == null || si.length() == 0) {
            return 0;
        }
        return Integer.parseInt(si, 10);
    }

    static boolean isServiceCodeCallForwarding(String sc) {
        return sc != null && (sc.equals(SC_CFU) || sc.equals(SC_CFB) || sc.equals(SC_CFNRy) || sc.equals(SC_CFNR) || sc.equals(SC_CF_All) || sc.equals(SC_CF_All_Conditional));
    }

    static boolean isServiceCodeCallBarring(String sc) {
        String[] barringMMI;
        Resources resource = Resources.getSystem();
        if (sc != null && (barringMMI = resource.getStringArray(R.array.config_cdma_dun_supported_types)) != null) {
            for (String match : barringMMI) {
                if (sc.equals(match)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String scToBarringFacility(String sc) {
        if (sc == null) {
            throw new RuntimeException("invalid call barring sc");
        }
        if (sc.equals(SC_BAOC)) {
            return CommandsInterface.CB_FACILITY_BAOC;
        }
        if (sc.equals(SC_BAOIC)) {
            return CommandsInterface.CB_FACILITY_BAOIC;
        }
        if (sc.equals(SC_BAOICxH)) {
            return CommandsInterface.CB_FACILITY_BAOICxH;
        }
        if (sc.equals(SC_BAIC)) {
            return CommandsInterface.CB_FACILITY_BAIC;
        }
        if (sc.equals(SC_BAICr)) {
            return CommandsInterface.CB_FACILITY_BAICr;
        }
        if (sc.equals(SC_BA_ALL)) {
            return CommandsInterface.CB_FACILITY_BA_ALL;
        }
        if (sc.equals(SC_BA_MO)) {
            return CommandsInterface.CB_FACILITY_BA_MO;
        }
        if (sc.equals(SC_BA_MT)) {
            return CommandsInterface.CB_FACILITY_BA_MT;
        }
        throw new RuntimeException("invalid call barring sc");
    }

    GsmMmiCode(GSMPhone phone, UiccCardApplication app) {
        super(phone.getHandler().getLooper());
        this.mState = MmiCode.State.PENDING;
        this.mIsSsInfo = false;
        this.mPhone = phone;
        this.mContext = phone.getContext();
        this.mUiccApplication = app;
        if (app != null) {
            this.mIccRecords = app.getIccRecords();
        }
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
            if (this.mIsPendingUSSD) {
                this.mPhone.mCi.cancelPendingUssd(obtainMessage(7, this));
            } else {
                this.mPhone.onMMIDone(this);
            }
        }
    }

    @Override // com.android.internal.telephony.MmiCode
    public boolean isCancelable() {
        return this.mIsPendingUSSD;
    }

    boolean isMMI() {
        return this.mPoundString != null;
    }

    boolean isShortCode() {
        return this.mPoundString == null && this.mDialingNumber != null && this.mDialingNumber.length() <= 2;
    }

    private static boolean isTwoDigitShortCode(Context context, String dialString) {
        Rlog.d(LOG_TAG, "isTwoDigitShortCode");
        if (dialString == null || dialString.length() != 2) {
            return false;
        }
        if (sTwoDigitNumberPattern == null) {
            sTwoDigitNumberPattern = context.getResources().getStringArray(R.array.config_bg_current_drain_high_threshold_to_restricted_bucket);
        }
        String[] arr$ = sTwoDigitNumberPattern;
        for (String dialnumber : arr$) {
            Rlog.d(LOG_TAG, "Two Digit Number Pattern " + dialnumber);
            if (dialString.equals(dialnumber)) {
                Rlog.d(LOG_TAG, "Two Digit Number Pattern -true");
                return true;
            }
        }
        Rlog.d(LOG_TAG, "Two Digit Number Pattern -false");
        return false;
    }

    private static boolean isShortCode(String dialString, GSMPhone phone) {
        if (dialString == null || dialString.length() == 0 || PhoneNumberUtils.isLocalEmergencyNumber(dialString, phone.getContext())) {
            return false;
        }
        return isShortCodeUSSD(dialString, phone);
    }

    private static boolean isShortCodeUSSD(String dialString, GSMPhone phone) {
        return (dialString == null || dialString.length() > 2 || (!phone.isInCall() && dialString.length() == 2 && dialString.charAt(0) == '1')) ? false : true;
    }

    boolean isPinPukCommand() {
        return this.mSc != null && (this.mSc.equals(SC_PIN) || this.mSc.equals(SC_PIN2) || this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2));
    }

    boolean isTemporaryModeCLIR() {
        return this.mSc != null && this.mSc.equals(SC_CLIR) && this.mDialingNumber != null && (isActivate() || isDeactivate());
    }

    int getCLIRMode() {
        if (this.mSc != null && this.mSc.equals(SC_CLIR)) {
            if (isActivate()) {
                return 2;
            }
            if (isDeactivate()) {
                return 1;
            }
        }
        return 0;
    }

    boolean isActivate() {
        return this.mAction != null && this.mAction.equals(ACTION_ACTIVATE);
    }

    boolean isDeactivate() {
        return this.mAction != null && this.mAction.equals(ACTION_DEACTIVATE);
    }

    boolean isInterrogate() {
        return this.mAction != null && this.mAction.equals(ACTION_INTERROGATE);
    }

    boolean isRegister() {
        return this.mAction != null && this.mAction.equals(ACTION_REGISTER);
    }

    boolean isErasure() {
        return this.mAction != null && this.mAction.equals(ACTION_ERASURE);
    }

    public boolean isPendingUSSD() {
        return this.mIsPendingUSSD;
    }

    @Override // com.android.internal.telephony.MmiCode
    public boolean isUssdRequest() {
        return this.mIsUssdRequest;
    }

    public boolean isSsInfo() {
        return this.mIsSsInfo;
    }

    void processCode() {
        String facility;
        int cfAction;
        try {
            if (isShortCode()) {
                Rlog.d(LOG_TAG, "isShortCode");
                sendUssd(this.mDialingNumber);
                return;
            }
            if (this.mDialingNumber != null) {
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            }
            if (this.mSc != null && this.mSc.equals(SC_CLIP)) {
                Rlog.d(LOG_TAG, "is CLIP");
                if (isInterrogate()) {
                    this.mPhone.mCi.queryCLIP(obtainMessage(5, this));
                    return;
                }
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            }
            if (this.mSc != null && this.mSc.equals(SC_CLIR)) {
                Rlog.d(LOG_TAG, "is CLIR");
                if (isActivate()) {
                    this.mPhone.mCi.setCLIR(1, obtainMessage(1, this));
                    return;
                } else if (isDeactivate()) {
                    this.mPhone.mCi.setCLIR(2, obtainMessage(1, this));
                    return;
                } else {
                    if (isInterrogate()) {
                        this.mPhone.mCi.getCLIR(obtainMessage(2, this));
                        return;
                    }
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                }
            }
            if (isServiceCodeCallForwarding(this.mSc)) {
                Rlog.d(LOG_TAG, "is CF");
                String dialingNumber = this.mSia;
                int serviceClass = siToServiceClass(this.mSib);
                int reason = scToCallForwardReason(this.mSc);
                int time = siToTime(this.mSic);
                if (isInterrogate()) {
                    this.mPhone.mCi.queryCallForwardStatus(reason, serviceClass, dialingNumber, obtainMessage(3, this));
                    return;
                }
                if (isActivate()) {
                    if (isEmptyOrNull(dialingNumber)) {
                        cfAction = 1;
                        this.mIsCallFwdReg = false;
                    } else {
                        cfAction = 3;
                        this.mIsCallFwdReg = true;
                    }
                } else if (isDeactivate()) {
                    cfAction = 0;
                } else if (isRegister()) {
                    cfAction = 3;
                } else if (isErasure()) {
                    cfAction = 4;
                } else {
                    throw new RuntimeException("invalid action");
                }
                int isSettingUnconditionalVoice = ((reason == 0 || reason == 4) && ((serviceClass & 1) != 0 || serviceClass == 0)) ? 1 : 0;
                int isEnableDesired = (cfAction == 1 || cfAction == 3) ? 1 : 0;
                Rlog.d(LOG_TAG, "is CF setCallForward");
                this.mPhone.mCi.setCallForward(cfAction, reason, serviceClass, dialingNumber, time, obtainMessage(6, isSettingUnconditionalVoice, isEnableDesired, this));
                return;
            }
            if (isServiceCodeCallBarring(this.mSc)) {
                String password = this.mSia;
                int serviceClass2 = siToServiceClass(this.mSib);
                String facility2 = scToBarringFacility(this.mSc);
                if (isInterrogate()) {
                    this.mPhone.mCi.queryFacilityLock(facility2, password, serviceClass2, obtainMessage(5, this));
                    return;
                } else {
                    if (isActivate() || isDeactivate()) {
                        this.mPhone.mCi.setFacilityLock(facility2, isActivate(), password, serviceClass2, obtainMessage(1, this));
                        return;
                    }
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                }
            }
            if (this.mSc != null && this.mSc.equals(SC_PWD)) {
                String oldPwd = this.mSib;
                String newPwd = this.mSic;
                if (isActivate() || isRegister()) {
                    this.mAction = ACTION_REGISTER;
                    if (this.mSia == null) {
                        facility = CommandsInterface.CB_FACILITY_BA_ALL;
                    } else {
                        facility = scToBarringFacility(this.mSia);
                    }
                    if (newPwd.equals(this.mPwd)) {
                        this.mPhone.mCi.changeBarringPassword(facility, oldPwd, newPwd, obtainMessage(1, this));
                        return;
                    } else {
                        handlePasswordError(R.string.Midnight);
                        return;
                    }
                }
                throw new RuntimeException("Invalid or Unsupported MMI Code");
            }
            if (this.mSc != null && this.mSc.equals(SC_WAIT)) {
                int serviceClass3 = siToServiceClass(this.mSia);
                if (isActivate() || isDeactivate()) {
                    this.mPhone.mCi.setCallWaiting(isActivate(), serviceClass3, obtainMessage(1, this));
                    return;
                } else {
                    if (isInterrogate()) {
                        this.mPhone.mCi.queryCallWaiting(serviceClass3, obtainMessage(5, this));
                        return;
                    }
                    throw new RuntimeException("Invalid or Unsupported MMI Code");
                }
            }
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
                        Rlog.d(LOG_TAG, "process mmi service code using UiccApp sc=" + this.mSc);
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
                            throw new RuntimeException("uicc unsupported service code=" + this.mSc);
                        }
                    }
                    throw new RuntimeException("No application mUiccApplicaiton is null");
                }
                throw new RuntimeException("Ivalid register/action=" + this.mAction);
            }
            if (this.mPoundString != null) {
                sendUssd(this.mPoundString);
                return;
            }
            throw new RuntimeException("Invalid or Unsupported MMI Code");
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

    void onUssdFinished(String ussdMessage, boolean isUssdRequest) {
        if (this.mState == MmiCode.State.PENDING) {
            if (ussdMessage == null) {
                this.mMessage = this.mContext.getText(R.string.NetworkPreferenceSwitchSummary);
            } else {
                this.mMessage = ussdMessage;
            }
            this.mIsUssdRequest = isUssdRequest;
            if (!isUssdRequest) {
                this.mState = MmiCode.State.COMPLETE;
            }
            this.mPhone.onMMIDone(this);
        }
    }

    void onUssdFinishedError() {
        if (this.mState == MmiCode.State.PENDING) {
            this.mState = MmiCode.State.FAILED;
            this.mMessage = this.mContext.getText(R.string.CnirMmi);
            this.mPhone.onMMIDone(this);
        }
    }

    void sendUssd(String ussdMessage) {
        this.mIsPendingUSSD = true;
        this.mPhone.mCi.sendUSSD(ussdMessage, obtainMessage(4, this));
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                onSetComplete(msg, (AsyncResult) msg.obj);
                break;
            case 2:
                onGetClirComplete((AsyncResult) msg.obj);
                break;
            case 3:
                onQueryCfComplete((AsyncResult) msg.obj);
                break;
            case 4:
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    this.mState = MmiCode.State.FAILED;
                    this.mMessage = getErrorMessage(ar);
                    this.mPhone.onMMIDone(this);
                }
                break;
            case 5:
                onQueryComplete((AsyncResult) msg.obj);
                break;
            case 6:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                if (ar2.exception == null && msg.arg1 == 1) {
                    boolean cffEnabled = msg.arg2 == 1;
                    if (this.mIccRecords != null) {
                        this.mIccRecords.setVoiceCallForwardingFlag(1, cffEnabled, this.mDialingNumber);
                        this.mPhone.setCallForwardingPreference(cffEnabled);
                    }
                }
                onSetComplete(msg, ar2);
                break;
            case 7:
                this.mPhone.onMMIDone(this);
                break;
        }
    }

    private CharSequence getErrorMessage(AsyncResult ar) {
        if (ar.exception instanceof CommandException) {
            CommandException.Error err = ((CommandException) ar.exception).getCommandError();
            if (err == CommandException.Error.FDN_CHECK_FAILURE) {
                Rlog.i(LOG_TAG, "FDN_CHECK_FAILURE");
                return this.mContext.getText(R.string.ColpMmi);
            }
            if (err == CommandException.Error.USSD_MODIFIED_TO_DIAL) {
                Rlog.i(LOG_TAG, "USSD_MODIFIED_TO_DIAL");
                return this.mContext.getText(R.string.mediasize_iso_a3);
            }
            if (err == CommandException.Error.USSD_MODIFIED_TO_SS) {
                Rlog.i(LOG_TAG, "USSD_MODIFIED_TO_SS");
                return this.mContext.getText(R.string.mediasize_iso_a4);
            }
            if (err == CommandException.Error.USSD_MODIFIED_TO_USSD) {
                Rlog.i(LOG_TAG, "USSD_MODIFIED_TO_USSD");
                return this.mContext.getText(R.string.mediasize_iso_a5);
            }
            if (err == CommandException.Error.SS_MODIFIED_TO_DIAL) {
                Rlog.i(LOG_TAG, "SS_MODIFIED_TO_DIAL");
                return this.mContext.getText(R.string.mediasize_iso_a6);
            }
            if (err == CommandException.Error.SS_MODIFIED_TO_USSD) {
                Rlog.i(LOG_TAG, "SS_MODIFIED_TO_USSD");
                return this.mContext.getText(R.string.mediasize_iso_a7);
            }
            if (err == CommandException.Error.SS_MODIFIED_TO_SS) {
                Rlog.i(LOG_TAG, "SS_MODIFIED_TO_SS");
                return this.mContext.getText(R.string.mediasize_iso_a8);
            }
        }
        return this.mContext.getText(R.string.CnirMmi);
    }

    private CharSequence getScString() {
        if (this.mSc != null) {
            if (isServiceCodeCallBarring(this.mSc)) {
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_HRPD_PUK_ERROR);
            }
            if (isServiceCodeCallForwarding(this.mSc)) {
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_HRPD_IN_PROGRESS);
            }
            if (this.mSc.equals(SC_CLIP)) {
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_HRPD_ENTRY);
            }
            if (this.mSc.equals(SC_CLIR)) {
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_HRPD_ERROR);
            }
            if (this.mSc.equals(SC_PWD)) {
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_HRPD_PUK_IN_PROGRESS);
            }
            if (this.mSc.equals(SC_WAIT)) {
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_HRPD_PUK_ENTRY);
            }
            if (isPinPukCommand()) {
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_HRPD_PUK_SUCCESS);
            }
        }
        return "";
    }

    private void onSetComplete(Message msg, AsyncResult ar) {
        int pukAttemptsRemaining;
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            if (ar.exception instanceof CommandException) {
                CommandException.Error err = ((CommandException) ar.exception).getCommandError();
                if (err == CommandException.Error.PASSWORD_INCORRECT) {
                    if (isPinPukCommand()) {
                        if (this.mSc.equals(SC_PUK) || this.mSc.equals(SC_PUK2)) {
                            sb.append(this.mContext.getText(R.string.Noon));
                            if (ar.result != null && ((int[]) ar.result).length != 0 && (pukAttemptsRemaining = ((int[]) ar.result)[0]) >= 0) {
                                sb.append(this.mContext.getText(R.string.conversation_single_line_image_placeholder));
                                sb.append(pukAttemptsRemaining);
                            }
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
                } else if (err == CommandException.Error.FDN_CHECK_FAILURE) {
                    Rlog.i(LOG_TAG, "FDN_CHECK_FAILURE");
                    sb.append(this.mContext.getText(R.string.ColpMmi));
                } else {
                    sb.append(getErrorMessage(ar));
                }
            } else {
                sb.append(this.mContext.getText(R.string.CnirMmi));
            }
        } else if (isActivate()) {
            this.mState = MmiCode.State.COMPLETE;
            if (this.mIsCallFwdReg) {
                sb.append(this.mContext.getText(R.string.EmergencyCallWarningSummary));
            } else {
                sb.append(this.mContext.getText(R.string.ColrMmi));
            }
            if (this.mSc.equals(SC_CLIR)) {
                this.mPhone.saveClirSetting(1);
            }
        } else if (isDeactivate()) {
            this.mState = MmiCode.State.COMPLETE;
            sb.append(this.mContext.getText(R.string.DndMmi));
            if (this.mSc.equals(SC_CLIR)) {
                this.mPhone.saveClirSetting(2);
            }
        } else if (isRegister()) {
            this.mState = MmiCode.State.COMPLETE;
            sb.append(this.mContext.getText(R.string.EmergencyCallWarningSummary));
        } else if (isErasure()) {
            this.mState = MmiCode.State.COMPLETE;
            sb.append(this.mContext.getText(R.string.EmergencyCallWarningTitle));
        } else {
            this.mState = MmiCode.State.FAILED;
            sb.append(this.mContext.getText(R.string.CnirMmi));
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private void onGetClirComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            sb.append(getErrorMessage(ar));
        } else {
            int[] clirArgs = (int[]) ar.result;
            switch (clirArgs[1]) {
                case 0:
                    sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_ERROR));
                    this.mState = MmiCode.State.COMPLETE;
                    break;
                case 1:
                    sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_IN_PROGRESS));
                    this.mState = MmiCode.State.COMPLETE;
                    break;
                case 2:
                    sb.append(this.mContext.getText(R.string.CnirMmi));
                    this.mState = MmiCode.State.FAILED;
                    break;
                case 3:
                    switch (clirArgs[0]) {
                        case 1:
                            sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK1_PUK_IN_PROGRESS));
                            break;
                        case 2:
                            sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK1_PUK_SUCCESS));
                            break;
                        default:
                            sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK1_PUK_IN_PROGRESS));
                            break;
                    }
                    this.mState = MmiCode.State.COMPLETE;
                    break;
                case 4:
                    switch (clirArgs[0]) {
                        case 1:
                            sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK1_SUCCESS));
                            break;
                        case 2:
                            sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_ENTRY));
                            break;
                        default:
                            sb.append(this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_NETWORK2_ENTRY));
                            break;
                    }
                    this.mState = MmiCode.State.COMPLETE;
                    break;
            }
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private CharSequence serviceClassToCFString(int serviceClass) {
        switch (serviceClass) {
            case 1:
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_RUIM_PUK_ERROR);
            case 2:
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_RUIM_PUK_IN_PROGRESS);
            case 4:
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_RUIM_PUK_SUCCESS);
            case 8:
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_RUIM_SUCCESS);
            case 16:
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_ERROR);
            case 32:
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_ENTRY);
            case 64:
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_IN_PROGRESS);
            case 128:
                return this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_ENTRY);
            default:
                return null;
        }
    }

    private CharSequence makeCFQueryResultMessage(CallForwardInfo info, int serviceClassMask) {
        CharSequence template;
        String[] sources = {"{0}", "{1}", "{2}"};
        CharSequence[] destinations = new CharSequence[3];
        boolean needTimeTemplate = info.reason == 2;
        if (info.status == 1) {
            if (needTimeTemplate) {
                template = this.mContext.getText(R.string.PERSOSUBSTATE_SIM_IMPI_ENTRY);
            } else {
                template = this.mContext.getText(R.string.PERSOSUBSTATE_SIM_ICCID_SUCCESS);
            }
        } else if (info.status == 0 && isEmptyOrNull(info.number)) {
            template = this.mContext.getText(R.string.PERSOSUBSTATE_SIM_ICCID_IN_PROGRESS);
        } else if (needTimeTemplate) {
            template = this.mContext.getText(R.string.PERSOSUBSTATE_SIM_IMPI_IN_PROGRESS);
        } else {
            template = this.mContext.getText(R.string.PERSOSUBSTATE_SIM_IMPI_ERROR);
        }
        destinations[0] = serviceClassToCFString(info.serviceClass & serviceClassMask);
        destinations[1] = PhoneNumberUtils.stringFromStringAndTOA(info.number, info.toa);
        destinations[2] = Integer.toString(info.timeSeconds);
        if (info.reason == 0 && (info.serviceClass & serviceClassMask) == 1) {
            boolean cffEnabled = info.status == 1;
            if (this.mIccRecords != null) {
                this.mIccRecords.setVoiceCallForwardingFlag(1, cffEnabled, info.number);
                this.mPhone.setCallForwardingPreference(cffEnabled);
            }
        }
        return TextUtils.replace(template, sources, destinations);
    }

    private void onQueryCfComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            sb.append(getErrorMessage(ar));
        } else {
            CallForwardInfo[] infos = (CallForwardInfo[]) ar.result;
            if (infos.length == 0) {
                sb.append(this.mContext.getText(R.string.DndMmi));
                if (this.mIccRecords != null) {
                    this.mPhone.setCallForwardingPreference(false);
                    this.mIccRecords.setVoiceCallForwardingFlag(1, false, null);
                }
            } else {
                SpannableStringBuilder tb = new SpannableStringBuilder();
                for (int serviceClassMask = 1; serviceClassMask <= 128; serviceClassMask <<= 1) {
                    int s = infos.length;
                    for (int i = 0; i < s; i++) {
                        if ((infos[i].serviceClass & serviceClassMask) != 0) {
                            tb.append(makeCFQueryResultMessage(infos[i], serviceClassMask));
                            tb.append((CharSequence) "\n");
                        }
                    }
                }
                sb.append((CharSequence) tb);
            }
            this.mState = MmiCode.State.COMPLETE;
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private void onQueryComplete(AsyncResult ar) {
        StringBuilder sb = new StringBuilder(getScString());
        sb.append("\n");
        if (ar.exception != null) {
            this.mState = MmiCode.State.FAILED;
            sb.append(getErrorMessage(ar));
        } else {
            int[] ints = (int[]) ar.result;
            if (ints.length != 0) {
                if (ints[0] == 0) {
                    sb.append(this.mContext.getText(R.string.DndMmi));
                } else if (this.mSc.equals(SC_WAIT)) {
                    sb.append(createQueryCallWaitingResultMessage(ints[1]));
                } else if (isServiceCodeCallBarring(this.mSc)) {
                    sb.append(createQueryCallBarringResultMessage(ints[0]));
                } else if (ints[0] == 1) {
                    sb.append(this.mContext.getText(R.string.ColrMmi));
                } else {
                    sb.append(this.mContext.getText(R.string.CnirMmi));
                }
            } else {
                sb.append(this.mContext.getText(R.string.CnirMmi));
            }
            this.mState = MmiCode.State.COMPLETE;
        }
        this.mMessage = sb;
        this.mPhone.onMMIDone(this);
    }

    private CharSequence createQueryCallWaitingResultMessage(int serviceClass) {
        StringBuilder sb = new StringBuilder(this.mContext.getText(R.string.CwMmi));
        for (int classMask = 1; classMask <= 128; classMask <<= 1) {
            if ((classMask & serviceClass) != 0) {
                sb.append("\n");
                sb.append(serviceClassToCFString(classMask & serviceClass));
            }
        }
        return sb;
    }

    private CharSequence createQueryCallBarringResultMessage(int serviceClass) {
        StringBuilder sb = new StringBuilder(this.mContext.getText(R.string.CwMmi));
        for (int classMask = 1; classMask <= 128; classMask <<= 1) {
            if ((classMask & serviceClass) != 0) {
                sb.append("\n");
                sb.append(serviceClassToCFString(classMask & serviceClass));
            }
        }
        return sb;
    }

    @Override // android.os.Handler
    public String toString() {
        StringBuilder sb = new StringBuilder("GsmMmiCode {");
        sb.append("State=" + getState());
        if (this.mAction != null) {
            sb.append(" action=" + this.mAction);
        }
        if (this.mSc != null) {
            sb.append(" sc=" + this.mSc);
        }
        if (this.mSia != null) {
            sb.append(" sia=" + this.mSia);
        }
        if (this.mSib != null) {
            sb.append(" sib=" + this.mSib);
        }
        if (this.mSic != null) {
            sb.append(" sic=" + this.mSic);
        }
        if (this.mPoundString != null) {
            sb.append(" poundString=" + this.mPoundString);
        }
        if (this.mDialingNumber != null) {
            sb.append(" dialingNumber=" + this.mDialingNumber);
        }
        if (this.mPwd != null) {
            sb.append(" pwd=" + this.mPwd);
        }
        sb.append("}");
        return sb.toString();
    }
}
