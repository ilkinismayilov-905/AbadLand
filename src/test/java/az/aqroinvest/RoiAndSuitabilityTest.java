package az.aqroinvest;

import az.aqroinvest.domain.*;
import az.aqroinvest.service.RoiService;
import az.aqroinvest.service.SuitabilityService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoiAndSuitabilityTest {

    static Crop crop(String code, int inv, int rev, int opex, int firstHarvest, int frost, int maxElev, WaterNeed water) {
        Crop c = new Crop();
        c.setCode(code);
        c.setNameAz(code);
        c.setInvestmentPerHa(inv);
        c.setRevenuePerHa(rev);
        c.setOpexPerHa(opex);
        c.setFirstHarvestYear(firstHarvest);
        c.setYieldTPerHa(2.0);
        c.setFrostToleranceC(frost);
        c.setMaxElevationM(maxElev);
        c.setWaterNeed(water);
        return c;
    }

    static Plot plot(double area, int fee, boolean irrigation, String recommended) {
        Plot p = new Plot();
        p.setAreaHa(area);
        p.setFeePerHa(fee);
        p.setIrrigation(irrigation);
        p.setRecommendedCrops(recommended);
        return p;
    }

    static PlotClimate climate(int elev, double absMin, double precip) {
        PlotClimate c = new PlotClimate();
        c.setElevationM(elev);
        c.setAbsMinTempC(absMin);
        c.setPrecipMm(precip);
        return c;
    }

    final Crop hazelnut = crop("HAZELNUT", 9000, 7000, 2500, 4, -20, 1500, WaterNeed.MEDIUM);
    final Crop pomegranate = crop("POMEGRANATE", 10000, 9000, 3500, 3, -12, 1000, WaterNeed.MEDIUM);

    @Test
    void roiPaybackAndScenariosForFzl001Hazelnut() {
        RoiService.Roi roi = new RoiService().calculate(plot(42.5, 80, true, "HAZELNUT"), hazelnut, climate(420, -9.5, 390), 78);
        assertThat(roi.investment()).isEqualTo(382_500);
        assertThat(roi.annualNet()).isEqualTo(187_850);           // 42.5 × (7000 − 2500 − 80)
        assertThat(roi.payback()).isEqualTo(5.0);                 // 3 + 382 500 / 187 850
        assertThat(roi.yieldTons()).isEqualTo(66.3);              // 42.5 × 2.0 × 0.78
        assertThat(roi.scenarios()).extracting(RoiService.Scenario::name).containsExactly("Pessimist", "Baza", "Optimist");
        RoiService.Scenario pess = roi.scenarios().get(0), base = roi.scenarios().get(1), opt = roi.scenarios().get(2);
        assertThat(pess.firstHarvestYear()).isEqualTo(5);
        assertThat(pess.payback()).isEqualTo(7.9);                // 4 + 382 500 / 98 600
        assertThat(base.cumulative10()).isEqualTo(-382_500 + 187_850L * 7);
        assertThat(opt.payback()).isEqualTo(4.5);
        assertThat(roi.risks()).extracting(RoiService.Risk::level)
                .containsExactly(RiskLevel.LOW, RiskLevel.LOW, RiskLevel.LOW);
    }

    @Test
    void roiWithNonPositiveNetHasNoPaybackAndHighRisks() {
        Crop apple = crop("APPLE", 25000, 5000, 7000, 3, -30, 2000, WaterNeed.HIGH);
        RoiService.Roi roi = new RoiService().calculate(plot(10, 50, false, ""), apple, climate(500, -10, 250), null);
        assertThat(roi.payback()).isNull();
        assertThat(roi.risks()).extracting(RoiService.Risk::level)
                .containsExactly(RiskLevel.LOW, RiskLevel.HIGH, RiskLevel.HIGH);
    }

    @Test
    void frostRiskMediumWithinThreeDegrees() {
        RoiService.Roi roi = new RoiService().calculate(plot(10, 50, true, ""), pomegranate, climate(500, -10, 600), null);
        assertThat(roi.risks().get(0).level()).isEqualTo(RiskLevel.MEDIUM);
    }

    final SuitabilityService rules = new SuitabilityService(null, null, null, null, null, null);

    @Test
    void suitabilityFrostAndElevationPenalties() {
        // Laçın-like: -17 °C < -12 and 1150 m > 1000 m, not recommended
        SuitabilityService.RuleScore r = rules.ruleScore(plot(30, 50, true, "WALNUT"), climate(1150, -17, 620), pomegranate);
        assertThat(r.score()).isEqualTo(100 - 40 - 30 - 20);
        assertThat(r.reason()).contains("Şaxta").contains("Hündürlük");
    }

    @Test
    void suitabilityRecommendedCropWithGoodClimateIsFull() {
        assertThat(rules.ruleScore(plot(42.5, 80, true, "HAZELNUT,POMEGRANATE"), climate(420, -9.5, 390), pomegranate).score()).isEqualTo(100);
    }

    @Test
    void suitabilityWaterPenaltiesWithoutIrrigation() {
        // MEDIUM water need, no irrigation (−15), dry (−15)
        assertThat(rules.ruleScore(plot(35, 60, false, "POMEGRANATE"), climate(380, -9, 250), pomegranate).score()).isEqualTo(70);
    }

    @Test
    void suitabilityWithoutClimateUsesRecommendedList() {
        assertThat(rules.ruleScore(plot(10, 50, true, "POMEGRANATE"), null, pomegranate).score()).isEqualTo(70);
        assertThat(rules.ruleScore(plot(10, 50, true, "WALNUT"), null, pomegranate)).isNull();
    }
}
