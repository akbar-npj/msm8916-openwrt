package com.android.phone.sip;

import android.content.Context;
import android.net.sip.SipProfile;
import android.util.Log;
import com.android.internal.os.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class SipProfileDb {
    private static final String TAG = SipProfileDb.class.getSimpleName();
    private int mProfilesCount = -1;
    private String mProfilesDirectory;
    private SipSharedPreferences mSipSharedPreferences;

    public SipProfileDb(Context context) {
        this.mProfilesDirectory = context.getFilesDir().getAbsolutePath() + "/profiles/";
        this.mSipSharedPreferences = new SipSharedPreferences(context);
    }

    public void deleteProfile(SipProfile sipProfile) {
        synchronized (SipProfileDb.class) {
            deleteProfile(new File(this.mProfilesDirectory + sipProfile.getProfileName()));
            if (this.mProfilesCount < 0) {
                retrieveSipProfileListInternal();
            }
            SipSharedPreferences sipSharedPreferences = this.mSipSharedPreferences;
            int i = this.mProfilesCount - 1;
            this.mProfilesCount = i;
            sipSharedPreferences.setProfilesCount(i);
        }
    }

    private void deleteProfile(File file) {
        if (file.isDirectory()) {
            File[] arr$ = file.listFiles();
            for (File child : arr$) {
                deleteProfile(child);
            }
        }
        file.delete();
    }

    /* JADX WARN: Code duplicated, block: B:24:0x0069 A[Catch: all -> 0x006d, TRY_ENTER, TryCatch #5 {, blocks: (B:4:0x0004, B:6:0x0008, B:7:0x000b, B:9:0x002d, B:10:0x0030, B:15:0x005b, B:16:0x005e, B:24:0x0069, B:25:0x006c), top: B:42:0x0004 }] */
    public void saveProfile(SipProfile sipProfile) throws IOException {
        ObjectOutputStream objectOutputStream;
        FileOutputStream fileOutputStream = null;
        synchronized (SipProfileDb.class) {
            if (this.mProfilesCount < 0) {
                retrieveSipProfileListInternal();
            }
            File file = new File(this.mProfilesDirectory + sipProfile.getProfileName());
            if (!file.exists()) {
                file.mkdirs();
            }
            AtomicFile atomicFile = new AtomicFile(new File(file, ".pobj"));
            try {
                try {
                    FileOutputStream fileOutputStreamStartWrite = atomicFile.startWrite();
                    try {
                        objectOutputStream = new ObjectOutputStream(fileOutputStreamStartWrite);
                        try {
                            try {
                                objectOutputStream.writeObject(sipProfile);
                                objectOutputStream.flush();
                                SipSharedPreferences sipSharedPreferences = this.mSipSharedPreferences;
                                int i = this.mProfilesCount + 1;
                                this.mProfilesCount = i;
                                sipSharedPreferences.setProfilesCount(i);
                                atomicFile.finishWrite(fileOutputStreamStartWrite);
                                if (objectOutputStream != null) {
                                    objectOutputStream.close();
                                }
                            } catch (IOException e) {
                                e = e;
                                fileOutputStream = fileOutputStreamStartWrite;
                                atomicFile.failWrite(fileOutputStream);
                                throw e;
                            }
                        } catch (Throwable th) {
                            th = th;
                            if (objectOutputStream != null) {
                                objectOutputStream.close();
                            }
                            throw th;
                        }
                    } catch (IOException e2) {
                        e = e2;
                        objectOutputStream = null;
                        fileOutputStream = fileOutputStreamStartWrite;
                    }
                } catch (Throwable th2) {
                    th = th2;
                    objectOutputStream = null;
                    if (objectOutputStream != null) {
                        objectOutputStream.close();
                    }
                    throw th;
                }
            } catch (IOException e3) {
                e = e3;
                objectOutputStream = null;
            }
        }
    }

    public int getProfilesCount() {
        return this.mProfilesCount < 0 ? this.mSipSharedPreferences.getProfilesCount() : this.mProfilesCount;
    }

    public List<SipProfile> retrieveSipProfileList() {
        List<SipProfile> listRetrieveSipProfileListInternal;
        synchronized (SipProfileDb.class) {
            listRetrieveSipProfileListInternal = retrieveSipProfileListInternal();
        }
        return listRetrieveSipProfileListInternal;
    }

    private List<SipProfile> retrieveSipProfileListInternal() throws Throwable {
        List<SipProfile> sipProfileList = Collections.synchronizedList(new ArrayList());
        File root = new File(this.mProfilesDirectory);
        String[] dirs = root.list();
        if (dirs != null) {
            for (String dir : dirs) {
                File f = new File(new File(root, dir), ".pobj");
                if (f.exists()) {
                    try {
                        SipProfile p = deserialize(f);
                        if (p != null && dir.equals(p.getProfileName())) {
                            sipProfileList.add(p);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "retrieveProfileListFromStorage()", e);
                    }
                }
            }
            this.mProfilesCount = sipProfileList.size();
            this.mSipSharedPreferences.setProfilesCount(this.mProfilesCount);
        }
        return sipProfileList;
    }

    private SipProfile deserialize(File profileObjectFile) throws Throwable {
        SipProfile p;
        AtomicFile atomicFile = new AtomicFile(profileObjectFile);
        ObjectInputStream ois = null;
        try {
            try {
                ObjectInputStream ois2 = new ObjectInputStream(atomicFile.openRead());
                try {
                    p = (SipProfile) ois2.readObject();
                    if (ois2 != null) {
                        ois2.close();
                    }
                    ois = ois2;
                } catch (ClassNotFoundException e) {
                    e = e;
                    ois = ois2;
                    Log.w(TAG, "deserialize a profile: " + e);
                    if (ois != null) {
                        ois.close();
                    }
                    p = null;
                } catch (Throwable th) {
                    th = th;
                    ois = ois2;
                    if (ois != null) {
                        ois.close();
                    }
                    throw th;
                }
            } catch (Throwable th2) {
                th = th2;
            }
        } catch (ClassNotFoundException e2) {
            e = e2;
        }
        return p;
    }
}
