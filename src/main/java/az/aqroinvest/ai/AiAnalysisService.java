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
        if (rerun && !admin) throw new BusinessException(az.aqroinvest.web.I18n.t("plots.ai.rerunAdminOnly"));
        Optional<AiAnalysis> cached = latest(plot).filter(a -> !SAMPLE_MODEL.equals(a.getModel()));
        if (cached.isPresent() && !rerun) return cached.get();
        if (!ai.isConfigured()) throw new BusinessException(az.aqroinvest.web.I18n.t("plots.ai.noKey"));
        if (!admin && usedToday(email) >= maxPerDay)
            throw new BusinessException(az.aqroinvest.web.I18n.t("plots.ai.dailyLimit", String.valueOf(maxPerDay)));
        if (!running.add(plot.getId())) throw new BusinessException(az.aqroinvest.web.I18n.t("plots.ai.running"));
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
                a.setError(az.aqroinvest.web.I18n.t("plots.ai.badFormat"));
            }
            analyses.save(a);
            audit.log(email, user.getRole().name(), "AI_ANALYSIS", "Plot", plot.getCode(), a.getStatus() + " " + a.getModel());
            if (!"DONE".equals(a.getStatus())) throw new BusinessException(a.getError());
            suitability.applyAi(a);
            return a;
        } catch (java.io.IOException e) {
            throw new BusinessException(az.aqroinvest.web.I18n.t("plots.ai.imageReadFailed"));
        } finally {
            running.remove(plot.getId());
        }
    }

    long usedToday(String email) {
        LocalDateTime start = LocalDate.now(BAKU).atStartOfDay();
        return auditLogs.countByActorEmailIgnoreCaseAndActionAndAtAfter(email, "AI_ANALYSIS", start);
    }

    /** prompts/land-analysis-user.txt: plot facts, climate and the allowed-crop table; {placeholders} filled below. */
    private static final String USER_TEMPLATE = load("prompts/land-analysis-user.txt");
    static final String NO_IMAGE_NOTE = "Peyk şəkli YOXDUR — bunu dataGaps-da qeyd et və etibarlılığı aşağı sal. Yalnız JSON cavab ver.";
    private static final String UNKNOWN = "məlum deyil";

    private static String load(String resource) {
        try {
            return new org.springframework.core.io.ClassPathResource(resource).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(resource, e);
        }
    }

    /** Facts in Azerbaijani (the prompt language) whatever language the user browses in; nothing invented: missing → "məlum deyil". */
    String userText(Plot p, PlotClimate c, boolean hasImage) {
        Locale azLocale = az.aqroinvest.web.I18n.AZ;
        java.util.function.Function<Enum<?>, String> label = e -> e == null ? UNKNOWN
                : e.name() + " (" + az.aqroinvest.web.I18n.t(azLocale, "enum." + e.getDeclaringClass().getSimpleName() + "." + e.name()) + ")";
        java.util.Map<String, String> v = new java.util.LinkedHashMap<>();
        v.put("code", p.getCode());
        v.put("name", p.getName());
        v.put("district", p.getDistrict());
        v.put("economicRegion", label.apply(p.getEconomicRegion()));
        v.put("village", p.getVillage());
        v.put("lat", String.format(Locale.ROOT, "%.5f", p.getLat()));
        v.put("lng", String.format(Locale.ROOT, "%.5f", p.getLng()));
        v.put("areaHa", String.format(Locale.ROOT, "%.1f", p.getAreaHa()));
        v.put("purpose", label.apply(p.getPurpose()));
        v.put("deminingStatus", label.apply(p.getDemining()));
        v.put("waterSource", p.getWaterSource());
        v.put("irrigation", p.isIrrigation() ? "var" : "yoxdur");
        v.put("soilType", p.getSoilType());
        v.put("roadKm", String.format(Locale.ROOT, "%.1f", p.getRoadKm()));
        v.put("electricity", p.isElectricity() ? "var" : "yoxdur");
        v.put("climateYear", c == null ? UNKNOWN : c.getFetchedAt() == null ? "sınaq dəyərləri, real ölçü deyil" : "2025 il arxivi");
        v.put("elevationM", c == null ? null : String.valueOf(c.getElevationM()));
        v.put("meanTempC", c == null ? null : String.valueOf(c.getMeanTempC()));
        v.put("absMinTempC", c == null ? null : String.valueOf(c.getAbsMinTempC()));
        v.put("frostDays", c == null ? null : String.valueOf(c.getFrostDays()));
        v.put("precipMm", c == null ? null : String.valueOf(c.getPrecipMm()));
        v.put("cropTable", crops.findAll().stream().sorted(java.util.Comparator.comparing(Crop::getCode))
                .map(cr -> cr.getCode() + " | " + cr.getNameAz() + " | " + cr.getFrostToleranceC() + " | " + cr.getMaxElevationM()
                        + " | " + (cr.getWaterNeed() == null ? UNKNOWN : cr.getWaterNeed().name()))
                .collect(Collectors.joining("\n")));
        String text = USER_TEMPLATE;
        for (var e : v.entrySet()) {
            String val = e.getValue() == null || e.getValue().isBlank() || e.getValue().equals("null") ? UNKNOWN : e.getValue();
            text = text.replace("{" + e.getKey() + "}", val);
        }
        if (!hasImage) text = text.replace("Şəkildə sarı xətt sahənin sərhədidir. Yalnız JSON cavab ver.", NO_IMAGE_NOTE);
        return text;
    }
}
