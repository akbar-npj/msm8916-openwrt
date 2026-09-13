package com.android.services.telephony.common;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface ICallCommandService extends IInterface {
    void addCall() throws RemoteException;

    void answerCall(int i) throws RemoteException;

    void answerCallWithCallType(int i, int i2) throws RemoteException;

    void disconnectCall(int i) throws RemoteException;

    int getActiveSubscription() throws RemoteException;

    void hangupWithReason(int i, String str, boolean z, int i2, String str2) throws RemoteException;

    void hold(int i, boolean z) throws RemoteException;

    void merge() throws RemoteException;

    void modifyCallConfirm(boolean z, int i) throws RemoteException;

    void modifyCallInitiate(int i, int i2) throws RemoteException;

    void mute(boolean z) throws RemoteException;

    void muteInternal(boolean z) throws RemoteException;

    void playDtmfTone(char c, boolean z) throws RemoteException;

    void postDialCancel(int i) throws RemoteException;

    void postDialWaitContinue(int i) throws RemoteException;

    void rejectCall(Call call, boolean z, String str) throws RemoteException;

    void separateCall(int i) throws RemoteException;

    void setActiveAndConversationSub(int i) throws RemoteException;

    void setActiveSubscription(int i) throws RemoteException;

    void setAudioMode(int i) throws RemoteException;

    void setSubInConversation(int i) throws RemoteException;

    void setSystemBarNavigationEnabled(boolean z) throws RemoteException;

    void speaker(boolean z) throws RemoteException;

    void stopDtmfTone() throws RemoteException;

    void swap() throws RemoteException;

    void updateMuteState(int i, boolean z) throws RemoteException;

    public static abstract class Stub extends Binder implements ICallCommandService {
        public Stub() {
            attachInterface(this, "com.android.services.telephony.common.ICallCommandService");
        }

        public static ICallCommandService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface("com.android.services.telephony.common.ICallCommandService");
            if (iin != null && (iin instanceof ICallCommandService)) {
                return (ICallCommandService) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            Call _arg0;
            switch (code) {
                case 1:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg1 = data.readInt();
                    answerCall(_arg1);
                    reply.writeNoException();
                    return true;
                case 2:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg2 = data.readInt();
                    int _arg3 = data.readInt();
                    answerCallWithCallType(_arg2, _arg3);
                    reply.writeNoException();
                    return true;
                case 3:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg4 = data.readInt();
                    int _arg5 = data.readInt();
                    modifyCallInitiate(_arg4, _arg5);
                    reply.writeNoException();
                    return true;
                case 4:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    boolean _arg6 = data.readInt() != 0;
                    int _arg7 = data.readInt();
                    modifyCallConfirm(_arg6, _arg7);
                    reply.writeNoException();
                    return true;
                case 5:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    if (data.readInt() != 0) {
                        _arg0 = Call.CREATOR.createFromParcel(data);
                    } else {
                        _arg0 = null;
                    }
                    boolean _arg8 = data.readInt() != 0;
                    String _arg9 = data.readString();
                    rejectCall(_arg0, _arg8, _arg9);
                    reply.writeNoException();
                    return true;
                case 6:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg10 = data.readInt();
                    disconnectCall(_arg10);
                    reply.writeNoException();
                    return true;
                case 7:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg11 = data.readInt();
                    separateCall(_arg11);
                    reply.writeNoException();
                    return true;
                case 8:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg12 = data.readInt();
                    boolean _arg13 = data.readInt() != 0;
                    hold(_arg12, _arg13);
                    reply.writeNoException();
                    return true;
                case 9:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    merge();
                    reply.writeNoException();
                    return true;
                case 10:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    swap();
                    reply.writeNoException();
                    return true;
                case 11:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    addCall();
                    reply.writeNoException();
                    return true;
                case 12:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    boolean _arg14 = data.readInt() != 0;
                    mute(_arg14);
                    reply.writeNoException();
                    return true;
                case 13:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    boolean _arg15 = data.readInt() != 0;
                    muteInternal(_arg15);
                    reply.writeNoException();
                    return true;
                case 14:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    boolean _arg16 = data.readInt() != 0;
                    speaker(_arg16);
                    reply.writeNoException();
                    return true;
                case 15:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    char _arg17 = (char) data.readInt();
                    boolean _arg18 = data.readInt() != 0;
                    playDtmfTone(_arg17, _arg18);
                    reply.writeNoException();
                    return true;
                case 16:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    stopDtmfTone();
                    reply.writeNoException();
                    return true;
                case 17:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg19 = data.readInt();
                    setAudioMode(_arg19);
                    reply.writeNoException();
                    return true;
                case 18:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg20 = data.readInt();
                    postDialCancel(_arg20);
                    reply.writeNoException();
                    return true;
                case 19:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg21 = data.readInt();
                    postDialWaitContinue(_arg21);
                    reply.writeNoException();
                    return true;
                case 20:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    boolean _arg22 = data.readInt() != 0;
                    setSystemBarNavigationEnabled(_arg22);
                    reply.writeNoException();
                    return true;
                case 21:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg23 = data.readInt();
                    String _arg24 = data.readString();
                    boolean _arg25 = data.readInt() != 0;
                    int _arg26 = data.readInt();
                    String _arg27 = data.readString();
                    hangupWithReason(_arg23, _arg24, _arg25, _arg26, _arg27);
                    reply.writeNoException();
                    return true;
                case 22:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _result = getActiveSubscription();
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                case 23:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg28 = data.readInt();
                    setActiveSubscription(_arg28);
                    reply.writeNoException();
                    return true;
                case 24:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg29 = data.readInt();
                    setSubInConversation(_arg29);
                    reply.writeNoException();
                    return true;
                case 25:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg30 = data.readInt();
                    setActiveAndConversationSub(_arg30);
                    reply.writeNoException();
                    return true;
                case 26:
                    data.enforceInterface("com.android.services.telephony.common.ICallCommandService");
                    int _arg31 = data.readInt();
                    boolean _arg32 = data.readInt() != 0;
                    updateMuteState(_arg31, _arg32);
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString("com.android.services.telephony.common.ICallCommandService");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ICallCommandService {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void answerCall(int callId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void answerCallWithCallType(int callId, int callType) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    _data.writeInt(callType);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void modifyCallInitiate(int callId, int callType) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    _data.writeInt(callType);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void modifyCallConfirm(boolean responseType, int callId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(responseType ? 1 : 0);
                    _data.writeInt(callId);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void rejectCall(Call call, boolean rejectWithMessage, String message) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    if (call != null) {
                        _data.writeInt(1);
                        call.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeInt(rejectWithMessage ? 1 : 0);
                    _data.writeString(message);
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void disconnectCall(int callId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void separateCall(int callId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void hold(int callId, boolean hold) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    _data.writeInt(hold ? 1 : 0);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void merge() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    this.mRemote.transact(9, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void swap() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    this.mRemote.transact(10, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void addCall() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    this.mRemote.transact(11, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void mute(boolean onOff) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(onOff ? 1 : 0);
                    this.mRemote.transact(12, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void muteInternal(boolean onOff) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(onOff ? 1 : 0);
                    this.mRemote.transact(13, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void speaker(boolean onOff) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(onOff ? 1 : 0);
                    this.mRemote.transact(14, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void playDtmfTone(char digit, boolean timedShortTone) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(digit);
                    _data.writeInt(timedShortTone ? 1 : 0);
                    this.mRemote.transact(15, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void stopDtmfTone() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    this.mRemote.transact(16, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void setAudioMode(int mode) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(mode);
                    this.mRemote.transact(17, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void postDialCancel(int callId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    this.mRemote.transact(18, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void postDialWaitContinue(int callId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    this.mRemote.transact(19, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void setSystemBarNavigationEnabled(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(enable ? 1 : 0);
                    this.mRemote.transact(20, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void hangupWithReason(int callId, String userUri, boolean mpty, int failCause, String errorInfo) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(callId);
                    _data.writeString(userUri);
                    _data.writeInt(mpty ? 1 : 0);
                    _data.writeInt(failCause);
                    _data.writeString(errorInfo);
                    this.mRemote.transact(21, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public int getActiveSubscription() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    this.mRemote.transact(22, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void setActiveSubscription(int subscriptionId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(subscriptionId);
                    this.mRemote.transact(23, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void setSubInConversation(int subscriptionId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(subscriptionId);
                    this.mRemote.transact(24, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void setActiveAndConversationSub(int subscriptionId) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(subscriptionId);
                    this.mRemote.transact(25, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // com.android.services.telephony.common.ICallCommandService
            public void updateMuteState(int subscriptionId, boolean muted) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.android.services.telephony.common.ICallCommandService");
                    _data.writeInt(subscriptionId);
                    _data.writeInt(muted ? 1 : 0);
                    this.mRemote.transact(26, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
