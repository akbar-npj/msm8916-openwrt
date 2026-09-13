package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import com.android.internal.telephony.gsm.UsimPhoneBookManager;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public final class AdnRecordCache extends Handler implements IccConstants {
    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
    static final int EVENT_UPDATE_ADN_DONE = 2;
    private static final int UPDATE_ADN_EF_NOT_KNOWN = 1;
    private static final int UPDATE_ADN_NO_ADN_LIST = 2;
    private static final int UPDATE_ADN_NO_ADN_RECORD = 3;
    private static final int UPDATE_ADN_PENDING = 4;
    private static final int UPDATE_ADN_SUCCESS = 0;
    private static final int USIM_EFANR_TAG = 196;
    private static final int USIM_EFEMAIL_TAG = 202;
    private IccFileHandler mFh;
    private UsimPhoneBookManager mUsimGlobalPhoneBookManager;
    private UsimPhoneBookManager mUsimLocalPhoneBookManager;
    private UsimPhoneBookManager mUsimPhoneBookManager;
    private int mAdncountofIcc = 0;
    SparseArray<ArrayList<AdnRecord>> mGlobalAdnLikeFiles = new SparseArray<>();
    SparseArray<ArrayList<AdnRecord>> mLocalAdnLikeFiles = new SparseArray<>();
    SparseArray<ArrayList<AdnRecord>> mAdnLikeFiles = this.mGlobalAdnLikeFiles;
    SparseArray<ArrayList<Message>> mAdnLikeWaiters = new SparseArray<>();
    SparseArray<Message> mUserWriteResponse = new SparseArray<>();
    private boolean mUseLocalPb = false;

    AdnRecordCache(IccFileHandler fh) {
        this.mFh = fh;
        this.mUsimGlobalPhoneBookManager = new UsimPhoneBookManager(this.mFh, this);
        this.mUsimLocalPhoneBookManager = new UsimPhoneBookManager(this.mFh, this);
        this.mUsimPhoneBookManager = this.mUsimGlobalPhoneBookManager;
    }

    public void reset() {
        this.mGlobalAdnLikeFiles.clear();
        this.mLocalAdnLikeFiles.clear();
        this.mUsimGlobalPhoneBookManager.reset();
        this.mUsimLocalPhoneBookManager.reset();
        clearWaiters();
        clearUserWriters();
    }

    private void clearWaiters() {
        int size = this.mAdnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            ArrayList<Message> waiters = this.mAdnLikeWaiters.valueAt(i);
            AsyncResult ar = new AsyncResult((Object) null, (Object) null, new RuntimeException("AdnCache reset"));
            notifyWaiters(waiters, ar);
        }
        this.mAdnLikeWaiters.clear();
    }

    private void clearUserWriters() {
        int size = this.mUserWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse(this.mUserWriteResponse.valueAt(i), "AdnCace reset");
        }
        this.mUserWriteResponse.clear();
    }

    public ArrayList<AdnRecord> getRecordsIfLoaded(int efid) {
        return this.mAdnLikeFiles.get(efid);
    }

    public int extensionEfForEf(int efid) {
        switch (efid) {
            case IccConstants.EF_PBR /* 20272 */:
                return 0;
            case 28474:
            case IccConstants.EF_MSISDN /* 28480 */:
                return IccConstants.EF_EXT1;
            case IccConstants.EF_FDN /* 28475 */:
                return IccConstants.EF_EXT2;
            case IccConstants.EF_SDN /* 28489 */:
                return IccConstants.EF_EXT3;
            case IccConstants.EF_MBDN /* 28615 */:
                return IccConstants.EF_EXT6;
            default:
                return -1;
        }
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            Exception e = new RuntimeException(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    public void updateAdnByIndex(int efid, AdnRecord adn, int recordIndex, String pin2, Message response) {
        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }
        Message pendingResponse = this.mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
            return;
        }
        useLocalPb(false);
        this.mUserWriteResponse.put(efid, response);
        new AdnRecordLoader(this.mFh).updateEF(adn, efid, extensionEF, recordIndex, pin2, obtainMessage(2, efid, recordIndex, adn));
    }

    public void updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn, String pin2, Message response) {
        int result = updateAdnBySearchOnEf(false, efid, oldAdn, newAdn, pin2, response);
        if (result != 0 && efid == 20272) {
            result = updateAdnBySearchOnEf(true, efid, oldAdn, newAdn, pin2, response);
        }
        switch (result) {
            case 1:
                sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
                break;
            case 2:
                sendErrorResponse(response, "Adn list not exist for EF:" + efid);
                break;
            case 3:
                sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
                break;
            case 4:
                sendErrorResponse(response, "Have pending update for EF:" + efid);
                break;
        }
    }

    private int updateAdnBySearchOnEf(boolean useLocalPb, int efid, AdnRecord oldAdn, AdnRecord newAdn, String pin2, Message response) {
        ArrayList<AdnRecord> oldAdnList;
        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            return 1;
        }
        try {
            if (efid == 20272) {
                useLocalPb(useLocalPb);
                oldAdnList = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
            } else {
                oldAdnList = getRecordsIfLoaded(efid);
            }
        } catch (NullPointerException e) {
            oldAdnList = null;
        }
        if (oldAdnList == null) {
            return 2;
        }
        int index = -1;
        int count = 1;
        for (AdnRecord nextAdnRecord : oldAdnList) {
            boolean isEmailOrAnrIsFull = false;
            if (efid == 20272) {
                int pbrIndex = this.mUsimPhoneBookManager.getPbrIndexBy(count - 1);
                int anrNum = this.mUsimPhoneBookManager.getEmptyAnrNum_Pbrindex(pbrIndex);
                int emailNum = this.mUsimPhoneBookManager.getEmptyEmailNum_Pbrindex(pbrIndex);
                if (oldAdn.getAdditionalNumbers() == null && newAdn.getAdditionalNumbers() != null && anrNum == 0) {
                    isEmailOrAnrIsFull = true;
                }
                if (oldAdn.getEmails() == null && newAdn.getEmails() != null && emailNum == 0) {
                    isEmailOrAnrIsFull = true;
                }
            }
            if (!isEmailOrAnrIsFull && oldAdn.isEqual(nextAdnRecord)) {
                index = count;
                break;
            }
            count++;
        }
        if (index == -1) {
            return 3;
        }
        Log.d("AdnRecordCache", "update oldADN:" + oldAdn.toString() + ", newAdn:" + newAdn.toString() + ",index :" + index);
        if (efid == 20272) {
            AdnRecord foundAdn = oldAdnList.get(index - 1);
            newAdn.mEfid = foundAdn.mEfid;
            newAdn.mExtRecord = foundAdn.mExtRecord;
            newAdn.mRecordNumber = foundAdn.mRecordNumber;
        }
        Message pendingResponse = this.mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            return 4;
        }
        if (efid == 20272) {
            updateEmailAndAnr(efid, oldAdn, newAdn, index, pin2, response);
        } else {
            this.mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(this.mFh).updateEF(newAdn, efid, extensionEF, index, pin2, obtainMessage(2, efid, index, newAdn));
        }
        return 0;
    }

    private void useLocalPb(boolean useLocalPb) {
        if (this.mUseLocalPb != useLocalPb) {
            Log.d("AdnRecordCache", "Using " + (useLocalPb ? "Local" : "Global") + " Phonebook");
            this.mUseLocalPb = useLocalPb;
            this.mFh.useLocalPb(useLocalPb);
            this.mAdnLikeFiles = useLocalPb ? this.mLocalAdnLikeFiles : this.mGlobalAdnLikeFiles;
            this.mUsimPhoneBookManager = useLocalPb ? this.mUsimLocalPhoneBookManager : this.mUsimGlobalPhoneBookManager;
        }
    }

    public void requestLoadAllAdnLike(int efid, int extensionEf, Message response) {
        ArrayList<AdnRecord> result;
        if (efid == 20272) {
            ArrayList<AdnRecord> combinedResult = new ArrayList<>();
            useLocalPb(false);
            ArrayList<AdnRecord> result2 = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
            if (result2 != null) {
                combinedResult.addAll(result2);
            }
            useLocalPb(true);
            result = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
            if (result != null) {
                combinedResult.addAll(result);
            }
            if (!combinedResult.isEmpty()) {
                result = combinedResult;
            }
        } else {
            result = getRecordsIfLoaded(efid);
        }
        if (result != null) {
            if (response != null) {
                AsyncResult.forMessage(response).result = result;
                response.sendToTarget();
                return;
            }
            return;
        }
        ArrayList<Message> waiters = this.mAdnLikeWaiters.get(efid);
        if (waiters != null) {
            waiters.add(response);
            return;
        }
        ArrayList<Message> waiters2 = new ArrayList<>();
        waiters2.add(response);
        this.mAdnLikeWaiters.put(efid, waiters2);
        if (extensionEf < 0) {
            if (response != null) {
                AsyncResult.forMessage(response).exception = new RuntimeException("EF is not known ADN-like EF:" + efid);
                response.sendToTarget();
                return;
            }
            return;
        }
        new AdnRecordLoader(this.mFh).loadAllFromEF(efid, extensionEf, obtainMessage(1, efid, 0));
    }

    private void notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {
        if (waiters != null) {
            int s = waiters.size();
            for (int i = 0; i < s; i++) {
                Message waiter = waiters.get(i);
                AsyncResult.forMessage(waiter, ar.result, ar.exception);
                waiter.sendToTarget();
            }
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case 1:
                AsyncResult ar = (AsyncResult) msg.obj;
                int efid = msg.arg1;
                ArrayList<Message> waiters = this.mAdnLikeWaiters.get(efid);
                this.mAdnLikeWaiters.delete(efid);
                if (ar.exception == null) {
                    this.mAdnLikeFiles.put(efid, (ArrayList) ar.result);
                }
                notifyWaiters(waiters, ar);
                if (this.mAdnLikeFiles.get(28474) != null) {
                    setAdnCount(this.mAdnLikeFiles.get(28474).size());
                }
                break;
            case 2:
                AsyncResult ar2 = (AsyncResult) msg.obj;
                int efid2 = msg.arg1;
                int index = msg.arg2;
                AdnRecord adn = (AdnRecord) ar2.userObj;
                if (ar2.exception == null) {
                    if (this.mAdnLikeFiles.get(efid2) != null) {
                        this.mAdnLikeFiles.get(efid2).set(index - 1, adn);
                    }
                    if (efid2 == 20272) {
                        this.mUsimPhoneBookManager.loadEfFilesFromUsim().set(index - 1, adn);
                    }
                }
                Message response = this.mUserWriteResponse.get(efid2);
                this.mUserWriteResponse.delete(efid2);
                if (response != null) {
                    AsyncResult.forMessage(response, (Object) null, ar2.exception);
                    response.sendToTarget();
                }
                break;
        }
    }

    private void updateEmailAndAnr(int efid, AdnRecord oldAdn, AdnRecord newAdn, int index, String pin2, Message response) {
        int extensionEF = extensionEfForEf(newAdn.mEfid);
        boolean success = updateUsimRecord(oldAdn, newAdn, index, USIM_EFEMAIL_TAG);
        if (success) {
            boolean success2 = updateUsimRecord(oldAdn, newAdn, index, 196);
            if (success2) {
                this.mUserWriteResponse.put(efid, response);
                new AdnRecordLoader(this.mFh).updateEF(newAdn, newAdn.mEfid, extensionEF, newAdn.mRecordNumber, pin2, obtainMessage(2, efid, index, newAdn));
                return;
            } else {
                sendErrorResponse(response, "update anr failed");
                return;
            }
        }
        sendErrorResponse(response, "update email failed");
    }

    private boolean updateUsimRecord(AdnRecord oldAdn, AdnRecord newAdn, int index, int tag) {
        String[] oldRecords;
        String[] newRecords;
        boolean success;
        String oldRecord = null;
        String newRecord = null;
        switch (tag) {
            case 196:
                oldRecords = oldAdn.getAdditionalNumbers();
                newRecords = newAdn.getAdditionalNumbers();
                break;
            case USIM_EFEMAIL_TAG /* 202 */:
                oldRecords = oldAdn.getEmails();
                newRecords = newAdn.getEmails();
                break;
            default:
                return false;
        }
        if (oldRecords != null) {
            String[] arr$ = oldRecords;
            int len$ = arr$.length;
            if (0 < len$) {
                String record = arr$[0];
                oldRecord = record;
            }
        }
        if (newRecords != null) {
            String[] arr$2 = newRecords;
            int len$2 = arr$2.length;
            if (0 < len$2) {
                String record2 = arr$2[0];
                newRecord = record2;
            }
        }
        if ((TextUtils.isEmpty(oldRecord) && TextUtils.isEmpty(newRecord)) || (oldRecord != null && oldRecord.equals(newRecord))) {
            success = true;
        } else {
            try {
                switch (tag) {
                    case 196:
                        success = this.mUsimPhoneBookManager.updateAnrFile(index, oldRecord, newRecord);
                        break;
                    case USIM_EFEMAIL_TAG /* 202 */:
                        success = this.mUsimPhoneBookManager.updateEmailFile(index, oldRecord, newRecord);
                        break;
                    default:
                        success = false;
                        break;
                }
            } catch (RuntimeException e) {
                success = false;
                Log.e("AdnRecordCache", "update usim record failed", e);
            }
        }
        return success;
    }

    public void updateUsimAdnByIndex(int efid, AdnRecord newAdn, int recordIndex, String pin2, Message response) {
        ArrayList<AdnRecord> oldAdnList;
        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            return;
        }
        useLocalPb(false);
        try {
            if (efid == 20272) {
                oldAdnList = this.mUsimPhoneBookManager.loadEfFilesFromUsim();
            } else {
                oldAdnList = getRecordsIfLoaded(efid);
            }
        } catch (NullPointerException e) {
            oldAdnList = null;
        }
        if (oldAdnList == null) {
            sendErrorResponse(response, "Adn list not exist for EF:" + efid);
            return;
        }
        if (efid == 20272) {
            AdnRecord foundAdn = oldAdnList.get(recordIndex - 1);
            newAdn.mEfid = foundAdn.mEfid;
            newAdn.mExtRecord = foundAdn.mExtRecord;
            newAdn.mRecordNumber = foundAdn.mRecordNumber;
        }
        Message pendingResponse = this.mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:" + efid);
        } else if (efid == 20272) {
            updateEmailAndAnr(efid, oldAdnList.get(recordIndex - 1), newAdn, recordIndex, pin2, response);
        } else {
            this.mUserWriteResponse.put(efid, response);
            new AdnRecordLoader(this.mFh).updateEF(newAdn, efid, extensionEF, recordIndex, pin2, obtainMessage(2, efid, recordIndex, newAdn));
        }
    }

    public int getAnrCount() {
        return this.mUsimGlobalPhoneBookManager.getAnrCount() + this.mUsimLocalPhoneBookManager.getAnrCount();
    }

    public int getEmailCount() {
        return this.mUsimGlobalPhoneBookManager.getEmailCount() + this.mUsimLocalPhoneBookManager.getEmailCount();
    }

    public int getSpareAnrCount() {
        return this.mUsimGlobalPhoneBookManager.getSpareAnrCount() + this.mUsimLocalPhoneBookManager.getSpareAnrCount();
    }

    public int getSpareEmailCount() {
        return this.mUsimGlobalPhoneBookManager.getSpareEmailCount() + this.mUsimLocalPhoneBookManager.getSpareEmailCount();
    }

    public int getAdnCount() {
        return this.mAdncountofIcc;
    }

    public void setAdnCount(int count) {
        this.mAdncountofIcc = count;
    }

    public int getUsimAdnCount() {
        return this.mUsimGlobalPhoneBookManager.getUsimAdnCount() + this.mUsimLocalPhoneBookManager.getUsimAdnCount();
    }
}
