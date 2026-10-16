package az.aqroinvest.web;

import az.aqroinvest.domain.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Single place for enum labels (az/en/ru via I18n) and Tailwind badge classes. Use in templates: ${@statusLabels.label(x)}. */
@Component("statusLabels")
public class StatusLabels {

    private static final String GREEN = "bg-green-100 text-green-800";
    private static final String YELLOW = "bg-yellow-100 text-yellow-800";
    private static final String RED = "bg-red-100 text-red-800";
    private static final String BLUE = "bg-blue-100 text-blue-800";
    private static final String GRAY = "bg-gray-100 text-gray-700";
    private static final String ORANGE = "bg-orange-100 text-orange-800";

    private final Map<Enum<?>, String> map = new HashMap<>();

    public StatusLabels() {
        put(Role.INVESTOR, GREEN);
        put(Role.OPERATOR, BLUE);
        put(Role.COMMISSION, ORANGE);
        put(Role.ADMIN, RED);
        put(EconomicRegion.QARABAG, GRAY);
        put(EconomicRegion.SHERQI_ZANGEZUR, GRAY);
        put(ActivityType.CROP_FARMING, YELLOW);
        put(ActivityType.LIVESTOCK, ORANGE);
        put(ActivityType.HORTICULTURE, GREEN);
        put(ActivityType.OTHER, GRAY);
        put(ListingStatus.DRAFT, GRAY);
        put(ListingStatus.PUBLISHED, GREEN);
        put(ListingStatus.UNPUBLISHED, ORANGE);
        put(DeminingStatus.CLEARED, GREEN);
        put(DeminingStatus.PARTIALLY_CLEARED, YELLOW);
        put(DeminingStatus.IN_PROGRESS, RED);
        put(CompetitionStatus.PENDING_APPROVAL, YELLOW);
        put(CompetitionStatus.PUBLISHED, GREEN);
        put(CompetitionStatus.REJECTED, RED);
        put(CompetitionStatus.CLOSED, GRAY);
        put(ApplicationStatus.SUBMITTED, BLUE);
        put(ApplicationStatus.UNDER_REVIEW, YELLOW);
        put(ApplicationStatus.INFO_REQUESTED, ORANGE);
        put(ApplicationStatus.DECIDED, GREEN);
        put(Decision.APPROVED, GREEN);
        put(Decision.REJECTED, RED);
        put(DocType.BUSINESS_PLAN, GRAY);
        put(DocType.SOIL_ANALYSIS, GRAY);
        put(DocType.IDENTITY, GRAY);
        put(DocType.OTHER, GRAY);
        put(WaterNeed.LOW, GREEN);
        put(WaterNeed.MEDIUM, YELLOW);
        put(WaterNeed.HIGH, RED);
        put(SuitabilitySource.RULE, GRAY);
        put(SuitabilitySource.AI, BLUE);
        put(SuitabilitySource.EXPERT, GREEN);
        put(RiskLevel.LOW, GREEN);
        put(RiskLevel.MEDIUM, YELLOW);
        put(RiskLevel.HIGH, RED);
        put(AiState.RUNNING, BLUE);
        put(AiState.AVAILABLE, GREEN);
        put(AiState.FAILED, RED);
        put(AiState.SAMPLE, YELLOW);
        put(AiState.NONE, GRAY);
        put(WorkRole.AGRONOMIST, GRAY);
        put(WorkRole.VET, GRAY);
        put(WorkRole.MACHINE_OPERATOR, GRAY);
        put(WorkRole.WORKER, GRAY);
        put(WorkRole.ACCOUNTANT, GRAY);
        put(WorkRole.OTHER, GRAY);
        put(CalendarStage.SOIL_PREP, ORANGE);
        put(CalendarStage.PLANTING, GREEN);
        put(CalendarStage.IRRIGATION, BLUE);
        put(CalendarStage.FERTILIZING, YELLOW);
        put(CalendarStage.PRUNING, GRAY);
        put(CalendarStage.HARVEST, RED);
        put(TaskStatus.PLANNED, BLUE);
        put(TaskStatus.DONE, GREEN);
        put(TaskStatus.DELAYED, RED);
        put(FarmStatus.ACTIVE, GREEN);
        put(FarmStatus.AT_RISK, RED);
        put(FarmStatus.COMPLETED, GRAY);
        put(ReportStatus.SUBMITTED, BLUE);
        put(ReportStatus.ACCEPTED, GREEN);
        put(ReportStatus.NEEDS_FIX, ORANGE);
        put(OfferingStatus.OPEN, GREEN);
        put(OfferingStatus.FUNDED, BLUE);
        put(OfferingStatus.CLOSED, GRAY);
        put(DealType.LEASE, BLUE);
        put(DealType.SALE, ORANGE);
        put(AuctionStatus.PENDING_APPROVAL, YELLOW);
        put(AuctionStatus.SCHEDULED, BLUE);
        put(AuctionStatus.LIVE, GREEN);
        put(AuctionStatus.ENDED, ORANGE);
        put(AuctionStatus.CONFIRMED, GREEN);
        put(AuctionStatus.FAILED, GRAY);
        put(AuctionStatus.REJECTED, RED);
        put(AuctionStatus.CANCELLED, GRAY);
        put(PlotStatus.AVAILABLE, GREEN);
        put(PlotStatus.IN_AUCTION, ORANGE);
        put(PlotStatus.LEASED, BLUE);
        put(PlotStatus.SOLD, GRAY);
        put(InvestorType.INDIVIDUAL, GRAY);
        put(InvestorType.LEGAL, GRAY);
        put(IntegrationStatus.SUCCESS, GREEN);
        put(IntegrationStatus.FAILED, RED);
    }

    private void put(Enum<?> e, String badge) {
        map.put(e, badge);
    }

    /** Label in the current language: key "enum.&lt;Type&gt;.&lt;NAME&gt;" in i18n/enums*.properties. */
    public String label(Enum<?> e) {
        return e == null ? "—" : I18n.t("enum." + e.getDeclaringClass().getSimpleName() + "." + e.name());
    }

    public String badge(Enum<?> e) {
        String v = e == null ? null : map.get(e);
        return "inline-block px-2 py-0.5 rounded text-xs font-medium " + (v == null ? GRAY : v);
    }

    /** Role from a Spring authority string like "ROLE_OPERATOR". */
    public String roleLabel(String authority) {
        return label(Role.valueOf(authority.replace("ROLE_", "")));
    }
}
