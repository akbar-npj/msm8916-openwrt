package com.android.internal.telephony.uicc;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.MSimTelephonyManager;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.MccTable;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/* JADX INFO: loaded from: classes.dex */
public final class RuimRecords extends IccRecords {
    private static final int CSIM_IMSI_MNC_LENGTH = 2;
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_GET_CDMA_SUBSCRIPTION_DONE = 10;
    private static final int EVENT_GET_DEVICE_IDENTITY_DONE = 4;
    private static final int EVENT_GET_ICCID_DONE = 5;
    private static final int EVENT_GET_SMS_DONE = 22;
    private static final int EVENT_GET_SST_DONE = 17;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;
    private static final int EVENT_SMS_ON_RUIM = 21;
    private static final int EVENT_UPDATE_DONE = 14;
    static final String LOG_TAG = "RuimRecords";
    boolean mCsimSpnDisplayCondition;
    private byte[] mEFli;
    private byte[] mEFpl;
    private String mHomeNetworkId;
    private String mHomeSystemId;
    private String mMdn;
    private String mMin;
    private String mMin2Min1;
    private String mMyMobileNumber;
    private boolean mOtaCommited;
    private String mPrlVersion;
    private boolean mRecordsRequired;

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public String toString() {
        return "RuimRecords: " + super.toString() + " m_ota_commited" + this.mOtaCommited + " mMyMobileNumber=xxxx mMin2Min1=" + this.mMin2Min1 + " mPrlVersion=" + this.mPrlVersion + " mEFpl=" + this.mEFpl + " mEFli=" + this.mEFli + " mCsimSpnDisplayCondition=" + this.mCsimSpnDisplayCondition + " mMdn=" + this.mMdn + " mMin=" + this.mMin + " mHomeSystemId=" + this.mHomeSystemId + " mHomeNetworkId=" + this.mHomeNetworkId;
    }

    public RuimRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);
        this.mOtaCommited = false;
        this.mRecordsRequired = false;
        this.mEFpl = null;
        this.mEFli = null;
        this.mCsimSpnDisplayCondition = false;
        this.mAdnCache = new AdnRecordCache(this.mFh);
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        resetRecords();
        this.mParentApp.registerForReady(this, 1, null);
        log("RuimRecords X ctor this=" + this);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dispose() {
        log("Disposing RuimRecords " + this);
        this.mParentApp.unregisterForReady(this);
        resetRecords();
        super.dispose();
    }

    protected void finalize() {
        log("RuimRecords finalized");
    }

    protected void resetRecords() {
        this.mMncLength = -1;
        this.mIccId = null;
        this.mAdnCache.reset();
        setSystemProperty("gsm.apn.ruim.operator.numeric", "");
        this.mRecordsRequested = false;
    }

    public String getMdnNumber() {
        return this.mMyMobileNumber;
    }

    public String getCdmaMin() {
        return this.mMin2Min1;
    }

    public String getPrlVersion() {
        return this.mPrlVersion;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete) {
        AsyncResult.forMessage(onComplete).exception = new IccException("setVoiceMailNumber not implemented");
        onComplete.sendToTarget();
        loge("method setVoiceMailNumber is not implemented");
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            fetchRuimRecords();
        }
    }

    private int decodeImsiDigits(int digits, int length) {
        int constant = 0;
        for (int i = 0; i < length; i++) {
            constant = (constant * 10) + 1;
        }
        int digits2 = digits + constant;
        int denominator = 1;
        for (int i2 = 0; i2 < length; i2++) {
            if ((digits2 / denominator) % 10 == 0) {
                digits2 -= denominator * 10;
            }
            denominator *= 10;
        }
        return digits2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String decodeImsi(byte[] data) {
        int mcc_data = ((data[9] & 3) << 8) | (data[8] & 255);
        int mcc = decodeImsiDigits(mcc_data, 3);
        int digits_11_12_data = data[6] & 127;
        int digits_11_12 = decodeImsiDigits(digits_11_12_data, 2);
        int first3digits = ((data[2] & 3) << 8) + (data[1] & 255);
        int second3digits = (((data[5] & 255) << 8) | (data[4] & 255)) >> 6;
        int digit7 = (data[4] >> 2) & 15;
        if (digit7 > 9) {
            digit7 = 0;
        }
        int last3digits = ((data[4] & 3) << 8) | (data[3] & 255);
        return String.format(Locale.US, "%03d", Integer.valueOf(mcc)) + String.format(Locale.US, "%02d", Integer.valueOf(digits_11_12)) + String.format(Locale.US, "%03d", Integer.valueOf(decodeImsiDigits(first3digits, 3))) + String.format(Locale.US, "%03d", Integer.valueOf(decodeImsiDigits(second3digits, 3))) + String.format(Locale.US, "%d", Integer.valueOf(digit7)) + String.format(Locale.US, "%03d", Integer.valueOf(decodeImsiDigits(last3digits, 3)));
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public String getOperatorNumeric() {
        if (this.mImsi == null) {
            return null;
        }
        if (this.mMncLength != -1 && this.mMncLength != 0) {
            return this.mImsi.substring(0, this.mMncLength + 3);
        }
        Integer.parseInt(this.mImsi.substring(0, 3));
        return this.mImsi.substring(0, 5);
    }

    private class EfPlLoaded implements IccRecords.IccRecordLoaded {
        private EfPlLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_PL";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.mEFpl = (byte[]) ar.result;
            RuimRecords.this.log("EF_PL=" + IccUtils.bytesToHexString(RuimRecords.this.mEFpl));
        }
    }

    private class EfCsimLiLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimLiLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_LI";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.mEFli = (byte[]) ar.result;
            for (int i = 0; i < RuimRecords.this.mEFli.length; i += 2) {
                switch (RuimRecords.this.mEFli[i + 1]) {
                    case 1:
                        RuimRecords.this.mEFli[i] = 101;
                        RuimRecords.this.mEFli[i + 1] = 110;
                        break;
                    case 2:
                        RuimRecords.this.mEFli[i] = 102;
                        RuimRecords.this.mEFli[i + 1] = 114;
                        break;
                    case 3:
                        RuimRecords.this.mEFli[i] = 101;
                        RuimRecords.this.mEFli[i + 1] = 115;
                        break;
                    case 4:
                        RuimRecords.this.mEFli[i] = 106;
                        RuimRecords.this.mEFli[i + 1] = 97;
                        break;
                    case 5:
                        RuimRecords.this.mEFli[i] = 107;
                        RuimRecords.this.mEFli[i + 1] = 111;
                        break;
                    case 6:
                        RuimRecords.this.mEFli[i] = 122;
                        RuimRecords.this.mEFli[i + 1] = 104;
                        break;
                    case 7:
                        RuimRecords.this.mEFli[i] = 104;
                        RuimRecords.this.mEFli[i + 1] = 101;
                        break;
                    default:
                        RuimRecords.this.mEFli[i] = 32;
                        RuimRecords.this.mEFli[i + 1] = 32;
                        break;
                }
            }
            RuimRecords.this.log("EF_LI=" + IccUtils.bytesToHexString(RuimRecords.this.mEFli));
        }
    }

    private class EfCsimSpnLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimSpnLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_SPN";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            RuimRecords.this.log("CSIM_SPN=" + IccUtils.bytesToHexString(data));
            RuimRecords.this.mCsimSpnDisplayCondition = (data[0] & 1) != 0;
            int encoding = data[1];
            byte b = data[2];
            byte[] spnData = new byte[32];
            int len = data.length + (-3) < 32 ? data.length - 3 : 32;
            System.arraycopy(data, 3, spnData, 0, len);
            int numBytes = 0;
            while (numBytes < spnData.length && (spnData[numBytes] & 255) != 255) {
                numBytes++;
            }
            if (numBytes == 0) {
                RuimRecords.this.mSpn = "";
                return;
            }
            try {
                switch (encoding) {
                    case 0:
                    case 8:
                        RuimRecords.this.mSpn = new String(spnData, 0, numBytes, "ISO-8859-1");
                        break;
                    case 1:
                    case 5:
                    case 6:
                    case 7:
                    default:
                        RuimRecords.this.log("SPN encoding not supported");
                        break;
                    case 2:
                        RuimRecords.this.mSpn = new String(spnData, 0, numBytes, "US-ASCII");
                        if (!TextUtils.isPrintableAsciiOnly(RuimRecords.this.mSpn)) {
                            RuimRecords.this.log("Some corruption in SPN decoding = " + RuimRecords.this.mSpn);
                            RuimRecords.this.log("Using ENCODING_GSM_7BIT_ALPHABET scheme...");
                            RuimRecords.this.mSpn = GsmAlphabet.gsm7BitPackedToString(spnData, 0, (numBytes * 8) / 7);
                        }
                        break;
                    case 3:
                    case 9:
                        RuimRecords.this.mSpn = GsmAlphabet.gsm7BitPackedToString(spnData, 0, (numBytes * 8) / 7);
                        break;
                    case 4:
                        RuimRecords.this.mSpn = new String(spnData, 0, numBytes, "utf-16");
                        break;
                }
            } catch (Exception e) {
                RuimRecords.this.log("spn decode error: " + e);
            }
            RuimRecords.this.log("spn=" + RuimRecords.this.mSpn);
            RuimRecords.this.log("spnCondition=" + RuimRecords.this.mCsimSpnDisplayCondition);
            SystemProperties.set("gsm.sim.operator.alpha", RuimRecords.this.mSpn);
        }
    }

    private class EfCsimMdnLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimMdnLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_MDN";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            RuimRecords.this.log("CSIM_MDN=" + IccUtils.bytesToHexString(data));
            int mdnDigitsNum = data[0] & 15;
            RuimRecords.this.mMdn = IccUtils.cdmaBcdToString(data, 1, mdnDigitsNum);
            RuimRecords.this.log("CSIM MDN=" + RuimRecords.this.mMdn);
        }
    }

    private class EfCsimImsimLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimImsimLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_IMSIM";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            if (data == null || data.length < 10) {
                RuimRecords.this.log("Invalid IMSI from EF_CSIM_IMSIM " + IccUtils.bytesToHexString(data));
                RuimRecords.this.mImsi = null;
                RuimRecords.this.mMin = null;
                return;
            }
            RuimRecords.this.log("CSIM_IMSIM=" + IccUtils.bytesToHexString(data));
            boolean provisioned = (data[7] & 128) == 128;
            if (provisioned) {
                RuimRecords.this.mImsi = RuimRecords.this.decodeImsi(data);
                if (RuimRecords.this.mImsi != null) {
                    RuimRecords.this.mMin = RuimRecords.this.mImsi.substring(5, 15);
                }
                RuimRecords.this.log("IMSI: " + RuimRecords.this.mImsi.substring(0, 5) + "xxxxxxxxx");
            } else {
                RuimRecords.this.log("IMSI not provisioned in card");
            }
            String operatorNumeric = RuimRecords.this.getOperatorNumeric();
            if (operatorNumeric != null && operatorNumeric.length() <= 6) {
                MccTable.updateMccMncConfiguration(RuimRecords.this.mContext, operatorNumeric, false);
            }
            RuimRecords.this.mImsiReadyRegistrants.notifyRegistrants();
        }
    }

    private class EfCsimCdmaHomeLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimCdmaHomeLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_CDMAHOME";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            ArrayList<byte[]> dataList = (ArrayList) ar.result;
            RuimRecords.this.log("CSIM_CDMAHOME data size=" + dataList.size());
            if (!dataList.isEmpty()) {
                StringBuilder sidBuf = new StringBuilder();
                StringBuilder nidBuf = new StringBuilder();
                for (byte[] data : dataList) {
                    if (data.length == 5) {
                        int sid = ((data[1] & 255) << 8) | (data[0] & 255);
                        int nid = ((data[3] & 255) << 8) | (data[2] & 255);
                        sidBuf.append(sid).append(',');
                        nidBuf.append(nid).append(',');
                    }
                }
                sidBuf.setLength(sidBuf.length() - 1);
                nidBuf.setLength(nidBuf.length() - 1);
                RuimRecords.this.mHomeSystemId = sidBuf.toString();
                RuimRecords.this.mHomeNetworkId = nidBuf.toString();
            }
        }
    }

    private class EfCsimEprlLoaded implements IccRecords.IccRecordLoaded {
        private EfCsimEprlLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_CSIM_EPRL";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            RuimRecords.this.onGetCSimEprlDone(ar);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onGetCSimEprlDone(AsyncResult ar) {
        byte[] data = (byte[]) ar.result;
        log("CSIM_EPRL=" + IccUtils.bytesToHexString(data));
        if (data.length > 3) {
            int prlId = ((data[2] & 255) << 8) | (data[3] & 255);
            this.mPrlVersion = Integer.toString(prlId);
        }
        log("CSIM PRL version=" + this.mPrlVersion);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public void handleMessage(Message msg) {
        boolean isRecordLoadResponse = false;
        try {
            if (this.mDestroyed.get()) {
                loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                return;
            }
            switch (msg.what) {
                case 1:
                    onReady();
                    break;
                case 2:
                case 3:
                case 6:
                case 7:
                case 8:
                case 9:
                case 11:
                case 12:
                case 13:
                case 15:
                case 16:
                case 20:
                default:
                    super.handleMessage(msg);
                    break;
                case 4:
                    log("Event EVENT_GET_DEVICE_IDENTITY_DONE Received");
                    break;
                case 5:
                    isRecordLoadResponse = true;
                    AsyncResult ar = (AsyncResult) msg.obj;
                    byte[] data = (byte[]) ar.result;
                    if (ar.exception == null) {
                        this.mIccId = IccUtils.bcdToString(data, 0, data.length);
                        log("iccid: " + this.mIccId);
                    }
                    break;
                case 10:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    String[] localTemp = (String[]) ar2.result;
                    if (ar2.exception == null) {
                        this.mMyMobileNumber = localTemp[0];
                        this.mMin2Min1 = localTemp[3];
                        this.mPrlVersion = localTemp[4];
                        log("MDN: " + this.mMyMobileNumber + " MIN: " + this.mMin2Min1);
                    }
                    break;
                case 14:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    if (ar3.exception != null) {
                        Rlog.i(LOG_TAG, "RuimRecords update failed", ar3.exception);
                    }
                    break;
                case 17:
                    log("Event EVENT_GET_SST_DONE Received");
                    break;
                case 18:
                case 19:
                case 21:
                case 22:
                    Rlog.w(LOG_TAG, "Event not supported: " + msg.what);
                    break;
            }
        } catch (RuntimeException exc) {
            Rlog.w(LOG_TAG, "Exception parsing RUIM record", exc);
        } finally {
            if (0 != 0) {
                onRecordLoaded();
            }
        }
    }

    private String findBestLanguage(byte[] languages) {
        String[] locales = this.mContext.getAssets().getLocales();
        if (languages == null || locales == null) {
            return null;
        }
        for (int i = 0; i + 1 < languages.length; i += 2) {
            try {
                String lang = new String(languages, i, 2, "ISO-8859-1");
                for (int j = 0; j < locales.length; j++) {
                    if (locales[j] != null && locales[j].length() >= 2 && locales[j].substring(0, 2).equals(lang)) {
                        return lang;
                    }
                }
                if (0 != 0) {
                    break;
                }
            } catch (UnsupportedEncodingException e) {
                log("Failed to parse SIM language records");
            }
        }
        return null;
    }

    private void setLocaleFromCsim() {
        String prefLang = findBestLanguage(this.mEFli);
        if (prefLang == null) {
            prefLang = findBestLanguage(this.mEFpl);
        }
        if (prefLang != null) {
            String imsi = getIMSI();
            String country = null;
            if (imsi != null) {
                country = MccTable.countryCodeForMcc(Integer.parseInt(imsi.substring(0, 3)));
            }
            log("Setting locale to " + prefLang + "_" + country);
            MccTable.setSystemLocale(this.mContext, prefLang, country);
            return;
        }
        log("No suitable CSIM selected locale");
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
            setSystemProperty("gsm.apn.ruim.operator.numeric", operator);
        } else {
            log("onAllRecordsLoaded empty 'gsm.sim.operator.numeric' skipping");
        }
        if (!TextUtils.isEmpty(this.mImsi)) {
            log("onAllRecordsLoaded set mcc imsi=" + this.mImsi);
            setSystemProperty("gsm.sim.operator.iso-country", MccTable.countryCodeForMcc(Integer.parseInt(this.mImsi.substring(0, 3))));
        } else {
            log("onAllRecordsLoaded empty imsi skipping setting mcc");
        }
        setLocaleFromCsim();
        this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onReady() {
        fetchRuimRecords();
        this.mCi.getCDMASubscription(obtainMessage(10));
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    void recordsRequired() {
        log("recordsRequired mRecordsRequired = " + this.mRecordsRequired);
        if (!this.mRecordsRequired) {
            this.mRecordsRequired = true;
            fetchRuimRecords();
        }
    }

    private void fetchRuimRecords() {
        if (!this.mRecordsRequired || IccCardApplicationStatus.AppState.APPSTATE_READY != this.mParentApp.getState()) {
            log("fetchRuimRecords: Abort fetching records rRecordsRequested = " + this.mRecordsRequested + " state = " + this.mParentApp.getState() + " required = " + this.mRecordsRequired);
            return;
        }
        this.mRecordsRequested = true;
        log("fetchRuimRecords " + this.mRecordsToLoad);
        this.mFh.loadEFTransparent(IccConstants.EF_ICCID, obtainMessage(5));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_PL, obtainMessage(100, new EfPlLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(28474, obtainMessage(100, new EfCsimLiLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(28481, obtainMessage(100, new EfCsimSpnLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixed(IccConstants.EF_CSIM_MDN, 1, obtainMessage(100, new EfCsimMdnLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CSIM_IMSIM, obtainMessage(100, new EfCsimImsimLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_CSIM_CDMAHOME, obtainMessage(100, new EfCsimCdmaHomeLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_CSIM_EPRL, 4, obtainMessage(100, new EfCsimEprlLoaded()));
        this.mRecordsToLoad++;
        this.mFh.getEFLinearRecordSize(IccConstants.EF_SMS, obtainMessage(35));
        log("fetchRuimRecords " + this.mRecordsToLoad + " requested: " + this.mRecordsRequested);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getDisplayRule(String plmn) {
        return 0;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public boolean isProvisioned() {
        if (SystemProperties.getBoolean("persist.radio.test-csim", false)) {
            return true;
        }
        if (this.mParentApp == null) {
            return false;
        }
        if (this.mParentApp.getType() == IccCardApplicationStatus.AppType.APPTYPE_CSIM) {
            return (this.mMdn == null || this.mMin == null) ? false : true;
        }
        return true;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        log("RuimRecords:setVoiceMessageWaiting - NOP for CDMA");
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getVoiceMessageCount() {
        log("RuimRecords:getVoiceMessageCount - NOP for CDMA");
        return 0;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void handleFileUpdate(int efid) {
        switch (efid) {
            case 28474:
                log("SIM Refresh for EF_ADN");
                this.mAdnCache.reset();
                break;
            default:
                this.mAdnCache.reset();
                fetchRuimRecords();
                break;
        }
    }

    public String getMdn() {
        return this.mMdn;
    }

    public String getMin() {
        return this.mMin;
    }

    public String getSid() {
        return this.mHomeSystemId;
    }

    public String getNid() {
        return this.mHomeNetworkId;
    }

    public boolean getCsimSpnDisplayCondition() {
        return this.mCsimSpnDisplayCondition;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[RuimRecords] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[RuimRecords] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("RuimRecords: " + this);
        pw.println(" extends:");
        super.dump(fd, pw, args);
        pw.println(" mOtaCommited=" + this.mOtaCommited);
        pw.println(" mMyMobileNumber=" + this.mMyMobileNumber);
        pw.println(" mMin2Min1=" + this.mMin2Min1);
        pw.println(" mPrlVersion=" + this.mPrlVersion);
        pw.println(" mEFpl[]=" + Arrays.toString(this.mEFpl));
        pw.println(" mEFli[]=" + Arrays.toString(this.mEFli));
        pw.println(" mCsimSpnDisplayCondition=" + this.mCsimSpnDisplayCondition);
        pw.println(" mMdn=" + this.mMdn);
        pw.println(" mMin=" + this.mMin);
        pw.println(" mHomeSystemId=" + this.mHomeSystemId);
        pw.println(" mHomeNetworkId=" + this.mHomeNetworkId);
        pw.flush();
    }

    private void setSystemProperty(String key, String val) {
        if (!MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            SystemProperties.set(key, val);
        }
    }
}
