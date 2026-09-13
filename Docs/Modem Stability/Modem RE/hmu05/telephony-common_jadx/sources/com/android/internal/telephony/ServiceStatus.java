package com.android.internal.telephony;

/* JADX INFO: loaded from: classes.dex */
public class ServiceStatus {
    public StatusForAccessTech[] accessTechStatus;
    public boolean isValid = false;
    public int status;
    public int type;
    public byte[] userdata;

    public static class StatusForAccessTech {
        public int networkMode;
        public int registered;
        public int restrictCause;
        public int status;

        public String toString() {
            return " mode = " + this.networkMode + " Status = " + this.status + " restrictCause = " + this.restrictCause + " registered = " + this.registered;
        }
    }
}
