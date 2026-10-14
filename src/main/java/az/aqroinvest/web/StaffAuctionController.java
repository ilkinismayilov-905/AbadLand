package az.aqroinvest.web;

import az.aqroinvest.auction.AuctionService;
import az.aqroinvest.auction.AuctionService.NewAuction;
import az.aqroinvest.domain.DealType;
import az.aqroinvest.domain.PlotStatus;
import az.aqroinvest.repo.PlotRepository;
import az.aqroinvest.service.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@RequestMapping("/idare/herraclar")
public class StaffAuctionController {

    private final AuctionService auctions;
    private final PlotRepository plots;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("auctions", auctions.list());
        model.addAttribute("freePlots", plots.findAll(Sort.by("code")).stream().filter(p -> p.getStatus() == PlotStatus.AVAILABLE).toList());
        model.addAttribute("dealTypes", auctions.saleEnabled() ? DealType.values() : new DealType[]{DealType.LEASE});
        model.addAttribute("defaultStart", LocalDateTime.now(az.aqroinvest.service.AuditService.BAKU).plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0));
        return "idare-herraclar";
    }

    @PostMapping
    public String create(@RequestParam(required = false) Long plotId, @RequestParam(required = false) DealType dealType,
                         @RequestParam(required = false) Long startPrice, @RequestParam(required = false) Long step,
                         @RequestParam(required = false) Long deposit, @RequestParam(required = false) Integer leaseYears,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
                         @RequestParam(required = false) String rules, Authentication auth, RedirectAttributes ra) {
        return act(ra, () -> "Hərrac yaradıldı və komissiyanın təsdiqinə göndərildi: " + auctions.create(
                new NewAuction(plotId, dealType, startPrice, step, deposit, leaseYears, startsAt, endsAt, rules), auth.getName()).getCode());
    }

    @PostMapping("/{id}/tesdiq")
    public String approve(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return act(ra, () -> { auctions.approve(id, auth.getName()); return "Hərrac təsdiqləndi."; });
    }

    @PostMapping("/{id}/redd")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment, Authentication auth, RedirectAttributes ra) {
        return act(ra, () -> { auctions.reject(id, auth.getName(), comment); return "Hərrac rədd edildi."; });
    }

    @PostMapping("/{id}/legv")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String comment, Authentication auth, RedirectAttributes ra) {
        return act(ra, () -> { auctions.cancel(id, auth.getName(), comment); return "Hərrac ləğv edildi."; });
    }

    @PostMapping("/{id}/netice")
    public String confirm(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return act(ra, () -> { auctions.confirm(id, auth.getName()); return "Nəticə təsdiqləndi, protokol dərc edildi."; });
    }

    private String act(RedirectAttributes ra, java.util.function.Supplier<String> action) {
        try {
            ra.addFlashAttribute("success", action.get());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/herraclar";
    }
}
