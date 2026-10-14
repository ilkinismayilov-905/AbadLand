package az.aqroinvest.web;

import az.aqroinvest.domain.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Single place for Azerbaijani enum labels and Tailwind badge classes. Use in templates: ${@statusLabels.label(x)}. */
@Component("statusLabels")
public class StatusLabels {

    private static final String GREEN = "bg-green-100 text-green-800";
    private static final String YELLOW = "bg-yellow-100 text-yellow-800";
    private static final String RED = "bg-red-100 text-red-800";
    private static final String BLUE = "bg-blue-100 text-blue-800";
    private static final String GRAY = "bg-gray-100 text-gray-700";
    private static final String ORANGE = "bg-orange-100 text-orange-800";

    private final Map<Enum<?>, String[]> map = new HashMap<>();

    public StatusLabels() {
        put(Role.INVESTOR, "İnvestor", GREEN);
        put(Role.OPERATOR, "Operator", BLUE);
        put(Role.COMMISSION, "Komissiya", ORANGE);
        put(Role.ADMIN, "Admin", RED);
        put(EconomicRegion.QARABAG, "Qarabağ", GRAY);
        put(EconomicRegion.SHERQI_ZANGEZUR, "Şərqi Zəngəzur", GRAY);
        put(ActivityType.CROP_FARMING, "Əkinçilik", YELLOW);
        put(ActivityType.LIVESTOCK, "Heyvandarlıq", ORANGE);
        put(ActivityType.HORTICULTURE, "Bağçılıq", GREEN);
        put(ActivityType.OTHER, "Digər", GRAY);
        put(DeminingStatus.CLEARED, "Tam təmizlənib", GREEN);
        put(DeminingStatus.PARTIALLY_CLEARED, "Qismən təmizlənib", YELLOW);
        put(DeminingStatus.IN_PROGRESS, "Təmizlənir", RED);
        put(CompetitionStatus.PENDING_APPROVAL, "Təsdiq gözləyir", YELLOW);
        put(CompetitionStatus.PUBLISHED, "Dərc edilib", GREEN);
        put(CompetitionStatus.REJECTED, "Rədd edilib", RED);
        put(CompetitionStatus.CLOSED, "Bağlanıb", GRAY);
        put(ApplicationStatus.SUBMITTED, "Qəbul edilib", BLUE);
        put(ApplicationStatus.UNDER_REVIEW, "Baxılır", YELLOW);
        put(ApplicationStatus.INFO_REQUESTED, "Əlavə məlumat tələb olunur", ORANGE);
        put(ApplicationStatus.DECIDED, "Qərar verilib", GREEN);
        put(Decision.APPROVED, "Müsbət", GREEN);
        put(Decision.REJECTED, "Mənfi", RED);
        put(DocType.BUSINESS_PLAN, "Biznes plan", GRAY);
        put(DocType.SOIL_ANALYSIS, "Torpaq analizi", GRAY);
        put(DocType.IDENTITY, "Şəxsiyyət sənədi", GRAY);
        put(DocType.OTHER, "Digər", GRAY);
        put(WaterNeed.LOW, "Az", GREEN);
        put(WaterNeed.MEDIUM, "Orta", YELLOW);
        put(WaterNeed.HIGH, "Çox", RED);
        put(SuitabilitySource.RULE, "Qayda", GRAY);
        put(SuitabilitySource.AI, "AI", BLUE);
        put(SuitabilitySource.EXPERT, "Ekspert", GREEN);
        put(RiskLevel.LOW, "Aşağı", GREEN);
        put(RiskLevel.MEDIUM, "Orta", YELLOW);
        put(RiskLevel.HIGH, "Yüksək", RED);
        put(WorkRole.AGRONOMIST, "Aqronom", GRAY);
        put(WorkRole.VET, "Baytar", GRAY);
        put(WorkRole.MACHINE_OPERATOR, "Texnika operatoru", GRAY);
        put(WorkRole.WORKER, "Fəhlə", GRAY);
        put(WorkRole.ACCOUNTANT, "Mühasib", GRAY);
        put(WorkRole.OTHER, "Digər", GRAY);
        put(CalendarStage.SOIL_PREP, "Torpaq hazırlığı", ORANGE);
        put(CalendarStage.PLANTING, "Əkin", GREEN);
        put(CalendarStage.IRRIGATION, "Suvarma mövsümü", BLUE);
        put(CalendarStage.FERTILIZING, "Gübrələmə", YELLOW);
        put(CalendarStage.PRUNING, "Budama / qulluq", GRAY);
        put(CalendarStage.HARVEST, "Yığım", RED);
        put(TaskStatus.PLANNED, "Planlaşdırılıb", BLUE);
        put(TaskStatus.DONE, "İcra olunub", GREEN);
        put(TaskStatus.DELAYED, "Gecikir", RED);
        put(FarmStatus.ACTIVE, "Aktiv", GREEN);
        put(FarmStatus.AT_RISK, "Risk var", RED);
        put(FarmStatus.COMPLETED, "Tamamlanıb", GRAY);
        put(ReportStatus.SUBMITTED, "Göndərilib", BLUE);
        put(ReportStatus.ACCEPTED, "Qəbul edilib", GREEN);
        put(ReportStatus.NEEDS_FIX, "Düzəliş tələb olunur", ORANGE);
        put(OfferingStatus.OPEN, "Açıq", GREEN);
        put(OfferingStatus.FUNDED, "Toplandı", BLUE);
        put(OfferingStatus.CLOSED, "Bağlandı", GRAY);
        put(DealType.LEASE, "İcarə", BLUE);
        put(DealType.SALE, "Satış", ORANGE);
        put(AuctionStatus.PENDING_APPROVAL, "Təsdiq gözləyir", YELLOW);
        put(AuctionStatus.SCHEDULED, "Planlaşdırılıb", BLUE);
        put(AuctionStatus.LIVE, "Gedir", GREEN);
        put(AuctionStatus.ENDED, "Başa çatıb", ORANGE);
        put(AuctionStatus.CONFIRMED, "Nəticə təsdiqlənib", GREEN);
        put(AuctionStatus.FAILED, "Baş tutmadı", GRAY);
        put(AuctionStatus.REJECTED, "Rədd edilib", RED);
        put(AuctionStatus.CANCELLED, "Ləğv edilib", GRAY);
        put(PlotStatus.AVAILABLE, "Boşdur", GREEN);
        put(PlotStatus.IN_AUCTION, "Hərracda", ORANGE);
        put(PlotStatus.LEASED, "İcarəyə verilib", BLUE);
        put(PlotStatus.SOLD, "Satılıb", GRAY);
        put(InvestorType.INDIVIDUAL, "Fiziki şəxs", GRAY);
        put(InvestorType.LEGAL, "Hüquqi şəxs", GRAY);
        put(IntegrationStatus.SUCCESS, "Uğurlu", GREEN);
        put(IntegrationStatus.FAILED, "Uğursuz", RED);
    }

    private void put(Enum<?> e, String label, String badge) {
        map.put(e, new String[]{label, badge});
    }

    public String label(Enum<?> e) {
        return e == null ? "—" : map.getOrDefault(e, new String[]{e.name()})[0];
    }

    public String badge(Enum<?> e) {
        String[] v = e == null ? null : map.get(e);
        return "inline-block px-2 py-0.5 rounded text-xs font-medium " + (v == null ? GRAY : v[1]);
    }

    /** Role from a Spring authority string like "ROLE_OPERATOR". */
    public String roleLabel(String authority) {
        return label(Role.valueOf(authority.replace("ROLE_", "")));
    }
}
