package io.github.paramkathir.outagetracker.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BucketsTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private static Instant minutesAgo(long m) {
        return NOW.minus(Duration.ofMinutes(m));
    }

    @Test
    void countsIntoOldestToNewestBuckets() {
        List<Instant> times = List.of(minutesAgo(5), minutesAgo(9), minutesAgo(25), minutesAgo(59));
        int[] b = Buckets.count(times, NOW, Duration.ofMinutes(10), 6);
        assertEquals(1, b[0]); // 50-60 min ago
        assertEquals(0, b[1]);
        assertEquals(0, b[2]);
        assertEquals(1, b[3]); // 20-30 min ago
        assertEquals(0, b[4]);
        assertEquals(2, b[5]); // last 10 min
    }

    @Test
    void edgesAreHalfOpenAtTheOldEndAndInclusiveAtNow() {
        List<Instant> times = List.of(NOW, minutesAgo(60), minutesAgo(10));
        int[] b = Buckets.count(times, NOW, Duration.ofMinutes(10), 6);
        // Each bucket covers (start, end]: "now" closes the latest bucket, and a report exactly
        // 10 min ago closes the one before it.
        assertEquals(1, b[5]);
        assertEquals(1, b[4]);
        assertEquals(0, b[0], "exactly 60 min ago is outside the window");
    }

    @Test
    void ignoresTimesOutsideTheWindow() {
        List<Instant> times = List.of(NOW.plusSeconds(1), minutesAgo(61), minutesAgo(1000));
        int[] b = Buckets.count(times, NOW, Duration.ofMinutes(10), 6);
        int total = 0;
        for (int n : b) {
            total += n;
        }
        assertEquals(0, total);
    }

    @Test
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> Buckets.count(List.of(), NOW, Duration.ofMinutes(1), 0));
        assertThrows(IllegalArgumentException.class, () -> Buckets.count(List.of(), NOW, Duration.ZERO, 3));
    }
}
