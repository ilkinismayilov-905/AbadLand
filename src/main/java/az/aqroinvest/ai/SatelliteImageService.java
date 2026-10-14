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
import java.util.Locale;

/** Esri World Imagery export of the plot bbox (+15 %), cached as uploads/satellite/{code}.jpg. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SatelliteImageService {

    private static final String EXPORT = "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/export"
            + "?bbox=%.6f,%.6f,%.6f,%.6f&bboxSR=4326&imageSR=3857&size=1024,1024&format=jpg&f=image";

    private final HttpClient http;
    private final ObjectMapper json;

    @Value("${app.upload-dir}")
    private String uploadDir;
    @Value("${app.external.enabled:true}")
    private boolean externalEnabled;

    /** Cached image path, fetching it once if needed; null if unavailable. */
    public synchronized Path image(Plot plot) {
        Path file = Path.of(uploadDir, "satellite", plot.getCode().replaceAll("[^A-Za-z0-9-]", "") + ".jpg");
        if (Files.isRegularFile(file)) return file;
        if (!externalEnabled) return null;
        try {
            double[] b = bbox(plot.getGeoJson(), 0.15);
            String url = String.format(Locale.ROOT, EXPORT, b[0], b[1], b[2], b[3]);
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = r.body();
            boolean jpeg = body.length > 3 && (body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8;
            if (r.statusCode() != 200 || !jpeg) {
                log.warn("Esri export for {} failed: HTTP {}", plot.getCode(), r.statusCode());
                return null;
            }
            Files.createDirectories(file.getParent());
            Files.write(file, body);
            return file;
        } catch (Exception e) {
            log.warn("Esri export for {} failed: {}", plot.getCode(), e.toString());
            return null;
        }
    }

    /** [minLng, minLat, maxLng, maxLat] of the polygon, padded by the given fraction on each side. */
    double[] bbox(String geoJson, double pad) throws Exception {
        double minLng = 180, minLat = 90, maxLng = -180, maxLat = -90;
        for (JsonNode ring : json.readTree(geoJson).path("coordinates"))
            for (JsonNode pt : ring) {
                minLng = Math.min(minLng, pt.get(0).asDouble());
                maxLng = Math.max(maxLng, pt.get(0).asDouble());
                minLat = Math.min(minLat, pt.get(1).asDouble());
                maxLat = Math.max(maxLat, pt.get(1).asDouble());
            }
        double dx = (maxLng - minLng) * pad, dy = (maxLat - minLat) * pad;
        return new double[]{minLng - dx, minLat - dy, maxLng + dx, maxLat + dy};
    }
}
