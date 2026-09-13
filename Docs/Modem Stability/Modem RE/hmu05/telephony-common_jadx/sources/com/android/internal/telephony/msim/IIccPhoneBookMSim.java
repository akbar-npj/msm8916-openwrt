package com.android.internal.telephony.msim;

import android.content.ContentValues;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.android.internal.telephony.uicc.AdnRecord;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public interface IIccPhoneBookMSim extends IInterface {
    int getAdnCount(int i) throws RemoteException;

    List<AdnRecord> getAdnRecordsInEf(int i, int i2) throws RemoteException;

    int[] getAdnRecordsSize(int i, int i2) throws RemoteException;

    int getAnrCount(int i) throws RemoteException;

    int getEmailCount(int i) throws RemoteException;

    int getSpareAnrCount(int i) throws RemoteException;

    int getSpareEmailCount(int i) throws RemoteException;

    boolean updateAdnRecordsInEfByIndex(int i, String str, String str2, int i2, String str3, int i3) throws RemoteException;

    boolean updateAdnRecordsInEfBySearch(int i, String str, String str2, String str3, String str4, String str5, int i2) throws RemoteException;

    boolean updateAdnRecordsInEfBySearchOnSubscription(int i, ContentValues contentValues, String str, int i2) throws RemoteException;

    boolean updateUsimAdnRecordsInEfByIndex(int i, String str, String str2, String[] strArr, String[] strArr2, int i2, String str3, int i3) throws RemoteException;

    public static abstract class Stub extends Binder implements IIccPhoneBookMSim {
        private static final String DESCRIPTOR = "com.android.internal.telephony.msim.IIccPhoneBookMSim";
        static final int TRANSACTION_getAdnCount = 7;
        static final int TRANSACTION_getAdnRecordsInEf = 1;
        static final int TRANSACTION_getAdnRecordsSize = 5;
        static final int TRANSACTION_getAnrCount = 8;
        static final int TRANSACTION_getEmailCount = 9;
        static final int TRANSACTION_getSpareAnrCount = 10;
        static final int TRANSACTION_getSpareEmailCount = 11;
        static final int TRANSACTION_updateAdnRecordsInEfByIndex = 3;
        static final int TRANSACTION_updateAdnRecordsInEfBySearch = 2;
        static final int TRANSACTION_updateAdnRecordsInEfBySearchOnSubscription = 4;
        static final int TRANSACTION_updateUsimAdnRecordsInEfByIndex = 6;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IIccPhoneBookMSim asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof IIccPhoneBookMSim)) {
                return (IIccPhoneBookMSim) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            ContentValues _arg1;
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg0 = data.readInt();
                    int _arg2 = data.readInt();
                    List<AdnRecord> _result = getAdnRecordsInEf(_arg0, _arg2);
                    reply.writeNoException();
                    reply.writeTypedList(_result);
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg3 = data.readInt();
                    String _arg4 = data.readString();
                    String _arg5 = data.readString();
                    String _arg6 = data.readString();
                    String _arg7 = data.readString();
                    String _arg8 = data.readString();
                    int _arg9 = data.readInt();
                    boolean _result2 = updateAdnRecordsInEfBySearch(_arg3, _arg4, _arg5, _arg6, _arg7, _arg8, _arg9);
                    reply.writeNoException();
                    reply.writeInt(_result2 ? 1 : 0);
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg10 = data.readInt();
                    String _arg11 = data.readString();
                    String _arg12 = data.readString();
                    int _arg13 = data.readInt();
                    String _arg14 = data.readString();
                    int _arg15 = data.readInt();
                    boolean _result3 = updateAdnRecordsInEfByIndex(_arg10, _arg11, _arg12, _arg13, _arg14, _arg15);
                    reply.writeNoException();
                    reply.writeInt(_result3 ? 1 : 0);
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg16 = data.readInt();
                    if (data.readInt() != 0) {
                        _arg1 = (ContentValues) ContentValues.CREATOR.createFromParcel(data);
                    } else {
                        _arg1 = null;
                    }
                    String _arg17 = data.readString();
                    int _arg18 = data.readInt();
                    boolean _result4 = updateAdnRecordsInEfBySearchOnSubscription(_arg16, _arg1, _arg17, _arg18);
                    reply.writeNoException();
                    reply.writeInt(_result4 ? 1 : 0);
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg19 = data.readInt();
                    int _arg20 = data.readInt();
                    int[] _result5 = getAdnRecordsSize(_arg19, _arg20);
                    reply.writeNoException();
                    reply.writeIntArray(_result5);
                    return true;
                case 6:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg21 = data.readInt();
                    String _arg22 = data.readString();
                    String _arg23 = data.readString();
                    String[] _arg24 = data.createStringArray();
                    String[] _arg25 = data.createStringArray();
                    int _arg26 = data.readInt();
                    String _arg27 = data.readString();
                    int _arg28 = data.readInt();
                    boolean _result6 = updateUsimAdnRecordsInEfByIndex(_arg21, _arg22, _arg23, _arg24, _arg25, _arg26, _arg27, _arg28);
                    reply.writeNoException();
                    reply.writeInt(_result6 ? 1 : 0);
                    return true;
                case 7:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg29 = data.readInt();
                    int _result7 = getAdnCount(_arg29);
                    reply.writeNoException();
                    reply.writeInt(_result7);
                    return true;
                case 8:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg30 = data.readInt();
                    int _result8 = getAnrCount(_arg30);
                    reply.writeNoException();
                    reply.writeInt(_result8);
                    return true;
                case 9:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg31 = data.readInt();
                    int _result9 = getEmailCount(_arg31);
                    reply.writeNoException();
                    reply.writeInt(_result9);
                    return true;
                case 10:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg32 = data.readInt();
                    int _result10 = getSpareAnrCount(_arg32);
                    reply.writeNoException();
                    reply.writeInt(_result10);
                    return true;
                case 11:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg33 = data.readInt();
                    int _result11 = getSpareEmailCount(_arg33);
                    reply.writeNoException();
                    reply.writeInt(_result11);
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements IIccPhoneBookMSim {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public List<AdnRecord> getAdnRecordsInEf(int efid, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    _data.writeInt(subscription);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    List<AdnRecord> _result = _reply.createTypedArrayList(AdnRecord.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    _data.writeString(oldTag);
                    _data.writeString(oldPhoneNumber);
                    _data.writeString(newTag);
                    _data.writeString(newPhoneNumber);
                    _data.writeString(pin2);
                    _data.writeInt(subscription);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, int index, String pin2, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    _data.writeString(newTag);
                    _data.writeString(newPhoneNumber);
                    _data.writeInt(index);
                    _data.writeString(pin2);
                    _data.writeInt(subscription);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public boolean updateAdnRecordsInEfBySearchOnSubscription(int efid, ContentValues values, String pin2, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    if (values != null) {
                        _data.writeInt(1);
                        values.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeString(pin2);
                    _data.writeInt(subscription);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public int[] getAdnRecordsSize(int efid, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    _data.writeInt(subscription);
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                    int[] _result = _reply.createIntArray();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public boolean updateUsimAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, String[] anrNumbers, String[] emails, int index, String pin2, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    _data.writeString(newTag);
                    _data.writeString(newPhoneNumber);
                    _data.writeStringArray(anrNumbers);
                    _data.writeStringArray(emails);
                    _data.writeInt(index);
                    _data.writeString(pin2);
                    _data.writeInt(subscription);
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public int getAdnCount(int sub) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(sub);
                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public int getAnrCount(int sub) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(sub);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public int getEmailCount(int sub) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(sub);
                    this.mRemote.transact(9, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public int getSpareAnrCount(int sub) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(sub);
                    this.mRemote.transact(10, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.IIccPhoneBookMSim
            public int getSpareEmailCount(int sub) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(sub);
                    this.mRemote.transact(11, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
