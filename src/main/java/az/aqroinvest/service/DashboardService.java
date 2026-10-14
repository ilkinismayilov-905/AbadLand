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

    private final ApplicationRepository applications;
    private final CompetitionRepository competitions;

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
