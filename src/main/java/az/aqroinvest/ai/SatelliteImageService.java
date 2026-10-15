package az.aqroinvest.ai;

import az.aqroinvest.domain.Plot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Esri World Imagery export of the plot (square Web Mercator extent = bbox + 15 % per side) with the plot boundary drawn
 * as a yellow outline — the AI prompt refers to it. Cached as uploads/satellite/{code}-v2.jpg.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SatelliteImageService {

    private static final String EXPORT = "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/export"
            + "?bbox=%.2f,%.2f,%.2f,%.2f&bboxSR=3857&imageSR=3857&size=%d,%d&format=jpg&f=image";
    static final int SIZE = 1024;
    private static final double R = 6378137; // Web Mercator sphere radius (m)

    private final HttpClient http;
    private final ObjectMapper json;

    @Value("${app.upload-dir}")
    private String uploadDir;
    @Value("${app.external.enabled:true}")
    private boolean externalEnabled;

    /** Cached image path, fetching it once if needed; null if unavailable. */
    public synchronized Path image(Plot plot) {
        // "-v2": images cached before the outline was added are not reused
        Path file = Path.of(uploadDir, "satellite", plot.getCode().replaceAll("[^A-Za-z0-9-]", "") + "-v2.jpg");
        if (Files.isRegularFile(file)) return file;
        if (!externalEnabled) return null;
        try {
            List<double[]> ring = mercatorRing(plot.getGeoJson());
            double[] e = squareExtent(ring, 0.15);
            String url = String.format(Locale.ROOT, EXPORT, e[0], e[1], e[2], e[3], SIZE, SIZE);
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = r.body();
            boolean jpeg = body.length > 3 && (body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8;
            if (r.statusCode() != 200 || !jpeg) {
                log.warn("Esri export for {} failed: HTTP {}", plot.getCode(), r.statusCode());
                return null;
            }
            Files.createDirectories(file.getParent());
            Files.write(file, withOutline(body, ring, e));
            return file;
        } catch (Exception ex) {
            log.warn("Esri export for {} failed: {}", plot.getCode(), ex.toString());
            return null;
        }
    }

    /** Outer ring of the GeoJSON polygon in Web Mercator metres. */
    List<double[]> mercatorRing(String geoJson) throws Exception {
        List<double[]> pts = new ArrayList<>();
        for (JsonNode pt : json.readTree(geoJson).path("coordinates").path(0))
            pts.add(mercator(pt.get(0).asDouble(), pt.get(1).asDouble()));
        if (pts.size() < 3) throw new IllegalArgumentException("polygon");
        return pts;
    }

    static double[] mercator(double lng, double lat) {
        return new double[]{R * Math.toRadians(lng), R * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2))};
    }

    /**
     * [minX, minY, maxX, maxY] in metres: square (the image is square, so Esri does not stretch or re-fit it),
     * centred on the polygon, side = longer polygon side × (1 + 2 × pad).
     */
    static double[] squareExtent(List<double[]> ring, double pad) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : ring) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        double half = Math.max(maxX - minX, maxY - minY) * (1 + 2 * pad) / 2, cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        return new double[]{cx - half, cy - half, cx + half, cy + half};
    }

    /** Pixel position of a mercator point in a square image of the given size covering the extent (y grows downwards). */
    static double[] pixel(double[] p, double[] extent, int size) {
        return new double[]{(p[0] - extent[0]) / (extent[2] - extent[0]) * size, (extent[3] - p[1]) / (extent[3] - extent[1]) * size};
    }

    /** Draws the boundary: dark halo + 3 px yellow line, so it reads on both bright and dark ground. */
    static byte[] withOutline(byte[] jpeg, List<double[]> ring, double[] extent) throws java.io.IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(jpeg));
        if (src == null) return jpeg;
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < ring.size(); i++) {
            double[] px = pixel(ring.get(i), extent, img.getWidth());
            if (i == 0) path.moveTo(px[0], px[1]);
            else path.lineTo(px[0], px[1]);
        }
        path.closePath();
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 0, 0, 140));
        g.draw(path);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 214, 0));
        g.draw(path);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
