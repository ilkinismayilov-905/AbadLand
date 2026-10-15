package az.aqroinvest.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Staff-drawn plot polygon: parse + check (GeoJSON Polygon or Feature, inside Azerbaijan), centre and area. */
public final class Geo {

    /** lng/lat ring (closed), centre and area in hectares. */
    public record Shape(String geoJson, double lng, double lat, double areaHa) {}

    /** Larger drawn polygons are rejected (typo / zoomed-out drawing). */
    public static final double MAX_HA = 5000;

    private Geo() {}

    /** @throws IllegalArgumentException with a message key when the polygon is unusable */
    public static Shape parse(String text, ObjectMapper json) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("listing.geo.invalid");
        }
        if (root == null) throw new IllegalArgumentException("listing.geo.invalid");
        if ("Feature".equals(root.path("type").asText())) root = root.path("geometry");
        if (!"Polygon".equals(root.path("type").asText())) throw new IllegalArgumentException("listing.geo.notPolygon");
        List<double[]> pts = new ArrayList<>();
        for (JsonNode pt : root.path("coordinates").path(0)) {
            if (!pt.path(0).isNumber() || !pt.path(1).isNumber()) throw new IllegalArgumentException("listing.geo.invalid");
            pts.add(new double[]{pt.path(0).asDouble(), pt.path(1).asDouble()});
        }
        if (!pts.isEmpty() && same(pts.get(0), pts.get(pts.size() - 1))) pts.remove(pts.size() - 1);
        if (pts.size() < 3) throw new IllegalArgumentException("listing.geo.tooFewPoints");
        // Azerbaijan incl. Nakhchivan, generous
        for (double[] p : pts)
            if (p[0] < 44.5 || p[0] > 51 || p[1] < 38.2 || p[1] > 42) throw new IllegalArgumentException("listing.geo.outside");
        double lng = pts.stream().mapToDouble(p -> p[0]).average().orElseThrow();
        double lat = pts.stream().mapToDouble(p -> p[1]).average().orElseThrow();
        double area = areaHa(pts, lat);
        if (area < 0.1) throw new IllegalArgumentException("listing.geo.tooSmall");
        if (area > MAX_HA) throw new IllegalArgumentException("listing.geo.tooLarge");
        StringBuilder sb = new StringBuilder("{\"type\":\"Polygon\",\"coordinates\":[[");
        for (double[] p : pts) sb.append(String.format(Locale.ROOT, "[%.6f,%.6f],", p[0], p[1]));
        sb.append(String.format(Locale.ROOT, "[%.6f,%.6f]]]}", pts.get(0)[0], pts.get(0)[1]));
        return new Shape(sb.toString(), lng, lat, Math.round(area * 10) / 10.0);
    }

    /** Shoelace on a local equirectangular projection — exact enough for plots of a few km. */
    static double areaHa(List<double[]> pts, double lat0) {
        double kx = 111_320 * Math.cos(Math.toRadians(lat0)), ky = 110_540, sum = 0;
        for (int i = 0; i < pts.size(); i++) {
            double[] a = pts.get(i), b = pts.get((i + 1) % pts.size());
            sum += a[0] * kx * b[1] * ky - b[0] * kx * a[1] * ky;
        }
        return Math.abs(sum) / 2 / 10_000;
    }

    private static boolean same(double[] a, double[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }
}
