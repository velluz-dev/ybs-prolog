package com.ybs.demo.shared;

import java.util.Map;

public final class GeoUtils {
    private GeoUtils() {}

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    public static double distKm(double lat1, double lon1, double lat2, double lon2) {
        return haversineKm(lat1, lon1, lat2, lon2);
    }

    public static double trafficFactor(String bucket) {
        Map<String, Double> factors = Map.of(
                "morning_peak", 1.50,
                "midday", 1.10,
                "evening_peak", 1.40,
                "night", 0.95
        );
        return factors.getOrDefault(bucket, 1.0);
    }
}
