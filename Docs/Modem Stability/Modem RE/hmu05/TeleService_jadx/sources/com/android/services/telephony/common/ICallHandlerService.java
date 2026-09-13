package com.android.services.telephony.common;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public interface ICallHandlerService extends IInterface {
    void bringToForeground(boolean z) throws RemoteException;

    void onActiveSubChanged(int i) throws RemoteException;

    void onAudioModeChange(int i, boolean z) throws RemoteException;

    void onDisconnect(Call call) throws RemoteException;

    void onIncoming(Call call, List<String> list) throws RemoteException;

    void onModifyCall(Call call) throws RemoteException;

    void onPostDialWait(int i, String str) throws RemoteException;

    void onSuppServiceFailed(int i) throws RemoteException;

    void onSupportedAudioModeChange(int i) throws RemoteException;

    void onUpdate(List<Call> list) throws RemoteException;

    void startCallService(ICallCommandService iCallCommandService) throws RemoteException;

    public static abstract class Stub extends Binder implements ICallHandlerService {
        public Stub() {
            attachInterface(this, "com.android.services.telephony.common.ICallHandlerService");
        }

        public static ICallHandlerService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface("com.android.services.telephony.common.ICallHandlerService");
            if (iin != null && (iin instanceof ICallHandlerService)) {
                return (ICallHandlerService) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            Call _arg0;
            Call _arg1;
            Call _arg2;
            switch (code) {
                case 1:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    ICallCommandService _arg3 = ICallCommandService.Stub.asInterface(data.readStrongBinder());
                    startCallService(_arg3);
                    return true;
                case 2:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    if (data.readInt() != 0) {
                        _arg2 = Call.CREATOR.createFromParcel(data);
                    } else {
                        _arg2 = null;
                    }
                    List<String> _arg4 = data.createStringArrayList();
                    onIncoming(_arg2, _arg4);
                    return true;
                case 3:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    List<Call> _arg5 = data.createTypedArrayList(Call.CREATOR);
                    onUpdate(_arg5);
                    return true;
                case 4:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    if (data.readInt() != 0) {
                        _arg1 = Call.CREATOR.createFromParcel(data);
                    } else {
                        _arg1 = null;
                    }
                    onDisconnect(_arg1);
                    return true;
                case 5:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    int _arg6 = data.readInt();
                    boolean _arg7 = data.readInt() != 0;
                    onAudioModeChange(_arg6, _arg7);
                    return true;
                case 6:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    int _arg8 = data.readInt();
                    onSupportedAudioModeChange(_arg8);
                    return true;
                case 7:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    boolean _arg9 = data.readInt() != 0;
                    bringToForeground(_arg9);
                    return true;
                case 8:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    int _arg10 = data.readInt();
                    String _arg11 = data.readString();
                    onPostDialWait(_arg10, _arg11);
                    return true;
                case 9:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    if (data.readInt() != 0) {
                        _arg0 = Call.CREATOR.createFromParcel(data);
                    } else {
                        _arg0 = null;
                    }
                    onModifyCall(_arg0);
                    return true;
                case 10:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    int _arg12 = data.readInt();
                    onActiveSubChanged(_arg12);
                    return true;
                case 11:
                    data.enforceInterface("com.android.services.telephony.common.ICallHandlerService");
                    int _arg13 = data.readInt();
                    onSuppServiceFailed(_arg13);
                    return true;
                case 1598968902:
                    reply.writeString("com.android.services.telephony.common.ICallHandlerService");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ICallHandlerService {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void startCallService(ICallCommandService callCommandService) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    _data.writeStrongBinder(callCommandService != null ? callCommandService.asBinder() : null);
                    this.mRemote.transact(1, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onIncoming(Call call, List<String> textReponses) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    if (call != null) {
                        _data.writeInt(1);
                        call.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeStringList(textReponses);
                    this.mRemote.transact(2, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onUpdate(List<Call> call) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    _data.writeTypedList(call);
                    this.mRemote.transact(3, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onDisconnect(Call call) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    if (call != null) {
                        _data.writeInt(1);
                        call.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(4, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onAudioModeChange(int mode, boolean muted) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    _data.writeInt(mode);
                    _data.writeInt(muted ? 1 : 0);
                    this.mRemote.transact(5, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onSupportedAudioModeChange(int modeMask) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    _data.writeInt(modeMask);
                    this.mRemote.transact(6, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void bringToForeground(boolean showDialpad) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    _data.writeInt(showDialpad ? 1 : 0);
                    this.mRemote.transact(7, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onPostDialWait(int callId, String remainingChars) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    _data.writeInt(callId);
                    _data.writeString(remainingChars);
                    this.mRemote.transact(8, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onModifyCall(Call call) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    if (call != null) {
                        _data.writeInt(1);
                        call.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(9, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onActiveSubChanged(int activeSub) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    _data.writeInt(activeSub);
                    this.mRemote.transact(10, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallHandlerService
            public void onSuppServiceFailed(int service) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallHandlerService");
                    _data.writeInt(service);
                    this.mRemote.transact(11, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }
        }
    }
}
