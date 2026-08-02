package com.berk.courier_tracking_api.util;

public final class HaversineUtility {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private HaversineUtility() {
    }

    /**
     * İki coğrafi koordinat arasındaki kuş uçuşu mesafeyi kilometre cinsinden hesaplar.
     */
    public static double calculateDistanceKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double haversine = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));

        return EARTH_RADIUS_KM * centralAngle;
    }
}
