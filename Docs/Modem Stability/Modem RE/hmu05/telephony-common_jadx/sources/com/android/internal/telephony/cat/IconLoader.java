package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.android.internal.telephony.uicc.IccFileHandler;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public class IconLoader extends Handler {
    private static final int CLUT_ENTRY_SIZE = 3;
    private static final int CLUT_LOCATION_OFFSET = 4;
    private static final int EVENT_READ_CLUT_DONE = 3;
    private static final int EVENT_READ_EF_IMG_RECOED_DONE = 1;
    private static final int EVENT_READ_ICON_DONE = 2;
    private static final int STATE_MULTI_ICONS = 2;
    private static final int STATE_SINGLE_ICON = 1;
    private static IconLoader sLoader = null;
    private static HandlerThread sThread = null;
    private Bitmap mCurrentIcon;
    private int mCurrentRecordIndex;
    private Message mEndMsg;
    private byte[] mIconData;
    private Bitmap[] mIcons;
    protected HashMap<Integer, Bitmap> mIconsCache;
    private ImageDescriptor mId;
    private int mRecordNumber;
    private int[] mRecordNumbers;
    protected IccFileHandler mSimFH;
    private int mState;

    private IconLoader(Looper looper, IccFileHandler fh) {
        super(looper);
        this.mState = 1;
        this.mId = null;
        this.mCurrentIcon = null;
        this.mSimFH = null;
        this.mEndMsg = null;
        this.mIconData = null;
        this.mRecordNumbers = null;
        this.mCurrentRecordIndex = 0;
        this.mIcons = null;
        this.mIconsCache = null;
        this.mSimFH = fh;
        this.mIconsCache = new HashMap<>(50);
    }

    protected IconLoader() {
        this.mState = 1;
        this.mId = null;
        this.mCurrentIcon = null;
        this.mSimFH = null;
        this.mEndMsg = null;
        this.mIconData = null;
        this.mRecordNumbers = null;
        this.mCurrentRecordIndex = 0;
        this.mIcons = null;
        this.mIconsCache = null;
    }

    static IconLoader getInstance(Handler caller, IccFileHandler fh) {
        if (sLoader != null) {
            return sLoader;
        }
        if (fh != null) {
            sThread = new HandlerThread("Cat Icon Loader");
            sThread.start();
            return new IconLoader(sThread.getLooper(), fh);
        }
        return null;
    }

    void loadIcons(int[] recordNumbers, Message msg) {
        if (recordNumbers != null && recordNumbers.length != 0 && msg != null) {
            this.mEndMsg = msg;
            this.mIcons = new Bitmap[recordNumbers.length];
            this.mRecordNumbers = recordNumbers;
            this.mCurrentRecordIndex = 0;
            this.mState = 2;
            startLoadingIcon(recordNumbers[0]);
        }
    }

    void loadIcon(int recordNumber, Message msg) {
        if (msg != null) {
            this.mEndMsg = msg;
            this.mState = 1;
            startLoadingIcon(recordNumber);
        }
    }

    private void startLoadingIcon(int recordNumber) {
        this.mId = null;
        this.mIconData = null;
        this.mCurrentIcon = null;
        this.mRecordNumber = recordNumber;
        if (this.mIconsCache.containsKey(Integer.valueOf(recordNumber))) {
            this.mCurrentIcon = this.mIconsCache.get(Integer.valueOf(recordNumber));
            postIcon();
        } else {
            readId();
        }
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        try {
            switch (msg.what) {
                case 1:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (handleImageDescriptor((byte[]) ar.result)) {
                        readIconData();
                        return;
                    }
                    throw new Exception("Unable to parse image descriptor");
                case 2:
                    AsyncResult ar2 = (AsyncResult) msg.obj;
                    byte[] rawData = (byte[]) ar2.result;
                    if (this.mId.mCodingScheme == 17) {
                        this.mCurrentIcon = parseToBnW(rawData, rawData.length);
                        this.mIconsCache.put(Integer.valueOf(this.mRecordNumber), this.mCurrentIcon);
                        postIcon();
                        return;
                    } else if (this.mId.mCodingScheme == 33) {
                        this.mIconData = rawData;
                        readClut();
                        return;
                    } else {
                        postIcon();
                        return;
                    }
                case 3:
                    AsyncResult ar3 = (AsyncResult) msg.obj;
                    byte[] clut = (byte[]) ar3.result;
                    this.mCurrentIcon = parseToRGB(this.mIconData, this.mIconData.length, false, clut);
                    this.mIconsCache.put(Integer.valueOf(this.mRecordNumber), this.mCurrentIcon);
                    postIcon();
                    return;
                default:
                    return;
            }
        } catch (Exception e) {
            CatLog.d(this, "Icon load failed");
            postIcon();
        }
    }

    private boolean handleImageDescriptor(byte[] rawData) {
        this.mId = ImageDescriptor.parse(rawData, 1);
        return this.mId != null;
    }

    private void readClut() {
        int length = this.mIconData[3] * 3;
        Message msg = obtainMessage(3);
        this.mSimFH.loadEFImgTransparent(this.mId.mImageId, this.mIconData[4], this.mIconData[5], length, msg);
    }

    private void readId() {
        if (this.mRecordNumber < 0) {
            this.mCurrentIcon = null;
            postIcon();
        } else {
            Message msg = obtainMessage(1);
            this.mSimFH.loadEFImgLinearFixed(this.mRecordNumber, msg);
        }
    }

    private void readIconData() {
        Message msg = obtainMessage(2);
        this.mSimFH.loadEFImgTransparent(this.mId.mImageId, 0, 0, this.mId.mLength, msg);
    }

    private void postIcon() {
        if (this.mState == 1) {
            this.mEndMsg.obj = this.mCurrentIcon;
            this.mEndMsg.sendToTarget();
        } else if (this.mState == 2) {
            Bitmap[] bitmapArr = this.mIcons;
            int i = this.mCurrentRecordIndex;
            this.mCurrentRecordIndex = i + 1;
            bitmapArr[i] = this.mCurrentIcon;
            if (this.mCurrentRecordIndex < this.mRecordNumbers.length) {
                startLoadingIcon(this.mRecordNumbers[this.mCurrentRecordIndex]);
                return;
            }
            this.mEndMsg.obj = this.mIcons;
            this.mEndMsg.sendToTarget();
        }
    }

    public static Bitmap parseToBnW(byte[] data, int length) {
        int valueIndex;
        int valueIndex2 = 0 + 1;
        int width = data[0] & 255;
        int height = data[valueIndex2] & 255;
        int numOfPixels = width * height;
        int[] pixels = new int[numOfPixels];
        int bitIndex = 7;
        byte currentByte = 0;
        int pixelIndex = 0;
        int valueIndex3 = valueIndex2 + 1;
        while (pixelIndex < numOfPixels) {
            if (pixelIndex % 8 == 0) {
                valueIndex = valueIndex3 + 1;
                currentByte = data[valueIndex3];
                bitIndex = 7;
            } else {
                valueIndex = valueIndex3;
            }
            pixels[pixelIndex] = bitToBnW((currentByte >> bitIndex) & 1);
            bitIndex--;
            pixelIndex++;
            valueIndex3 = valueIndex;
        }
        if (pixelIndex != numOfPixels) {
            CatLog.d("IconLoader", "parseToBnW; size error");
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static int bitToBnW(int bit) {
        return bit == 1 ? -1 : -16777216;
    }

    public static Bitmap parseToRGB(byte[] data, int length, boolean transparency, byte[] clut) {
        int valueIndex;
        int valueIndex2 = 0 + 1;
        int width = data[0] & 255;
        int valueIndex3 = valueIndex2 + 1;
        int height = data[valueIndex2] & 255;
        int valueIndex4 = valueIndex3 + 1;
        int bitsPerImg = data[valueIndex3] & 255;
        int i = valueIndex4 + 1;
        int numOfClutEntries = data[valueIndex4] & 255;
        if (true == transparency) {
            clut[numOfClutEntries - 1] = 0;
        }
        int numOfPixels = width * height;
        int[] pixels = new int[numOfPixels];
        int bitsStartOffset = 8 - bitsPerImg;
        int bitIndex = bitsStartOffset;
        int valueIndex5 = 6 + 1;
        byte currentByte = data[6];
        int mask = getMask(bitsPerImg);
        boolean bitsOverlaps = 8 % bitsPerImg == 0;
        int pixelIndex = 0;
        while (pixelIndex < numOfPixels) {
            if (bitIndex < 0) {
                valueIndex = valueIndex5 + 1;
                currentByte = data[valueIndex5];
                bitIndex = bitsOverlaps ? bitsStartOffset : bitIndex * (-1);
            } else {
                valueIndex = valueIndex5;
            }
            int clutEntry = (currentByte >> bitIndex) & mask;
            int clutIndex = clutEntry * 3;
            pixels[pixelIndex] = Color.rgb((int) clut[clutIndex], (int) clut[clutIndex + 1], (int) clut[clutIndex + 2]);
            bitIndex -= bitsPerImg;
            pixelIndex++;
            valueIndex5 = valueIndex;
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static int getMask(int numOfBits) {
        switch (numOfBits) {
            case 1:
                return 1;
            case 2:
                return 3;
            case 3:
                return 7;
            case 4:
                return 15;
            case 5:
                return 31;
            case 6:
                return 63;
            case 7:
                return 127;
            case 8:
                return 255;
            default:
                return 0;
        }
    }

    public void dispose() {
        this.mSimFH = null;
        if (sThread != null) {
            sThread.quit();
            sThread = null;
        }
        this.mIconsCache = null;
        sLoader = null;
    }
}
