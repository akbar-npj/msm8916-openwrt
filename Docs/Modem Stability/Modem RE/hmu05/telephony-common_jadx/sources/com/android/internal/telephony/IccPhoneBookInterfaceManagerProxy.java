package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.RemoteException;
import android.os.ServiceManager;
import com.android.internal.telephony.uicc.AdnRecord;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class IccPhoneBookInterfaceManagerProxy extends IIccPhoneBook.Stub {
    private IccPhoneBookInterfaceManager mIccPhoneBookInterfaceManager;

    public IccPhoneBookInterfaceManagerProxy(IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        this.mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
        if (ServiceManager.getService("simphonebook") == null) {
            ServiceManager.addService("simphonebook", this);
        }
    }

    public void setmIccPhoneBookInterfaceManager(IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        this.mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsWithContentValuesInEfBySearch(int efid, ContentValues values, String pin2) throws RemoteException {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsWithContentValuesInEfBySearch(efid, values, pin2);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, int index, String pin2) {
        return this.mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex(efid, newTag, newPhoneNumber, index, pin2);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int[] getAdnRecordsSize(int efid) {
        return this.mIccPhoneBookInterfaceManager.getAdnRecordsSize(efid);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        return this.mIccPhoneBookInterfaceManager.getAdnRecordsInEf(efid);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public boolean updateUsimAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, String[] anrNumbers, String[] emails, int index, String pin2) throws RemoteException {
        return this.mIccPhoneBookInterfaceManager.updateUsimAdnRecordsInEfByIndex(efid, newTag, newPhoneNumber, anrNumbers, emails, index, pin2);
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getAdnCount() {
        return this.mIccPhoneBookInterfaceManager.getAdnCount();
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getAnrCount() {
        return this.mIccPhoneBookInterfaceManager.getAnrCount();
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getEmailCount() {
        return this.mIccPhoneBookInterfaceManager.getEmailCount();
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getSpareAnrCount() {
        return this.mIccPhoneBookInterfaceManager.getSpareAnrCount();
    }

    @Override // com.android.internal.telephony.IIccPhoneBook
    public int getSpareEmailCount() {
        return this.mIccPhoneBookInterfaceManager.getSpareEmailCount();
    }
}
