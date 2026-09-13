package com.android.internal.telephony;

import android.telephony.CellInfo;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public interface PhoneNotifier {
    void notifyCallForwardingChanged(Phone phone);

    void notifyCellInfo(Phone phone, List<CellInfo> list);

    void notifyCellLocation(Phone phone);

    void notifyDataActivity(Phone phone);

    void notifyDataConnection(Phone phone, String str, String str2, PhoneConstants.DataState dataState);

    void notifyDataConnectionFailed(Phone phone, String str, String str2);

    void notifyMessageWaitingChanged(Phone phone);

    void notifyOtaspChanged(Phone phone, int i);

    void notifyPhoneState(Phone phone);

    void notifyServiceState(Phone phone);

    void notifySignalStrength(Phone phone);
}
