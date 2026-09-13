package com.android.phone;

import android.content.Intent;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.Connection;
import java.util.concurrent.ConcurrentHashMap;

/* JADX INFO: loaded from: classes.dex */
public class CallGatewayManager {
    private static CallGatewayManager sSingleton;
    private final ConcurrentHashMap<Connection, RawGatewayInfo> mMap = new ConcurrentHashMap<>(4, 0.9f, 1);
    private static final String LOG_TAG = CallGatewayManager.class.getSimpleName();
    public static final RawGatewayInfo EMPTY_INFO = new RawGatewayInfo(null, null, null);

    public static synchronized CallGatewayManager getInstance() {
        if (sSingleton == null) {
            sSingleton = new CallGatewayManager();
        }
        return sSingleton;
    }

    private CallGatewayManager() {
    }

    public static RawGatewayInfo getRawGatewayInfo(Intent intent, String number) {
        return hasPhoneProviderExtras(intent) ? new RawGatewayInfo(intent.getStringExtra("com.android.phone.extra.GATEWAY_PROVIDER_PACKAGE"), getProviderGatewayUri(intent), number) : EMPTY_INFO;
    }

    public void setGatewayInfoForConnection(Connection connection, RawGatewayInfo gatewayInfo) {
        if (!gatewayInfo.isEmpty()) {
            this.mMap.put(connection, gatewayInfo);
        } else {
            this.mMap.remove(connection);
        }
    }

    public void clearGatewayData(Connection connection) {
        setGatewayInfoForConnection(connection, EMPTY_INFO);
    }

    public RawGatewayInfo getGatewayInfo(Connection connection) {
        RawGatewayInfo info = this.mMap.get(connection);
        return info != null ? info : EMPTY_INFO;
    }

    public static boolean hasPhoneProviderExtras(Intent intent) {
        if (intent == null) {
            return false;
        }
        String name = intent.getStringExtra("com.android.phone.extra.GATEWAY_PROVIDER_PACKAGE");
        String gatewayUri = intent.getStringExtra("com.android.phone.extra.GATEWAY_URI");
        return (TextUtils.isEmpty(name) || TextUtils.isEmpty(gatewayUri)) ? false : true;
    }

    public static void checkAndCopyPhoneProviderExtras(Intent src, Intent dst) {
        if (!hasPhoneProviderExtras(src)) {
            Log.d(LOG_TAG, "checkAndCopyPhoneProviderExtras: some or all extras are missing.");
        } else {
            dst.putExtra("com.android.phone.extra.GATEWAY_PROVIDER_PACKAGE", src.getStringExtra("com.android.phone.extra.GATEWAY_PROVIDER_PACKAGE"));
            dst.putExtra("com.android.phone.extra.GATEWAY_URI", src.getStringExtra("com.android.phone.extra.GATEWAY_URI"));
        }
    }

    public static Uri getProviderGatewayUri(Intent intent) {
        String uri = intent.getStringExtra("com.android.phone.extra.GATEWAY_URI");
        if (TextUtils.isEmpty(uri)) {
            return null;
        }
        return Uri.parse(uri);
    }

    public static String formatProviderUri(Uri uri) {
        if (uri != null) {
            if ("tel".equals(uri.getScheme())) {
                return PhoneNumberUtils.formatNumber(uri.getSchemeSpecificPart());
            }
            return uri.toString();
        }
        return null;
    }

    public static class RawGatewayInfo {
        public Uri gatewayUri;
        public String packageName;
        public String trueNumber;

        public RawGatewayInfo(String packageName, Uri gatewayUri, String trueNumber) {
            this.packageName = packageName;
            this.gatewayUri = gatewayUri;
            this.trueNumber = trueNumber;
        }

        public String getFormattedGatewayNumber() {
            return CallGatewayManager.formatProviderUri(this.gatewayUri);
        }

        public boolean isEmpty() {
            return TextUtils.isEmpty(this.packageName) || this.gatewayUri == null;
        }
    }
}
