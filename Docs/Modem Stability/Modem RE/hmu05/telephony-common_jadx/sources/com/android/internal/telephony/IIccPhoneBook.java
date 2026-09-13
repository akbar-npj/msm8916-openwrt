package com.android.internal.telephony;

import android.content.ContentValues;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.android.internal.telephony.uicc.AdnRecord;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public interface IIccPhoneBook extends IInterface {
    int getAdnCount() throws RemoteException;

    List<AdnRecord> getAdnRecordsInEf(int i) throws RemoteException;

    int[] getAdnRecordsSize(int i) throws RemoteException;

    int getAnrCount() throws RemoteException;

    int getEmailCount() throws RemoteException;

    int getSpareAnrCount() throws RemoteException;

    int getSpareEmailCount() throws RemoteException;

    boolean updateAdnRecordsInEfByIndex(int i, String str, String str2, int i2, String str3) throws RemoteException;

    boolean updateAdnRecordsInEfBySearch(int i, String str, String str2, String str3, String str4, String str5) throws RemoteException;

    boolean updateAdnRecordsWithContentValuesInEfBySearch(int i, ContentValues contentValues, String str) throws RemoteException;

    boolean updateUsimAdnRecordsInEfByIndex(int i, String str, String str2, String[] strArr, String[] strArr2, int i2, String str3) throws RemoteException;

    public static abstract class Stub extends Binder implements IIccPhoneBook {
        private static final String DESCRIPTOR = "com.android.internal.telephony.IIccPhoneBook";
        static final int TRANSACTION_getAdnCount = 7;
        static final int TRANSACTION_getAdnRecordsInEf = 1;
        static final int TRANSACTION_getAdnRecordsSize = 5;
        static final int TRANSACTION_getAnrCount = 8;
        static final int TRANSACTION_getEmailCount = 9;
        static final int TRANSACTION_getSpareAnrCount = 10;
        static final int TRANSACTION_getSpareEmailCount = 11;
        static final int TRANSACTION_updateAdnRecordsInEfByIndex = 4;
        static final int TRANSACTION_updateAdnRecordsInEfBySearch = 2;
        static final int TRANSACTION_updateAdnRecordsWithContentValuesInEfBySearch = 3;
        static final int TRANSACTION_updateUsimAdnRecordsInEfByIndex = 6;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IIccPhoneBook asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof IIccPhoneBook)) {
                return (IIccPhoneBook) iin;
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
                    List<AdnRecord> _result = getAdnRecordsInEf(_arg0);
                    reply.writeNoException();
                    reply.writeTypedList(_result);
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg2 = data.readInt();
                    String _arg3 = data.readString();
                    String _arg4 = data.readString();
                    String _arg5 = data.readString();
                    String _arg6 = data.readString();
                    String _arg7 = data.readString();
                    boolean _result2 = updateAdnRecordsInEfBySearch(_arg2, _arg3, _arg4, _arg5, _arg6, _arg7);
                    reply.writeNoException();
                    reply.writeInt(_result2 ? 1 : 0);
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg8 = data.readInt();
                    if (data.readInt() != 0) {
                        _arg1 = (ContentValues) ContentValues.CREATOR.createFromParcel(data);
                    } else {
                        _arg1 = null;
                    }
                    String _arg9 = data.readString();
                    boolean _result3 = updateAdnRecordsWithContentValuesInEfBySearch(_arg8, _arg1, _arg9);
                    reply.writeNoException();
                    reply.writeInt(_result3 ? 1 : 0);
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg10 = data.readInt();
                    String _arg11 = data.readString();
                    String _arg12 = data.readString();
                    int _arg13 = data.readInt();
                    String _arg14 = data.readString();
                    boolean _result4 = updateAdnRecordsInEfByIndex(_arg10, _arg11, _arg12, _arg13, _arg14);
                    reply.writeNoException();
                    reply.writeInt(_result4 ? 1 : 0);
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg15 = data.readInt();
                    int[] _result5 = getAdnRecordsSize(_arg15);
                    reply.writeNoException();
                    reply.writeIntArray(_result5);
                    return true;
                case 6:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg16 = data.readInt();
                    String _arg17 = data.readString();
                    String _arg18 = data.readString();
                    String[] _arg19 = data.createStringArray();
                    String[] _arg20 = data.createStringArray();
                    int _arg21 = data.readInt();
                    String _arg22 = data.readString();
                    boolean _result6 = updateUsimAdnRecordsInEfByIndex(_arg16, _arg17, _arg18, _arg19, _arg20, _arg21, _arg22);
                    reply.writeNoException();
                    reply.writeInt(_result6 ? 1 : 0);
                    return true;
                case 7:
                    data.enforceInterface(DESCRIPTOR);
                    int _result7 = getAdnCount();
                    reply.writeNoException();
                    reply.writeInt(_result7);
                    return true;
                case 8:
                    data.enforceInterface(DESCRIPTOR);
                    int _result8 = getAnrCount();
                    reply.writeNoException();
                    reply.writeInt(_result8);
                    return true;
                case 9:
                    data.enforceInterface(DESCRIPTOR);
                    int _result9 = getEmailCount();
                    reply.writeNoException();
                    reply.writeInt(_result9);
                    return true;
                case 10:
                    data.enforceInterface(DESCRIPTOR);
                    int _result10 = getSpareAnrCount();
                    reply.writeNoException();
                    reply.writeInt(_result10);
                    return true;
                case 11:
                    data.enforceInterface(DESCRIPTOR);
                    int _result11 = getSpareEmailCount();
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

        private static class Proxy implements IIccPhoneBook {
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

            @Override // com.android.internal.telephony.IIccPhoneBook
            public List<AdnRecord> getAdnRecordsInEf(int efid) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    List<AdnRecord> _result = _reply.createTypedArrayList(AdnRecord.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public boolean updateAdnRecordsInEfBySearch(int efid, String oldTag, String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) throws RemoteException {
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
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public boolean updateAdnRecordsWithContentValuesInEfBySearch(int efid, ContentValues values, String pin2) throws RemoteException {
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
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public boolean updateAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, int index, String pin2) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    _data.writeString(newTag);
                    _data.writeString(newPhoneNumber);
                    _data.writeInt(index);
                    _data.writeString(pin2);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public int[] getAdnRecordsSize(int efid) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(efid);
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                    int[] _result = _reply.createIntArray();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public boolean updateUsimAdnRecordsInEfByIndex(int efid, String newTag, String newPhoneNumber, String[] anrNumbers, String[] emails, int index, String pin2) throws RemoteException {
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
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public int getAdnCount() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public int getAnrCount() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public int getEmailCount() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(9, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public int getSpareAnrCount() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(10, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.IIccPhoneBook
            public int getSpareEmailCount() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
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
