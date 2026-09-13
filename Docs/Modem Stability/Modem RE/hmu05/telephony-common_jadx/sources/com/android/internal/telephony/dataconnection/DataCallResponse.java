package com.android.internal.telephony.dataconnection;

import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.os.SystemProperties;
import android.telephony.Rlog;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/* JADX INFO: loaded from: classes.dex */
public class DataCallResponse {
    private final boolean DBG = true;
    private final String LOG_TAG = "DataCallResponse";
    public int version = 0;
    public int status = 0;
    public int cid = 0;
    public int active = 0;
    public String type = "";
    public String ifname = "";
    public String[] addresses = new String[0];
    public String[] dnses = new String[0];
    public String[] gateways = new String[0];
    public int suggestedRetryTime = -1;

    public enum SetupResult {
        SUCCESS,
        ERR_BadCommand,
        ERR_UnacceptableParameter,
        ERR_GetLastErrorFromRil,
        ERR_Stale,
        ERR_RilError;

        public DcFailCause mFailCause = DcFailCause.fromInt(0);

        SetupResult() {
        }

        @Override // java.lang.Enum
        public String toString() {
            return name() + "  SetupResult.mFailCause=" + this.mFailCause;
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("DataCallResponse: {").append("version=").append(this.version).append(" status=").append(this.status).append(" retry=").append(this.suggestedRetryTime).append(" cid=").append(this.cid).append(" active=").append(this.active).append(" type=").append(this.type).append("' ifname='").append(this.ifname);
        sb.append("' addresses=[");
        String[] arr$ = this.addresses;
        for (String addr : arr$) {
            sb.append(addr);
            sb.append(",");
        }
        if (this.addresses.length > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("] dnses=[");
        String[] arr$2 = this.dnses;
        for (String addr2 : arr$2) {
            sb.append(addr2);
            sb.append(",");
        }
        if (this.dnses.length > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("] gateways=[");
        String[] arr$3 = this.gateways;
        for (String addr3 : arr$3) {
            sb.append(addr3);
            sb.append(",");
        }
        if (this.gateways.length > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]}");
        return sb.toString();
    }

    public SetupResult setLinkProperties(LinkProperties linkProperties, boolean okToUseSystemPropertyDns) {
        SetupResult result;
        int addrPrefixLen;
        if (linkProperties == null) {
            linkProperties = new LinkProperties();
        } else {
            linkProperties.clear();
        }
        if (this.status == DcFailCause.NONE.getErrorCode()) {
            String propertyPrefix = "net." + this.ifname + ".";
            try {
                linkProperties.setInterfaceName(this.ifname);
                if (this.addresses != null && this.addresses.length > 0) {
                    for (String str : this.addresses) {
                        String addr = str.trim();
                        if (!addr.isEmpty()) {
                            String[] ap = addr.split("/");
                            if (ap.length == 2) {
                                addr = ap[0];
                                addrPrefixLen = Integer.parseInt(ap[1]);
                            } else {
                                addrPrefixLen = 0;
                            }
                            try {
                                InetAddress ia = NetworkUtils.numericToInetAddress(addr);
                                if (!ia.isAnyLocalAddress()) {
                                    if (addrPrefixLen == 0) {
                                        addrPrefixLen = ia instanceof Inet4Address ? 32 : 128;
                                    }
                                    Rlog.d("DataCallResponse", "addr/pl=" + addr + "/" + addrPrefixLen);
                                    LinkAddress la = new LinkAddress(ia, addrPrefixLen);
                                    linkProperties.addLinkAddress(la);
                                }
                            } catch (IllegalArgumentException e) {
                                throw new UnknownHostException("Non-numeric ip addr=" + addr);
                            }
                        }
                    }
                    if (this.dnses != null && this.dnses.length > 0) {
                        for (String str2 : this.dnses) {
                            String addr2 = str2.trim();
                            if (!addr2.isEmpty()) {
                                try {
                                    InetAddress ia2 = NetworkUtils.numericToInetAddress(addr2);
                                    if (!ia2.isAnyLocalAddress()) {
                                        linkProperties.addDns(ia2);
                                    }
                                } catch (IllegalArgumentException e2) {
                                    throw new UnknownHostException("Non-numeric dns addr=" + addr2);
                                }
                            }
                        }
                    } else if (okToUseSystemPropertyDns) {
                        String[] dnsServers = {SystemProperties.get(propertyPrefix + "dns1"), SystemProperties.get(propertyPrefix + "dns2")};
                        for (String str3 : dnsServers) {
                            String dnsAddr = str3.trim();
                            if (!dnsAddr.isEmpty()) {
                                try {
                                    InetAddress ia3 = NetworkUtils.numericToInetAddress(dnsAddr);
                                    if (!ia3.isAnyLocalAddress()) {
                                        linkProperties.addDns(ia3);
                                    }
                                } catch (IllegalArgumentException e3) {
                                    throw new UnknownHostException("Non-numeric dns addr=" + dnsAddr);
                                }
                            }
                        }
                    } else {
                        throw new UnknownHostException("Empty dns response and no system default dns");
                    }
                    if (this.gateways == null || this.gateways.length == 0) {
                        String sysGateways = SystemProperties.get(propertyPrefix + "gw");
                        if (sysGateways != null) {
                            this.gateways = sysGateways.split(" ");
                        } else {
                            this.gateways = new String[0];
                        }
                    }
                    String[] arr$ = this.gateways;
                    for (String str4 : arr$) {
                        String addr3 = str4.trim();
                        if (!addr3.isEmpty()) {
                            try {
                                linkProperties.addRoute(new RouteInfo(NetworkUtils.numericToInetAddress(addr3)));
                            } catch (IllegalArgumentException e4) {
                                throw new UnknownHostException("Non-numeric gateway addr=" + addr3);
                            }
                        }
                    }
                    result = SetupResult.SUCCESS;
                } else {
                    throw new UnknownHostException("no address for ifname=" + this.ifname);
                }
            } catch (UnknownHostException e5) {
                Rlog.d("DataCallResponse", "setLinkProperties: UnknownHostException " + e5);
                e5.printStackTrace();
                result = SetupResult.ERR_UnacceptableParameter;
            }
        } else if (this.version < 4) {
            result = SetupResult.ERR_GetLastErrorFromRil;
        } else {
            result = SetupResult.ERR_RilError;
        }
        if (result != SetupResult.SUCCESS) {
            Rlog.d("DataCallResponse", "setLinkProperties: error clearing LinkProperties status=" + this.status + " result=" + result);
            linkProperties.clear();
        }
        return result;
    }
}
