package com.android.internal.telephony.uicc;

import android.R;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import com.android.internal.telephony.CommandsInterface;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/* JADX INFO: loaded from: classes.dex */
public class UiccController extends Handler {
    public static final int APP_FAM_3GPP = 1;
    public static final int APP_FAM_3GPP2 = 2;
    public static final int APP_FAM_IMS = 3;
    public static final int APP_FAM_UNKNOWN = -1;
    protected static final boolean DBG = true;
    protected static final int EVENT_GET_ICC_STATUS_DONE = 2;
    protected static final int EVENT_ICC_STATUS_CHANGED = 1;
    protected static final int EVENT_RADIO_UNAVAILABLE = 3;
    protected static final int EVENT_REFRESH = 4;
    protected static final int EVENT_REFRESH_OEM = 5;
    protected static final String LOG_TAG = "UiccController";
    protected static UiccController mInstance;
    protected static final Object mLock = new Object();
    private CommandsInterface mCi;
    protected Context mContext;
    protected RegistrantList mIccChangedRegistrants;
    protected boolean mOEMHookSimRefresh;
    protected UiccCard mUiccCard;

    public static UiccController make(Context c, CommandsInterface ci) {
        UiccController uiccController;
        synchronized (mLock) {
            if (mInstance != null) {
                throw new RuntimeException("UiccController.make() should only be called once");
            }
            mInstance = new UiccController(c, ci);
            uiccController = mInstance;
        }
        return uiccController;
    }

    public static UiccController getInstance() {
        UiccController uiccController;
        synchronized (mLock) {
            if (mInstance == null) {
                throw new RuntimeException("UiccController.getInstance can't be called before make()");
            }
            uiccController = mInstance;
        }
        return uiccController;
    }

    public static void destroy() {
        synchronized (mLock) {
            if (mInstance == null) {
                throw new RuntimeException("UiccController.destroy() should only be called after make()");
            }
            mInstance.mCi.unregisterForIccStatusChanged(mInstance);
            mInstance.mCi.unregisterForAvailable(mInstance);
            mInstance.mCi.unregisterForNotAvailable(mInstance);
            mInstance.mCi.unregisterForIccRefresh(mInstance);
            mInstance.mCi = null;
            mInstance.mContext = null;
            mInstance = null;
        }
    }

    public UiccCard getUiccCard() {
        UiccCard uiccCard;
        synchronized (mLock) {
            uiccCard = this.mUiccCard;
        }
        return uiccCard;
    }

    public UiccCardApplication getUiccCardApplication(int family) {
        UiccCardApplication application;
        synchronized (mLock) {
            application = this.mUiccCard != null ? this.mUiccCard.getApplication(family) : null;
        }
        return application;
    }

    public IccRecords getIccRecords(int family) {
        IccRecords iccRecords;
        UiccCardApplication app;
        synchronized (mLock) {
            iccRecords = (this.mUiccCard == null || (app = this.mUiccCard.getApplication(family)) == null) ? null : app.getIccRecords();
        }
        return iccRecords;
    }

    public IccFileHandler getIccFileHandler(int family) {
        IccFileHandler iccFileHandler;
        UiccCardApplication app;
        synchronized (mLock) {
            iccFileHandler = (this.mUiccCard == null || (app = this.mUiccCard.getApplication(family)) == null) ? null : app.getIccFileHandler();
        }
        return iccFileHandler;
    }

    public static int getFamilyFromRadioTechnology(int radioTechnology) {
        if (ServiceState.isGsm(radioTechnology) || radioTechnology == 13) {
            return 1;
        }
        if (ServiceState.isCdma(radioTechnology)) {
            return 2;
        }
        return -1;
    }

    public void registerForIccChanged(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mIccChangedRegistrants.add(r);
            r.notifyRegistrant();
        }
    }

    public void unregisterForIccChanged(Handler h) {
        synchronized (mLock) {
            this.mIccChangedRegistrants.remove(h);
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        synchronized (mLock) {
            switch (msg.what) {
                case 1:
                    log("Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                    this.mCi.getIccCardStatus(obtainMessage(2));
                    break;
                case 2:
                    log("Received EVENT_GET_ICC_STATUS_DONE");
                    onGetIccCardStatusDone((AsyncResult) msg.obj);
                    break;
                case 3:
                    log("EVENT_RADIO_UNAVAILABLE ");
                    disposeCard(this.mUiccCard);
                    this.mUiccCard = null;
                    this.mIccChangedRegistrants.notifyRegistrants();
                    break;
                case 4:
                    log("Sim REFRESH received");
                    if (!this.mOEMHookSimRefresh) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception == null) {
                            handleRefresh((IccRefreshResponse) ar.result);
                        } else {
                            log("Exception on refresh " + ar.exception);
                        }
                    }
                    break;
                case 5:
                    log("Sim REFRESH OEM received");
                    if (this.mOEMHookSimRefresh) {
                        AsyncResult ar2 = (AsyncResult) msg.obj;
                        if (ar2.exception == null) {
                            ByteBuffer payload = ByteBuffer.wrap((byte[]) ar2.result);
                            handleRefresh(parseOemSimRefresh(payload));
                        } else {
                            log("Exception on refresh " + ar2.exception);
                        }
                    }
                    break;
                default:
                    Rlog.e(LOG_TAG, " Unknown Event " + msg.what);
                    break;
            }
        }
    }

    protected synchronized void disposeCard(UiccCard uiccCard) {
        log("Disposing card");
        if (uiccCard != null) {
            uiccCard.dispose();
        }
    }

    private void handleRefresh(IccRefreshResponse refreshResponse) {
        if (refreshResponse == null) {
            log("handleRefresh received without input");
            return;
        }
        if (this.mUiccCard != null) {
            this.mUiccCard.onRefresh(refreshResponse);
        }
        this.mCi.getIccCardStatus(obtainMessage(2));
    }

    public static IccRefreshResponse parseOemSimRefresh(ByteBuffer payload) {
        IccRefreshResponse response = new IccRefreshResponse();
        payload.order(ByteOrder.nativeOrder());
        response.refreshResult = payload.getInt();
        response.efId = payload.getInt();
        int aidLen = payload.getInt();
        byte[] aid = new byte[44];
        payload.get(aid, 0, 44);
        response.aid = aidLen == 0 ? null : new String(aid).substring(0, aidLen);
        Rlog.d(LOG_TAG, "refresh SIM card , refresh result:" + response.refreshResult + ", ef Id:" + response.efId + ", aid:" + response.aid);
        return response;
    }

    private UiccController(Context c, CommandsInterface ci) {
        this.mIccChangedRegistrants = new RegistrantList();
        this.mOEMHookSimRefresh = false;
        log("Creating UiccController");
        this.mContext = c;
        this.mCi = ci;
        this.mCi.registerForIccStatusChanged(this, 1, null);
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForNotAvailable(this, 3, null);
        this.mOEMHookSimRefresh = this.mContext.getResources().getBoolean(R.bool.config_built_in_sip_phone);
        if (this.mOEMHookSimRefresh) {
            this.mCi.registerForSimRefreshEvent(this, 5, null);
        } else {
            this.mCi.registerForIccRefresh(this, 4, null);
        }
    }

    private synchronized void onGetIccCardStatusDone(AsyncResult ar) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG, "Error getting ICC status. RIL_REQUEST_GET_ICC_STATUS should never return an error", ar.exception);
        } else {
            IccCardStatus status = (IccCardStatus) ar.result;
            if (this.mUiccCard == null) {
                log("Creating a new card");
                this.mUiccCard = new UiccCard(this.mContext, this.mCi, status);
            } else {
                log("Update already existing card");
                this.mUiccCard.update(this.mContext, this.mCi, status);
            }
            log("Notifying IccChangedRegistrants");
            this.mIccChangedRegistrants.notifyRegistrants();
        }
    }

    protected UiccController() {
        this.mIccChangedRegistrants = new RegistrantList();
        this.mOEMHookSimRefresh = false;
    }

    private static void log(String string) {
        Rlog.d(LOG_TAG, string);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccController: " + this);
        pw.println(" mContext=" + this.mContext);
        pw.println(" mInstance=" + mInstance);
        pw.println(" mCi=" + this.mCi);
        pw.println(" mUiccCard=" + this.mUiccCard);
        pw.println(" mIccChangedRegistrants: size=" + this.mIccChangedRegistrants.size());
        for (int i = 0; i < this.mIccChangedRegistrants.size(); i++) {
            pw.println("  mIccChangedRegistrants[" + i + "]=" + ((Registrant) this.mIccChangedRegistrants.get(i)).getHandler());
        }
        pw.println();
        pw.flush();
        if (this.mUiccCard != null) {
            this.mUiccCard.dump(fd, pw, args);
        }
    }
}
