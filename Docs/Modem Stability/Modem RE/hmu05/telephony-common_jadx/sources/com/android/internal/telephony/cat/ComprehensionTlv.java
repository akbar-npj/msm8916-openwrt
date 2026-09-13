package com.android.internal.telephony.cat;

import android.telephony.Rlog;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
class ComprehensionTlv {
    private static final String LOG_TAG = "ComprehensionTlv";
    private boolean mCr;
    private int mLength;
    private byte[] mRawValue;
    private int mTag;
    private int mValueIndex;

    protected ComprehensionTlv(int tag, boolean cr, int length, byte[] data, int valueIndex) {
        this.mTag = tag;
        this.mCr = cr;
        this.mLength = length;
        this.mValueIndex = valueIndex;
        this.mRawValue = data;
    }

    public int getTag() {
        return this.mTag;
    }

    public boolean isComprehensionRequired() {
        return this.mCr;
    }

    public int getLength() {
        return this.mLength;
    }

    public int getValueIndex() {
        return this.mValueIndex;
    }

    public byte[] getRawValue() {
        return this.mRawValue;
    }

    public static List<ComprehensionTlv> decodeMany(byte[] data, int startIndex) throws ResultException {
        ArrayList<ComprehensionTlv> items = new ArrayList<>();
        int endIndex = data.length;
        while (startIndex < endIndex) {
            ComprehensionTlv ctlv = decode(data, startIndex);
            if (ctlv != null) {
                items.add(ctlv);
                startIndex = ctlv.mValueIndex + ctlv.mLength;
            } else {
                CatLog.d(LOG_TAG, "decodeMany: ctlv is null, stop decoding");
                break;
            }
        }
        return items;
    }

    public static ComprehensionTlv decode(byte[] data, int startIndex) throws ResultException {
        int curIndex;
        boolean cr;
        int tag;
        int length;
        int endIndex = data.length;
        int curIndex2 = startIndex + 1;
        try {
            int temp = data[startIndex] & 255;
            switch (temp) {
                case 0:
                case 128:
                case 255:
                    Rlog.d("CAT     ", "decode: unexpected first tag byte=" + Integer.toHexString(temp) + ", startIndex=" + startIndex + " curIndex=" + curIndex2 + " endIndex=" + endIndex);
                    return null;
                case 127:
                    int tag2 = ((data[curIndex2] & 255) << 8) | (data[curIndex2 + 1] & 255);
                    cr = (32768 & tag2) != 0;
                    tag = tag2 & (-32769);
                    curIndex2 += 2;
                    break;
                default:
                    cr = (temp & 128) != 0;
                    tag = temp & (-129);
                    break;
            }
            curIndex = curIndex2 + 1;
            try {
                int temp2 = data[curIndex2] & 255;
                if (temp2 < 128) {
                    length = temp2;
                } else if (temp2 == 129) {
                    int curIndex3 = curIndex + 1;
                    length = data[curIndex] & 255;
                    if (length < 128) {
                        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "length < 0x80 length=" + Integer.toHexString(length) + " startIndex=" + startIndex + " curIndex=" + curIndex3 + " endIndex=" + endIndex);
                    }
                    curIndex = curIndex3;
                } else if (temp2 == 130) {
                    length = ((data[curIndex] & 255) << 8) | (data[curIndex + 1] & 255);
                    curIndex += 2;
                    if (length < 256) {
                        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "two byte length < 0x100 length=" + Integer.toHexString(length) + " startIndex=" + startIndex + " curIndex=" + curIndex + " endIndex=" + endIndex);
                    }
                } else if (temp2 == 131) {
                    length = ((data[curIndex] & 255) << 16) | ((data[curIndex + 1] & 255) << 8) | (data[curIndex + 2] & 255);
                    curIndex += 3;
                    if (length < 65536) {
                        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "three byte length < 0x10000 length=0x" + Integer.toHexString(length) + " startIndex=" + startIndex + " curIndex=" + curIndex + " endIndex=" + endIndex);
                    }
                } else {
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "Bad length modifer=" + temp2 + " startIndex=" + startIndex + " curIndex=" + curIndex + " endIndex=" + endIndex);
                }
                return new ComprehensionTlv(tag, cr, length, data, curIndex);
            } catch (IndexOutOfBoundsException e) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "IndexOutOfBoundsException startIndex=" + startIndex + " curIndex=" + curIndex + " endIndex=" + endIndex);
            }
        } catch (IndexOutOfBoundsException e2) {
            curIndex = curIndex2;
        }
    }
}
