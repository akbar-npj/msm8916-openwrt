package com.android.internal.telephony.cdma;

import android.R;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.RuimRecords;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class CdmaLteServiceStateTracker extends CdmaServiceStateTracker {
    private CDMALTEPhone mCdmaLtePhone;
    private final CellInfoLte mCellInfoLte;
    private CellIdentityLte mLasteCellIdentityLte;
    private CellIdentityLte mNewCellIdentityLte;

    public CdmaLteServiceStateTracker(CDMALTEPhone phone) {
        super(phone, new CellInfoLte());
        this.mNewCellIdentityLte = new CellIdentityLte();
        this.mLasteCellIdentityLte = new CellIdentityLte();
        this.mCdmaLtePhone = phone;
        this.mCellInfoLte = (CellInfoLte) this.mCellInfo;
        ((CellInfoLte) this.mCellInfo).setCellSignalStrength(new CellSignalStrengthLte());
        ((CellInfoLte) this.mCellInfo).setCellIdentity(new CellIdentityLte());
        log("CdmaLteServiceStateTracker Constructors");
    }

    @Override // com.android.internal.telephony.cdma.CdmaServiceStateTracker, com.android.internal.telephony.ServiceStateTracker, android.os.Handler
    public void handleMessage(Message msg) {
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
        }
        switch (msg.what) {
            case 5:
                log("handleMessage EVENT_POLL_STATE_GPRS");
                AsyncResult ar = (AsyncResult) msg.obj;
                handlePollStateResult(msg.what, ar);
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    @Override // com.android.internal.telephony.cdma.CdmaServiceStateTracker
    protected void handlePollStateResultMessage(int what, AsyncResult ar) {
        int mcc;
        int mnc;
        int tac;
        int pci;
        int eci;
        if (what == 5) {
            String[] states = (String[]) ar.result;
            log("handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=" + states.length + " states=" + states);
            int type = 0;
            int regState = -1;
            if (states.length > 0) {
                try {
                    regState = Integer.parseInt(states[0]);
                    if (states.length >= 4 && states[3] != null) {
                        type = Integer.parseInt(states[3]);
                    }
                } catch (NumberFormatException ex) {
                    loge("handlePollStateResultMessage: error parsing GprsRegistrationState: " + ex);
                }
                if (states.length >= 10) {
                    String operatorNumeric = null;
                    try {
                        operatorNumeric = this.mNewSS.getOperatorNumeric();
                        mcc = Integer.parseInt(operatorNumeric.substring(0, 3));
                    } catch (Exception e) {
                        try {
                            operatorNumeric = this.mSS.getOperatorNumeric();
                            mcc = Integer.parseInt(operatorNumeric.substring(0, 3));
                        } catch (Exception ex2) {
                            loge("handlePollStateResultMessage: bad mcc operatorNumeric=" + operatorNumeric + " ex=" + ex2);
                            operatorNumeric = "";
                            mcc = Integer.MAX_VALUE;
                        }
                    }
                    try {
                        mnc = Integer.parseInt(operatorNumeric.substring(3));
                    } catch (Exception e2) {
                        loge("handlePollStateResultMessage: bad mnc operatorNumeric=" + operatorNumeric + " e=" + e2);
                        mnc = Integer.MAX_VALUE;
                    }
                    try {
                        tac = Integer.decode(states[6]).intValue();
                    } catch (Exception e3) {
                        loge("handlePollStateResultMessage: bad tac states[6]=" + states[6] + " e=" + e3);
                        tac = Integer.MAX_VALUE;
                    }
                    try {
                        pci = Integer.decode(states[7]).intValue();
                    } catch (Exception e4) {
                        loge("handlePollStateResultMessage: bad pci states[7]=" + states[7] + " e=" + e4);
                        pci = Integer.MAX_VALUE;
                    }
                    try {
                        eci = Integer.decode(states[8]).intValue();
                    } catch (Exception e5) {
                        loge("handlePollStateResultMessage: bad eci states[8]=" + states[8] + " e=" + e5);
                        eci = Integer.MAX_VALUE;
                    }
                    try {
                        Integer.decode(states[9]).intValue();
                    } catch (Exception e6) {
                    }
                    this.mNewCellIdentityLte = new CellIdentityLte(mcc, mnc, eci, pci, tac);
                    log("handlePollStateResultMessage: mNewLteCellIdentity=" + this.mNewCellIdentityLte);
                }
            }
            this.mNewSS.setRilDataRadioTechnology(type);
            int dataRegState = regCodeToServiceState(regState);
            this.mNewSS.setDataRegState(dataRegState);
            log("handlPollStateResultMessage: CdmaLteSST setDataRegState=" + dataRegState + " regState=" + regState + " dataRadioTechnology=" + type);
            this.mDataRoaming = regCodeIsRoaming(regState);
            return;
        }
        super.handlePollStateResultMessage(what, ar);
    }

    @Override // com.android.internal.telephony.cdma.CdmaServiceStateTracker
    protected void pollState() {
        this.mPollingContext = new int[1];
        this.mPollingContext[0] = 0;
        switch (this.mCi.getRadioState()) {
            case RADIO_UNAVAILABLE:
                this.mNewSS.setStateOutOfService();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                return;
            case RADIO_OFF:
                this.mNewSS.setStateOff();
                this.mNewCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                this.mGotCountryCode = false;
                pollStateDone();
                if (!isIwlanFeatureAvailable()) {
                    return;
                }
                break;
        }
        int[] iArr = this.mPollingContext;
        iArr[0] = iArr[0] + 1;
        this.mCi.getOperator(obtainMessage(25, this.mPollingContext));
        int[] iArr2 = this.mPollingContext;
        iArr2[0] = iArr2[0] + 1;
        this.mCi.getVoiceRegistrationState(obtainMessage(24, this.mPollingContext));
        int[] iArr3 = this.mPollingContext;
        iArr3[0] = iArr3[0] + 1;
        this.mCi.getDataRegistrationState(obtainMessage(5, this.mPollingContext));
    }

    @Override // com.android.internal.telephony.cdma.CdmaServiceStateTracker
    protected void pollStateDone() {
        String eriText;
        log("pollStateDone: lte 1 ss=[" + this.mSS + "] newSS=[" + this.mNewSS + "]");
        useDataRegStateForDataOnlyDevices();
        boolean hasRegistered = this.mSS.getVoiceRegState() != 0 && this.mNewSS.getVoiceRegState() == 0;
        boolean hasDeregistered = this.mSS.getVoiceRegState() == 0 && this.mNewSS.getVoiceRegState() != 0;
        boolean hasCdmaDataConnectionAttached = this.mSS.getDataRegState() != 0 && this.mNewSS.getDataRegState() == 0;
        boolean hasCdmaDataConnectionDetached = this.mSS.getDataRegState() == 0 && this.mNewSS.getDataRegState() != 0;
        boolean hasCdmaDataConnectionChanged = this.mSS.getDataRegState() != this.mNewSS.getDataRegState();
        boolean hasVoiceRadioTechnologyChanged = this.mSS.getRilVoiceRadioTechnology() != this.mNewSS.getRilVoiceRadioTechnology();
        boolean hasDataRadioTechnologyChanged = this.mSS.getRilDataRadioTechnology() != this.mNewSS.getRilDataRadioTechnology();
        boolean hasChanged = !this.mNewSS.equals(this.mSS);
        boolean hasRoamingOn = !this.mSS.getRoaming() && this.mNewSS.getRoaming();
        boolean hasRoamingOff = this.mSS.getRoaming() && !this.mNewSS.getRoaming();
        boolean hasLocationChanged = !this.mNewCellLoc.equals(this.mCellLoc);
        boolean has4gHandoff = this.mNewSS.getDataRegState() == 0 && ((this.mSS.getRilDataRadioTechnology() == 14 && this.mNewSS.getRilDataRadioTechnology() == 13) || (this.mSS.getRilDataRadioTechnology() == 13 && this.mNewSS.getRilDataRadioTechnology() == 14));
        boolean hasMultiApnSupport = ((this.mNewSS.getRilDataRadioTechnology() != 14 && this.mNewSS.getRilDataRadioTechnology() != 13) || this.mSS.getRilDataRadioTechnology() == 14 || this.mSS.getRilDataRadioTechnology() == 13) ? false : true;
        boolean hasLostMultiApnSupport = this.mNewSS.getRilDataRadioTechnology() >= 4 && this.mNewSS.getRilDataRadioTechnology() <= 8;
        boolean needNotifyData = this.mSS.getCssIndicator() != this.mNewSS.getCssIndicator();
        log("pollStateDone: hasRegistered=" + hasRegistered + " hasDeegistered=" + hasDeregistered + " hasCdmaDataConnectionAttached=" + hasCdmaDataConnectionAttached + " hasCdmaDataConnectionDetached=" + hasCdmaDataConnectionDetached + " hasCdmaDataConnectionChanged=" + hasCdmaDataConnectionChanged + " hasVoiceRadioTechnologyChanged= " + hasVoiceRadioTechnologyChanged + " hasDataRadioTechnologyChanged=" + hasDataRadioTechnologyChanged + " hasChanged=" + hasChanged + " hasRoamingOn=" + hasRoamingOn + " hasRoamingOff=" + hasRoamingOff + " hasLocationChanged=" + hasLocationChanged + " has4gHandoff = " + has4gHandoff + " hasMultiApnSupport=" + hasMultiApnSupport + " hasLostMultiApnSupport=" + hasLostMultiApnSupport);
        if (this.mSS.getVoiceRegState() != this.mNewSS.getVoiceRegState() || this.mSS.getDataRegState() != this.mNewSS.getDataRegState()) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE, Integer.valueOf(this.mSS.getVoiceRegState()), Integer.valueOf(this.mSS.getDataRegState()), Integer.valueOf(this.mNewSS.getVoiceRegState()), Integer.valueOf(this.mNewSS.getDataRegState()));
        }
        ServiceState tss = this.mSS;
        this.mSS = this.mNewSS;
        this.mNewSS = tss;
        this.mNewSS.setStateOutOfService();
        CdmaCellLocation tcl = this.mCellLoc;
        this.mCellLoc = this.mNewCellLoc;
        this.mNewCellLoc = tcl;
        this.mNewSS.setStateOutOfService();
        if (hasVoiceRadioTechnologyChanged) {
            updatePhoneObject();
        }
        if (hasDataRadioTechnologyChanged) {
            this.mPhone.setSystemProperty("gsm.network.type", ServiceState.rilRadioTechnologyToString(this.mSS.getRilDataRadioTechnology()));
            sendMessage(obtainMessage(10));
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                handleIwlan();
            }
        }
        if (hasRegistered) {
            this.mNetworkAttachedRegistrants.notifyRegistrants();
        }
        if (hasChanged) {
            if (this.mCi.getRadioState().isOn() && this.mPhone.isEriFileLoaded() && !this.mIsSubscriptionFromRuim) {
                if (this.mSS.getVoiceRegState() == 0 || this.mSS.getDataRegState() == 0) {
                    eriText = this.mPhone.getCdmaEriText();
                } else if (this.mSS.getVoiceRegState() == 3) {
                    eriText = this.mIccRecords != null ? this.mIccRecords.getServiceProviderName() : null;
                    if (TextUtils.isEmpty(eriText)) {
                        eriText = SystemProperties.get("ro.cdma.home.operator.alpha");
                    }
                } else {
                    eriText = this.mPhone.getContext().getText(R.string.PERSOSUBSTATE_SIM_ICCID_ERROR).toString();
                }
                this.mSS.setOperatorAlphaLong(eriText);
            }
            if (this.mUiccApplcation != null && this.mUiccApplcation.getState() == IccCardApplicationStatus.AppState.APPSTATE_READY && this.mIccRecords != null) {
                boolean showSpn = ((RuimRecords) this.mIccRecords).getCsimSpnDisplayCondition();
                int iconIndex = this.mSS.getCdmaEriIconIndex();
                if (showSpn && iconIndex == 1 && isInHomeSidNid(this.mSS.getSystemId(), this.mSS.getNetworkId()) && this.mIccRecords != null) {
                    this.mSS.setOperatorAlphaLong(this.mIccRecords.getServiceProviderName());
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.alpha", this.mSS.getOperatorAlphaLong());
            String prevOperatorNumeric = SystemProperties.get("gsm.operator.numeric", "");
            String operatorNumeric = this.mSS.getOperatorNumeric();
            this.mPhone.setSystemProperty("gsm.operator.numeric", operatorNumeric);
            updateCarrierMccMncConfiguration(operatorNumeric, prevOperatorNumeric, this.mPhone.getContext());
            if (operatorNumeric == null) {
                log("operatorNumeric is null");
                this.mPhone.setSystemProperty("gsm.operator.iso-country", "");
                this.mGotCountryCode = false;
            } else {
                String isoCountryCode = "";
                operatorNumeric.substring(0, 3);
                try {
                    isoCountryCode = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
                } catch (NumberFormatException ex) {
                    loge("countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex2) {
                    loge("countryCodeForMcc error" + ex2);
                }
                this.mPhone.setSystemProperty("gsm.operator.iso-country", isoCountryCode);
                this.mGotCountryCode = true;
                if (shouldFixTimeZoneNow(this.mPhone, operatorNumeric, prevOperatorNumeric, this.mNeedFixZone)) {
                    fixTimeZone(isoCountryCode);
                }
            }
            this.mPhone.setSystemProperty("gsm.operator.isroaming", this.mSS.getRoaming() ? "true" : "false");
            this.mPhone.notifyServiceStateChanged(this.mSS);
            updateSpnDisplay();
        }
        if (hasCdmaDataConnectionDetached) {
            this.mDetachedRegistrants.notifyRegistrants();
        }
        if (hasCdmaDataConnectionChanged || hasDataRadioTechnologyChanged) {
            notifyDataRegStateRilRadioTechnologyChanged();
            if (isIwlanFeatureAvailable() && 18 == this.mSS.getRilDataRadioTechnology()) {
                this.mPhone.notifyDataConnection(Phone.REASON_IWLAN_AVAILABLE);
                this.mIwlanRegistrants.notifyRegistrants();
                needNotifyData = false;
            } else {
                needNotifyData = true;
            }
        }
        if (needNotifyData) {
            this.mPhone.notifyDataConnection(null);
        }
        if (hasCdmaDataConnectionAttached || has4gHandoff) {
            this.mAttachedRegistrants.notifyRegistrants();
        }
        if (hasRoamingOn) {
            this.mRoamingOnRegistrants.notifyRegistrants();
        }
        if (hasRoamingOff) {
            this.mRoamingOffRegistrants.notifyRegistrants();
        }
        if (hasLocationChanged) {
            this.mPhone.notifyLocationChanged();
        }
        ArrayList<CellInfo> arrayCi = new ArrayList<>();
        synchronized (this.mCellInfo) {
            CellInfoLte cil = (CellInfoLte) this.mCellInfo;
            boolean cidChanged = !this.mNewCellIdentityLte.equals(this.mLasteCellIdentityLte);
            if (hasRegistered || hasDeregistered || cidChanged) {
                long jElapsedRealtime = SystemClock.elapsedRealtime() * 1000;
                boolean registered = this.mSS.getVoiceRegState() == 0;
                this.mLasteCellIdentityLte = this.mNewCellIdentityLte;
                cil.setRegisterd(registered);
                cil.setCellIdentity(this.mLasteCellIdentityLte);
                log("pollStateDone: hasRegistered=" + hasRegistered + " hasDeregistered=" + hasDeregistered + " cidChanged=" + cidChanged + " mCellInfo=" + this.mCellInfo);
                arrayCi.add(this.mCellInfo);
            }
            this.mPhoneBase.notifyCellInfo(arrayCi);
        }
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected boolean onSignalStrengthResult(AsyncResult ar, boolean isGsm) {
        if (this.mSS.getRilDataRadioTechnology() == 14) {
            isGsm = true;
        }
        boolean ssChanged = super.onSignalStrengthResult(ar, isGsm);
        synchronized (this.mCellInfo) {
            if (this.mSS.getRilDataRadioTechnology() == 14) {
                this.mCellInfoLte.setTimeStamp(SystemClock.elapsedRealtime() * 1000);
                this.mCellInfoLte.setTimeStampType(4);
                this.mCellInfoLte.getCellSignalStrength().initialize(this.mSignalStrength, Integer.MAX_VALUE);
            }
            if (this.mCellInfoLte.getCellIdentity() != null) {
                ArrayList<CellInfo> arrayCi = new ArrayList<>();
                arrayCi.add(this.mCellInfoLte);
                this.mPhoneBase.notifyCellInfo(arrayCi);
            }
        }
        return ssChanged;
    }

    @Override // com.android.internal.telephony.cdma.CdmaServiceStateTracker, com.android.internal.telephony.ServiceStateTracker
    public boolean isConcurrentVoiceAndDataAllowed() {
        return this.mSS.getCssIndicator() == 1;
    }

    private boolean isInHomeSidNid(int sid, int nid) {
        if (isSidsAllZeros() || this.mHomeSystemId.length != this.mHomeNetworkId.length || sid == 0) {
            return true;
        }
        for (int i = 0; i < this.mHomeSystemId.length; i++) {
            if (this.mHomeSystemId[i] == sid && (this.mHomeNetworkId[i] == 0 || this.mHomeNetworkId[i] == 65535 || nid == 0 || nid == 65535 || this.mHomeNetworkId[i] == nid)) {
                return true;
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    public List<CellInfo> getAllCellInfo() {
        if (this.mCi.getRilVersion() >= 8) {
            return super.getAllCellInfo();
        }
        ArrayList<CellInfo> arrayList = new ArrayList<>();
        synchronized (this.mCellInfo) {
            arrayList.add(this.mCellInfoLte);
        }
        log("getAllCellInfo: arrayList=" + arrayList);
        return arrayList;
    }

    @Override // com.android.internal.telephony.ServiceStateTracker
    protected void updatePhoneObject() {
        int voiceRat = this.mSS.getRilVoiceRadioTechnology();
        if (voiceRat == 14) {
            voiceRat = 6;
        }
        this.mPhoneBase.updatePhoneObject(voiceRat);
    }

    @Override // com.android.internal.telephony.cdma.CdmaServiceStateTracker, com.android.internal.telephony.ServiceStateTracker
    protected void log(String s) {
        Rlog.d("CdmaSST", "[CdmaLteSST] " + s);
    }

    @Override // com.android.internal.telephony.cdma.CdmaServiceStateTracker, com.android.internal.telephony.ServiceStateTracker
    protected void loge(String s) {
        Rlog.e("CdmaSST", "[CdmaLteSST] " + s);
    }

    @Override // com.android.internal.telephony.cdma.CdmaServiceStateTracker, com.android.internal.telephony.ServiceStateTracker
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CdmaLteServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mCdmaLtePhone=" + this.mCdmaLtePhone);
    }
}
