package com.android.internal.telephony.cdma;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes.dex */
public class CDMALTEPhone extends CDMAPhone {
    private static final boolean DBG = true;
    static final String LOG_LTE_TAG = "CDMALTEPhone";
    private IsimUiccRecords mIsimUiccRecords;
    private SIMRecords mSimRecords;

    private static class NetworkSelectMessage {
        public Message message;
        public String operatorAlphaLong;
        public String operatorNumeric;

        private NetworkSelectMessage() {
        }

        /* synthetic */ NetworkSelectMessage(AnonymousClass1 x0) {
            this();
        }
    }

    public CDMALTEPhone(Context context, CommandsInterface ci, PhoneNotifier notifier) {
        super(context, ci, notifier, false);
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase, android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 16:
                handleSetSelectNetwork((AsyncResult) msg.obj);
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone
    protected void initSstIcc() {
        this.mSST = new CdmaLteServiceStateTracker(this);
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void dispose() {
        synchronized (PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void removeReferences() {
        super.removeReferences();
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;
        if (this.mSST == null) {
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (this.mSST.getCurrentDataConnectionState() != 0 && this.mOosIsDisconnect) {
            ret = PhoneConstants.DataState.DISCONNECTED;
            log("getDataConnectionState: Data is Out of Service. ret = " + ret);
        } else if (!this.mDcTracker.isApnTypeEnabled(apnType)) {
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else {
            switch (AnonymousClass1.$SwitchMap$com$android$internal$telephony$DctConstants$State[this.mDcTracker.getState(apnType).ordinal()]) {
                case 1:
                case 2:
                case 3:
                    ret = PhoneConstants.DataState.DISCONNECTED;
                    break;
                case 4:
                case 5:
                    if (this.mCT.mState != PhoneConstants.State.IDLE && !this.mSST.isConcurrentVoiceAndDataAllowed()) {
                        ret = PhoneConstants.DataState.SUSPENDED;
                    } else {
                        ret = PhoneConstants.DataState.CONNECTED;
                    }
                    break;
                case 6:
                case 7:
                    ret = PhoneConstants.DataState.CONNECTING;
                    break;
            }
        }
        log("getDataConnectionState apnType=" + apnType + " ret=" + ret);
        return ret;
    }

    /* JADX INFO: renamed from: com.android.internal.telephony.cdma.CDMALTEPhone$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$android$internal$telephony$DctConstants$State = new int[DctConstants.State.values().length];

        static {
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.RETRYING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.FAILED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.IDLE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTED.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.DISCONNECTING.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.CONNECTING.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$android$internal$telephony$DctConstants$State[DctConstants.State.SCANNING.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public void selectNetworkManually(OperatorInfo network, Message response) {
        NetworkSelectMessage nsm = new NetworkSelectMessage(null);
        nsm.message = response;
        nsm.operatorNumeric = network.getOperatorNumeric();
        nsm.operatorAlphaLong = network.getOperatorAlphaLong();
        Message msg = obtainMessage(16, nsm);
        this.mCi.setNetworkSelectionModeManual(network.getOperatorNumeric(), msg);
    }

    private void handleSetSelectNetwork(AsyncResult ar) {
        if (!(ar.userObj instanceof NetworkSelectMessage)) {
            loge("unexpected result from user object.");
            return;
        }
        NetworkSelectMessage nsm = (NetworkSelectMessage) ar.userObj;
        if (nsm.message != null) {
            log("sending original message to recipient");
            AsyncResult.forMessage(nsm.message, ar.result, ar.exception);
            nsm.message.sendToTarget();
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(PhoneBase.NETWORK_SELECTION_KEY, nsm.operatorNumeric);
        editor.putString(PhoneBase.NETWORK_SELECTION_NAME_KEY, nsm.operatorAlphaLong);
        if (!editor.commit()) {
            loge("failed to commit network selection preference");
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone
    public boolean updateCurrentCarrierInProvider() {
        if (this.mSimRecords != null) {
            try {
                Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, Telephony.Carriers.CURRENT);
                ContentValues map = new ContentValues();
                String operatorNumeric = this.mSimRecords.getOperatorNumeric();
                map.put("numeric", operatorNumeric);
                log("updateCurrentCarrierInProvider from UICC: numeric=" + operatorNumeric);
                this.mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                loge("Can't store current operator ret false", (Exception) e);
            }
        } else {
            log("updateCurrentCarrierInProvider mIccRecords == null ret false");
        }
        return false;
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getDeviceId() {
        String id = getImei();
        Rlog.d("CDMAPhone", "getDeviceId(): is " + id);
        return id;
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getSubscriberId() {
        if (super.getSubscriberId() != null) {
            return super.getSubscriberId();
        }
        return this.mSimRecords != null ? this.mSimRecords.getIMSI() : "";
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getGroupIdLevel1() {
        return this.mSimRecords != null ? this.mSimRecords.getGid1() : "";
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getImei() {
        return this.mImei;
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public String getDeviceSvn() {
        return this.mImeiSv;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public IsimRecords getIsimRecords() {
        return this.mIsimUiccRecords;
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public String getMsisdn() {
        if (this.mSimRecords != null) {
            return this.mSimRecords.getMsisdnNumber();
        }
        return null;
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.Phone
    public void getAvailableNetworks(Message response) {
        this.mCi.getAvailableNetworks(response);
    }

    @Override // com.android.internal.telephony.PhoneBase, com.android.internal.telephony.Phone
    public void requestIsimAuthentication(String nonce, Message result) {
        this.mCi.requestIsimAuthentication(nonce, result);
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase
    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication = this.mUiccController.getUiccCardApplication(3);
            IsimUiccRecords newIsimUiccRecords = null;
            if (newUiccApplication != null) {
                newIsimUiccRecords = (IsimUiccRecords) newUiccApplication.getIccRecords();
            }
            this.mIsimUiccRecords = newIsimUiccRecords;
            UiccCardApplication newUiccApplication2 = this.mUiccController.getUiccCardApplication(1);
            SIMRecords newSimRecords = null;
            if (newUiccApplication2 != null) {
                newSimRecords = (SIMRecords) newUiccApplication2.getIccRecords();
            }
            if (this.mSimRecords != newSimRecords) {
                if (this.mSimRecords != null) {
                    log("Removing stale SIMRecords object.");
                    this.mSimRecords = null;
                }
                if (newSimRecords != null) {
                    log("New SIMRecords found");
                    this.mSimRecords = newSimRecords;
                }
            }
            super.onUpdateIccAvailability();
        }
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone
    protected void log(String s) {
        Rlog.d(LOG_LTE_TAG, s);
    }

    protected void loge(String s) {
        Rlog.e(LOG_LTE_TAG, s);
    }

    protected void loge(String s, Throwable e) {
        Rlog.e(LOG_LTE_TAG, s, e);
    }

    @Override // com.android.internal.telephony.cdma.CDMAPhone, com.android.internal.telephony.PhoneBase
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CDMALTEPhone extends:");
        super.dump(fd, pw, args);
    }
}
