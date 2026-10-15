package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "plots")
@Getter
@Setter
@NoArgsConstructor
public class Plot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String code;
    private String name;
    private String district;
    @Enumerated(EnumType.STRING)
    private EconomicRegion economicRegion;
    private String village;
    private double areaHa;
    @Enumerated(EnumType.STRING)
    private ActivityType purpose;
    @Enumerated(EnumType.STRING)
    private DeminingStatus demining;
    private String waterSource;
    private boolean irrigation;
    private String soilType;
    private double roadKm;
    private boolean electricity;
    private int leaseYears;
    private int feePerHa;
    /** comma-separated crop codes */
    private String recommendedCrops;
    @Column(length = 3000)
    private String description;
    @Lob
    private String geoJson;
    private double lat;
    @Enumerated(EnumType.STRING)
    private PlotStatus status = PlotStatus.AVAILABLE;
    private double lng;

    // ---- staff listing (SPEC "Plot listings")
    @Enumerated(EnumType.STRING)
    private PlotCategory category;
    @Enumerated(EnumType.STRING)
    private ListingStatus listingStatus = ListingStatus.PUBLISHED;
    @ManyToOne
    private User createdBy;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime publishedAt;
    @Column(length = 3000)
    private String conceptLinkToAgri;
    private Long estimatedInvestment;
    private Integer expectedJobs;

    public boolean isPublished() {
        return listingStatus == ListingStatus.PUBLISHED;
    }

    /** "Yeni" badge: published within the last 7 days. */
    public boolean isRecent() {
        return publishedAt != null && publishedAt.isAfter(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Baku")).minusDays(7));
    }
}
