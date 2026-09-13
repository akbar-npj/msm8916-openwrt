package com.android.internal.telephony.cat;

import java.util.List;

/* JADX INFO: loaded from: classes.dex */
class BerTlv {
    public static final int BER_EVENT_DOWNLOAD_TAG = 214;
    public static final int BER_MENU_SELECTION_TAG = 211;
    public static final int BER_PROACTIVE_COMMAND_TAG = 208;
    public static final int BER_UNKNOWN_TAG = 0;
    private List<ComprehensionTlv> mCompTlvs;
    private boolean mLengthValid;
    private int mTag;

    private BerTlv(int tag, List<ComprehensionTlv> ctlvs, boolean lengthValid) {
        this.mTag = 0;
        this.mCompTlvs = null;
        this.mLengthValid = true;
        this.mTag = tag;
        this.mCompTlvs = ctlvs;
        this.mLengthValid = lengthValid;
    }

    public List<ComprehensionTlv> getComprehensionTlvs() {
        return this.mCompTlvs;
    }

    public int getTag() {
        return this.mTag;
    }

    public boolean isLengthValid() {
        return this.mLengthValid;
    }

    public static BerTlv decode(byte[] data) throws ResultException {
        int curIndex;
        int endIndex = data.length;
        int length = 0;
        boolean isLengthValid = true;
        int curIndex2 = 0 + 1;
        try {
            int tag = data[0] & 255;
            if (tag == 208) {
                curIndex = curIndex2 + 1;
                try {
                    int temp = data[curIndex2] & 255;
                    if (temp < 128) {
                        length = temp;
                    } else if (temp == 129) {
                        int curIndex3 = curIndex + 1;
                        int temp2 = data[curIndex] & 255;
                        if (temp2 < 128) {
                            throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "length < 0x80 length=" + Integer.toHexString(0) + " curIndex=" + curIndex3 + " endIndex=" + endIndex);
                        }
                        length = temp2;
                        curIndex = curIndex3;
                    } else {
                        throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "Expected first byte to be length or a length tag and < 0x81 byte= " + Integer.toHexString(temp) + " curIndex=" + curIndex + " endIndex=" + endIndex);
                    }
                } catch (ResultException e) {
                    e = e;
                    throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, e.explanation());
                } catch (IndexOutOfBoundsException e2) {
                    throw new ResultException(ResultCode.REQUIRED_VALUES_MISSING, "IndexOutOfBoundsException  curIndex=" + curIndex + " endIndex=" + endIndex);
                }
            } else if (ComprehensionTlvTag.COMMAND_DETAILS.value() == (tag & (-129))) {
                tag = 0;
                curIndex = 0;
            } else {
                curIndex = curIndex2;
            }
            if (endIndex - curIndex < length) {
                throw new ResultException(ResultCode.CMD_DATA_NOT_UNDERSTOOD, "Command had extra data endIndex=" + endIndex + " curIndex=" + curIndex + " length=" + length);
            }
            List<ComprehensionTlv> ctlvs = ComprehensionTlv.decodeMany(data, curIndex);
            if (tag == 208) {
                int totalLength = 0;
                for (ComprehensionTlv item : ctlvs) {
                    int itemLength = item.getLength();
                    if (itemLength >= 128 && itemLength <= 255) {
                        totalLength += itemLength + 3;
                    } else if (itemLength >= 0 && itemLength < 128) {
                        totalLength += itemLength + 2;
                    } else {
                        isLengthValid = false;
                        break;
                    }
                }
                if (length != totalLength) {
                    isLengthValid = false;
                }
            }
            return new BerTlv(tag, ctlvs, isLengthValid);
        } catch (ResultException e3) {
            e = e3;
        } catch (IndexOutOfBoundsException e4) {
            curIndex = curIndex2;
        }
    }
}
