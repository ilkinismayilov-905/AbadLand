package az.aqroinvest.ai;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.AiAnalysisRepository;
import az.aqroinvest.repo.AuditLogRepository;
import az.aqroinvest.repo.CropRepository;
import az.aqroinvest.repo.PlotClimateRepository;
import az.aqroinvest.repo.UserRepository;
import az.aqroinvest.service.AuditService;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.service.SuitabilityService;
import az.aqroinvest.web.StatusLabels;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static az.aqroinvest.service.AuditService.BAKU;

/** Satellite image + climate + plot facts → Claude → AiAnalysis + AI suitability rows. One real analysis per plot (cached). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    public static final String SAMPLE_MODEL = "sample";

    private final AiClient ai;
    private final SatelliteImageService satellite;
    private final ClimateService climateService;
    private final PlotClimateRepository climates;
    private final AiAnalysisRepository analyses;
    private final AuditLogRepository auditLogs;
    private final CropRepository crops;
    private final UserRepository users;
    private final SuitabilityService suitability;
    private final AuditService audit;
    private final StatusLabels labels;
    private final ObjectMapper json;

    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    @Value("${ai.max-per-user-per-day:5}")
    private int maxPerDay;

    public boolean isConfigured() {
        return ai.isConfigured();
    }

    /** Latest DONE analysis (real or sample). */
    public Optional<AiAnalysis> latest(Plot plot) {
        return analyses.findFirstByPlotAndStatusOrderByCreatedAtDesc(plot, "DONE");
    }

    public Optional<AiView> view(AiAnalysis a) {
        try {
            return Optional.of(json.readValue(a.getResultJson(), AiView.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Runs (or returns the cached) analysis. rerun = admin forces a new call.
     * @return the analysis; cached ones return instantly without calling the model.
     */
    public AiAnalysis analyze(Plot plot, String email, boolean rerun) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        boolean admin = user.getRole() == Role.ADMIN;
        if (rerun && !admin) throw new BusinessException("Yenidən analiz yalnız admin üçündür");
        Optional<AiAnalysis> cached = latest(plot).filter(a -> !SAMPLE_MODEL.equals(a.getModel()));
        if (cached.isPresent() && !rerun) return cached.get();
        if (!ai.isConfigured()) throw new BusinessException("AI açarı konfiqurasiya edilməyib");
        if (!admin && usedToday(email) >= maxPerDay)
            throw new BusinessException("Gündəlik AI analizi limitinə çatdınız (" + maxPerDay + "). Sabah yenidən cəhd edin.");
        if (!running.add(plot.getId())) throw new BusinessException("Bu sahə üçün analiz artıq gedir — bir neçə saniyə sonra səhifəni yeniləyin.");
        try {
            PlotClimate climate = climates.findByPlot(plot).orElse(null);
            if (climate == null || climate.getFetchedAt() == null) {
                climateService.refresh(plot);
                climate = climates.findByPlot(plot).orElse(null);
            }
            Path img = satellite.image(plot);
            byte[] jpeg = img == null ? null : Files.readAllBytes(img);
            AiAnalysis a = new AiAnalysis();
            a.setPlot(plot);
            a.setModel(ai.model());
            a.setCreatedAt(LocalDateTime.now(BAKU));
            try {
                String result = ai.analyze(jpeg, userText(plot, climate, jpeg != null));
                json.readValue(result, AiView.class); // must match the SPEC shape
                a.setResultJson(result);
                a.setStatus("DONE");
            } catch (AiException e) {
                a.setStatus("FAILED");
                a.setError(e.getMessage());
            } catch (Exception e) {
                a.setStatus("FAILED");
                a.setError("AI cavabı gözlənilən formatda deyil.");
            }
            analyses.save(a);
            audit.log(email, user.getRole().name(), "AI_ANALYSIS", "Plot", plot.getCode(), a.getStatus() + " " + a.getModel());
            if (!"DONE".equals(a.getStatus())) throw new BusinessException(a.getError());
            suitability.applyAi(a);
            return a;
        } catch (java.io.IOException e) {
            throw new BusinessException("Peyk görüntüsünü oxumaq mümkün olmadı.");
        } finally {
            running.remove(plot.getId());
        }
    }

    long usedToday(String email) {
        LocalDateTime start = LocalDate.now(BAKU).atStartOfDay();
        return auditLogs.countByActorEmailIgnoreCaseAndActionAndAtAfter(email, "AI_ANALYSIS", start);
    }

    /** Plot facts + climate + allowed crop codes (SPEC "User text"). */
    String userText(Plot p, PlotClimate c, boolean hasImage) {
        String allowed = crops.findAll().stream().map(cr -> cr.getCode() + " (" + cr.getNameAz() + ")").collect(Collectors.joining(", "));
        StringBuilder sb = new StringBuilder();
        sb.append(hasImage ? "Satellite image above (Esri World Imagery, bbox +15%).\n" : "Satellite image is NOT available — say so in dataGaps.\n");
        sb.append("Plot: ").append(p.getCode()).append(" — ").append(p.getName()).append('\n');
        sb.append("District: ").append(p.getDistrict()).append(", region: ").append(labels.label(p.getEconomicRegion()))
                .append(", village: ").append(p.getVillage()).append('\n');
        sb.append(String.format(Locale.ROOT, "Area: %.1f ha; purpose: %s; lat/lng: %.5f, %.5f%n", p.getAreaHa(),
                labels.label(p.getPurpose()), p.getLat(), p.getLng()));
        sb.append("Demining: ").append(labels.label(p.getDemining())).append('\n');
        sb.append("Water source: ").append(p.getWaterSource()).append("; irrigation: ").append(p.isIrrigation() ? "yes" : "no").append('\n');
        sb.append("Soil type: ").append(p.getSoilType()).append(String.format(Locale.ROOT, "; road: %.1f km; electricity: %s%n",
                p.getRoadKm(), p.isElectricity() ? "yes" : "no"));
        if (c != null)
            sb.append(String.format(Locale.ROOT, "Climate (%s): elevation %s m, mean temp %s °C, absolute min %s °C, frost days %s, precipitation %s mm/year%n",
                    c.getFetchedAt() == null ? "test values" : "Open-Meteo 2025", c.getElevationM(), c.getMeanTempC(),
                    c.getAbsMinTempC(), c.getFrostDays(), c.getPrecipMm()));
        else sb.append("Climate: not available\n");
        sb.append("Allowed crop codes (use these in suitableCrops[].code, null if none fits): ").append(allowed).append('\n');
        return sb.toString();
    }
}
