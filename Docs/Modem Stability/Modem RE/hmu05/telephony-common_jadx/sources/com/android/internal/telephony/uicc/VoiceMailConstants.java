package com.android.internal.telephony.uicc;

import android.os.Environment;
import android.telephony.Rlog;
import android.util.Xml;
import com.android.internal.util.XmlUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/* JADX INFO: loaded from: classes.dex */
class VoiceMailConstants {
    static final String LOG_TAG = "VoiceMailConstants";
    static final int NAME = 0;
    static final int NUMBER = 1;
    static final String PARTNER_VOICEMAIL_PATH = "etc/voicemail-conf.xml";
    static final int SIZE = 3;
    static final int TAG = 2;
    private HashMap<String, String[]> CarrierVmMap = new HashMap<>();

    VoiceMailConstants() {
        loadVoiceMail();
    }

    boolean containsCarrier(String carrier) {
        return this.CarrierVmMap.containsKey(carrier);
    }

    String getCarrierName(String carrier) {
        String[] data = this.CarrierVmMap.get(carrier);
        return data[0];
    }

    String getVoiceMailNumber(String carrier) {
        String[] data = this.CarrierVmMap.get(carrier);
        return data[1];
    }

    String getVoiceMailTag(String carrier) {
        String[] data = this.CarrierVmMap.get(carrier);
        return data[2];
    }

    /* JADX WARN: Not initialized variable reg: 6, insn: 0x00cb: IF  (r6 I:??[int, boolean, OBJECT, ARRAY, byte, short, char] A[D('vmReader' java.io.FileReader)]) == (0 ??[int, boolean, OBJECT, ARRAY, byte, short, char])  -> B:30:0x00d0 (LINE:115), block:B:28:0x00cb */
    private void loadVoiceMail() {
        FileReader vmReader;
        File vmFile = new File(Environment.getRootDirectory(), PARTNER_VOICEMAIL_PATH);
        try {
            try {
                try {
                    FileReader vmReader2 = new FileReader(vmFile);
                    try {
                        XmlPullParser parser = Xml.newPullParser();
                        parser.setInput(vmReader2);
                        XmlUtils.beginDocument(parser, "voicemail");
                        while (true) {
                            XmlUtils.nextElement(parser);
                            String name = parser.getName();
                            if (!"voicemail".equals(name)) {
                                break;
                            }
                            String numeric = parser.getAttributeValue(null, "numeric");
                            String[] data = {parser.getAttributeValue(null, "carrier"), parser.getAttributeValue(null, "vmnumber"), parser.getAttributeValue(null, "vmtag")};
                            this.CarrierVmMap.put(numeric, data);
                        }
                        if (vmReader2 != null) {
                            vmReader2.close();
                        }
                    } catch (IOException e) {
                        Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e);
                        if (vmReader2 != null) {
                            vmReader2.close();
                        }
                    } catch (XmlPullParserException e2) {
                        Rlog.w(LOG_TAG, "Exception in Voicemail parser " + e2);
                        if (vmReader2 != null) {
                            vmReader2.close();
                        }
                    }
                } catch (FileNotFoundException e3) {
                    Rlog.w(LOG_TAG, "Can't open " + Environment.getRootDirectory() + "/" + PARTNER_VOICEMAIL_PATH);
                }
            } catch (IOException e4) {
            }
        } catch (Throwable th) {
            if (vmReader != null) {
                try {
                    vmReader.close();
                } catch (IOException e5) {
                }
            }
            throw th;
        }
    }
}
