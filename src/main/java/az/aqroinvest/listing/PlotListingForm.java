package az.aqroinvest.listing;

import az.aqroinvest.domain.DeminingStatus;
import az.aqroinvest.domain.PlotCategory;
import lombok.Data;

import java.util.List;

/** Wizard form (all steps; each step posts only its own fields — null means "not on this step"). */
@Data
public class PlotListingForm {
    // 1. Torpaq
    private String geoJson;
    private Double areaHa;
    private String district;
    private String village;
    // 2. Təyinat
    private String title;
    private PlotCategory category;
    private String description;
    private List<String> crops;
    private String conceptLinkToAgri;
    // 3. Sahə məlumatları və şərtlər
    private DeminingStatus demining;
    private String waterSource;
    private Boolean irrigation;
    private String soilType;
    private Double roadKm;
    private Boolean electricity;
    private Integer leaseYears;
    private Integer feePerHa;
    private Long estimatedInvestment;
    private Integer expectedJobs;
}
