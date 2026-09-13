package com.android.internal.telephony.dataconnection;

import android.R;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkConfig;
import android.net.NetworkUtils;
import android.net.ProxyProperties;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.CellLocation;
import android.telephony.MSimTelephonyManager;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.Objects;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/* JADX INFO: loaded from: classes.dex */
public class DcTracker extends DcTrackerBase {
    static final String APN_ID = "apn_id";
    private static final int POLL_PDP_MILLIS = 5000;
    private static final String PUPPET_MASTER_RADIO_STRESS_TEST = "gsm.defaultpdpcontext.active";
    protected final String LOG_TAG;
    private ApnChangeObserver mApnObserver;
    private AtomicBoolean mAttached;
    private boolean mCanSetPreferApn;
    private CdmaSubscriptionSourceManager mCdmaSsm;
    private CdmaDataProfileTracker mOmhDpt;
    protected boolean mOosIsDisconnect;
    private boolean mReregisterOnReconnectFailure;
    static final Uri PREFERAPN_NO_UPDATE_URI = Uri.parse("content://telephony/carriers/preferapn_no_update");
    private static final boolean SUPPORT_MPDN = SystemProperties.getBoolean("persist.telephony.mpdn", true);
    public static final String PROPERTY_CDMA_IPPROTOCOL = SystemProperties.get("persist.telephony.cdma.protocol", "IP");
    public static final String PROPERTY_CDMA_ROAMING_IPPROTOCOL = SystemProperties.get("persist.telephony.cdma.rproto", "IP");

    private class ApnChangeObserver extends ContentObserver {
        public ApnChangeObserver() {
            super(DcTracker.this.mDataConnectionTracker);
        }

        @Override // android.database.ContentObserver
        public void onChange(boolean selfChange) {
            DcTracker.this.sendMessage(DcTracker.this.obtainMessage(270355));
        }
    }

    public DcTracker(PhoneBase p) {
        super(p);
        this.mReregisterOnReconnectFailure = false;
        this.mOosIsDisconnect = SystemProperties.getBoolean(PhoneBase.PROPERTY_OOS_IS_DISCONNECT, true);
        this.mCanSetPreferApn = false;
        this.mAttached = new AtomicBoolean(false);
        if (p.getPhoneType() == 1) {
            this.LOG_TAG = "GsmDCT";
        } else if (p.getPhoneType() == 2) {
            this.LOG_TAG = "CdmaDCT";
        } else {
            this.LOG_TAG = "DCT";
            loge("unexpected phone type [" + p.getPhoneType() + "]");
        }
        log(this.LOG_TAG + ".constructor");
        p.mCi.registerForAvailable(this, 270337, null);
        p.mCi.registerForOffOrNotAvailable(this, 270342, null);
        p.getServiceStateTracker().registerForIwlanAvailable(this, 270379, null);
        p.getCallTracker().registerForVoiceCallEnded(this, 270344, null);
        p.getCallTracker().registerForVoiceCallStarted(this, 270343, null);
        p.getServiceStateTracker().registerForDataConnectionAttached(this, 270352, null);
        p.getServiceStateTracker().registerForDataConnectionDetached(this, 270345, null);
        p.getServiceStateTracker().registerForRoamingOn(this, 270347, null);
        p.getServiceStateTracker().registerForRoamingOff(this, 270348, null);
        p.getServiceStateTracker().registerForPsRestrictedEnabled(this, 270358, null);
        p.getServiceStateTracker().registerForPsRestrictedDisabled(this, 270359, null);
        p.getServiceStateTracker().registerForDataRegStateOrRatChanged(this, 270376, null);
        if (p.getPhoneType() == 2) {
            this.mCdmaSsm = CdmaSubscriptionSourceManager.getInstance(p.getContext(), p.mCi, this, 270357, null);
            sendMessage(obtainMessage(270357));
        }
        this.mDataConnectionTracker = this;
        if (CdmaDataProfileTracker.OMH_ENABLED && p.getPhoneType() == 2) {
            this.mOmhDpt = new CdmaDataProfileTracker((CDMAPhone) p);
            this.mOmhDpt.registerForModemProfileReady(this, 270377, null);
        }
        this.mApnObserver = new ApnChangeObserver();
        p.getContext().getContentResolver().registerContentObserver(Telephony.Carriers.CONTENT_URI, true, this.mApnObserver);
        initApnContexts();
        log("SUPPORT_MPDN = " + SUPPORT_MPDN);
        log("OMH_ENABLED = " + CdmaDataProfileTracker.OMH_ENABLED);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.android.internal.telephony.data-reconnect." + apnContext.getDataProfileType());
            filter.addAction("com.android.internal.telephony.data-restart-trysetup." + apnContext.getDataProfileType());
            this.mPhone.getContext().registerReceiver(this.mIntentReceiver, filter, null, this.mPhone);
        }
        supplyMessenger();
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void dispose() {
        log("dispose");
        cleanUpAllConnections(true, null);
        super.dispose();
        this.mPhone.mCi.unregisterForAvailable(this);
        this.mPhone.mCi.unregisterForOffOrNotAvailable(this);
        IccRecords r = this.mIccRecords.get();
        if (r != null) {
            r.unregisterForRecordsLoaded(this);
        }
        this.mPhone.mCi.unregisterForDataNetworkStateChanged(this);
        this.mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
        this.mPhone.getCallTracker().unregisterForVoiceCallStarted(this);
        this.mPhone.getServiceStateTracker().unregisterForDataConnectionAttached(this);
        this.mPhone.getServiceStateTracker().unregisterForDataConnectionDetached(this);
        this.mPhone.getServiceStateTracker().unregisterForRoamingOn(this);
        this.mPhone.getServiceStateTracker().unregisterForRoamingOff(this);
        this.mPhone.getServiceStateTracker().unregisterForPsRestrictedEnabled(this);
        this.mPhone.getServiceStateTracker().unregisterForPsRestrictedDisabled(this);
        this.mPhone.getContext().getContentResolver().unregisterContentObserver(this.mApnObserver);
        this.mApnContexts.clear();
        this.mPrioritySortedApnContexts.clear();
        if (this.mCdmaSsm != null) {
            this.mCdmaSsm.dispose(this);
        }
        if (this.mOmhDpt != null) {
            this.mOmhDpt.unregisterForModemProfileReady(this);
        }
        destroyDataConnections();
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isApnTypeActive(String type) {
        ApnContext apnContext = this.mApnContexts.get(type);
        return (apnContext == null || apnContext.getDcAc() == null) ? false : true;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isDataPossible(String apnType) {
        ApnContext apnContext = this.mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        boolean apnContextIsEnabled = apnContext.isEnabled();
        DctConstants.State apnContextState = apnContext.getState();
        boolean apnTypePossible = (apnContextIsEnabled && apnContextState == DctConstants.State.FAILED) ? false : true;
        boolean dataAllowed = isDataAllowed();
        boolean possible = dataAllowed && apnTypePossible;
        MSimTelephonyManager mtmgr = (MSimTelephonyManager) this.mPhone.getContext().getSystemService("phone_msim");
        if (apnContext.getDataProfileType().equals("default") || apnContext.getDataProfileType().equals("ia") || apnContext.getDataProfileType().equals("hipri")) {
            if (this.mPhone.getServiceState().getRilDataRadioTechnology() == 18) {
                possible = false;
            } else if (this.mPhone.getSubscription() != mtmgr.getDefaultDataSubscription()) {
                log("Default data activation not possible on non-DDS subscription");
                possible = false;
            }
        }
        return possible;
    }

    protected void finalize() {
        log("finalize");
    }

    protected void supplyMessenger() {
        int subId = this.mPhone.getSubscription();
        log("supplyMessenger for subId = " + subId);
        ConnectivityManager cm = (ConnectivityManager) this.mPhone.getContext().getSystemService("connectivity");
        cm.supplyMessengerForSubscription(0, new Messenger(this), subId);
        cm.supplyMessengerForSubscription(2, new Messenger(this), subId);
        cm.supplyMessengerForSubscription(3, new Messenger(this), subId);
        cm.supplyMessengerForSubscription(4, new Messenger(this), subId);
        cm.supplyMessengerForSubscription(5, new Messenger(this), subId);
        cm.supplyMessengerForSubscription(10, new Messenger(this), subId);
        cm.supplyMessengerForSubscription(11, new Messenger(this), subId);
        cm.supplyMessengerForSubscription(12, new Messenger(this), subId);
    }

    private ApnContext addApnContext(String type, NetworkConfig networkConfig) {
        ApnContext apnContext = new ApnContext(this.mPhone.getContext(), type, this.LOG_TAG, networkConfig);
        this.mApnContexts.put(type, apnContext);
        this.mPrioritySortedApnContexts.add(apnContext);
        return apnContext;
    }

    protected void initApnContexts() {
        ApnContext apnContext;
        log("initApnContexts: E");
        boolean defaultEnabled = SystemProperties.getBoolean("net.def_data_on_boot", true);
        String[] networkConfigStrings = this.mPhone.getContext().getResources().getStringArray(R.array.config_allowedSecureInstantAppSettings);
        for (String networkConfigString : networkConfigStrings) {
            NetworkConfig networkConfig = new NetworkConfig(networkConfigString);
            switch (networkConfig.type) {
                case 0:
                    apnContext = addApnContext("default", networkConfig);
                    apnContext.setEnabled(defaultEnabled);
                    break;
                case 1:
                case 6:
                case 7:
                case 8:
                case 9:
                case 13:
                default:
                    log("initApnContexts: skipping unknown type=" + networkConfig.type);
                    continue;
                    break;
                case 2:
                    apnContext = addApnContext("mms", networkConfig);
                    break;
                case 3:
                    apnContext = addApnContext("supl", networkConfig);
                    break;
                case 4:
                    apnContext = addApnContext("dun", networkConfig);
                    break;
                case 5:
                    apnContext = addApnContext("hipri", networkConfig);
                    break;
                case 10:
                    apnContext = addApnContext("fota", networkConfig);
                    break;
                case 11:
                    apnContext = addApnContext("ims", networkConfig);
                    break;
                case 12:
                    apnContext = addApnContext("cbs", networkConfig);
                    break;
                case 14:
                    apnContext = addApnContext("ia", networkConfig);
                    break;
            }
            log("initApnContexts: apnContext=" + apnContext);
        }
        log("initApnContexts: X mApnContexts=" + this.mApnContexts);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public LinkProperties getLinkProperties(String apnType) {
        DcAsyncChannel dcac;
        ApnContext apnContext = this.mApnContexts.get(apnType);
        if (apnContext != null && (dcac = apnContext.getDcAc()) != null) {
            log("return link properites for " + apnType);
            return dcac.getLinkPropertiesSync();
        }
        log("return new LinkProperties");
        return new LinkProperties();
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public LinkCapabilities getLinkCapabilities(String apnType) {
        DcAsyncChannel dataConnectionAc;
        ApnContext apnContext = this.mApnContexts.get(apnType);
        if (apnContext != null && (dataConnectionAc = apnContext.getDcAc()) != null) {
            log("get active pdp is not null, return link Capabilities for " + apnType);
            return dataConnectionAc.getLinkCapabilitiesSync();
        }
        log("return new LinkCapabilities");
        return new LinkCapabilities();
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public String[] getActiveApnTypes() {
        log("get all active apn types");
        ArrayList<String> result = new ArrayList<>();
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() && apnContext.isReady()) {
                result.add(apnContext.getDataProfileType());
            }
        }
        return (String[]) result.toArray(new String[0]);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public String getActiveApnString(String apnType) {
        DataProfile apnSetting;
        ApnContext apnContext = this.mApnContexts.get(apnType);
        if (apnContext == null || (apnSetting = apnContext.getDataProfile()) == null) {
            return null;
        }
        return apnSetting.apn;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isApnTypeEnabled(String apnType) {
        ApnContext apnContext = this.mApnContexts.get(apnType);
        if (apnContext == null) {
            return false;
        }
        return apnContext.isEnabled();
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void setState(DctConstants.State s) {
        log("setState should not be used in GSM" + s);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public DctConstants.State getState(String apnType) {
        ApnContext apnContext = this.mApnContexts.get(apnType);
        return apnContext != null ? apnContext.getState() : DctConstants.State.FAILED;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean isProvisioningApn(String apnType) {
        ApnContext apnContext = this.mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.isProvisioningApn();
        }
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public DctConstants.State getOverallState() {
        boolean isConnecting = false;
        boolean isFailed = true;
        boolean isAnyEnabled = false;
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.isEnabled()) {
                isAnyEnabled = true;
                switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[apnContext.getState().ordinal()]) {
                    case 1:
                    case 2:
                        log("overall state is CONNECTED");
                        return DctConstants.State.CONNECTED;
                    case 3:
                    case 4:
                        isConnecting = true;
                        isFailed = false;
                        break;
                    case 5:
                    case 6:
                        isFailed = false;
                        break;
                    default:
                        isAnyEnabled = true;
                        break;
                }
            }
        }
        if (!isAnyEnabled) {
            log("overall state is IDLE");
            return DctConstants.State.IDLE;
        }
        if (isConnecting) {
            log("overall state is CONNECTING");
            return DctConstants.State.CONNECTING;
        }
        if (!isFailed) {
            log("overall state is IDLE");
            return DctConstants.State.IDLE;
        }
        log("overall state is FAILED");
        return DctConstants.State.FAILED;
    }

    /* JADX INFO: renamed from: com.android.internal.telephony.dataconnection.DcTracker$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.FAILED.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public synchronized int enableApnType(String apnType) {
        int i = 1;
        synchronized (this) {
            log("DcTracker: enableApnType");
            ApnContext apnContext = this.mApnContexts.get(apnType);
            if (apnContext == null || !isApnTypeAvailable(apnType)) {
                log("enableApnType: " + apnType + " is type not available");
                i = 2;
            } else {
                log("enableApnType: " + apnType + " mState(" + apnContext.getState() + ")");
                if (apnContext.getState() == DctConstants.State.CONNECTED) {
                    log("enableApnType: return APN_ALREADY_ACTIVE");
                    i = 0;
                } else {
                    setEnabled(apnTypeToId(apnType), true);
                    log("enableApnType: new apn request for type " + apnType + " return APN_REQUEST_STARTED");
                }
            }
        }
        return i;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public synchronized int disableApnType(String type) {
        int i;
        log("disableApnType:" + type);
        ApnContext apnContext = this.mApnContexts.get(type);
        if (apnContext != null && isApnTypeAvailable(type)) {
            setEnabled(apnTypeToId(type), false);
            if (apnContext.getState() != DctConstants.State.IDLE && apnContext.getState() != DctConstants.State.FAILED) {
                log("diableApnType: return APN_REQUEST_STARTED");
                i = 1;
            } else {
                log("disableApnType: return APN_ALREADY_INACTIVE");
                i = 4;
            }
        } else {
            log("disableApnType: apn context was not found or apnType: " + type + " is not available, return APN_REQUEST_FAILED");
            i = 3;
        }
        return i;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean isApnTypeAvailable(String type) {
        if (type.equals("dun") && fetchDunApn() != null) {
            return true;
        }
        if (this.mAllDps != null) {
            for (DataProfile apn : this.mAllDps) {
                if (apn.canHandleType(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean getAnyDataEnabled() {
        return getAnyDataEnabled(false);
    }

    private boolean getAnyDataEnabled(boolean enableMmsData) {
        synchronized (this.mDataEnabledLock) {
            if (!this.mInternalDataEnabled || ((!this.mUserDataEnabled && !enableMmsData) || !sPolicyDataEnabled)) {
                log(String.format("getAnyDataEnabled data disabled: mInternalDataEnabled=%b mUserDataEnabled=%b enableMmsData=%b sPolicyDataEnabled=%b", Boolean.valueOf(this.mInternalDataEnabled), Boolean.valueOf(this.mUserDataEnabled), Boolean.valueOf(enableMmsData), Boolean.valueOf(sPolicyDataEnabled)));
                return false;
            }
            for (ApnContext apnContext : this.mApnContexts.values()) {
                if (isDataAllowed(apnContext)) {
                    return true;
                }
            }
            return false;
        }
    }

    protected boolean isDataAllowed(ApnContext apnContext) {
        if ((!apnContext.getDataProfileType().equals("default") && !apnContext.getDataProfileType().equals("ia") && !apnContext.getDataProfileType().equals("hipri")) || this.mPhone.getServiceState().getRilDataRadioTechnology() != 18) {
            return apnContext.isReady() && isDataAllowed();
        }
        log("Default data call activation not allowed in iwlan.");
        return false;
    }

    protected void onDataConnectionDetached() {
        log("onDataConnectionDetached: stop polling and notify detached");
        stopNetStatPoll();
        stopDataStallAlarm();
        MSimTelephonyManager mtmgr = (MSimTelephonyManager) this.mPhone.getContext().getSystemService("phone_msim");
        if (this.mPhone.getSubscription() == mtmgr.getDefaultDataSubscription()) {
            log("PS detach on default data subscription, notify.");
            notifyDataConnection(Phone.REASON_DATA_DETACHED);
        } else {
            log("PS detach on non-default data subscription, dont notify.");
        }
        this.mAttached.set(false);
    }

    private void onDataConnectionAttached() {
        log("onDataConnectionAttached");
        this.mAttached.set(true);
        if (getOverallState() == DctConstants.State.CONNECTED) {
            log("onDataConnectionAttached: start polling notify attached");
            startNetStatPoll();
            startDataStallAlarm(false);
            notifyDataConnection(Phone.REASON_DATA_ATTACHED);
        } else {
            notifyOffApnsOfAvailability(Phone.REASON_DATA_ATTACHED);
        }
        this.mAutoAttachOnCreation = true;
        setupDataOnConnectableApns(Phone.REASON_DATA_ATTACHED);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean isDataAllowed() {
        boolean internalDataEnabled;
        synchronized (this.mDataEnabledLock) {
            internalDataEnabled = this.mInternalDataEnabled;
        }
        boolean attachedState = this.mAttached.get();
        boolean desiredPowerState = this.mPhone.getServiceStateTracker().getDesiredPowerState();
        int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
        if (radioTech == 18 && !desiredPowerState) {
            desiredPowerState = true;
        }
        IccRecords r = this.mIccRecords.get();
        boolean recordsLoaded = r != null ? r.getRecordsLoaded() : false;
        boolean subscriptionFromNv = isNvSubscription();
        boolean allowed = (attachedState || this.mAutoAttachOnCreation) && (subscriptionFromNv || recordsLoaded) && ((this.mPhone.getState() == PhoneConstants.State.IDLE || this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) && internalDataEnabled && ((!this.mPhone.getServiceState().getRoaming() || getDataOnRoamingEnabled()) && !this.mIsPsRestricted && desiredPowerState));
        if (!allowed) {
            String reason = "";
            if (!attachedState && !this.mAutoAttachOnCreation) {
                reason = " - Attached= " + attachedState;
            }
            if (!subscriptionFromNv && !recordsLoaded) {
                reason = reason + " - SIM not loaded and not NV subscription";
            }
            if (this.mPhone.getState() != PhoneConstants.State.IDLE && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                reason = (reason + " - PhoneState= " + this.mPhone.getState()) + " - Concurrent voice and data not allowed";
            }
            if (!internalDataEnabled) {
                reason = reason + " - mInternalDataEnabled= false";
            }
            if (this.mPhone.getServiceState().getRoaming() && !getDataOnRoamingEnabled()) {
                reason = reason + " - Roaming and data roaming not enabled";
            }
            if (this.mIsPsRestricted) {
                reason = reason + " - mIsPsRestricted= true";
            }
            if (!desiredPowerState) {
                reason = reason + " - desiredPowerState= false";
            }
            log("isDataAllowed: not allowed due to" + reason);
        }
        return allowed;
    }

    private void setupDataOnConnectableApns(String reason) {
        DataProfile dp;
        log("setupDataOnConnectableApns: " + reason);
        for (ApnContext apnContext : this.mPrioritySortedApnContexts) {
            log("setupDataOnConnectableApns: apnContext " + apnContext);
            if (apnContext.getState() == DctConstants.State.FAILED) {
                apnContext.setState(DctConstants.State.IDLE);
            }
            if (apnContext.isConnectable()) {
                log("setupDataOnConnectableApns: isConnectable() call trySetupData");
                if (this.mOmhDpt != null && (dp = this.mOmhDpt.getDataProfile(apnContext.getDataProfileType())) != null) {
                    boolean dupFound = false;
                    for (DataProfile temp : this.mAllDps) {
                        if (temp.toHash().equals(dp.toHash())) {
                            log("Skip addition of duplicate profile, dp=" + dp);
                            dupFound = true;
                            break;
                        }
                    }
                    if (!dupFound) {
                        log("Adding dp = " + dp + " in mAllDps");
                        this.mAllDps.add(dp);
                    }
                }
                apnContext.setReason(reason);
                trySetupData(apnContext);
            }
        }
    }

    private boolean trySetupData(ApnContext apnContext) {
        log("trySetupData for type:" + apnContext.getDataProfileType() + " due to " + apnContext.getReason() + " apnContext=" + apnContext);
        log("trySetupData with mIsPsRestricted=" + this.mIsPsRestricted);
        if (this.mPhone.getSimulatedRadioControl() != null) {
            apnContext.setState(DctConstants.State.CONNECTED);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getDataProfileType());
            log("trySetupData: X We're on the simulator; assuming connected retValue=true");
            return true;
        }
        this.mPhone.getServiceStateTracker().getDesiredPowerState();
        if (!SUPPORT_MPDN && !isAnyActiveApnContextHandlesType(apnContext.getDataProfileType())) {
            if (disconnectOneLowerPriorityCall(apnContext.getDataProfileType())) {
                log("Lower/Equal priority call disconnected.");
                return false;
            }
            if (isHigherPriorityDataCallActive(apnContext.getDataProfileType())) {
                log("Higher priority call active. Ignoring setup data call request.");
                return false;
            }
        }
        boolean enableMmsData = false;
        if (apnContext.getDataProfileType().equals("mms")) {
            enableMmsData = this.mPhone.getContext().getResources().getBoolean(R.bool.config_bluetooth_sco_off_call);
        }
        if (apnContext.isConnectable() && isDataAllowed(apnContext) && getAnyDataEnabled(enableMmsData) && !isEmergency()) {
            if (apnContext.getState() == DctConstants.State.FAILED) {
                log("trySetupData: make a FAILED ApnContext IDLE so its reusable");
                apnContext.setState(DctConstants.State.IDLE);
            }
            int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
            if (apnContext.getState() == DctConstants.State.IDLE) {
                ArrayList<DataProfile> waitingDps = buildWaitingApns(apnContext.getDataProfileType(), radioTech);
                if (waitingDps.isEmpty()) {
                    notifyNoData(DcFailCause.MISSING_UNKNOWN_APN, apnContext);
                    notifyOffApnsOfAvailability(apnContext.getReason());
                    log("trySetupData: X No APN found retValue=false");
                    return false;
                }
                apnContext.setWaitingDataProfiles(waitingDps);
                log("trySetupData: Create from mAllDps : " + apnListToString(this.mAllDps));
            }
            log("trySetupData: call setupData, waitingApns : " + apnListToString(apnContext.getWaitingApns()));
            boolean retValue = setupData(apnContext, radioTech);
            notifyOffApnsOfAvailability(apnContext.getReason());
            log("trySetupData: X retValue=" + retValue);
            return retValue;
        }
        if (!apnContext.getDataProfileType().equals("default") && apnContext.isConnectable()) {
            this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getDataProfileType());
        }
        notifyOffApnsOfAvailability(apnContext.getReason());
        log("trySetupData: X apnContext not 'ready' retValue=false");
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void notifyOffApnsOfAvailability(String reason) {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if ((!this.mAttached.get() && this.mOosIsDisconnect) || !apnContext.isReady()) {
                this.mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(), apnContext.getDataProfileType(), PhoneConstants.DataState.DISCONNECTED);
            }
        }
    }

    protected boolean cleanUpAllConnections(boolean tearDown, String reason) {
        log("cleanUpAllConnections: tearDown=" + tearDown + " reason=" + reason);
        boolean didDisconnect = false;
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                didDisconnect = true;
            }
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }
        stopNetStatPoll();
        stopDataStallAlarm();
        this.mRequestedApnType = "default";
        return didDisconnect;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onCleanUpAllConnections(String cause) {
        cleanUpAllConnections(true, cause);
    }

    protected void cleanUpConnection(boolean tearDown, ApnContext apnContext) {
        DataProfile dunSetting;
        if (apnContext == null) {
            log("cleanUpConnection: apn context is null");
            return;
        }
        DcAsyncChannel dcac = apnContext.getDcAc();
        log("cleanUpConnection: E tearDown=" + tearDown + " reason=" + apnContext.getReason() + " apnContext=" + apnContext);
        if (tearDown) {
            if (apnContext.isDisconnected()) {
                apnContext.setState(DctConstants.State.IDLE);
                if (!apnContext.isReady()) {
                    if (dcac != null) {
                        dcac.tearDown(apnContext, "", null);
                    }
                    apnContext.setDataConnectionAc(null);
                }
            } else if (dcac != null) {
                if (apnContext.getState() != DctConstants.State.DISCONNECTING) {
                    boolean disconnectAll = false;
                    if ("dun".equals(apnContext.getDataProfileType()) && (dunSetting = fetchDunApn()) != null && dunSetting.equals(apnContext.getDataProfile())) {
                        log("tearing down dedicated DUN connection");
                        disconnectAll = true;
                    }
                    log("cleanUpConnection: tearing down" + (disconnectAll ? " all" : ""));
                    Message msg = obtainMessage(270351, apnContext);
                    if (disconnectAll) {
                        apnContext.getDcAc().tearDownAll(apnContext.getReason(), msg);
                    } else {
                        apnContext.getDcAc().tearDown(apnContext, apnContext.getReason(), msg);
                    }
                    apnContext.setState(DctConstants.State.DISCONNECTING);
                }
            } else {
                apnContext.setState(DctConstants.State.IDLE);
                this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getDataProfileType());
            }
        } else {
            if (dcac != null) {
                dcac.reqReset();
            }
            apnContext.setState(DctConstants.State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getDataProfileType());
            apnContext.setDataConnectionAc(null);
        }
        if (this.mOmhDpt != null) {
            this.mOmhDpt.clearActiveDataProfile();
        }
        if (dcac != null) {
            cancelReconnectAlarm(apnContext);
        }
        log("cleanUpConnection: X tearDown=" + tearDown + " reason=" + apnContext.getReason() + " apnContext=" + apnContext + " dcac=" + apnContext.getDcAc());
    }

    protected void cancelReconnectAlarm(ApnContext apnContext) {
        PendingIntent intent;
        if (apnContext != null && (intent = apnContext.getReconnectIntent()) != null) {
            AlarmManager am = (AlarmManager) this.mPhone.getContext().getSystemService("alarm");
            am.cancel(intent);
            apnContext.setReconnectIntent(null);
        }
    }

    private String[] parseTypes(String types) {
        if (types == null || types.equals("")) {
            String[] result = {"*"};
            return result;
        }
        return types.split(",");
    }

    private boolean imsiMatches(String imsiDB, String imsiSIM) {
        int len = imsiDB.length();
        if (len <= 0 || len > imsiSIM.length()) {
            return false;
        }
        for (int idx = 0; idx < len; idx++) {
            char c = imsiDB.charAt(idx);
            if (c != 'x' && c != 'X' && c != imsiSIM.charAt(idx)) {
                return false;
            }
        }
        return true;
    }

    private boolean mvnoMatches(IccRecords r, String mvno_type, String mvno_match_data) {
        if (mvno_type.equalsIgnoreCase("spn")) {
            if (r.getServiceProviderName() != null && r.getServiceProviderName().equalsIgnoreCase(mvno_match_data)) {
                return true;
            }
        } else if (mvno_type.equalsIgnoreCase("imsi")) {
            String imsiSIM = r.getIMSI();
            if (imsiSIM != null && imsiMatches(mvno_match_data, imsiSIM)) {
                return true;
            }
        } else if (mvno_type.equalsIgnoreCase("gid")) {
            String gid1 = r.getGid1();
            int mvno_match_data_length = mvno_match_data.length();
            if (gid1 != null && gid1.length() >= mvno_match_data_length && gid1.substring(0, mvno_match_data_length).equalsIgnoreCase(mvno_match_data)) {
                return true;
            }
        }
        return false;
    }

    private ApnSetting makeApnSetting(Cursor cursor) {
        String[] types = parseTypes(cursor.getString(cursor.getColumnIndexOrThrow("type")));
        ApnSetting apn = new ApnSetting(cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.MmsSms.WordsTable.ID)), cursor.getString(cursor.getColumnIndexOrThrow("numeric")), cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.APN)), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROXY))), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PORT)), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSC))), NetworkUtils.trimV4AddrZeros(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPROXY))), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPORT)), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.USER)), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PASSWORD)), cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.AUTH_TYPE)), types, cursor.getString(cursor.getColumnIndexOrThrow("protocol")), cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.ROAMING_PROTOCOL)), cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.CARRIER_ENABLED)) == 1, cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER)));
        return apn;
    }

    private ArrayList<DataProfile> createApnList(Cursor cursor) {
        ArrayList<DataProfile> result = new ArrayList<>();
        IccRecords r = this.mIccRecords.get();
        if (cursor.moveToFirst()) {
            String mvnoType = null;
            String mvnoMatchData = null;
            do {
                String cursorMvnoType = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_TYPE));
                String cursorMvnoMatchData = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_MATCH_DATA));
                if (mvnoType != null) {
                    if (mvnoType.equals(cursorMvnoType) && mvnoMatchData.equals(cursorMvnoMatchData)) {
                        result.add(makeApnSetting(cursor));
                    }
                } else if (mvnoMatches(r, cursorMvnoType, cursorMvnoMatchData)) {
                    result.clear();
                    mvnoType = cursorMvnoType;
                    mvnoMatchData = cursorMvnoMatchData;
                    result.add(makeApnSetting(cursor));
                } else if (cursorMvnoType.equals("")) {
                    result.add(makeApnSetting(cursor));
                }
            } while (cursor.moveToNext());
        }
        log("createApnList: X result=" + result);
        return result;
    }

    private boolean dataConnectionNotInUse(DcAsyncChannel dcac) {
        log("dataConnectionNotInUse: check if dcac is inuse dcac=" + dcac);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getDcAc() == dcac) {
                log("dataConnectionNotInUse: in use by apnContext=" + apnContext);
                return false;
            }
        }
        log("dataConnectionNotInUse: tearDownAll");
        dcac.tearDownAll("No connection", null);
        log("dataConnectionNotInUse: not in use return true");
        return true;
    }

    private DcAsyncChannel findFreeDataConnection() {
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            if (dcac.isInactiveSync() && dataConnectionNotInUse(dcac)) {
                log("findFreeDataConnection: found free DataConnection= dcac=" + dcac);
                return dcac;
            }
        }
        log("findFreeDataConnection: NO free DataConnection");
        return null;
    }

    private boolean setupData(ApnContext apnContext, int radioTech) {
        DataProfile dcacApnSetting;
        log("setupData: apnContext=" + apnContext);
        int profileId = getApnProfileID(apnContext.getDataProfileType());
        DataProfile apnSetting = apnContext.getNextWaitingApn();
        if (apnSetting == null) {
            log("setupData: return for no apn found!");
            return false;
        }
        DcAsyncChannel dcac = checkForCompatibleConnectedApnContext(apnContext);
        if (dcac != null && (dcacApnSetting = dcac.getApnSettingSync()) != null) {
            apnSetting = dcacApnSetting;
        }
        if (dcac == null) {
            if (isOnlySingleDcAllowed(radioTech)) {
                if (isHigherPriorityApnContextActive(apnContext)) {
                    log("setupData: Higher priority ApnContext active.  Ignoring call");
                    return false;
                }
                if (cleanUpAllConnections(true, Phone.REASON_SINGLE_PDN_ARBITRATION)) {
                    log("setupData: Some calls are disconnecting first.  Wait and retry");
                    return false;
                }
                log("setupData: Single pdp. Continue setting up data call.");
            }
            dcac = findFreeDataConnection();
            if (dcac == null) {
                dcac = createDataConnection();
            }
            if (dcac == null) {
                log("setupData: No free DataConnection and couldn't create one, WEIRD");
                return false;
            }
        }
        log("setupData: dcac=" + dcac + " apnSetting=" + apnSetting);
        apnContext.setDataConnectionAc(dcac);
        apnContext.setDataProfile(apnSetting);
        apnContext.setState(DctConstants.State.CONNECTING);
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getDataProfileType());
        Message msg = obtainMessage();
        msg.what = 270336;
        msg.obj = apnContext;
        dcac.bringUp(apnContext, getInitialMaxRetry(), profileId, radioTech, msg);
        log("setupData: initing!");
        return true;
    }

    private void onApnChanged() {
        log("onApnChanged: tryRestartDataConnections");
        tryRestartDataConnections(Phone.REASON_APN_CHANGED);
    }

    private void tryRestartDataConnections(String reason) {
        DctConstants.State overallState = getOverallState();
        boolean isDisconnected = overallState == DctConstants.State.IDLE || overallState == DctConstants.State.FAILED;
        updateCurrentCarrierInProvider();
        if (this.mOmhDpt != null) {
            log("OMH: tryRestartDataConnections(): calling loadProfiles()");
            this.mOmhDpt.loadProfiles();
            return;
        }
        log("tryRestartDataConnections: createAllApnList and cleanUpAllConnections");
        createAllApnList();
        setInitialAttachApn();
        cleanUpAllConnections(isDisconnected ? false : true, reason);
        if (isDisconnected) {
            setupDataOnConnectableApns(reason);
        }
    }

    private void onModemDataProfileReady() {
        if (this.mState == DctConstants.State.FAILED) {
            cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
        }
        if (isDisconnected()) {
            log("OMH: onModemDataProfileReady(): Setting up data call");
            setupDataOnConnectableApns(Phone.REASON_SIM_LOADED);
        }
    }

    private DcAsyncChannel findDataConnectionAcByCid(int cid) {
        for (DcAsyncChannel dcac : this.mDataConnectionAcHashMap.values()) {
            if (dcac.getCidSync() == cid) {
                return dcac;
            }
        }
        return null;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void gotoIdleAndNotifyDataConnection(String reason) {
        log("gotoIdleAndNotifyDataConnection: reason=" + reason);
        notifyDataConnection(reason);
        this.mActiveDp = null;
    }

    private boolean isAnyActiveApnContextHandlesType(String apnType) {
        DataProfile apnSetting;
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!apnContext.isDisconnected() && (apnSetting = apnContext.getDataProfile()) != null && apnSetting.canHandleType(apnType)) {
                log("isAnyActiveApnContextHandlesType:  - apnContext = [" + apnContext + "] can handle apnType=" + apnType);
                return true;
            }
        }
        return false;
    }

    private boolean isHigherPriorityDataCallActive(String apnType) {
        ApnContext apnContext = this.mApnContexts.get(apnType);
        ApnContext[] arr$ = (ApnContext[]) getPrioritySortedApnContextList().toArray(new ApnContext[0]);
        for (ApnContext apnContextEntry : arr$) {
            if (apnContextEntry.isHigherPriority(apnContext) && (apnContextEntry.getState() == DctConstants.State.CONNECTED || apnContextEntry.getState() == DctConstants.State.CONNECTING)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHigherPriorityApnContextActive(ApnContext apnContext) {
        for (ApnContext otherContext : this.mPrioritySortedApnContexts) {
            if (apnContext.getDataProfileType().equalsIgnoreCase(otherContext.getDataProfileType())) {
                return false;
            }
            if (otherContext.isEnabled() && otherContext.getState() != DctConstants.State.FAILED) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnlySingleDcAllowed(int rilRadioTech) {
        int[] singleDcRats = this.mPhone.getContext().getResources().getIntArray(R.array.config_biometric_sensors);
        boolean onlySingleDcAllowed = false;
        if (Build.IS_DEBUGGABLE && SystemProperties.getBoolean("persist.telephony.test.singleDc", false)) {
            onlySingleDcAllowed = true;
        }
        if (singleDcRats != null) {
            for (int i = 0; i < singleDcRats.length && !onlySingleDcAllowed; i++) {
                if (rilRadioTech == singleDcRats[i]) {
                    onlySingleDcAllowed = true;
                }
            }
        }
        log("isOnlySingleDcAllowed(" + rilRadioTech + "): " + onlySingleDcAllowed);
        return true;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean disconnectOneLowerPriorityCall(String apnType) {
        boolean disconnect = false;
        ApnContext apnContext = this.mApnContexts.get(apnType);
        ApnContext[] arr$ = (ApnContext[]) getPrioritySortedApnContextList().toArray(new ApnContext[0]);
        for (ApnContext apnContextEntry : arr$) {
            if (!apnContextEntry.isDisconnected() && apnContextEntry.isLowerPriority(apnContext)) {
                disconnect = true;
                apnContextEntry.setReason(Phone.REASON_SINGLE_PDN_ARBITRATION);
                cleanUpConnection(true, apnContextEntry);
                break;
            }
        }
        log("disconnectOneLowerPriorityCall:" + apnContext.getDataProfileType() + " " + disconnect);
        return disconnect;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void restartRadio() {
        log("restartRadio: ************TURN OFF RADIO**************");
        cleanUpAllConnections(true, Phone.REASON_RADIO_TURNED_OFF);
        this.mPhone.getServiceStateTracker().powerOffRadioSafely(this);
        int reset = Integer.parseInt(SystemProperties.get("net.ppp.reset-by-timeout", "0"));
        SystemProperties.set("net.ppp.reset-by-timeout", String.valueOf(reset + 1));
    }

    private boolean retryAfterDisconnected(ApnContext apnContext) {
        String reason = apnContext.getReason();
        if (!Phone.REASON_RADIO_TURNED_OFF.equals(reason) && ((!isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology()) || !isHigherPriorityApnContextActive(apnContext)) && (SUPPORT_MPDN || !Phone.REASON_SINGLE_PDN_ARBITRATION.equals(reason)))) {
            return true;
        }
        return false;
    }

    private void startAlarmForReconnect(int delay, ApnContext apnContext) {
        String apnType = apnContext.getDataProfileType();
        Intent intent = new Intent("com.android.internal.telephony.data-reconnect." + apnType);
        intent.putExtra("reconnect_alarm_extra_reason", apnContext.getReason());
        intent.putExtra("reconnect_alarm_extra_type", apnType);
        log("startAlarmForReconnect: delay=" + delay + " action=" + intent.getAction() + " apn=" + apnContext);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(alarmIntent);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) delay), alarmIntent);
    }

    private void startAlarmForRestartTrySetup(int delay, ApnContext apnContext) {
        String apnType = apnContext.getDataProfileType();
        Intent intent = new Intent("com.android.internal.telephony.data-restart-trysetup." + apnType);
        intent.putExtra("restart_trysetup_alarm_extra_type", apnType);
        log("startAlarmForRestartTrySetup: delay=" + delay + " action=" + intent.getAction() + " apn=" + apnContext);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this.mPhone.getContext(), 0, intent, 134217728);
        apnContext.setReconnectIntent(alarmIntent);
        this.mAlarmManager.set(2, SystemClock.elapsedRealtime() + ((long) delay), alarmIntent);
    }

    private void notifyNoData(DcFailCause lastFailCauseCode, ApnContext apnContext) {
        log("notifyNoData: type=" + apnContext.getDataProfileType());
        if (lastFailCauseCode.isPermanentFail() && !apnContext.getDataProfileType().equals("default")) {
            this.mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getDataProfileType());
        }
    }

    private void onRecordsLoaded() {
        log("onRecordsLoaded");
        updateCurrentCarrierInProvider();
        if (this.mOmhDpt != null) {
            log("OMH: onRecordsLoaded(): calling loadProfiles()");
            this.mOmhDpt.loadProfiles();
            if (this.mPhone.mCi.getRadioState().isOn()) {
                log("onRecordsLoaded: notifying data availability");
                notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
                return;
            }
            return;
        }
        log("onRecordsLoaded: createAllApnList");
        createAllApnList();
        setInitialAttachApn();
        if (this.mPhone.mCi.getRadioState().isOn()) {
            log("onRecordsLoaded: notifying data availability");
            notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
        }
        boolean isDisconnected = isDisconnected();
        log("onRecordsLoaded: isDisconnected = " + isDisconnected);
        if (isDisconnected) {
            setupDataOnConnectableApns(Phone.REASON_SIM_LOADED);
        }
    }

    private void onNvReady() {
        log("onNvReady");
        updateCurrentCarrierInProvider();
        createAllApnList();
        setupDataOnConnectableApns(Phone.REASON_NV_READY);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onSetDependencyMet(String apnType, boolean met) {
        ApnContext apnContext;
        if (!"hipri".equals(apnType)) {
            ApnContext apnContext2 = this.mApnContexts.get(apnType);
            if (apnContext2 == null) {
                loge("onSetDependencyMet: ApnContext not found in onSetDependencyMet(" + apnType + ", " + met + ")");
                return;
            }
            applyNewState(apnContext2, apnContext2.isEnabled(), met);
            if (!"default".equals(apnType) || (apnContext = this.mApnContexts.get("hipri")) == null) {
                return;
            }
            applyNewState(apnContext, apnContext.isEnabled(), met);
        }
    }

    private void applyNewState(ApnContext apnContext, boolean enabled, boolean met) {
        boolean cleanup = false;
        boolean trySetup = false;
        log("applyNewState(" + apnContext.getDataProfileType() + ", " + enabled + "(" + apnContext.isEnabled() + "), " + met + "(" + apnContext.getDependencyMet() + "))");
        if (apnContext.isReady()) {
            if (enabled && met) {
                DctConstants.State state = apnContext.getState();
                switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[state.ordinal()]) {
                    case 1:
                    case 2:
                    case 4:
                    case 6:
                        log("applyNewState: 'ready' so return");
                        break;
                    case 3:
                    case 5:
                    case 7:
                        trySetup = true;
                        apnContext.setReason(Phone.REASON_DATA_ENABLED);
                    default:
                        cleanup = true;
                        break;
                }
            }
            if (!enabled) {
                apnContext.setReason(Phone.REASON_DATA_DISABLED);
            } else {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_UNMET);
            }
            cleanup = true;
        } else if (enabled && met) {
            if (apnContext.isEnabled()) {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_MET);
            } else {
                apnContext.setReason(Phone.REASON_DATA_ENABLED);
            }
            if (apnContext.getState() == DctConstants.State.FAILED) {
                apnContext.setState(DctConstants.State.IDLE);
            }
            trySetup = true;
        }
        apnContext.setEnabled(enabled);
        apnContext.setDependencyMet(met);
        if (cleanup) {
            cleanUpConnection(true, apnContext);
        }
        if (trySetup) {
            trySetupData(apnContext);
        }
    }

    private DcAsyncChannel checkForCompatibleConnectedApnContext(ApnContext apnContext) {
        String apnType = apnContext.getDataProfileType();
        DataProfile dunSetting = null;
        if ("dun".equals(apnType)) {
            dunSetting = fetchDunApn();
        }
        log("checkForCompatibleConnectedApnContext: apnContext=" + apnContext);
        DcAsyncChannel potentialDcac = null;
        ApnContext potentialApnCtx = null;
        for (ApnContext curApnCtx : this.mApnContexts.values()) {
            DcAsyncChannel curDcac = curApnCtx.getDcAc();
            if (curDcac != null) {
                DataProfile apnSetting = curApnCtx.getDataProfile();
                if (dunSetting != null) {
                    if (dunSetting.equals(apnSetting)) {
                        switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[curApnCtx.getState().ordinal()]) {
                            case 1:
                                log("checkForCompatibleConnectedApnContext: found dun conn=" + curDcac + " curApnCtx=" + curApnCtx);
                                return curDcac;
                            case 3:
                            case 4:
                                potentialDcac = curDcac;
                                potentialApnCtx = curApnCtx;
                                break;
                        }
                    } else {
                        continue;
                    }
                } else if (apnSetting != null && apnSetting.canHandleType(apnType)) {
                    switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[curApnCtx.getState().ordinal()]) {
                        case 1:
                            log("checkForCompatibleConnectedApnContext: found canHandle conn=" + curDcac + " curApnCtx=" + curApnCtx);
                            return curDcac;
                        case 3:
                        case 4:
                            potentialDcac = curDcac;
                            potentialApnCtx = curApnCtx;
                            break;
                    }
                }
            }
        }
        if (potentialDcac != null) {
            log("checkForCompatibleConnectedApnContext: found potential conn=" + potentialDcac + " curApnCtx=" + potentialApnCtx);
            return potentialDcac;
        }
        log("checkForCompatibleConnectedApnContext: NO conn apnContext=" + apnContext);
        return null;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onEnableApn(int apnId, int enabled) {
        ApnContext apnContext = this.mApnContexts.get(apnIdToType(apnId));
        if (apnContext == null) {
            loge("onEnableApn(" + apnId + ", " + enabled + "): NO ApnContext");
        } else {
            log("onEnableApn: apnContext=" + apnContext + " call applyNewState");
            applyNewState(apnContext, enabled == 1, apnContext.getDependencyMet());
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean onTrySetupData(String reason) {
        log("onTrySetupData: reason=" + reason);
        setupDataOnConnectableApns(reason);
        return true;
    }

    protected boolean onTrySetupData(ApnContext apnContext) {
        log("onTrySetupData: apnContext=" + apnContext);
        return trySetupData(apnContext);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onRoamingOff() {
        log("onRoamingOff");
        if (this.mUserDataEnabled) {
            if (!getDataOnRoamingEnabled()) {
                notifyOffApnsOfAvailability(Phone.REASON_ROAMING_OFF);
                setupDataOnConnectableApns(Phone.REASON_ROAMING_OFF);
            } else {
                notifyDataConnection(Phone.REASON_ROAMING_OFF);
            }
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onRoamingOn() {
        if (this.mUserDataEnabled) {
            if (getDataOnRoamingEnabled()) {
                log("onRoamingOn: setup data on roaming");
                setupDataOnConnectableApns(Phone.REASON_ROAMING_ON);
                notifyDataConnection(Phone.REASON_ROAMING_ON);
            } else {
                log("onRoamingOn: Tear down data connection on roaming.");
                cleanUpAllConnections(true, Phone.REASON_ROAMING_ON);
                notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
            }
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onRadioAvailable() {
        log("onRadioAvailable");
        if (this.mPhone.getSimulatedRadioControl() != null) {
            notifyDataConnection(null);
            log("onRadioAvailable: We're on the simulator; assuming data is connected");
        }
        IccRecords r = this.mIccRecords.get();
        if (r != null && r.getRecordsLoaded()) {
            notifyOffApnsOfAvailability(null);
        }
        if (getOverallState() != DctConstants.State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onRadioOffOrNotAvailable() {
        this.mReregisterOnReconnectFailure = false;
        if (this.mPhone.getSimulatedRadioControl() != null) {
            log("We're on the simulator; assuming radio off is meaningless");
        } else {
            log("onRadioOffOrNotAvailable: is off and clean up all connections");
            cleanUpAllConnections(false, Phone.REASON_RADIO_TURNED_OFF);
        }
        notifyOffApnsOfAvailability(null);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void completeConnection(ApnContext apnContext) {
        apnContext.isProvisioningApn();
        log("completeConnection: successful, notify the world apnContext=" + apnContext);
        if (this.mIsProvisioning && !TextUtils.isEmpty(this.mProvisioningUrl)) {
            log("completeConnection: MOBILE_PROVISIONING_ACTION url=" + this.mProvisioningUrl);
            Intent newIntent = Intent.makeMainSelectorActivity("android.intent.action.MAIN", "android.intent.category.APP_BROWSER");
            newIntent.setData(Uri.parse(this.mProvisioningUrl));
            newIntent.setFlags(272629760);
            try {
                this.mPhone.getContext().startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                loge("completeConnection: startActivityAsUser failed" + e);
            }
        }
        this.mIsProvisioning = false;
        this.mProvisioningUrl = null;
        this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getDataProfileType());
        startNetStatPoll();
        startDataStallAlarm(false);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onDataSetupComplete(AsyncResult ar) {
        DcFailCause dcFailCause = DcFailCause.UNKNOWN;
        boolean handleError = false;
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) ar.userObj;
            if (ar.exception == null) {
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac == null) {
                    log("onDataSetupComplete: no connection to DC, handle as error");
                    DcFailCause cause = DcFailCause.CONNECTION_TO_DATACONNECTIONAC_BROKEN;
                    handleError = true;
                } else {
                    DataProfile apn = apnContext.getDataProfile();
                    log("onDataSetupComplete: success apn=" + (apn == null ? "unknown" : apn.apn));
                    if (apn != null && apn.proxy != null && apn.proxy.length() != 0) {
                        try {
                            String port = apn.port;
                            if (TextUtils.isEmpty(port)) {
                                port = "8080";
                            }
                            ProxyProperties proxy = new ProxyProperties(apn.proxy, Integer.parseInt(port), (String) null);
                            dcac.setLinkPropertiesHttpProxySync(proxy);
                        } catch (NumberFormatException e) {
                            loge("onDataSetupComplete: NumberFormatException making ProxyProperties (" + apn.port + "): " + e);
                        }
                    }
                    if (TextUtils.equals(apnContext.getDataProfileType(), "default")) {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "true");
                        if (this.mCanSetPreferApn && this.mPreferredDp == null) {
                            log("onDataSetupComplete: PREFERED APN is null");
                            this.mPreferredDp = apn;
                            if (this.mPreferredDp != null) {
                                setPreferredApn(this.mPreferredDp.id);
                            }
                        }
                    } else {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                    }
                    apnContext.setState(DctConstants.State.CONNECTED);
                    boolean isProvApn = apnContext.isProvisioningApn();
                    if (!isProvApn || this.mIsProvisioning) {
                        completeConnection(apnContext);
                    } else {
                        log("onDataSetupComplete: successful, BUT send connected to prov apn as mIsProvisioning:" + this.mIsProvisioning + " == false && (isProvisioningApn:" + isProvApn + " == true");
                        Intent intent = new Intent("android.intent.action.DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN");
                        intent.putExtra(Telephony.Carriers.APN, apnContext.getDataProfile().apn);
                        intent.putExtra("apnType", apnContext.getDataProfileType());
                        String apnType = apnContext.getDataProfileType();
                        LinkProperties linkProperties = getLinkProperties(apnType);
                        if (linkProperties != null) {
                            intent.putExtra("linkProperties", linkProperties);
                            String iface = linkProperties.getInterfaceName();
                            if (iface != null) {
                                intent.putExtra("iface", iface);
                            }
                        }
                        LinkCapabilities linkCapabilities = getLinkCapabilities(apnType);
                        if (linkCapabilities != null) {
                            intent.putExtra("linkCapabilities", (Parcelable) linkCapabilities);
                        }
                        this.mPhone.getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
                    }
                    log("onDataSetupComplete: SETUP complete type=" + apnContext.getDataProfileType() + ", reason:" + apnContext.getReason());
                }
            } else {
                DcFailCause cause2 = (DcFailCause) ar.result;
                DataProfile apn2 = apnContext.getDataProfile();
                Object[] objArr = new Object[2];
                objArr[0] = apn2 == null ? "unknown" : apn2.apn;
                objArr[1] = cause2;
                log(String.format("onDataSetupComplete: error apn=%s cause=%s", objArr));
                if (cause2 == null) {
                    cause2 = DcFailCause.UNKNOWN;
                }
                if (cause2.isEventLoggable()) {
                    int cid = getCellLocationId();
                    EventLog.writeEvent(EventLogTags.PDP_SETUP_FAIL, Integer.valueOf(cause2.ordinal()), Integer.valueOf(cid), Integer.valueOf(TelephonyManager.getDefault().getNetworkType()));
                }
                if (cause2.isPermanentFail()) {
                    apnContext.decWaitingApnsPermFailCount();
                }
                apnContext.removeWaitingApn(apnContext.getDataProfile());
                log(String.format("onDataSetupComplete: WaitingApns.size=%d WaitingApnsPermFailureCountDown=%d", Integer.valueOf(apnContext.getWaitingApns().size()), Integer.valueOf(apnContext.getWaitingApnsPermFailCount())));
                handleError = true;
            }
            if (handleError) {
                onDataSetupCompleteError(ar);
                return;
            }
            return;
        }
        throw new RuntimeException("onDataSetupComplete: No apnContext");
    }

    private int getApnDelay() {
        return this.mFailFast ? SystemProperties.getInt("persist.radio.apn_ff_delay", 3000) : SystemProperties.getInt("persist.radio.apn_delay", 20000);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onDataSetupCompleteError(AsyncResult ar) {
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) ar.userObj;
            if (apnContext.getWaitingApns().isEmpty()) {
                apnContext.setState(DctConstants.State.FAILED);
                this.mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, apnContext.getDataProfileType());
                apnContext.setDataConnectionAc(null);
                if (apnContext.getWaitingApnsPermFailCount() == 0) {
                    log("onDataSetupCompleteError: All APN's had permanent failures, stop retrying");
                    return;
                }
                int delay = getApnDelay();
                log("onDataSetupCompleteError: Not all APN's had permanent failures delay=" + delay);
                startAlarmForRestartTrySetup(delay, apnContext);
                return;
            }
            log("onDataSetupCompleteError: Try next APN");
            apnContext.setState(DctConstants.State.SCANNING);
            startAlarmForReconnect(getApnDelay(), apnContext);
            return;
        }
        throw new RuntimeException("onDataSetupCompleteError: No apnContext");
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onDisconnectDone(int connId, AsyncResult ar) {
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) ar.userObj;
            log("onDisconnectDone: EVENT_DISCONNECT_DONE apnContext=" + apnContext);
            apnContext.setState(DctConstants.State.IDLE);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getDataProfileType());
            if (isDisconnected() && this.mPhone.getServiceStateTracker().processPendingRadioPowerOffAfterDataOff()) {
                log("onDisconnectDone: radio will be turned off, no retries");
                apnContext.setDataProfile(null);
                apnContext.setDataConnectionAc(null);
                return;
            }
            if (this.mAttached.get() && apnContext.isReady() && retryAfterDisconnected(apnContext)) {
                if (Objects.equal(apnContext.getReason(), Phone.REASON_NW_TYPE_CHANGED)) {
                    if (isDisconnected()) {
                        log("onDisconnectDone: Cleanup due to NW type changed done. Restart all apns");
                        setupDataOnConnectableApns(Phone.REASON_NW_TYPE_CHANGED);
                    }
                } else {
                    SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                    log("onDisconnectDone: attached, ready and retry after disconnect");
                    startAlarmForReconnect(getApnDelay(), apnContext);
                }
            } else {
                boolean restartRadioAfterProvisioning = this.mPhone.getContext().getResources().getBoolean(R.bool.config_batteryStatsResetOnUnplugAfterSignificantCharge);
                if (apnContext.isProvisioningApn() && restartRadioAfterProvisioning) {
                    log("onDisconnectDone: restartRadio after provisioning");
                    restartRadio();
                }
                apnContext.setDataProfile(null);
                apnContext.setDataConnectionAc(null);
                if (isOnlySingleDcAllowed(this.mPhone.getServiceState().getRilDataRadioTechnology())) {
                    log("onDisconnectDone: isOnlySigneDcAllowed true so setup single apn");
                    if (isDisconnected()) {
                        setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
                    } else {
                        log("onDisconnectDone: Wait for all apn contexts to be disconnected");
                    }
                } else {
                    log("onDisconnectDone: not retrying");
                }
            }
            if (!SUPPORT_MPDN && isDisconnected()) {
                setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
                return;
            }
            return;
        }
        loge("onDisconnectDone: Invalid ar in onDisconnectDone, ignore");
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onDisconnectDcRetrying(int connId, AsyncResult ar) {
        if (ar.userObj instanceof ApnContext) {
            ApnContext apnContext = (ApnContext) ar.userObj;
            apnContext.setState(DctConstants.State.RETRYING);
            log("onDisconnectDcRetrying: apnContext=" + apnContext);
            this.mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getDataProfileType());
            return;
        }
        loge("onDisconnectDcRetrying: Invalid ar in onDisconnectDone, ignore");
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onVoiceCallStarted() {
        log("onVoiceCallStarted");
        this.mInVoiceCall = true;
        if (isConnected() && !this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            log("onVoiceCallStarted stop polling");
            stopNetStatPoll();
            stopDataStallAlarm();
            notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onVoiceCallEnded() {
        log("onVoiceCallEnded");
        this.mInVoiceCall = false;
        if (isConnected()) {
            if (!this.mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                startNetStatPoll();
                startDataStallAlarm(false);
                notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
            } else {
                resetPollStats();
            }
        }
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void onCleanUpConnection(boolean tearDown, int apnId, String reason) {
        log("onCleanUpConnection");
        ApnContext apnContext = this.mApnContexts.get(apnIdToType(apnId));
        if (apnContext != null) {
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean isConnected() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (apnContext.getState() == DctConstants.State.CONNECTED) {
                return true;
            }
        }
        return false;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public boolean isDisconnected() {
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                return false;
            }
        }
        return true;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void notifyDataConnection(String reason) {
        log("notifyDataConnection: reason=" + reason);
        for (ApnContext apnContext : this.mApnContexts.values()) {
            if (this.mAttached.get() || !this.mOosIsDisconnect) {
                if (apnContext.isReady()) {
                    log("notifyDataConnection: type:" + apnContext.getDataProfileType());
                    this.mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(), apnContext.getDataProfileType());
                }
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    private boolean isNvSubscription() {
        int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
        return this.mCdmaSsm != null && UiccController.getFamilyFromRadioTechnology(radioTech) == 2 && this.mCdmaSsm.getCdmaSubscriptionSource() == 1;
    }

    private String getOperatorNumeric() {
        String result;
        if (isNvSubscription()) {
            result = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
            log("getOperatorNumberic - returning from NV: " + result);
        } else {
            IccRecords r = this.mIccRecords.get();
            result = r != null ? r.getOperatorNumeric() : "";
            log("getOperatorNumberic - returning from card: " + result);
        }
        return result == null ? "" : result;
    }

    private void createAllApnList() {
        this.mAllDps.clear();
        String operator = getOperatorNumeric();
        if (operator != null && !operator.isEmpty()) {
            String selection = ("numeric = '" + operator + "'") + " and carrier_enabled = 1";
            log("createAllApnList: selection=" + selection);
            Cursor cursor = this.mPhone.getContext().getContentResolver().query(Telephony.Carriers.CONTENT_URI, null, selection, null, null);
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    this.mAllDps = createApnList(cursor);
                }
                cursor.close();
            }
        }
        if (this.mAllDps.isEmpty()) {
            int radioTech = this.mPhone.getServiceState().getRilDataRadioTechnology();
            if (!CdmaDataProfileTracker.OMH_ENABLED && UiccController.getFamilyFromRadioTechnology(radioTech) == 2) {
                addDummyDataProfiles(operator);
            }
        }
        if (this.mAllDps.isEmpty()) {
            log("createAllApnList: No APN found for carrier: " + operator);
            this.mPreferredDp = null;
        } else {
            this.mPreferredDp = getPreferredApn();
            if (this.mPreferredDp != null && !this.mPreferredDp.numeric.equals(operator)) {
                this.mPreferredDp = null;
                setPreferredApn(-1);
            }
            log("createAllApnList: mPreferredApn=" + this.mPreferredDp);
        }
        log("createAllApnList: X mAllDps=" + this.mAllDps);
    }

    private void addDummyDataProfiles(String operator) {
        log("createAllApnList: Creating dummy apn for cdma operator:" + operator);
        String[] defaultApnTypes = {"default", "mms", "supl", "hipri", "fota", "ims", "cbs"};
        String[] dunApnTypes = {"dun"};
        ApnSetting apn = new ApnSetting(0, operator, null, null, null, null, null, null, null, null, null, 3, defaultApnTypes, PROPERTY_CDMA_IPPROTOCOL, PROPERTY_CDMA_ROAMING_IPPROTOCOL, true, 0);
        this.mAllDps.add(apn);
        ApnSetting apn2 = new ApnSetting(3, operator, null, null, null, null, null, null, null, null, null, 3, dunApnTypes, PROPERTY_CDMA_IPPROTOCOL, PROPERTY_CDMA_ROAMING_IPPROTOCOL, true, 0);
        this.mAllDps.add(apn2);
    }

    private DcAsyncChannel createDataConnection() {
        log("createDataConnection E");
        int id = this.mUniqueIdGenerator.getAndIncrement();
        DataConnection conn = DataConnection.makeDataConnection(this.mPhone, id, this, this.mDcTesterFailBringUpAll, this.mDcc);
        this.mDataConnections.put(Integer.valueOf(id), conn);
        DcAsyncChannel dcac = new DcAsyncChannel(conn, this.LOG_TAG);
        int status = dcac.fullyConnectSync(this.mPhone.getContext(), this, conn.getHandler());
        if (status == 0) {
            this.mDataConnectionAcHashMap.put(Integer.valueOf(dcac.getDataConnectionIdSync()), dcac);
        } else {
            loge("createDataConnection: Could not connect to dcac=" + dcac + " status=" + status);
        }
        log("createDataConnection() X id=" + id + " dc=" + conn);
        return dcac;
    }

    private void destroyDataConnections() {
        if (this.mDataConnections != null) {
            log("destroyDataConnections: clear mDataConnectionList");
            this.mDataConnections.clear();
        } else {
            log("destroyDataConnections: mDataConnecitonList is empty, ignore");
        }
    }

    /* JADX WARN: Code duplicated, block: B:50:0x01f7  */
    private ArrayList<DataProfile> buildWaitingApns(String requestedApnType, int radioTech) {
        boolean usePreferred;
        DataProfile dun;
        log("buildWaitingApns: E requestedApnType=" + requestedApnType);
        ArrayList<DataProfile> apnList = new ArrayList<>();
        if (requestedApnType.equals("dun") && (dun = fetchDunApn()) != null) {
            apnList.add(dun);
            log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + apnList);
        } else {
            String operator = getOperatorNumeric();
            try {
                usePreferred = !this.mPhone.getContext().getResources().getBoolean(R.bool.config_batterySdCardAccessibility);
            } catch (Resources.NotFoundException e) {
                log("buildWaitingApns: usePreferred NotFoundException set to true");
                usePreferred = true;
            }
            log("buildWaitingApns: usePreferred=" + usePreferred + " canSetPreferApn=" + this.mCanSetPreferApn + " mPreferredApn=" + this.mPreferredDp + " operator=" + operator + " radioTech=" + radioTech);
            if (usePreferred && this.mCanSetPreferApn && this.mPreferredDp != null && this.mPreferredDp.canHandleType(requestedApnType)) {
                log("buildWaitingApns: Preferred APN:" + operator + ":" + this.mPreferredDp.numeric + ":" + this.mPreferredDp);
                if (this.mPreferredDp.numeric != null && this.mPreferredDp.numeric.equals(operator)) {
                    if (this.mPreferredDp.bearer == 0 || this.mPreferredDp.bearer == radioTech) {
                        apnList.add(this.mPreferredDp);
                        log("buildWaitingApns: X added preferred apnList=" + apnList);
                    } else {
                        log("buildWaitingApns: no preferred APN");
                        setPreferredApn(-1);
                        this.mPreferredDp = null;
                    }
                } else {
                    log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    this.mPreferredDp = null;
                }
                if (this.mAllDps == null) {
                    loge("mAllDps is empty!");
                } else {
                    loge("mAllDps is empty!");
                }
                log("buildWaitingApns: X apnList=" + apnList);
            } else {
                if (this.mAllDps == null && !this.mAllDps.isEmpty()) {
                    log("buildWaitingApns: mAllDps=" + this.mAllDps);
                    for (DataProfile apn : this.mAllDps) {
                        log("buildWaitingApns: apn=" + apn);
                        if (apn.canHandleType(requestedApnType)) {
                            if (apn.bearer == 0 || apn.bearer == radioTech) {
                                log("buildWaitingApns: adding apn=" + apn.toString());
                                apnList.add(apn);
                            } else {
                                log("buildWaitingApns: bearer:" + apn.bearer + " != radioTech:" + radioTech);
                            }
                        } else {
                            log("buildWaitingApns: couldn't handle requesedApnType=" + requestedApnType);
                        }
                    }
                } else {
                    loge("mAllDps is empty!");
                }
                log("buildWaitingApns: X apnList=" + apnList);
            }
        }
        return apnList;
    }

    private String apnListToString(ArrayList<DataProfile> apns) {
        StringBuilder result = new StringBuilder();
        int size = apns.size();
        for (int i = 0; i < size; i++) {
            result.append('[').append(apns.get(i).toString()).append(']');
        }
        return result.toString();
    }

    private void setPreferredApn(int pos) {
        if (!this.mCanSetPreferApn) {
            log("setPreferredApn: X !canSEtPreferApn");
            return;
        }
        log("setPreferredApn: delete");
        ContentResolver resolver = this.mPhone.getContext().getContentResolver();
        resolver.delete(PREFERAPN_NO_UPDATE_URI, null, null);
        if (pos >= 0) {
            log("setPreferredApn: insert");
            ContentValues values = new ContentValues();
            values.put(APN_ID, Integer.valueOf(pos));
            resolver.insert(PREFERAPN_NO_UPDATE_URI, values);
        }
    }

    private DataProfile getPreferredApn() {
        if (this.mAllDps.isEmpty()) {
            log("getPreferredApn: X not found mAllDps.isEmpty");
            return null;
        }
        Cursor cursor = this.mPhone.getContext().getContentResolver().query(PREFERAPN_NO_UPDATE_URI, new String[]{Telephony.MmsSms.WordsTable.ID, "name", Telephony.Carriers.APN}, null, null, "name ASC");
        if (cursor != null) {
            this.mCanSetPreferApn = true;
        } else {
            this.mCanSetPreferApn = false;
        }
        log("getPreferredApn: mRequestedApnType=" + this.mRequestedApnType + " cursor=" + cursor + " cursor.count=" + (cursor != null ? cursor.getCount() : 0));
        if (this.mCanSetPreferApn && cursor.getCount() > 0) {
            cursor.moveToFirst();
            int pos = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.MmsSms.WordsTable.ID));
            for (DataProfile p : this.mAllDps) {
                log("getPreferredApn: apnSetting=" + p);
                if (p.id == pos && p.canHandleType(this.mRequestedApnType)) {
                    log("getPreferredApn: X found apnSetting" + p);
                    cursor.close();
                    return p;
                }
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        log("getPreferredApn: X not found");
        return null;
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase, android.os.Handler
    public void handleMessage(Message msg) {
        log("handleMessage msg=" + msg);
        if (!this.mPhone.mIsTheCurrentActivePhone || this.mIsDisposed) {
            loge("handleMessage: Ignore GSM msgs since GSM phone is inactive");
            return;
        }
        switch (msg.what) {
            case 270338:
                onRecordsLoaded();
                return;
            case 270339:
                if (msg.obj instanceof ApnContext) {
                    onTrySetupData((ApnContext) msg.obj);
                    return;
                } else if (msg.obj instanceof String) {
                    onTrySetupData((String) msg.obj);
                    return;
                } else {
                    loge("EVENT_TRY_SETUP request w/o apnContext or String");
                    return;
                }
            case 270345:
                onDataConnectionDetached();
                return;
            case 270352:
                onDataConnectionAttached();
                return;
            case 270354:
                doRecovery();
                return;
            case 270355:
                onApnChanged();
                return;
            case 270357:
            case 270376:
                if (onUpdateIcc()) {
                    log("onUpdateIcc: tryRestartDataConnections nwTypeChanged");
                    tryRestartDataConnections(Phone.REASON_NW_TYPE_CHANGED);
                    return;
                } else {
                    if (!CdmaDataProfileTracker.OMH_ENABLED && isNvSubscription()) {
                        onNvReady();
                        return;
                    }
                    return;
                }
            case 270358:
                log("EVENT_PS_RESTRICT_ENABLED " + this.mIsPsRestricted);
                stopNetStatPoll();
                stopDataStallAlarm();
                this.mIsPsRestricted = true;
                return;
            case 270359:
                log("EVENT_PS_RESTRICT_DISABLED " + this.mIsPsRestricted);
                this.mIsPsRestricted = false;
                if (isConnected()) {
                    startNetStatPoll();
                    startDataStallAlarm(false);
                    return;
                }
                if (this.mState == DctConstants.State.FAILED) {
                    cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
                    this.mReregisterOnReconnectFailure = false;
                }
                ApnContext apnContext = this.mApnContexts.get("default");
                if (apnContext != null) {
                    apnContext.setReason(Phone.REASON_PS_RESTRICT_ENABLED);
                    trySetupData(apnContext);
                    return;
                } else {
                    loge("**** Default ApnContext not found ****");
                    if (Build.IS_DEBUGGABLE) {
                        throw new RuntimeException("Default ApnContext not found");
                    }
                    return;
                }
            case 270360:
                boolean tearDown = msg.arg1 != 0;
                log("EVENT_CLEAN_UP_CONNECTION tearDown=" + tearDown);
                if (msg.obj instanceof ApnContext) {
                    cleanUpConnection(tearDown, (ApnContext) msg.obj);
                    return;
                } else {
                    loge("EVENT_CLEAN_UP_CONNECTION request w/o apn context, call super");
                    super.handleMessage(msg);
                    return;
                }
            case 270377:
                onModemDataProfileReady();
                return;
            case 270379:
                notifyOffApnsOfAvailability(Phone.REASON_IWLAN_AVAILABLE);
                return;
            default:
                super.handleMessage(msg);
                return;
        }
    }

    protected int getApnProfileID(String apnType) {
        if (TextUtils.equals(apnType, "ims")) {
            return 2;
        }
        if (TextUtils.equals(apnType, "fota")) {
            return 3;
        }
        if (TextUtils.equals(apnType, "cbs")) {
            return 4;
        }
        return (TextUtils.equals(apnType, "ia") || !TextUtils.equals(apnType, "dun")) ? 0 : 1;
    }

    private int getCellLocationId() {
        CellLocation loc = this.mPhone.getCellLocation();
        if (loc == null) {
            return -1;
        }
        if (loc instanceof GsmCellLocation) {
            int cid = ((GsmCellLocation) loc).getCid();
            return cid;
        }
        if (!(loc instanceof CdmaCellLocation)) {
            return -1;
        }
        int cid2 = ((CdmaCellLocation) loc).getBaseStationId();
        return cid2;
    }

    protected IccRecords getUiccRecords(int appFamily) {
        return this.mUiccController.getIccRecords(appFamily);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected boolean onUpdateIcc() {
        boolean result = false;
        if (this.mUiccController == null) {
            loge("onUpdateIcc: mUiccController is null. Error!");
            return false;
        }
        int dataRat = this.mPhone.getServiceState().getRilDataRadioTechnology();
        int appFamily = UiccController.getFamilyFromRadioTechnology(dataRat);
        IccRecords newIccRecords = getUiccRecords(appFamily);
        log("onUpdateIcc: newIccRecords " + (newIccRecords != null ? newIccRecords.getClass().getName() : null));
        if (dataRat == 0) {
            return false;
        }
        IccRecords r = this.mIccRecords.get();
        if (r != newIccRecords) {
            if (r != null) {
                log("Removing stale icc objects. " + (r != null ? r.getClass().getName() : null));
                r.unregisterForRecordsLoaded(this);
                this.mIccRecords.set(null);
            }
            if (newIccRecords != null) {
                log("New records found " + (newIccRecords != null ? newIccRecords.getClass().getName() : null));
                this.mIccRecords.set(newIccRecords);
                newIccRecords.registerForRecordsLoaded(this, 270338, null);
            }
            result = true;
        }
        return result;
    }

    protected void updateCurrentCarrierInProvider() {
        if (this.mPhone instanceof GSMPhone) {
            ((GSMPhone) this.mPhone).updateCurrentCarrierInProvider();
        } else if (this.mPhone instanceof CDMAPhone) {
            ((CDMAPhone) this.mPhone).updateCurrentCarrierInProvider(getOperatorNumeric());
        }
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void log(String s) {
        Rlog.d(this.LOG_TAG, s);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    protected void loge(String s) {
        Rlog.e(this.LOG_TAG, s);
    }

    @Override // com.android.internal.telephony.dataconnection.DcTrackerBase
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DataConnectionTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mReregisterOnReconnectFailure=" + this.mReregisterOnReconnectFailure);
        pw.println(" canSetPreferApn=" + this.mCanSetPreferApn);
        pw.println(" mApnObserver=" + this.mApnObserver);
        pw.println(" getOverallState=" + getOverallState());
        pw.println(" mDataConnectionAsyncChannels=%s\n" + this.mDataConnectionAcHashMap);
        pw.println(" mAttached=" + this.mAttached.get());
        pw.println(" SUPPORT_MPDN=" + SUPPORT_MPDN);
        pw.println(" mIsOmhEnabled=" + CdmaDataProfileTracker.OMH_ENABLED);
    }
}
