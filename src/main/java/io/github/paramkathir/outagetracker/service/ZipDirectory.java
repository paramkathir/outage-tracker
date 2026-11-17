package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.core.GeoPoint;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Small built-in ZIP → coordinate lookup loaded from {@code zips.csv}. Stands in for a real
 * geocoding API so the demo works offline.
 */
@Component
public class ZipDirectory {

    public record ZipEntry(String zip, String place, GeoPoint location) {
    }

    private final Map<String, ZipEntry> byZip;

    public ZipDirectory() {
        this.byZip = Collections.unmodifiableMap(load("/zips.csv"));
    }

    public Optional<ZipEntry> find(String zip) {
        return zip == null ? Optional.empty() : Optional.ofNullable(byZip.get(zip.strip()));
    }

    public List<ZipEntry> all() {
        return List.copyOf(byZip.values());
    }

    private static Map<String, ZipEntry> load(String resource) {
        Map<String, ZipEntry> entries = new LinkedHashMap<>();
        try (InputStream in = ZipDirectory.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                List<String> cols = splitCsv(line);
                String zip = cols.get(0);
                entries.put(zip, new ZipEntry(zip, cols.get(1),
                        GeoPoint.of(Double.parseDouble(cols.get(2)), Double.parseDouble(cols.get(3)))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    /** Minimal CSV split supporting double-quoted fields (place names contain commas). */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(cur.toString().strip());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().strip());
        return out;
    }
}
