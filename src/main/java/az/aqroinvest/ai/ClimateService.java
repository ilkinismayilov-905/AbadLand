package az.aqroinvest.ai;

import az.aqroinvest.domain.Plot;
import az.aqroinvest.domain.PlotClimate;
import az.aqroinvest.repo.PlotClimateRepository;
import az.aqroinvest.repo.PlotRepository;
import az.aqroinvest.service.SuitabilityService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import static az.aqroinvest.service.AuditService.BAKU;

/** Open-Meteo elevation + 2025 daily archive → PlotClimate. Failures keep the existing (seeded) values. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClimateService {

    private static final String ELEVATION = "https://api.open-meteo.com/v1/elevation?latitude=%.5f&longitude=%.5f";
    private static final String ARCHIVE = "https://archive-api.open-meteo.com/v1/archive?latitude=%.5f&longitude=%.5f"
            + "&start_date=2025-01-01&end_date=2025-12-31&daily=temperature_2m_mean,temperature_2m_min,precipitation_sum&timezone=Asia%%2FBaku";

    public record Stats(double meanTempC, double absMinTempC, int frostDays, double precipMm) {}

    private final HttpClient http;
    private final ObjectMapper json;
    private final PlotRepository plots;
    private final PlotClimateRepository climates;
    private final SuitabilityService suitability;

    @Value("${app.external.enabled:true}")
    private boolean externalEnabled;

    /** @return true if the plot now has real Open-Meteo data. */
    public boolean refresh(Plot plot) {
        if (!externalEnabled) return false;
        try {
            JsonNode elev = get(String.format(Locale.ROOT, ELEVATION, plot.getLat(), plot.getLng()));
            Stats s = stats(get(String.format(Locale.ROOT, ARCHIVE, plot.getLat(), plot.getLng())));
            PlotClimate c = climates.findByPlot(plot).orElseGet(() -> {
                PlotClimate n = new PlotClimate();
                n.setPlot(plot);
                return n;
            });
            c.setElevationM((int) Math.round(elev.path("elevation").path(0).asDouble()));
            c.setMeanTempC(round1(s.meanTempC()));
            c.setAbsMinTempC(round1(s.absMinTempC()));
            c.setFrostDays(s.frostDays());
            c.setPrecipMm((double) Math.round(s.precipMm()));
            c.setFetchedAt(LocalDateTime.now(BAKU));
            climates.save(c);
            suitability.recompute(plot);
            return true;
        } catch (Exception e) {
            log.warn("Open-Meteo for {} failed, keeping existing values: {}", plot.getCode(), e.toString());
            return false;
        }
    }

    /** @return number of plots updated. */
    public int refreshAll() {
        return (int) plots.findAll().stream().filter(this::refresh).count();
    }

    /** Aggregates the archive's daily arrays (nulls skipped). Frost day = daily minimum below 0 °C. */
    static Stats stats(JsonNode archive) {
        JsonNode d = archive.path("daily");
        double sumMean = 0, min = Double.MAX_VALUE, precip = 0;
        int nMean = 0, frost = 0;
        for (JsonNode v : d.path("temperature_2m_mean")) if (!v.isNull()) { sumMean += v.asDouble(); nMean++; }
        for (JsonNode v : d.path("temperature_2m_min")) if (!v.isNull()) { min = Math.min(min, v.asDouble()); if (v.asDouble() < 0) frost++; }
        for (JsonNode v : d.path("precipitation_sum")) if (!v.isNull()) precip += v.asDouble();
        if (nMean == 0 || min == Double.MAX_VALUE) throw new IllegalStateException("empty archive");
        return new Stats(sumMean / nMean, min, frost, precip);
    }

    private JsonNode get(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
        return json.readTree(r.body());
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
