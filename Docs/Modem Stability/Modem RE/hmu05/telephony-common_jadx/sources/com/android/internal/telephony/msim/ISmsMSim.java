package com.android.internal.telephony.msim;

import android.app.PendingIntent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.android.internal.telephony.SmsRawData;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public interface ISmsMSim extends IInterface {
    boolean copyMessageToIccEf(String str, int i, byte[] bArr, byte[] bArr2, int i2) throws RemoteException;

    boolean disableCellBroadcast(int i, int i2) throws RemoteException;

    boolean disableCellBroadcastRange(int i, int i2, int i3) throws RemoteException;

    boolean enableCellBroadcast(int i, int i2) throws RemoteException;

    boolean enableCellBroadcastRange(int i, int i2, int i3) throws RemoteException;

    List<SmsRawData> getAllMessagesFromIccEf(String str, int i) throws RemoteException;

    String getImsSmsFormat(int i) throws RemoteException;

    int getPreferredSmsSubscription() throws RemoteException;

    int getPremiumSmsPermission(String str, int i) throws RemoteException;

    int getSmsCapacityOnIcc(int i) throws RemoteException;

    boolean isImsSmsSupported(int i) throws RemoteException;

    boolean isSMSPromptEnabled() throws RemoteException;

    void sendData(String str, String str2, String str3, int i, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i2) throws RemoteException;

    void sendDataWithOrigPort(String str, String str2, String str3, int i, int i2, byte[] bArr, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i3) throws RemoteException;

    void sendMultipartText(String str, String str2, String str3, List<String> list, List<PendingIntent> list2, List<PendingIntent> list3, int i) throws RemoteException;

    void sendMultipartTextWithOptions(String str, String str2, String str3, List<String> list, List<PendingIntent> list2, List<PendingIntent> list3, int i, boolean z, int i2, int i3) throws RemoteException;

    void sendText(String str, String str2, String str3, String str4, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i) throws RemoteException;

    void sendTextWithOptions(String str, String str2, String str3, String str4, PendingIntent pendingIntent, PendingIntent pendingIntent2, int i, boolean z, int i2, int i3) throws RemoteException;

    void setPremiumSmsPermission(String str, int i, int i2) throws RemoteException;

    boolean updateMessageOnIccEf(String str, int i, int i2, byte[] bArr, int i3) throws RemoteException;

    public static abstract class Stub extends Binder implements ISmsMSim {
        private static final String DESCRIPTOR = "com.android.internal.telephony.msim.ISmsMSim";
        static final int TRANSACTION_copyMessageToIccEf = 3;
        static final int TRANSACTION_disableCellBroadcast = 11;
        static final int TRANSACTION_disableCellBroadcastRange = 13;
        static final int TRANSACTION_enableCellBroadcast = 10;
        static final int TRANSACTION_enableCellBroadcastRange = 12;
        static final int TRANSACTION_getAllMessagesFromIccEf = 1;
        static final int TRANSACTION_getImsSmsFormat = 17;
        static final int TRANSACTION_getPreferredSmsSubscription = 18;
        static final int TRANSACTION_getPremiumSmsPermission = 14;
        static final int TRANSACTION_getSmsCapacityOnIcc = 20;
        static final int TRANSACTION_isImsSmsSupported = 16;
        static final int TRANSACTION_isSMSPromptEnabled = 19;
        static final int TRANSACTION_sendData = 4;
        static final int TRANSACTION_sendDataWithOrigPort = 5;
        static final int TRANSACTION_sendMultipartText = 8;
        static final int TRANSACTION_sendMultipartTextWithOptions = 9;
        static final int TRANSACTION_sendText = 6;
        static final int TRANSACTION_sendTextWithOptions = 7;
        static final int TRANSACTION_setPremiumSmsPermission = 15;
        static final int TRANSACTION_updateMessageOnIccEf = 2;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ISmsMSim asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof ISmsMSim)) {
                return (ISmsMSim) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            PendingIntent _arg4;
            PendingIntent _arg5;
            PendingIntent _arg6;
            PendingIntent _arg7;
            PendingIntent _arg8;
            PendingIntent _arg9;
            PendingIntent _arg10;
            PendingIntent _arg11;
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg0 = data.readString();
                    int _arg1 = data.readInt();
                    List<SmsRawData> _result = getAllMessagesFromIccEf(_arg0, _arg1);
                    reply.writeNoException();
                    reply.writeTypedList(_result);
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg2 = data.readString();
                    int _arg3 = data.readInt();
                    int _arg12 = data.readInt();
                    byte[] _arg13 = data.createByteArray();
                    int _arg14 = data.readInt();
                    boolean _result2 = updateMessageOnIccEf(_arg2, _arg3, _arg12, _arg13, _arg14);
                    reply.writeNoException();
                    reply.writeInt(_result2 ? 1 : 0);
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg15 = data.readString();
                    int _arg16 = data.readInt();
                    byte[] _arg17 = data.createByteArray();
                    byte[] _arg18 = data.createByteArray();
                    int _arg19 = data.readInt();
                    boolean _result3 = copyMessageToIccEf(_arg15, _arg16, _arg17, _arg18, _arg19);
                    reply.writeNoException();
                    reply.writeInt(_result3 ? 1 : 0);
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg20 = data.readString();
                    String _arg21 = data.readString();
                    String _arg22 = data.readString();
                    int _arg23 = data.readInt();
                    byte[] _arg24 = data.createByteArray();
                    if (data.readInt() != 0) {
                        _arg10 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg10 = null;
                    }
                    if (data.readInt() != 0) {
                        _arg11 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg11 = null;
                    }
                    int _arg25 = data.readInt();
                    sendData(_arg20, _arg21, _arg22, _arg23, _arg24, _arg10, _arg11, _arg25);
                    reply.writeNoException();
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg26 = data.readString();
                    String _arg27 = data.readString();
                    String _arg28 = data.readString();
                    int _arg29 = data.readInt();
                    int _arg30 = data.readInt();
                    byte[] _arg31 = data.createByteArray();
                    if (data.readInt() != 0) {
                        _arg8 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg8 = null;
                    }
                    if (data.readInt() != 0) {
                        _arg9 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg9 = null;
                    }
                    int _arg32 = data.readInt();
                    sendDataWithOrigPort(_arg26, _arg27, _arg28, _arg29, _arg30, _arg31, _arg8, _arg9, _arg32);
                    reply.writeNoException();
                    return true;
                case 6:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg33 = data.readString();
                    String _arg34 = data.readString();
                    String _arg35 = data.readString();
                    String _arg36 = data.readString();
                    if (data.readInt() != 0) {
                        _arg6 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg6 = null;
                    }
                    if (data.readInt() != 0) {
                        _arg7 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg7 = null;
                    }
                    int _arg37 = data.readInt();
                    sendText(_arg33, _arg34, _arg35, _arg36, _arg6, _arg7, _arg37);
                    reply.writeNoException();
                    return true;
                case 7:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg38 = data.readString();
                    String _arg39 = data.readString();
                    String _arg40 = data.readString();
                    String _arg41 = data.readString();
                    if (data.readInt() != 0) {
                        _arg4 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg4 = null;
                    }
                    if (data.readInt() != 0) {
                        _arg5 = (PendingIntent) PendingIntent.CREATOR.createFromParcel(data);
                    } else {
                        _arg5 = null;
                    }
                    int _arg42 = data.readInt();
                    boolean _arg43 = data.readInt() != 0;
                    int _arg44 = data.readInt();
                    int _arg45 = data.readInt();
                    sendTextWithOptions(_arg38, _arg39, _arg40, _arg41, _arg4, _arg5, _arg42, _arg43, _arg44, _arg45);
                    reply.writeNoException();
                    return true;
                case 8:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg46 = data.readString();
                    String _arg47 = data.readString();
                    String _arg48 = data.readString();
                    List<String> _arg49 = data.createStringArrayList();
                    List<PendingIntent> _arg50 = data.createTypedArrayList(PendingIntent.CREATOR);
                    List<PendingIntent> _arg51 = data.createTypedArrayList(PendingIntent.CREATOR);
                    int _arg52 = data.readInt();
                    sendMultipartText(_arg46, _arg47, _arg48, _arg49, _arg50, _arg51, _arg52);
                    reply.writeNoException();
                    return true;
                case 9:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg53 = data.readString();
                    String _arg54 = data.readString();
                    String _arg55 = data.readString();
                    List<String> _arg56 = data.createStringArrayList();
                    List<PendingIntent> _arg57 = data.createTypedArrayList(PendingIntent.CREATOR);
                    List<PendingIntent> _arg58 = data.createTypedArrayList(PendingIntent.CREATOR);
                    int _arg59 = data.readInt();
                    boolean _arg60 = data.readInt() != 0;
                    int _arg61 = data.readInt();
                    int _arg62 = data.readInt();
                    sendMultipartTextWithOptions(_arg53, _arg54, _arg55, _arg56, _arg57, _arg58, _arg59, _arg60, _arg61, _arg62);
                    reply.writeNoException();
                    return true;
                case 10:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg63 = data.readInt();
                    int _arg64 = data.readInt();
                    boolean _result4 = enableCellBroadcast(_arg63, _arg64);
                    reply.writeNoException();
                    reply.writeInt(_result4 ? 1 : 0);
                    return true;
                case 11:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg65 = data.readInt();
                    int _arg66 = data.readInt();
                    boolean _result5 = disableCellBroadcast(_arg65, _arg66);
                    reply.writeNoException();
                    reply.writeInt(_result5 ? 1 : 0);
                    return true;
                case 12:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg67 = data.readInt();
                    int _arg68 = data.readInt();
                    int _arg69 = data.readInt();
                    boolean _result6 = enableCellBroadcastRange(_arg67, _arg68, _arg69);
                    reply.writeNoException();
                    reply.writeInt(_result6 ? 1 : 0);
                    return true;
                case 13:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg70 = data.readInt();
                    int _arg71 = data.readInt();
                    int _arg72 = data.readInt();
                    boolean _result7 = disableCellBroadcastRange(_arg70, _arg71, _arg72);
                    reply.writeNoException();
                    reply.writeInt(_result7 ? 1 : 0);
                    return true;
                case 14:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg73 = data.readString();
                    int _arg74 = data.readInt();
                    int _result8 = getPremiumSmsPermission(_arg73, _arg74);
                    reply.writeNoException();
                    reply.writeInt(_result8);
                    return true;
                case 15:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg75 = data.readString();
                    int _arg76 = data.readInt();
                    int _arg77 = data.readInt();
                    setPremiumSmsPermission(_arg75, _arg76, _arg77);
                    reply.writeNoException();
                    return true;
                case 16:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg78 = data.readInt();
                    boolean _result9 = isImsSmsSupported(_arg78);
                    reply.writeNoException();
                    reply.writeInt(_result9 ? 1 : 0);
                    return true;
                case 17:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg79 = data.readInt();
                    String _result10 = getImsSmsFormat(_arg79);
                    reply.writeNoException();
                    reply.writeString(_result10);
                    return true;
                case 18:
                    data.enforceInterface(DESCRIPTOR);
                    int _result11 = getPreferredSmsSubscription();
                    reply.writeNoException();
                    reply.writeInt(_result11);
                    return true;
                case 19:
                    data.enforceInterface(DESCRIPTOR);
                    boolean _result12 = isSMSPromptEnabled();
                    reply.writeNoException();
                    reply.writeInt(_result12 ? 1 : 0);
                    return true;
                case 20:
                    data.enforceInterface(DESCRIPTOR);
                    int _arg80 = data.readInt();
                    int _result13 = getSmsCapacityOnIcc(_arg80);
                    reply.writeNoException();
                    reply.writeInt(_result13);
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ISmsMSim {
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

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public List<SmsRawData> getAllMessagesFromIccEf(String callingPkg, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeInt(subscription);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    List<SmsRawData> _result = _reply.createTypedArrayList(SmsRawData.CREATOR);
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public boolean updateMessageOnIccEf(String callingPkg, int messageIndex, int newStatus, byte[] pdu, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeInt(messageIndex);
                    _data.writeInt(newStatus);
                    _data.writeByteArray(pdu);
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

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public boolean copyMessageToIccEf(String callingPkg, int status, byte[] pdu, byte[] smsc, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeInt(status);
                    _data.writeByteArray(pdu);
                    _data.writeByteArray(smsc);
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

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public void sendData(String callingPkg, String destAddr, String scAddr, int destPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeString(destAddr);
                    _data.writeString(scAddr);
                    _data.writeInt(destPort);
                    _data.writeByteArray(data);
                    if (sentIntent != null) {
                        _data.writeInt(1);
                        sentIntent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    if (deliveryIntent != null) {
                        _data.writeInt(1);
                        deliveryIntent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeInt(subscription);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public void sendDataWithOrigPort(String callingPkg, String destAddr, String scAddr, int destPort, int origPort, byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeString(destAddr);
                    _data.writeString(scAddr);
                    _data.writeInt(destPort);
                    _data.writeInt(origPort);
                    _data.writeByteArray(data);
                    if (sentIntent != null) {
                        _data.writeInt(1);
                        sentIntent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    if (deliveryIntent != null) {
                        _data.writeInt(1);
                        deliveryIntent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeInt(subscription);
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public void sendText(String callingPkg, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeString(destAddr);
                    _data.writeString(scAddr);
                    _data.writeString(text);
                    if (sentIntent != null) {
                        _data.writeInt(1);
                        sentIntent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    if (deliveryIntent != null) {
                        _data.writeInt(1);
                        deliveryIntent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeInt(subscription);
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public void sendTextWithOptions(String callingPkg, String destAddr, String scAddr, String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int priority, boolean isExpectMore, int validityPeriod, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeString(destAddr);
                    _data.writeString(scAddr);
                    _data.writeString(text);
                    if (sentIntent != null) {
                        _data.writeInt(1);
                        sentIntent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    if (deliveryIntent != null) {
                        _data.writeInt(1);
                        deliveryIntent.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeInt(priority);
                    _data.writeInt(isExpectMore ? 1 : 0);
                    _data.writeInt(validityPeriod);
                    _data.writeInt(subscription);
                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public void sendMultipartText(String callingPkg, String destinationAddress, String scAddress, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeString(destinationAddress);
                    _data.writeString(scAddress);
                    _data.writeStringList(parts);
                    _data.writeTypedList(sentIntents);
                    _data.writeTypedList(deliveryIntents);
                    _data.writeInt(subscription);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public void sendMultipartTextWithOptions(String callingPkg, String destinationAddress, String scAddress, List<String> parts, List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, int priority, boolean isExpectMore, int validityPeriod, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(callingPkg);
                    _data.writeString(destinationAddress);
                    _data.writeString(scAddress);
                    _data.writeStringList(parts);
                    _data.writeTypedList(sentIntents);
                    _data.writeTypedList(deliveryIntents);
                    _data.writeInt(priority);
                    _data.writeInt(isExpectMore ? 1 : 0);
                    _data.writeInt(validityPeriod);
                    _data.writeInt(subscription);
                    this.mRemote.transact(9, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public boolean enableCellBroadcast(int messageIdentifier, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(messageIdentifier);
                    _data.writeInt(subscription);
                    this.mRemote.transact(10, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public boolean disableCellBroadcast(int messageIdentifier, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(messageIdentifier);
                    _data.writeInt(subscription);
                    this.mRemote.transact(11, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public boolean enableCellBroadcastRange(int startMessageId, int endMessageId, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(startMessageId);
                    _data.writeInt(endMessageId);
                    _data.writeInt(subscription);
                    this.mRemote.transact(12, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public boolean disableCellBroadcastRange(int startMessageId, int endMessageId, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(startMessageId);
                    _data.writeInt(endMessageId);
                    _data.writeInt(subscription);
                    this.mRemote.transact(13, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public int getPremiumSmsPermission(String packageName, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(packageName);
                    _data.writeInt(subscription);
                    this.mRemote.transact(14, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public void setPremiumSmsPermission(String packageName, int permission, int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(packageName);
                    _data.writeInt(permission);
                    _data.writeInt(subscription);
                    this.mRemote.transact(15, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public boolean isImsSmsSupported(int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(subscription);
                    this.mRemote.transact(16, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public String getImsSmsFormat(int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(subscription);
                    this.mRemote.transact(17, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public int getPreferredSmsSubscription() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(18, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public boolean isSMSPromptEnabled() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(19, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.internal.telephony.msim.ISmsMSim
            public int getSmsCapacityOnIcc(int subscription) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(subscription);
                    this.mRemote.transact(20, _data, _reply, 0);
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
