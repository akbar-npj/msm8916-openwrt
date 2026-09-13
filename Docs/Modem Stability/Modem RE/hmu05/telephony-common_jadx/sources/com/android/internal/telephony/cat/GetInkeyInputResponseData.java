package com.android.internal.telephony.cat;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/* JADX INFO: compiled from: ResponseData.java */
/* JADX INFO: loaded from: classes.dex */
class GetInkeyInputResponseData extends ResponseData {
    protected static final byte GET_INKEY_NO = 0;
    protected static final byte GET_INKEY_YES = 1;
    public String mInData;
    private boolean mIsPacked;
    private boolean mIsUcs2;
    private boolean mIsYesNo;
    private boolean mYesNoResponse;

    public GetInkeyInputResponseData(String inData, boolean ucs2, boolean packed) {
        this.mIsUcs2 = ucs2;
        this.mIsPacked = packed;
        this.mInData = inData;
        this.mIsYesNo = false;
    }

    public GetInkeyInputResponseData(boolean yesNoResponse) {
        this.mIsUcs2 = false;
        this.mIsPacked = false;
        this.mInData = "";
        this.mIsYesNo = true;
        this.mYesNoResponse = yesNoResponse;
    }

    @Override // com.android.internal.telephony.cat.ResponseData
    public void format(ByteArrayOutputStream buf) {
        byte[] data;
        byte b = GET_INKEY_YES;
        if (buf != null) {
            int tag = ComprehensionTlvTag.TEXT_STRING.value() | 128;
            buf.write(tag);
            if (this.mIsYesNo) {
                data = new byte[1];
                if (!this.mYesNoResponse) {
                    b = 0;
                }
                data[0] = b;
            } else if (this.mInData != null && this.mInData.length() > 0) {
                try {
                    if (this.mIsUcs2) {
                        data = this.mInData.getBytes("UTF-16BE");
                    } else if (this.mIsPacked) {
                        int size = this.mInData.length();
                        byte[] tempData = GsmAlphabet.stringToGsm7BitPacked(this.mInData, 0, 0);
                        data = new byte[size];
                        System.arraycopy(tempData, 1, data, 0, size);
                    } else {
                        data = GsmAlphabet.stringToGsm8BitPacked(this.mInData);
                    }
                } catch (EncodeException e) {
                    data = new byte[0];
                } catch (UnsupportedEncodingException e2) {
                    data = new byte[0];
                }
            } else {
                data = new byte[0];
            }
            writeLength(buf, data.length + 1);
            if (this.mIsUcs2) {
                buf.write(8);
            } else if (this.mIsPacked) {
                buf.write(0);
            } else {
                buf.write(4);
            }
            byte[] arr$ = data;
            for (byte b2 : arr$) {
                buf.write(b2);
            }
        }
    }
}
