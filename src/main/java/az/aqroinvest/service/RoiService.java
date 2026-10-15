package az.aqroinvest.service;

import az.aqroinvest.domain.*;
import az.aqroinvest.web.I18n;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** ROI + prediction from SPEC. Pure calculation, test values ("Sınaq dəyəri"). */
@Service
public class RoiService {

    public record Scenario(String name, double revenueFactor, int firstHarvestYear, long annualNet, Double payback, long cumulative10) {}

    public record Risk(String name, RiskLevel level, String text) {}

    public record Roi(Crop crop, long investment, long annualNet, Double payback, double yieldTons,
                      List<Scenario> scenarios, List<Risk> risks) {
        /** Largest |10-year cumulative| — scale for CSS bars. */
        public long maxAbsCumulative() {
            return Math.max(1, scenarios.stream().mapToLong(s -> Math.abs(s.cumulative10())).max().orElse(1));
        }

        /** CSS bar width 2–100 % for a scenario. */
        public long barPercent(Scenario s) {
            return Math.max(2, Math.abs(s.cumulative10()) * 100 / maxAbsCumulative());
        }
    }

    /** @param suitability effective score 0–100 or null (default 0.8 factor) */
    public Roi calculate(Plot plot, Crop crop, PlotClimate climate, Integer suitability) {
        double area = plot.getAreaHa();
        long investment = Math.round(area * crop.getInvestmentPerHa());
        Scenario base = scenario(I18n.t("plots.roi.base"), plot, crop, 1.0, 0, investment);
        List<Scenario> scenarios = List.of(
                scenario(I18n.t("plots.roi.pessimist"), plot, crop, 0.7, 1, investment),
                base,
                scenario(I18n.t("plots.roi.optimist"), plot, crop, 1.2, 0, investment));
        double factor = suitability == null ? 0.8 : suitability / 100.0;
        double yield = Math.round(area * crop.getYieldTPerHa() * factor * 10) / 10.0;
        return new Roi(crop, investment, base.annualNet(), base.payback(), yield, scenarios, risks(plot, crop, climate));
    }

    static Scenario scenario(String name, Plot plot, Crop crop, double revenueFactor, int extraYears, long investment) {
        int firstHarvest = crop.getFirstHarvestYear() + extraYears;
        long annualNet = Math.round(plot.getAreaHa() * (crop.getRevenuePerHa() * revenueFactor - crop.getOpexPerHa() - plot.getFeePerHa()));
        Double payback = annualNet <= 0 ? null : Math.round(((firstHarvest - 1) + (double) investment / annualNet) * 10) / 10.0;
        int productiveYears = Math.max(0, 10 - (firstHarvest - 1));
        long cumulative = -investment + annualNet * productiveYears;
        return new Scenario(name, revenueFactor, firstHarvest, annualNet, payback, cumulative);
    }

    static List<Risk> risks(Plot plot, Crop crop, PlotClimate climate) {
        List<Risk> risks = new ArrayList<>();
        if (climate != null && climate.getAbsMinTempC() != null) {
            double min = climate.getAbsMinTempC();
            RiskLevel l = min < crop.getFrostToleranceC() ? RiskLevel.HIGH
                    : min < crop.getFrostToleranceC() + 3 ? RiskLevel.MEDIUM : RiskLevel.LOW;
            risks.add(new Risk(I18n.t("plots.roi.frost"), l, I18n.t("plots.roi.frostText",
                    String.format(java.util.Locale.ROOT, "%.0f", min), String.valueOf(crop.getFrostToleranceC()))));
        }
        if (climate != null && climate.getPrecipMm() != null) {
            double p = climate.getPrecipMm();
            RiskLevel l = plot.isIrrigation() ? RiskLevel.LOW : p < 300 ? RiskLevel.HIGH : p < 500 ? RiskLevel.MEDIUM : RiskLevel.LOW;
            risks.add(new Risk(I18n.t("plots.roi.drought"), l, I18n.t(plot.isIrrigation() ? "plots.roi.droughtTextIrrigated" : "plots.roi.droughtText",
                    String.format(java.util.Locale.ROOT, "%.0f", p))));
        }
        RiskLevel w = crop.getWaterNeed() == WaterNeed.HIGH && !plot.isIrrigation() ? RiskLevel.HIGH : RiskLevel.LOW;
        risks.add(new Risk(I18n.t("plots.roi.water"), w, I18n.t(plot.isIrrigation() ? "plots.roi.waterTextIrrigated" : "plots.roi.waterText",
                I18n.t("plots.roi.waterNeed." + crop.getWaterNeed().name()))));
        return risks;
    }
}
