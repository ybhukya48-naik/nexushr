package com.zidio.nexushr.web;

import java.util.Locale;

/**
 * Office check-in and check-out are for a Windows or Mac computer.
 * A phone browser is refused. Site selfie punches are a separate path.
 */
public final class WindowsAttendanceClient {

    private WindowsAttendanceClient() {
    }

    public static boolean allowed(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("android")
                || ua.contains("iphone")
                || ua.contains("ipad")
                || ua.contains("ipod")
                || ua.contains("mobile")
                || ua.contains("windows phone")) {
            return false;
        }
        return ua.contains("windows nt")
                || ua.contains("macintosh")
                || ua.contains("mac os x");
    }
}
