package org.sleuthkit.autopsy.enhancedgallery.datamodel;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight in-memory cache mapping AbstractFile obj_id → GPS coordinates.
 * Populated by MediaFileLoader when it reads EXIF data via Autopsy's
 * BlackboardArtifact (TSK_GPS_TAKENFROM / TSK_GEO_TRACKPOINT).
 * Thread-safe for reads after initial load.
 */
public class GpsCache {

    public static final class GpsPoint {
        public final double lat;
        public final double lng;
        public final String label;   // reverse-geocoded place name, or null

        public GpsPoint(double lat, double lng, String label) {
            this.lat   = lat;
            this.lng   = lng;
            this.label = label;
        }

        public String mapsUrl() {
            return "https://www.google.com/maps?q=" + lat + "," + lng;
        }

        @Override
        public String toString() {
            return String.format("%.5f, %.5f%s", lat, lng,
                    label != null ? " (" + label + ")" : "");
        }
    }

    private final Map<Long, GpsPoint> cache = new HashMap<>();

    public void put(long objId, double lat, double lng, String label) {
        cache.put(objId, new GpsPoint(lat, lng, label));
    }

    public boolean hasGps(long objId)      { return cache.containsKey(objId); }
    public GpsPoint getGps(long objId)     { return cache.get(objId); }
    public int      size()                 { return cache.size(); }
    public void     clear()                { cache.clear(); }
}
