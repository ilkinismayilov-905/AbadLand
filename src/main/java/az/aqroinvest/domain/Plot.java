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
    @Column(length = 2000)
    private String description;
    @Lob
    private String geoJson;
    private double lat;
    private double lng;
}
