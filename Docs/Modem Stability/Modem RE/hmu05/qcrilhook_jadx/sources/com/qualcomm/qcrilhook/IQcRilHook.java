package com.qualcomm.qcrilhook;

import android.os.AsyncResult;
import android.os.Handler;

/* JADX INFO: loaded from: classes.dex */
public interface IQcRilHook {
    public static final int QCRILHOOK_BASE = 524288;
    public static final int QCRILHOOK_DMS_GET_DEVICE_SERIAL_NUMBERS = 528394;
    public static final int QCRILHOOK_DMS_GET_FTM_MODE = 528391;
    public static final int QCRILHOOK_DMS_GET_SPC_CHANGE_ENABLED = 528395;
    public static final int QCRILHOOK_DMS_GET_SW_VERSION = 528392;
    public static final int QCRILHOOK_DMS_SET_SPC_CHANGE_ENABLED = 528396;
    public static final int QCRILHOOK_DMS_UPDATE_SERVICE_PROGRAMING_CODE = 528393;
    public static final int QCRILHOOK_GO_DORMANT = 524291;
    public static final int QCRILHOOK_ME_DEPERSONALIZATION = 524292;
    public static final int QCRILHOOK_NAS_GET_3GPP2_SUBSCRIPTION_INFO = 528385;
    public static final int QCRILHOOK_NAS_GET_MOB_CAI_REV = 528387;
    public static final int QCRILHOOK_NAS_GET_RTRE_CONFIG = 528389;
    public static final int QCRILHOOK_NAS_SET_3GPP2_SUBSCRIPTION_INFO = 528386;
    public static final int QCRILHOOK_NAS_SET_MOB_CAI_REV = 528388;
    public static final int QCRILHOOK_NAS_SET_RTRE_CONFIG = 528390;
    public static final int QCRILHOOK_NAS_UPDATE_AKEY = 528384;
    public static final int QCRILHOOK_NV_READ = 524289;
    public static final int QCRILHOOK_NV_WRITE = 524290;
    public static final int QCRILHOOK_QMI_OEMHOOK_REQUEST_ID = 524388;
    public static final int QCRILHOOK_REQUEST_ID_BASE = 524289;
    public static final int QCRILHOOK_REQUEST_ID_MAX = 524387;
    public static final int QCRILHOOK_UNSOL_BASE = 525288;
    public static final int QCRILHOOK_UNSOL_CDMA_BURST_DTMF = 525289;
    public static final int QCRILHOOK_UNSOL_CDMA_CONT_DTMF_START = 525290;
    public static final int QCRILHOOK_UNSOL_CDMA_CONT_DTMF_STOP = 525291;
    public static final int QCRILHOOK_UNSOL_EXTENDED_DBM_INTL = 525288;
    public static final int QCRILHOOK_UNSOL_LOCAL_RINGBACK_START = 525292;
    public static final int QCRILHOOK_UNSOL_LOCAL_RINGBACK_STOP = 525293;
    public static final int QCRILHOOK_UNSOL_MAX = 525387;
    public static final int QCRILHOOK_UNSOL_OEMHOOK = 525388;
    public static final int QCRILHOOK_UNSOL_PDC_CLEAR_CONFIGS = 525305;
    public static final int QCRILHOOK_UNSOL_PDC_CONFIG = 525302;
    public static final int QCRILHOOK_VOICE_GET_CONFIG = 528398;
    public static final int QCRILHOOK_VOICE_SET_CONFIG = 528397;
    public static final int QCRIL_EVT_HOOK_CDMA_AVOID_CUR_NWK = 524302;
    public static final int QCRIL_EVT_HOOK_CDMA_CLEAR_AVOIDANCE_LIST = 524303;
    public static final int QCRIL_EVT_HOOK_CDMA_GET_AVOIDANCE_LIST = 524304;
    public static final int QCRIL_EVT_HOOK_DEACT_CONFIGS = 524327;
    public static final int QCRIL_EVT_HOOK_DELETE_ALL_CONFIGS = 524319;
    public static final int QCRIL_EVT_HOOK_ENABLE_ENGINEER_MODE = 524307;
    public static final int QCRIL_EVT_HOOK_GET_AVAILABLE_CONFIGS = 524311;
    public static final int QCRIL_EVT_HOOK_GET_CONFIG = 524310;
    public static final int QCRIL_EVT_HOOK_GET_META_INFO = 524321;
    public static final int QCRIL_EVT_HOOK_GET_PAGING_PRIORITY = 524296;
    public static final int QCRIL_EVT_HOOK_GET_PREFERRED_NETWORK_ACQ_ORDER = 524316;
    public static final int QCRIL_EVT_HOOK_GET_PREFERRED_NETWORK_BAND_PREF = 524326;
    public static final int QCRIL_EVT_HOOK_GET_TUNEAWAY = 524294;
    public static final int QCRIL_EVT_HOOK_INFORM_SHUTDOWN = 524298;
    public static final int QCRIL_EVT_HOOK_PERFORM_INCREMENTAL_NW_SCAN = 524306;
    public static final int QCRIL_EVT_HOOK_SEL_CONFIG = 524320;
    public static final int QCRIL_EVT_HOOK_SET_BUILTIN_PLMN_LIST = 524305;
    public static final int QCRIL_EVT_HOOK_SET_CDMA_SUB_SRC_WITH_SPC = 524299;
    public static final int QCRIL_EVT_HOOK_SET_CONFIG = 524309;
    public static final int QCRIL_EVT_HOOK_SET_PAGING_PRIORITY = 524295;
    public static final int QCRIL_EVT_HOOK_SET_PREFERRED_NETWORK_ACQ_ORDER = 524315;
    public static final int QCRIL_EVT_HOOK_SET_PREFERRED_NETWORK_BAND_PREF = 524325;
    public static final int QCRIL_EVT_HOOK_SET_TUNEAWAY = 524293;
    public static final int SERVICE_PROGRAMMING_BASE = 4096;

    public static class QcRilExtendedDbmIntlKddiAocr {
        public byte chg_ind;
        public byte db_subtype;
        public short mcc;
        public byte sub_unit;
        public byte unit;
    }

    boolean qcRilCleanupConfigs();

    String[] qcRilGetAvailableConfigs(String str);

    String qcRilGetConfig();

    boolean qcRilGoDormant(String str);

    boolean qcRilSetCdmaSubSrcWithSpc(int i, String str);

    boolean qcRilSetConfig(String str);

    void registerForExtendedDbmIntl(Handler handler, int i, Object obj);

    void registerForFieldTestData(Handler handler, int i, Object obj);

    AsyncResult sendQcRilHookMsg(int i);

    AsyncResult sendQcRilHookMsg(int i, byte b);

    AsyncResult sendQcRilHookMsg(int i, int i2);

    AsyncResult sendQcRilHookMsg(int i, String str);

    AsyncResult sendQcRilHookMsg(int i, byte[] bArr);

    void unregisterForExtendedDbmIntl(Handler handler);

    void unregisterForFieldTestData(Handler handler);
}
