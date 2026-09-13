package com.android.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;

/* JADX INFO: loaded from: classes.dex */
public class ContactsAsyncHelper {
    private static ContactsAsyncHelper sInstance = new ContactsAsyncHelper();
    private static Handler sThreadHandler;
    private final Handler mResultHandler = new Handler() { // from class: com.android.phone.ContactsAsyncHelper.1
        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            WorkerArgs args = (WorkerArgs) msg.obj;
            switch (msg.arg1) {
                case 1:
                    if (args.listener != null) {
                        args.listener.onImageLoadComplete(msg.what, args.photo, args.photoIcon, args.cookie);
                    }
                    break;
            }
        }
    };

    public interface OnImageLoadCompleteListener {
        void onImageLoadComplete(int i, Drawable drawable, Bitmap bitmap, Object obj);
    }

    private static final class WorkerArgs {
        public Context context;
        public Object cookie;
        public OnImageLoadCompleteListener listener;
        public Drawable photo;
        public Bitmap photoIcon;
        public Uri uri;

        private WorkerArgs() {
        }
    }

    private class WorkerHandler extends Handler {
        public WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            WorkerArgs args = (WorkerArgs) msg.obj;
            switch (msg.arg1) {
                case 1:
                    InputStream inputStream = null;
                    try {
                        try {
                            inputStream = ContactsContract.Contacts.openContactPhotoInputStream(args.context.getContentResolver(), args.uri, true);
                        } catch (Exception e) {
                            Log.e("ContactsAsyncHelper", "Error opening photo input stream", e);
                        }
                        if (inputStream != null) {
                            args.photo = Drawable.createFromStream(inputStream, args.uri.toString());
                            args.photoIcon = getPhotoIconWhenAppropriate(args.context, args.photo);
                        } else {
                            args.photo = null;
                            args.photoIcon = null;
                        }
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e2) {
                                Log.e("ContactsAsyncHelper", "Unable to close input stream.", e2);
                            }
                        }
                    } catch (Throwable th) {
                        if (0 != 0) {
                            try {
                                inputStream.close();
                            } catch (IOException e3) {
                                Log.e("ContactsAsyncHelper", "Unable to close input stream.", e3);
                            }
                            break;
                        }
                        throw th;
                    }
                    break;
            }
            Message reply = ContactsAsyncHelper.this.mResultHandler.obtainMessage(msg.what);
            reply.arg1 = msg.arg1;
            reply.obj = msg.obj;
            reply.sendToTarget();
        }

        private Bitmap getPhotoIconWhenAppropriate(Context context, Drawable photo) {
            if (!(photo instanceof BitmapDrawable)) {
                return null;
            }
            int iconSize = context.getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
            Bitmap orgBitmap = ((BitmapDrawable) photo).getBitmap();
            int orgWidth = orgBitmap.getWidth();
            int orgHeight = orgBitmap.getHeight();
            int longerEdge = orgWidth > orgHeight ? orgWidth : orgHeight;
            if (longerEdge > iconSize) {
                float ratio = longerEdge / iconSize;
                int newWidth = (int) (orgWidth / ratio);
                int newHeight = (int) (orgHeight / ratio);
                if (newWidth <= 0 || newHeight <= 0) {
                    Log.w("ContactsAsyncHelper", "Photo icon's width or height become 0.");
                    return null;
                }
                return Bitmap.createScaledBitmap(orgBitmap, newWidth, newHeight, true);
            }
            return orgBitmap;
        }
    }

    private ContactsAsyncHelper() {
        HandlerThread thread = new HandlerThread("ContactsAsyncWorker");
        thread.start();
        sThreadHandler = new WorkerHandler(thread.getLooper());
    }

    public static final void startObtainPhotoAsync(int token, Context context, Uri personUri, OnImageLoadCompleteListener listener, Object cookie) {
        if (personUri == null) {
            Log.wtf("ContactsAsyncHelper", "Uri is missing");
            return;
        }
        WorkerArgs args = new WorkerArgs();
        args.cookie = cookie;
        args.context = context;
        args.uri = personUri;
        args.listener = listener;
        Message msg = sThreadHandler.obtainMessage(token);
        msg.arg1 = 1;
        msg.obj = args;
        sThreadHandler.sendMessage(msg);
    }
}
