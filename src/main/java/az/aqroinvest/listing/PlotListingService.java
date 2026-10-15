package az.aqroinvest.listing;

import az.aqroinvest.ai.ClimateService;
import az.aqroinvest.domain.*;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.AuditService;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.service.StorageService;
import az.aqroinvest.service.SuitabilityService;
import az.aqroinvest.web.I18n;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

import static az.aqroinvest.service.AuditService.BAKU;

/**
 * Staff plot listings (SPEC "Plot listings"): OPERATOR / ADMIN draft a state plot in a 4-step wizard, only ADMIN publishes
 * or unpublishes. ListingStatus changes only here; every action is audited.
 */
@Service
@RequiredArgsConstructor
public class PlotListingService {

    public static final int MIN_PHOTOS = 3;
    public static final int MAX_PHOTOS = 10;
    public static final double MIN_HA = 0.5;
    /** Below this share of agricultural published plots the admin list shows a warning. */
    public static final double AGRI_SHARE_MIN = 85;

    /** What happens right after publishing. */
    public enum AfterPublish { CATALOG, COMPETITION, AUCTION }

    /** Several rule errors at once (shown as a list in the form). */
    @Getter
    public static class Invalid extends BusinessException {
        private final List<String> errors;

        public Invalid(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = errors;
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NotFound extends RuntimeException {}

    private final PlotRepository plots;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final CompetitionRepository competitions;
    private final AuctionRepository auctions;
    private final ApplicationRepository applications;
    private final CropRepository crops;
    private final StorageService storage;
    private final ClimateService climate;
    private final SuitabilityService suitability;
    private final AuditService audit;
    private final ObjectMapper json;

    // ---------------------------------------------------------------- reading

    public Plot get(Long id) {
        return plots.findById(id).orElseThrow(NotFound::new);
    }

    /** Admin list with filters; newest first. */
    public List<Plot> list(ListingStatus status, PlotCategory category, String district) {
        return plots.findAll().stream()
                .filter(p -> status == null || p.getListingStatus() == status)
                .filter(p -> category == null || p.getCategory() == category)
                .filter(p -> district == null || district.isBlank() || district.equals(p.getDistrict()))
                .sorted(Comparator.comparing((Plot p) -> p.getCreatedAt() == null ? LocalDateTime.MIN : p.getCreatedAt()).reversed()
                        .thenComparing(Plot::getCode))
                .toList();
    }

    public List<Document> photos(Plot p) {
        return documents.findByPlotOrderBySortOrderAscIdAsc(p).stream().filter(d -> d.getType() == DocType.PHOTO).toList();
    }

    public List<Document> files(Plot p) {
        return documents.findByPlotOrderBySortOrderAscIdAsc(p).stream().filter(d -> d.getType() != DocType.PHOTO).toList();
    }

    /** Cover photo id per plot (first photo) for catalog cards. */
    public Map<Long, Long> covers() {
        Map<Long, Long> m = new HashMap<>();
        documents.findAll().stream().filter(d -> d.getPlot() != null && d.getType() == DocType.PHOTO)
                .sorted(Comparator.comparingInt(Document::getSortOrder).thenComparing(Document::getId))
                .forEach(d -> m.putIfAbsent(d.getPlot().getId(), d.getId()));
        return m;
    }

    /** % of published plots that are agricultural (100 when none). */
    public double agriSharePct() {
        List<Plot> pub = plots.findAll().stream().filter(Plot::isPublished).toList();
        if (pub.isEmpty()) return 100;
        return 100.0 * pub.stream().filter(p -> p.getCategory() == null || !p.getCategory().isAgrotourism()).count() / pub.size();
    }

    /** Competition / auction the plot is in (admin list column), or null. */
    public String engagement(Plot p) {
        Optional<Competition> c = competitions.findAllByOrderByIdDesc().stream()
                .filter(x -> x.getStatus() != CompetitionStatus.REJECTED && x.getPlots().stream().anyMatch(q -> q.getId().equals(p.getId()))).findFirst();
        if (c.isPresent()) return c.get().getCode();
        return auctions.findByPlotOrderByStartsAtDesc(p).stream().findFirst().map(Auction::getCode).orElse(null);
    }

    // ---------------------------------------------------------------- wizard

    /** Step 1 of a new listing: creates the DRAFT plot (district needed for the code). */
    @Transactional
    public Plot create(String email, PlotListingForm f) {
        User actor = staff(email, false);
        Districts.District d = Districts.get(f.getDistrict());
        if (d == null) throw new Invalid(List.of(I18n.t("listing.err.district")));
        Plot p = new Plot();
        p.setCode(nextCode(d));
        p.setListingStatus(ListingStatus.DRAFT);
        p.setStatus(PlotStatus.AVAILABLE);
        p.setPurpose(ActivityType.CROP_FARMING);
        p.setDemining(DeminingStatus.CLEARED);
        p.setLeaseYears(25);
        p.setCreatedBy(actor);
        p.setCreatedAt(LocalDateTime.now(BAKU));
        applyLand(p, f);
        plots.save(p);
        audit.log(email, actor.getRole().name(), "LISTING_CREATED", "Plot", p.getCode(), null);
        return p;
    }

    /** Saves one wizard step (1–3) of a DRAFT / UNPUBLISHED plot; rule errors of the step are thrown together. */
    @Transactional
    public Plot saveStep(Long id, String email, int step, PlotListingForm f, MultipartFile deminingCert) {
        User actor = staff(email, false);
        Plot p = editable(id);
        switch (step) {
            case 1 -> applyLand(p, f);
            case 2 -> applyPurpose(p, f);
            case 3 -> {
                applyConditions(p, f);
                if (present(deminingCert)) storeFile(p, deminingCert, DocType.DEMINING_CERT);
            }
            default -> throw new NotFound();
        }
        audit.log(email, actor.getRole().name(), "LISTING_STEP_" + step, "Plot", p.getCode(), null);
        return p;
    }

    @Transactional
    public void uploadFiles(Long id, String email, List<MultipartFile> photos, List<MultipartFile> docs) {
        User actor = staff(email, false);
        Plot p = editable(id);
        List<MultipartFile> pics = photos == null ? List.of() : photos.stream().filter(PlotListingService::present).toList();
        if (photos(p).size() + pics.size() > MAX_PHOTOS) throw new BusinessException(I18n.t("listing.err.tooManyPhotos", MAX_PHOTOS));
        for (MultipartFile f : pics) {
            String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase(Locale.ROOT);
            if (!name.matches(".*\\.(jpe?g|png)$")) throw new BusinessException(I18n.t("listing.err.photoType"));
        }
        int order = photos(p).stream().mapToInt(Document::getSortOrder).max().orElse(-1);
        for (MultipartFile f : pics) storeFile(p, f, DocType.PHOTO).setSortOrder(++order);
        for (MultipartFile f : docs == null ? List.<MultipartFile>of() : docs) {
            if (!present(f)) continue;
            if (!String.valueOf(f.getOriginalFilename()).toLowerCase(Locale.ROOT).endsWith(".pdf")) throw new BusinessException(I18n.t("listing.err.docType"));
            storeFile(p, f, DocType.OTHER);
        }
        audit.log(email, actor.getRole().name(), "LISTING_FILES", "Plot", p.getCode(), pics.size() + " photo(s)");
    }

    /** Moves a photo one place up (-1) or down (+1); the first photo is the cover. */
    @Transactional
    public void movePhoto(Long id, Long docId, int dir, String email) {
        staff(email, false);
        Plot p = editable(id);
        List<Document> list = new ArrayList<>(photos(p));
        int i = indexOf(list, docId), j = i + dir;
        if (j >= 0 && j < list.size()) Collections.swap(list, i, j);
        for (int k = 0; k < list.size(); k++) list.get(k).setSortOrder(k);
    }

    @Transactional
    public void deleteFile(Long id, Long docId, String email) {
        User actor = staff(email, false);
        Plot p = editable(id);
        Document d = documents.findById(docId).filter(x -> x.getPlot() != null && x.getPlot().getId().equals(p.getId())).orElseThrow(NotFound::new);
        documents.delete(d);
        audit.log(email, actor.getRole().name(), "LISTING_FILE_DELETED", "Plot", p.getCode(), d.getOriginalName());
    }

    /** Everything that blocks publishing (empty = ready). */
    public List<String> missing(Plot p) {
        List<String> m = new ArrayList<>();
        if (blank(p.getName())) m.add(I18n.t("listing.missing.title"));
        else if (p.getName().strip().length() < 10 || p.getName().strip().length() > 120) m.add(I18n.t("listing.err.titleLength"));
        else if (genericTitle(p.getName())) m.add(I18n.t("listing.err.titleGeneric"));
        if (Districts.get(p.getDistrict()) == null) m.add(I18n.t("listing.err.district"));
        if (blank(p.getGeoJson())) m.add(I18n.t("listing.missing.polygon"));
        if (p.getAreaHa() < MIN_HA || p.getAreaHa() > Geo.MAX_HA) m.add(I18n.t("listing.err.area"));
        if (p.getCategory() == null) m.add(I18n.t("listing.missing.category"));
        if (blank(p.getDescription())) m.add(I18n.t("listing.missing.description"));
        if (p.getCategory() != null && p.getCategory().needsCrops() && blank(p.getRecommendedCrops())) m.add(I18n.t("listing.missing.crops"));
        if (p.getCategory() != null && p.getCategory().isAgrotourism() && blank(p.getConceptLinkToAgri())) m.add(I18n.t("listing.missing.concept"));
        if (p.getDemining() == DeminingStatus.IN_PROGRESS) m.add(I18n.t("listing.missing.deminingInProgress"));
        else if (p.getDemining() != DeminingStatus.CLEARED && files(p).stream().noneMatch(d -> d.getType() == DocType.DEMINING_CERT))
            m.add(I18n.t("listing.missing.deminingCert"));
        if (p.getRoadKm() < 0 || p.getRoadKm() > 200) m.add(I18n.t("listing.err.road"));
        if (p.getLeaseYears() < 1 || p.getLeaseYears() > 49) m.add(I18n.t("listing.err.lease"));
        if (p.getFeePerHa() < 0) m.add(I18n.t("listing.err.fee"));
        if (photos(p).size() < MIN_PHOTOS) m.add(I18n.t("listing.missing.photos", MIN_PHOTOS, photos(p).size()));
        return m;
    }

    /** Never blocking: drawn vs entered area, polygon centre outside the chosen district. */
    public List<String> warnings(Plot p) {
        List<String> w = new ArrayList<>();
        if (blank(p.getGeoJson())) return w;
        try {
            Geo.Shape s = Geo.parse(p.getGeoJson(), json);
            if (p.getAreaHa() > 0 && Math.abs(p.getAreaHa() - s.areaHa()) > 0.2 * s.areaHa())
                w.add(I18n.t("listing.warn.areaMismatch", fmtHa(s.areaHa()), fmtHa(p.getAreaHa())));
            if (Boolean.FALSE.equals(Districts.contains(p.getDistrict(), s.lng(), s.lat())))
                w.add(I18n.t("listing.warn.outsideDistrict", p.getDistrict()));
        } catch (IllegalArgumentException e) {
            // stored polygons were already checked on save
        }
        return w;
    }

    // ---------------------------------------------------------------- publishing (ADMIN)

    /**
     * DRAFT / UNPUBLISHED → PUBLISHED: plot AVAILABLE, climate fetched, RULE suitability computed. COMPETITION adds the plot to a
     * PENDING_APPROVAL competition (the commission still approves it); AUCTION is handled by the controller (auction form).
     */
    @Transactional
    public void publish(Long id, String email, AfterPublish after, Long competitionId) {
        User admin = staff(email, true);
        Plot p = editable(id);
        List<String> m = missing(p);
        if (!m.isEmpty()) throw new Invalid(m);
        Competition comp = null;
        if (after == AfterPublish.COMPETITION) {
            comp = competitionId == null ? null : competitions.findById(competitionId).orElse(null);
            if (comp == null || comp.getStatus() != CompetitionStatus.PENDING_APPROVAL) throw new BusinessException(I18n.t("listing.err.competition"));
        }
        p.setListingStatus(ListingStatus.PUBLISHED);
        p.setStatus(PlotStatus.AVAILABLE);
        p.setPublishedAt(LocalDateTime.now(BAKU));
        audit.log(email, admin.getRole().name(), "LISTING_PUBLISHED", "Plot", p.getCode(), after == null ? null : after.name());
        if (!climate.refresh(p)) suitability.recompute(p); // refresh recomputes on success; offline → RULE scores without climate
        if (comp != null) {
            comp.getPlots().add(p);
            audit.log(email, admin.getRole().name(), "LISTING_TO_COMPETITION", "Plot", p.getCode(), comp.getCode());
        }
    }

    /** PUBLISHED → UNPUBLISHED unless the plot is in a published competition, an open auction or has an approved application. */
    @Transactional
    public void unpublish(Long id, String email) {
        User admin = staff(email, true);
        Plot p = get(id);
        if (!p.isPublished()) throw new BusinessException(I18n.t("listing.err.notPublished"));
        String reason = unpublishBlocker(p);
        if (reason != null) throw new BusinessException(reason);
        p.setListingStatus(ListingStatus.UNPUBLISHED);
        audit.log(email, admin.getRole().name(), "LISTING_UNPUBLISHED", "Plot", p.getCode(), null);
    }

    /** null = may be unpublished. */
    public String unpublishBlocker(Plot p) {
        boolean inComp = competitions.findByStatusOrderByDeadlineAsc(CompetitionStatus.PUBLISHED).stream()
                .anyMatch(c -> c.getPlots().stream().anyMatch(q -> q.getId().equals(p.getId())));
        if (inComp) return I18n.t("listing.err.inCompetition");
        boolean liveAuction = auctions.findByPlotOrderByStartsAtDesc(p).stream()
                .anyMatch(a -> a.getStatus() == AuctionStatus.PENDING_APPROVAL || a.getStatus() == AuctionStatus.SCHEDULED
                        || a.getStatus() == AuctionStatus.LIVE || a.getStatus() == AuctionStatus.ENDED);
        if (liveAuction) return I18n.t("listing.err.inAuction");
        if (applications.findByDecision(Decision.APPROVED).stream().anyMatch(a -> a.getPlot().getId().equals(p.getId())))
            return I18n.t("listing.err.approvedApplication");
        return null;
    }

    public List<Competition> pendingCompetitions() {
        return competitions.findByStatusOrderByDeadlineAsc(CompetitionStatus.PENDING_APPROVAL);
    }

    // ---------------------------------------------------------------- internals

    private void applyLand(Plot p, PlotListingForm f) {
        List<String> errors = new ArrayList<>();
        Districts.District d = f.getDistrict() == null ? null : Districts.get(f.getDistrict());
        if (f.getDistrict() != null && d == null) errors.add(I18n.t("listing.err.district"));
        if (f.getAreaHa() != null && (f.getAreaHa() < MIN_HA || f.getAreaHa() > Geo.MAX_HA)) errors.add(I18n.t("listing.err.area"));
        Geo.Shape s = null;
        if (!blank(f.getGeoJson())) {
            try {
                s = Geo.parse(f.getGeoJson(), json);
            } catch (IllegalArgumentException e) {
                errors.add(I18n.t(e.getMessage()));
            }
        }
        if (!errors.isEmpty()) throw new Invalid(errors);
        if (d != null) {
            p.setDistrict(d.name());
            p.setEconomicRegion(d.region());
        }
        if (f.getVillage() != null) p.setVillage(trim(f.getVillage(), 80));
        if (s != null) {
            p.setGeoJson(s.geoJson());
            p.setLng(s.lng());
            p.setLat(s.lat());
            p.setAreaHa(f.getAreaHa() != null ? f.getAreaHa() : s.areaHa());
        } else if (f.getAreaHa() != null) p.setAreaHa(f.getAreaHa());
    }

    private void applyPurpose(Plot p, PlotListingForm f) {
        List<String> errors = new ArrayList<>();
        if (f.getTitle() != null && !f.getTitle().isBlank()) {
            String t = f.getTitle().strip();
            if (t.length() < 10 || t.length() > 120) errors.add(I18n.t("listing.err.titleLength"));
            else if (genericTitle(t)) errors.add(I18n.t("listing.err.titleGeneric"));
        }
        if (f.getDescription() != null && f.getDescription().strip().length() > 3000) errors.add(I18n.t("listing.err.descriptionLength"));
        List<String> codes = f.getCrops() == null ? List.of() : f.getCrops().stream().filter(c -> !blank(c)).distinct().toList();
        for (String c : codes) if (crops.findByCode(c).isEmpty()) errors.add(I18n.t("listing.err.unknownCrop", c));
        if (!errors.isEmpty()) throw new Invalid(errors);
        if (f.getTitle() != null) p.setName(trim(f.getTitle(), 120));
        if (f.getCategory() != null) {
            p.setCategory(f.getCategory());
            p.setPurpose(f.getCategory().purpose());
        }
        if (f.getDescription() != null) p.setDescription(trim(f.getDescription(), 3000));
        if (f.getCrops() != null) p.setRecommendedCrops(codes.isEmpty() ? null : String.join(",", codes));
        if (f.getConceptLinkToAgri() != null) p.setConceptLinkToAgri(trim(f.getConceptLinkToAgri(), 3000));
    }

    private void applyConditions(Plot p, PlotListingForm f) {
        List<String> errors = new ArrayList<>();
        if (f.getRoadKm() != null && (f.getRoadKm() < 0 || f.getRoadKm() > 200)) errors.add(I18n.t("listing.err.road"));
        if (f.getLeaseYears() != null && (f.getLeaseYears() < 1 || f.getLeaseYears() > 49)) errors.add(I18n.t("listing.err.lease"));
        if (f.getFeePerHa() != null && f.getFeePerHa() < 0) errors.add(I18n.t("listing.err.fee"));
        if (f.getEstimatedInvestment() != null && f.getEstimatedInvestment() < 0) errors.add(I18n.t("listing.err.investment"));
        if (f.getExpectedJobs() != null && (f.getExpectedJobs() < 0 || f.getExpectedJobs() > 10_000)) errors.add(I18n.t("listing.err.jobs"));
        if (!errors.isEmpty()) throw new Invalid(errors);
        if (f.getDemining() != null) p.setDemining(f.getDemining());
        if (f.getWaterSource() != null) p.setWaterSource(trim(f.getWaterSource(), 120));
        if (f.getIrrigation() != null) p.setIrrigation(f.getIrrigation());
        if (f.getSoilType() != null) p.setSoilType(trim(f.getSoilType(), 80));
        if (f.getRoadKm() != null) p.setRoadKm(f.getRoadKm());
        if (f.getElectricity() != null) p.setElectricity(f.getElectricity());
        if (f.getLeaseYears() != null) p.setLeaseYears(f.getLeaseYears());
        if (f.getFeePerHa() != null) p.setFeePerHa(f.getFeePerHa());
        p.setEstimatedInvestment(f.getEstimatedInvestment());
        p.setExpectedJobs(f.getExpectedJobs());
    }

    private Document storeFile(Plot p, MultipartFile f, DocType type) {
        Document d = storage.store(f, type, null, null);
        d.setPlot(p);
        return documents.save(d);
    }

    /** DRAFT / UNPUBLISHED only. */
    private Plot editable(Long id) {
        Plot p = get(id);
        if (p.isPublished()) throw new BusinessException(I18n.t("listing.err.notEditable"));
        return p;
    }

    private User staff(String email, boolean adminOnly) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        boolean ok = adminOnly ? u.getRole() == Role.ADMIN : u.getRole() == Role.OPERATOR || u.getRole() == Role.ADMIN;
        if (!ok) throw new BusinessException(I18n.t(adminOnly ? "listing.err.adminOnly" : "listing.err.forbidden"));
        return u;
    }

    /** District prefix + next free number, e.g. FZL-013. */
    synchronized String nextCode(Districts.District d) {
        String prefix = d.prefix() + "-";
        int max = plots.findAll().stream().map(Plot::getCode).filter(c -> c != null && c.startsWith(prefix) && c.substring(prefix.length()).matches("\\d+"))
                .mapToInt(c -> Integer.parseInt(c.substring(prefix.length()))).max().orElse(0);
        return prefix + String.format("%03d", max + 1);
    }

    private static final Set<String> GENERIC_TITLES = Set.of("elan", "test", "sınaq", "torpaq", "sahə", "layihə",
            "yeni elan", "test elan", "elan 1", "asdf", "qwerty");

    /** "Elan", "Test", "test test", "1234567890"… */
    static boolean genericTitle(String title) {
        String t = title.strip().toLowerCase(Locale.forLanguageTag("az")).replaceAll("\\s+", " ");
        if (GENERIC_TITLES.contains(t) || t.matches("[\\d\\s.,-]+")) return true;
        return Arrays.stream(t.split(" ")).allMatch(GENERIC_TITLES::contains);
    }

    private static int indexOf(List<Document> list, Long id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).getId().equals(id)) return i;
        throw new NotFound();
    }

    private static String fmtHa(double v) {
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    private static boolean present(MultipartFile f) {
        return f != null && !f.isEmpty();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Stripped and capped; blank → null. */
    private static String trim(String s, int max) {
        String t = s.strip();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
