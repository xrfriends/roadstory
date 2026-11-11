package com.roadstory.common.geo;

public record GeoPoint(double latitude, double longitude) {
    public static final double EARTH_RADIUS_METERS = 6_371_008.8;

    public GeoPoint {
        if (!isValid(latitude, longitude)) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
    }

    public static boolean isValid(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
            && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
    }

    public double distanceTo(GeoPoint other) {
        double deltaLatitude = Math.toRadians(other.latitude - latitude);
        double deltaLongitude = Math.toRadians(other.longitude - longitude);
        double haversine = square(Math.sin(deltaLatitude / 2))
            + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
            * square(Math.sin(deltaLongitude / 2));
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.max(0, Math.min(1, haversine))));
    }

    public double bearingTo(GeoPoint other) {
        double from = Math.toRadians(latitude);
        double to = Math.toRadians(other.latitude);
        double deltaLongitude = Math.toRadians(other.longitude - longitude);
        double east = Math.sin(deltaLongitude) * Math.cos(to);
        double north = Math.cos(from) * Math.sin(to) - Math.sin(from) * Math.cos(to) * Math.cos(deltaLongitude);
        return (Math.toDegrees(Math.atan2(east, north)) + 360) % 360;
    }

    private static double square(double value) {
        return value * value;
    }
}
