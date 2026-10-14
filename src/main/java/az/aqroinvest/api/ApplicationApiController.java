package az.aqroinvest.api;

import az.aqroinvest.api.ApiDtos.ApplicationStatusDto;
import az.aqroinvest.api.ApiDtos.DecisionDto;
import az.aqroinvest.domain.Application;
import az.aqroinvest.domain.ApplicationStatus;
import az.aqroinvest.repo.ApplicationRepository;
import az.aqroinvest.web.StatusLabels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicationApiController {

    private final ApplicationRepository applications;
    private final StatusLabels labels;

    @GetMapping("/api/v1/applications/{number}/status")
    @Tag(name = "Müraciətlər")
    @Operation(summary = "Müraciətin statusu (sahibi və ya işçi; sessiya ilə)")
    public ApplicationStatusDto status(@PathVariable String number, Authentication auth) {
        Application a = applications.findByNumber(number).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean staff = auth.getAuthorities().stream().anyMatch(g -> !g.getAuthority().equals("ROLE_INVESTOR"));
        if (!staff && !a.getInvestor().getEmail().equalsIgnoreCase(auth.getName()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new ApplicationStatusDto(a.getNumber(), a.getStatus().name(), labels.label(a.getStatus()),
                a.getDecision() == null ? null : a.getDecision().name(), a.getEktisRef(), a.getUpdatedAt());
    }

    @GetMapping("/api/v1/external/decisions")
    @Tag(name = "Xarici sistemlər")
    @Operation(summary = "Komissiya qərarları (X-API-Key başlığı tələb olunur)", security = @SecurityRequirement(name = "apiKey"))
    public List<DecisionDto> decisions() {
        return applications.findByStatusOrderByUpdatedAtDesc(ApplicationStatus.DECIDED).stream()
                .map(a -> new DecisionDto(a.getNumber(), a.getDecision().name(), a.getDecisionReason(), a.getInvestmentAzn(),
                        a.getJobs(), a.getPlot().getCode(), a.getPlot().getDistrict(), a.getCompetition().getCode(),
                        a.getEktisRef(), a.getUpdatedAt())).toList();
    }
}
