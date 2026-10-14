package az.aqroinvest.service;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.CompetitionRepository;
import az.aqroinvest.repo.PlotRepository;
import az.aqroinvest.service.SuitabilityService.Suit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static az.aqroinvest.service.AuditService.BAKU;

@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(readOnly = true) // lazy collections also outside web requests
public class PlotService {

    /** Catalog/map filter; all fields optional. fruit = crop code for "Hansı meyvə üçün?". */
    public record PlotFilter(EconomicRegion region, String district, ActivityType activity, Double minHa, Double maxHa,
                             DeminingStatus demining, String fruit) {}

    /** A plot with what the catalog/map shows. fruitScore set only when the fruit filter is active. */
    public record PlotCard(Plot plot, List<Suit> suitability, Competition competition, Long daysLeft, Integer fruitScore) {
        public List<Suit> top3() {
            return suitability.stream().limit(3).toList();
        }
    }

    public static final int FRUIT_MIN_SCORE = 60;

    private final PlotRepository plots;
    private final CompetitionRepository competitions;
    private final SuitabilityService suitability;

    public List<PlotCard> search(PlotFilter f) {
        Map<Long, List<Suit>> suits = suitability.forAllPlots();
        Map<Long, Competition> comps = openCompetitionByPlot();
        List<PlotCard> cards = new ArrayList<>();
        for (Plot p : plots.findAll(org.springframework.data.domain.Sort.by("code"))) {
            if (f.region() != null && p.getEconomicRegion() != f.region()) continue;
            if (notBlank(f.district()) && !p.getDistrict().equals(f.district())) continue;
            if (f.activity() != null && p.getPurpose() != f.activity()) continue;
            if (f.minHa() != null && p.getAreaHa() < f.minHa()) continue;
            if (f.maxHa() != null && p.getAreaHa() > f.maxHa()) continue;
            if (f.demining() != null && p.getDemining() != f.demining()) continue;
            List<Suit> s = suits.getOrDefault(p.getId(), List.of());
            Integer fruitScore = null;
            if (notBlank(f.fruit())) {
                fruitScore = s.stream().filter(x -> x.crop().getCode().equals(f.fruit())).map(Suit::score).findFirst().orElse(null);
                if (fruitScore == null || fruitScore < FRUIT_MIN_SCORE) continue;
            }
            Competition c = comps.get(p.getId());
            cards.add(new PlotCard(p, s, c, c == null ? null : daysLeft(c), fruitScore));
        }
        if (notBlank(f.fruit())) cards.sort(Comparator.comparing(PlotCard::fruitScore).reversed());
        return cards;
    }

    public PlotCard card(Plot p) {
        Competition c = openCompetitionByPlot().get(p.getId());
        return new PlotCard(p, suitability.forPlot(p), c, c == null ? null : daysLeft(c), null);
    }

    public List<String> districts() {
        return plots.findAll().stream().map(Plot::getDistrict).distinct().sorted().toList();
    }

    /** PUBLISHED competitions with deadline today or later, by plot id. */
    public Map<Long, Competition> openCompetitionByPlot() {
        LocalDate today = LocalDate.now(BAKU);
        Map<Long, Competition> m = new HashMap<>();
        for (Competition c : competitions.findByStatusOrderByDeadlineAsc(CompetitionStatus.PUBLISHED))
            if (!c.getDeadline().isBefore(today)) c.getPlots().forEach(p -> m.putIfAbsent(p.getId(), c));
        return m;
    }

    public static long daysLeft(Competition c) {
        return ChronoUnit.DAYS.between(LocalDate.now(BAKU), c.getDeadline());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
