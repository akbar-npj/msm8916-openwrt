package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.CommandsInterface;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public abstract class IccFileHandler extends Handler implements IccConstants {
    protected static final int COMMAND_GET_RESPONSE = 192;
    protected static final int COMMAND_READ_BINARY = 176;
    protected static final int COMMAND_READ_RECORD = 178;
    protected static final int COMMAND_SEEK = 162;
    protected static final int COMMAND_UPDATE_BINARY = 214;
    protected static final int COMMAND_UPDATE_RECORD = 220;
    protected static final int EF_TYPE_CYCLIC = 3;
    protected static final int EF_TYPE_LINEAR_FIXED = 1;
    protected static final int EF_TYPE_TRANSPARENT = 0;
    protected static final int EVENT_GET_BINARY_SIZE_DONE = 4;
    protected static final int EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE = 8;
    protected static final int EVENT_GET_RECORD_SIZE_DONE = 6;
    protected static final int EVENT_GET_RECORD_SIZE_IMG_DONE = 11;
    protected static final int EVENT_READ_BINARY_DONE = 5;
    protected static final int EVENT_READ_ICON_DONE = 10;
    protected static final int EVENT_READ_IMG_DONE = 9;
    protected static final int EVENT_READ_RECORD_DONE = 7;
    protected static final int GET_RESPONSE_EF_IMG_SIZE_BYTES = 10;
    protected static final int GET_RESPONSE_EF_SIZE_BYTES = 15;
    protected static final int READ_RECORD_MODE_ABSOLUTE = 4;
    protected static final int RESPONSE_DATA_ACCESS_CONDITION_1 = 8;
    protected static final int RESPONSE_DATA_ACCESS_CONDITION_2 = 9;
    protected static final int RESPONSE_DATA_ACCESS_CONDITION_3 = 10;
    protected static final int RESPONSE_DATA_FILE_ID_1 = 4;
    protected static final int RESPONSE_DATA_FILE_ID_2 = 5;
    protected static final int RESPONSE_DATA_FILE_SIZE_1 = 2;
    protected static final int RESPONSE_DATA_FILE_SIZE_2 = 3;
    protected static final int RESPONSE_DATA_FILE_STATUS = 11;
    protected static final int RESPONSE_DATA_FILE_TYPE = 6;
    protected static final int RESPONSE_DATA_LENGTH = 12;
    protected static final int RESPONSE_DATA_RECORD_LENGTH = 14;
    protected static final int RESPONSE_DATA_RFU_1 = 0;
    protected static final int RESPONSE_DATA_RFU_2 = 1;
    protected static final int RESPONSE_DATA_RFU_3 = 7;
    protected static final int RESPONSE_DATA_STRUCTURE = 13;
    protected static final int TYPE_DF = 2;
    protected static final int TYPE_EF = 4;
    protected static final int TYPE_MF = 1;
    protected static final int TYPE_RFU = 0;
    protected final String mAid;
    protected final CommandsInterface mCi;
    protected final UiccCardApplication mParentApp;
    protected boolean mUseLocalPb = false;

    protected abstract String getEFPath(int i);

    protected abstract void logd(String str);

    protected abstract void loge(String str);

    static class LoadLinearFixedContext {
        int mCount;
        int mCountLoadrecords;
        int mCountRecords;
        int mEfid;
        boolean mLoadAll;
        boolean mLoadPart;
        Message mOnLoaded;
        String mPath;
        int mRecordNum;
        ArrayList<Integer> mRecordNums;
        int mRecordSize;
        ArrayList<byte[]> results;

        LoadLinearFixedContext(int efid, int recordNum, Message onLoaded) {
            this.mEfid = efid;
            this.mRecordNum = recordNum;
            this.mOnLoaded = onLoaded;
            this.mLoadAll = false;
            this.mLoadPart = false;
            this.mPath = null;
        }

        LoadLinearFixedContext(int efid, int recordNum, String path, Message onLoaded) {
            this.mEfid = efid;
            this.mRecordNum = recordNum;
            this.mOnLoaded = onLoaded;
            this.mLoadAll = false;
            this.mLoadPart = false;
            this.mPath = path;
        }

        LoadLinearFixedContext(int efid, String path, Message onLoaded) {
            this.mEfid = efid;
            this.mRecordNum = 1;
            this.mLoadAll = true;
            this.mLoadPart = false;
            this.mOnLoaded = onLoaded;
            this.mPath = path;
        }

        LoadLinearFixedContext(int efid, Message onLoaded) {
            this.mEfid = efid;
            this.mRecordNum = 1;
            this.mLoadAll = true;
            this.mLoadPart = false;
            this.mOnLoaded = onLoaded;
            this.mPath = null;
        }

        LoadLinearFixedContext(int efid, ArrayList<Integer> recordNums, String path, Message onLoaded) {
            this.mEfid = efid;
            this.mRecordNum = recordNums.get(0).intValue();
            this.mLoadAll = false;
            this.mLoadPart = true;
            this.mRecordNums = new ArrayList<>();
            this.mRecordNums.addAll(recordNums);
            this.mCount = 0;
            this.mCountLoadrecords = recordNums.size();
            this.mOnLoaded = onLoaded;
            this.mPath = path;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void initLCResults(int size) {
            this.results = new ArrayList<>(size);
            byte[] data = new byte[this.mRecordSize];
            for (int i = 0; i < this.mRecordSize; i++) {
                data[i] = -1;
            }
            for (int i2 = 0; i2 < size; i2++) {
                this.results.add(data);
            }
        }
    }

    protected IccFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        this.mParentApp = app;
        this.mAid = aid;
        this.mCi = ci;
    }

    public void dispose() {
    }

    public void loadEFLinearFixed(int fileid, String path, int recordNum, Message onLoaded) {
        Message response = obtainMessage(6, new LoadLinearFixedContext(fileid, recordNum, path, onLoaded));
        this.mCi.iccIOForApp(COMMAND_GET_RESPONSE, fileid, path, 0, 0, 15, null, null, this.mAid, response);
    }

    public void loadEFLinearFixed(int fileid, int recordNum, Message onLoaded) {
        loadEFLinearFixed(fileid, getEFPath(fileid), recordNum, onLoaded);
    }

    public void loadEFImgLinearFixed(int recordNum, Message onLoaded) {
        Message response = obtainMessage(11, new LoadLinearFixedContext(20256, recordNum, onLoaded));
        this.mCi.iccIOForApp(COMMAND_GET_RESPONSE, 20256, getEFPath(20256), recordNum, 4, 10, null, null, this.mAid, response);
    }

    public void getEFLinearRecordSize(int fileid, String path, Message onLoaded) {
        Message response = obtainMessage(8, new LoadLinearFixedContext(fileid, path, onLoaded));
        this.mCi.iccIOForApp(COMMAND_GET_RESPONSE, fileid, path, 0, 0, 15, null, null, this.mAid, response);
    }

    public void getEFLinearRecordSize(int fileid, Message onLoaded) {
        getEFLinearRecordSize(fileid, getEFPath(fileid), onLoaded);
    }

    public void loadEFLinearFixedAll(int fileid, Message onLoaded) {
        loadEFLinearFixedAll(fileid, getEFPath(fileid), onLoaded);
    }

    public void loadEFLinearFixedAll(int fileid, String path, Message onLoaded) {
        Message response = obtainMessage(6, new LoadLinearFixedContext(fileid, path, onLoaded));
        this.mCi.iccIOForApp(COMMAND_GET_RESPONSE, fileid, path, 0, 0, 15, null, null, this.mAid, response);
    }

    public void loadEFLinearFixedPart(int fileid, ArrayList<Integer> recordNums, Message onLoaded) {
        Message response = obtainMessage(6, new LoadLinearFixedContext(fileid, recordNums, getEFPath(fileid), onLoaded));
        this.mCi.iccIOForApp(COMMAND_GET_RESPONSE, fileid, getEFPath(fileid), 0, 0, 15, null, null, this.mAid, response);
    }

    public void loadEFTransparent(int fileid, Message onLoaded) {
        Message response = obtainMessage(4, fileid, 0, onLoaded);
        this.mCi.iccIOForApp(COMMAND_GET_RESPONSE, fileid, getEFPath(fileid), 0, 0, 15, null, null, this.mAid, response);
    }

    public void loadEFTransparent(int fileid, int size, Message onLoaded) {
        Message response = obtainMessage(5, fileid, 0, onLoaded);
        this.mCi.iccIOForApp(176, fileid, getEFPath(fileid), 0, 0, size, null, null, this.mAid, response);
    }

    public void loadEFImgTransparent(int fileid, int highOffset, int lowOffset, int length, Message onLoaded) {
        Message response = obtainMessage(10, fileid, 0, onLoaded);
        logd("IccFileHandler: loadEFImgTransparent fileid = " + fileid + " filePath = " + getEFPath(20256) + " highOffset = " + highOffset + " lowOffset = " + lowOffset + " length = " + length);
        this.mCi.iccIOForApp(176, fileid, getEFPath(20256), highOffset, lowOffset, length, null, null, this.mAid, response);
    }

    public void updateEFLinearFixed(int fileid, String path, int recordNum, byte[] data, String pin2, Message onComplete) {
        this.mCi.iccIOForApp(COMMAND_UPDATE_RECORD, fileid, path, recordNum, 4, data.length, IccUtils.bytesToHexString(data), pin2, this.mAid, onComplete);
    }

    public void updateEFLinearFixed(int fileid, int recordNum, byte[] data, String pin2, Message onComplete) {
        this.mCi.iccIOForApp(COMMAND_UPDATE_RECORD, fileid, getEFPath(fileid), recordNum, 4, data.length, IccUtils.bytesToHexString(data), pin2, this.mAid, onComplete);
    }

    public void updateEFTransparent(int fileid, byte[] data, Message onComplete) {
        this.mCi.iccIOForApp(214, fileid, getEFPath(fileid), 0, 0, data.length, IccUtils.bytesToHexString(data), null, this.mAid, onComplete);
    }

    private void sendResult(Message response, Object result, Throwable ex) {
        if (response != null) {
            AsyncResult.forMessage(response, result, ex);
            response.sendToTarget();
        }
    }

    private boolean processException(Message response, AsyncResult ar) {
        IccIoResult result = (IccIoResult) ar.result;
        if (ar.exception != null) {
            sendResult(response, null, ar.exception);
            return true;
        }
        IccException iccException = result.getException();
        if (iccException == null) {
            return false;
        }
        sendResult(response, null, iccException);
        return true;
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        try {
            switch (msg.what) {
                case 4:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    Message response = (Message) ar.userObj;
                    IccIoResult result = (IccIoResult) ar.result;
                    if (!processException(response, (AsyncResult) msg.obj)) {
                        byte[] data = result.payload;
                        int fileid = msg.arg1;
                        if (4 != data[6]) {
                            throw new IccFileTypeMismatch();
                        }
                        if (data[13] != 0) {
                            throw new IccFileTypeMismatch();
                        }
                        int size = ((data[2] & 255) << 8) + (data[3] & 255);
                        this.mCi.iccIOForApp(176, fileid, getEFPath(fileid), 0, 0, size, null, null, this.mAid, obtainMessage(5, fileid, 0, response));
                        return;
                    }
                    return;
                case 5:
                case 10:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    Message response2 = (Message) ar2.userObj;
                    IccIoResult result2 = (IccIoResult) ar2.result;
                    if (!processException(response2, (AsyncResult) msg.obj)) {
                        sendResult(response2, result2.payload, null);
                        return;
                    }
                    return;
                case 6:
                case 11:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    LoadLinearFixedContext lc = (LoadLinearFixedContext) ar3.userObj;
                    IccIoResult result3 = (IccIoResult) ar3.result;
                    if (!processException(lc.mOnLoaded, (AsyncResult) msg.obj)) {
                        byte[] data2 = result3.payload;
                        String path = lc.mPath;
                        if (4 != data2[6]) {
                            throw new IccFileTypeMismatch();
                        }
                        if (1 != data2[13]) {
                            throw new IccFileTypeMismatch();
                        }
                        lc.mRecordSize = data2[14] & 255;
                        int size2 = ((data2[2] & 255) << 8) + (data2[3] & 255);
                        lc.mCountRecords = size2 / lc.mRecordSize;
                        if (lc.mLoadAll) {
                            lc.results = new ArrayList<>(lc.mCountRecords);
                        } else if (lc.mLoadPart) {
                            lc.initLCResults(lc.mCountRecords);
                        }
                        if (path == null) {
                            path = getEFPath(lc.mEfid);
                        }
                        this.mCi.iccIOForApp(COMMAND_READ_RECORD, lc.mEfid, path, lc.mRecordNum, 4, lc.mRecordSize, null, null, this.mAid, obtainMessage(7, lc));
                        return;
                    }
                    return;
                case 7:
                case 9:
                    AsyncResult ar4 = (AsyncResult) msg.obj;
                    LoadLinearFixedContext lc2 = (LoadLinearFixedContext) ar4.userObj;
                    IccIoResult result4 = (IccIoResult) ar4.result;
                    Message response3 = lc2.mOnLoaded;
                    String path2 = lc2.mPath;
                    if (!processException(response3, (AsyncResult) msg.obj)) {
                        if (lc2.mLoadAll) {
                            lc2.results.add(result4.payload);
                            lc2.mRecordNum++;
                            if (lc2.mRecordNum > lc2.mCountRecords) {
                                sendResult(response3, lc2.results, null);
                                return;
                            }
                            if (path2 == null) {
                                path2 = getEFPath(lc2.mEfid);
                            }
                            this.mCi.iccIOForApp(COMMAND_READ_RECORD, lc2.mEfid, path2, lc2.mRecordNum, 4, lc2.mRecordSize, null, null, this.mAid, obtainMessage(7, lc2));
                            return;
                        }
                        if (lc2.mLoadPart) {
                            lc2.results.set(lc2.mRecordNum - 1, result4.payload);
                            lc2.mCount++;
                            if (lc2.mCount < lc2.mCountLoadrecords) {
                                lc2.mRecordNum = lc2.mRecordNums.get(lc2.mCount).intValue();
                                if (lc2.mRecordNum <= lc2.mCountRecords) {
                                    if (path2 == null) {
                                        path2 = getEFPath(lc2.mEfid);
                                    }
                                    this.mCi.iccIOForApp(COMMAND_READ_RECORD, lc2.mEfid, path2, lc2.mRecordNum, 4, lc2.mRecordSize, null, null, this.mAid, obtainMessage(7, lc2));
                                    return;
                                }
                                sendResult(response3, lc2.results, null);
                                return;
                            }
                            sendResult(response3, lc2.results, null);
                            return;
                        }
                        sendResult(response3, result4.payload, null);
                        return;
                    }
                    return;
                case 8:
                    AsyncResult ar5 = (AsyncResult) msg.obj;
                    LoadLinearFixedContext lc3 = (LoadLinearFixedContext) ar5.userObj;
                    IccIoResult result5 = (IccIoResult) ar5.result;
                    Message response4 = lc3.mOnLoaded;
                    if (!processException(response4, (AsyncResult) msg.obj)) {
                        byte[] data3 = result5.payload;
                        if (4 != data3[6] || 1 != data3[13]) {
                            throw new IccFileTypeMismatch();
                        }
                        int[] recordSize = new int[3];
                        recordSize[0] = data3[14] & 255;
                        recordSize[1] = ((data3[2] & 255) << 8) + (data3[3] & 255);
                        recordSize[2] = recordSize[1] / recordSize[0];
                        sendResult(response4, recordSize, null);
                        return;
                    }
                    return;
                default:
                    return;
            }
        } catch (Exception exc) {
            if (0 != 0) {
                sendResult(null, null, exc);
            } else {
                loge("uncaught exception" + exc);
            }
        }
    }

    protected String getCommonIccEFPath(int efid) {
        switch (efid) {
            case IccConstants.EF_PL /* 12037 */:
            case IccConstants.EF_ICCID /* 12258 */:
                return IccConstants.MF_SIM;
            case 20256:
                return "3F007F105F50";
            case IccConstants.EF_PBR /* 20272 */:
                if (this.mUseLocalPb) {
                    return "3F007FFF5F3A";
                }
                return "3F007F105F3A";
            case 28474:
            case IccConstants.EF_FDN /* 28475 */:
            case IccConstants.EF_MSISDN /* 28480 */:
            case IccConstants.EF_SDN /* 28489 */:
            case IccConstants.EF_EXT1 /* 28490 */:
            case IccConstants.EF_EXT2 /* 28491 */:
            case IccConstants.EF_EXT3 /* 28492 */:
                return "3F007F10";
            default:
                return null;
        }
    }

    public void useLocalPb(boolean useLocalPb) {
        logd("Using " + (useLocalPb ? "Local" : "Global") + " Phonebook");
        this.mUseLocalPb = useLocalPb;
    }
}
