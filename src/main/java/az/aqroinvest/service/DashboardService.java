package az.aqroinvest.service;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.ApplicationRepository;
import az.aqroinvest.repo.CompetitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** Staff dashboard numbers, computed from applications (small data — in memory is fine). */
@Service
@RequiredArgsConstructor
public class DashboardService {

    public record DistrictRow(String district, long applications, long investment, long barPercent) {}

    public record Dashboard(long totalApplications, long totalInvestment, long expectedJobs, long approved,
                            long publishedCompetitions, Map<ApplicationStatus, Long> byStatus, List<DistrictRow> byDistrict) {}

    /** Public home page: stats strip counts + one sample plot (soonest open deadline) for the hero card. */
    public record Home(long openPlots, long totalHa, long activeCompetitions, long activeAuctions, long districts,
                       long approvedProjects, PlotService.PlotCard sample) {}

    private final ApplicationRepository applications;
    private final CompetitionRepository competitions;
    private final PlotService plotService;
    private final az.aqroinvest.repo.FarmProjectRepository farms;
    private final az.aqroinvest.auction.AuctionService auctionService;

    public Home home() {
        List<PlotService.PlotCard> cards = plotService.search(PlotService.PlotFilter.none());
        long activeAuctions = auctionService.list().stream()
                .filter(a -> a.getStatus() == AuctionStatus.LIVE || a.getStatus() == AuctionStatus.SCHEDULED).count();
        return new Home(
                cards.stream().filter(c -> c.plot().getStatus() == PlotStatus.AVAILABLE || c.plot().getStatus() == PlotStatus.IN_AUCTION).count(),
                Math.round(cards.stream().mapToDouble(c -> c.plot().getAreaHa()).sum()),
                cards.stream().map(PlotService.PlotCard::competition).filter(Objects::nonNull).distinct().count(),
                activeAuctions,
                cards.stream().map(c -> c.plot().getDistrict()).distinct().count(),
                farms.count(),
                cards.stream().filter(c -> c.competition() != null)
                        .min(Comparator.comparing(PlotService.PlotCard::daysLeft).thenComparing(c -> c.plot().getCode())).orElse(null));
    }

    public Dashboard build() {
        List<Application> all = applications.findAll();
        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) byStatus.put(s, 0L);
        Map<String, long[]> districts = new TreeMap<>();
        for (Application a : all) {
            byStatus.merge(a.getStatus(), 1L, Long::sum);
            long[] d = districts.computeIfAbsent(a.getPlot().getDistrict(), k -> new long[2]);
            d[0]++;
            d[1] += a.getInvestmentAzn();
        }
        long maxInv = Math.max(1, districts.values().stream().mapToLong(d -> d[1]).max().orElse(1));
        List<DistrictRow> rows = districts.entrySet().stream()
                .map(e -> new DistrictRow(e.getKey(), e.getValue()[0], e.getValue()[1], Math.max(2, e.getValue()[1] * 100 / maxInv)))
                .sorted(Comparator.comparingLong(DistrictRow::investment).reversed()).toList();
        return new Dashboard(
                all.size(),
                all.stream().mapToLong(Application::getInvestmentAzn).sum(),
                // jobs promised by applications that are not rejected
                all.stream().filter(a -> a.getDecision() != Decision.REJECTED).mapToLong(Application::getJobs).sum(),
                all.stream().filter(a -> a.getDecision() == Decision.APPROVED).count(),
                competitions.countByStatus(CompetitionStatus.PUBLISHED),
                byStatus, rows);
    }
}
