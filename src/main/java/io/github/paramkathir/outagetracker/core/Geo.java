package io.github.paramkathir.outagetracker.core;

import java.util.Collection;

/**
 * Small, dependency-free geo helpers.
 */
public final class Geo {

    /** Mean Earth radius in kilometres. */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * Must be derived from the same Earth radius haversine uses; a rounded constant like
     * 111.32 makes the box slightly smaller than the circle and silently drops edge points.
     */
    private static final double KM_PER_DEGREE_LAT = Math.PI * EARTH_RADIUS_KM / 180.0;

    /** Small safety margin so floating-point error never excludes a point on the boundary. */
    private static final double BOX_MARGIN = 1.01;

    private Geo() {
    }

    /** Great-circle distance between two points using the haversine formula. */
    public static double distanceKm(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * A lat/lng box that fully contains the circle of {@code radiusKm} around {@code center}.
     * Used as a cheap, index-friendly SQL pre-filter before the exact haversine check.
     */
    public static BoundingBox boundingBox(GeoPoint center, double radiusKm) {
        double r = radiusKm * BOX_MARGIN;
        double dLat = r / KM_PER_DEGREE_LAT;
        // Use the latitude nearest the pole within the box: longitude degrees shrink there,
        // so this gives the widest (safe) longitude span.
        double extremeLat = Math.min(90, Math.abs(center.latitude()) + dLat);
        double cosLat = Math.cos(Math.toRadians(extremeLat));
        double dLng = cosLat < 1e-6 ? 180 : Math.min(180, r / (KM_PER_DEGREE_LAT * cosLat));
        return new BoundingBox(
                Math.max(-90, center.latitude() - dLat),
                Math.min(90, center.latitude() + dLat),
                Math.max(-180, center.longitude() - dLng),
                Math.min(180, center.longitude() + dLng));
    }

    /**
     * Arithmetic mean of the points. Accurate enough at metro scale (a few km), which is
     * the scale outage clusters live at; not intended for points spanning the antimeridian.
     */
    public static GeoPoint centroid(Collection<GeoPoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("cannot take centroid of zero points");
        }
        double lat = 0;
        double lng = 0;
        for (GeoPoint p : points) {
            lat += p.latitude();
            lng += p.longitude();
        }
        return new GeoPoint(lat / points.size(), lng / points.size());
    }

    /** Incrementally folds one more point into a running-mean centroid of {@code count} points. */
    public static GeoPoint addToCentroid(GeoPoint centroid, int count, GeoPoint added) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        int n = count + 1;
        return new GeoPoint(
                centroid.latitude() + (added.latitude() - centroid.latitude()) / n,
                centroid.longitude() + (added.longitude() - centroid.longitude()) / n);
    }

    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {

        public boolean contains(GeoPoint p) {
            return p.latitude() >= minLat && p.latitude() <= maxLat
                    && p.longitude() >= minLng && p.longitude() <= maxLng;
        }
    }
}
