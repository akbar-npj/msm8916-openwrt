package com.android.phone.common;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.provider.CallLog;
import android.util.Log;
import com.android.internal.telephony.CallerInfo;

/* JADX INFO: loaded from: classes.dex */
public class CallLogAsync {

    public static class AddCallArgs {
        public final int callType;
        public final CallerInfo ci;
        public final Context context;
        public final int durationInSec;
        public final int durationType;
        public final String number;
        public final int presentation;
        public final int subscription;
        public final long timestamp;
        public final String videocallduration;

        public AddCallArgs(Context context, CallerInfo ci, String number, int presentation, int callType, long timestamp, long durationInMillis, int subscription, int durationType, String videocallduration) {
            this.context = context;
            this.ci = ci;
            this.number = number;
            this.presentation = presentation;
            this.callType = callType;
            this.timestamp = timestamp;
            this.durationInSec = (int) (durationInMillis / 1000);
            this.durationType = durationType;
            this.subscription = subscription;
            this.videocallduration = videocallduration;
        }
    }

    public AsyncTask addCall(AddCallArgs args) {
        assertUiThread();
        return new AddCallTask().execute(args);
    }

    private class AddCallTask extends AsyncTask<AddCallArgs, Void, Uri[]> {
        private AddCallTask() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Uri[] doInBackground(AddCallArgs... callList) {
            int count = callList.length;
            Uri[] result = new Uri[count];
            for (int i = 0; i < count; i++) {
                AddCallArgs c = callList[i];
                try {
                    if (c.videocallduration != null) {
                        Log.d("CallLogAsync", "add calllog with video call duration");
                        result[i] = CallLog.Calls.addCall(c.ci, c.context, c.number, c.presentation, c.callType, c.timestamp, c.durationInSec, c.subscription, c.durationType, c.videocallduration);
                    } else {
                        result[i] = CallLog.Calls.addCall(c.ci, c.context, c.number, c.presentation, c.callType, c.timestamp, c.durationInSec, c.subscription, c.durationType);
                    }
                } catch (Exception e) {
                    Log.e("CallLogAsync", "Exception raised during adding CallLog entry: " + e);
                    result[i] = null;
                }
            }
            return result;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(Uri[] result) {
            for (Uri uri : result) {
                if (uri == null) {
                    Log.e("CallLogAsync", "Failed to write call to the log.");
                }
            }
        }
    }

    private void assertUiThread() {
        if (!Looper.getMainLooper().equals(Looper.myLooper())) {
            throw new RuntimeException("Not on the UI thread!");
        }
    }
}
