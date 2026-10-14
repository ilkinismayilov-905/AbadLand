package az.aqroinvest.web;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.CompetitionRepository;
import az.aqroinvest.repo.CropRepository;
import az.aqroinvest.repo.PlotClimateRepository;
import az.aqroinvest.repo.PlotRepository;
import az.aqroinvest.service.PlotService;
import az.aqroinvest.service.PlotService.PlotCard;
import az.aqroinvest.service.PlotService.PlotFilter;
import az.aqroinvest.service.RoiService;
import az.aqroinvest.service.SuitabilityService.Suit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;

@Controller
@RequiredArgsConstructor
public class PlotController {

    private final PlotService plotService;
    private final RoiService roiService;
    private final PlotRepository plots;
    private final CropRepository crops;
    private final PlotClimateRepository climates;
    private final CompetitionRepository competitions;
    private final StatusLabels labels;
    private final Fmt fmt;
    private final az.aqroinvest.ai.AiAnalysisService aiService;
    private final az.aqroinvest.ai.SatelliteImageService satellite;

    @GetMapping("/saheler")
    public String catalog(@ModelAttribute("filter") PlotFilter filter, Model model) {
        addFilterOptions(model);
        model.addAttribute("cards", plotService.search(filter));
        return "saheler";
    }

    @GetMapping("/xerite")
    public String map(@ModelAttribute("filter") PlotFilter filter, Model model) {
        addFilterOptions(model);
        model.addAttribute("mapPlots", plotService.search(filter).stream().map(c -> Map.of(
                "id", c.plot().getId(), "code", c.plot().getCode(), "name", c.plot().getName(),
                "district", c.plot().getDistrict(), "areaHa", c.plot().getAreaHa(),
                "purpose", c.plot().getPurpose().name(), "geoJson", c.plot().getGeoJson(),
                "score", c.fruitScore() == null ? "" : c.fruitScore() + "%")).toList());
        return "xerite";
    }

    @GetMapping("/saheler/{id}")
    public String passport(@PathVariable Long id, @RequestParam(required = false) String crop,
                           @RequestParam(required = false) Integer score, Model model) {
        Plot plot = plots.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlotCard card = plotService.card(plot);
        List<Crop> cropOptions = crops.findAll().stream().filter(c -> c.getActivityType() == plot.getPurpose()).toList();
        String firstRec = plot.getRecommendedCrops() == null ? null : plot.getRecommendedCrops().split(",")[0];
        var analysis = aiService.latest(plot).orElse(null);
        var aiView = analysis == null ? null : aiService.view(analysis).orElse(null);
        boolean sample = analysis != null && az.aqroinvest.ai.AiAnalysisService.SAMPLE_MODEL.equals(analysis.getModel());
        // ROI pre-selects the AI's top crop (real analyses only — the sample must not change the demo default)
        String aiTop = aiView == null || sample || aiView.suitableCrops() == null ? null : aiView.suitableCrops().stream()
                .filter(c -> c.code() != null && cropOptions.stream().anyMatch(o -> o.getCode().equals(c.code())))
                .max(Comparator.comparing(c -> c.score() == null ? 0 : c.score())).map(c -> c.code()).orElse(null);
        Crop selected = cropOptions.stream().filter(c -> c.getCode().equals(crop)).findFirst()
                .or(() -> cropOptions.stream().filter(c -> c.getCode().equals(aiTop)).findFirst())
                .or(() -> cropOptions.stream().filter(c -> c.getCode().equals(firstRec)).findFirst())
                .or(() -> cropOptions.stream().findFirst()).orElse(null);
        PlotClimate climate = climates.findByPlot(plot).orElse(null);
        if (selected != null) {
            Integer suit = card.suitability().stream().filter(s -> s.crop().getId().equals(selected.getId()))
                    .map(Suit::score).findFirst().orElse(null);
            model.addAttribute("roi", roiService.calculate(plot, selected, climate, suit));
        }
        model.addAttribute("card", card);
        model.addAttribute("plot", plot);
        model.addAttribute("climate", climate);
        model.addAttribute("cropOptions", cropOptions);
        model.addAttribute("selectedCrop", selected);
        model.addAttribute("matchScore", score);
        model.addAttribute("ai", aiView);
        model.addAttribute("aiAnalysis", analysis);
        model.addAttribute("aiSample", sample);
        model.addAttribute("aiConfigured", aiService.isConfigured());
        return "pasport";
    }

    /** Cached Esri image of the plot (fetched once). */
    @GetMapping(value = "/saheler/{id}/peyk.jpg", produces = "image/jpeg")
    @ResponseBody
    public org.springframework.http.ResponseEntity<org.springframework.core.io.FileSystemResource> satelliteImage(@PathVariable Long id) {
        Plot plot = plots.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var path = satellite.image(plot);
        if (path == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(1)))
                .body(new org.springframework.core.io.FileSystemResource(path));
    }

    @PostMapping("/saheler/{id}/ai-analiz")
    public String aiAnalysis(@PathVariable Long id, @RequestParam(required = false) String yeniden,
                             org.springframework.security.core.Authentication auth,
                             org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        Plot plot = plots.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            aiService.analyze(plot, auth.getName(), yeniden != null);
            ra.addFlashAttribute("success", "AI analizi hazırdır.");
        } catch (az.aqroinvest.service.BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/saheler/" + id + "#ai";
    }

    @GetMapping("/muqayise")
    public String compare(@RequestParam(required = false) List<Long> ids, Model model) {
        List<Plot> list = ids == null ? List.of() : plots.findAllById(ids.stream().distinct().limit(3).toList());
        List<PlotCard> cards = list.stream().map(plotService::card).toList();
        List<Row> rows = new ArrayList<>();
        if (!cards.isEmpty()) {
            rows.add(row("Rayon", cards, c -> c.plot().getDistrict() + " (" + c.plot().getVillage() + ")", null, false));
            rows.add(row("Sahə, ha", cards, c -> c.plot().getAreaHa(), c -> c.plot().getAreaHa(), true));
            rows.add(row("Təyinat", cards, c -> c.plot().getPurpose(), null, false));
            rows.add(row("Minalardan təmizlənmə", cards, c -> c.plot().getDemining(),
                    c -> (double) -c.plot().getDemining().ordinal(), true));
            rows.add(row("Su mənbəyi", cards, c -> c.plot().getWaterSource(), null, false));
            rows.add(row("Suvarma", cards, c -> c.plot().isIrrigation() ? "Var" : "Yoxdur", c -> c.plot().isIrrigation() ? 1.0 : 0.0, true));
            rows.add(row("Torpaq", cards, c -> c.plot().getSoilType(), null, false));
            rows.add(row("Yola məsafə, km", cards, c -> c.plot().getRoadKm(), c -> c.plot().getRoadKm(), false));
            rows.add(row("Elektrik", cards, c -> c.plot().isElectricity() ? "Var" : "Yoxdur", c -> c.plot().isElectricity() ? 1.0 : 0.0, true));
            rows.add(row("İcarə haqqı, AZN/ha/il", cards, c -> c.plot().getFeePerHa(), c -> (double) c.plot().getFeePerHa(), false));
            rows.add(row("İcarə müddəti, il", cards, c -> c.plot().getLeaseYears(), null, false));
            rows.add(row("Ən uyğun bitki", cards, c -> c.suitability().isEmpty() ? "—"
                            : c.suitability().get(0).crop().getNameAz() + " " + c.suitability().get(0).score() + "%",
                    c -> c.suitability().isEmpty() ? 0.0 : c.suitability().get(0).score(), true));
            rows.add(row("Müsabiqə", cards, c -> c.competition() == null ? "—" : c.competition().getCode() + " (" + c.daysLeft() + " gün)", null, false));
        }
        model.addAttribute("cards", cards);
        model.addAttribute("rows", rows);
        return "muqayise";
    }

    @GetMapping("/musabiqeler")
    public String competitionList(Model model) {
        model.addAttribute("competitions", competitions.findByStatusOrderByDeadlineAsc(CompetitionStatus.PUBLISHED));
        return "musabiqeler";
    }

    /** One compare row; best = indexes holding the max (higherIsBetter) or min value. */
    public record Row(String label, List<String> values, Set<Integer> best) {}

    private Row row(String label, List<PlotCard> cards, Function<PlotCard, Object> value,
                           Function<PlotCard, Double> metric, boolean higherIsBetter) {
        List<String> values = cards.stream().map(value).map(v -> v instanceof Enum<?> e ? labels.label(e)
                : v instanceof Number n ? fmt.dec(n) : String.valueOf(v)).toList();
        Set<Integer> best = new HashSet<>();
        if (metric != null && cards.size() > 1) {
            List<Double> m = cards.stream().map(metric).toList();
            double target = higherIsBetter ? Collections.max(m) : Collections.min(m);
            if (!m.stream().allMatch(v -> v == target))
                for (int i = 0; i < m.size(); i++) if (m.get(i) == target) best.add(i);
        }
        return new Row(label, values, best);
    }

    private void addFilterOptions(Model model) {
        model.addAttribute("districts", plotService.districts());
        model.addAttribute("fruits", crops.findAll().stream().filter(Crop::isFruit).toList());
        model.addAttribute("regions", EconomicRegion.values());
        model.addAttribute("activities", ActivityType.values());
        model.addAttribute("deminings", DeminingStatus.values());
    }
}
