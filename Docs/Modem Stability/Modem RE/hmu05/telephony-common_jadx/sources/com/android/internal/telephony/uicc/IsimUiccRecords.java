package com.android.internal.telephony.uicc;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.gsm.SimTlv;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

/* JADX INFO: loaded from: classes.dex */
public final class IsimUiccRecords extends IccRecords implements IsimRecords {
    private static final boolean DBG = true;
    private static final boolean DUMP_RECORDS = false;
    private static final int EVENT_APP_READY = 1;
    protected static final String LOG_TAG = "IsimUiccRecords";
    private static final int TAG_ISIM_VALUE = 128;
    private String mIsimDomain;
    private String mIsimImpi;
    private String[] mIsimImpu;

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public String toString() {
        return "IsimUiccRecords: " + super.toString() + " mIsimImpi=" + this.mIsimImpi + " mIsimDomain=" + this.mIsimDomain + " mIsimImpu=" + this.mIsimImpu;
    }

    public IsimUiccRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);
        this.mAdnCache = new AdnRecordCache(this.mFh);
        this.mRecordsRequested = false;
        this.mRecordsToLoad = 0;
        this.mParentApp.registerForReady(this, 1, null);
        log("IsimUiccRecords X ctor this=" + this);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dispose() {
        log("Disposing " + this);
        this.mParentApp.unregisterForReady(this);
        resetRecords();
        super.dispose();
    }

    @Override // com.android.internal.telephony.uicc.IccRecords, android.os.Handler
    public void handleMessage(Message msg) {
        if (this.mDestroyed.get()) {
            Rlog.e(LOG_TAG, "Received message " + msg + "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        try {
            switch (msg.what) {
                case 1:
                    onReady();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        } catch (RuntimeException exc) {
            Rlog.w(LOG_TAG, "Exception parsing SIM record", exc);
        }
    }

    protected void fetchIsimRecords() {
        this.mRecordsRequested = true;
        this.mFh.loadEFTransparent(IccConstants.EF_IMPI, obtainMessage(100, new EfIsimImpiLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFLinearFixedAll(IccConstants.EF_IMPU, obtainMessage(100, new EfIsimImpuLoaded()));
        this.mRecordsToLoad++;
        this.mFh.loadEFTransparent(IccConstants.EF_DOMAIN, obtainMessage(100, new EfIsimDomainLoaded()));
        this.mRecordsToLoad++;
        log("fetchIsimRecords " + this.mRecordsToLoad);
    }

    protected void resetRecords() {
        this.mRecordsRequested = false;
    }

    private class EfIsimImpiLoaded implements IccRecords.IccRecordLoaded {
        private EfIsimImpiLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_ISIM_IMPI";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            IsimUiccRecords.this.mIsimImpi = IsimUiccRecords.isimTlvToString(data);
        }
    }

    private class EfIsimImpuLoaded implements IccRecords.IccRecordLoaded {
        private EfIsimImpuLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_ISIM_IMPU";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            ArrayList<byte[]> impuList = (ArrayList) ar.result;
            IsimUiccRecords.this.log("EF_IMPU record count: " + impuList.size());
            IsimUiccRecords.this.mIsimImpu = new String[impuList.size()];
            int i = 0;
            for (byte[] identity : impuList) {
                String impu = IsimUiccRecords.isimTlvToString(identity);
                IsimUiccRecords.this.mIsimImpu[i] = impu;
                i++;
            }
        }
    }

    private class EfIsimDomainLoaded implements IccRecords.IccRecordLoaded {
        private EfIsimDomainLoaded() {
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public String getEfName() {
            return "EF_ISIM_DOMAIN";
        }

        @Override // com.android.internal.telephony.uicc.IccRecords.IccRecordLoaded
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            IsimUiccRecords.this.mIsimDomain = IsimUiccRecords.isimTlvToString(data);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String isimTlvToString(byte[] record) {
        SimTlv tlv = new SimTlv(record, 0, record.length);
        while (tlv.getTag() != 128) {
            if (!tlv.nextObject()) {
                Rlog.e(LOG_TAG, "[ISIM] can't find TLV tag in ISIM record, returning null");
                return null;
            }
        }
        return new String(tlv.getData(), Charset.forName("UTF-8"));
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void onRecordLoaded() {
        this.mRecordsToLoad--;
        if (this.mRecordsToLoad == 0 && this.mRecordsRequested) {
            onAllRecordsLoaded();
        } else if (this.mRecordsToLoad < 0) {
            loge("recordsToLoad <0, programmer error suspected");
            this.mRecordsToLoad = 0;
        }
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void onAllRecordsLoaded() {
        this.mRecordsLoadedRegistrants.notifyRegistrants(new AsyncResult((Object) null, (Object) null, (Throwable) null));
    }

    @Override // com.android.internal.telephony.uicc.IsimRecords
    public String getIsimImpi() {
        return this.mIsimImpi;
    }

    @Override // com.android.internal.telephony.uicc.IsimRecords
    public String getIsimDomain() {
        return this.mIsimDomain;
    }

    @Override // com.android.internal.telephony.uicc.IsimRecords
    public String[] getIsimImpu() {
        if (this.mIsimImpu != null) {
            return (String[]) this.mIsimImpu.clone();
        }
        return null;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getDisplayRule(String plmn) {
        return 0;
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onReady() {
        fetchIsimRecords();
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void handleFileUpdate(int efid) {
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void onRefresh(boolean fileChanged, int[] fileList) {
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete) {
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void setVoiceMessageWaiting(int line, int countWaiting) {
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[ISIM] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    protected void loge(String s) {
        Rlog.e(LOG_TAG, "[ISIM] " + s);
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IsimRecords: " + this);
        pw.println(" extends:");
        super.dump(fd, pw, args);
        pw.println(" mIsimImpi=" + this.mIsimImpi);
        pw.println(" mIsimDomain=" + this.mIsimDomain);
        pw.println(" mIsimImpu[]=" + Arrays.toString(this.mIsimImpu));
        pw.flush();
    }

    @Override // com.android.internal.telephony.uicc.IccRecords
    public int getVoiceMessageCount() {
        return 0;
    }
}
