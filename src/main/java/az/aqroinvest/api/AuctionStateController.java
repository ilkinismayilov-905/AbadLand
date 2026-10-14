package az.aqroinvest.api;

import az.aqroinvest.auction.AuctionService;
import az.aqroinvest.domain.Auction;
import az.aqroinvest.domain.Bid;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.web.StatusLabels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static az.aqroinvest.service.AuditService.BAKU;

/** Live auction state for polling (no websockets). Bidders appear only as participant numbers. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Hərraclar")
public class AuctionStateController {

    public record BidDto(int participantNo, long amount, long at, boolean extended) {}

    public record MeDto(int participantNo, boolean depositConfirmed, boolean isHighest) {}

    public record StateDto(String status, String statusLabel, long startPrice, Long currentPrice, long step, long minNextBid,
                           long startsAt, long endsAt, long serverNow, long participants, int extensions, List<BidDto> bids, MeDto me) {}

    private final AuctionService auctions;
    private final StatusLabels labels;

    @GetMapping("/api/v1/auctions/{id}/state")
    @Operation(summary = "Hərracın canlı vəziyyəti (hər 2 s sorğu üçün)")
    public StateDto state(@PathVariable Long id, Authentication auth) {
        Auction a;
        try {
            a = auctions.get(id);
        } catch (BusinessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<Bid> all = auctions.bidsOf(a);
        Integer topNo = all.stream().max((x, y) -> Long.compare(x.getAmount(), y.getAmount()))
                .map(b -> b.getParticipant().getNumber()).orElse(null);
        MeDto me = auctions.participantOf(a, auth == null ? null : auth.getName())
                .map(p -> new MeDto(p.getNumber(), p.isDepositConfirmed(), topNo != null && topNo == p.getNumber())).orElse(null);
        return new StateDto(a.getStatus().name(), labels.label(a.getStatus()), a.getStartPrice(), a.getCurrentPrice(), a.getStep(),
                a.minNextBid(), epoch(a.getStartsAt()), epoch(a.getEndsAt()), epoch(LocalDateTime.now(BAKU)), auctions.participantCount(a),
                a.getExtensions(), all.stream().limit(20)
                .map(b -> new BidDto(b.getParticipant().getNumber(), b.getAmount(), epoch(b.getCreatedAt()), b.isExtended())).toList(), me);
    }

    static long epoch(LocalDateTime t) {
        return t.atZone(BAKU).toInstant().toEpochMilli();
    }

}
