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
public class SpnOverride {
    static final String LOG_TAG = "SpnOverride";
    static final String PARTNER_SPN_OVERRIDE_PATH = "etc/spn-conf.xml";
    private HashMap<String, String> mCarrierSpnMap = new HashMap<>();

    SpnOverride() {
        loadSpnOverrides();
    }

    boolean containsCarrier(String carrier) {
        return this.mCarrierSpnMap.containsKey(carrier);
    }

    String getSpn(String carrier) {
        return this.mCarrierSpnMap.get(carrier);
    }

    private void loadSpnOverrides() {
        File spnFile = new File(Environment.getRootDirectory(), PARTNER_SPN_OVERRIDE_PATH);
        try {
            FileReader spnReader = new FileReader(spnFile);
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(spnReader);
                XmlUtils.beginDocument(parser, "spnOverrides");
                while (true) {
                    XmlUtils.nextElement(parser);
                    String name = parser.getName();
                    if ("spnOverride".equals(name)) {
                        String numeric = parser.getAttributeValue(null, "numeric");
                        String data = parser.getAttributeValue(null, "spn");
                        this.mCarrierSpnMap.put(numeric, data);
                    } else {
                        return;
                    }
                }
            } catch (IOException e) {
                Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e);
            } catch (XmlPullParserException e2) {
                Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e2);
            }
        } catch (FileNotFoundException e3) {
            Rlog.w(LOG_TAG, "Can not open " + Environment.getRootDirectory() + "/" + PARTNER_SPN_OVERRIDE_PATH);
        }
    }
}
