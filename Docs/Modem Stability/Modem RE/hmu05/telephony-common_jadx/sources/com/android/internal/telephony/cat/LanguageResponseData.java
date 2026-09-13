package com.android.internal.telephony.cat;

import com.android.internal.telephony.GsmAlphabet;
import java.io.ByteArrayOutputStream;

/* JADX INFO: compiled from: ResponseData.java */
/* JADX INFO: loaded from: classes.dex */
class LanguageResponseData extends ResponseData {
    private String mLang;

    public LanguageResponseData(String lang) {
        this.mLang = lang;
    }

    @Override // com.android.internal.telephony.cat.ResponseData
    public void format(ByteArrayOutputStream buf) {
        byte[] data;
        if (buf != null) {
            int tag = ComprehensionTlvTag.LANGUAGE.value() | 128;
            buf.write(tag);
            if (this.mLang != null && this.mLang.length() > 0) {
                data = GsmAlphabet.stringToGsm8BitPacked(this.mLang);
            } else {
                data = new byte[0];
            }
            buf.write(data.length);
            byte[] arr$ = data;
            for (byte b : arr$) {
                buf.write(b);
            }
        }
    }
}
