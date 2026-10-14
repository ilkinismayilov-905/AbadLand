package az.aqroinvest.web;

import az.aqroinvest.auction.AuctionService;
import az.aqroinvest.domain.*;
import az.aqroinvest.service.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Public auction pages + investor participation. State changes only in AuctionService. */
@Controller
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctions;
    private final Viewer3dData viewer3d;

    @GetMapping("/herraclar")
    public String list(@RequestParam(required = false) AuctionStatus status, @RequestParam(required = false) DealType dealType, Model model) {
        model.addAttribute("auctions", auctions.list().stream()
                .filter(a -> a.getStatus() != AuctionStatus.PENDING_APPROVAL && a.getStatus() != AuctionStatus.REJECTED)
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> dealType == null || a.getDealType() == dealType).toList());
        model.addAttribute("status", status);
        model.addAttribute("dealType", dealType);
        model.addAttribute("statuses", new AuctionStatus[]{AuctionStatus.LIVE, AuctionStatus.SCHEDULED, AuctionStatus.ENDED,
                AuctionStatus.CONFIRMED, AuctionStatus.FAILED, AuctionStatus.CANCELLED});
        model.addAttribute("dealTypes", DealType.values());
        return "herraclar";
    }

    @GetMapping("/herraclar/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        Auction a = visible(id, auth);
        model.addAttribute("a", a);
        model.addAttribute("me", auctions.participantOf(a, auth == null ? null : auth.getName()).orElse(null));
        model.addAttribute("plot3d", viewer3d.of(a.getPlot(), false, auth));
        model.addAttribute("legalNote", AuctionService.LEGAL_NOTE);
        return "herrac";
    }

    @GetMapping("/herraclar/{id}/protokol")
    public String protocol(@PathVariable Long id, Authentication auth, Model model) {
        Auction a = visible(id, auth);
        if (a.getStatus() != AuctionStatus.CONFIRMED && !staff(auth)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("a", a);
        model.addAttribute("bids", auctions.bidsOf(a));
        model.addAttribute("participants", auctions.participantCount(a));
        model.addAttribute("staff", staff(auth));
        model.addAttribute("winnerNo", a.getWinner() == null ? null : auctions.participantOf(a, a.getWinner().getEmail()).map(AuctionParticipant::getNumber).orElse(null));
        model.addAttribute("legalNote", AuctionService.LEGAL_NOTE);
        return "herrac-protokol";
    }

    @PostMapping("/herraclar/{id}/qeydiyyat")
    public String register(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean rules, Authentication auth, RedirectAttributes ra) {
        try {
            var p = auctions.register(id, auth.getName(), rules);
            ra.addFlashAttribute("success", "Qeydiyyat tamamlandı: siz İştirakçı #" + p.getNumber() + " oldunuz. İndi zaminatı təsdiqləyin.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/herraclar/" + id;
    }

    @PostMapping("/herraclar/{id}/zaminat")
    public String deposit(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            auctions.confirmDeposit(id, auth.getName());
            ra.addFlashAttribute("success", "Zaminat təsdiqləndi (simulyasiya). Təklif verə bilərsiniz.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/herraclar/" + id;
    }

    /** Called by auction-live.js (fetch + CSRF header). */
    @PostMapping("/herraclar/{id}/teklif")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bid(@PathVariable Long id, @RequestParam(required = false) Long amount, Authentication auth) {
        try {
            var r = auctions.placeBid(id, auth.getName(), amount);
            return ResponseEntity.ok(Map.of("ok", true, "message", r.message(), "amount", r.amount(), "extended", r.extended()));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/kabinet/herraclarim")
    public String mine(Authentication auth, Model model) {
        model.addAttribute("rows", auctions.myParticipations(auth.getName()).stream().map(p -> Map.of(
                "p", p, "a", p.getAuction(), "last", auctions.lastBidOf(p).map(b -> b.getAmount()).orElse(-1L), // -1 = no bid (Map.of forbids null)
                "highest", p.getAuction().getCurrentPrice() != null && auctions.lastBidOf(p).map(b -> b.getAmount() == p.getAuction().getCurrentPrice()).orElse(false),
                "won", p.getAuction().getWinner() != null && p.getAuction().getWinner().getId().equals(p.getUser().getId()))).toList());
        return "kabinet-herraclarim";
    }

    /** Pending / rejected auctions are staff-only; everything else is public. */
    private Auction visible(Long id, Authentication auth) {
        Auction a;
        try {
            a = auctions.get(id);
        } catch (BusinessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if ((a.getStatus() == AuctionStatus.PENDING_APPROVAL || a.getStatus() == AuctionStatus.REJECTED) && !staff(auth))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return a;
    }

    private static boolean staff(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(g -> !g.getAuthority().equals("ROLE_INVESTOR"));
    }
}
