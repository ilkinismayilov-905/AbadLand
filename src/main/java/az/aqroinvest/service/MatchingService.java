package az.aqroinvest.service;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.CropRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** Matching score 0–100 from SPEC ("Matching score"). */
@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(readOnly = true) // lazy collections also outside web requests
public class MatchingService {

    /** Quiz answers. budget ≥ 2 000 000 means "2M+" (no upper limit); size = "5-20" | "20-50" | "50-150" | "150+"; district "" = no preference. */
    public record Answers(ActivityType activity, Long budget, String size, String district, String experience) {
        public boolean complete() {
            return activity != null && budget != null && size != null && !size.isBlank() && district != null
                    && experience != null && !experience.isBlank();
        }
    }

    public record Component(String name, int points, int max) {}

    public record Reason(boolean ok, String text) {}

    public record Match(Plot plot, Competition competition, Long daysLeft, int score,
                        List<Component> components, List<Reason> reasons) {
        public List<Reason> topReasons() {
            return reasons.stream().limit(4).toList();
        }
    }

    public static final long BUDGET_NO_LIMIT = 2_000_000;

    private final PlotService plotService;
    private final CropRepository crops;
    private final az.aqroinvest.web.StatusLabels labels;

    public List<Match> match(Answers a) {
        Map<String, Crop> byCode = new HashMap<>();
        crops.findAll().forEach(c -> byCode.put(c.getCode(), c));
        Map<Long, Competition> open = plotService.openCompetitionByPlot();
        List<Match> result = new ArrayList<>();
        for (Competition c : new LinkedHashSet<>(open.values()))
            for (Plot p : c.getPlots()) {
                if (open.get(p.getId()) != c) continue; // plot counted once, under its earliest-deadline competition
                Match m = score(p, byCode.get(firstRecommended(p)), a);
                result.add(new Match(p, c, PlotService.daysLeft(c), m.score(), m.components(), m.reasons()));
            }
        result.sort(Comparator.comparingInt(Match::score).reversed().thenComparing(m -> m.plot().getCode()));
        return result;
    }

    /** Pure scoring of one plot; firstCrop = first recommended crop (null → 0 investment estimate). */
    public Match score(Plot p, Crop firstCrop, Answers a) {
        List<Component> comps = new ArrayList<>();
        List<Reason> reasons = new ArrayList<>();

        boolean activityOk = p.getPurpose() == a.activity();
        comps.add(new Component("Fəaliyyət növü", activityOk ? 30 : 0, 30));
        if (!activityOk) reasons.add(new Reason(false, "Təyinat uyğun deyil: " + labels.label(p.getPurpose())));

        long est = Math.round(p.getAreaHa() * (firstCrop == null ? 0 : firstCrop.getInvestmentPerHa()));
        long budget = a.budget() >= BUDGET_NO_LIMIT ? Long.MAX_VALUE : a.budget();
        int budgetPts = est <= budget ? 25 : est <= budget * 1.25 ? 12 : 0;
        comps.add(new Component("Büdcə", budgetPts, 25));
        reasons.add(est <= budget
                ? new Reason(true, "Büdcənizə sığır (təxmini " + money(est) + ")")
                : new Reason(false, "Büdcəni ~" + Math.round((est / (double) budget - 1) * 100) + "% aşır"));

        double[] range = range(a.size());
        double area = p.getAreaHa();
        int sizePts = area >= range[0] && area <= range[1] ? 20 : area >= range[0] * 0.8 && area <= range[1] * 1.2 ? 10 : 0;
        comps.add(new Component("Sahə ölçüsü", sizePts, 20));

        int deminingPts = switch (p.getDemining()) { case CLEARED -> 10; case PARTIALLY_CLEARED -> 3; case IN_PROGRESS -> 0; };
        comps.add(new Component("Minalardan təmizlənmə", deminingPts, 10));

        int roadPts = p.getRoadKm() <= 5 ? 2 : p.getRoadKm() <= 15 ? 1 : 0;
        comps.add(new Component("İnfrastruktur", (p.isIrrigation() ? 5 : 0) + (p.isElectricity() ? 3 : 0) + roadPts, 10));
        reasons.add(new Reason(p.isIrrigation(), p.isIrrigation() ? "Suvarma var" : "Suvarma yoxdur"));
        reasons.add(new Reason(p.getRoadKm() <= 5, "Yola " + km(p.getRoadKm()) + " km məsafə"));
        reasons.add(switch (p.getDemining()) {
            case CLEARED -> new Reason(true, "Minalardan tam təmizlənib");
            case PARTIALLY_CLEARED -> new Reason(false, "Qismən təmizlənib — əlavə yoxlama lazımdır");
            case IN_PROGRESS -> new Reason(false, "Minalardan təmizlənmə davam edir");
        });

        boolean regionOk = a.district() == null || a.district().isBlank() || a.district().equals(p.getDistrict());
        comps.add(new Component("Region", regionOk ? 5 : 0, 5));

        int total = comps.stream().mapToInt(Component::points).sum();
        return new Match(p, null, null, total, comps, reasons);
    }

    static double[] range(String size) {
        return switch (size) {
            case "5-20" -> new double[]{5, 20};
            case "20-50" -> new double[]{20, 50};
            case "50-150" -> new double[]{50, 150};
            case "150+" -> new double[]{150, Double.MAX_VALUE / 2};
            default -> throw new IllegalArgumentException("size");
        };
    }

    static String firstRecommended(Plot p) {
        return p.getRecommendedCrops() == null || p.getRecommendedCrops().isBlank() ? null : p.getRecommendedCrops().split(",")[0].trim();
    }

    private static String money(long v) {
        return String.format("%,d", v).replace(',', ' ') + " AZN";
    }

    private static String km(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }
}
