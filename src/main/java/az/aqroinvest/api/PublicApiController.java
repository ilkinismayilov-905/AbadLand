package az.aqroinvest.api;

import az.aqroinvest.api.ApiDtos.*;
import az.aqroinvest.domain.Competition;
import az.aqroinvest.domain.CompetitionStatus;
import az.aqroinvest.domain.Plot;
import az.aqroinvest.repo.CompetitionRepository;
import az.aqroinvest.repo.PlotClimateRepository;
import az.aqroinvest.repo.PlotRepository;
import az.aqroinvest.service.PlotService;
import az.aqroinvest.service.SuitabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Açıq API", description = "Sahələr və müsabiqələr")
public class PublicApiController {

    private final PlotRepository plots;
    private final PlotClimateRepository climates;
    private final CompetitionRepository competitions;
    private final PlotService plotService;
    private final SuitabilityService suitability;

    @GetMapping("/plots")
    @Operation(summary = "Bütün sahələr")
    public List<PlotDto> plots() {
        Map<Long, Competition> open = plotService.openCompetitionByPlot();
        return plots.findAll(Sort.by("code")).stream().map(p -> dto(p, open.get(p.getId()))).toList();
    }

    @GetMapping("/plots/{code}")
    @Operation(summary = "Sahə pasportu (iqlim və bitki uyğunluğu ilə)")
    public PlotDetailDto plot(@PathVariable String code) {
        Plot p = plots.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ClimateDto climate = climates.findByPlot(p).map(c -> new ClimateDto(c.getElevationM(), c.getMeanTempC(),
                c.getAbsMinTempC(), c.getFrostDays(), c.getPrecipMm())).orElse(null);
        List<SuitabilityDto> suits = suitability.forPlot(p).stream().map(s -> new SuitabilityDto(s.crop().getCode(),
                s.crop().getNameAz(), s.score(), s.source().name(), s.reason())).toList();
        return new PlotDetailDto(dto(p, plotService.openCompetitionByPlot().get(p.getId())), p.getVillage(), p.getWaterSource(),
                p.getSoilType(), p.getLeaseYears(), p.getFeePerHa(), p.getDescription(), climate, suits);
    }

    @GetMapping("/competitions")
    @Operation(summary = "Dərc edilmiş müsabiqələr")
    public List<CompetitionDto> competitions() {
        return competitions.findByStatusOrderByDeadlineAsc(CompetitionStatus.PUBLISHED).stream()
                .map(c -> new CompetitionDto(c.getCode(), c.getTitle(), c.getMinInvestment(), c.getDeadline(),
                        PlotService.daysLeft(c), c.getPlots().stream().map(Plot::getCode).sorted().toList())).toList();
    }

    static PlotDto dto(Plot p, Competition open) {
        return new PlotDto(p.getCode(), p.getName(), p.getDistrict(), p.getEconomicRegion().name(), p.getAreaHa(),
                p.getPurpose().name(), p.getDemining().name(), p.isIrrigation(), p.isElectricity(), p.getRoadKm(),
                p.getLat(), p.getLng(), open == null ? null : open.getCode());
    }
}
