package az.aqroinvest.listing;

import az.aqroinvest.domain.EconomicRegion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Districts of the liberated territories: economic region, plot code prefix and a rough bounding box
 * [minLng, minLat, maxLng, maxLat] padded by ~0.1° (only for the "outside district" warning and the map jump, never to reject).
 * shortcut: hand-made boxes, replace with official district polygons when available.
 */
public final class Districts {
    private Districts() {}

    public record District(String name, EconomicRegion region, String prefix, double[] box) {}

    public static final List<District> ALL = List.of(
            new District("Ağdam", EconomicRegion.QARABAG, "AGD", new double[]{46.45, 39.75, 47.35, 40.30}),
            new District("Cəbrayıl", EconomicRegion.QARABAG, "CBR", new double[]{46.65, 39.10, 47.40, 39.65}),
            new District("Füzuli", EconomicRegion.QARABAG, "FZL", new double[]{46.75, 39.30, 47.55, 39.90}),
            new District("Kəlbəcər", EconomicRegion.SHERQI_ZANGEZUR, "KLB", new double[]{45.45, 39.70, 46.65, 40.55}),
            new District("Laçın", EconomicRegion.SHERQI_ZANGEZUR, "LCN", new double[]{45.85, 39.20, 46.85, 40.05}),
            new District("Qubadlı", EconomicRegion.SHERQI_ZANGEZUR, "QBD", new double[]{46.25, 39.10, 46.95, 39.60}),
            new District("Tərtər", EconomicRegion.QARABAG, "TRT", new double[]{46.30, 40.05, 47.30, 40.65}),
            new District("Xocalı", EconomicRegion.QARABAG, "XCL", new double[]{46.45, 39.70, 47.05, 40.20}),
            new District("Xocavənd", EconomicRegion.QARABAG, "XCV", new double[]{46.55, 39.40, 47.20, 40.00}),
            new District("Zəngilan", EconomicRegion.SHERQI_ZANGEZUR, "ZNG", new double[]{46.30, 38.85, 46.95, 39.40}),
            new District("Şuşa", EconomicRegion.QARABAG, "SHS", new double[]{46.55, 39.58, 46.95, 39.92}));

    public static District get(String name) {
        return name == null ? null : ALL.stream().filter(d -> d.name().equals(name)).findFirst().orElse(null);
    }

    /** name → box, for the drawing map (JSON in the page). */
    public static Map<String, double[]> boxes() {
        Map<String, double[]> m = new LinkedHashMap<>();
        ALL.forEach(d -> m.put(d.name(), d.box()));
        return m;
    }

    /** name → region, for the region → district select filter. */
    public static Map<String, EconomicRegion> regions() {
        Map<String, EconomicRegion> m = new LinkedHashMap<>();
        ALL.forEach(d -> m.put(d.name(), d.region()));
        return m;
    }

    /** null when the district is unknown (→ no warning). */
    static Boolean contains(String district, double lng, double lat) {
        District d = get(district);
        if (d == null) return null;
        double[] b = d.box();
        return lng >= b[0] && lng <= b[2] && lat >= b[1] && lat <= b[3];
    }
}
