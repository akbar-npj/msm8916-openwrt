package com.android.internal.telephony.gsm;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class UsimPhoneBookManager extends Handler implements IccConstants {
    private static final int ANR_ADDITIONAL_NUMBER_END_ID = 12;
    private static final int ANR_ADDITIONAL_NUMBER_START_ID = 3;
    private static final int ANR_ADN_RECORD_IDENTIFIER_ID = 16;
    private static final int ANR_ADN_SFI_ID = 15;
    private static final int ANR_BCD_NUMBER_LENGTH = 1;
    private static final int ANR_CAPABILITY_ID = 13;
    private static final int ANR_DESCRIPTION_ID = 0;
    private static final int ANR_EXTENSION_ID = 14;
    private static final int ANR_TON_NPI_ID = 2;
    private static final boolean DBG = true;
    private static final int EVENT_ANR_LOAD_DONE = 5;
    private static final int EVENT_EF_ANR_RECORD_SIZE_DONE = 7;
    private static final int EVENT_EF_EMAIL_RECORD_SIZE_DONE = 6;
    private static final int EVENT_EF_IAP_RECORD_SIZE_DONE = 10;
    private static final int EVENT_EMAIL_LOAD_DONE = 4;
    private static final int EVENT_IAP_LOAD_DONE = 3;
    private static final int EVENT_PBR_LOAD_DONE = 1;
    private static final int EVENT_UPDATE_ANR_RECORD_DONE = 9;
    private static final int EVENT_UPDATE_EMAIL_RECORD_DONE = 8;
    private static final int EVENT_UPDATE_IAP_RECORD_DONE = 11;
    private static final int EVENT_USIM_ADN_LOAD_DONE = 2;
    private static final String LOG_TAG = "UsimPhoneBookManager";
    private static final int MAX_NUMBER_SIZE_BYTES = 11;
    private static final int USIM_EFAAS_TAG = 199;
    private static final int USIM_EFADN_TAG = 192;
    private static final int USIM_EFANR_TAG = 196;
    private static final int USIM_EFCCP1_TAG = 203;
    private static final int USIM_EFEMAIL_TAG = 202;
    private static final int USIM_EFEXT1_TAG = 194;
    private static final int USIM_EFGRP_TAG = 198;
    private static final int USIM_EFGSD_TAG = 200;
    private static final int USIM_EFIAP_TAG = 193;
    private static final int USIM_EFPBC_TAG = 197;
    private static final int USIM_EFSNE_TAG = 195;
    private static final int USIM_EFUID_TAG = 201;
    private static final int USIM_TYPE1_TAG = 168;
    private static final int USIM_TYPE2_TAG = 169;
    private static final int USIM_TYPE3_TAG = 170;
    private AdnRecordCache mAdnCache;
    private ArrayList<Integer> mAdnLengthList;
    private ArrayList<Integer>[] mAnrFlagsRecord;
    private ArrayList<Integer>[] mEmailFlagsRecord;
    private IccFileHandler mFh;
    private int mPendingExtLoads;
    private Object mLock = new Object();
    private boolean mEmailPresentInIap = false;
    private int mEmailTagNumberInIap = 0;
    private boolean mAnrPresentInIap = false;
    private int mAnrTagNumberInIap = 0;
    private boolean mIapPresent = false;
    private boolean mSuccess = false;
    private boolean mRefreshCache = false;
    private ArrayList<AdnRecord> mPhoneBookRecords = new ArrayList<>();
    private Map<Integer, ArrayList<byte[]>> mIapFileRecord = new HashMap();
    private Map<Integer, ArrayList<byte[]>> mEmailFileRecord = new HashMap();
    private Map<Integer, ArrayList<byte[]>> mAnrFileRecord = new HashMap();
    private Map<Integer, ArrayList<Integer>> mRecordNums = new HashMap();
    private PbrFile mPbrFile = null;
    private Map<Integer, ArrayList<Integer>> mAnrFlags = new HashMap();
    private Map<Integer, ArrayList<Integer>> mEmailFlags = new HashMap();
    private Map<Integer, ArrayList<String>> mEmailsForAdnRec = new HashMap();
    private Map<Integer, ArrayList<String>> mAnrsForAdnRec = new HashMap();
    private Boolean mIsPbrPresent = true;

    public UsimPhoneBookManager(IccFileHandler fh, AdnRecordCache cache) {
        this.mAdnLengthList = null;
        this.mFh = fh;
        this.mAdnLengthList = new ArrayList<>();
        this.mAdnCache = cache;
    }

    public void reset() {
        log("reset");
        if (this.mAnrFlagsRecord != null && this.mEmailFlagsRecord != null && this.mPbrFile != null) {
            for (int i = 0; i < this.mPbrFile.mFileIds.size(); i++) {
                this.mAnrFlagsRecord[i].clear();
                this.mEmailFlagsRecord[i].clear();
            }
        }
        this.mAnrFlags.clear();
        this.mEmailFlags.clear();
        this.mPhoneBookRecords.clear();
        this.mIapFileRecord.clear();
        this.mEmailFileRecord.clear();
        this.mAnrFileRecord.clear();
        this.mRecordNums.clear();
        this.mPbrFile = null;
        this.mAdnLengthList.clear();
        this.mIsPbrPresent = true;
        this.mRefreshCache = false;
    }

    public ArrayList<AdnRecord> loadEfFilesFromUsim() {
        synchronized (this.mLock) {
            if (!this.mPhoneBookRecords.isEmpty()) {
                if (this.mRefreshCache) {
                    this.mRefreshCache = false;
                    refreshCache();
                }
                return this.mPhoneBookRecords;
            }
            if (!this.mIsPbrPresent.booleanValue()) {
                return null;
            }
            if (this.mPbrFile == null) {
                readPbrFileAndWait();
            }
            if (this.mPbrFile == null) {
                return null;
            }
            int numRecs = this.mPbrFile.mFileIds.size();
            if (this.mAnrFlagsRecord == null && this.mEmailFlagsRecord == null) {
                this.mAnrFlagsRecord = new ArrayList[numRecs];
                this.mEmailFlagsRecord = new ArrayList[numRecs];
                for (int i = 0; i < numRecs; i++) {
                    this.mAnrFlagsRecord[i] = new ArrayList<>();
                    this.mEmailFlagsRecord[i] = new ArrayList<>();
                }
            }
            for (int i2 = 0; i2 < numRecs; i2++) {
                readAdnFileAndWait(i2);
                readEmailFileAndWait(i2);
                readAnrFileAndWait(i2);
            }
            return this.mPhoneBookRecords;
        }
    }

    private void refreshCache() {
        if (this.mPbrFile != null) {
            this.mPhoneBookRecords.clear();
            int numRecs = this.mPbrFile.mFileIds.size();
            for (int i = 0; i < numRecs; i++) {
                readAdnFileAndWait(i);
            }
        }
    }

    public void invalidateCache() {
        this.mRefreshCache = true;
    }

    private void readPbrFileAndWait() {
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_PBR, obtainMessage(1));
        try {
            this.mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }

    private void readEmailFileAndWait(int recNum) {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from readEmailFileAndWait");
            return;
        }
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(Integer.valueOf(recNum));
        if (fileIds != null && fileIds.containsKey(Integer.valueOf(USIM_EFEMAIL_TAG))) {
            if (this.mEmailPresentInIap) {
                readIapFileAndWait(fileIds.get(Integer.valueOf(USIM_EFIAP_TAG)).intValue(), recNum);
                if (!hasRecordIn(this.mIapFileRecord, recNum)) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }
                this.mFh.loadEFLinearFixedAll(fileIds.get(Integer.valueOf(USIM_EFEMAIL_TAG)).intValue(), obtainMessage(4, Integer.valueOf(recNum)));
            } else {
                this.mFh.loadEFLinearFixedPart(fileIds.get(Integer.valueOf(USIM_EFEMAIL_TAG)).intValue(), getValidRecordNums(recNum), obtainMessage(4, Integer.valueOf(recNum)));
            }
            log("readEmailFileAndWait email efid is : " + fileIds.get(Integer.valueOf(USIM_EFEMAIL_TAG)));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
            }
            if (!hasRecordIn(this.mEmailFileRecord, recNum)) {
                Rlog.e(LOG_TAG, "Error: Email file is empty");
            } else {
                updatePhoneAdnRecordWithEmail(recNum);
            }
        }
    }

    private void readAnrFileAndWait(int recNum) {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from readAnrFileAndWait");
            return;
        }
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(Integer.valueOf(recNum));
        if (fileIds != null && !fileIds.isEmpty() && fileIds.containsKey(196)) {
            if (this.mAnrPresentInIap) {
                readIapFileAndWait(fileIds.get(Integer.valueOf(USIM_EFIAP_TAG)).intValue(), recNum);
                if (!hasRecordIn(this.mIapFileRecord, recNum)) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }
                this.mFh.loadEFLinearFixedAll(fileIds.get(196).intValue(), obtainMessage(5, Integer.valueOf(recNum)));
            } else {
                this.mFh.loadEFLinearFixedPart(fileIds.get(196).intValue(), getValidRecordNums(recNum), obtainMessage(5, Integer.valueOf(recNum)));
            }
            log("readAnrFileAndWait anr efid is : " + fileIds.get(196));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
            }
            if (!hasRecordIn(this.mAnrFileRecord, recNum)) {
                Rlog.e(LOG_TAG, "Error: Anr file is empty");
            } else {
                updatePhoneAdnRecordWithAnr(recNum);
            }
        }
    }

    private void readIapFileAndWait(int efid, int recNum) {
        log("pbrIndex is " + recNum + ",iap efid is : " + efid);
        this.mFh.loadEFLinearFixedPart(efid, getValidRecordNums(recNum), obtainMessage(3, Integer.valueOf(recNum)));
        try {
            this.mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

    public boolean updateEmailFile(int adnRecNum, String oldEmail, String newEmail) {
        int pbrIndex = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(pbrIndex, USIM_EFEMAIL_TAG);
        if (oldEmail == null) {
            oldEmail = "";
        }
        if (newEmail == null) {
            newEmail = "";
        }
        String emails = oldEmail + "," + newEmail;
        this.mSuccess = false;
        if (efid == -1) {
            return this.mSuccess;
        }
        if (this.mEmailPresentInIap && TextUtils.isEmpty(oldEmail) && !TextUtils.isEmpty(newEmail)) {
            if (getEmptyEmailNum_Pbrindex(pbrIndex) == 0) {
                log("updateEmailFile getEmptyEmailNum_Pbrindex=0, pbrIndex is " + pbrIndex);
                this.mSuccess = true;
                return this.mSuccess;
            }
            this.mSuccess = updateIapFile(adnRecNum, oldEmail, newEmail, USIM_EFEMAIL_TAG);
        } else {
            this.mSuccess = true;
        }
        if (this.mSuccess) {
            log("updateEmailFile oldEmail : " + oldEmail + " newEmail:" + newEmail + " emails:" + emails + " efid" + efid + " adnRecNum: " + adnRecNum);
            synchronized (this.mLock) {
                this.mFh.getEFLinearRecordSize(efid, obtainMessage(6, adnRecNum, efid, emails));
                try {
                    this.mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "interrupted while trying to update by search");
                }
            }
        }
        if (this.mEmailPresentInIap && this.mSuccess && !TextUtils.isEmpty(oldEmail) && TextUtils.isEmpty(newEmail)) {
            this.mSuccess = updateIapFile(adnRecNum, oldEmail, newEmail, USIM_EFEMAIL_TAG);
        }
        return this.mSuccess;
    }

    public boolean updateAnrFile(int adnRecNum, String oldAnr, String newAnr) {
        int pbrIndex = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(pbrIndex, 196);
        if (oldAnr == null) {
            oldAnr = "";
        }
        if (newAnr == null) {
            newAnr = "";
        }
        String anrs = oldAnr + "," + newAnr;
        this.mSuccess = false;
        if (efid == -1) {
            return this.mSuccess;
        }
        if (this.mAnrPresentInIap && TextUtils.isEmpty(oldAnr) && !TextUtils.isEmpty(newAnr)) {
            if (getEmptyAnrNum_Pbrindex(pbrIndex) == 0) {
                log("updateAnrFile getEmptyAnrNum_Pbrindex=0, pbrIndex is " + pbrIndex);
                this.mSuccess = true;
                return this.mSuccess;
            }
            this.mSuccess = updateIapFile(adnRecNum, oldAnr, newAnr, 196);
        } else {
            this.mSuccess = true;
        }
        log("updateAnrFile oldAnr : " + oldAnr + ", newAnr:" + newAnr + " anrs:" + anrs + ", efid" + efid + ", adnRecNum: " + adnRecNum);
        synchronized (this.mLock) {
            this.mFh.getEFLinearRecordSize(efid, obtainMessage(7, adnRecNum, efid, anrs));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
        if (this.mAnrPresentInIap && this.mSuccess && !TextUtils.isEmpty(oldAnr) && TextUtils.isEmpty(newAnr)) {
            this.mSuccess = updateIapFile(adnRecNum, oldAnr, newAnr, 196);
        }
        return this.mSuccess;
    }

    private boolean updateIapFile(int adnRecNum, String oldValue, String newValue, int tag) {
        int pbrIndex = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(pbrIndex, USIM_EFIAP_TAG);
        this.mSuccess = false;
        int recordNumber = -1;
        if (efid == -1) {
            return this.mSuccess;
        }
        switch (tag) {
            case 196:
                recordNumber = getAnrRecNumber(adnRecNum - 1, this.mPhoneBookRecords.size(), oldValue);
                break;
            case USIM_EFEMAIL_TAG /* 202 */:
                recordNumber = getEmailRecNumber(adnRecNum - 1, this.mPhoneBookRecords.size(), oldValue);
                break;
        }
        if (TextUtils.isEmpty(newValue)) {
            recordNumber = -1;
        }
        log("updateIapFile  efid=" + efid + ", recordNumber= " + recordNumber + ", adnRecNum=" + adnRecNum);
        synchronized (this.mLock) {
            this.mFh.getEFLinearRecordSize(efid, obtainMessage(10, adnRecNum, recordNumber, Integer.valueOf(tag)));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
        return this.mSuccess;
    }

    private int getEfidByTag(int recNum, int tag) {
        int efid = -1;
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(Integer.valueOf(recNum));
        if (fileIds == null) {
            return -1;
        }
        if (fileIds.containsKey(Integer.valueOf(tag))) {
            efid = fileIds.get(Integer.valueOf(tag)).intValue();
        }
        return efid;
    }

    public int getPbrIndexBy(int adnIndex) {
        int len = this.mAdnLengthList.size();
        int size = 0;
        for (int i = 0; i < len; i++) {
            size += this.mAdnLengthList.get(i).intValue();
            if (adnIndex < size) {
                log("getPbrIndexBy  adnIndex: " + adnIndex + " PbrIndex: " + i);
                return i;
            }
        }
        return -1;
    }

    private int getInitIndexBy(int pbrIndex) {
        int index = 0;
        while (pbrIndex > 0) {
            index += this.mAdnLengthList.get(pbrIndex - 1).intValue();
            pbrIndex--;
        }
        return index;
    }

    private boolean hasRecordIn(Map<Integer, ArrayList<byte[]>> record, int pbrIndex) {
        if (record == null || record.isEmpty()) {
            return false;
        }
        try {
            return record.get(Integer.valueOf(pbrIndex)) != null;
        } catch (IndexOutOfBoundsException e) {
            Rlog.e(LOG_TAG, "record is empty in pbrIndex" + pbrIndex);
            return false;
        }
    }

    private void updatePhoneAdnRecordWithEmail(int pbrIndex) {
        AdnRecord rec;
        if (hasRecordIn(this.mEmailFileRecord, pbrIndex)) {
            int numAdnRecs = this.mAdnLengthList.get(pbrIndex).intValue();
            if (this.mEmailPresentInIap && hasRecordIn(this.mIapFileRecord, pbrIndex)) {
                for (int i = 0; i < numAdnRecs; i++) {
                    try {
                        byte[] record = this.mIapFileRecord.get(Integer.valueOf(pbrIndex)).get(i);
                        int recNum = record[this.mEmailTagNumberInIap];
                        if (recNum > 0) {
                            String[] emails = {readEmailRecord(recNum - 1, pbrIndex)};
                            int adnRecIndex = i + getInitIndexBy(pbrIndex);
                            AdnRecord rec2 = this.mPhoneBookRecords.get(adnRecIndex);
                            if (rec2 != null && !TextUtils.isEmpty(emails[0])) {
                                rec2.setEmails(emails);
                                this.mPhoneBookRecords.set(adnRecIndex, rec2);
                                this.mEmailFlags.get(Integer.valueOf(pbrIndex)).set(recNum - 1, 1);
                            }
                        }
                    } catch (IndexOutOfBoundsException e) {
                        Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                        return;
                    }
                }
                return;
            }
            int len = this.mAdnLengthList.get(pbrIndex).intValue();
            if (!this.mEmailPresentInIap) {
                parseType1EmailFile(len, pbrIndex);
            }
            for (int i2 = getInitIndexBy(pbrIndex); i2 < getInitIndexBy(pbrIndex) + numAdnRecs; i2++) {
                try {
                    ArrayList<String> emailList = this.mEmailsForAdnRec.get(Integer.valueOf(i2));
                    if (emailList != null && (rec = this.mPhoneBookRecords.get(i2)) != null) {
                        String[] emails2 = new String[emailList.size()];
                        System.arraycopy(emailList.toArray(), 0, emails2, 0, emailList.size());
                        rec.setEmails(emails2);
                        this.mPhoneBookRecords.set(i2, rec);
                    }
                } catch (IndexOutOfBoundsException e2) {
                    return;
                }
            }
        }
    }

    private void updatePhoneAdnRecordWithAnr(int pbrIndex) {
        AdnRecord rec;
        if (hasRecordIn(this.mAnrFileRecord, pbrIndex)) {
            int numAdnRecs = this.mAdnLengthList.get(pbrIndex).intValue();
            if (this.mAnrPresentInIap && hasRecordIn(this.mIapFileRecord, pbrIndex)) {
                for (int i = 0; i < numAdnRecs; i++) {
                    try {
                        byte[] record = this.mIapFileRecord.get(Integer.valueOf(pbrIndex)).get(i);
                        int recNum = record[this.mAnrTagNumberInIap];
                        if (recNum > 0) {
                            String[] anrs = {readAnrRecord(recNum - 1, pbrIndex)};
                            int adnRecIndex = i + getInitIndexBy(pbrIndex);
                            AdnRecord rec2 = this.mPhoneBookRecords.get(adnRecIndex);
                            if (rec2 != null && !TextUtils.isEmpty(anrs[0])) {
                                rec2.setAdditionalNumbers(anrs);
                                this.mPhoneBookRecords.set(adnRecIndex, rec2);
                                this.mAnrFlags.get(Integer.valueOf(pbrIndex)).set(recNum - 1, 1);
                            }
                        }
                    } catch (IndexOutOfBoundsException e) {
                        Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                        return;
                    }
                }
                return;
            }
            if (!this.mAnrPresentInIap) {
                parseType1AnrFile(numAdnRecs, pbrIndex);
            }
            for (int i2 = getInitIndexBy(pbrIndex); i2 < getInitIndexBy(pbrIndex) + numAdnRecs; i2++) {
                try {
                    ArrayList<String> anrList = this.mAnrsForAdnRec.get(Integer.valueOf(i2));
                    if (anrList != null && (rec = this.mPhoneBookRecords.get(i2)) != null) {
                        String[] anrs2 = new String[anrList.size()];
                        System.arraycopy(anrList.toArray(), 0, anrs2, 0, anrList.size());
                        rec.setAdditionalNumbers(anrs2);
                        this.mPhoneBookRecords.set(i2, rec);
                    }
                } catch (IndexOutOfBoundsException e2) {
                    return;
                }
            }
        }
    }

    void parseType1EmailFile(int numRecs, int pbrIndex) {
        if (hasRecordIn(this.mEmailFileRecord, pbrIndex)) {
            log("parseType1EmailFile: pbrIndex is: " + pbrIndex + ", numRecs is: " + numRecs);
            for (int i = 0; i < numRecs; i++) {
                try {
                    this.mEmailFileRecord.get(Integer.valueOf(pbrIndex)).get(i);
                    String email = readEmailRecord(i, pbrIndex);
                    if (email != null && !email.equals("")) {
                        int adnRecIndex = i + getInitIndexBy(pbrIndex);
                        ArrayList<String> val = this.mEmailsForAdnRec.get(Integer.valueOf(adnRecIndex));
                        if (val == null) {
                            val = new ArrayList<>();
                        }
                        val.add(email);
                        this.mEmailsForAdnRec.put(Integer.valueOf(adnRecIndex), val);
                        this.mEmailFlags.get(Integer.valueOf(pbrIndex)).set(i, 1);
                    }
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: No email record for ADN, continuing");
                    return;
                }
            }
        }
    }

    void parseType1AnrFile(int numRecs, int pbrIndex) {
        if (hasRecordIn(this.mAnrFileRecord, pbrIndex)) {
            log("parseType1AnrFile: pbrIndex is: " + pbrIndex + ", numRecs is: " + numRecs);
            for (int i = 0; i < numRecs; i++) {
                try {
                    this.mAnrFileRecord.get(Integer.valueOf(pbrIndex)).get(i);
                    String anr = readAnrRecord(i, pbrIndex);
                    if (anr != null && !anr.equals("")) {
                        int adnRecIndex = i + getInitIndexBy(pbrIndex);
                        ArrayList<String> val = this.mAnrsForAdnRec.get(Integer.valueOf(adnRecIndex));
                        if (val == null) {
                            val = new ArrayList<>();
                        }
                        val.add(anr);
                        this.mAnrsForAdnRec.put(Integer.valueOf(adnRecIndex), val);
                        this.mAnrFlags.get(Integer.valueOf(pbrIndex)).set(i, 1);
                    }
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: No anr record for ADN, continuing");
                    return;
                }
            }
        }
    }

    private String readEmailRecord(int recNum, int pbrIndex) {
        if (!hasRecordIn(this.mEmailFileRecord, pbrIndex)) {
            return null;
        }
        try {
            byte[] emailRec = this.mEmailFileRecord.get(Integer.valueOf(pbrIndex)).get(recNum);
            return IccUtils.adnStringFieldToString(emailRec, 0, emailRec.length - 2);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private String readAnrRecord(int recNum, int pbrIndex) {
        if (!hasRecordIn(this.mAnrFileRecord, pbrIndex)) {
            return null;
        }
        try {
            byte[] anrRec = this.mAnrFileRecord.get(Integer.valueOf(pbrIndex)).get(recNum);
            int numberLength = anrRec[1] & 255;
            if (numberLength > 11) {
                log("Invalid number length in anr record " + numberLength);
                return "";
            }
            return PhoneNumberUtils.calledPartyBCDToString(anrRec, 2, numberLength);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private void readAdnFileAndWait(int recNum) {
        if (this.mPbrFile == null) {
            Rlog.e(LOG_TAG, "mPbrFile is NULL, exiting from readAdnFileAndWait");
            return;
        }
        Map<Integer, Integer> fileIds = this.mPbrFile.mFileIds.get(Integer.valueOf(recNum));
        if (fileIds != null && !fileIds.isEmpty()) {
            int extEf = 0;
            if (fileIds.containsKey(Integer.valueOf(USIM_EFEXT1_TAG))) {
                extEf = fileIds.get(Integer.valueOf(USIM_EFEXT1_TAG)).intValue();
            }
            log("readAdnFileAndWait adn efid is : " + fileIds.get(Integer.valueOf(USIM_EFADN_TAG)));
            this.mAdnCache.requestLoadAllAdnLike(fileIds.get(Integer.valueOf(USIM_EFADN_TAG)).intValue(), extEf, obtainMessage(2, Integer.valueOf(recNum)));
            try {
                this.mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
            }
        }
    }

    private int getEmailRecNumber(int adnRecIndex, int numRecs, String oldEmail) {
        int pbrIndex = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
        log("getEmailRecNumber adnRecIndex is: " + adnRecIndex + ", recordIndex is :" + recordIndex);
        if (!hasRecordIn(this.mEmailFileRecord, pbrIndex)) {
            log("getEmailRecNumber recordNumber is: -1");
            return -1;
        }
        if (this.mEmailPresentInIap && hasRecordIn(this.mIapFileRecord, pbrIndex)) {
            byte[] record = null;
            try {
                record = this.mIapFileRecord.get(Integer.valueOf(pbrIndex)).get(recordIndex);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getEmailRecNumber");
            }
            if (record != null && record[this.mEmailTagNumberInIap] > 0) {
                int recordNumber = record[this.mEmailTagNumberInIap];
                log(" getEmailRecNumber: record is " + IccUtils.bytesToHexString(record) + ", the email recordNumber is :" + recordNumber);
                return recordNumber;
            }
            int recsSize = this.mEmailFileRecord.get(Integer.valueOf(pbrIndex)).size();
            log("getEmailRecNumber recsSize is: " + recsSize);
            if (TextUtils.isEmpty(oldEmail)) {
                for (int i = 0; i < recsSize; i++) {
                    String emailRecord = readEmailRecord(i, pbrIndex);
                    if (TextUtils.isEmpty(emailRecord)) {
                        log("getEmailRecNumber: Got empty record.Email record num is :" + (i + 1));
                        return i + 1;
                    }
                    if (this.mEmailFlags.get(Integer.valueOf(pbrIndex)).get(i).intValue() == 0) {
                        log("Unused but non empty record.Email record num is :" + (i + 1));
                        return i + 1;
                    }
                }
            }
            log("getEmailRecNumber: no email record index found");
            return -1;
        }
        return recordIndex + 1;
    }

    private int getAnrRecNumber(int adnRecIndex, int numRecs, String oldAnr) {
        int pbrIndex = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
        if (!hasRecordIn(this.mAnrFileRecord, pbrIndex)) {
            return -1;
        }
        if (this.mAnrPresentInIap && hasRecordIn(this.mIapFileRecord, pbrIndex)) {
            byte[] record = null;
            try {
                record = this.mIapFileRecord.get(Integer.valueOf(pbrIndex)).get(recordIndex);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getAnrRecNumber");
            }
            if (record != null && record[this.mAnrTagNumberInIap] > 0) {
                int recordNumber = record[this.mAnrTagNumberInIap];
                log("getAnrRecNumber: recnum from iap is :" + recordNumber);
                return recordNumber;
            }
            int recsSize = this.mAnrFileRecord.get(Integer.valueOf(pbrIndex)).size();
            log("getAnrRecNumber: anr record size is :" + recsSize);
            if (TextUtils.isEmpty(oldAnr)) {
                for (int i = 0; i < recsSize; i++) {
                    String anrRecord = readAnrRecord(i, pbrIndex);
                    if (TextUtils.isEmpty(anrRecord)) {
                        log("getAnrRecNumber: Empty anr record. Anr record num is :" + (i + 1));
                        return i + 1;
                    }
                    if (this.mAnrFlags.get(Integer.valueOf(pbrIndex)).get(i).intValue() == 0) {
                        log("Unused but non empty record.Anr record num is :" + (i + 1));
                        return i + 1;
                    }
                }
            }
            log("getAnrRecNumber: no anr record index found");
            return -1;
        }
        return recordIndex + 1;
    }

    private byte[] buildEmailData(int length, int adnRecIndex, String email) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = -1;
        }
        if (TextUtils.isEmpty(email)) {
            log("[buildEmailData] Empty email record");
        } else {
            byte[] byteEmail = GsmAlphabet.stringToGsm8BitPacked(email);
            System.arraycopy(byteEmail, 0, data, 0, byteEmail.length);
            int pbrIndex = getPbrIndexBy(adnRecIndex);
            int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
            if (this.mEmailPresentInIap) {
                data[length - 1] = (byte) (recordIndex + 1);
            }
            log("buildEmailData: data is" + IccUtils.bytesToHexString(data));
        }
        return data;
    }

    private byte[] buildAnrData(int length, int adnRecIndex, String anr) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = -1;
        }
        if (TextUtils.isEmpty(anr)) {
            log("[buildAnrData] Empty anr record");
            return data;
        }
        data[0] = 0;
        byte[] byteAnr = PhoneNumberUtils.numberToCalledPartyBCD(anr);
        if (byteAnr == null) {
            return null;
        }
        if (byteAnr.length > 10) {
            System.arraycopy(byteAnr, 0, data, 2, 10);
            data[1] = (byte) 10;
        } else {
            System.arraycopy(byteAnr, 0, data, 2, byteAnr.length);
            data[1] = (byte) byteAnr.length;
        }
        data[13] = -1;
        data[14] = -1;
        if (length == 17) {
            int pbrIndex = getPbrIndexBy(adnRecIndex);
            int recordIndex = adnRecIndex - getInitIndexBy(pbrIndex);
            data[16] = (byte) (recordIndex + 1);
        }
        log("buildAnrData: data is" + IccUtils.bytesToHexString(data));
        return data;
    }

    private void createPbrFile(ArrayList<byte[]> records) {
        if (records == null) {
            this.mPbrFile = null;
            this.mIsPbrPresent = false;
        } else {
            this.mPbrFile = new PbrFile(records);
        }
    }

    private void putValidRecNums(int pbrIndex) {
        ArrayList<Integer> recordNums = new ArrayList<>();
        int initAdnIndex = getInitIndexBy(pbrIndex);
        log("pbr index is " + pbrIndex + ", initAdnIndex is " + initAdnIndex);
        for (int i = 0; i < this.mAdnLengthList.get(pbrIndex).intValue(); i++) {
            recordNums.add(Integer.valueOf(i + 1));
            log("valid recnum is " + (i + 1));
        }
        if (recordNums.size() == 0) {
            recordNums.add(1);
        }
        this.mRecordNums.put(Integer.valueOf(pbrIndex), recordNums);
    }

    private ArrayList<Integer> getValidRecordNums(int pbrIndex) {
        return this.mRecordNums.get(Integer.valueOf(pbrIndex));
    }

    private boolean hasValidRecords(int pbrIndex) {
        return this.mRecordNums.get(Integer.valueOf(pbrIndex)).size() > 0;
    }

    /* JADX WARN: Code duplicated, block: B:261:0x0478 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:287:0x0533 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        String oldAnr = null;
        String newAnr = null;
        String oldEmail = null;
        String newEmail = null;
        switch (msg.what) {
            case 1:
                log("Loading PBR done");
                AsyncResult ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    createPbrFile((ArrayList) ar.result);
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                    break;
                }
                return;
            case 2:
                log("Loading USIM ADN records done");
                AsyncResult ar2 = (AsyncResult) msg.obj;
                int pbrIndex = ((Integer) ar2.userObj).intValue();
                if (ar2.exception == null && this.mPbrFile != null) {
                    this.mPhoneBookRecords.addAll((ArrayList) ar2.result);
                    this.mAdnLengthList.add(pbrIndex, Integer.valueOf(((ArrayList) ar2.result).size()));
                    putValidRecNums(pbrIndex);
                } else {
                    log("can't load USIM ADN records");
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                    break;
                }
                return;
            case 3:
                log("Loading USIM IAP records done");
                AsyncResult ar3 = (AsyncResult) msg.obj;
                int pbrIndex2 = ((Integer) ar3.userObj).intValue();
                if (ar3.exception == null && this.mPbrFile != null) {
                    this.mIapFileRecord.put(Integer.valueOf(pbrIndex2), (ArrayList) ar3.result);
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                    break;
                }
                return;
            case 4:
                log("Loading USIM Email records done");
                AsyncResult ar4 = (AsyncResult) msg.obj;
                int pbrIndex3 = ((Integer) ar4.userObj).intValue();
                if (ar4.exception == null && this.mPbrFile != null) {
                    this.mEmailFileRecord.put(Integer.valueOf(pbrIndex3), (ArrayList) ar4.result);
                    log("handlemessage EVENT_EMAIL_LOAD_DONE size is: " + this.mEmailFileRecord.get(Integer.valueOf(pbrIndex3)).size());
                    for (int m = 0; m < this.mEmailFileRecord.get(Integer.valueOf(pbrIndex3)).size(); m++) {
                        this.mEmailFlagsRecord[pbrIndex3].add(0);
                    }
                    this.mEmailFlags.put(Integer.valueOf(pbrIndex3), this.mEmailFlagsRecord[pbrIndex3]);
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                    break;
                }
                return;
            case 5:
                log("Loading USIM Anr records done");
                AsyncResult ar5 = (AsyncResult) msg.obj;
                int pbrIndex4 = ((Integer) ar5.userObj).intValue();
                if (ar5.exception == null && this.mPbrFile != null) {
                    this.mAnrFileRecord.put(Integer.valueOf(pbrIndex4), (ArrayList) ar5.result);
                    log("handlemessage EVENT_ANR_LOAD_DONE size is: " + this.mAnrFileRecord.get(Integer.valueOf(pbrIndex4)).size());
                    for (int m2 = 0; m2 < this.mAnrFileRecord.get(Integer.valueOf(pbrIndex4)).size(); m2++) {
                        this.mAnrFlagsRecord[pbrIndex4].add(0);
                    }
                    this.mAnrFlags.put(Integer.valueOf(pbrIndex4), this.mAnrFlagsRecord[pbrIndex4]);
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                    break;
                }
                return;
            case 6:
                log("Loading EF_EMAIL_RECORD_SIZE_DONE");
                AsyncResult ar6 = (AsyncResult) msg.obj;
                String emails = (String) ar6.userObj;
                int adnRecIndex = msg.arg1 - 1;
                int efid = msg.arg2;
                String[] email = emails.split(",");
                if (email.length == 1) {
                    oldEmail = email[0];
                    newEmail = "";
                } else if (email.length > 1) {
                    oldEmail = email[0];
                    newEmail = email[1];
                }
                if (ar6.exception != null || this.mPbrFile == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                int[] recordSize = (int[]) ar6.result;
                int recordNumber = getEmailRecNumber(adnRecIndex, this.mPhoneBookRecords.size(), oldEmail);
                if (recordSize.length != 3 || recordNumber > recordSize[2] || recordNumber <= 0) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                byte[] data = buildEmailData(recordSize[0], adnRecIndex, newEmail);
                this.mFh.updateEFLinearFixed(efid, recordNumber, data, null, obtainMessage(8, recordNumber, adnRecIndex, data));
                this.mPendingExtLoads = 1;
                return;
            case 7:
                log("Loading EF_ANR_RECORD_SIZE_DONE");
                AsyncResult ar7 = (AsyncResult) msg.obj;
                String anrs = (String) ar7.userObj;
                int adnRecIndex2 = msg.arg1 - 1;
                int efid2 = msg.arg2;
                String[] anr = anrs.split(",");
                if (anr.length == 1) {
                    oldAnr = anr[0];
                    newAnr = "";
                } else if (anr.length > 1) {
                    oldAnr = anr[0];
                    newAnr = anr[1];
                }
                if (ar7.exception != null || this.mPbrFile == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                int[] recordSize2 = (int[]) ar7.result;
                int recordNumber2 = getAnrRecNumber(adnRecIndex2, this.mPhoneBookRecords.size(), oldAnr);
                if (recordSize2.length != 3 || recordNumber2 > recordSize2[2] || recordNumber2 <= 0) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                byte[] data2 = buildAnrData(recordSize2[0], adnRecIndex2, newAnr);
                if (data2 == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                this.mFh.updateEFLinearFixed(efid2, recordNumber2, data2, null, obtainMessage(9, recordNumber2, adnRecIndex2, data2));
                this.mPendingExtLoads = 1;
                return;
            case 8:
                log("Loading UPDATE_EMAIL_RECORD_DONE");
                AsyncResult ar8 = (AsyncResult) msg.obj;
                if (ar8.exception != null || this.mPbrFile == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                byte[] data3 = (byte[]) ar8.userObj;
                int recordNumber3 = msg.arg1;
                int pbrIndex5 = getPbrIndexBy(msg.arg2);
                log("EVENT_UPDATE_EMAIL_RECORD_DONE");
                this.mPendingExtLoads = 0;
                this.mSuccess = true;
                this.mEmailFileRecord.get(Integer.valueOf(pbrIndex5)).set(recordNumber3 - 1, data3);
                for (int i = 0; i < data3.length; i++) {
                    log("EVENT_UPDATE_EMAIL_RECORD_DONE data = " + ((int) data3[i]) + ",i is " + i);
                    if (data3[i] != -1) {
                        log("EVENT_UPDATE_EMAIL_RECORD_DONE data !=0xff");
                        this.mEmailFlags.get(Integer.valueOf(pbrIndex5)).set(recordNumber3 - 1, 1);
                        synchronized (this.mLock) {
                            this.mLock.notify();
                            break;
                        }
                        return;
                    }
                    this.mEmailFlags.get(Integer.valueOf(pbrIndex5)).set(recordNumber3 - 1, 0);
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                    return;
                }
            case 9:
                log("Loading UPDATE_ANR_RECORD_DONE");
                AsyncResult ar9 = (AsyncResult) msg.obj;
                byte[] data4 = (byte[]) ar9.userObj;
                int recordNumber4 = msg.arg1;
                int pbrIndex6 = getPbrIndexBy(msg.arg2);
                if (ar9.exception != null || this.mPbrFile == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                log("EVENT_UPDATE_ANR_RECORD_DONE");
                this.mPendingExtLoads = 0;
                this.mSuccess = true;
                this.mAnrFileRecord.get(Integer.valueOf(pbrIndex6)).set(recordNumber4 - 1, data4);
                for (byte b : data4) {
                    if (b != -1) {
                        this.mAnrFlags.get(Integer.valueOf(pbrIndex6)).set(recordNumber4 - 1, 1);
                        synchronized (this.mLock) {
                            this.mLock.notify();
                            break;
                        }
                        return;
                    }
                    this.mAnrFlags.get(Integer.valueOf(pbrIndex6)).set(recordNumber4 - 1, 0);
                }
                synchronized (this.mLock) {
                    this.mLock.notify();
                    return;
                }
            case 10:
                log("EVENT_EF_IAP_RECORD_SIZE_DONE");
                AsyncResult ar10 = (AsyncResult) msg.obj;
                int recordNumber5 = msg.arg2;
                int adnRecIndex3 = msg.arg1 - 1;
                int tag = ((Integer) ar10.userObj).intValue();
                if (ar10.exception != null || this.mPbrFile == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                int pbrIndex7 = getPbrIndexBy(adnRecIndex3);
                int efid3 = getEfidByTag(pbrIndex7, USIM_EFIAP_TAG);
                int[] recordSize3 = (int[]) ar10.result;
                int recordIndex = adnRecIndex3 - getInitIndexBy(pbrIndex7);
                log("handleIAP_RECORD_SIZE_DONE adnRecIndex is: " + adnRecIndex3 + ", recordNumber is: " + recordNumber5 + ", recordIndex is: " + recordIndex);
                if (recordSize3.length != 3 || recordIndex + 1 > recordSize3[2] || recordNumber5 == 0) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                if (hasRecordIn(this.mIapFileRecord, pbrIndex7)) {
                    byte[] data5 = this.mIapFileRecord.get(Integer.valueOf(pbrIndex7)).get(recordIndex);
                    byte[] record_data = new byte[data5.length];
                    System.arraycopy(data5, 0, record_data, 0, record_data.length);
                    switch (tag) {
                        case 196:
                            record_data[this.mAnrTagNumberInIap] = (byte) recordNumber5;
                            break;
                        case USIM_EFEMAIL_TAG /* 202 */:
                            record_data[this.mEmailTagNumberInIap] = (byte) recordNumber5;
                            break;
                    }
                    this.mPendingExtLoads = 1;
                    log(" IAP  efid= " + efid3 + ", update IAP index= " + recordIndex + " with value= " + IccUtils.bytesToHexString(record_data));
                    this.mFh.updateEFLinearFixed(efid3, recordIndex + 1, record_data, null, obtainMessage(11, adnRecIndex3, recordNumber5, record_data));
                    return;
                }
                return;
            case 11:
                log("EVENT_UPDATE_IAP_RECORD_DONE");
                AsyncResult ar11 = (AsyncResult) msg.obj;
                if (ar11.exception != null || this.mPbrFile == null) {
                    this.mSuccess = false;
                    synchronized (this.mLock) {
                        this.mLock.notify();
                        break;
                    }
                    return;
                }
                byte[] data6 = (byte[]) ar11.userObj;
                int adnRecIndex4 = msg.arg1;
                int pbrIndex8 = getPbrIndexBy(adnRecIndex4);
                int recordIndex2 = adnRecIndex4 - getInitIndexBy(pbrIndex8);
                log("handleMessage EVENT_UPDATE_IAP_RECORD_DONE recordIndex is: " + recordIndex2 + ", adnRecIndex is: " + adnRecIndex4);
                this.mPendingExtLoads = 0;
                this.mSuccess = true;
                this.mIapFileRecord.get(Integer.valueOf(pbrIndex8)).set(recordIndex2, data6);
                log("the iap email recordNumber is :" + ((int) data6[this.mEmailTagNumberInIap]));
                synchronized (this.mLock) {
                    this.mLock.notify();
                    break;
                }
                return;
            default:
                return;
        }
    }

    private class PbrFile {
        HashMap<Integer, Map<Integer, Integer>> mFileIds = new HashMap<>();

        PbrFile(ArrayList<byte[]> records) {
            int recNum = 0;
            for (byte[] record : records) {
                SimTlv recTlv = new SimTlv(record, 0, record.length);
                parseTag(recTlv, recNum);
                recNum++;
            }
        }

        void parseTag(SimTlv tlv, int recNum) {
            Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseTag: recNum=" + recNum);
            Map<Integer, Integer> val = new HashMap<>();
            do {
                int tag = tlv.getTag();
                switch (tag) {
                    case UsimPhoneBookManager.USIM_TYPE1_TAG /* 168 */:
                    case UsimPhoneBookManager.USIM_TYPE2_TAG /* 169 */:
                    case UsimPhoneBookManager.USIM_TYPE3_TAG /* 170 */:
                        byte[] data = tlv.getData();
                        SimTlv tlvEf = new SimTlv(data, 0, data.length);
                        parseEf(tlvEf, val, tag);
                        break;
                }
            } while (tlv.nextObject());
            this.mFileIds.put(Integer.valueOf(recNum), val);
        }

        void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag) {
            int tagNumberWithinParentTag = 0;
            do {
                int tag = tlv.getTag();
                if (parentTag == UsimPhoneBookManager.USIM_TYPE1_TAG && tag == UsimPhoneBookManager.USIM_EFIAP_TAG) {
                    UsimPhoneBookManager.this.mIapPresent = true;
                }
                if (parentTag == UsimPhoneBookManager.USIM_TYPE2_TAG && UsimPhoneBookManager.this.mIapPresent && tag == UsimPhoneBookManager.USIM_EFEMAIL_TAG) {
                    UsimPhoneBookManager.this.mEmailPresentInIap = true;
                    UsimPhoneBookManager.this.mEmailTagNumberInIap = tagNumberWithinParentTag;
                    UsimPhoneBookManager.this.log("parseEf: EmailPresentInIap tag = " + UsimPhoneBookManager.this.mEmailTagNumberInIap);
                }
                if (parentTag == UsimPhoneBookManager.USIM_TYPE2_TAG && UsimPhoneBookManager.this.mIapPresent && tag == 196) {
                    UsimPhoneBookManager.this.mAnrPresentInIap = true;
                    UsimPhoneBookManager.this.mAnrTagNumberInIap = tagNumberWithinParentTag;
                    UsimPhoneBookManager.this.log("parseEf: AnrPresentInIap tag = " + UsimPhoneBookManager.this.mAnrTagNumberInIap);
                }
                switch (tag) {
                    case UsimPhoneBookManager.USIM_EFADN_TAG /* 192 */:
                    case UsimPhoneBookManager.USIM_EFIAP_TAG /* 193 */:
                    case UsimPhoneBookManager.USIM_EFEXT1_TAG /* 194 */:
                    case UsimPhoneBookManager.USIM_EFSNE_TAG /* 195 */:
                    case 196:
                    case UsimPhoneBookManager.USIM_EFPBC_TAG /* 197 */:
                    case UsimPhoneBookManager.USIM_EFGRP_TAG /* 198 */:
                    case UsimPhoneBookManager.USIM_EFAAS_TAG /* 199 */:
                    case UsimPhoneBookManager.USIM_EFGSD_TAG /* 200 */:
                    case UsimPhoneBookManager.USIM_EFUID_TAG /* 201 */:
                    case UsimPhoneBookManager.USIM_EFEMAIL_TAG /* 202 */:
                    case UsimPhoneBookManager.USIM_EFCCP1_TAG /* 203 */:
                        byte[] data = tlv.getData();
                        int efid = ((data[0] & 255) << 8) | (data[1] & 255);
                        val.put(Integer.valueOf(tag), Integer.valueOf(efid));
                        Rlog.d(UsimPhoneBookManager.LOG_TAG, "parseEf.put(" + tag + "," + efid + ") parent tag:" + parentTag);
                        break;
                }
                tagNumberWithinParentTag++;
            } while (tlv.nextObject());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    public int getAnrCount() {
        int count = 0;
        int pbrIndex = this.mAnrFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            count += this.mAnrFlags.get(Integer.valueOf(j)).size();
        }
        log("getAnrCount count is: " + count);
        return count;
    }

    public int getEmailCount() {
        int count = 0;
        int pbrIndex = this.mEmailFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            count += this.mEmailFlags.get(Integer.valueOf(j)).size();
        }
        log("getEmailCount count is: " + count);
        return count;
    }

    public int getSpareAnrCount() {
        int count = 0;
        int pbrIndex = this.mAnrFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            for (int i = 0; i < this.mAnrFlags.get(Integer.valueOf(j)).size(); i++) {
                if (this.mAnrFlags.get(Integer.valueOf(j)).get(i).intValue() == 0) {
                    count++;
                }
            }
        }
        log("getSpareAnrCount count is" + count);
        return count;
    }

    public int getSpareEmailCount() {
        int count = 0;
        int pbrIndex = this.mEmailFlags.size();
        for (int j = 0; j < pbrIndex; j++) {
            for (int i = 0; i < this.mEmailFlags.get(Integer.valueOf(j)).size(); i++) {
                if (this.mEmailFlags.get(Integer.valueOf(j)).get(i).intValue() == 0) {
                    count++;
                }
            }
        }
        log("getSpareEmailCount count is: " + count);
        return count;
    }

    public int getUsimAdnCount() {
        if (this.mPhoneBookRecords == null || this.mPhoneBookRecords.isEmpty()) {
            return 0;
        }
        log("getUsimAdnCount count is" + this.mPhoneBookRecords.size());
        return this.mPhoneBookRecords.size();
    }

    public int getEmptyEmailNum_Pbrindex(int pbrindex) {
        int count = 0;
        int size = 0;
        if (this.mEmailFlags.containsKey(Integer.valueOf(pbrindex))) {
            size = this.mEmailFlags.get(Integer.valueOf(pbrindex)).size();
            for (int i = 0; i < size; i++) {
                if (this.mEmailFlags.get(Integer.valueOf(pbrindex)).get(i).intValue() == 0) {
                    count++;
                }
            }
        }
        log("getEmptyEmailNum_Pbrindex pbrIndex is: " + pbrindex + " size is: " + size + ", count is " + count);
        return count;
    }

    public int getEmptyAnrNum_Pbrindex(int pbrindex) {
        int count = 0;
        int size = 0;
        if (this.mAnrFlags.containsKey(Integer.valueOf(pbrindex))) {
            size = this.mAnrFlags.get(Integer.valueOf(pbrindex)).size();
            for (int i = 0; i < size; i++) {
                if (this.mAnrFlags.get(Integer.valueOf(pbrindex)).get(i).intValue() == 0) {
                    count++;
                }
            }
        }
        log("getEmptyAnrNum_Pbrindex pbrIndex is: " + pbrindex + " size is: " + size + ", count is " + count);
        return count;
    }
}
