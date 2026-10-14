package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "crops")
@Getter
@Setter
@NoArgsConstructor
public class Crop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String code;
    private String nameAz;
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;
    private boolean fruit;
    private int investmentPerHa;
    private int revenuePerHa;
    private int opexPerHa;
    private int firstHarvestYear;
    private double yieldTPerHa;
    private int frostToleranceC;
    private int maxElevationM;
    @Enumerated(EnumType.STRING)
    private WaterNeed waterNeed;
}
