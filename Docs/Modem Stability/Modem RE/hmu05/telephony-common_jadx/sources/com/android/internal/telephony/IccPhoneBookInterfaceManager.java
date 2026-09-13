package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.text.TextUtils;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/* JADX INFO: loaded from: classes.dex */
public abstract class IccPhoneBookInterfaceManager extends IIccPhoneBook.Stub {
    protected static final boolean ALLOW_SIM_OP_IN_UI_THREAD = false;
    protected static final boolean DBG = true;
    protected static final int EVENT_GET_SIZE_DONE = 1;
    protected static final int EVENT_LOAD_DONE = 2;
    protected static final int EVENT_UPDATE_DONE = 3;
    protected AdnRecordCache mAdnCache;
    protected PhoneBase mPhone;
    protected int[] mRecordSize;
    protected List<AdnRecord> mRecords;
    protected boolean mSuccess;
    private UiccCardApplication mCurrentApp = null;
    protected final Object mLock = new Object();
    private boolean mIs3gCard = false;
    protected Handler mBaseHandler = new Handler() { // from class: com.android.internal.telephony.IccPhoneBookInterfaceManager.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (ar.exception == null) {
                            IccPhoneBookInterfaceManager.this.mRecordSize = (int[]) ar.result;
                            IccPhoneBookInterfaceManager.this.logd("GET_RECORD_SIZE Size " + IccPhoneBookInterfaceManager.this.mRecordSize[0] + " total " + IccPhoneBookInterfaceManager.this.mRecordSize[1] + " #record " + IccPhoneBookInterfaceManager.this.mRecordSize[2]);
                        }
                        notifyPending(ar);
                        break;
                    }
                    return;
                case 2:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        if (ar2.exception == null) {
                            IccPhoneBookInterfaceManager.this.mRecords = (List) ar2.result;
                        } else {
                            IccPhoneBookInterfaceManager.this.logd("Cannot load ADN records");
                            if (IccPhoneBookInterfaceManager.this.mRecords != null) {
                                IccPhoneBookInterfaceManager.this.mRecords.clear();
                            }
                        }
                        notifyPending(ar2);
                        break;
                    }
                    return;
                case 3:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    synchronized (IccPhoneBookInterfaceManager.this.mLock) {
                        IccPhoneBookInterfaceManager.this.mSuccess = ar3.exception == null;
                        notifyPending(ar3);
                        break;
                    }
                    return;
                default:
                    return;
            }
        }

        private void notifyPending(AsyncResult ar) {
            if (ar.userObj != null) {
                AtomicBoolean status = (AtomicBoolean) ar.userObj;
                status.set(true);
                IccPhoneBookInterfaceManager.this.mLock.notifyAll();
            }
        }
    };

    @Override // com.android.internal.telephony.IIccPhoneBook
    public abstract int[] getAdnRecordsSize(int i);

    protected abstract void logd(String str);

    protected abstract void loge(String str);

    public IccPhoneBookInterfaceManager(PhoneBase phone) {
        this.mPhone = phone;
    }

    private void cleanUp() {
        if (this.mAdnCache != null) {
            this.mAdnCache.reset();
            this.mAdnCache = null;
        }
        this.mIs3gCard = false;
        this.mCurrentApp = null;
        if (this.mRecords != null) {
            this.mRecords.clear();
        }
    }

    public void dispose() {
        cleanUp();
    }

    public void setIccCard(UiccCard card) {
        logd("Card update received: " + card);
        if (card == null) {
            logd("Card is null. Cleanup");
            cleanUp();
            return;
        }
        UiccCardApplication validApp = null;
        int numApps = card.getNumApplications();
        boolean isCurrentAppFound = false;
        this.mIs3gCard = false;
        for (int i = 0; i < numApps; i++) {
            UiccCardApplication app = card.getApplicationIndex(i);
            if (app != null) {
                IccCardApplicationStatus.AppType type = app.getType();
                if (type == IccCardApplicationStatus.AppType.APPTYPE_CSIM || type == IccCardApplicationStatus.AppType.APPTYPE_USIM || type == IccCardApplicationStatus.AppType.APPTYPE_ISIM) {
                    logd("Card is 3G");
                    this.mIs3gCard = true;
                }
                if (!isCurrentAppFound) {
                    if (validApp == null && type != IccCardApplicationStatus.AppType.APPTYPE_UNKNOWN) {
                        validApp = app;
                    }
                    if (this.mCurrentApp == app) {
                        logd("Existing app found");
                        isCurrentAppFound = true;
                    }
                }
                if (this.mIs3gCard && isCurrentAppFound) {
                    break;
                }
            }
        }
        if ((this.mCurrentApp == null || !isCurrentAppFound) && validApp != null) {
            logd("Setting currentApp: " + validApp);
            this.mCurrentApp = validApp;
            this.mAdnCache = this.mCurrentApp.getIccRecords().getAdnCache();
        }
    }

    protected void publish() {
        ServiceManager.addService("simphonebook", this);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        logd("updateAdnRecordsInEfBySearch: efid=" + efid + " (" + oldTag + "," + oldPhoneNumber + ")==> (" + newTag + "," + newPhoneNumber + ") pin2=" + pin2);
        int efid2 = updateEfForIccType(efid);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(3, status);
            AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnBySearch(efid2, oldAdn, newAdn, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsWithContentValuesInEfBySearch(int efid, ContentValues values, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        String oldTag = values.getAsString(IccProvider.STR_TAG);
        String newTag = values.getAsString(IccProvider.STR_NEW_TAG);
        String oldPhoneNumber = values.getAsString("number");
        String newPhoneNumber = values.getAsString(IccProvider.STR_NEW_NUMBER);
        String oldEmail = values.getAsString(IccProvider.STR_EMAILS);
        String newEmail = values.getAsString(IccProvider.STR_NEW_EMAILS);
        String oldAnr = values.getAsString(IccProvider.STR_ANRS);
        String newAnr = values.getAsString(IccProvider.STR_NEW_ANRS);
        String[] oldEmailArray = TextUtils.isEmpty(oldEmail) ? null : getStringArray(oldEmail);
        String[] newEmailArray = TextUtils.isEmpty(newEmail) ? null : getStringArray(newEmail);
        String[] oldAnrArray = TextUtils.isEmpty(oldAnr) ? null : getAnrStringArray(oldAnr);
        String[] newAnrArray = TextUtils.isEmpty(newAnr) ? null : getAnrStringArray(newAnr);
        int efid2 = updateEfForIccType(efid);
        logd("updateAdnRecordsInEfBySearch: efid=" + efid2 + ", values = " + values + ", pin2=" + pin2);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(3, status);
            AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber, oldEmailArray, oldAnrArray);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber, newEmailArray, newAnrArray);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnBySearch(efid2, oldAdn, newAdn, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, int index, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        logd("updateAdnRecordsInEfByIndex: efid=" + efid + " Index=" + index + " ==> (" + newTag + "," + newPhoneNumber + ") pin2=" + pin2);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(3, status);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateAdnByIndex(efid, newAdn, index, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by index due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.READ_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.READ_CONTACTS permission");
        }
        int efid2 = updateEfForIccType(efid);
        logd("getAdnRecordsInEF: efid=" + efid2);
        synchronized (this.mLock) {
            checkThread();
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(2, status);
            if (this.mAdnCache != null) {
                this.mAdnCache.requestLoadAllAdnLike(efid2, this.mAdnCache.extensionEfForEf(efid2), response);
                waitForResult(status);
            } else {
                loge("Failure while trying to load from SIM due to uninitialised adncache");
            }
        }
        return this.mRecords;
    }

    protected void checkThread() {
        if (this.mBaseHandler.getLooper().equals(Looper.myLooper())) {
            loge("query() called on the main UI thread!");
            throw new IllegalStateException("You cannot call query on this provder from the main UI thread.");
        }
    }

    private String[] getAnrStringArray(String str) {
        if (str != null) {
            return str.split("&");
        }
        return null;
    }

    private String[] getStringArray(String str) {
        if (str != null) {
            return str.split(",");
        }
        return null;
    }

    protected void waitForResult(AtomicBoolean status) {
        while (!status.get()) {
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                logd("interrupted while trying to update by search");
            }
        }
    }

    private int updateEfForIccType(int efid) {
        if (efid == 28474 && this.mIs3gCard) {
            logd("Translate EF_ADN to EF_PBR");
            return IccConstants.EF_PBR;
        }
        return efid;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateUsimAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, String[] anrNumbers, String[] emails, int index, String pin2) {
        if (this.mPhone.getContext().checkCallingOrSelfPermission("android.permission.WRITE_CONTACTS") != 0) {
            throw new SecurityException("Requires android.permission.WRITE_CONTACTS permission");
        }
        logd("updateAdnRecordsInEfByIndex: efid=" + efid + " Index=" + index + " ==> (" + newTag + "," + newPhoneNumber + ") pin2=" + pin2);
        synchronized (this.mLock) {
            checkThread();
            this.mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = this.mBaseHandler.obtainMessage(3, status);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber, emails, anrNumbers);
            int efid2 = updateEfForIccType(efid);
            if (this.mAdnCache != null) {
                this.mAdnCache.updateUsimAdnByIndex(efid2, newAdn, index, pin2, response);
                waitForResult(status);
            } else {
                logd("Failure while trying to update by index due to uninitialised adncache");
            }
        }
        return this.mSuccess;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getAdnCount() {
        if (this.mAdnCache != null) {
            if (this.mIs3gCard) {
                int adnCount = this.mAdnCache.getUsimAdnCount();
                return adnCount;
            }
            int adnCount2 = this.mAdnCache.getAdnCount();
            return adnCount2;
        }
        loge("mAdnCache is NULL when getAdnCount.");
        return 0;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getAnrCount() {
        if (this.mAdnCache != null) {
            int anrCount = this.mAdnCache.getAnrCount();
            return anrCount;
        }
        loge("mAdnCache is NULL when getAnrCount.");
        return 0;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getEmailCount() {
        if (this.mAdnCache != null) {
            int emailCount = this.mAdnCache.getEmailCount();
            return emailCount;
        }
        loge("mAdnCache is NULL when getEmailCount.");
        return 0;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getSpareAnrCount() {
        if (this.mAdnCache != null) {
            int spareAnrCount = this.mAdnCache.getSpareAnrCount();
            return spareAnrCount;
        }
        loge("mAdnCache is NULL when getSpareAnrCount.");
        return 0;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getSpareEmailCount() {
        if (this.mAdnCache != null) {
            int spareEmailCount = this.mAdnCache.getSpareEmailCount();
            return spareEmailCount;
        }
        loge("mAdnCache is NULL when getSpareEmailCount.");
        return 0;
    }
}
