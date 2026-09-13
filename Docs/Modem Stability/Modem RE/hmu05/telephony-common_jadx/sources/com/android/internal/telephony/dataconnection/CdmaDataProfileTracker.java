package com.android.internal.telephony.dataconnection;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;
import java.util.ArrayList;
import java.util.HashMap;

/* JADX INFO: loaded from: classes.dex */
public final class CdmaDataProfileTracker extends Handler {
    private static final int EVENT_GET_DATA_CALL_PROFILE_DONE = 1;
    private static final int EVENT_LOAD_PROFILES = 2;
    private static final int EVENT_READ_MODEM_PROFILES = 0;
    protected DataProfile mActiveDp;
    private CdmaSubscriptionSourceManager mCdmaSsm;
    private IccRecords mIccRecords;
    private CDMAPhone mPhone;
    private static final String[] mSupportedApnTypes = {"default", "mms", "supl", "dun", "hipri", "fota", "ims", "cbs"};
    private static final String[] mDefaultApnTypes = {"default", "mms", "supl", "hipri", "fota", "ims", "cbs"};
    public static final String PROPERTY_OMH_ENABLED = "persist.omh.enabled";
    public static final boolean OMH_ENABLED = SystemProperties.getBoolean(PROPERTY_OMH_ENABLED, false);
    protected final String LOG_TAG = "CDMA";
    private ArrayList<DataProfile> mDataProfilesList = new ArrayList<>();
    private int mOmhReadProfileContext = 0;
    private int mOmhReadProfileCount = 0;
    ArrayList<DataProfile> mOmhDataProfilesList = new ArrayList<>();
    ArrayList<DataProfile> mTempOmhDataProfilesList = new ArrayList<>();
    private RegistrantList mModemDataProfileRegistrants = new RegistrantList();
    HashMap<String, Integer> mOmhServicePriorityMap = new HashMap<>();

    CdmaDataProfileTracker(CDMAPhone phone) {
        this.mPhone = phone;
        this.mCdmaSsm = CdmaSubscriptionSourceManager.getInstance(phone.getContext(), phone.mCi, this, 2, null);
        log("SUPPORT_OMH: " + OMH_ENABLED);
    }

    void loadProfiles() {
        log("loadProfiles...");
        this.mDataProfilesList.clear();
        readDataProfilesFromModem();
    }

    private String[] parseTypes(String types) {
        if (types == null || types.equals("")) {
            String[] result = {"*"};
            return result;
        }
        return types.split(",");
    }

    public void dispose() {
    }

    protected void finalize() {
        Log.d("CDMA", "CdmaDataProfileTracker finalized");
    }

    public void registerForModemProfileReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        this.mModemDataProfileRegistrants.add(r);
    }

    public void unregisterForModemProfileReady(Handler h) {
        this.mModemDataProfileRegistrants.remove(h);
    }

    @Override // android.os.Handler
    public void handleMessage(Message msg) {
        if (!this.mPhone.mIsTheCurrentActivePhone) {
            Log.d("CDMA", "Ignore CDMA msgs since CDMA phone is inactive");
        }
        switch (msg.what) {
            case 0:
                onReadDataProfilesFromModem();
                break;
            case 1:
                onGetDataCallProfileDone((AsyncResult) msg.obj, msg.arg1);
                break;
            case 2:
                loadProfiles();
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private void readDataProfilesFromModem() {
        if (OMH_ENABLED) {
            sendMessage(obtainMessage(0));
        } else {
            log("OMH is disabled, ignoring request!");
        }
    }

    private void onReadDataProfilesFromModem() {
        log("OMH: onReadDataProfilesFromModem()");
        this.mOmhReadProfileContext++;
        this.mOmhReadProfileCount = 0;
        this.mOmhDataProfilesList.clear();
        this.mTempOmhDataProfilesList.clear();
        this.mOmhServicePriorityMap.clear();
        DataProfileOmh.DataProfileTypeModem[] arr$ = DataProfileOmh.DataProfileTypeModem.values();
        for (DataProfileOmh.DataProfileTypeModem p : arr$) {
            log("OMH: Reading profiles for:" + p.getid());
            this.mOmhReadProfileCount++;
            this.mPhone.mCi.getDataCallProfile(p.getid(), obtainMessage(1, this.mOmhReadProfileContext, 0, p));
        }
    }

    private void onGetDataCallProfileDone(AsyncResult ar, int context) {
        if (context == this.mOmhReadProfileContext) {
            if (ar.exception != null) {
                log("OMH: Exception in onGetDataCallProfileDone:" + ar.exception);
                this.mOmhReadProfileCount--;
                return;
            }
            new ArrayList();
            ArrayList<DataProfile> dataProfileListModem = (ArrayList) ar.result;
            DataProfileOmh.DataProfileTypeModem modemProfile = (DataProfileOmh.DataProfileTypeModem) ar.userObj;
            this.mOmhReadProfileCount--;
            if (dataProfileListModem != null && dataProfileListModem.size() > 0) {
                String serviceType = modemProfile.getDataServiceType();
                log("OMH: # profiles returned from modem:" + dataProfileListModem.size() + " for " + serviceType);
                this.mOmhServicePriorityMap.put(serviceType, Integer.valueOf(omhListGetArbitratedPriority(dataProfileListModem, serviceType)));
                for (DataProfile dp : dataProfileListModem) {
                    ((DataProfileOmh) dp).setDataProfileTypeModem(modemProfile);
                    DataProfileOmh omhDuplicateDp = getDuplicateProfile(dp);
                    if (omhDuplicateDp == null) {
                        this.mTempOmhDataProfilesList.add(dp);
                        ((DataProfileOmh) dp).addServiceType(DataProfileOmh.DataProfileTypeModem.getDataProfileTypeModem(serviceType));
                    } else {
                        log("OMH: Duplicate Profile " + omhDuplicateDp);
                        omhDuplicateDp.addServiceType(DataProfileOmh.DataProfileTypeModem.getDataProfileTypeModem(serviceType));
                    }
                }
            }
            if (this.mOmhReadProfileCount == 0) {
                log("OMH: Modem omh profile read complete.");
                addServiceTypeToUnSpecified();
                if (this.mTempOmhDataProfilesList.isEmpty()) {
                    this.mTempOmhDataProfilesList = addDummyDataProfiles();
                }
                this.mDataProfilesList.addAll(this.mTempOmhDataProfilesList);
                this.mModemDataProfileRegistrants.notifyRegistrants();
            }
        }
    }

    private ArrayList<DataProfile> addDummyDataProfiles() {
        log("OMH profiles not found. Creating dummy data profiles");
        ArrayList<DataProfile> mDummyProfileList = new ArrayList<>();
        String[] dunApnTypes = {"dun"};
        String operator = getCdmaOperatorNumeric();
        ApnSetting apn = new ApnSetting(0, operator, null, null, null, null, null, null, null, null, null, 3, mDefaultApnTypes, DcTracker.PROPERTY_CDMA_IPPROTOCOL, DcTracker.PROPERTY_CDMA_ROAMING_IPPROTOCOL, true, 0);
        mDummyProfileList.add(apn);
        ApnSetting apn2 = new ApnSetting(3, operator, null, null, null, null, null, null, null, null, null, 3, dunApnTypes, DcTracker.PROPERTY_CDMA_IPPROTOCOL, DcTracker.PROPERTY_CDMA_ROAMING_IPPROTOCOL, true, 0);
        mDummyProfileList.add(apn2);
        return mDummyProfileList;
    }

    private String getCdmaOperatorNumeric() {
        String operatorNumeric;
        int dataNetworkType = this.mPhone.getServiceState().getRilDataRadioTechnology();
        int appFamily = UiccController.getFamilyFromRadioTechnology(dataNetworkType);
        if (appFamily == 2 && this.mCdmaSsm.getCdmaSubscriptionSource() == 1) {
            operatorNumeric = SystemProperties.get(CDMAPhone.PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
            log("getCdmaOperatorNumberic - returning from NV: " + operatorNumeric);
        } else {
            this.mIccRecords = UiccController.getInstance().getIccRecords(appFamily);
            operatorNumeric = this.mIccRecords != null ? this.mIccRecords.getOperatorNumeric() : "";
        }
        log("getCdmaOperatorNumeric:  " + operatorNumeric);
        return operatorNumeric;
    }

    private DataProfileOmh getDuplicateProfile(DataProfile dp) {
        for (DataProfile dataProfile : this.mTempOmhDataProfilesList) {
            if (((DataProfileOmh) dp).getProfileId() == ((DataProfileOmh) dataProfile).getProfileId()) {
                return (DataProfileOmh) dataProfile;
            }
        }
        return null;
    }

    public DataProfile getDataProfile(String serviceType) {
        log("getDataProfile: serviceType=" + serviceType);
        DataProfile profile = null;
        for (DataProfile dp : this.mDataProfilesList) {
            if (dp.canHandleType(serviceType) && (!OMH_ENABLED || dp.getDataProfileType() == DataProfile.DataProfileType.PROFILE_TYPE_OMH)) {
                profile = dp;
                break;
            }
        }
        if (profile == null) {
            log("getDataProfile: OMH profile not found for " + serviceType);
            for (DataProfile dp2 : this.mDataProfilesList) {
                if (dp2.canHandleType(serviceType)) {
                    profile = dp2;
                    break;
                }
            }
            log("getDataProfile: using hardcoded profile " + profile);
        }
        log("getDataProfile: return profile=" + profile);
        return profile;
    }

    private void addServiceTypeToUnSpecified() {
        String[] arr$ = mSupportedApnTypes;
        for (String apntype : arr$) {
            if (!this.mOmhServicePriorityMap.containsKey(apntype)) {
                for (DataProfile dp : this.mTempOmhDataProfilesList) {
                    if (((DataProfileOmh) dp).getDataProfileTypeModem() == DataProfileOmh.DataProfileTypeModem.PROFILE_TYPE_UNSPECIFIED) {
                        ((DataProfileOmh) dp).addServiceType(DataProfileOmh.DataProfileTypeModem.getDataProfileTypeModem(apntype));
                        log("OMH: Service Type added to UNSPECIFIED is : " + DataProfileOmh.DataProfileTypeModem.getDataProfileTypeModem(apntype));
                        break;
                    }
                }
            }
        }
    }

    private int omhListGetArbitratedPriority(ArrayList<DataProfile> dataProfileListModem, String serviceType) {
        DataProfile profile = null;
        for (DataProfile dp : dataProfileListModem) {
            if (!((DataProfileOmh) dp).isValidPriority()) {
                log("[OMH] Invalid priority... skipping");
            } else if (profile == null) {
                profile = dp;
            } else if (serviceType == "supl") {
                if (((DataProfileOmh) dp).isPriorityLower(((DataProfileOmh) profile).getPriority())) {
                    profile = dp;
                }
            } else if (((DataProfileOmh) dp).isPriorityHigher(((DataProfileOmh) profile).getPriority())) {
                profile = dp;
            }
        }
        return ((DataProfileOmh) profile).getPriority();
    }

    public void clearActiveDataProfile() {
        this.mActiveDp = null;
    }

    public boolean isApnTypeActive(String type) {
        return this.mActiveDp != null && this.mActiveDp.canHandleType(type);
    }

    public boolean isOmhEnabled() {
        return OMH_ENABLED;
    }

    protected boolean isApnTypeAvailable(String type) {
        String[] arr$ = mSupportedApnTypes;
        for (String s : arr$) {
            if (TextUtils.equals(type, s)) {
                return true;
            }
        }
        return false;
    }

    protected void log(String s) {
        Log.d("CDMA", "[CdmaDataProfileTracker] " + s);
    }

    protected void loge(String s) {
        Log.e("CDMA", "[CdmaDataProfileTracker] " + s);
    }
}
