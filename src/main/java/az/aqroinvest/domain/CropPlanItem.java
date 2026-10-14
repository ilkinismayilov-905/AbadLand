package az.aqroinvest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "crop_plan_items")
@Getter
@Setter
@NoArgsConstructor
public class CropPlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private FarmProject project;
    @ManyToOne(optional = false)
    private Crop crop;
    private double areaHa;
    private int seasonYear;
}
