package az.aqroinvest;

import az.aqroinvest.domain.*;
import az.aqroinvest.service.MatchingService;
import az.aqroinvest.service.MatchingService.Answers;
import az.aqroinvest.service.MatchingService.Reason;
import az.aqroinvest.web.StatusLabels;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingServiceTest {

    final MatchingService service = new MatchingService(null, null, new StatusLabels());

    static Plot plot(String district, double area, DeminingStatus demining, boolean irrigation, boolean electricity, double roadKm) {
        Plot p = new Plot();
        p.setDistrict(district);
        p.setAreaHa(area);
        p.setPurpose(ActivityType.HORTICULTURE);
        p.setDemining(demining);
        p.setIrrigation(irrigation);
        p.setElectricity(electricity);
        p.setRoadKm(roadKm);
        return p;
    }

    static Crop crop(int investmentPerHa) {
        Crop c = new Crop();
        c.setInvestmentPerHa(investmentPerHa);
        return c;
    }

    final Answers demo = new Answers(ActivityType.HORTICULTURE, 500_000L, "20-50", "Füzuli", "3-10");

    @Test
    void perfectPlotScores100() {
        // FZL-001: 42.5 ha × 9 000 (Fındıq) = 382 500 ≤ 500 000
        var m = service.score(plot("Füzuli", 42.5, DeminingStatus.CLEARED, true, true, 3.2), crop(9000), demo);
        assertThat(m.score()).isEqualTo(100);
        assertThat(m.reasons()).extracting(Reason::text).contains("Büdcənizə sığır (təxmini 382 500 AZN)", "Minalardan tam təmizlənib");
    }

    @Test
    void budgetBoundaries() {
        Plot p = plot("Füzuli", 50, DeminingStatus.CLEARED, true, true, 1);
        assertThat(budgetPoints(p, 10_000)).isEqualTo(25);   // 500 000 = budget
        assertThat(budgetPoints(p, 12_500)).isEqualTo(12);   // 625 000 = 1.25 × budget
        assertThat(budgetPoints(p, 12_501)).isEqualTo(0);    // just over 1.25 ×
        var over = service.score(p, crop(11_500), demo);     // 575 000 → ~15% over
        assertThat(over.reasons()).extracting(Reason::text).contains("Büdcəni ~15% aşır");
        assertThat(service.score(p, crop(1_000_000), new Answers(ActivityType.HORTICULTURE, MatchingService.BUDGET_NO_LIMIT, "20-50", "", "0"))
                .components().get(1).points()).isEqualTo(25); // 2M+ = no limit
    }

    int budgetPoints(Plot p, int perHa) {
        return service.score(p, crop(perHa), demo).components().get(1).points();
    }

    @Test
    void fzl002ReasonsAndScore() {
        var m = service.score(plot("Füzuli", 35, DeminingStatus.CLEARED, false, true, 12), crop(10_000), demo);
        assertThat(m.reasons()).extracting(Reason::text).contains("Yola 12 km məsafə", "Suvarma yoxdur");
        assertThat(m.reasons()).filteredOn(r -> r.text().equals("Suvarma yoxdur")).allMatch(r -> !r.ok());
        assertThat(m.score()).isEqualTo(30 + 25 + 20 + 10 + (0 + 3 + 1) + 5);
    }

    @Test
    void sizeNearRangeActivityMismatchAndRegion() {
        Plot p = plot("Laçın", 55, DeminingStatus.PARTIALLY_CLEARED, true, false, 20);
        p.setPurpose(ActivityType.LIVESTOCK);
        var m = service.score(p, crop(1000), demo);
        assertThat(m.components()).extracting(MatchingService.Component::points)
                .containsExactly(0, 25, 10, 3, 5, 0);           // 55 ha within 20% of 50; road > 15 km → 0
        assertThat(m.reasons().get(0)).isEqualTo(new Reason(false, "Təyinat uyğun deyil: Heyvandarlıq"));
    }

    @Test
    void otherActivityMatchesOnlyOtherPlots() {
        var other = new Answers(ActivityType.OTHER, 500_000L, "20-50", "", "0", null, "arıçılıq", null, null);
        assertThat(other.invalid()).isNull();
        Plot hort = plot("Füzuli", 30, DeminingStatus.CLEARED, true, true, 1);
        Plot bees = plot("Füzuli", 30, DeminingStatus.CLEARED, true, true, 1);
        bees.setPurpose(ActivityType.OTHER);
        assertThat(service.score(bees, crop(1000), other).components().get(0).points()).isEqualTo(30);
        assertThat(service.score(hort, crop(1000), other).components().get(0).points()).isEqualTo(0);
        assertThat(new Answers(ActivityType.OTHER, 500_000L, "20-50", "", "0", null, " ", null, null).invalid())
                .isEqualTo("Fəaliyyət növünü yazın.");
        assertThat(new Answers(ActivityType.OTHER, 500_000L, "20-50", "", "0", null, "a".repeat(101), null, null).invalid())
                .contains("100 simvol");
    }

    @Test
    void customBudget() {
        Plot p = plot("Füzuli", 20, DeminingStatus.CLEARED, true, true, 1); // 20 ha × 10 000 = 200 000
        var fits = new Answers(ActivityType.HORTICULTURE, MatchingService.BUDGET_CUSTOM, "5-20", "", "0", "200 000", null, null, null);
        assertThat(fits.invalid()).isNull();
        assertThat(fits.effectiveBudget()).isEqualTo(200_000);
        assertThat(service.score(p, crop(10_000), fits).components().get(1).points()).isEqualTo(25);
        var tight = new Answers(ActivityType.HORTICULTURE, MatchingService.BUDGET_CUSTOM, "5-20", "", "0", "170000", null, null, null);
        assertThat(service.score(p, crop(10_000), tight).components().get(1).points()).isEqualTo(12); // 200 000 ≤ 1.25 × 170 000
        assertThat(new Answers(ActivityType.HORTICULTURE, 0L, "5-20", "", "0", "9 000", null, null, null).invalid()).contains("10 000");
        assertThat(new Answers(ActivityType.HORTICULTURE, 0L, "5-20", "", "0", "15 500", null, null, null).invalid()).contains("1 000");
        assertThat(new Answers(ActivityType.HORTICULTURE, 0L, "5-20", "", "0", "abc", null, null, null).invalid()).isEqualTo("Büdcə məbləğini yazın.");
    }

    @Test
    void customAreaRangeUsesSameTolerance() {
        var custom = new Answers(ActivityType.HORTICULTURE, 500_000L, MatchingService.CUSTOM_SIZE, "", "0", null, null, "10", "30");
        assertThat(custom.invalid()).isNull();
        assertThat(sizePoints(30, custom)).isEqualTo(20);   // in range
        assertThat(sizePoints(36, custom)).isEqualTo(10);   // ≤ 30 × 1.2
        assertThat(sizePoints(8, custom)).isEqualTo(10);    // ≥ 10 × 0.8
        assertThat(sizePoints(37, custom)).isEqualTo(0);
        assertThat(sizePoints(3, new Answers(ActivityType.HORTICULTURE, 500_000L, "1-5", "", "0"))).isEqualTo(20);
        assertThat(new Answers(ActivityType.HORTICULTURE, 500_000L, "custom", "", "0", null, null, "0,2", "3").invalid()).contains("0,5");
        assertThat(new Answers(ActivityType.HORTICULTURE, 500_000L, "custom", "", "0", null, null, "20", "10").invalid()).contains("Maksimum");
        assertThat(new Answers(ActivityType.HORTICULTURE, 500_000L, "custom", "", "0", null, null, "2,5", "4").invalid()).isNull();
    }

    int sizePoints(double area, Answers a) {
        return service.score(plot("Füzuli", area, DeminingStatus.CLEARED, true, true, 1), crop(1), a).components().get(2).points();
    }
}
