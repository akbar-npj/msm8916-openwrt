package com.android.internal.telephony.uicc;

import android.R;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.MSimTelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/* JADX INFO: loaded from: classes.dex */
public abstract class IccRecords extends Handler implements IccConstants {
    protected static final boolean DBG = true;
    protected static final int EVENT_APP_READY = 1;
    public static final int EVENT_CFI = 0;
    public static final int EVENT_GET_ICC_RECORD_DONE = 100;
    protected static final int EVENT_GET_SMS_RECORD_SIZE_DONE = 35;
    public static final int EVENT_REFRESH = 31;
    public static final int EVENT_REFRESH_OEM = 29;
    protected static final int EVENT_SET_MSISDN_DONE = 30;
    public static final int EVENT_SPN = 1;
    public static final int SPN_RULE_SHOW_PLMN = 2;
    public static final int SPN_RULE_SHOW_SPN = 1;
    protected static final int UNINITIALIZED = -1;
    protected static final int UNKNOWN = 0;
    protected AdnRecordCache mAdnCache;
    protected CommandsInterface mCi;
    protected Context mContext;
    protected IccFileHandler mFh;
    protected String mGid1;
    protected String mIccId;
    protected String mImsi;
    private boolean mOEMHookSimRefresh;
    protected UiccCardApplication mParentApp;
    protected int mRecordsToLoad;
    protected String mSpn;
    protected AtomicBoolean mDestroyed = new AtomicBoolean(false);
    protected RegistrantList mRecordsLoadedRegistrants = new RegistrantList();
    protected RegistrantList mImsiReadyRegistrants = new RegistrantList();
    protected RegistrantList mRecordsEventsRegistrants = new RegistrantList();
    protected RegistrantList mNewSmsRegistrants = new RegistrantList();
    protected RegistrantList mNetworkSelectionModeAutomaticRegistrants = new RegistrantList();
    protected boolean mRecordsRequested = false;
    protected String mMsisdn = null;
    protected String mMsisdnTag = null;
    protected String mVoiceMailNum = null;
    protected String mVoiceMailTag = null;
    protected String mNewVoiceMailNum = null;
    protected String mNewVoiceMailTag = null;
    protected boolean mIsVoiceMailFixed = false;
    protected int mMncLength = -1;
    protected int mMailboxIndex = 0;
    protected int mSmsCountOnIcc = -1;

    public interface IccRecordLoaded {
        String getEfName();

        void onRecordLoaded(AsyncResult asyncResult);
    }

    public abstract int getDisplayRule(String str);

    public abstract int getVoiceMessageCount();

    protected abstract void handleFileUpdate(int i);

    protected abstract void log(String str);

    protected abstract void loge(String str);

    protected abstract void onAllRecordsLoaded();

    public abstract void onReady();

    protected abstract void onRecordLoaded();

    public abstract void onRefresh(boolean z, int[] iArr);

    public abstract void setVoiceMailNumber(String str, String str2, Message message);

    public abstract void setVoiceMessageWaiting(int i, int i2);

    @Override // android.os.Handler
    public String toString() {
        return "mDestroyed=" + this.mDestroyed + " mContext=" + this.mContext + " mCi=" + this.mCi + " mFh=" + this.mFh + " mParentApp=" + this.mParentApp + " recordsLoadedRegistrants=" + this.mRecordsLoadedRegistrants + " mImsiReadyRegistrants=" + this.mImsiReadyRegistrants + " mRecordsEventsRegistrants=" + this.mRecordsEventsRegistrants + " mNewSmsRegistrants=" + this.mNewSmsRegistrants + " mNetworkSelectionModeAutomaticRegistrants=" + this.mNetworkSelectionModeAutomaticRegistrants + " recordsToLoad=" + this.mRecordsToLoad + " adnCache=" + this.mAdnCache + " recordsRequested=" + this.mRecordsRequested + " iccid=" + this.mIccId + " msisdn=" + this.mMsisdn + " msisdnTag=" + this.mMsisdnTag + " voiceMailNum=" + this.mVoiceMailNum + " voiceMailTag=" + this.mVoiceMailTag + " newVoiceMailNum=" + this.mNewVoiceMailNum + " newVoiceMailTag=" + this.mNewVoiceMailTag + " isVoiceMailFixed=" + this.mIsVoiceMailFixed + " mImsi=" + this.mImsi + " mncLength=" + this.mMncLength + " mailboxIndex=" + this.mMailboxIndex + " spn=" + this.mSpn;
    }

    public IccRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        this.mOEMHookSimRefresh = false;
        this.mContext = c;
        this.mCi = ci;
        this.mFh = app.getIccFileHandler();
        this.mParentApp = app;
        this.mOEMHookSimRefresh = this.mContext.getResources().getBoolean(R.bool.config_built_in_sip_phone);
        if (this.mOEMHookSimRefresh) {
            this.mCi.registerForSimRefreshEvent(this, 29, null);
        } else {
            this.mCi.registerForIccRefresh(this, 31, null);
        }
    }

    public void dispose() {
        this.mDestroyed.set(true);
        if (this.mOEMHookSimRefresh) {
            this.mCi.unregisterForSimRefreshEvent(this);
        } else {
            this.mCi.unregisterForIccRefresh(this);
        }
        this.mParentApp = null;
        this.mFh = null;
        this.mCi = null;
        this.mContext = null;
    }

    void recordsRequired() {
    }

    public AdnRecordCache getAdnCache() {
        return this.mAdnCache;
    }

    public String getIccId() {
        return this.mIccId;
    }

    public void registerForRecordsLoaded(Handler h, int what, Object obj) {
        if (!this.mDestroyed.get()) {
            Registrant r = new Registrant(h, what, obj);
            this.mRecordsLoadedRegistrants.add(r);
            if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
                r.notifyRegistrant(new AsyncResult((Object) null, (Object) null, (Throwable) null));
            }
        }
    }

    public void unregisterForRecordsLoaded(Handler h) {
        this.mRecordsLoadedRegistrants.remove(h);
    }

    public void registerForImsiReady(Handler h, int what, Object obj) {
        if (!this.mDestroyed.get()) {
            Registrant r = new Registrant(h, what, obj);
            this.mImsiReadyRegistrants.add(r);
            if (this.mImsi != null) {
                r.notifyRegistrant(new AsyncResult((Object) null, (Object) null, (Throwable) null));
            }
        }
    }

    public void unregisterForImsiReady(Handler h) {
        this.mImsiReadyRegistrants.remove(h);
    }

    public void registerForRecordsEvents(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mRecordsEventsRegistrants.add(r);
    }

    public void unregisterForRecordsEvents(Handler h) {
        this.mRecordsEventsRegistrants.remove(h);
    }

    public void registerForNewSms(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mNewSmsRegistrants.add(r);
    }

    public void unregisterForNewSms(Handler h) {
        this.mNewSmsRegistrants.remove(h);
    }

    public void registerForNetworkSelectionModeAutomatic(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mNetworkSelectionModeAutomaticRegistrants.add(r);
    }

    public void unregisterForNetworkSelectionModeAutomatic(Handler h) {
        this.mNetworkSelectionModeAutomaticRegistrants.remove(h);
    }

    public String getIMSI() {
        return this.mImsi;
    }

    public String getMsisdnNumber() {
        return this.mMsisdn;
    }

    public String getGid1() {
        return null;
    }

    public void setMsisdnNumber(String alphaTag, String number, Message onComplete) {
        this.mMsisdn = number;
        this.mMsisdnTag = alphaTag;
        log("Set MSISDN: " + this.mMsisdnTag + " " + this.mMsisdn);
        AdnRecord adn = new AdnRecord(this.mMsisdnTag, this.mMsisdn);
        new AdnRecordLoader(this.mFh).updateEF(adn, IccConstants.EF_MSISDN, IccConstants.EF_EXT1, 1, null, obtainMessage(30, onComplete));
    }

    public String getMsisdnAlphaTag() {
        return this.mMsisdnTag;
    }

    public String getVoiceMailNumber() {
        return this.mVoiceMailNum;
    }

    public String getServiceProviderName() {
        return this.mSpn;
    }

    public String getVoiceMailAlphaTag() {
        return this.mVoiceMailTag;
    }

    protected void onIccRefreshInit() {
        this.mAdnCache.reset();
        if (this.mParentApp.getState() == IccCardApplicationStatus.AppState.APPSTATE_READY) {
            sendMessage(obtainMessage(1));
        }
    }

    public boolean getRecordsLoaded() {
        return this.mRecordsToLoad == 0 && this.mRecordsRequested;
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        try {
            switch (msg.what) {
                case EVENT_REFRESH_OEM /* 29 */:
                    log("Card REFRESH OEM occurred: ");
                    if (this.mOEMHookSimRefresh) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception == null) {
                            handleRefreshOem((byte[]) ar.result);
                            return;
                        } else {
                            loge("Icc refresh Exception: " + ar.exception);
                            return;
                        }
                    }
                    return;
                case 31:
                    log("Card REFRESH occurred: ");
                    if (!this.mOEMHookSimRefresh) {
                        AsyncResult ar2 = (AsyncResult) msg.obj;
                        if (ar2.exception == null) {
                            handleRefresh((IccRefreshResponse) ar2.result);
                            return;
                        } else {
                            loge("Icc refresh Exception: " + ar2.exception);
                            return;
                        }
                    }
                    return;
                case 35:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    if (ar3.exception != null) {
                        loge("Exception in EVENT_GET_SMS_RECORD_SIZE_DONE " + ar3.exception);
                        return;
                    }
                    int[] recordSize = (int[]) ar3.result;
                    try {
                        this.mSmsCountOnIcc = recordSize[2];
                        log("EVENT_GET_SMS_RECORD_SIZE_DONE Size " + recordSize[0] + " total " + recordSize[1] + " record " + recordSize[2]);
                        return;
                    } catch (ArrayIndexOutOfBoundsException exc) {
                        loge("ArrayIndexOutOfBoundsException in EVENT_GET_SMS_RECORD_SIZE_DONE: " + exc.toString());
                        return;
                    }
                case EVENT_GET_ICC_RECORD_DONE /* 100 */:
                    AsyncResult ar4 = (AsyncResult) msg.obj;
                    IccRecordLoaded recordLoaded = (IccRecordLoaded) ar4.userObj;
                    log(recordLoaded.getEfName() + " LOADED");
                    if (ar4.exception != null) {
                        loge("Record Load Exception: " + ar4.exception);
                    } else {
                        recordLoaded.onRecordLoaded(ar4);
                    }
                    return;
                default:
                    super.handleMessage(msg);
                    return;
            }
        } catch (RuntimeException exc2) {
            loge("Exception parsing SIM record: " + exc2);
            return;
        } finally {
            onRecordLoaded();
        }
        onRecordLoaded();
    }

    private void handleRefresh(IccRefreshResponse refreshResponse) {
        if (refreshResponse == null) {
            log("handleRefresh received without input");
        }
        if (refreshResponse.aid == null || refreshResponse.aid.equals(this.mParentApp.getAid())) {
            switch (refreshResponse.refreshResult) {
                case 0:
                    log("handleRefresh with SIM_FILE_UPDATED");
                    handleFileUpdate(refreshResponse.efId);
                    break;
                case 1:
                    log("handleRefresh with SIM_REFRESH_INIT");
                    this.mAdnCache.reset();
                    break;
                case 2:
                    log("handleRefresh with SIM_REFRESH_RESET");
                    if (powerOffOnSimReset()) {
                        this.mCi.setRadioPower(false, null);
                    } else {
                        this.mAdnCache.reset();
                        this.mRecordsRequested = false;
                        this.mImsi = null;
                    }
                    break;
                default:
                    log("handleRefresh with unknown operation");
                    break;
            }
        }
    }

    private void handleRefreshOem(byte[] data) {
        ByteBuffer payload = ByteBuffer.wrap(data);
        IccRefreshResponse response = UiccController.parseOemSimRefresh(payload);
        IccCardApplicationStatus appStatus = new IccCardApplicationStatus();
        IccCardApplicationStatus.AppType appType = appStatus.AppTypeFromRILInt(payload.getInt());
        int subId = payload.get();
        if (appType == IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN || appType == this.mParentApp.getType()) {
            handleRefresh(response);
            if (response.refreshResult == 0 || response.refreshResult == 1) {
                log("send broadcast org.codeaurora.intent.action.ACTION_SIM_REFRESH_UPDATE");
                Intent sendIntent = new Intent("org.codeaurora.intent.action.ACTION_SIM_REFRESH_UPDATE");
                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    sendIntent.putExtra("subscription", subId);
                }
                this.mContext.sendBroadcast(sendIntent, null);
            }
        }
    }

    public boolean isCspPlmnEnabled() {
        return false;
    }

    public String getOperatorNumeric() {
        return null;
    }

    public boolean isCallForwardStatusStored() {
        return false;
    }

    public boolean getVoiceCallForwardingFlag() {
        return false;
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable, String number) {
    }

    public boolean isProvisioned() {
        return true;
    }

    public IsimRecords getIsimRecords() {
        return null;
    }

    public UsimServiceTable getUsimServiceTable() {
        return null;
    }

    public int getSmsCapacityOnIcc() {
        log("getSmsCapacityOnIcc: " + this.mSmsCountOnIcc);
        return this.mSmsCountOnIcc;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IccRecords: " + this);
        pw.println(" mDestroyed=" + this.mDestroyed);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mFh=" + this.mFh);
        pw.println(" mParentApp=" + this.mParentApp);
        pw.println(" recordsLoadedRegistrants: size=" + this.mRecordsLoadedRegistrants.size());
        for (int i = 0; i < this.mRecordsLoadedRegistrants.size(); i++) {
            pw.println("  recordsLoadedRegistrants[" + i + "]=" + ((Registrant) this.mRecordsLoadedRegistrants.get(i)).getHandler());
        }
        pw.println(" mImsiReadyRegistrants: size=" + this.mImsiReadyRegistrants.size());
        for (int i2 = 0; i2 < this.mImsiReadyRegistrants.size(); i2++) {
            pw.println("  mImsiReadyRegistrants[" + i2 + "]=" + ((Registrant) this.mImsiReadyRegistrants.get(i2)).getHandler());
        }
        pw.println(" mRecordsEventsRegistrants: size=" + this.mRecordsEventsRegistrants.size());
        for (int i3 = 0; i3 < this.mRecordsEventsRegistrants.size(); i3++) {
            pw.println("  mRecordsEventsRegistrants[" + i3 + "]=" + ((Registrant) this.mRecordsEventsRegistrants.get(i3)).getHandler());
        }
        pw.println(" mNewSmsRegistrants: size=" + this.mNewSmsRegistrants.size());
        for (int i4 = 0; i4 < this.mNewSmsRegistrants.size(); i4++) {
            pw.println("  mNewSmsRegistrants[" + i4 + "]=" + ((Registrant) this.mNewSmsRegistrants.get(i4)).getHandler());
        }
        pw.println(" mNetworkSelectionModeAutomaticRegistrants: size=" + this.mNetworkSelectionModeAutomaticRegistrants.size());
        for (int i5 = 0; i5 < this.mNetworkSelectionModeAutomaticRegistrants.size(); i5++) {
            pw.println("  mNetworkSelectionModeAutomaticRegistrants[" + i5 + "]=" + ((Registrant) this.mNetworkSelectionModeAutomaticRegistrants.get(i5)).getHandler());
        }
        pw.println(" mRecordsRequested=" + this.mRecordsRequested);
        pw.println(" mRecordsToLoad=" + this.mRecordsToLoad);
        pw.println(" mRdnCache=" + this.mAdnCache);
        pw.println(" iccid=" + this.mIccId);
        pw.println(" mMsisdn=" + this.mMsisdn);
        pw.println(" mMsisdnTag=" + this.mMsisdnTag);
        pw.println(" mVoiceMailNum=" + this.mVoiceMailNum);
        pw.println(" mVoiceMailTag=" + this.mVoiceMailTag);
        pw.println(" mNewVoiceMailNum=" + this.mNewVoiceMailNum);
        pw.println(" mNewVoiceMailTag=" + this.mNewVoiceMailTag);
        pw.println(" mIsVoiceMailFixed=" + this.mIsVoiceMailFixed);
        pw.println(" mImsi=" + this.mImsi);
        pw.println(" mMncLength=" + this.mMncLength);
        pw.println(" mMailboxIndex=" + this.mMailboxIndex);
        pw.println(" mSpn=" + this.mSpn);
        pw.flush();
    }

    protected boolean powerOffOnSimReset() {
        return !this.mContext.getResources().getBoolean(R.bool.config_canInternalDisplayHostDesktops);
    }
}
