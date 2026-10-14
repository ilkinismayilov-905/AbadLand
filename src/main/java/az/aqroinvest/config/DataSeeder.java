package az.aqroinvest.config;

import az.aqroinvest.domain.*;
import az.aqroinvest.farm.CalendarService;
import az.aqroinvest.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static az.aqroinvest.service.AuditService.BAKU;

/** Fictional demo data, loaded on every start (H2 in-memory = demo reset). */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PasswordEncoder encoder;
    private final CalendarService calendarService;
    private final az.aqroinvest.service.SuitabilityService suitabilityService;
    private final UserRepository users;
    private final CropRepository crops;
    private final CropCalendarTemplateRepository templates;
    private final PlotRepository plots;
    private final PlotClimateRepository climates;
    private final PlotCropSuitabilityRepository suitabilities;
    private final AiAnalysisRepository aiAnalyses;
    private final CompetitionRepository competitions;
    private final ApplicationRepository applications;
    private final DocumentRepository documents;
    private final StatusHistoryRepository history;
    private final NotificationRepository notifications;
    private final AuditLogRepository auditLogs;
    private final IntegrationLogRepository integrationLogs;
    private final FarmProjectRepository farms;
    private final CropPlanItemRepository cropPlans;
    private final CalendarTaskRepository tasks;
    private final WorkforceItemRepository workforce;
    private final ProgressReportRepository reports;
    private final ShareOfferingRepository offerings;
    private final ShareHoldingRepository holdings;

    private final Map<String, User> u = new HashMap<>();
    private final Map<String, Crop> c = new HashMap<>();
    private final Map<String, Plot> p = new HashMap<>();

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return; // e.g. pg profile with existing data
        LocalDate today = LocalDate.now(BAKU);
        seedUsers();
        seedCrops();
        seedTemplates();
        seedPlots();
        seedClimate();
        Map<String, Competition> comp = seedCompetitions(today);
        seedApplicationsAndFarms(comp, today);
        seedAiSample();
        suitabilityService.recomputeAll();
        logCounts();
    }

    // ---------------------------------------------------------------- users

    private void seedUsers() {
        user("elvin@demo.az", "Elvin Məmmədov", Role.INVESTOR, InvestorType.INDIVIDUAL, "5ELV1N7", null, null, 6);
        user("rashad@demo.az", "Rəşad Hüseynov", Role.INVESTOR, InvestorType.INDIVIDUAL, "7RSH2K4", null, null, 12);
        user("nigar@demo.az", "Nigar Əliyeva", Role.INVESTOR, InvestorType.INDIVIDUAL, "3NGR8P1", null, null, 4);
        user("leyla@demo.az", "Leyla Qasımova", Role.INVESTOR, InvestorType.INDIVIDUAL, "9LYL4M2", null, null, 2);
        user("tural@demo.az", "Tural Babayev", Role.INVESTOR, InvestorType.LEGAL, null, "1402345671", "Qarabağ Taxıl MMC", 9);
        user("kamran@demo.az", "Kamran Rzayev", Role.INVESTOR, InvestorType.LEGAL, null, "1709876542", "Zəngəzur Bağları MMC", 15);
        user("operator@demo.az", "Səbinə Operator", Role.OPERATOR, null, null, null, null, null);
        user("komissiya@demo.az", "Fərid Komissiya", Role.COMMISSION, null, null, null, null, null);
        user("admin@demo.az", "Admin İstifadəçi", Role.ADMIN, null, null, null, null, null);
    }

    private void user(String email, String name, Role role, InvestorType type, String fin, String voen, String company, Integer exp) {
        User x = new User();
        x.setEmail(email);
        x.setPasswordHash(encoder.encode("Demo1234"));
        x.setFullName(name);
        x.setPhone("+994 50 000 00 " + String.format("%02d", u.size() + 1));
        x.setRole(role);
        x.setInvestorType(type);
        x.setFin(fin);
        x.setVoen(voen);
        x.setCompanyName(company);
        x.setExperienceYears(exp);
        u.put(email.substring(0, email.indexOf('@')), users.save(x));
    }

    // ---------------------------------------------------------------- crops ("Sınaq dəyəri")

    private void seedCrops() {
        crop("HAZELNUT", "Fındıq", ActivityType.HORTICULTURE, true, 9000, 7000, 2500, 4, 2.0, -20, 1500, WaterNeed.MEDIUM);
        crop("POMEGRANATE", "Nar", ActivityType.HORTICULTURE, true, 10000, 9000, 3500, 3, 15, -12, 1000, WaterNeed.MEDIUM);
        crop("GRAPE", "Üzüm", ActivityType.HORTICULTURE, true, 12000, 10000, 4000, 3, 10, -18, 1200, WaterNeed.MEDIUM);
        crop("APPLE", "Alma", ActivityType.HORTICULTURE, true, 25000, 18000, 7000, 3, 30, -30, 2000, WaterNeed.HIGH);
        crop("WALNUT", "Qoz", ActivityType.HORTICULTURE, true, 8000, 6000, 2000, 5, 2.5, -25, 1800, WaterNeed.MEDIUM);
        crop("WHEAT", "Buğda", ActivityType.CROP_FARMING, false, 1200, 1400, 700, 1, 3.5, -20, 2000, WaterNeed.LOW);
        crop("BARLEY", "Arpa", ActivityType.CROP_FARMING, false, 1000, 1100, 600, 1, 3.0, -22, 2400, WaterNeed.LOW);
        crop("BEEF_CATTLE", "Maldarlıq", ActivityType.LIVESTOCK, false, 3000, 2200, 1000, 2, 0, -30, 2800, WaterNeed.MEDIUM);
    }

    private void crop(String code, String name, ActivityType type, boolean fruit, int inv, int rev, int opex, int firstHarvest,
                      double yield, int frost, int maxElev, WaterNeed water) {
        Crop x = new Crop();
        x.setCode(code);
        x.setNameAz(name);
        x.setActivityType(type);
        x.setFruit(fruit);
        x.setInvestmentPerHa(inv);
        x.setRevenuePerHa(rev);
        x.setOpexPerHa(opex);
        x.setFirstHarvestYear(firstHarvest);
        x.setYieldTPerHa(yield);
        x.setFrostToleranceC(frost);
        x.setMaxElevationM(maxElev);
        x.setWaterNeed(water);
        c.put(code, crops.save(x));
    }

    private void seedTemplates() {
        // HAZELNUT: harvest at firstHarvestYear - 1 = 3
        tpl("HAZELNUT", CalendarStage.SOIL_PREP, 9, 10, 0);
        tpl("HAZELNUT", CalendarStage.PLANTING, 11, 11, 0);
        tpl("HAZELNUT", CalendarStage.IRRIGATION, 5, 9, 1);
        tpl("HAZELNUT", CalendarStage.PRUNING, 2, 2, 1);
        tpl("HAZELNUT", CalendarStage.HARVEST, 8, 9, 3);
        tpl("POMEGRANATE", CalendarStage.SOIL_PREP, 10, 10, 0);
        tpl("POMEGRANATE", CalendarStage.PLANTING, 11, 12, 0);
        tpl("POMEGRANATE", CalendarStage.IRRIGATION, 5, 9, 1);
        tpl("POMEGRANATE", CalendarStage.PRUNING, 2, 3, 1);
        tpl("POMEGRANATE", CalendarStage.HARVEST, 10, 10, 2);
        tpl("GRAPE", CalendarStage.PLANTING, 3, 4, 0);
        tpl("GRAPE", CalendarStage.IRRIGATION, 5, 8, 0);
        tpl("GRAPE", CalendarStage.PRUNING, 2, 2, 1);
        tpl("GRAPE", CalendarStage.HARVEST, 9, 9, 2);
        for (String code : List.of("WHEAT", "BARLEY")) {
            tpl(code, CalendarStage.SOIL_PREP, 9, 9, 0);
            tpl(code, CalendarStage.PLANTING, 10, 10, 0);
            tpl(code, CalendarStage.FERTILIZING, 3, 3, 1);
            tpl(code, CalendarStage.HARVEST, 6, 7, 1);
        }
        for (String code : List.of("APPLE", "WALNUT")) {
            tpl(code, CalendarStage.SOIL_PREP, 10, 10, 0);
            tpl(code, CalendarStage.PLANTING, 11, 12, 0);
            tpl(code, CalendarStage.IRRIGATION, 5, 9, 1);
            tpl(code, CalendarStage.PRUNING, 2, 3, 1);
            tpl(code, CalendarStage.HARVEST, 9, 10, c.get(code).getFirstHarvestYear() - 1);
        }
    }

    private void tpl(String crop, CalendarStage stage, int start, int end, int offset) {
        CropCalendarTemplate t = new CropCalendarTemplate();
        t.setCrop(c.get(crop));
        t.setStage(stage);
        t.setStartMonth(start);
        t.setEndMonth(end);
        t.setYearOffset(offset);
        templates.save(t);
    }

    // ---------------------------------------------------------------- plots

    private void seedPlots() {
        Plot fzl1 = plot("FZL-001", "Füzuli bağ sahəsi №1", "Füzuli", EconomicRegion.QARABAG, "Qaraxanbəyli", 42.5,
                ActivityType.HORTICULTURE, DeminingStatus.CLEARED, "Suvarma kanalı", true, "Boz-qəhvəyi", 3.2, true, 80,
                "HAZELNUT,POMEGRANATE", "Düz relyef, kanala yaxın, bağçılıq üçün hazır sahə.");
        fzl1.setGeoJson(rect(47.135, 39.598, 47.144, 39.604));
        fzl1.setLng(47.1395);
        fzl1.setLat(39.601);
        plot("FZL-002", "Füzuli bağ sahəsi №2", "Füzuli", EconomicRegion.QARABAG, "Horadiz", 35,
                ActivityType.HORTICULTURE, DeminingStatus.CLEARED, "Artezian quyusu (layihə)", false, "Boz", 12, true, 60,
                "POMEGRANATE,GRAPE", "Suvarma sistemi yoxdur, yol məsafəsi uzundur; qiyməti aşağıdır.", 47.06, 39.57);
        plot("CBR-001", "Cəbrayıl nar bağı", "Cəbrayıl", EconomicRegion.QARABAG, "Şükürbəyli", 60,
                ActivityType.HORTICULTURE, DeminingStatus.PARTIALLY_CLEARED, "Araz kanalı", true, "Boz-çəmən", 8, true, 70,
                "POMEGRANATE,GRAPE", "Araz vadisində isti iqlimli sahə, qismən təmizlənib.", 46.99, 39.40);
        Plot lcn = plot("LCN-001", "Laçın dağ bağı", "Laçın", EconomicRegion.SHERQI_ZANGEZUR, "Malıbəy", 30,
                ActivityType.HORTICULTURE, DeminingStatus.CLEARED, "Həkəri çayı", true, "Dağ-qəhvəyi", 6, true, 50,
                "WALNUT,APPLE", "Çay kənarında dağ yamacı, qoz və alma üçün perspektivli.");
        lcn.setGeoJson(rect(46.53, 39.62, 46.54, 39.627));
        lcn.setLng(46.535);
        lcn.setLat(39.6235);
        plot("KLB-001", "Kəlbəcər yay otlağı", "Kəlbəcər", EconomicRegion.SHERQI_ZANGEZUR, "Istisu", 150,
                ActivityType.LIVESTOCK, DeminingStatus.CLEARED, "Bulaqlar", false, "Dağ-çəmən", 15, false, 20,
                "BEEF_CATTLE", "Yüksək dağ otlağı, maldarlıq üçün.", 46.05, 40.10);
        plot("ZNG-001", "Zəngilan üzüm bağı", "Zəngilan", EconomicRegion.SHERQI_ZANGEZUR, "Ağalı", 45,
                ActivityType.HORTICULTURE, DeminingStatus.PARTIALLY_CLEARED, "Bəsitçay", true, "Boz-qəhvəyi", 4, true, 70,
                "POMEGRANATE,GRAPE", "Ağalı ağıllı kəndinə yaxın, isti dərə iqlimi.", 46.66, 39.10);
        plot("AGD-001", "Ağdam taxıl sahəsi", "Ağdam", EconomicRegion.QARABAG, "Qiyaslı", 120,
                ActivityType.CROP_FARMING, DeminingStatus.CLEARED, "Tərtər kanalı", true, "Şabalıdı", 5, true, 40,
                "WHEAT,BARLEY", "Geniş düzənlik, dənli bitkilər üçün.", 46.93, 39.99);
        plot("AGD-002", "Ağdam üzüm bağı", "Ağdam", EconomicRegion.QARABAG, "Şıxbabalı", 25,
                ActivityType.HORTICULTURE, DeminingStatus.CLEARED, "Suvarma kanalı", true, "Boz-qəhvəyi", 3, true, 75,
                "GRAPE,POMEGRANATE", "Ənənəvi üzümçülük zonası.", 47.00, 40.03);
        plot("TRT-001", "Tərtər taxıl sahəsi №1", "Tərtər", EconomicRegion.QARABAG, "Talış", 80,
                ActivityType.CROP_FARMING, DeminingStatus.CLEARED, "Tərtərçay", true, "Şabalıdı", 7, true, 40,
                "WHEAT,BARLEY", "Suvarılan düzən sahə.", 46.80, 40.30);
        plot("TRT-002", "Tərtər taxıl sahəsi №2", "Tərtər", EconomicRegion.QARABAG, "Suqovuşan", 55,
                ActivityType.CROP_FARMING, DeminingStatus.CLEARED, "Yağış", false, "Şabalıdı", 9, false, 30,
                "WHEAT", "Dəmyə əkinçilik sahəsi.", 46.68, 40.25);
        plot("SHS-001", "Şuşa alma bağı", "Şuşa", EconomicRegion.QARABAG, "Daşaltı", 18,
                ActivityType.HORTICULTURE, DeminingStatus.CLEARED, "Bulaq", true, "Dağ-qəhvəyi", 2, true, 90,
                "APPLE,WALNUT", "Dağ iqlimi, alma və qoz üçün.", 46.74, 39.76);
        plot("QBD-001", "Qubadlı qoz bağı", "Qubadlı", EconomicRegion.SHERQI_ZANGEZUR, "Xanlıq", 40,
                ActivityType.HORTICULTURE, DeminingStatus.IN_PROGRESS, "Bərgüşad çayı", true, "Dağ-qəhvəyi", 10, false, 50,
                "WALNUT,POMEGRANATE", "Mina təmizlənməsi davam edir.", 46.58, 39.34);
        plot("XCV-001", "Xocavənd taxıl sahəsi", "Xocavənd", EconomicRegion.QARABAG, "Hadrut", 90,
                ActivityType.CROP_FARMING, DeminingStatus.PARTIALLY_CLEARED, "Yağış", false, "Şabalıdı", 11, true, 35,
                "WHEAT,BARLEY", "Dəmyə taxıl sahəsi.", 47.03, 39.52);
        plot("KLB-002", "Kəlbəcər otlaq №2", "Kəlbəcər", EconomicRegion.SHERQI_ZANGEZUR, "Ağdaban", 200,
                ActivityType.LIVESTOCK, DeminingStatus.CLEARED, "Tərtərçay", false, "Dağ-çəmən", 18, false, 15,
                "BEEF_CATTLE", "Böyük yay otlağı.", 46.15, 40.05);
        plot("CBR-002", "Cəbrayıl taxıl sahəsi", "Cəbrayıl", EconomicRegion.QARABAG, "Mehdili", 70,
                ActivityType.CROP_FARMING, DeminingStatus.CLEARED, "Araz kanalı", true, "Boz", 6, true, 40,
                "WHEAT,BARLEY", "Araz boyu suvarılan sahə.", 47.10, 39.42);
        plot("XCL-001", "Xocalı üzüm bağı", "Xocalı", EconomicRegion.QARABAG, "Ballıca", 22,
                ActivityType.HORTICULTURE, DeminingStatus.CLEARED, "Bulaq + damcı", true, "Boz-qəhvəyi", 4, true, 80,
                "GRAPE,APPLE", "Kiçik bağ sahəsi, damcı suvarma var.", 46.80, 39.91);
        plots.saveAll(p.values());
    }

    private Plot plot(String code, String name, String district, EconomicRegion region, String village, double area,
                      ActivityType purpose, DeminingStatus demining, String water, boolean irrigation, String soil,
                      double roadKm, boolean electricity, int fee, String recCrops, String desc, double lng, double lat) {
        Plot x = plot(code, name, district, region, village, area, purpose, demining, water, irrigation, soil, roadKm,
                electricity, fee, recCrops, desc);
        // square polygon of the plot's area around the centre
        double half = Math.sqrt(area * 10000) / 2;
        double dLat = half / 111_000, dLng = half / (111_000 * Math.cos(Math.toRadians(lat)));
        x.setGeoJson(rect(lng - dLng, lat - dLat, lng + dLng, lat + dLat));
        x.setLng(lng);
        x.setLat(lat);
        return x;
    }

    private Plot plot(String code, String name, String district, EconomicRegion region, String village, double area,
                      ActivityType purpose, DeminingStatus demining, String water, boolean irrigation, String soil,
                      double roadKm, boolean electricity, int fee, String recCrops, String desc) {
        Plot x = new Plot();
        x.setCode(code);
        x.setName(name);
        x.setDistrict(district);
        x.setEconomicRegion(region);
        x.setVillage(village);
        x.setAreaHa(area);
        x.setPurpose(purpose);
        x.setDemining(demining);
        x.setWaterSource(water);
        x.setIrrigation(irrigation);
        x.setSoilType(soil);
        x.setRoadKm(roadKm);
        x.setElectricity(electricity);
        x.setLeaseYears(25);
        x.setFeePerHa(fee);
        x.setRecommendedCrops(recCrops);
        x.setDescription(desc);
        p.put(code, x);
        return x;
    }

    private static String rect(double minLng, double minLat, double maxLng, double maxLat) {
        return String.format(Locale.ROOT,
                "{\"type\":\"Polygon\",\"coordinates\":[[[%.5f,%.5f],[%.5f,%.5f],[%.5f,%.5f],[%.5f,%.5f],[%.5f,%.5f]]]}",
                minLng, minLat, maxLng, minLat, maxLng, maxLat, minLng, maxLat, minLng, minLat);
    }

    /** Plausible offline values; a real Open-Meteo fetch (Step 6) replaces them. */
    private void seedClimate() {
        climate("FZL-001", 420, 14.6, -9.5, 32, 390);
        climate("FZL-002", 380, 14.9, -9.0, 30, 340);
        climate("CBR-001", 300, 15.3, -8.0, 25, 320);
        climate("LCN-001", 1150, 10.2, -17.0, 95, 620);
        climate("KLB-001", 2100, 5.1, -26.0, 160, 780);
        climate("ZNG-001", 650, 13.4, -11.0, 45, 480);
        climate("AGD-001", 330, 14.5, -11.5, 38, 420);
        climate("AGD-002", 360, 14.4, -11.0, 37, 430);
        climate("TRT-001", 280, 14.3, -13.0, 42, 400);
        climate("TRT-002", 350, 14.0, -13.5, 45, 280);
        climate("SHS-001", 1380, 8.9, -18.5, 110, 700);
        climate("QBD-001", 900, 11.8, -14.0, 70, 560);
        climate("XCV-001", 700, 12.6, -13.0, 60, 290);
        climate("KLB-002", 1900, 6.0, -24.0, 145, 720);
        climate("CBR-002", 280, 15.4, -8.5, 24, 310);
        climate("XCL-001", 800, 12.0, -14.5, 65, 520);
    }

    private void climate(String plot, int elev, double mean, double absMin, int frostDays, double precip) {
        PlotClimate x = new PlotClimate();
        x.setPlot(p.get(plot));
        x.setElevationM(elev);
        x.setMeanTempC(mean);
        x.setAbsMinTempC(absMin);
        x.setFrostDays(frostDays);
        x.setPrecipMm(precip);
        x.setFetchedAt(LocalDateTime.now(BAKU));
        climates.save(x);
    }

    // ---------------------------------------------------------------- competitions

    private Map<String, Competition> seedCompetitions(LocalDate today) {
        Map<String, Competition> m = new HashMap<>();
        m.put("03", competition("MUS-2026-03", "Füzuli, Cəbrayıl, Laçın və Zəngilan bağçılıq sahələri",
                "Bağçılıq təcrübəsi və biznes plan; minimum 20 iş yeri tövsiyə olunur.", 200_000, today.plusDays(12),
                CompetitionStatus.PUBLISHED, "FZL-001", "FZL-002", "CBR-001", "LCN-001", "ZNG-001"));
        m.put("04", competition("MUS-2026-04", "Ağdam və Tərtər əkinçilik sahələri",
                "Dənli bitkilər üzrə təcrübə; texnika parkı haqqında məlumat.", 80_000, today.plusDays(5),
                CompetitionStatus.PUBLISHED, "AGD-001", "AGD-002", "TRT-001", "TRT-002"));
        m.put("05", competition("MUS-2026-05", "Kəlbəcər, Şuşa və Qubadlı sahələri",
                "Heyvandarlıq və dağ bağçılığı; yerli işçilərin cəlb edilməsi.", 150_000, today.plusDays(30),
                CompetitionStatus.PENDING_APPROVAL, "KLB-001", "KLB-002", "SHS-001", "QBD-001"));
        m.put("2025", competition("MUS-2025-02", "Xocalı və Xocavənd sahələri (2025)",
                "Bağlanmış müsabiqə.", 100_000, LocalDate.of(2025, 6, 30),
                CompetitionStatus.CLOSED, "XCL-001", "XCV-001"));
        return m;
    }

    private Competition competition(String code, String title, String req, long min, LocalDate deadline,
                                    CompetitionStatus status, String... plotCodes) {
        Competition x = new Competition();
        x.setCode(code);
        x.setTitle(title);
        x.setRequirements(req);
        x.setMinInvestment(min);
        x.setDeadline(deadline);
        x.setStatus(status);
        x.setCreatedBy(u.get("operator"));
        if (status == CompetitionStatus.PUBLISHED || status == CompetitionStatus.CLOSED) x.setApprovedBy(u.get("komissiya"));
        for (String pc : plotCodes) x.getPlots().add(p.get(pc));
        return competitions.save(x);
    }

    // ---------------------------------------------------------------- applications, farms, shares

    private void seedApplicationsAndFarms(Map<String, Competition> comp, LocalDate today) {
        LocalDateTime now = LocalDateTime.now(BAKU);
        Application a1 = app("QAR-2026-000001", "nigar", comp.get("03"), "CBR-001", "POMEGRANATE", 600_000, 22, 36, now.minusDays(6));
        Application a2 = app("QAR-2026-000002", "kamran", comp.get("03"), "ZNG-001", "GRAPE", 540_000, 25, 36, now.minusDays(9));
        to(a2, ApplicationStatus.UNDER_REVIEW, "operator", null, now.minusDays(7));
        Application a3 = app("QAR-2026-000003", "leyla", comp.get("03"), "LCN-001", "WALNUT", 240_000, 10, 48, now.minusDays(10));
        to(a3, ApplicationStatus.UNDER_REVIEW, "operator", null, now.minusDays(8));
        a3.setInfoRequest("Zəhmət olmasa torpaq analizinin nəticəsini əlavə edin.");
        to(a3, ApplicationStatus.INFO_REQUESTED, "operator", a3.getInfoRequest(), now.minusDays(4));
        app("QAR-2026-000004", "tural", comp.get("04"), "AGD-001", "WHEAT", 150_000, 12, 24, now.minusDays(2));
        Application a5 = app("QAR-2026-000005", "nigar", comp.get("04"), "TRT-001", "WHEAT", 100_000, 8, 24, now.minusDays(5));
        to(a5, ApplicationStatus.UNDER_REVIEW, "operator", null, now.minusDays(3));
        Application a6 = app("QAR-2026-000006", "kamran", comp.get("03"), "FZL-002", "POMEGRANATE", 300_000, 15, 36, now.minusDays(20));
        to(a6, ApplicationStatus.UNDER_REVIEW, "operator", null, now.minusDays(18));
        decide(a6, Decision.REJECTED, "Biznes planda suvarma həlli göstərilməyib.", now.minusDays(12));
        Application a7 = app("QAR-2026-000007", "rashad", comp.get("2025"), "XCL-001", "GRAPE", 264_000, 12, 36,
                LocalDateTime.of(2026, 1, 10, 11, 0));
        to(a7, ApplicationStatus.UNDER_REVIEW, "operator", null, LocalDateTime.of(2026, 1, 13, 10, 0));
        decide(a7, Decision.APPROVED, "Biznes plan əsaslandırılıb, yerli işçi öhdəliyi yüksəkdir.", LocalDateTime.of(2026, 1, 25, 15, 0));
        Application a8 = app("QAR-2025-000031", "tural", comp.get("2025"), "XCV-001", "WHEAT", 108_000, 25, 24,
                LocalDateTime.of(2025, 6, 20, 9, 30));
        to(a8, ApplicationStatus.UNDER_REVIEW, "operator", null, LocalDateTime.of(2025, 6, 25, 10, 0));
        decide(a8, Decision.APPROVED, "Taxılçılıq təcrübəsi və texnika parkı mövcuddur.", LocalDateTime.of(2025, 7, 15, 14, 0));
        applications.saveAll(List.of(a1, a2, a3, a5, a6, a7, a8));

        // Farm 1: Rəşad — grape, on schedule
        FarmProject f1 = farm(a7, FarmStatus.ACTIVE, LocalDate.of(2026, 2, 1));
        CropPlanItem grape = planItem(f1, "GRAPE", 22, 2026);
        for (CalendarTask t : calendarService.generate(grape)) {
            if (t.getStage() == CalendarStage.PLANTING) done(t, LocalDate.of(2026, 4, 10), "Tinglər əkildi");
            if (t.getStage() == CalendarStage.IRRIGATION) done(t, LocalDate.of(2026, 8, 28), "Damcı suvarma işlədi");
        }
        work(f1, WorkRole.AGRONOMIST, 1, true);
        work(f1, WorkRole.WORKER, 8, true);
        work(f1, WorkRole.MACHINE_OPERATOR, 2, false);
        work(f1, WorkRole.ACCOUNTANT, 1, true);
        report(f1, "2026-Q2", 120_000, "Tinglər əkildi, suvarma quraşdırıldı.", ReportStatus.ACCEPTED, LocalDateTime.of(2026, 7, 5, 12, 0));
        report(f1, "2026-Q3", 175_000, "Suvarma mövsümü başa çatdı.", ReportStatus.SUBMITTED, today.minusDays(11).atTime(16, 0));

        // Farm 2: Tural — wheat, at risk (delayed tasks, old report, low investment)
        FarmProject f2 = farm(a8, FarmStatus.AT_RISK, LocalDate.of(2025, 8, 1));
        CropPlanItem wheat = planItem(f2, "WHEAT", 90, 2025);
        for (CalendarTask t : calendarService.generate(wheat)) {
            if (t.getStage() == CalendarStage.SOIL_PREP) done(t, LocalDate.of(2025, 9, 20), null);
            else if (t.getStage() == CalendarStage.PLANTING) done(t, LocalDate.of(2025, 10, 25), null);
            else t.setStatus(CalendarService.statusOf(t, today));
        }
        work(f2, WorkRole.WORKER, 4, true);
        work(f2, WorkRole.MACHINE_OPERATOR, 1, false);
        report(f2, "2025-Q4", 20_000, "Torpaq hazırlığı və səpin.", ReportStatus.ACCEPTED, LocalDateTime.of(2026, 1, 10, 10, 0));

        // Share offering by Rəşad: 100 × 5 000 AZN, lead 30 + Nigar 10 = 40% sold
        ShareOffering o = new ShareOffering();
        o.setProject(f1);
        o.setLeadInvestor(u.get("rashad"));
        o.setTotalShares(100);
        o.setPricePerShare(5_000);
        o.setLeadShares(30);
        o.setDeadline(today.plusDays(20));
        o.setStatus(OfferingStatus.OPEN);
        o.setCreatedAt(now.minusDays(3));
        offerings.save(o);
        ShareHolding h = new ShareHolding();
        h.setOffering(o);
        h.setInvestor(u.get("nigar"));
        h.setShares(10);
        h.setCreatedAt(now.minusDays(1));
        holdings.save(h);
    }

    private Application app(String number, String investor, Competition comp, String plot, String crop,
                            long investment, int jobs, int months, LocalDateTime createdAt) {
        Application a = new Application();
        a.setNumber(number);
        a.setInvestor(u.get(investor));
        a.setCompetition(comp);
        a.setPlot(p.get(plot));
        a.setStatus(ApplicationStatus.SUBMITTED);
        a.setPlannedCrop(crop);
        a.setInvestmentAzn(investment);
        a.setJobs(jobs);
        a.setMonths(months);
        a.setBusinessPlan("Nümunə biznes plan: " + c.get(crop).getNameAz() + " əkini, " + jobs + " iş yeri, "
                + months + " ay ərzində mərhələli investisiya. (Uydurma məlumat)");
        a.setCreatedAt(createdAt);
        a.setUpdatedAt(createdAt);
        applications.save(a);
        hist(a, null, ApplicationStatus.SUBMITTED, investor, null, createdAt);
        Document d = new Document();
        d.setApplication(a);
        d.setType(DocType.BUSINESS_PLAN);
        d.setOriginalName("biznes-plan-" + number + ".pdf");
        d.setStoredName("seed-" + number + ".pdf"); // no file on disk for seed rows
        d.setSize(245_000);
        d.setUploadedAt(createdAt);
        documents.save(d);
        return a;
    }

    private void to(Application a, ApplicationStatus status, String actor, String comment, LocalDateTime at) {
        hist(a, a.getStatus(), status, actor, comment, at);
        a.setStatus(status);
        a.setUpdatedAt(at);
        notify(a.getInvestor(), "Müraciət " + a.getNumber() + ": status dəyişdi.", a, at);
    }

    private void decide(Application a, Decision decision, String reason, LocalDateTime at) {
        a.setDecision(decision);
        a.setDecisionReason(reason);
        String ref = "EKTIS-INV-" + at.getYear() + "-" + a.getNumber().substring(a.getNumber().length() - 6);
        a.setEktisRef(ref);
        to(a, ApplicationStatus.DECIDED, "komissiya", reason, at);
        IntegrationLog l = new IntegrationLog();
        l.setApplicationNumber(a.getNumber());
        l.setOperation("SEND_DECISION");
        l.setRequestJson("{\"schemaVersion\":\"1.0\",\"applicationNumber\":\"" + a.getNumber() + "\",\"decision\":\"" + decision + "\"}");
        l.setResponseJson("{\"status\":\"ACCEPTED\",\"ektisReference\":\"" + ref + "\"}");
        l.setStatus(IntegrationStatus.SUCCESS);
        l.setEktisRef(ref);
        l.setDurationMs(312);
        l.setAt(at.plusSeconds(1));
        integrationLogs.save(l);
    }

    private void hist(Application a, ApplicationStatus from, ApplicationStatus to, String actor, String comment, LocalDateTime at) {
        User who = u.get(actor);
        StatusHistory h = new StatusHistory();
        h.setApplication(a);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setComment(comment);
        h.setActorName(who.getFullName());
        h.setActorRole(who.getRole());
        h.setAt(at);
        history.save(h);
        AuditLog log = new AuditLog();
        log.setActorEmail(who.getEmail());
        log.setActorRole(who.getRole().name());
        log.setAction(from == null ? "APPLICATION_SUBMIT" : "STATUS_CHANGE");
        log.setEntity("Application");
        log.setEntityId(a.getNumber());
        log.setDetails((from == null ? "" : from + " → ") + to);
        log.setAt(at);
        auditLogs.save(log);
    }

    private void notify(User user, String msg, Application a, LocalDateTime at) {
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(msg);
        n.setLink("/kabinet/muraciet/" + a.getId());
        n.setRead(a.getStatus() != ApplicationStatus.INFO_REQUESTED); // keep the open info request unread
        n.setAt(at);
        notifications.save(n);
    }

    private FarmProject farm(Application a, FarmStatus status, LocalDate start) {
        FarmProject f = new FarmProject();
        f.setApplication(a);
        f.setPlot(a.getPlot());
        f.setInvestor(a.getInvestor());
        f.setStatus(status);
        f.setStartDate(start);
        f.setCommittedInvestment(a.getInvestmentAzn());
        f.setCommittedJobs(a.getJobs());
        f.setCommittedMonths(a.getMonths());
        return farms.save(f);
    }

    private CropPlanItem planItem(FarmProject f, String crop, double area, int seasonYear) {
        CropPlanItem i = new CropPlanItem();
        i.setProject(f);
        i.setCrop(c.get(crop));
        i.setAreaHa(area);
        i.setSeasonYear(seasonYear);
        return cropPlans.save(i);
    }

    private void done(CalendarTask t, LocalDate actual, String note) {
        t.setActualDate(actual);
        t.setNote(note);
        t.setStatus(TaskStatus.DONE);
    }

    private void work(FarmProject f, WorkRole role, int count, boolean local) {
        WorkforceItem w = new WorkforceItem();
        w.setProject(f);
        w.setRole(role);
        w.setCount(count);
        w.setLocal(local);
        workforce.save(w);
    }

    private void report(FarmProject f, String period, long invested, String note, ReportStatus status, LocalDateTime at) {
        ProgressReport r = new ProgressReport();
        r.setProject(f);
        r.setPeriod(period);
        r.setInvestedToDate(invested);
        r.setNote(note);
        r.setStatus(status);
        r.setSubmittedAt(at);
        reports.save(r);
    }

    // ---------------------------------------------------------------- AI sample

    private void seedAiSample() {
        AiAnalysis a = new AiAnalysis();
        a.setPlot(p.get("FZL-001"));
        a.setModel("sample"); // UI label: "Nümunə analiz"
        a.setCreatedAt(LocalDateTime.now(BAKU));
        a.setStatus("DONE");
        a.setResultJson("""
                {"summary":"Düz relyefli, qismən bitki örtüklü kənd təsərrüfatı sahəsi; suvarma kanalına yaxındır.",
                 "landCover":"Əsasən çılpaq torpaq və seyrək ot örtüyü, şərq hissədə köhnə bağ izləri.",
                 "terrain":"Düz, cüzi maillik (təxminən 1–3%).",
                 "waterSigns":"Şimal sərhədi boyunca suvarma kanalı görünür.",
                 "risks":["Yay quraqlığı — suvarma mütləqdir","Yazda gec şaxta ehtimalı (aşağı)"],
                 "suitableCrops":[{"code":"POMEGRANATE","name":"Nar","score":85,"reason":"İsti iqlim və suvarma imkanı."},
                                  {"code":"HAZELNUT","name":"Fındıq","score":78,"reason":"Şaxtaya davamlı, suvarma ilə yaxşı məhsul."},
                                  {"code":"GRAPE","name":"Üzüm","score":72,"reason":"Rayonda ənənəvi bitki."}],
                 "notRecommended":[{"name":"Alma","reason":"Yüksək su tələbi və isti yay."}],
                 "irrigationAdvice":"Damcı suvarma tövsiyə olunur; kanal suyu mövsümi yoxlanmalıdır.",
                 "confidence":"MEDIUM",
                 "dataGaps":["Torpaq analizi yoxdur","Qrunt sularının səviyyəsi məlum deyil"]}
                """);
        aiAnalyses.save(a);
    }

    private void logCounts() {
        Map<String, JpaRepository<?, ?>> all = new LinkedHashMap<>();
        all.put("users", users);
        all.put("crops", crops);
        all.put("calendarTemplates", templates);
        all.put("plots", plots);
        all.put("plotClimates", climates);
        all.put("suitabilities", suitabilities);
        all.put("aiAnalyses", aiAnalyses);
        all.put("competitions", competitions);
        all.put("applications", applications);
        all.put("documents", documents);
        all.put("statusHistory", history);
        all.put("notifications", notifications);
        all.put("auditLogs", auditLogs);
        all.put("integrationLogs", integrationLogs);
        all.put("farmProjects", farms);
        all.put("cropPlanItems", cropPlans);
        all.put("calendarTasks", tasks);
        all.put("workforceItems", workforce);
        all.put("progressReports", reports);
        all.put("shareOfferings", offerings);
        all.put("shareHoldings", holdings);
        StringBuilder sb = new StringBuilder("Seed counts:");
        all.forEach((k, r) -> sb.append(' ').append(k).append('=').append(r.count()));
        log.info(sb.toString());
    }
}
