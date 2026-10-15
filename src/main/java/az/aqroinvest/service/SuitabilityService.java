package az.aqroinvest.service;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.AiAnalysisRepository;
import az.aqroinvest.repo.CropRepository;
import az.aqroinvest.repo.PlotClimateRepository;
import az.aqroinvest.repo.PlotCropSuitabilityRepository;
import az.aqroinvest.repo.PlotRepository;
import az.aqroinvest.web.I18n;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Crop suitability per plot. Stored rows by source; reads pick EXPERT > AI > RULE per crop. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuitabilityService {

    /** Effective score shown in UI. */
    public record Suit(Crop crop, int score, SuitabilitySource source, String reason) {}

    /** RULE result before storing. */
    public record RuleScore(int score, String reason) {}

    private final PlotRepository plots;
    private final CropRepository crops;
    private final PlotClimateRepository climates;
    private final PlotCropSuitabilityRepository repo;
    private final AiAnalysisRepository aiAnalyses;
    private final ObjectMapper json;

    @Transactional
    public void recomputeAll() {
        plots.findAll().forEach(this::recompute);
    }

    /** Re-run RULE scores and re-import the latest AI analysis for one plot (call after plot/climate/AI changes). */
    @Transactional
    public void recompute(Plot plot) {
        repo.deleteByPlotAndSource(plot, SuitabilitySource.RULE);
        PlotClimate climate = climates.findByPlot(plot).orElse(null);
        for (Crop crop : cropsFor(plot)) {
            RuleScore r = ruleScore(plot, climate, crop);
            if (r != null) save(plot, crop, r.score(), SuitabilitySource.RULE, r.reason());
        }
        aiAnalyses.findFirstByPlotAndStatusOrderByCreatedAtDesc(plot, "DONE").ifPresent(this::applyAi);
    }

    /** Stores AI rows from an analysis: suitableCrops[].code (score as given), notRecommended[] matched by crop name. */
    @Transactional
    public void applyAi(AiAnalysis analysis) {
        Plot plot = analysis.getPlot();
        repo.deleteByPlotAndSource(plot, SuitabilitySource.AI);
        try {
            JsonNode root = json.readTree(analysis.getResultJson());
            Map<String, Crop> byCode = crops.findAll().stream().collect(Collectors.toMap(Crop::getCode, c -> c));
            Map<String, Crop> byName = crops.findAll().stream().collect(Collectors.toMap(c -> c.getNameAz().toLowerCase(), c -> c));
            Set<Crop> done = new HashSet<>();
            for (JsonNode s : root.path("suitableCrops")) {
                Crop crop = byCode.get(s.path("code").asText(""));
                if (crop != null && done.add(crop))
                    save(plot, crop, clamp(s.path("score").asInt()), SuitabilitySource.AI, s.path("reason").asText(null));
            }
            for (JsonNode n : root.path("notRecommended")) {
                Crop crop = byName.get(n.path("name").asText("").trim().toLowerCase());
                if (crop != null && done.add(crop))
                    save(plot, crop, 20, SuitabilitySource.AI, n.path("reason").asText(null));
            }
        } catch (Exception e) {
            log.warn("Cannot read AI analysis {} for plot {}: {}", analysis.getId(), plot.getCode(), e.getMessage());
        }
    }

    /** Effective suitability per crop, best first. */
    public List<Suit> forPlot(Plot plot) {
        PlotClimate climate = climates.findByPlot(plot).orElse(null);
        return effective(repo.findByPlot(plot), r -> reason(r, climate));
    }

    /** Effective suitability for all plots, keyed by plot id (one query). */
    public Map<Long, List<Suit>> forAllPlots() {
        Map<Long, PlotClimate> climateByPlot = climates.findAll().stream().collect(Collectors.toMap(c -> c.getPlot().getId(), c -> c, (a, b) -> a));
        return repo.findAll().stream()
                .collect(Collectors.groupingBy(s -> s.getPlot().getId(), Collectors.collectingAndThen(Collectors.toList(),
                        rows -> effective(rows, r -> reason(r, climateByPlot.get(r.getPlot().getId()))))));
    }

    /** RULE reasons are stored in AZ; re-derive them so they show in the request language. */
    private String reason(PlotCropSuitability r, PlotClimate climate) {
        if (r.getSource() != SuitabilitySource.RULE) return r.getReason();
        RuleScore s = ruleScore(r.getPlot(), climate, r.getCrop());
        return s == null ? r.getReason() : s.reason();
    }

    static List<Suit> effective(List<PlotCropSuitability> rows, java.util.function.Function<PlotCropSuitability, String> reason) {
        Map<Long, PlotCropSuitability> best = new HashMap<>();
        for (PlotCropSuitability r : rows)
            best.merge(r.getCrop().getId(), r, (a, b) -> priority(a.getSource()) >= priority(b.getSource()) ? a : b);
        return best.values().stream()
                .map(r -> new Suit(r.getCrop(), r.getScore(), r.getSource(), reason.apply(r)))
                .sorted(Comparator.comparingInt(Suit::score).reversed().thenComparing(s -> s.crop().getNameAz()))
                .toList();
    }

    private static int priority(SuitabilitySource s) {
        return switch (s) { case EXPERT -> 3; case AI -> 2; case RULE -> 1; };
    }

    /** Only crops of the plot's activity are scored (no cattle chips on an orchard). */
    private List<Crop> cropsFor(Plot plot) {
        return crops.findAll().stream().filter(c -> c.getActivityType() == plot.getPurpose()).toList();
    }

    /** RULE score from SPEC; null = no score (no climate and crop not recommended). */
    public RuleScore ruleScore(Plot plot, PlotClimate climate, Crop crop) {
        boolean recommended = isRecommended(plot, crop);
        if (climate == null) return recommended ? new RuleScore(70, I18n.t("plots.suit.recommendedNoClimate")) : null;
        int score = 100;
        List<String> why = new ArrayList<>();
        if (climate.getAbsMinTempC() != null && climate.getAbsMinTempC() < crop.getFrostToleranceC()) {
            score -= 40;
            why.add(I18n.t("plots.suit.frost", String.format(Locale.ROOT, "%.0f", climate.getAbsMinTempC()), String.valueOf(crop.getFrostToleranceC())));
        }
        if (climate.getElevationM() != null && climate.getElevationM() > crop.getMaxElevationM()) {
            score -= 30;
            why.add(I18n.t("plots.suit.elevation", String.valueOf(climate.getElevationM()), String.valueOf(crop.getMaxElevationM())));
        }
        if (!plot.isIrrigation() && crop.getWaterNeed() == WaterNeed.HIGH) {
            score -= 30;
            why.add(I18n.t("plots.suit.highWater"));
        } else if (!plot.isIrrigation() && crop.getWaterNeed() == WaterNeed.MEDIUM) {
            score -= 15;
            why.add(I18n.t("plots.suit.mediumWater"));
        }
        if (!plot.isIrrigation() && climate.getPrecipMm() != null && climate.getPrecipMm() < 300) {
            score -= 15;
            why.add(I18n.t("plots.suit.lowPrecip", String.format(Locale.ROOT, "%.0f", climate.getPrecipMm())));
        }
        if (!recommended) {
            score -= 20;
            why.add(I18n.t("plots.suit.notRecommended"));
        }
        return new RuleScore(clamp(score), why.isEmpty() ? I18n.t("plots.suit.ok") : String.join("; ", why));
    }

    static boolean isRecommended(Plot plot, Crop crop) {
        return plot.getRecommendedCrops() != null
                && Arrays.asList(plot.getRecommendedCrops().split(",")).contains(crop.getCode());
    }

    private void save(Plot plot, Crop crop, int score, SuitabilitySource source, String reason) {
        PlotCropSuitability s = new PlotCropSuitability();
        s.setPlot(plot);
        s.setCrop(crop);
        s.setScore(score);
        s.setSource(source);
        s.setReason(reason);
        repo.save(s);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
