package com.android.internal.telephony.uicc;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.gsm.SimTlv;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public class SIMRecords extends IccRecords {
    static final int CFF_LINE1_MASK = 15;
    static final int CFF_LINE1_RESET = 240;
    static final int CFF_UNCONDITIONAL_ACTIVE = 10;
    static final int CFF_UNCONDITIONAL_DEACTIVE = 5;
    private static final int CFIS_ADN_CAPABILITY_ID_OFFSET = 14;
    private static final int CFIS_ADN_EXTENSION_ID_OFFSET = 15;
    private static final int CFIS_BCD_NUMBER_LENGTH_OFFSET = 2;
    private static final int CFIS_TON_NPI_OFFSET = 3;
    private static final int CPHS_SST_MBN_ENABLED = 48;
    private static final int CPHS_SST_MBN_MASK = 48;
    private static final boolean CRASH_RIL = false;
    protected static final int EVENT_GET_AD_DONE = 9;
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_GET_CFF_DONE = 24;
    private static final int EVENT_GET_CFIS_DONE = 32;
    private static final int EVENT_GET_CPHS_MAILBOX_DONE = 11;
    private static final int EVENT_GET_CSP_CPHS_DONE = 33;
    private static final int EVENT_GET_GID1_DONE = 34;
    private static final int EVENT_GET_ICCID_DONE = 4;
    private static final int EVENT_GET_IMSI_DONE = 3;
    private static final int EVENT_GET_INFO_CPHS_DONE = 26;
    private static final int EVENT_GET_MBDN_DONE = 6;
    private static final int EVENT_GET_MBI_DONE = 5;
    protected static final int EVENT_GET_MSISDN_DONE = 10;
    private static final int EVENT_GET_MWIS_DONE = 7;
    private static final int EVENT_GET_PNN_DONE = 15;
    private static final int EVENT_GET_SMS_DONE = 22;
    private static final int EVENT_GET_SPDI_DONE = 13;
    private static final int EVENT_GET_SPN_DONE = 12;
    protected static final int EVENT_GET_SST_DONE = 17;
    private static final int EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE = 8;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;
    private static final int EVENT_SET_CPHS_MAILBOX_DONE = 25;
    private static final int EVENT_SET_MBDN_DONE = 20;
    private static final int EVENT_UPDATE_DONE = 14;
    protected static final String LOG_TAG = "SIMRecords";
    private static final String[] MCCMNC_CODES_HAVING_3DIGITS_MNC = {"302370", "302720", "310260", "405025", "405026", "405027", "405028", "405029", "405030", "405031", "405032", "405033", "405034", "405035", "405036", "405037", "405038", "405039", "405040", "405041", "405042", "405043", "405044", "405045", "405046", "405047", "405750", "405751", "405752", "405753", "405754", "405755", "405756", "405799", "405800", "405801", "405802", "405803", "405804", "405805", "405806", "405807", "405808", "405809", "405810", "405811", "405812", "405813", "405814", "405815", "405816", "405817", "405818", "405819", "405820", "405821", "405822", "405823", "405824", "405825", "405826", "405827", "405828", "405829", "405830", "405831", "405832", "405833", "405834", "405835", "405836", "405837", "405838", "405839", "405840", "405841", "405842", "405843", "405844", "405845", "405846", "405847", "405848", "405849", "405850", "405851", "405852", "405853", "405875", "405876", "405877", "405878", "405879", "405880", "405881", "405882", "405883", "405884", "405885", "405886", "405908", "405909", "405910", "405911", "405912", "405913", "405914", "405915", "405916", "405917", "405918", "405919", "405920", "405921", "405922", "405923", "405924", "405925", "405926", "405927", "405928", "405929", "405930", "405931", "405932", "502142", "502143", "502145", "502146", "502147", "502148"};
    static final int TAG_FULL_NETWORK_NAME = 67;
    static final int TAG_SHORT_NETWORK_NAME = 69;
    static final int TAG_SPDI = 163;
    static final int TAG_SPDI_PLMN_LIST = 128;
    private boolean mCallForwardingEnabled;
    private byte[] mCphsInfo;
    boolean mCspPlmnEnabled;
    byte[] mEfCPHS_MWI;
    byte[] mEfCff;
    byte[] mEfCfis;
    byte[] mEfMWIS;
    String mPnnHomeName;
    ArrayList<String> mSpdiNetworks;
    int mSpnDisplayCondition;
    SpnOverride mSpnOverride;
    private GetSpnFsmState mSpnState;
    UsimServiceTable mUsimServiceTable;
    VoiceMailConstants mVmConfig;

    private enum GetSpnFsmState {
        IDLE,
        INIT,
        READ_SPN_3GPP,
        READ_SPN_CPHS,
        READ_SPN_SHORT_CPHS
    }

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public String toString() {
        return "SimRecords: " + super.toString() + " mVmConfig" + this.mVmConfig + " mSpnOverride=mSpnOverride callForwardingEnabled=" + this.mCallForwardingEnabled + " spnState=" + this.mSpnState + " mCphsInfo=" + this.mCphsInfo + " mCspPlmnEnabled=" + this.mCspPlmnEnabled + " efMWIS=" + this.mEfMWIS + " efCPHS_MWI=" + this.mEfCPHS_MWI + " mEfCff=" + this.mEfCff + " mEfCfis=" + this.mEfCfis + " getOperatorNumeric=" + getOperatorNumeric();
    }

    public SIMRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);
        this.mCphsInfo = null;
        this.mCspPlmnEnabled = true;
        this.mEfMWIS = null;
        this.mEfCPHS_MWI = null;
        this.mEfCff = null;
        this.mEfCfis = null;
        this.mSpdiNetworks = null;
        this.mPnnHomeName = null;
        this.mAdnCache = new AdnRecordCache(this.mFh);
        this.mVmConfig = new VoiceMailConstants();
        this.mSpnOverride = new SpnOverride();
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        resetRecords();
        this.mParentApp.registerForReady(this, 1, null);
        log("SIMRecords X ctor this=" + this);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dispose() {
        log("Disposing SIMRecords this=" + this);
        this.mParentApp.unregisterForReady(this);
        resetRecords();
        super.dispose();
    }

    protected void finalize() {
        log("finalized");
    }

    protected void resetRecords() {
        this.mImsi = null;
        this.mMsisdn = null;
        this.mVoiceMailNum = null;
        this.mMncLength = -1;
        this.mIccId = null;
        this.mSpnDisplayCondition = -1;
        this.mEfMWIS = null;
        this.mEfCPHS_MWI = null;
        this.mSpdiNetworks = null;
        this.mPnnHomeName = null;
        this.mGid1 = null;
        this.mAdnCache.reset();
        log("SIMRecords: onRadioOffOrNotAvailable set 'gsm.sim.operator.numeric' to operator=null");
        setSystemProperty("gsm.sim.operator.numeric", null);
        setSystemProperty("gsm.apn.sim.operator.numeric", null);
        setSystemProperty("gsm.sim.operator.alpha", null);
        setSystemProperty("gsm.sim.operator.iso-country", null);
        this.mRecordsRequested = false;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getIMSI() {
        return this.mImsi;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getMsisdnNumber() {
        return this.mMsisdn;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getGid1() {
        return this.mGid1;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public UsimServiceTable getUsimServiceTable() {
        return this.mUsimServiceTable;
    }

    private int getExtFromEf(int ef) {
        switch (ef) {
            case IccConstants.EF_MSISDN /* 28480 */:
                if (this.mParentApp.getType() == IccCardApplicationStatus.AppType.APPTYPE_USIM) {
                    return IccConstants.EF_EXT5;
                }
                return IccConstants.EF_EXT1;
            default:
                return IccConstants.EF_EXT1;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setMsisdnNumber(String alphaTag, String number, Message onComplete) {
        this.mMsisdn = number;
        this.mMsisdnTag = alphaTag;
        log("Set MSISDN: " + this.mMsisdnTag + " xxxxxxx");
        AdnRecord adn = new AdnRecord(this.mMsisdnTag, this.mMsisdn);
        new AdnRecordLoader(this.mFh).updateEF(adn, IccConstants.EF_MSISDN, getExtFromEf(IccConstants.EF_MSISDN), 1, null, obtainMessage(30, onComplete));
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getMsisdnAlphaTag() {
        return this.mMsisdnTag;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getVoiceMailNumber() {
        return this.mVoiceMailNum;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete) {
        if (this.mIsVoiceMailFixed) {
            AsyncResult.forMessage(onComplete).exception = new IccVmFixedException("Voicemail number is fixed by operator");
            onComplete.sendToTarget();
            return;
        }
        this.mNewVoiceMailNum = voiceNumber;
        this.mNewVoiceMailTag = alphaTag;
        AdnRecord adn = new AdnRecord(this.mNewVoiceMailTag, this.mNewVoiceMailNum);
        if (this.mMailboxIndex != 0 && this.mMailboxIndex != 255) {
            new AdnRecordLoader(this.mFh).updateEF(adn, IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, null, obtainMessage(20, onComplete));
        } else {
            if (isCphsMailboxEnabled()) {
                new AdnRecordLoader(this.mFh).updateEF(adn, IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, null, obtainMessage(25, onComplete));
                return;
            }
            AsyncResult.forMessage(onComplete).exception = new IccVmNotSupportedException("Update SIM voice mailbox error");
            onComplete.sendToTarget();
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getVoiceMailAlphaTag() {
        return this.mVoiceMailTag;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        if (line == 1) {
            try {
                if (this.mEfMWIS != null) {
                    this.mEfMWIS[0] = (byte) ((countWaiting != 0 ? 1 : 0) | (this.mEfMWIS[0] & 254));
                    if (countWaiting < 0) {
                        this.mEfMWIS[1] = 0;
                    } else {
                        this.mEfMWIS[1] = (byte) countWaiting;
                    }
                    this.mFh.updateEFLinearFixed(IccConstants.EF_MWIS, 1, this.mEfMWIS, null, obtainMessage(14, IccConstants.EF_MWIS, 0));
                }
                if (this.mEfCPHS_MWI != null) {
                    this.mEfCPHS_MWI[0] = (byte) ((countWaiting == 0 ? 5 : 10) | (this.mEfCPHS_MWI[0] & 240));
                    this.mFh.updateEFTransparent(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS, this.mEfCPHS_MWI, obtainMessage(14, Integer.valueOf(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS)));
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                logw("Error saving voice mail state to SIM. Probably malformed SIM record", ex);
            }
        }
    }

    private boolean validEfCfis(byte[] data) {
        return data != null && data[0] >= 1 && data[0] <= 4;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getVoiceMessageCount() {
        int countVoiceMessages = -1;
        if (this.mEfMWIS != null) {
            boolean voiceMailWaiting = (this.mEfMWIS[0] & 1) != 0;
            countVoiceMessages = this.mEfMWIS[1] & 255;
            if (voiceMailWaiting && countVoiceMessages == 0) {
                countVoiceMessages = -1;
            }
            log(" VoiceMessageCount from SIM MWIS = " + countVoiceMessages);
        } else if (this.mEfCPHS_MWI != null) {
            int indicator = this.mEfCPHS_MWI[0] & 15;
            if (indicator == 10) {
                countVoiceMessages = -1;
            } else if (indicator == 5) {
                countVoiceMessages = 0;
            }
            log(" VoiceMessageCount from SIM CPHS = " + countVoiceMessages);
        }
        return countVoiceMessages;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public boolean isCallForwardStatusStored() {
        return (this.mEfCfis == null && this.mEfCff == null) ? false : true;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public boolean getVoiceCallForwardingFlag() {
        return this.mCallForwardingEnabled;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceCallForwardingFlag(int line, boolean enable, String dialNumber) {
        if (line == 1) {
            this.mCallForwardingEnabled = enable;
            this.mRecordsEventsRegistrants.notifyResult(0);
            try {
                if (validEfCfis(this.mEfCfis)) {
                    if (enable) {
                        byte[] bArr = this.mEfCfis;
                        bArr[1] = (byte) (bArr[1] | 1);
                    } else {
                        byte[] bArr2 = this.mEfCfis;
                        bArr2[1] = (byte) (bArr2[1] & 254);
                    }
                    log("setVoiceCallForwardingFlag: enable=" + enable + " mEfCfis=" + IccUtils.bytesToHexString(this.mEfCfis));
                    if (enable && !TextUtils.isEmpty(dialNumber)) {
                        log("EF_CFIS: updating cf number, " + dialNumber);
                        byte[] bcdNumber = PhoneNumberUtils.numberToCalledPartyBCD(dialNumber);
                        System.arraycopy(bcdNumber, 0, this.mEfCfis, 3, bcdNumber.length);
                        this.mEfCfis[2] = (byte) bcdNumber.length;
                        this.mEfCfis[14] = -1;
                        this.mEfCfis[15] = -1;
                    }
                    this.mFh.updateEFLinearFixed(IccConstants.EF_CFIS, 1, this.mEfCfis, null, obtainMessage(14, Integer.valueOf(IccConstants.EF_CFIS)));
                } else {
                    log("setVoiceCallForwardingFlag: ignoring enable=" + enable + " invalid mEfCfis=" + IccUtils.bytesToHexString(this.mEfCfis));
                }
                if (this.mEfCff != null) {
                    if (enable) {
                        this.mEfCff[0] = (byte) ((this.mEfCff[0] & 240) | 10);
                    } else {
                        this.mEfCff[0] = (byte) ((this.mEfCff[0] & 240) | 5);
                    }
                    this.mFh.updateEFTransparent(IccConstants.EF_CFF_CPHS, this.mEfCff, obtainMessage(14, Integer.valueOf(IccConstants.EF_CFF_CPHS)));
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                logw("Error saving call forwarding flag to SIM. Probably malformed SIM record", ex);
            }
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            fetchSimRecords();
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getOperatorNumeric() {
        if (this.mImsi == null) {
            log("getOperatorNumeric: IMSI == null");
            return null;
        }
        if (this.mMncLength == -1 || this.mMncLength == 0) {
            log("getSIMOperatorNumeric: bad mncLength");
            return null;
        }
        return this.mImsi.substring(0, this.mMncLength + 3);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public void handleMessage(Message msg) {
        boolean isRecordLoadResponse = false;
        try {
            if (this.mDestroyed.get()) {
                loge("Received message " + msg + "[" + msg.what + "]  while being destroyed. Ignoring.");
                return;
            }
            try {
                switch (msg.what) {
                    case 1:
                        onReady();
                        break;
                    case 2:
                    case 16:
                    case 21:
                    case 23:
                    case 27:
                    case 28:
                    case IccRecords.EVENT_REFRESH_OEM /* 29 */:
                    case 31:
                    default:
                        super.handleMessage(msg);
                        break;
                    case 3:
                        isRecordLoadResponse = true;
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception != null) {
                            loge("Exception querying IMSI, Exception:" + ar.exception);
                        } else {
                            this.mImsi = (String) ar.result;
                            if (this.mImsi != null && (this.mImsi.length() < 6 || this.mImsi.length() > 15)) {
                                loge("invalid IMSI " + this.mImsi);
                                this.mImsi = null;
                            }
                            log("IMSI: xxxxxxx");
                            if ((this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                String mccmncCode = this.mImsi.substring(0, 6);
                                String[] arr$ = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                for (String mccmnc : arr$) {
                                    if (mccmnc.equals(mccmncCode)) {
                                        this.mMncLength = 3;
                                    }
                                }
                            }
                            if (this.mMncLength == 0) {
                                try {
                                    int mcc = Integer.parseInt(this.mImsi.substring(0, 3));
                                    this.mMncLength = MccTable.smallestDigitsMccForMnc(mcc);
                                } catch (NumberFormatException e) {
                                    this.mMncLength = 0;
                                    loge("Corrupt IMSI!");
                                }
                            }
                            if (this.mMncLength != 0 && this.mMncLength != -1) {
                                MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                            }
                            this.mImsiReadyRegistrants.notifyRegistrants();
                        }
                        break;
                    case 4:
                        isRecordLoadResponse = true;
                        AsyncResult ar2 = (AsyncResult) msg.obj;
                        byte[] data = (byte[]) ar2.result;
                        if (ar2.exception == null) {
                            this.mIccId = IccUtils.bcdToString(data, 0, data.length);
                            log("iccid: " + this.mIccId);
                        }
                        break;
                    case 5:
                        isRecordLoadResponse = true;
                        AsyncResult ar3 = (AsyncResult) msg.obj;
                        byte[] data2 = (byte[]) ar3.result;
                        boolean isValidMbdn = false;
                        if (ar3.exception == null) {
                            log("EF_MBI: " + IccUtils.bytesToHexString(data2));
                            this.mMailboxIndex = data2[0] & 255;
                            if (this.mMailboxIndex != 0 && this.mMailboxIndex != 255) {
                                log("Got valid mailbox number for MBDN");
                                isValidMbdn = true;
                            }
                        }
                        this.mRecordsToLoad++;
                        if (isValidMbdn) {
                            new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, obtainMessage(6));
                        } else {
                            new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                        }
                        break;
                    case 6:
                    case 11:
                        this.mVoiceMailNum = null;
                        this.mVoiceMailTag = null;
                        isRecordLoadResponse = true;
                        AsyncResult ar4 = (AsyncResult) msg.obj;
                        if (ar4.exception != null) {
                            log("Invalid or missing EF" + (msg.what == 11 ? "[MAILBOX]" : "[MBDN]"));
                            if (msg.what == 6) {
                                this.mRecordsToLoad++;
                                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                            }
                        } else {
                            AdnRecord adn = (AdnRecord) ar4.result;
                            log("VM: " + adn + (msg.what == 11 ? " EF[MAILBOX]" : " EF[MBDN]"));
                            if (adn.isEmpty() && msg.what == 6) {
                                this.mRecordsToLoad++;
                                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                            } else {
                                this.mVoiceMailNum = adn.getNumber();
                                this.mVoiceMailTag = adn.getAlphaTag();
                            }
                        }
                        break;
                    case 7:
                        isRecordLoadResponse = true;
                        AsyncResult ar5 = (AsyncResult) msg.obj;
                        byte[] data3 = (byte[]) ar5.result;
                        log("EF_MWIS : " + IccUtils.bytesToHexString(data3));
                        if (ar5.exception != null) {
                            log("EVENT_GET_MWIS_DONE exception = " + ar5.exception);
                        } else if ((data3[0] & 255) == 255) {
                            log("SIMRecords: Uninitialized record MWIS");
                        } else {
                            this.mEfMWIS = data3;
                        }
                        break;
                    case 8:
                        isRecordLoadResponse = true;
                        AsyncResult ar6 = (AsyncResult) msg.obj;
                        byte[] data4 = (byte[]) ar6.result;
                        log("EF_CPHS_MWI: " + IccUtils.bytesToHexString(data4));
                        if (ar6.exception != null) {
                            log("EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE exception = " + ar6.exception);
                        } else {
                            this.mEfCPHS_MWI = data4;
                        }
                        break;
                    case 9:
                        isRecordLoadResponse = true;
                        try {
                            AsyncResult ar7 = (AsyncResult) msg.obj;
                            byte[] data5 = (byte[]) ar7.result;
                            if (ar7.exception != null) {
                                if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                    String mccmncCode2 = this.mImsi.substring(0, 6);
                                    String[] arr$2 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                    for (String mccmnc2 : arr$2) {
                                        if (mccmnc2.equals(mccmncCode2)) {
                                            this.mMncLength = 3;
                                        }
                                    }
                                }
                                if (this.mMncLength == 0 || this.mMncLength == -1) {
                                    if (this.mImsi != null) {
                                        try {
                                            int mcc2 = Integer.parseInt(this.mImsi.substring(0, 3));
                                            this.mMncLength = MccTable.smallestDigitsMccForMnc(mcc2);
                                        } catch (NumberFormatException e2) {
                                            this.mMncLength = 0;
                                            loge("Corrupt IMSI!");
                                        }
                                    } else {
                                        this.mMncLength = 0;
                                        log("MNC length not present in EF_AD");
                                    }
                                }
                                if (this.mImsi != null && this.mMncLength != 0) {
                                    MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                                }
                            } else {
                                log("EF_AD: " + IccUtils.bytesToHexString(data5));
                                if (data5.length < 3) {
                                    log("Corrupt AD data on SIM");
                                    if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                        String mccmncCode3 = this.mImsi.substring(0, 6);
                                        String[] arr$3 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                        for (String mccmnc3 : arr$3) {
                                            if (mccmnc3.equals(mccmncCode3)) {
                                                this.mMncLength = 3;
                                            }
                                        }
                                    }
                                    if (this.mMncLength == 0 || this.mMncLength == -1) {
                                        if (this.mImsi != null) {
                                            try {
                                                int mcc3 = Integer.parseInt(this.mImsi.substring(0, 3));
                                                this.mMncLength = MccTable.smallestDigitsMccForMnc(mcc3);
                                            } catch (NumberFormatException e3) {
                                                this.mMncLength = 0;
                                                loge("Corrupt IMSI!");
                                            }
                                        } else {
                                            this.mMncLength = 0;
                                            log("MNC length not present in EF_AD");
                                        }
                                    }
                                    if (this.mImsi != null && this.mMncLength != 0) {
                                        MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                                    }
                                } else if (data5.length == 3) {
                                    log("MNC length not present in EF_AD");
                                    if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                        String mccmncCode4 = this.mImsi.substring(0, 6);
                                        String[] arr$4 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                        for (String mccmnc4 : arr$4) {
                                            if (mccmnc4.equals(mccmncCode4)) {
                                                this.mMncLength = 3;
                                            }
                                        }
                                    }
                                    if (this.mMncLength == 0 || this.mMncLength == -1) {
                                        if (this.mImsi != null) {
                                            try {
                                                int mcc4 = Integer.parseInt(this.mImsi.substring(0, 3));
                                                this.mMncLength = MccTable.smallestDigitsMccForMnc(mcc4);
                                            } catch (NumberFormatException e4) {
                                                this.mMncLength = 0;
                                                loge("Corrupt IMSI!");
                                            }
                                        } else {
                                            this.mMncLength = 0;
                                            log("MNC length not present in EF_AD");
                                        }
                                    }
                                    if (this.mImsi != null && this.mMncLength != 0) {
                                        MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                                    }
                                } else {
                                    this.mMncLength = data5[3] & 15;
                                    if (this.mMncLength == 15) {
                                        this.mMncLength = 0;
                                    }
                                    if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                        String mccmncCode5 = this.mImsi.substring(0, 6);
                                        String[] arr$5 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                        for (String mccmnc5 : arr$5) {
                                            if (mccmnc5.equals(mccmncCode5)) {
                                                this.mMncLength = 3;
                                            }
                                        }
                                    }
                                    if (this.mMncLength == 0 || this.mMncLength == -1) {
                                        if (this.mImsi != null) {
                                            try {
                                                int mcc5 = Integer.parseInt(this.mImsi.substring(0, 3));
                                                this.mMncLength = MccTable.smallestDigitsMccForMnc(mcc5);
                                            } catch (NumberFormatException e5) {
                                                this.mMncLength = 0;
                                                loge("Corrupt IMSI!");
                                            }
                                        } else {
                                            this.mMncLength = 0;
                                            log("MNC length not present in EF_AD");
                                        }
                                    }
                                    if (this.mImsi != null && this.mMncLength != 0) {
                                        MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                                    }
                                }
                            }
                        } catch (Throwable th) {
                            if ((this.mMncLength == -1 || this.mMncLength == 0 || this.mMncLength == 2) && this.mImsi != null && this.mImsi.length() >= 6) {
                                String mccmncCode6 = this.mImsi.substring(0, 6);
                                String[] arr$6 = MCCMNC_CODES_HAVING_3DIGITS_MNC;
                                for (String mccmnc6 : arr$6) {
                                    if (mccmnc6.equals(mccmncCode6)) {
                                        this.mMncLength = 3;
                                    }
                                }
                            }
                            if (this.mMncLength == 0 || this.mMncLength == -1) {
                                if (this.mImsi != null) {
                                    try {
                                        int mcc6 = Integer.parseInt(this.mImsi.substring(0, 3));
                                        this.mMncLength = MccTable.smallestDigitsMccForMnc(mcc6);
                                    } catch (NumberFormatException e6) {
                                        this.mMncLength = 0;
                                        loge("Corrupt IMSI!");
                                    }
                                } else {
                                    this.mMncLength = 0;
                                    log("MNC length not present in EF_AD");
                                }
                                break;
                            }
                            if (this.mImsi == null || this.mMncLength == 0) {
                                throw th;
                            }
                            MccTable.updateMccMncConfiguration(this.mContext, this.mImsi.substring(0, this.mMncLength + 3), false);
                            throw th;
                        }
                        break;
                    case 10:
                        isRecordLoadResponse = true;
                        AsyncResult ar8 = (AsyncResult) msg.obj;
                        if (ar8.exception != null) {
                            log("Invalid or missing EF[MSISDN]");
                        } else {
                            AdnRecord adn2 = (AdnRecord) ar8.result;
                            this.mMsisdn = adn2.getNumber();
                            this.mMsisdnTag = adn2.getAlphaTag();
                            log("MSISDN: xxxxxxx");
                        }
                        break;
                    case 12:
                        isRecordLoadResponse = true;
                        getSpnFsm(false, (AsyncResult) msg.obj);
                        break;
                    case 13:
                        isRecordLoadResponse = true;
                        AsyncResult ar9 = (AsyncResult) msg.obj;
                        byte[] data6 = (byte[]) ar9.result;
                        if (ar9.exception == null) {
                            parseEfSpdi(data6);
                        }
                        break;
                    case 14:
                        AsyncResult ar10 = (AsyncResult) msg.obj;
                        if (ar10.exception != null) {
                            logw("update failed. ", ar10.exception);
                        }
                        break;
                    case 15:
                        isRecordLoadResponse = true;
                        AsyncResult ar11 = (AsyncResult) msg.obj;
                        byte[] data7 = (byte[]) ar11.result;
                        if (ar11.exception == null) {
                            SimTlv tlv = new SimTlv(data7, 0, data7.length);
                            while (tlv.isValidObject()) {
                                if (tlv.getTag() == TAG_FULL_NETWORK_NAME) {
                                    this.mPnnHomeName = IccUtils.networkNameToString(tlv.getData(), 0, tlv.getData().length);
                                } else {
                                    tlv.nextObject();
                                }
                            }
                        }
                        break;
                    case 17:
                        isRecordLoadResponse = true;
                        AsyncResult ar12 = (AsyncResult) msg.obj;
                        byte[] data8 = (byte[]) ar12.result;
                        if (ar12.exception == null) {
                            this.mUsimServiceTable = new UsimServiceTable(data8);
                            log("SST: " + this.mUsimServiceTable);
                        }
                        break;
                    case 18:
                        isRecordLoadResponse = true;
                        AsyncResult ar13 = (AsyncResult) msg.obj;
                        if (ar13.exception == null) {
                            handleSmses((ArrayList) ar13.result);
                        }
                        break;
                    case 19:
                        Rlog.i("ENF", "marked read: sms " + msg.arg1);
                        break;
                    case 20:
                        isRecordLoadResponse = false;
                        AsyncResult ar14 = (AsyncResult) msg.obj;
                        if (ar14.exception == null) {
                            this.mVoiceMailNum = this.mNewVoiceMailNum;
                            this.mVoiceMailTag = this.mNewVoiceMailTag;
                        }
                        if (isCphsMailboxEnabled()) {
                            AdnRecord adn3 = new AdnRecord(this.mVoiceMailTag, this.mVoiceMailNum);
                            Message onCphsCompleted = (Message) ar14.userObj;
                            if (ar14.exception == null && ar14.userObj != null) {
                                AsyncResult.forMessage((Message) ar14.userObj).exception = null;
                                ((Message) ar14.userObj).sendToTarget();
                                log("Callback with MBDN successful.");
                                onCphsCompleted = null;
                            }
                            new AdnRecordLoader(this.mFh).updateEF(adn3, IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, null, obtainMessage(25, onCphsCompleted));
                        } else if (ar14.userObj != null) {
                            AsyncResult.forMessage((Message) ar14.userObj).exception = ar14.exception;
                            ((Message) ar14.userObj).sendToTarget();
                        }
                        break;
                    case 22:
                        isRecordLoadResponse = false;
                        AsyncResult ar15 = (AsyncResult) msg.obj;
                        if (ar15.exception == null) {
                            handleSms((byte[]) ar15.result);
                        } else {
                            loge("Error on GET_SMS with exp " + ar15.exception);
                        }
                        break;
                    case 24:
                        isRecordLoadResponse = true;
                        AsyncResult ar16 = (AsyncResult) msg.obj;
                        byte[] data9 = (byte[]) ar16.result;
                        if (ar16.exception == null) {
                            log("EF_CFF_CPHS: " + IccUtils.bytesToHexString(data9));
                            this.mEfCff = data9;
                            if (validEfCfis(this.mEfCfis)) {
                                log("EVENT_GET_CFF_DONE: EF_CFIS is valid, ignoring EF_CFF_CPHS");
                            } else {
                                this.mCallForwardingEnabled = (data9[0] & 15) == 10;
                                this.mRecordsEventsRegistrants.notifyResult(0);
                            }
                        }
                        break;
                    case 25:
                        isRecordLoadResponse = false;
                        AsyncResult ar17 = (AsyncResult) msg.obj;
                        if (ar17.exception == null) {
                            this.mVoiceMailNum = this.mNewVoiceMailNum;
                            this.mVoiceMailTag = this.mNewVoiceMailTag;
                        } else {
                            log("Set CPHS MailBox with exception: " + ar17.exception);
                        }
                        if (ar17.userObj != null) {
                            log("Callback with CPHS MB successful.");
                            AsyncResult.forMessage((Message) ar17.userObj).exception = ar17.exception;
                            ((Message) ar17.userObj).sendToTarget();
                        }
                        break;
                    case 26:
                        isRecordLoadResponse = true;
                        AsyncResult ar18 = (AsyncResult) msg.obj;
                        if (ar18.exception == null) {
                            this.mCphsInfo = (byte[]) ar18.result;
                            log("iCPHS: " + IccUtils.bytesToHexString(this.mCphsInfo));
                        }
                        break;
                    case CallFailCause.STATUS_ENQUIRY /* 30 */:
                        isRecordLoadResponse = false;
                        AsyncResult ar19 = (AsyncResult) msg.obj;
                        if (ar19.userObj != null) {
                            AsyncResult.forMessage((Message) ar19.userObj).exception = ar19.exception;
                            ((Message) ar19.userObj).sendToTarget();
                        }
                        break;
                    case 32:
                        isRecordLoadResponse = true;
                        AsyncResult ar20 = (AsyncResult) msg.obj;
                        byte[] data10 = (byte[]) ar20.result;
                        if (ar20.exception == null) {
                            log("EF_CFIS: " + IccUtils.bytesToHexString(data10));
                            if (validEfCfis(data10)) {
                                this.mEfCfis = data10;
                                this.mCallForwardingEnabled = (data10[1] & 1) != 0;
                                log("EF_CFIS: callForwardingEnabled=" + this.mCallForwardingEnabled);
                                this.mRecordsEventsRegistrants.notifyResult(0);
                            } else {
                                log("EF_CFIS: invalid data=" + IccUtils.bytesToHexString(data10));
                            }
                        }
                        break;
                    case 33:
                        isRecordLoadResponse = true;
                        AsyncResult ar21 = (AsyncResult) msg.obj;
                        if (ar21.exception != null) {
                            loge("Exception in fetching EF_CSP data " + ar21.exception);
                        } else {
                            byte[] data11 = (byte[]) ar21.result;
                            log("EF_CSP: " + IccUtils.bytesToHexString(data11));
                            handleEfCspData(data11);
                        }
                        break;
                    case 34:
                        isRecordLoadResponse = true;
                        AsyncResult ar22 = (AsyncResult) msg.obj;
                        byte[] data12 = (byte[]) ar22.result;
                        if (ar22.exception != null) {
                            loge("Exception in get GID1 " + ar22.exception);
                            this.mGid1 = null;
                        } else {
                            this.mGid1 = IccUtils.bytesToHexString(data12);
                            log("GID1: " + this.mGid1);
                        }
                        break;
                }
                if (!isRecordLoadResponse) {
                    return;
                }
            } catch (RuntimeException exc) {
                logw("Exception parsing SIM record", exc);
                if (0 == 0) {
                    return;
                }
            }
            onRecordLoaded();
        } catch (Throwable th2) {
            if (0 != 0) {
                onRecordLoaded();
            }
            throw th2;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void handleFileUpdate(int efid) {
        switch (efid) {
            case IccConstants.EF_CFF_CPHS /* 28435 */:
                this.mRecordsToLoad++;
                log("SIM Refresh called for EF_CFF_CPHS");
                this.mFh.loadEFTransparent(IccConstants.EF_CFF_CPHS, obtainMessage(24));
                break;
            case IccConstants.EF_CSP_CPHS /* 28437 */:
                this.mRecordsToLoad++;
                log("[CSP] SIM Refresh for EF_CSP_CPHS");
                this.mFh.loadEFTransparent(IccConstants.EF_CSP_CPHS, obtainMessage(33));
                break;
            case IccConstants.EF_MAILBOX_CPHS /* 28439 */:
                this.mRecordsToLoad++;
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MAILBOX_CPHS, IccConstants.EF_EXT1, 1, obtainMessage(11));
                break;
            case 28474:
                log("SIM Refresh for EF_ADN");
                this.mAdnCache.reset();
                break;
            case IccConstants.EF_MSISDN /* 28480 */:
                this.mRecordsToLoad++;
                log("SIM Refresh called for EF_MSISDN");
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MSISDN, getExtFromEf(IccConstants.EF_MSISDN), 1, obtainMessage(10));
                break;
            case IccConstants.EF_MBDN /* 28615 */:
                this.mRecordsToLoad++;
                new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MBDN, IccConstants.EF_EXT6, this.mMailboxIndex, obtainMessage(6));
                break;
            case IccConstants.EF_CFIS /* 28619 */:
                this.mRecordsToLoad++;
                log("SIM Refresh called for EF_CFIS");
                this.mFh.loadEFLinearFixed(IccConstants.EF_CFIS, 1, obtainMessage(32));
                break;
            default:
                this.mAdnCache.reset();
                fetchSimRecords();
                break;
        }
    }

    private void handleSimRefresh(IccRefreshResponse refreshResponse) {
        if (refreshResponse == null) {
            log("handleSimRefresh received without input");
        }
        if (refreshResponse.aid == null || refreshResponse.aid.equals(this.mParentApp.getAid())) {
            switch (refreshResponse.refreshResult) {
                case 0:
                    log("handleSimRefresh with SIM_FILE_UPDATED");
                    handleFileUpdate(refreshResponse.efId);
                    break;
                case 1:
                    log("handleSimRefresh with SIM_REFRESH_INIT");
                    onIccRefreshInit();
                    break;
                case 2:
                    log("handleSimRefresh with SIM_REFRESH_RESET");
                    this.mCi.setRadioPower(false, null);
                    this.mAdnCache.reset();
                    break;
                default:
                    log("handleSimRefresh with unknown operation");
                    break;
            }
        }
    }

    private int dispatchGsmMessage(SmsMessage message) {
        this.mNewSmsRegistrants.notifyResult(message);
        return 0;
    }

    public void handleSmsOnIcc(AsyncResult ar) {
        int[] index = (int[]) ar.result;
        if (ar.exception != null || index.length != 1) {
            loge(" Error on SMS_ON_SIM with exp " + ar.exception + " length " + index.length);
        } else {
            log("READ EF_SMS RECORD index= " + index[0]);
            this.mFh.loadEFLinearFixed(IccConstants.EF_SMS, index[0], obtainMessage(22));
        }
    }

    private void handleSms(byte[] ba) {
        if (ba[0] != 0) {
            Rlog.d("ENF", "status : " + ((int) ba[0]));
        }
        if (ba[0] == 3) {
            int n = ba.length;
            byte[] pdu = new byte[n - 1];
            System.arraycopy(ba, 1, pdu, 0, n - 1);
            SmsMessage message = SmsMessage.createFromPdu(pdu, SmsMessage.FORMAT_3GPP);
            dispatchGsmMessage(message);
        }
    }

    private void handleSmses(ArrayList<byte[]> messages) {
        int count = messages.size();
        for (int i = 0; i < count; i++) {
            byte[] ba = messages.get(i);
            if (ba[0] != 0) {
                Rlog.i("ENF", "status " + i + ": " + ((int) ba[0]));
            }
            if (ba[0] == 3) {
                int n = ba.length;
                byte[] pdu = new byte[n - 1];
                System.arraycopy(ba, 1, pdu, 0, n - 1);
                SmsMessage message = SmsMessage.createFromPdu(pdu, SmsMessage.FORMAT_3GPP);
                dispatchGsmMessage(message);
                ba[0] = 1;
            }
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void onRecordLoaded() {
        this.mRecordsToLoad--;
        log("onRecordLoaded " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
        if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
            onAllRecordsLoaded();
        } else if (this.mRecordsToLoad < 0) {
            loge("recordsToLoad <0, programmer error suspected");
            this.mRecordsToLoad = 0;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void onAllRecordsLoaded() {
        log("record load complete");
        String operator = getOperatorNumeric();
        if (!TextUtils.isEmpty(operator)) {
            log("onAllRecordsLoaded set 'gsm.sim.operator.numeric' to operator='" + operator + "'");
            setSystemProperty("gsm.sim.operator.numeric", operator);
            setSystemProperty("gsm.apn.sim.operator.numeric", operator);
        } else {
            log("onAllRecordsLoaded empty 'gsm.sim.operator.numeric' skipping");
        }
        if (!TextUtils.isEmpty(this.mImsi)) {
            log("onAllRecordsLoaded set mcc imsi=" + this.mImsi);
            setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(this.mImsi.substring(0, 3))));
        } else {
            log("onAllRecordsLoaded empty imsi skipping setting mcc");
        }
        setVoiceMailByCountry(operator);
        setSpnFromConfig(operator);
        this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
    }

    private void setSpnFromConfig(String carrier) {
        if (this.mSpnOverride.containsCarrier(carrier)) {
            this.mSpn = this.mSpnOverride.getSpn(carrier);
        }
    }

    private void setVoiceMailByCountry(String spn) {
        if (this.mVmConfig.containsCarrier(spn)) {
            this.mIsVoiceMailFixed = true;
            this.mVoiceMailNum = this.mVmConfig.getVoiceMailNumber(spn);
            this.mVoiceMailTag = this.mVmConfig.getVoiceMailTag(spn);
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onReady() {
        fetchSimRecords();
    }

    protected void fetchSimRecords() {
        this.mRecordsRequested = true;
        log("fetchSimRecords " + this.mRecordsToLoad);
        this.mCi.getIMSIForApp(this.mParentApp.getAid(), obtainMessage(3));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(4));
        this.mRecordsToLoad++;
        new AdnRecordLoader(this.mFh).loadFromEF(IccConstants.EF_MSISDN, getExtFromEf(IccConstants.EF_MSISDN), 1, obtainMessage(10));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_MBI, 1, obtainMessage(5));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_AD, obtainMessage(9));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_MWIS, 1, obtainMessage(7));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_VOICE_MAIL_INDICATOR_CPHS, obtainMessage(8));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_CFIS, 1, obtainMessage(32));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CFF_CPHS, obtainMessage(24));
        this.mRecordsToLoad++;
        getSpnFsm(true, null);
        this.mFh.loadEFTransparent(IccConstants.EF_SPDI, obtainMessage(13));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_PNN, 1, obtainMessage(15));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_SST, obtainMessage(17));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_INFO_CPHS, obtainMessage(26));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CSP_CPHS, obtainMessage(33));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_GID1, obtainMessage(34));
        this.mRecordsToLoad++;
        this.mFh.getEFLinearRecordSize(IccConstants.EF_SMS, obtainMessage(35));
        log("fetchSimRecords " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getDisplayRule(String plmn) {
        if (TextUtils.isEmpty(this.mSpn) || this.mSpnDisplayCondition == -1) {
            return 2;
        }
        if (isOnMatchingPlmn(plmn)) {
            if ((this.mSpnDisplayCondition & 1) != 1) {
                return 1;
            }
            int rule = 1 | 2;
            return rule;
        }
        if ((this.mSpnDisplayCondition & 2) != 0) {
            return 2;
        }
        int rule2 = 2 | 1;
        return rule2;
    }

    private boolean isOnMatchingPlmn(String plmn) {
        if (plmn == null) {
            return false;
        }
        if (plmn.equals(getOperatorNumeric())) {
            return true;
        }
        if (this.mSpdiNetworks == null) {
            return false;
        }
        for (String spdiNet : this.mSpdiNetworks) {
            if (plmn.equals(spdiNet)) {
                return true;
            }
        }
        return false;
    }

    private void getSpnFsm(boolean start, AsyncResult ar) {
        if (start) {
            if (this.mSpnState == GetSpnFsmState.READ_SPN_3GPP || this.mSpnState == GetSpnFsmState.READ_SPN_CPHS || this.mSpnState == GetSpnFsmState.READ_SPN_SHORT_CPHS || this.mSpnState == GetSpnFsmState.INIT) {
                this.mSpnState = GetSpnFsmState.INIT;
            }
            this.mSpnState = GetSpnFsmState.INIT;
        }
        switch (this.mSpnState) {
            case INIT:
                this.mSpn = null;
                this.mFh.loadEFTransparent(IccConstants.EF_SPN, obtainMessage(12));
                this.mRecordsToLoad++;
                this.mSpnState = GetSpnFsmState.READ_SPN_3GPP;
                break;
            case READ_SPN_3GPP:
                if (ar != null && ar.exception == null) {
                    byte[] data = (byte[]) ar.result;
                    this.mSpnDisplayCondition = data[0] & 255;
                    this.mSpn = IccUtils.adnStringFieldToString(data, 1, data.length - 1);
                    log("Load EF_SPN: " + this.mSpn + " spnDisplayCondition: " + this.mSpnDisplayCondition);
                    setSystemProperty("gsm.sim.operator.alpha", this.mSpn);
                    this.mRecordsEventsRegistrants.notifyResult(1);
                    this.mSpnState = GetSpnFsmState.IDLE;
                } else {
                    this.mFh.loadEFTransparent(IccConstants.EF_SPN_CPHS, obtainMessage(12));
                    this.mRecordsToLoad++;
                    this.mSpnState = GetSpnFsmState.READ_SPN_CPHS;
                    this.mSpnDisplayCondition = -1;
                }
                break;
            case READ_SPN_CPHS:
                if (ar != null && ar.exception == null) {
                    byte[] data2 = (byte[]) ar.result;
                    this.mSpn = IccUtils.adnStringFieldToString(data2, 0, data2.length);
                    log("Load EF_SPN_CPHS: " + this.mSpn);
                    setSystemProperty("gsm.sim.operator.alpha", this.mSpn);
                    this.mRecordsEventsRegistrants.notifyResult(1);
                    this.mSpnState = GetSpnFsmState.IDLE;
                } else {
                    this.mFh.loadEFTransparent(IccConstants.EF_SPN_SHORT_CPHS, obtainMessage(12));
                    this.mRecordsToLoad++;
                    this.mSpnState = GetSpnFsmState.READ_SPN_SHORT_CPHS;
                }
                break;
            case READ_SPN_SHORT_CPHS:
                if (ar != null && ar.exception == null) {
                    byte[] data3 = (byte[]) ar.result;
                    this.mSpn = IccUtils.adnStringFieldToString(data3, 0, data3.length);
                    log("Load EF_SPN_SHORT_CPHS: " + this.mSpn);
                    setSystemProperty("gsm.sim.operator.alpha", this.mSpn);
                    this.mRecordsEventsRegistrants.notifyResult(1);
                } else {
                    log("No SPN loaded in either CHPS or 3GPP");
                }
                this.mSpnState = GetSpnFsmState.IDLE;
                break;
            default:
                this.mSpnState = GetSpnFsmState.IDLE;
                break;
        }
    }

    private void parseEfSpdi(byte[] data) {
        SimTlv tlv = new SimTlv(data, 0, data.length);
        byte[] plmnEntries = null;
        while (tlv.isValidObject()) {
            if (tlv.getTag() == TAG_SPDI) {
                tlv = new SimTlv(tlv.getData(), 0, tlv.getData().length);
            }
            if (tlv.getTag() != 128) {
                tlv.nextObject();
            } else {
                plmnEntries = tlv.getData();
                break;
            }
        }
        if (plmnEntries != null) {
            this.mSpdiNetworks = new ArrayList<>(plmnEntries.length / 3);
            for (int i = 0; i + 2 < plmnEntries.length; i += 3) {
                String plmnCode = IccUtils.bcdToString(plmnEntries, i, 3);
                if (plmnCode.length() >= 5) {
                    log("EF_SPDI network: " + plmnCode);
                    this.mSpdiNetworks.add(plmnCode);
                }
            }
        }
    }

    private boolean isCphsMailboxEnabled() {
        if (this.mCphsInfo == null) {
            return false;
        }
        return (this.mCphsInfo[1] & 48) == 48;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[SIMRecords] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[SIMRecords] " + s);
    }

    protected void logw(String s, Throwable tr) {
        Rlog.w(LOG_TAG, "[SIMRecords] " + s, tr);
    }

    protected void logv(String s) {
        Rlog.v(LOG_TAG, "[SIMRecords] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public boolean isCspPlmnEnabled() {
        return this.mCspPlmnEnabled;
    }

    private void handleEfCspData(byte[] data) {
        int usedCspGroups = data.length / 2;
        this.mCspPlmnEnabled = true;
        for (int i = 0; i < usedCspGroups; i++) {
            if (data[i * 2] == -64) {
                log("[CSP] found ValueAddedServicesGroup, value " + ((int) data[(i * 2) + 1]));
                if ((data[(i * 2) + 1] & 128) == 128) {
                    this.mCspPlmnEnabled = true;
                    return;
                }
                this.mCspPlmnEnabled = false;
                log("[CSP] Set Automatic Network Selection");
                this.mNetworkSelectionModeAutomaticRegistrants.notifyRegistrants();
                return;
            }
        }
        log("[CSP] Value Added Service Group (0xC0), not found!");
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("SIMRecords: " + this);
        pw.println(" extends:");
        super.dump(fd, pw, args);
        pw.println(" mVmConfig=" + this.mVmConfig);
        pw.println(" mSpnOverride=" + this.mSpnOverride);
        pw.println(" mCallForwardingEnabled=" + this.mCallForwardingEnabled);
        pw.println(" mSpnState=" + this.mSpnState);
        pw.println(" mCphsInfo=" + this.mCphsInfo);
        pw.println(" mCspPlmnEnabled=" + this.mCspPlmnEnabled);
        pw.println(" mEfMWIS[]=" + Arrays.toString(this.mEfMWIS));
        pw.println(" mEfCPHS_MWI[]=" + Arrays.toString(this.mEfCPHS_MWI));
        pw.println(" mEfCff[]=" + Arrays.toString(this.mEfCff));
        pw.println(" mEfCfis[]=" + Arrays.toString(this.mEfCfis));
        pw.println(" mSpnDisplayCondition=" + this.mSpnDisplayCondition);
        pw.println(" mSpdiNetworks[]=" + this.mSpdiNetworks);
        pw.println(" mPnnHomeName=" + this.mPnnHomeName);
        pw.println(" mUsimServiceTable=" + this.mUsimServiceTable);
        pw.println(" mGid1=" + this.mGid1);
        pw.flush();
    }

    private void setSystemProperty(String key, String val) {
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            SystemProperties.set(key, val);
        }
    }
}
