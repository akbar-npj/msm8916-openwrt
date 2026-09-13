package com.android.internal.telephony;

import android.os.Handler;
import android.os.Message;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;

/* JADX INFO: loaded from: classes.dex */
public interface IccCard {
    void changeIccFdnPassword(String str, String str2, Message message);

    void changeIccLockPassword(String str, String str2, Message message);

    void closeLogicalChannel(int i, Message message);

    void exchangeApdu(int i, int i2, int i3, int i4, int i5, int i6, String str, Message message);

    void exchangeIccIo(int i, int i2, int i3, int i4, int i5, String str, Message message);

    void getAtr(Message message);

    boolean getIccFdnAvailable();

    boolean getIccFdnEnabled();

    IccFileHandler getIccFileHandler();

    boolean getIccLockEnabled();

    int getIccPin1RetryCount();

    boolean getIccPin2Blocked();

    int getIccPin2RetryCount();

    boolean getIccPuk2Blocked();

    IccRecords getIccRecords();

    String getServiceProviderName();

    IccCardConstants.State getState();

    boolean hasIccCard();

    boolean isApplicationOnIcc(IccCardApplicationStatus.AppType appType);

    void openLogicalChannel(String str, Message message);

    void registerForAbsent(Handler handler, int i, Object obj);

    void registerForLocked(Handler handler, int i, Object obj);

    void registerForPersoLocked(Handler handler, int i, Object obj);

    void setIccFdnEnabled(boolean z, String str, Message message);

    void setIccLockEnabled(boolean z, String str, Message message);

    void supplyDepersonalization(String str, String str2, Message message);

    void supplyPin(String str, Message message);

    void supplyPin2(String str, Message message);

    void supplyPuk(String str, String str2, Message message);

    void supplyPuk2(String str, String str2, Message message);

    void unregisterForAbsent(Handler handler);

    void unregisterForLocked(Handler handler);

    void unregisterForPersoLocked(Handler handler);
}
