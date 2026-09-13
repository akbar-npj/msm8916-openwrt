package com.android.internal.telephony.uicc;

import android.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cat.CatService;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes.dex */
public class UiccCard {
    protected static final boolean DBG = true;
    private static final int EVENT_CARD_ADDED = 14;
    private static final int EVENT_CARD_REMOVED = 13;
    protected static final String LOG_TAG = "UiccCard";
    private RegistrantList mAbsentRegistrants;
    protected IccCardStatus.CardState mCardState;
    protected CatService mCatService;
    private int mCdmaSubscriptionAppIndex;
    protected CommandsInterface mCi;
    protected Context mContext;
    private boolean mDestroyed;
    private int mGsmUmtsSubscriptionAppIndex;
    protected Handler mHandler;
    private int mImsSubscriptionAppIndex;
    private CommandsInterface.RadioState mLastRadioState;
    private final Object mLock;
    protected UiccCardApplication[] mUiccApplications;
    private IccCardStatus.PinState mUniversalPinState;

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics) {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mHandler = new Handler() { // from class: com.android.internal.telephony.uicc.UiccCard.2
            @Override // android.os.Handler
            public void handleMessage(Message msg) throws Throwable {
                if (UiccCard.this.mDestroyed) {
                    UiccCard.this.loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                }
                switch (msg.what) {
                    case 13:
                        UiccCard.this.onIccSwap(false);
                        break;
                    case 14:
                        UiccCard.this.onIccSwap(true);
                        break;
                    default:
                        UiccCard.this.loge("Unknown Event " + msg.what);
                        break;
                }
            }
        };
        log("Creating");
        this.mCardState = ics.mCardState;
        update(c, ci, ics);
    }

    protected UiccCard() {
        this.mLock = new Object();
        this.mUiccApplications = new UiccCardApplication[8];
        this.mDestroyed = false;
        this.mLastRadioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
        this.mAbsentRegistrants = new RegistrantList();
        this.mHandler = new Handler() { // from class: com.android.internal.telephony.uicc.UiccCard.2
            @Override // android.os.Handler
            public void handleMessage(Message msg) throws Throwable {
                if (UiccCard.this.mDestroyed) {
                    UiccCard.this.loge("Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
                }
                switch (msg.what) {
                    case 13:
                        UiccCard.this.onIccSwap(false);
                        break;
                    case 14:
                        UiccCard.this.onIccSwap(true);
                        break;
                    default:
                        UiccCard.this.loge("Unknown Event " + msg.what);
                        break;
                }
            }
        };
    }

    public void dispose() {
        synchronized (this.mLock) {
            log("Disposing card");
            if (this.mCatService != null) {
                this.mCatService.dispose();
            }
            UiccCardApplication[] arr$ = this.mUiccApplications;
            for (UiccCardApplication app : arr$) {
                if (app != null) {
                    app.dispose();
                }
            }
            this.mCatService = null;
            this.mUiccApplications = null;
        }
    }

    public void update(Context c, CommandsInterface ci, IccCardStatus ics) {
        synchronized (this.mLock) {
            if (this.mDestroyed) {
                loge("Updated after destroyed! Fix me!");
                return;
            }
            IccCardStatus.CardState oldState = this.mCardState;
            this.mCardState = ics.mCardState;
            this.mUniversalPinState = ics.mUniversalPinState;
            this.mGsmUmtsSubscriptionAppIndex = ics.mGsmUmtsSubscriptionAppIndex;
            this.mCdmaSubscriptionAppIndex = ics.mCdmaSubscriptionAppIndex;
            this.mImsSubscriptionAppIndex = ics.mImsSubscriptionAppIndex;
            this.mContext = c;
            this.mCi = ci;
            log(ics.mApplications.length + " applications");
            for (int i = 0; i < this.mUiccApplications.length; i++) {
                if (this.mUiccApplications[i] == null) {
                    if (i < ics.mApplications.length) {
                        this.mUiccApplications[i] = new UiccCardApplication(this, ics.mApplications[i], this.mContext, this.mCi);
                    }
                } else if (i >= ics.mApplications.length) {
                    this.mUiccApplications[i].dispose();
                    this.mUiccApplications[i] = null;
                } else {
                    this.mUiccApplications[i].update(ics.mApplications[i], this.mContext, this.mCi);
                }
            }
            createAndUpdateCatService();
            sanitizeApplicationIndexes();
            CommandsInterface.RadioState radioState = this.mCi.getRadioState();
            log("update: radioState=" + radioState + " mLastRadioState=" + this.mLastRadioState);
            if (radioState == CommandsInterface.RadioState.RADIO_ON && this.mLastRadioState == CommandsInterface.RadioState.RADIO_ON) {
                if (oldState != IccCardStatus.CardState.CARDSTATE_ABSENT && this.mCardState == IccCardStatus.CardState.CARDSTATE_ABSENT) {
                    log("update: notify card removed");
                    this.mAbsentRegistrants.notifyRegistrants();
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(13, null));
                } else if (oldState == IccCardStatus.CardState.CARDSTATE_ABSENT && this.mCardState != IccCardStatus.CardState.CARDSTATE_ABSENT) {
                    log("update: notify card added");
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(14, null));
                }
            }
            this.mLastRadioState = radioState;
        }
    }

    protected void createAndUpdateCatService() {
        if (this.mUiccApplications.length > 0 && this.mUiccApplications[0] != null) {
            this.mCatService = CatService.getInstance(this.mCi, this.mContext, this);
            return;
        }
        if (this.mCatService != null) {
            this.mCatService.dispose();
        }
        this.mCatService = null;
    }

    protected void finalize() {
        log("UiccCard finalized");
    }

    private void sanitizeApplicationIndexes() {
        this.mGsmUmtsSubscriptionAppIndex = checkIndex(this.mGsmUmtsSubscriptionAppIndex, IccCardApplicationStatus.AppType.APPTYPE_SIM, IccCardApplicationStatus.AppType.APPTYPE_USIM);
        this.mCdmaSubscriptionAppIndex = checkIndex(this.mCdmaSubscriptionAppIndex, IccCardApplicationStatus.AppType.APPTYPE_RUIM, IccCardApplicationStatus.AppType.APPTYPE_CSIM);
        this.mImsSubscriptionAppIndex = checkIndex(this.mImsSubscriptionAppIndex, IccCardApplicationStatus.AppType.APPTYPE_ISIM, null);
    }

    private int checkIndex(int index, IccCardApplicationStatus.AppType expectedAppType, IccCardApplicationStatus.AppType altExpectedAppType) {
        if (this.mUiccApplications == null || index >= this.mUiccApplications.length) {
            loge("App index " + index + " is invalid since there are no applications");
            return -1;
        }
        if (index < 0) {
            return -1;
        }
        if (this.mUiccApplications[index].getType() != expectedAppType && this.mUiccApplications[index].getType() != altExpectedAppType) {
            loge("App index " + index + " is invalid since it's not " + expectedAppType + " and not " + altExpectedAppType);
            return -1;
        }
        return index;
    }

    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (this.mLock) {
            Registrant r = new Registrant(h, what, obj);
            this.mAbsentRegistrants.add(r);
            if (this.mCardState == IccCardStatus.CardState.CARDSTATE_ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForAbsent(Handler h) {
        synchronized (this.mLock) {
            this.mAbsentRegistrants.remove(h);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Bottom block not found for handler: all -> 0x006f */
    /* JADX WARN: Code restructure failed: missing block: B:31:0x005d, code lost:
    
        r8 = th;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    public void onIccSwap(boolean isAdded) throws Throwable {
        boolean isHotSwapSupported = this.mContext.getResources().getBoolean(R.bool.config_cameraDoubleTapPowerGestureEnabled);
        if (!isHotSwapSupported) {
            synchronized (this.mLock) {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() { // from class: com.android.internal.telephony.uicc.UiccCard.1
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (UiccCard.this.mLock) {
                            if (which == -1) {
                                UiccCard.this.log("Reboot due to SIM swap");
                                PowerManager pm = (PowerManager) UiccCard.this.mContext.getSystemService("power");
                                pm.reboot("SIM is added.");
                            }
                        }
                    }
                };
                try {
                    Resources r = Resources.getSystem();
                    String title = isAdded ? r.getString(R.string.font_family_subhead_material) : r.getString(R.string.font_family_display_4_material);
                    String message = isAdded ? r.getString(R.string.font_family_title_material) : r.getString(R.string.font_family_headline_material);
                    String buttonTxt = r.getString(R.string.force_close);
                    AlertDialog dialog = new AlertDialog.Builder(this.mContext).setTitle(title).setMessage(message).setPositiveButton(buttonTxt, listener).create();
                    dialog.getWindow().setType(2003);
                    dialog.show();
                } catch (Throwable th) {
                    th = th;
                    while (true) {
                        throw th;
                    }
                }
            }
        }
    }

    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        boolean z;
        synchronized (this.mLock) {
            for (int i = 0; i < this.mUiccApplications.length; i++) {
                if (this.mUiccApplications[i] != null && this.mUiccApplications[i].getType() == type) {
                    z = true;
                }
            }
            z = false;
        }
        return z;
    }

    public IccCardStatus.CardState getCardState() {
        IccCardStatus.CardState cardState;
        synchronized (this.mLock) {
            cardState = this.mCardState;
        }
        return cardState;
    }

    public IccCardStatus.PinState getUniversalPinState() {
        IccCardStatus.PinState pinState;
        synchronized (this.mLock) {
            pinState = this.mUniversalPinState;
        }
        return pinState;
    }

    public UiccCardApplication getApplication(int family) {
        UiccCardApplication uiccCardApplication;
        synchronized (this.mLock) {
            int index = 8;
            try {
                switch (family) {
                    case 1:
                        index = this.mGsmUmtsSubscriptionAppIndex;
                        break;
                    case 2:
                        index = this.mCdmaSubscriptionAppIndex;
                        break;
                    case 3:
                        index = this.mImsSubscriptionAppIndex;
                        break;
                }
                if (index >= 0 && index < this.mUiccApplications.length) {
                    uiccCardApplication = this.mUiccApplications[index];
                } else {
                    uiccCardApplication = null;
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return uiccCardApplication;
    }

    /* JADX WARN: Code duplicated, block: B:10:0x0010  */
    public UiccCardApplication getApplicationIndex(int index) {
        UiccCardApplication uiccCardApplication;
        synchronized (this.mLock) {
            if (index < 0) {
                uiccCardApplication = null;
            } else if (index < this.mUiccApplications.length) {
                uiccCardApplication = this.mUiccApplications[index];
            } else {
                uiccCardApplication = null;
            }
            throw th;
        }
        return uiccCardApplication;
    }

    public int getNumApplications() {
        int count = 0;
        UiccCardApplication[] arr$ = this.mUiccApplications;
        for (UiccCardApplication a : arr$) {
            if (a != null) {
                count++;
            }
        }
        return count;
    }

    public void onRefresh(IccRefreshResponse refreshResponse) {
        for (int i = 0; i < this.mUiccApplications.length; i++) {
            if (this.mUiccApplications[i] != null) {
                this.mUiccApplications[i].onRefresh(refreshResponse);
            }
        }
    }

    protected void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        IccRecords ir;
        pw.println("UiccCard:");
        pw.println(" mCi=" + this.mCi);
        pw.println(" mDestroyed=" + this.mDestroyed);
        pw.println(" mLastRadioState=" + this.mLastRadioState);
        pw.println(" mCatService=" + this.mCatService);
        pw.println(" mAbsentRegistrants: size=" + this.mAbsentRegistrants.size());
        for (int i = 0; i < this.mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]=" + ((Registrant) this.mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mCardState=" + this.mCardState);
        pw.println(" mUniversalPinState=" + this.mUniversalPinState);
        pw.println(" mGsmUmtsSubscriptionAppIndex=" + this.mGsmUmtsSubscriptionAppIndex);
        pw.println(" mCdmaSubscriptionAppIndex=" + this.mCdmaSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + this.mImsSubscriptionAppIndex);
        pw.println(" mImsSubscriptionAppIndex=" + this.mImsSubscriptionAppIndex);
        pw.println(" mUiccApplications: length=" + this.mUiccApplications.length);
        for (int i2 = 0; i2 < this.mUiccApplications.length; i2++) {
            if (this.mUiccApplications[i2] == null) {
                pw.println("  mUiccApplications[" + i2 + "]=" + ((Object) null));
            } else {
                pw.println("  mUiccApplications[" + i2 + "]=" + this.mUiccApplications[i2].getType() + " " + this.mUiccApplications[i2]);
            }
        }
        pw.println();
        UiccCardApplication[] arr$ = this.mUiccApplications;
        for (UiccCardApplication app : arr$) {
            if (app != null) {
                app.dump(fd, pw, args);
                pw.println();
            }
        }
        UiccCardApplication[] arr$2 = this.mUiccApplications;
        for (UiccCardApplication app2 : arr$2) {
            if (app2 != null && (ir = app2.getIccRecords()) != null) {
                ir.dump(fd, pw, args);
                pw.println();
            }
        }
        pw.flush();
    }
}
