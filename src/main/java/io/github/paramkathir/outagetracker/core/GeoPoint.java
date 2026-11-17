package io.github.paramkathir.outagetracker.core;

/**
 * An immutable WGS-84 coordinate.
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude);
    }
}
