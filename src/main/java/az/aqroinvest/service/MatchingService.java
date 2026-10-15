package az.aqroinvest.service;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.CropRepository;
import az.aqroinvest.web.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** Matching score 0–100 from SPEC ("Matching score"). */
@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(readOnly = true) // lazy collections also outside web requests
public class MatchingService {

    /**
     * Quiz answers. budget ≥ 2 000 000 means "2M+" (no upper limit), budget = BUDGET_CUSTOM uses budgetCustom;
     * size = "1-5" | "5-20" | "20-50" | "50-150" | "150+" | "custom" (minHa–maxHa); district "" = no preference;
     * otherText = what the investor typed for activity OTHER. Typed values stay strings so "100 000" / "2,5" parse leniently.
     */
    public record Answers(ActivityType activity, Long budget, String size, String district, String experience,
                          String budgetCustom, String otherText, String minHa, String maxHa) {
        public Answers(ActivityType activity, Long budget, String size, String district, String experience) {
            this(activity, budget, size, district, experience, null, null, null, null);
        }

        public boolean complete() {
            return activity != null && budget != null && size != null && !size.isBlank() && district != null
                    && experience != null && !experience.isBlank();
        }

        /** First problem with the typed values (current language), or null when the answers can be scored. */
        public String invalid() {
            if (budget != null && budget == BUDGET_CUSTOM) {
                Long v = parseLong(budgetCustom);
                if (v == null) return I18n.t("plots.match.budgetMissing");
                if (v < BUDGET_CUSTOM_MIN) return I18n.t("plots.match.budgetMin");
                if (v % 1000 != 0) return I18n.t("plots.match.budgetStep");
            }
            if (activity == ActivityType.OTHER) {
                if (otherText == null || otherText.isBlank()) return I18n.t("plots.match.activityMissing");
                if (otherText.strip().length() > 100) return I18n.t("plots.match.activityTooLong");
            }
            if (CUSTOM_SIZE.equals(size)) {
                Double lo = parseHa(minHa), hi = parseHa(maxHa);
                if (lo == null || hi == null) return I18n.t("plots.match.sizeMissing");
                if (lo < 0.5) return I18n.t("plots.match.sizeMin");
                if (hi < lo) return I18n.t("plots.match.sizeOrder");
            }
            return null;
        }

        /** Budget in AZN used for scoring. */
        public long effectiveBudget() {
            return budget == BUDGET_CUSTOM ? parseLong(budgetCustom) : budget;
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
    /** Budget option "Digər məbləğ": the amount comes from Answers.budgetCustom. */
    public static final long BUDGET_CUSTOM = 0;
    public static final long BUDGET_CUSTOM_MIN = 10_000;
    public static final String CUSTOM_SIZE = "custom";

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
        comps.add(new Component(I18n.t("plots.match.cActivity"), activityOk ? 30 : 0, 30));
        if (!activityOk) reasons.add(new Reason(false, I18n.t("plots.match.purposeMismatch", labels.label(p.getPurpose()))));

        long est = Math.round(p.getAreaHa() * (firstCrop == null ? 0 : firstCrop.getInvestmentPerHa()));
        long budget = a.effectiveBudget() >= BUDGET_NO_LIMIT ? Long.MAX_VALUE : a.effectiveBudget();
        int budgetPts = est <= budget ? 25 : est <= budget * 1.25 ? 12 : 0;
        comps.add(new Component(I18n.t("plots.match.cBudget"), budgetPts, 25));
        reasons.add(est <= budget
                ? new Reason(true, I18n.t("plots.match.withinBudget", money(est)))
                : new Reason(false, I18n.t("plots.match.overBudget", String.valueOf(Math.round((est / (double) budget - 1) * 100)))));

        double[] range = range(a);
        double area = p.getAreaHa();
        int sizePts = area >= range[0] && area <= range[1] ? 20 : area >= range[0] * 0.8 && area <= range[1] * 1.2 ? 10 : 0;
        comps.add(new Component(I18n.t("plots.match.cSize"), sizePts, 20));

        int deminingPts = switch (p.getDemining()) { case CLEARED -> 10; case PARTIALLY_CLEARED -> 3; case IN_PROGRESS -> 0; };
        comps.add(new Component(I18n.t("plots.compare.demining"), deminingPts, 10));

        int roadPts = p.getRoadKm() <= 5 ? 2 : p.getRoadKm() <= 15 ? 1 : 0;
        comps.add(new Component(I18n.t("plots.match.cInfra"), (p.isIrrigation() ? 5 : 0) + (p.isElectricity() ? 3 : 0) + roadPts, 10));
        reasons.add(new Reason(p.isIrrigation(), I18n.t(p.isIrrigation() ? "plots.match.irrigationYes" : "plots.match.irrigationNo")));
        reasons.add(new Reason(p.getRoadKm() <= 5, I18n.t("plots.match.road", km(p.getRoadKm()))));
        reasons.add(switch (p.getDemining()) {
            case CLEARED -> new Reason(true, I18n.t("plots.match.cleared"));
            case PARTIALLY_CLEARED -> new Reason(false, I18n.t("plots.match.partial"));
            case IN_PROGRESS -> new Reason(false, I18n.t("plots.match.inProgress"));
        });

        boolean regionOk = a.district() == null || a.district().isBlank() || a.district().equals(p.getDistrict());
        comps.add(new Component(I18n.t("plots.match.cRegion"), regionOk ? 5 : 0, 5));

        int total = comps.stream().mapToInt(Component::points).sum();
        return new Match(p, null, null, total, comps, reasons);
    }

    static double[] range(Answers a) {
        return switch (a.size()) {
            case "1-5" -> new double[]{1, 5};
            case CUSTOM_SIZE -> new double[]{parseHa(a.minHa()), parseHa(a.maxHa())};
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

    /** "100 000" / "100000" → 100000; null when empty or not a number. */
    static Long parseLong(String s) {
        if (s == null) return null;
        String digits = s.replaceAll("[\\s\u00a0]", "");
        return digits.matches("\\d{1,12}") ? Long.parseLong(digits) : null;
    }

    /** "2,5" / "2.5" → 2.5; null when empty or not a number. */
    static Double parseHa(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            double v = Double.parseDouble(s.strip().replace(',', '.'));
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String money(long v) {
        return String.format("%,d", v).replace(',', ' ') + " AZN";
    }

    private static String km(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }
}
