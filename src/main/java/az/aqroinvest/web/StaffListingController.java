package az.aqroinvest.web;

import az.aqroinvest.domain.*;
import az.aqroinvest.listing.Districts;
import az.aqroinvest.listing.PlotListingForm;
import az.aqroinvest.listing.PlotListingService;
import az.aqroinvest.listing.PlotListingService.AfterPublish;
import az.aqroinvest.repo.CropRepository;
import az.aqroinvest.service.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** Staff plot listings: 4-step wizard (OPERATOR / ADMIN), list, publish / unpublish (ADMIN). */
@Controller
@RequestMapping("/idare")
@RequiredArgsConstructor
public class StaffListingController {

    private final PlotListingService listings;
    private final CropRepository crops;

    @GetMapping("/elanlar")
    public String list(@RequestParam(required = false) ListingStatus status, @RequestParam(required = false) PlotCategory category,
                       @RequestParam(required = false) String district, Model model) {
        List<Plot> list = listings.list(status, category, district);
        model.addAttribute("plots", list);
        java.util.Map<Long, String> engagement = new java.util.HashMap<>(), blockers = new java.util.HashMap<>(); // null values allowed
        list.forEach(p -> {
            engagement.put(p.getId(), listings.engagement(p));
            if (p.isPublished()) blockers.put(p.getId(), listings.unpublishBlocker(p));
        });
        model.addAttribute("engagement", engagement);
        model.addAttribute("blockers", blockers);
        model.addAttribute("agriShare", listings.agriSharePct());
        model.addAttribute("agriMin", PlotListingService.AGRI_SHARE_MIN);
        model.addAttribute("statuses", ListingStatus.values());
        model.addAttribute("categories", PlotCategory.values());
        model.addAttribute("districts", Districts.ALL.stream().map(Districts.District::name).toList());
        model.addAttribute("sel", new String[]{status == null ? "" : status.name(), category == null ? "" : category.name(), district == null ? "" : district});
        return "idare-elanlar";
    }

    @GetMapping("/elan/yeni")
    public String newListing(Model model) {
        return wizard(null, 1, new PlotListingForm(), model);
    }

    @PostMapping("/elan/yeni")
    public String create(@ModelAttribute("f") PlotListingForm f, Authentication auth, Model model, RedirectAttributes ra) {
        try {
            Plot p = listings.create(auth.getName(), f);
            ra.addFlashAttribute("success", I18n.t("listing.flash.saved"));
            return "redirect:/idare/elan/" + p.getId() + "/addim/2";
        } catch (BusinessException e) {
            errors(model, e);
            return wizard(null, 1, f, model);
        }
    }

    /** Drafts open in the wizard; published plots open their passport. */
    @GetMapping("/elan/{id}")
    public String open(@PathVariable Long id) {
        Plot p = listings.get(id);
        return p.isPublished() ? "redirect:/saheler/" + id : "redirect:/idare/elan/" + id + "/addim/1";
    }

    @GetMapping("/elan/{id}/addim/{step}")
    public String step(@PathVariable Long id, @PathVariable int step, Model model, RedirectAttributes ra) {
        Plot p = listings.get(id);
        if (p.isPublished()) {
            ra.addFlashAttribute("error", I18n.t("listing.err.notEditable"));
            return "redirect:/idare/elanlar";
        }
        if (step < 1 || step > 4) throw new PlotListingService.NotFound();
        return wizard(p, step, toForm(p), model);
    }

    /** Steps 1–3; ?autosave=1 (fetch from the page) saves and answers 204, or 422 when the step has rule errors. */
    @PostMapping("/elan/{id}/addim/{step}")
    public Object save(@PathVariable Long id, @PathVariable int step, @ModelAttribute("f") PlotListingForm f,
                       @RequestParam(required = false) MultipartFile deminingCert, @RequestParam(required = false) String autosave,
                       @RequestParam(required = false) String next, Authentication auth, Model model, RedirectAttributes ra) {
        boolean auto = autosave != null;
        try {
            listings.saveStep(id, auth.getName(), step, f, auto ? null : deminingCert);
        } catch (BusinessException e) {
            if (auto) return ResponseEntity.unprocessableEntity().build();
            errors(model, e);
            return wizard(listings.get(id), step, f, model);
        }
        if (auto) return ResponseEntity.noContent().build();
        ra.addFlashAttribute("success", I18n.t("listing.flash.saved"));
        return "redirect:/idare/elan/" + id + "/addim/" + (next != null ? step + 1 : step);
    }

    @PostMapping("/elan/{id}/fayllar")
    public String upload(@PathVariable Long id, @RequestParam(required = false) List<MultipartFile> photos,
                         @RequestParam(required = false) List<MultipartFile> docs, Authentication auth, RedirectAttributes ra) {
        return act(ra, id, () -> {
            listings.uploadFiles(id, auth.getName(), photos, docs);
            return "listing.flash.filesSaved";
        });
    }

    @PostMapping("/elan/{id}/foto/{docId}/{dir:yuxari|asagi}")
    public String move(@PathVariable Long id, @PathVariable Long docId, @PathVariable String dir, Authentication auth, RedirectAttributes ra) {
        return act(ra, id, () -> {
            listings.movePhoto(id, docId, dir.equals("yuxari") ? -1 : 1, auth.getName());
            return null;
        });
    }

    @PostMapping("/elan/{id}/fayl/{docId}/sil")
    public String deleteFile(@PathVariable Long id, @PathVariable Long docId, Authentication auth, RedirectAttributes ra) {
        return act(ra, id, () -> {
            listings.deleteFile(id, docId, auth.getName());
            return "listing.flash.fileDeleted";
        });
    }

    /** "Qaralama kimi saxla": everything is already saved — confirm and go back to the list. */
    @PostMapping("/elan/{id}/qaralama")
    public String keepDraft(@PathVariable Long id, RedirectAttributes ra) {
        listings.get(id);
        ra.addFlashAttribute("success", I18n.t("listing.flash.keptDraft"));
        return "redirect:/idare/elanlar";
    }

    @PostMapping("/elan/{id}/derc")
    public String publish(@PathVariable Long id, @RequestParam(required = false) AfterPublish after,
                          @RequestParam(required = false) Long competitionId, Authentication auth, RedirectAttributes ra) {
        try {
            listings.publish(id, auth.getName(), after, competitionId);
        } catch (PlotListingService.Invalid e) {
            ra.addFlashAttribute("submitErrors", e.getErrors());
            return "redirect:/idare/elan/" + id + "/addim/4";
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/idare/elan/" + id + "/addim/4";
        }
        Plot p = listings.get(id);
        ra.addFlashAttribute("success", I18n.t("listing.flash.published", p.getCode()));
        if (after == AfterPublish.AUCTION) return "redirect:/idare/herraclar?plotId=" + id + "#yeni";
        if (after == AfterPublish.COMPETITION) return "redirect:/idare/musabiqeler";
        return "redirect:/saheler/" + id;
    }

    @PostMapping("/elan/{id}/dercden-cixar")
    public String unpublish(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            listings.unpublish(id, auth.getName());
            ra.addFlashAttribute("success", I18n.t("listing.flash.unpublished"));
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/elanlar";
    }

    private String act(RedirectAttributes ra, Long id, Supplier<String> action) {
        try {
            String key = action.get();
            if (key != null) ra.addFlashAttribute("success", I18n.t(key));
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/elan/" + id + "/addim/4";
    }

    private static void errors(Model model, BusinessException e) {
        model.addAttribute("formErrors", e instanceof PlotListingService.Invalid i ? i.getErrors() : List.of(e.getMessage()));
    }

    private String wizard(Plot p, int step, PlotListingForm f, Model model) {
        model.addAttribute("p", p);
        model.addAttribute("f", f);
        model.addAttribute("step", step);
        model.addAttribute("regions", EconomicRegion.values());
        model.addAttribute("districtRegions", Districts.regions());
        model.addAttribute("districtBoxes", Districts.boxes());
        model.addAttribute("deminings", DeminingStatus.values());
        model.addAttribute("agriCategories", Arrays.stream(PlotCategory.values()).filter(c -> !c.isAgrotourism()).toList());
        model.addAttribute("tourismCategories", Arrays.stream(PlotCategory.values()).filter(PlotCategory::isAgrotourism).toList());
        model.addAttribute("cropsAgri", Arrays.stream(PlotCategory.values()).filter(PlotCategory::needsCrops).map(Enum::name).toList());
        model.addAttribute("crops", crops.findAll());
        if (p != null) {
            model.addAttribute("missing", listings.missing(p));
            model.addAttribute("warnings", listings.warnings(p));
            model.addAttribute("photos", listings.photos(p));
            model.addAttribute("files", listings.files(p));
            model.addAttribute("competitions", listings.pendingCompetitions());
        }
        return "idare-elan-wizard";
    }

    static PlotListingForm toForm(Plot p) {
        PlotListingForm f = new PlotListingForm();
        f.setGeoJson(p.getGeoJson());
        f.setAreaHa(p.getAreaHa() > 0 ? p.getAreaHa() : null);
        f.setDistrict(p.getDistrict());
        f.setVillage(p.getVillage());
        f.setTitle(p.getName());
        f.setCategory(p.getCategory());
        f.setDescription(p.getDescription());
        f.setCrops(p.getRecommendedCrops() == null ? List.of() : List.of(p.getRecommendedCrops().split(",")));
        f.setConceptLinkToAgri(p.getConceptLinkToAgri());
        f.setDemining(p.getDemining());
        f.setWaterSource(p.getWaterSource());
        f.setIrrigation(p.isIrrigation());
        f.setSoilType(p.getSoilType());
        f.setRoadKm(p.getRoadKm());
        f.setElectricity(p.isElectricity());
        f.setLeaseYears(p.getLeaseYears());
        f.setFeePerHa(p.getFeePerHa());
        f.setEstimatedInvestment(p.getEstimatedInvestment());
        f.setExpectedJobs(p.getExpectedJobs());
        return f;
    }
}
