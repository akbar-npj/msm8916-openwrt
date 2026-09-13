package com.android.internal.telephony.cdma;

import android.R;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.telephony.Rlog;
import android.util.Xml;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.util.XmlUtils;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/* JADX INFO: loaded from: classes.dex */
public final class EriManager {
    private static final boolean DBG = true;
    public static final int ERI_FROM_FILE_SYSTEM = 1;
    public static final int ERI_FROM_MODEM = 2;
    public static final int ERI_FROM_XML = 0;
    private static final String LOG_TAG = "CDMA";
    private static final boolean VDBG = false;
    private Context mContext;
    private EriFile mEriFile = new EriFile();
    private int mEriFileSource;
    private boolean mIsEriFileLoaded;

    class EriFile {
        int mVersionNumber = -1;
        int mNumberOfEriEntries = 0;
        int mEriFileType = -1;
        String[] mCallPromptId = {"", "", ""};
        HashMap<Integer, EriInfo> mRoamIndTable = new HashMap<>();

        EriFile() {
        }
    }

    class EriDisplayInformation {
        int mEriIconIndex;
        int mEriIconMode;
        String mEriIconText;

        EriDisplayInformation(int eriIconIndex, int eriIconMode, String eriIconText) {
            this.mEriIconIndex = eriIconIndex;
            this.mEriIconMode = eriIconMode;
            this.mEriIconText = eriIconText;
        }

        public String toString() {
            return "EriDisplayInformation: { IconIndex: " + this.mEriIconIndex + " EriIconMode: " + this.mEriIconMode + " EriIconText: " + this.mEriIconText + " }";
        }
    }

    public EriManager(PhoneBase phone, Context context, int eriFileSource) {
        this.mEriFileSource = 0;
        this.mContext = context;
        this.mEriFileSource = eriFileSource;
    }

    public void dispose() {
        this.mEriFile = new EriFile();
        this.mIsEriFileLoaded = false;
    }

    public void loadEriFile() {
        switch (this.mEriFileSource) {
            case 1:
                loadEriFileFromFileSystem();
                break;
            case 2:
                loadEriFileFromModem();
                break;
            default:
                loadEriFileFromXml();
                break;
        }
    }

    private void loadEriFileFromModem() {
    }

    private void loadEriFileFromFileSystem() {
    }

    private void loadEriFileFromXml() {
        XmlPullParser parser;
        int parsedEriEntries;
        FileInputStream stream = null;
        Resources r = this.mContext.getResources();
        try {
            try {
                Rlog.d(LOG_TAG, "loadEriFileFromXml: check for alternate file");
                FileInputStream stream2 = new FileInputStream(r.getString(R.string.imProtocolIcq));
                try {
                    parser = Xml.newPullParser();
                    parser.setInput(stream2, null);
                    Rlog.d(LOG_TAG, "loadEriFileFromXml: opened alternate file");
                    stream = stream2;
                    while (true) {
                        XmlUtils.nextElement(parser);
                        String name = parser.getName();
                        if (name == null) {
                            break;
                        }
                        if (name.equals("CallPromptId")) {
                            int id = Integer.parseInt(parser.getAttributeValue(null, "Id"));
                            String text = parser.getAttributeValue(null, "CallPromptText");
                            if (id >= 0 && id <= 2) {
                                this.mEriFile.mCallPromptId[id] = text;
                            } else {
                                Rlog.e(LOG_TAG, "Error Parsing ERI file: found" + id + " CallPromptId");
                            }
                        } else if (name.equals("EriInfo")) {
                            int roamingIndicator = Integer.parseInt(parser.getAttributeValue(null, "RoamingIndicator"));
                            int iconIndex = Integer.parseInt(parser.getAttributeValue(null, "IconIndex"));
                            int iconMode = Integer.parseInt(parser.getAttributeValue(null, "IconMode"));
                            String eriText = parser.getAttributeValue(null, "EriText");
                            int callPromptId = Integer.parseInt(parser.getAttributeValue(null, "CallPromptId"));
                            int alertId = Integer.parseInt(parser.getAttributeValue(null, "AlertId"));
                            parsedEriEntries++;
                            this.mEriFile.mRoamIndTable.put(Integer.valueOf(roamingIndicator), new EriInfo(roamingIndicator, iconIndex, iconMode, eriText, callPromptId, alertId));
                        }
                    }
                } catch (FileNotFoundException e) {
                    stream = stream2;
                    Rlog.d(LOG_TAG, "loadEriFileFromXml: no alternate file");
                    parser = null;
                } catch (XmlPullParserException e2) {
                    stream = stream2;
                    Rlog.d(LOG_TAG, "loadEriFileFromXml: no parser for alternate file");
                    parser = null;
                }
            } catch (FileNotFoundException e3) {
            } catch (XmlPullParserException e4) {
            }
            XmlUtils.beginDocument(parser, "EriFile");
            this.mEriFile.mVersionNumber = Integer.parseInt(parser.getAttributeValue(null, "VersionNumber"));
            this.mEriFile.mNumberOfEriEntries = Integer.parseInt(parser.getAttributeValue(null, "NumberOfEriEntries"));
            this.mEriFile.mEriFileType = Integer.parseInt(parser.getAttributeValue(null, "EriFileType"));
            parsedEriEntries = 0;
            if (parsedEriEntries != this.mEriFile.mNumberOfEriEntries) {
                Rlog.e(LOG_TAG, "Error Parsing ERI file: " + this.mEriFile.mNumberOfEriEntries + " defined, " + parsedEriEntries + " parsed!");
            }
            Rlog.d(LOG_TAG, "loadEriFileFromXml: eri parsing successful, file loaded");
            this.mIsEriFileLoaded = true;
            if (stream != null) {
                try {
                    return;
                } catch (IOException e5) {
                    return;
                }
            }
            return;
        } catch (Exception e6) {
            Rlog.e(LOG_TAG, "Got exception while loading ERI file.", e6);
            if (stream != null) {
                try {
                    return;
                } catch (IOException e7) {
                    return;
                }
            }
            return;
        } finally {
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser) parser).close();
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e8) {
                }
            }
        }
        if (parser == null) {
            Rlog.d(LOG_TAG, "loadEriFileFromXml: open normal file");
            parser = r.getXml(R.transition.explode);
        }
    }

    public int getEriFileVersion() {
        return this.mEriFile.mVersionNumber;
    }

    public int getEriNumberOfEntries() {
        return this.mEriFile.mNumberOfEriEntries;
    }

    public int getEriFileType() {
        return this.mEriFile.mEriFileType;
    }

    public boolean isEriFileLoaded() {
        return this.mIsEriFileLoaded;
    }

    private EriInfo getEriInfo(int roamingIndicator) {
        if (this.mEriFile.mRoamIndTable.containsKey(Integer.valueOf(roamingIndicator))) {
            return this.mEriFile.mRoamIndTable.get(Integer.valueOf(roamingIndicator));
        }
        return null;
    }

    private EriDisplayInformation getEriDisplayInformation(int roamInd, int defRoamInd) {
        EriDisplayInformation ret;
        EriInfo eriInfo;
        if (this.mIsEriFileLoaded && (eriInfo = getEriInfo(roamInd)) != null) {
            EriDisplayInformation ret2 = new EriDisplayInformation(eriInfo.iconIndex, eriInfo.iconMode, eriInfo.eriText);
            return ret2;
        }
        switch (roamInd) {
            case 0:
                ret = new EriDisplayInformation(0, 0, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_ERROR).toString());
                break;
            case 1:
                ret = new EriDisplayInformation(1, 0, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_IN_PROGRESS).toString());
                break;
            case 2:
                ret = new EriDisplayInformation(2, 1, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_SUCCESS).toString());
                break;
            case 3:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_SUCCESS).toString());
                break;
            case 4:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_CORPORATE_ENTRY).toString());
                break;
            case 5:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_CORPORATE_ERROR).toString());
                break;
            case 6:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_CORPORATE_IN_PROGRESS).toString());
                break;
            case 7:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_CORPORATE_PUK_ENTRY).toString());
                break;
            case 8:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_CORPORATE_PUK_ERROR).toString());
                break;
            case 9:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_CORPORATE_PUK_IN_PROGRESS).toString());
                break;
            case 10:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_CORPORATE_PUK_SUCCESS).toString());
                break;
            case 11:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_CORPORATE_SUCCESS).toString());
                break;
            case 12:
                ret = new EriDisplayInformation(roamInd, 0, this.mContext.getText(R.string.PERSOSUBSTATE_SIM_ICCID_ENTRY).toString());
                break;
            default:
                if (!this.mIsEriFileLoaded) {
                    Rlog.d(LOG_TAG, "ERI File not loaded");
                    if (defRoamInd > 2) {
                        ret = new EriDisplayInformation(2, 1, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_SUCCESS).toString());
                        break;
                    } else {
                        switch (defRoamInd) {
                            case 0:
                                ret = new EriDisplayInformation(0, 0, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_ERROR).toString());
                                break;
                            case 1:
                                ret = new EriDisplayInformation(1, 0, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_IN_PROGRESS).toString());
                                break;
                            case 2:
                                ret = new EriDisplayInformation(2, 1, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_SUCCESS).toString());
                                break;
                            default:
                                ret = new EriDisplayInformation(-1, -1, "ERI text");
                                break;
                        }
                    }
                } else {
                    EriInfo eriInfo2 = getEriInfo(roamInd);
                    EriInfo defEriInfo = getEriInfo(defRoamInd);
                    if (eriInfo2 == null) {
                        if (defEriInfo == null) {
                            Rlog.e(LOG_TAG, "ERI defRoamInd " + defRoamInd + " not found in ERI file ...on");
                            ret = new EriDisplayInformation(0, 0, this.mContext.getText(R.string.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK_ERROR).toString());
                        } else {
                            ret = new EriDisplayInformation(defEriInfo.iconIndex, defEriInfo.iconMode, defEriInfo.eriText);
                        }
                    } else {
                        ret = new EriDisplayInformation(eriInfo2.iconIndex, eriInfo2.iconMode, eriInfo2.eriText);
                    }
                    break;
                }
                break;
        }
        return ret;
    }

    public int getCdmaEriIconIndex(int roamInd, int defRoamInd) {
        return getEriDisplayInformation(roamInd, defRoamInd).mEriIconIndex;
    }

    public int getCdmaEriIconMode(int roamInd, int defRoamInd) {
        return getEriDisplayInformation(roamInd, defRoamInd).mEriIconMode;
    }

    public String getCdmaEriText(int roamInd, int defRoamInd) {
        return getEriDisplayInformation(roamInd, defRoamInd).mEriIconText;
    }
}
