package com.zidio.nexushr.service;

/**
 * Office check-in is the shared CYOND and Gorle office at Mayfair Garden,
 * Road No. 12, Banjara Hills, Hyderabad. Work from home is allowed only
 * after HR approves it, and that approval is checked before this fence.
 */
public final class OfficeFence {

    public static final double OFFICE_LATITUDE = 17.408507;
    public static final double OFFICE_LONGITUDE = 78.43903;
    /** Wide enough for a laptop location fix inside the building. */
    public static final double RADIUS_METERS = 500;

    private OfficeFence() {
    }

    public static boolean inside(Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            return false;
        }
        return metersBetween(OFFICE_LATITUDE, OFFICE_LONGITUDE, latitude, longitude) <= RADIUS_METERS;
    }

    static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double earth = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
