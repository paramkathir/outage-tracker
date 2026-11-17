package io.github.paramkathir.outagetracker.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/** Time-bucketing for activity charts. */
public final class Buckets {

    private Buckets() {
    }

    /**
     * Counts timestamps into {@code count} equal buckets of {@code width} ending at {@code end}
     * (inclusive). Index 0 is the oldest bucket, the last index the most recent. Timestamps
     * outside the window are ignored.
     */
    public static int[] count(Collection<Instant> times, Instant end, Duration width, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        if (width.isZero() || width.isNegative()) {
            throw new IllegalArgumentException("width must be positive");
        }
        int[] buckets = new int[count];
        long widthMs = width.toMillis();
        long endMs = end.toEpochMilli();
        long startMs = endMs - widthMs * count;
        for (Instant t : times) {
            long ms = t.toEpochMilli();
            if (ms <= startMs || ms > endMs) {
                continue;
            }
            int index = (int) ((ms - startMs - 1) / widthMs);
            buckets[Math.min(index, count - 1)]++;
        }
        return buckets;
    }
}
