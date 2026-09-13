package com.android.internal.telephony.uicc;

import android.telephony.Rlog;
import com.android.internal.telephony.SmsHeader;

/* JADX INFO: loaded from: classes.dex */
public class IccCardApplicationStatus {
    public String aid;
    public String app_label;
    public AppState app_state;
    public AppType app_type;
    public PersoSubState perso_substate;
    public IccCardStatus.PinState pin1;
    public int pin1_replaced;
    public IccCardStatus.PinState pin2;

    public enum AppType {
        APPTYPE_UNKNOWN,
        APPTYPE_SIM,
        APPTYPE_USIM,
        APPTYPE_RUIM,
        APPTYPE_CSIM,
        APPTYPE_ISIM
    }

    public enum AppState {
        APPSTATE_UNKNOWN,
        APPSTATE_DETECTED,
        APPSTATE_PIN,
        APPSTATE_PUK,
        APPSTATE_SUBSCRIPTION_PERSO,
        APPSTATE_READY;

        boolean isPinRequired() {
            return this == APPSTATE_PIN;
        }

        boolean isPukRequired() {
            return this == APPSTATE_PUK;
        }

        boolean isSubscriptionPersoEnabled() {
            return this == APPSTATE_SUBSCRIPTION_PERSO;
        }

        boolean isAppReady() {
            return this == APPSTATE_READY;
        }

        boolean isAppNotReady() {
            return this == APPSTATE_UNKNOWN || this == APPSTATE_DETECTED;
        }
    }

    public enum PersoSubState {
        PERSOSUBSTATE_UNKNOWN,
        PERSOSUBSTATE_IN_PROGRESS,
        PERSOSUBSTATE_READY,
        PERSOSUBSTATE_SIM_NETWORK,
        PERSOSUBSTATE_SIM_NETWORK_SUBSET,
        PERSOSUBSTATE_SIM_CORPORATE,
        PERSOSUBSTATE_SIM_SERVICE_PROVIDER,
        PERSOSUBSTATE_SIM_SIM,
        PERSOSUBSTATE_SIM_NETWORK_PUK,
        PERSOSUBSTATE_SIM_NETWORK_SUBSET_PUK,
        PERSOSUBSTATE_SIM_CORPORATE_PUK,
        PERSOSUBSTATE_SIM_SERVICE_PROVIDER_PUK,
        PERSOSUBSTATE_SIM_SIM_PUK,
        PERSOSUBSTATE_RUIM_NETWORK1,
        PERSOSUBSTATE_RUIM_NETWORK2,
        PERSOSUBSTATE_RUIM_HRPD,
        PERSOSUBSTATE_RUIM_CORPORATE,
        PERSOSUBSTATE_RUIM_SERVICE_PROVIDER,
        PERSOSUBSTATE_RUIM_RUIM,
        PERSOSUBSTATE_RUIM_NETWORK1_PUK,
        PERSOSUBSTATE_RUIM_NETWORK2_PUK,
        PERSOSUBSTATE_RUIM_HRPD_PUK,
        PERSOSUBSTATE_RUIM_CORPORATE_PUK,
        PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK,
        PERSOSUBSTATE_RUIM_RUIM_PUK;

        boolean isPersoSubStateUnknown() {
            return this == PERSOSUBSTATE_UNKNOWN;
        }
    }

    public AppType AppTypeFromRILInt(int type) {
        switch (type) {
            case 0:
                AppType newType = AppType.APPTYPE_UNKNOWN;
                return newType;
            case 1:
                AppType newType2 = AppType.APPTYPE_SIM;
                return newType2;
            case 2:
                AppType newType3 = AppType.APPTYPE_USIM;
                return newType3;
            case 3:
                AppType newType4 = AppType.APPTYPE_RUIM;
                return newType4;
            case 4:
                AppType newType5 = AppType.APPTYPE_CSIM;
                return newType5;
            case 5:
                AppType newType6 = AppType.APPTYPE_ISIM;
                return newType6;
            default:
                AppType newType7 = AppType.APPTYPE_UNKNOWN;
                loge("AppTypeFromRILInt: bad RIL_AppType: " + type + " use APPTYPE_UNKNOWN");
                return newType7;
        }
    }

    public AppState AppStateFromRILInt(int state) {
        switch (state) {
            case 0:
                AppState newState = AppState.APPSTATE_UNKNOWN;
                return newState;
            case 1:
                AppState newState2 = AppState.APPSTATE_DETECTED;
                return newState2;
            case 2:
                AppState newState3 = AppState.APPSTATE_PIN;
                return newState3;
            case 3:
                AppState newState4 = AppState.APPSTATE_PUK;
                return newState4;
            case 4:
                AppState newState5 = AppState.APPSTATE_SUBSCRIPTION_PERSO;
                return newState5;
            case 5:
                AppState newState6 = AppState.APPSTATE_READY;
                return newState6;
            default:
                AppState newState7 = AppState.APPSTATE_UNKNOWN;
                loge("AppStateFromRILInt: bad state: " + state + " use APPSTATE_UNKNOWN");
                return newState7;
        }
    }

    public PersoSubState PersoSubstateFromRILInt(int substate) {
        switch (substate) {
            case 0:
                PersoSubState newSubState = PersoSubState.PERSOSUBSTATE_UNKNOWN;
                return newSubState;
            case 1:
                PersoSubState newSubState2 = PersoSubState.PERSOSUBSTATE_IN_PROGRESS;
                return newSubState2;
            case 2:
                PersoSubState newSubState3 = PersoSubState.PERSOSUBSTATE_READY;
                return newSubState3;
            case 3:
                PersoSubState newSubState4 = PersoSubState.PERSOSUBSTATE_SIM_NETWORK;
                return newSubState4;
            case 4:
                PersoSubState newSubState5 = PersoSubState.PERSOSUBSTATE_SIM_NETWORK_SUBSET;
                return newSubState5;
            case 5:
                PersoSubState newSubState6 = PersoSubState.PERSOSUBSTATE_SIM_CORPORATE;
                return newSubState6;
            case 6:
                PersoSubState newSubState7 = PersoSubState.PERSOSUBSTATE_SIM_SERVICE_PROVIDER;
                return newSubState7;
            case 7:
                PersoSubState newSubState8 = PersoSubState.PERSOSUBSTATE_SIM_SIM;
                return newSubState8;
            case 8:
                PersoSubState newSubState9 = PersoSubState.PERSOSUBSTATE_SIM_NETWORK_PUK;
                return newSubState9;
            case 9:
                PersoSubState newSubState10 = PersoSubState.PERSOSUBSTATE_SIM_NETWORK_SUBSET_PUK;
                return newSubState10;
            case 10:
                PersoSubState newSubState11 = PersoSubState.PERSOSUBSTATE_SIM_CORPORATE_PUK;
                return newSubState11;
            case 11:
                PersoSubState newSubState12 = PersoSubState.PERSOSUBSTATE_SIM_SERVICE_PROVIDER_PUK;
                return newSubState12;
            case 12:
                PersoSubState newSubState13 = PersoSubState.PERSOSUBSTATE_SIM_SIM_PUK;
                return newSubState13;
            case 13:
                PersoSubState newSubState14 = PersoSubState.PERSOSUBSTATE_RUIM_NETWORK1;
                return newSubState14;
            case 14:
                PersoSubState newSubState15 = PersoSubState.PERSOSUBSTATE_RUIM_NETWORK2;
                return newSubState15;
            case 15:
                PersoSubState newSubState16 = PersoSubState.PERSOSUBSTATE_RUIM_HRPD;
                return newSubState16;
            case 16:
                PersoSubState newSubState17 = PersoSubState.PERSOSUBSTATE_RUIM_CORPORATE;
                return newSubState17;
            case 17:
                PersoSubState newSubState18 = PersoSubState.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER;
                return newSubState18;
            case 18:
                PersoSubState newSubState19 = PersoSubState.PERSOSUBSTATE_RUIM_RUIM;
                return newSubState19;
            case 19:
                PersoSubState newSubState20 = PersoSubState.PERSOSUBSTATE_RUIM_NETWORK1_PUK;
                return newSubState20;
            case 20:
                PersoSubState newSubState21 = PersoSubState.PERSOSUBSTATE_RUIM_NETWORK2_PUK;
                return newSubState21;
            case 21:
                PersoSubState newSubState22 = PersoSubState.PERSOSUBSTATE_RUIM_HRPD_PUK;
                return newSubState22;
            case 22:
                PersoSubState newSubState23 = PersoSubState.PERSOSUBSTATE_RUIM_CORPORATE_PUK;
                return newSubState23;
            case 23:
                PersoSubState newSubState24 = PersoSubState.PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK;
                return newSubState24;
            case SmsHeader.ELT_ID_STANDARD_WVG_OBJECT /* 24 */:
                PersoSubState newSubState25 = PersoSubState.PERSOSUBSTATE_RUIM_RUIM_PUK;
                return newSubState25;
            default:
                PersoSubState newSubState26 = PersoSubState.PERSOSUBSTATE_UNKNOWN;
                loge("PersoSubstateFromRILInt: bad substate: " + substate + " use PERSOSUBSTATE_UNKNOWN");
                return newSubState26;
        }
    }

    public IccCardStatus.PinState PinStateFromRILInt(int state) {
        switch (state) {
            case 0:
                IccCardStatus.PinState newPinState = IccCardStatus.PinState.PINSTATE_UNKNOWN;
                return newPinState;
            case 1:
                IccCardStatus.PinState newPinState2 = IccCardStatus.PinState.PINSTATE_ENABLED_NOT_VERIFIED;
                return newPinState2;
            case 2:
                IccCardStatus.PinState newPinState3 = IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;
                return newPinState3;
            case 3:
                IccCardStatus.PinState newPinState4 = IccCardStatus.PinState.PINSTATE_DISABLED;
                return newPinState4;
            case 4:
                IccCardStatus.PinState newPinState5 = IccCardStatus.PinState.PINSTATE_ENABLED_BLOCKED;
                return newPinState5;
            case 5:
                IccCardStatus.PinState newPinState6 = IccCardStatus.PinState.PINSTATE_ENABLED_PERM_BLOCKED;
                return newPinState6;
            default:
                IccCardStatus.PinState newPinState7 = IccCardStatus.PinState.PINSTATE_UNKNOWN;
                loge("PinStateFromRILInt: bad pin state: " + state + " use PINSTATE_UNKNOWN");
                return newPinState7;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(this.app_type).append(",").append(this.app_state);
        if (this.app_state == AppState.APPSTATE_SUBSCRIPTION_PERSO) {
            sb.append(",").append(this.perso_substate);
        }
        if (this.app_type == AppType.APPTYPE_CSIM || this.app_type == AppType.APPTYPE_USIM || this.app_type == AppType.APPTYPE_ISIM) {
            sb.append(",pin1=").append(this.pin1);
            sb.append(",pin2=").append(this.pin2);
        }
        sb.append("}");
        return sb.toString();
    }

    private void loge(String s) {
        Rlog.e("IccCardApplicationStatus", s);
    }
}
