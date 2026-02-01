package http.to.ems.messager;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Cached hostname to avoid repeated InetAddress.getLocalHost() calls (which can trigger DNS lookups).
 */
public final class HostnameCache {

    private static final String HOSTNAME = initHostname();

    private static String initHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    /** Cached hostname, resolved once at class load. */
    public static String getHostname() {
        return HOSTNAME;
    }

    private HostnameCache() { }
}
