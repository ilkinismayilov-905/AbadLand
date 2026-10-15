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
    private final Viewer3dData viewer3d;
    private final az.aqroinvest.auction.AuctionService auctionService;

    @GetMapping("/saheler")
    public String catalog(@ModelAttribute("filter") PlotFilter filter, Model model, jakarta.servlet.http.HttpServletRequest request) {
        addFilterOptions(model, filter, request);
        model.addAttribute("cards", plotService.search(filter));
        return "saheler";
    }

    @GetMapping("/xerite")
    public String map(@ModelAttribute("filter") PlotFilter filter, Model model,
                      org.springframework.security.core.Authentication auth, jakarta.servlet.http.HttpServletRequest request) {
        addFilterOptions(model, filter, request);
        model.addAttribute("mapPlots", plotService.search(filter).stream().map(c -> {
            Map<String, Object> m = viewerData(c, auth);
            m.put("district", c.plot().getDistrict());
            m.put("areaHa", c.plot().getAreaHa());
            m.put("score", c.fruitScore() == null ? "" : c.fruitScore() + "%");
            return m;
        }).toList());
        return "xerite";
    }

    private Map<String, Object> viewerData(PlotCard c, org.springframework.security.core.Authentication auth) {
        return viewer3d.of(c.plot(), c.competition() != null, auth);
    }

    @GetMapping("/saheler/{id}")
    public String passport(@PathVariable Long id, @RequestParam(required = false) String crop,
                           @RequestParam(required = false) Integer score, Model model,
                           org.springframework.security.core.Authentication auth) {
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
        model.addAttribute("plot3d", viewerData(card, auth));
        model.addAttribute("plotAuctions", auctionService.forPlot(plot));
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
            ra.addFlashAttribute("success", I18n.t("plots.ai.ready"));
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
            rows.add(row(I18n.t("plots.compare.district"), cards, c -> c.plot().getDistrict() + " (" + c.plot().getVillage() + ")", null, false));
            rows.add(row(I18n.t("plots.compare.area"), cards, c -> c.plot().getAreaHa(), c -> c.plot().getAreaHa(), true));
            rows.add(row(I18n.t("plots.compare.purpose"), cards, c -> c.plot().getPurpose(), null, false));
            rows.add(row(I18n.t("plots.compare.demining"), cards, c -> c.plot().getDemining(),
                    c -> (double) -c.plot().getDemining().ordinal(), true));
            rows.add(row(I18n.t("plots.compare.waterSource"), cards, c -> c.plot().getWaterSource(), null, false));
            rows.add(row(I18n.t("plots.compare.irrigation"), cards, c -> I18n.t(c.plot().isIrrigation() ? "plots.passport.yes" : "plots.passport.no"), c -> c.plot().isIrrigation() ? 1.0 : 0.0, true));
            rows.add(row(I18n.t("plots.passport.soil"), cards, c -> c.plot().getSoilType(), null, false));
            rows.add(row(I18n.t("plots.compare.roadKm"), cards, c -> c.plot().getRoadKm(), c -> c.plot().getRoadKm(), false));
            rows.add(row(I18n.t("plots.passport.electricity"), cards, c -> I18n.t(c.plot().isElectricity() ? "plots.passport.yes" : "plots.passport.no"), c -> c.plot().isElectricity() ? 1.0 : 0.0, true));
            rows.add(row(I18n.t("plots.compare.fee"), cards, c -> c.plot().getFeePerHa(), c -> (double) c.plot().getFeePerHa(), false));
            rows.add(row(I18n.t("plots.compare.leaseYears"), cards, c -> c.plot().getLeaseYears(), null, false));
            rows.add(row(I18n.t("plots.compare.bestCrop"), cards, c -> c.suitability().isEmpty() ? "—"
                            : c.suitability().get(0).crop().getName() + " " + c.suitability().get(0).score() + "%",
                    c -> c.suitability().isEmpty() ? 0.0 : c.suitability().get(0).score(), true));
            rows.add(row(I18n.t("plots.passport.competition"), cards, c -> c.competition() == null ? "—" : I18n.t("plots.compare.competitionDays", c.competition().getCode(), String.valueOf(c.daysLeft())), null, false));
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

    /** An active filter shown as a removable chip: "Nar ×" links to the same page without that filter. */
    public record Chip(String label, String removeUrl) {}

    private void addFilterOptions(Model model, PlotFilter f, jakarta.servlet.http.HttpServletRequest request) {
        List<Crop> fruits = crops.findAll().stream().filter(Crop::isFruit).toList();
        model.addAttribute("districts", plotService.districts());
        model.addAttribute("districtRegions", plotService.districtRegions());
        model.addAttribute("fruits", fruits);
        model.addAttribute("regions", EconomicRegion.values());
        model.addAttribute("activities", ActivityType.values());
        model.addAttribute("deminings", DeminingStatus.values());
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        model.addAttribute("cardsUrl", "/saheler" + query);
        model.addAttribute("mapUrl", "/xerite" + query);

        String path = request.getRequestURI();
        java.util.function.Function<String[], String> without = names -> {
            var b = org.springframework.web.util.UriComponentsBuilder.fromUriString(path + query);
            for (String n : names) b.replaceQueryParam(n);
            return b.build().toUriString();
        };
        List<Chip> chips = new java.util.ArrayList<>();
        if (notBlank(f.q())) chips.add(new Chip("“" + f.q().strip() + "”", without.apply(new String[]{"q"})));
        if (notBlank(f.fruit())) fruits.stream().filter(c -> c.getCode().equals(f.fruit())).findFirst()
                .ifPresent(c -> chips.add(new Chip(c.getName(), without.apply(new String[]{"fruit"}))));
        if (f.activity() != null) chips.add(new Chip(labels.label(f.activity()), without.apply(new String[]{"activity"})));
        if (f.region() != null) chips.add(new Chip(labels.label(f.region()), without.apply(new String[]{"region"})));
        if (notBlank(f.district())) chips.add(new Chip(f.district(), without.apply(new String[]{"district"})));
        if (f.demining() != null) chips.add(new Chip(labels.label(f.demining()), without.apply(new String[]{"demining"})));
        if (f.minHa() != null || f.maxHa() != null)
            chips.add(new Chip(I18n.t("plots.ha", (f.minHa() == null ? "0" : fmt.dec(f.minHa())) + "–" + (f.maxHa() == null ? "∞" : fmt.dec(f.maxHa()))),
                    without.apply(new String[]{"minHa", "maxHa"})));
        if (Boolean.TRUE.equals(f.open())) chips.add(new Chip(I18n.t("plots.filter.openOnly"), without.apply(new String[]{"open"})));
        model.addAttribute("activeChips", chips);
        model.addAttribute("clearUrl", path);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
