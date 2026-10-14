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
}
