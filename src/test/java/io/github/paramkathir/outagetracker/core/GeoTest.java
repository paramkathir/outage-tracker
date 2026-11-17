package io.github.paramkathir.outagetracker.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoTest {

    private static final GeoPoint DALLAS = GeoPoint.of(32.7767, -96.7970);
    private static final GeoPoint FORT_WORTH = GeoPoint.of(32.7555, -97.3308);

    @Test
    void distanceBetweenDallasAndFortWorthIsAbout50Km() {
        double d = Geo.distanceKm(DALLAS, FORT_WORTH);
        assertEquals(49.9, d, 0.5);
    }

    @Test
    void distanceIsSymmetricAndZeroForSamePoint() {
        assertEquals(Geo.distanceKm(DALLAS, FORT_WORTH), Geo.distanceKm(FORT_WORTH, DALLAS), 1e-9);
        assertEquals(0.0, Geo.distanceKm(DALLAS, DALLAS), 1e-9);
    }

    @Test
    void boundingBoxContainsEveryPointOnTheCircle() {
        double radius = 3.0;
        Geo.BoundingBox box = Geo.boundingBox(DALLAS, radius);
        // Walk the compass at exactly `radius` km and check each point is inside the box.
        for (int bearing = 0; bearing < 360; bearing += 15) {
            GeoPoint p = destination(DALLAS, bearing, radius * 0.999);
            assertTrue(box.contains(p), "bearing " + bearing + " escaped the box");
        }
        assertFalse(box.contains(FORT_WORTH));
    }

    @Test
    void incrementalCentroidMatchesBatchCentroid() {
        List<GeoPoint> points = List.of(
                GeoPoint.of(33.00, -96.70), GeoPoint.of(33.01, -96.69),
                GeoPoint.of(33.02, -96.71), GeoPoint.of(32.99, -96.72));
        GeoPoint running = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            running = Geo.addToCentroid(running, i, points.get(i));
        }
        GeoPoint batch = Geo.centroid(points);
        assertEquals(batch.latitude(), running.latitude(), 1e-12);
        assertEquals(batch.longitude(), running.longitude(), 1e-12);
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(91, 0));
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(0, -181));
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(Double.NaN, 0));
    }

    /** Point at a given bearing and distance (spherical model), used to probe the bounding box. */
    private static GeoPoint destination(GeoPoint from, double bearingDeg, double km) {
        double delta = km / Geo.EARTH_RADIUS_KM;
        double theta = Math.toRadians(bearingDeg);
        double lat1 = Math.toRadians(from.latitude());
        double lng1 = Math.toRadians(from.longitude());
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(delta)
                + Math.cos(lat1) * Math.sin(delta) * Math.cos(theta));
        double lng2 = lng1 + Math.atan2(Math.sin(theta) * Math.sin(delta) * Math.cos(lat1),
                Math.cos(delta) - Math.sin(lat1) * Math.sin(lat2));
        return GeoPoint.of(Math.toDegrees(lat2), Math.toDegrees(lng2));
    }
}
