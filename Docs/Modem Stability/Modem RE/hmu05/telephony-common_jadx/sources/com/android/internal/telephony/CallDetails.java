package com.android.internal.telephony;

import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class CallDetails {
    public static final int CALL_DOMAIN_AUTOMATIC = 3;
    public static final int CALL_DOMAIN_CS = 1;
    public static final int CALL_DOMAIN_NOT_SET = 4;
    public static final int CALL_DOMAIN_PS = 2;
    public static final int CALL_DOMAIN_UNKNOWN = 11;
    public static final int CALL_RESTRICT_CAUSE_DISABLED = 2;
    public static final int CALL_RESTRICT_CAUSE_NONE = 0;
    public static final int CALL_RESTRICT_CAUSE_RAT = 1;
    public static final int CALL_TYPE_SMS = 5;
    public static final int CALL_TYPE_UNKNOWN = 10;
    public static final int CALL_TYPE_VOICE = 0;
    public static final int CALL_TYPE_VT = 3;
    public static final int CALL_TYPE_VT_NODIR = 4;
    public static final int CALL_TYPE_VT_PAUSE = 6;
    public static final int CALL_TYPE_VT_RESUME = 7;
    public static final int CALL_TYPE_VT_RX = 2;
    public static final int CALL_TYPE_VT_TX = 1;
    public static final String EXTRAS_HANDOVER_INFORMATION = "handoverInfo";
    public static final String EXTRAS_IS_CONFERENCE_URI = "isConferenceUri";
    public static final String EXTRAS_PARENT_CALL_ID = "parentCallId";
    public static final int EXTRA_TYPE_LTE_TO_IWLAN_HO_FAIL = 1;
    public static final int VIDEO_PAUSE_STATE_PAUSED = 1;
    public static final int VIDEO_PAUSE_STATE_RESUMED = 2;
    public int call_domain;
    public int call_type;
    public String[] extras;
    public ServiceStatus[] localAbility;
    private int mVideoPauseState;
    public ServiceStatus[] peerAbility;

    public CallDetails() {
        this.mVideoPauseState = 2;
        this.call_type = 10;
        this.call_domain = 4;
        this.extras = null;
    }

    public CallDetails(int callType, int callDomain, String[] extraparams) {
        this.mVideoPauseState = 2;
        this.call_type = callType;
        this.call_domain = callDomain;
        this.extras = extraparams;
    }

    public CallDetails(CallDetails srcCall) {
        this.mVideoPauseState = 2;
        if (srcCall != null) {
            this.call_type = srcCall.call_type;
            this.call_domain = srcCall.call_domain;
            this.extras = srcCall.extras;
            this.localAbility = srcCall.localAbility;
            this.peerAbility = srcCall.peerAbility;
        }
    }

    public void setExtras(String[] extraparams) {
        this.extras = extraparams;
    }

    public static String[] getExtrasFromMap(Map<String, String> newExtras) {
        if (newExtras == null) {
            return null;
        }
        String[] extras = new String[newExtras.size()];
        if (extras != null) {
            for (Map.Entry<String, String> entry : newExtras.entrySet()) {
                extras[0] = "" + entry.getKey() + "=" + entry.getValue();
            }
        }
        return extras;
    }

    public void setExtrasFromMap(Map<String, String> newExtras) {
        this.extras = getExtrasFromMap(newExtras);
    }

    public void setVideoPauseState(int videoPauseState) {
        switch (videoPauseState) {
            case 1:
            case 2:
                this.mVideoPauseState = videoPauseState;
                break;
        }
    }

    public int getVideoPauseState() {
        return this.mVideoPauseState;
    }

    public String getValueForKeyFromExtras(String[] extras, String key) {
        for (int i = 0; extras != null && i < extras.length; i++) {
            if (extras[i] != null) {
                String[] currKey = extras[i].split("=");
                if (currKey.length == 2 && currKey[0].equals(key)) {
                    return currKey[1];
                }
            }
        }
        return null;
    }

    public String toString() {
        String extrasResult = "";
        String localSrvAbility = "";
        String peerSrvAbility = "";
        if (this.extras != null) {
            String[] arr$ = this.extras;
            for (String s : arr$) {
                extrasResult = extrasResult + s;
            }
        }
        if (this.localAbility != null) {
            ServiceStatus[] arr$2 = this.localAbility;
            for (ServiceStatus srv : arr$2) {
                if (srv != null) {
                    localSrvAbility = localSrvAbility + "isValid = " + srv.isValid + " type = " + srv.type + " status = " + srv.status;
                    if (srv.accessTechStatus != null) {
                        ServiceStatus.StatusForAccessTech[] arr$3 = srv.accessTechStatus;
                        for (ServiceStatus.StatusForAccessTech at : arr$3) {
                            localSrvAbility = localSrvAbility + " accTechStatus " + at;
                        }
                    }
                }
            }
        }
        if (this.peerAbility != null) {
            ServiceStatus[] arr$4 = this.peerAbility;
            for (ServiceStatus srv2 : arr$4) {
                if (srv2 != null) {
                    peerSrvAbility = peerSrvAbility + "isValid = " + srv2.isValid + " type = " + srv2.type + " status = " + srv2.status;
                    if (srv2.accessTechStatus != null) {
                        ServiceStatus.StatusForAccessTech[] arr$5 = srv2.accessTechStatus;
                        for (ServiceStatus.StatusForAccessTech at2 : arr$5) {
                            peerSrvAbility = peerSrvAbility + " accTechStatus " + at2;
                        }
                    }
                }
            }
        }
        return " " + this.call_type + " " + this.call_domain + " " + extrasResult + " videoPauseState" + this.mVideoPauseState + " Local Ability " + localSrvAbility + " Peer Ability " + peerSrvAbility;
    }
}
